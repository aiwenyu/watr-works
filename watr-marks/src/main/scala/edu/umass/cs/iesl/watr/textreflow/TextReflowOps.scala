package edu.umass.cs.iesl.watr
package textreflow

import scalaz._, Scalaz._

import geometry._
import watrmarks._
import textboxing.{TextBoxing => TB}

import matryoshka._
import matryoshka.data._
import matryoshka.implicits._

import utils.EnrichNumerics._

case class Offsets(begin: Int, len: Int, total: Int, pad: Int)

trait TextReflowFunctions extends TextReflowClipping {
  import TextReflowF._
  import utils.SlicingAndDicing._

  def fixf = Fix[TextReflowF](_)

  def atom[AtomT](c: AtomT, ops:TextReflowAtomOps) = fixf(Atom(c, ops))
  def atomStr[AtomT](c: AtomT, ops:TextReflowAtomOps) = fixf(Atom(c, ops))
  def rewrite(t: TextReflow, s: String) = fixf(Rewrite(t, s))
  def flow(as:TextReflow*) = flows(as)
  def flows(as: Seq[TextReflow]) = fixf(Flow(as.toList))
  def cache(a: TextReflow, text: String) = fixf(CachedText(a, text))

  def bracket(pre: Char, post: Char, a:TextReflow) = fixf(
    Bracket(pre.toString, post.toString, a)
  )
  def bracket(pre: String, post: String, a:TextReflow) = fixf(
    Bracket(pre, post, a)
  )

  def labeled(l: Label, a:TextReflow) = fixf(Labeled(Set(l), a))
  def labeled(ls: Set[Label], a:TextReflow) = fixf(Labeled(ls, a))
  def insert(s: String) = fixf(Insert(s))
  def space() = insert(" ")

  private def mkPad(s: String): TextReflow = insert(s)

  def addLabel(l: Label): TextReflow => TextReflow = tr => fixf(tr.unFix match {
    case f @ Labeled(ls, s)  => f.copy(labels = ls + l)
    case r                   => labeled(l, fixf(r)).unFix
  })


  def join(sep:String)(bs:TextReflow*): TextReflow =
    joins(sep)(bs.toSeq)

  def joins(sep:String)(bs:Seq[TextReflow]): TextReflow =
    concat(bs.toList intersperse mkPad(sep))

  def concat(bs: Seq[TextReflow]): TextReflow = {
    flows(bs)
  }

  def groupByPairs(reflow: TextReflowT)(
    groupf: (TextReflowT, TextReflowT, Int) => Boolean,
    onGrouped: List[TextReflowT] => List[TextReflowT] = (w => w)
  ): TextReflowT = {
    reflow match {
      case f @ Flow(as) =>
        val grouped = as
          .groupByPairsWithIndex({
            case (a, b, i) => groupf(a.unFix, b.unFix, i)
          })
          .map(g =>Flow(g.toList))
          .toList

        f.copy(as = onGrouped(grouped).map(fixf(_)))

      case x =>
        println(s"unmatched ${x}")
        x

    }
  }

  def hasLabel(l: Label): TextReflowT => Boolean = _ match {
    case Labeled(labels, _) if labels.contains(l) => true
    case _ => false
  }


  def everyLabel(l: Label, r: TextReflow)(f: TextReflow => TextReflow): TextReflow = {
    def ifLabeled(r:TextReflowT): TextReflowT =  {
      if (hasLabel(l)(r)) holes(r) match {
        case Labeled(labels, (a, fWhole)) => fWhole(f(a))
        case _ => r
      } else r
    }

    r.transCata(ifLabeled)
  }

  def everywhere(r: TextReflow)(f: TextReflowT => TextReflowT): TextReflow = {
    r.transCata(f)
  }

  import utils.ScalazTreeImplicits._

  def boxTF[T, F[_]: Foldable: Functor](
    tf: T
  )(implicit
    TR: Recursive.Aux[T, F],
    FShow: Delay[Show, F]
  ): TB.Box = {
    tf.cata(toTree).drawBox
  }

  def prettyPrintTree(reflow: TextReflow): TB.Box = {
    reflow.cata(toTree).drawBox
  }

  def prettyPrintCofree[B](cof: Cofree[TextReflowF, B])(implicit
    BS: Show[B],
    CS: Delay[Show, Cofree[TextReflowF, ?]]
  ): String = {
    CS(BS).shows(cof)
  }

  def cofreeBox[B](cof: Cofree[TextReflowF, B])(implicit
    BS: Show[B]
  ): TB.Box = {
    cofreeAttrToTree(cof).drawBox
  }

  def cofreeAttrToTree[A](c: Cofree[TextReflowF, A]): Tree[A] = {
    // val cname = c.tail.getClass.getSimpleName
    Tree.Node(
      c.head,
      c.tail.toStream.map(cofreeAttrToTree(_))
    )
  }

  implicit object OffsetsInst extends Show[Offsets] {
    def zero: Offsets = Offsets(0, 0, 0, 0)
    override def shows(f: Offsets): String = s"(${f.begin} ${f.len}) ${f.total} +:${f.pad}"
  }

  implicit class RicherTextReflowT(val theReflow: TextReflowT)  {

    def hasLabel(l: Label): Boolean = theReflow match {
      case Labeled(labels, _) if labels.contains(l) => true
      case _ => false
    }
  }

  type TextReflowCR = TextReflowF[Cofree[TextReflowF, Offsets]]

  type CharLoc = TreeLoc[Offsets]
  type CharLocState = State[CharLoc, CharLoc]

  def charRangeState[A](
    i: CharLoc, t: TextReflowT
  ): CharLocState = {
    State.get[CharLoc] <* State.modify[CharLoc]({
      case r => r.right
          .orElse(r.firstChild)
          .getOrElse(sys.error("char range state out of sync"))
    })
  }

  def hideChar: TextReflow => TextReflow = {tr =>
    fixf {
      tr.project match {
        case a @ Atom(c, ops)  => Rewrite(fixf(a), "")
        case f                 => f
      }
    }
  }

  implicit class RicherReflow(val theReflow: TextReflow) extends TextReflowJsonFormats {
    import play.api.libs.json._
    import TextReflowRendering._

    def toJson(): JsValue = {
      textReflowToJson(theReflow)
    }

    // def cacheText(): String = {
    //   val res = theReflow.cata(attributePara(renderText))
    //   res.toPair._1
    // }

    def toText(): String = {
      val res = theReflow.cata(attributePara(renderText))
      res.toPair._1
    }

    def toFormattedText(): String = {
      val res = theReflow.transCata(escapeLineFormatting)
      res.toText
    }

    def modifyCharAt(i: Int)(fn: (Char, Int) => Option[String]): TextReflow = {
      modifyChars(i, 1)(fn)
    }

    def annotateCharRanges(): Cofree[TextReflowF, Offsets] =
      annotateReflowCharRanges(theReflow)

    def modifyChars(begin: Int, len: Int)(fn: (Char, Int) => Option[String]): TextReflow = {
      //  :: W[F[A]] => M[A]
      def transChars: ElgotAlgebraM[(Offsets, ?), Option, TextReflowF, TextReflow] = {
        case (charOffs, a@ Atom(c, ops))
            if begin <= charOffs.begin &&  charOffs.begin < begin+len =>

          for {
            ch  <- ops.chars.headOption
            mod <- fn(ch, begin+len)
            .map(rewrite(fixf(a), _))
            .orElse(Option(fixf(a)))
          } yield mod

        case (_,      f)                 => Some(fixf(f))
      }

      val trans = liftTM(
        attributeElgotM[(Offsets, ?), Option](transChars)
      )

      val res = theReflow.annotateCharRanges.cataM(trans)

      res.get.head
    }

    def charCount: Int = {
      theReflow.para(countChars)
    }


    def slice(begin: Int, until:Int): Option[TextReflow] =
      sliceTextReflow(theReflow, begin, until)


    def lines: Seq[TextReflow] = ???

    def targetRegions(): Seq[TargetRegion] = {
      def regions(t: TextReflowF[Seq[TargetRegion]]): Seq[TargetRegion] = t match {
        case Atom    (ac, ops)               => Seq(ac.asInstanceOf[CharAtom].targetRegion)
        case Insert  (value)                 => Seq()
        case Rewrite (attr, to)             => attr
        case Bracket (pre, post, attr)  => attr
        case Mask    (mL, mR, attr)     => attr
        case Flow    (asAndattrs)            => asAndattrs.flatten
        case Labeled (labels, attr)     => attr
        case CachedText(attr, text)     => attr
      }

      theReflow.cata(regions)
    }

    def intersect(other: TextReflow): TextReflow = ???

    // def intersectPage(other: PageIndex): Seq[Component] = {
    //   ???
    // }

    def clipToTargetRegion(targetRegion: TargetRegion): Seq[(TextReflow, RangeInt)] = {
      clipReflowToTargetRegion(theReflow, targetRegion)
    }
  }
}
