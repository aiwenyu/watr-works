package edu.umass.cs.iesl.watr
package segment

import org.scalatest._

import geometry._

import ammonite.{ops => fs}
import java.net.URL
import TypeTags._
import corpora._
import utils.ExactFloats._

trait DocsegTestUtil extends  FlatSpec with Matchers {
  import DocumentSegmenter._

  object page {
    val page = (0 to 10).map(PageNum(_))
    def apply(i:Int) = page(i)
  }

  def wsv(s: String) = s.split(" ").map(_.trim).toList

  def getAttrVal(attr: String, text:String): String = {
    val index = text.indexOf(s""" ${attr}=""")
    text.substring(index)
      .dropWhile(_ != '"')
      .drop(1)
      .takeWhile(_ != '"')
      .mkString
  }


  def attrsToBounds(text: String): LTBounds = {
    LTBounds.Doubles(
      getAttrVal("x", text).toDouble,
      getAttrVal("y", text).toDouble,
      getAttrVal("width", text).toDouble,
      getAttrVal("height", text).toDouble
    )
  }

  def svgCutAndPasteToTest(svgstr: String): ParsedExample = {
    val lines = svgstr.split("\n").toList
      .map(_.trim)

    val (pageLines, nexts) = lines
      .span(!_.startsWith("---"))

    val (boundsLines, expectedOutputLines) = nexts.drop(1)
      .span(!_.startsWith("===="))

    val fileAndPageLine = pageLines
      .filter(_.startsWith("page=")).head

    val page = fileAndPageLine
      .dropWhile(!_.isDigit).takeWhile(_.isDigit).mkString
      .toInt

    val file = fileAndPageLine
      .split("/").last.dropRight(1)


    val parsedFrags = boundsLines
      .map({line =>
        val lineBounds = attrsToBounds(line)
        val foundText = line.split("-->").head.trim

        (foundText, PageNum(page), lineBounds)
      })

    ParsedExample(
      file,
      parsedFrags,
      expectedOutputLines.drop(1)
    )
  }

  def cutAndPasteToTestExample(cAndp: TextExample): ParsedExample = {
    val sourcePdfName = cAndp.source.split("/").last.trim
    val pageNumber = cAndp.source.split(":")(1).takeWhile(_.isDigit).mkString.toInt

    val parsedFrags = cAndp.fragments.split("\n").toList.map{ frag =>
      val boundsPart = frag.reverse.trim.drop(1).takeWhile(_ != '(').reverse
      val foundText = frag.substring(0, frag.length - boundsPart.length - 2).trim

      val bounds = boundsPart
        .replaceAll("[ltwh:]", "")
        .split(", ")
        .map(_.toDouble)

      (foundText, PageNum(pageNumber), LTBounds.Doubles(bounds(0), bounds(1), bounds(2), bounds(3)))
    }

    ParsedExample(
      sourcePdfName,
      parsedFrags,
      cAndp.expectedOutput.trim.split("\n").map(_.trim)
    )
  }

  def createFilteredMultiPageIndex(pdfIns: URL, pageNum: Int@@PageNum, regions: Seq[LTBounds]): DocumentSegmenter = {
    val path = fs.Path(pdfIns.getPath)

    val docId = DocumentID("dummy-id")

    val segmenter =  DocumentSegmenter.createSegmenter(docId, path, new MemDocZoningApi)

    // Assume these example regions are all from one page
    // val pageNum = regions.map(_.page.stable.pageNum).head
    // val allBboxes = regions.map(_.bbox)

    val minX = regions.map(_.left).min
    val minY = regions.map(_.top).min
    val maxX = regions.map(_.right).max
    val maxY = regions.map(_.bottom).max

    val totalBounds = LTBounds(
      minX, minY,
      maxX-minX,
      maxY-minY
    )
    // segmenter.mpageIndex.dbgFilterPages(pageNum)
    // segmenter.mpageIndex.dbgFilterComponents(pageNum, totalBounds)

    val interestingChars = segmenter.mpageIndex
      .getPageIndex(pageNum)
      .componentIndex
      .queryForIntersects(totalBounds)


    println("examining chars: ["+squishb(interestingChars)+"]")
    segmenter
  }

}


case class TestRegion(
  pdfUrl: URL,
  page: Int@@PageNum,
  bbox: LTBounds
)

case class Example(
  region: TestRegion,
  identifyingRegex: List[String],
  skip: Boolean = false
)

case class TextExample(
  source: String,
  fragments: String,
  expectedOutput: String
)

case class ParsedExample(
  source: String,
  targetRegions: Seq[(String, Int@@PageNum, LTBounds)],
  expectedOutput: Seq[String]
)