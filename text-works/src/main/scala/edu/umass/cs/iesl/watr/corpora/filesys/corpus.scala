package edu.umass.cs.iesl.watr
package corpora
package filesys


import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.net.URI
import java.nio.{file => nio}
import play.api.libs.json
import scala.util.{Try, Failure, Success}

import ammonite.{ops => fs}, fs._

import fs2.Task
import fs2.Stream

object Corpus {

  def initCorpus(corpusRoot: String): Unit = {

    val fullPath = fs.FilePath(corpusRoot) match {
      case p: fs.Path =>  p
      case p: fs.RelPath => fs.pwd / p
    }

    val validPath = exists(fullPath)

    if (!validPath) {
      sys.error(s"init: invalid corpus root specified ${fullPath}")
    }
    val corpus = Corpus(fullPath)
    if (corpus.sentinelExists()) {
      sys.error(s"init: corpus seems to already be initialized ${fullPath}")
    }
    corpus.touchSentinel()
    corpus.normalizeCorpusEntries()
  }

  def apply(appRoot: nio.Path): Corpus = {
    new Corpus(Path(appRoot))
  }

  def apply(appRoot: Path): Corpus = {
    new Corpus(appRoot)
  }
}

class Corpus(
  val corpusRoot: Path
) {
  private[this] val log = org.log4s.getLogger

  def normalizeCorpusEntry(pdf: Path): Unit = {
    val artifactPath = corpusRoot / s"${pdf.name}.d"
    if (pdf.isFile && !(exists! artifactPath)) {
      log.info(s" creating artifact dir ${pdf}")
      mkdir! artifactPath
    }
    if (pdf.isFile) {
      val dest = artifactPath / pdf.name

      if (exists(dest)) {
        log.info(s"corpus already contains file ${dest}, skipping...")
      } else {
        log.info(s" stashing ${pdf}")
        mv.into(pdf, artifactPath)
      }
    }
  }
  def normalizeCorpusEntries(): Unit = {

    log.info(s"normalizing corpus at ${corpusRoot}")

    ls(corpusRoot)
      .filter(p=> p.isFile && (p.ext=="pdf" || p.ext=="ps"))
      .foreach { pdf =>
        normalizeCorpusEntry(pdf)
      }
  }

  override val toString = {
    s"corpus:${corpusRoot}"
  }

  def getURI(): URI = {
    corpusRoot.toIO.toURI()
  }
  lazy val corpusSentinel =  corpusRoot / ".corpus-root"


  def touchSentinel(): Unit = {
    if (!sentinelExists()) {
      write(corpusSentinel, "")
    }
  }

  def sentinelExists(): Boolean = {
    exists(corpusSentinel)
  }

  def hasEntry(entryDescriptor: String): Boolean = {
    (entryDescriptor.endsWith(".d") &&
      exists(corpusRoot / entryDescriptor) &&
      stat(corpusRoot / entryDescriptor).isDir)
  }


  // e.g., 3245.pf, or sha1:afe23s...
  def entry(entryDescriptor: String): Option[CorpusEntry]= {
    Option(new CorpusEntry(entryDescriptor, this))
  }
  def ensureEntry(entryDescriptor: String): CorpusEntry = {
    val entry = new CorpusEntry(entryDescriptor, this)

    if (!exists(entry.artifactsRoot)) {
      mkdir(entry.artifactsRoot)
    }

    entry
  }


  def entryStream(): Stream[Task, CorpusEntry] = {

    zip.dirEntriesRecursive[Task](corpusRoot.toNIO)
      .filter{ p =>
        val f = Path(p)
        f.ext == "d" && f.isDir
      }
      .flatMap { p =>
        val e = new CorpusEntry(p.toFile().getName, this)
        Stream.emit(e)
      }
  }


  def entries(): Seq[CorpusEntry] = {
    val artifacts = (ls! corpusRoot)
      .filter(f => f.ext == "d" && f.isDir)
      .map { _.name }
      .sorted
      .map{ new CorpusEntry(_, this) }

    artifacts.filterNot(_.getArtifacts.isEmpty)
  }

}

sealed trait ArtifactDescriptor

class CorpusEntry(
  val entryDescriptor: String,
  val corpus: Corpus
) {

  val artifactsRoot = corpus.corpusRoot / RelPath(entryDescriptor)

  override val toString = {
    s"${corpus}/./${entryDescriptor}"
  }

  def getURI(): URI = {
    artifactsRoot.toIO.toURI()
  }


  val entryDescriptorRoot = {
    entryDescriptor.dropRight(2)
  }

  def getArtifacts(): Seq[String] = {
    val allFiles = ls(artifactsRoot)
      .filter(fs.stat(_).isFile)

    allFiles.map(_.name)
  }

  def resolveArtifact(artifactDescriptor: String, groupDescriptor: Option[String]): CorpusArtifact = {
    groupDescriptor.map { grp =>
      new CorpusArtifact(artifactDescriptor, Left(
        new CorpusArtifactGroup(grp, this)
      ))
    } getOrElse {
      new CorpusArtifact(artifactDescriptor, Right(this))
    }
  }

  def resolveArtifactPath(artifactDescriptor: String, groupDescriptor: Option[String]): fs.Path = {
    groupDescriptor.map{group =>
      artifactsRoot / group / artifactDescriptor
    } getOrElse {
      artifactsRoot / artifactDescriptor
    }
  }

  def putArtifactBytes(artifactDescriptor: String, content: Array[Byte], groupDescriptor: Option[String]=None): CorpusArtifact = {
    val outputPath = resolveArtifactPath(artifactDescriptor, groupDescriptor)
    if (fs.exists(outputPath)) {
      fs.rm(outputPath)
    }
    write(outputPath, content)

    resolveArtifact(artifactDescriptor, groupDescriptor)
  }

  def putArtifact(artifactDescriptor: String, content: String, groupDescriptor: Option[String]=None): CorpusArtifact = {
    val outputPath = resolveArtifactPath(artifactDescriptor, groupDescriptor)
    write(outputPath, content)
    resolveArtifact(artifactDescriptor, groupDescriptor)
  }

  def getArtifactPath(artifactDescriptor: String, groupDescriptor: Option[String]=None): fs.Path = {
    resolveArtifactPath(artifactDescriptor, groupDescriptor)
  }

  def getArtifact(artifactDescriptor: String, groupDescriptor: Option[String]=None): Option[CorpusArtifact] = {
    if (hasArtifact(artifactDescriptor, groupDescriptor)) {
      Option(resolveArtifact(artifactDescriptor, groupDescriptor))
    } else None
  }

  def getArtifactGroup(groupDescriptor: String): Option[CorpusArtifactGroup] = {
    if (hasArtifactGroup(groupDescriptor)) {
      new CorpusArtifactGroup(groupDescriptor, this).some
    } else None
  }

  def ensureArtifactGroup(groupDescriptor: String): CorpusArtifactGroup = {
    val group = new CorpusArtifactGroup(groupDescriptor, this)
    if (!exists(group.rootPath)) {
      mkdir(group.rootPath)
    }
    group
  }

  def deleteArtifact(artifactDescriptor: String, groupDescriptor: Option[String]=None): Unit = {
    val p = getArtifactPath(artifactDescriptor, groupDescriptor)
    if (fs.exists(p) && fs.stat(p).isFile) {
      fs.rm(p)
    }
  }

  // def stashArtifact(artifactDescriptor: String, groupDescriptor: Option[String]=None): Option[Path] = {
  //   val artifact = new CorpusArtifact(artifactDescriptor,
  //     new CorpusArtifactGroup(groupDescriptor, this)
  //   )
  //   artifact.stash
  // }

  def hasArtifactGroup(groupDescriptor: String): Boolean ={
    exists(artifactsRoot / RelPath(groupDescriptor))
  }

  def hasArtifact(artifactDescriptor: String, groupDescriptor: Option[String]=None): Boolean ={
    exists(artifactsRoot / RelPath(artifactDescriptor))
  }

  def getPdfArtifact(): Option[CorpusArtifact] = {
    getArtifacts.filter(_.endsWith(".pdf"))
      .headOption
      .flatMap { getArtifact(_, None) }
  }

}

class CorpusArtifactGroup(
  val groupDescriptor: String,
  val entry: CorpusEntry
) {
  lazy val rootPath = entry.artifactsRoot / RelPath(groupDescriptor)

  def descriptor = s"""${entry.entryDescriptor}/${groupDescriptor}"""

  override val toString = {
    s"${entry}/${groupDescriptor}"
  }

  def deleteGroupArtifacts(): Unit = {
    ls(rootPath)
      .foreach(fs.rm(_))
  }

  def putArtifactBytes(artifactDescriptor: String, content: Array[Byte]): CorpusArtifact = {
    entry.putArtifactBytes(artifactDescriptor, content, Some(groupDescriptor))
  }

  def putArtifact(artifactDescriptor: String, content: String): CorpusArtifact = {
    putArtifactBytes(artifactDescriptor, content.getBytes)
  }

  def getArtifact(artifactDescriptor: String): Option[CorpusArtifact] = {
    entry.getArtifact(artifactDescriptor, Some(groupDescriptor))
  }

  def getArtifacts(): Seq[CorpusArtifact] = {
    ls(rootPath).sortBy(_.name)
      .map(path => new CorpusArtifact(path.name, Left(this)))
  }

}

class CorpusArtifact(
  val artifactDescriptor: String,
  val grouping: Either[CorpusArtifactGroup, CorpusEntry]
) {
  private[this] val log = org.log4s.getLogger

  def rootPath = grouping.fold(
    group => group.rootPath,
    entry => entry.artifactsRoot
  )

  def descriptor = grouping.fold(
    group => s"""${group.descriptor}/${artifactDescriptor}""",
    entry => s"""${artifactDescriptor}"""
  )

  def artifactPath = rootPath / artifactDescriptor

  override val toString = descriptor

  def exists(): Boolean = {
    fs.exists(artifactPath)
  }

  def delete(): Unit = {
    fs.rm(artifactPath)
  }

  import utils.PathUtils._

  def stash(): Option[Path] = {
    val fileWithTimestamp = appendTimestamp(artifactDescriptor)
    val stashedName = rootPath / fileWithTimestamp
    log.trace(s"stashing ${artifactPath} as ${stashedName}")
    fs.mv(artifactPath, stashedName)
    Some(stashedName)
  }

  def unstash(): Option[Path] = {
    fs.ls(rootPath)
      .filter(_.toIO.getName.startsWith(artifactDescriptor))
      .sortBy(_.toIO.getName)
      .lastOption
      .map({ path =>
        fs.mv(path, artifactPath)
        artifactPath
      })
  }

  def asPath: Try[Path] = Success(artifactPath)

  def asDirectory: Try[Path] = {
    asPath.filter({p => fs.stat(p).isDir})
  }

  def asInputStream: Try[InputStream] = {
    val fis = nio.Files.newInputStream(artifactPath.toNIO)
    Success(fis)
  }

  def asReader: Try[Reader] = {
    asInputStream.map(new InputStreamReader(_))
  }

  def asJson: Try[json.JsValue] = try {
    asInputStream.map(json.Json.parse(_))
  } catch {
    case t: Exception => Failure(t)
  }

}
