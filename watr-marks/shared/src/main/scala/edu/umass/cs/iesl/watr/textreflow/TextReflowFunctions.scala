package edu.umass.cs.iesl.watr
package textreflow

import scalaz._, Scalaz._

import geometry._
import watrmarks._

import matryoshka._
import matryoshka.data._
import matryoshka.implicits._

import utils.EnrichNumerics._
import geometry.syntax._
import PageComponentImplicits._


trait TextReflowSharedFunctions extends TextReflowClipping {

  import TextReflowF._
  import utils.SlicingAndDicing._


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
    if (bs.length==1) bs(0) else {
      bs.foldLeft(flow())((acc, e) => (acc.project, e.project) match {
        case (Flow(a1), Flow(a2)) => flows(a1++a2)
        case (Flow(a1), b)        => flows(a1 :+ fixf(b))
        case (_,        _)        => sys.error(s"concat on $acc \n ++ $e")
      })
    }
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


  implicit class RicherReflow(val theReflow: TextReflowF.TextReflow)  {

    def applyLineFormatting(): TextReflow = {
      theReflow.transCata[TextReflow](escapeLineFormatting)
    }

    def toText(): String = {
      val res = theReflow.cata(attributePara(renderText))
      res.toPair._1
    }

    def toFormattedText(): String = {
      val res = theReflow.transCata[TextReflow](escapeLineFormatting)
      res.toText
    }

    def modifyCharAt(i: Int)(fn: (Char, Int) => Option[String]): TextReflow = {
      modifyChars(i, 1)(fn)
    }

    def annotateCharRanges(): Cofree[TextReflowF, Offsets] =
      annotateReflowCharRanges(theReflow)

    def modifyChars(begin: Int, len: Int)(fn: (Char, Int) => Option[String]): TextReflow = {
      val modRange = RangeInt(begin, len)
      def offs(o: Offsets) =  RangeInt(o.begin, o.len)

      def transChars: ElgotAlgebraM[(Offsets, ?), Option, TextReflowF, TextReflow] = wfa => {
        wfa match {
          case (charOffs, tf) if modRange.intersect(offs(charOffs)).nonEmpty => tf match {
            case  Rewrite(a, str) =>

              val newstr = for {
                (ch, i) <- str.zip(charOffs.begin until charOffs.begin+charOffs.len)
              } yield {
                if (modRange.contains(i)) fn(ch, i).getOrElse(ch.toString())
                else ch.toString()
              }

              Option(rewrite(a, newstr.mkString))
            case  a@ Insert(str) =>
              val newstr = for {
                (ch, i) <- str.zip(charOffs.begin until charOffs.begin+charOffs.len)
              } yield {
                if (modRange.contains(i)) fn(ch, i).getOrElse(ch.toString())
                else ch.toString()
              }

              Option(insert(newstr.mkString))

            case a@ Atom(c) =>
              val newstr = for {
                (ch, i) <- c.char.zip(charOffs.begin until charOffs.begin+charOffs.len)
              } yield {
                if (modRange.contains(i)) fn(ch, i).getOrElse(ch.toString())
                else ch.toString()
              }

              Option(rewrite(fixf(a), newstr.mkString))

            case f          => Some(fixf(f))
          }

          case (charOffs, tf) => Some(fixf(tf))
        }
      }

      // val attrElgot = attributeElgotM[(Offsets, ?), Option, Cofree[TextReflowF, TextReflow]].apply(transChars)

      // val trans = liftTM(attrElgot)
      val trans = liftTM(transChars)

      val res = theReflow.annotateCharRanges.cataM(trans)

      res.get
    }

    def charCount: Int = theReflow.para(countChars)

    def length: Int = charCount

    def slice(begin: Int, until:Int): Option[TextReflow] =
      sliceTextReflow(theReflow, begin, until)

    def sliceLabels(l: Label): Seq[TextReflow] = {
      labeledSlices(theReflow, l)
    }

    def charAtoms(): Seq[CharAtom] = theReflow.cata(
      (t:TextReflowF[List[CharAtom]]) => {
        orBubbleAttr[List[CharAtom]](t) {
          case Atom(ac) => List(ac)
        }
      }
    )

    def charBounds(): Seq[LTBounds] = {
      charAtoms().map(_.bbox)
    }

    def targetRegions(): Seq[PageRegion] = {
      charAtoms().map(_.pageRegion)
    }

    def targetRegion(): PageRegion = {
      targetRegions.reduce(_ union _)
    }


    def bounds(): LTBounds = {
      val pageCount = charAtoms.map(_.pageRegion.page.pageId).toSet.length
      if (pageCount > 1) {
        sys.error(s"TextReflow.bounds() called on reflow spanning multiple pages")
      }

      charBounds().reduce(_ union _)
    }


    def clipToBoundingRegion(clipBox: LTBounds): Seq[(TextReflow, RangeInt)] = {
      clipReflowToBoundingRegion(theReflow, clipBox)
    }
  }
}
