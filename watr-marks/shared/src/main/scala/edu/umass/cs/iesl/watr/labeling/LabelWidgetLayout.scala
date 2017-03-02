package edu.umass.cs.iesl.watr
package labeling

import scalaz.{@@ => _, _}, Scalaz._

import matryoshka._
import matryoshka.data._
import matryoshka.implicits._
import matryoshka.patterns.EnvT

import textreflow.data._
import utils.{CompassDirection => CDir}
import geometry._
import geometry.syntax._
import LabelWidgetF._

import utils.ScalazTreeImplicits._
import TypeTags._


case class AbsPosAttr(
  widget: LabelWidgetF[Unit],
  widgetBounds: LTBounds,
  id: Int@@RegionID
)

case class PosAttr(
  widget: LabelWidgetF[Unit],
  widgetBounds: LTBounds,
  id: Int@@RegionID,
  selfOffset: PositionVector, // = Point(0, 0),
  childOffsets: List[PositionVector] = List()
) {

  def toStackString = {
    val soff = selfOffset.prettyPrint
    val ch = childOffsets.map(_.prettyPrint).mkString(", ")
    s"curVec:${soff}   st:[ ${ch} ]"
  }

  override def toString = {
    val wpp = widgetBounds.prettyPrint
    val sstr = toStackString
    val cn = widget.getClass().getName.split("\\$").last
    s"${cn}#${id} bb:${wpp} ${sstr} ]"
  }

  def translate(pvec: PositionVector): PosAttr = {
    PosAttr(
      widget,
      widgetBounds.translate(pvec),
      id,
      selfOffset.translate(pvec),
      childOffsets.map(_.translate(pvec))
    )
  }
}

object LabelWidgetLayoutHelpers {
  def cofreeLWAttrToTree[A](c: Cofree[LabelWidgetF, A]): Tree[A] = {
    Tree.Node(
      c.head,
      c.tail.toStream.map(cofreeLWAttrToTree(_))
    )
  }

  import textboxing.{TextBoxing => TB}

  def printTree(pos: Cofree[LabelWidgetF, PosAttr]): TB.Box = {
    cofreeLWAttrToTree(
      pos.map(posAttr => posAttr.toString)
    ).drawBox
  }
}


trait LabelWidgetLayout extends LabelWidgetBasics {


  val zeroLTBounds: LTBounds = LTBounds(0, 0, 0, 0)
  val zeroPosVector: PositionVector = Point(0, 0)

  // Natural Transformation  from EnvT ~> LabelWidget
  def stripLWEnv = new (EnvT[PosAttr, LabelWidgetF, ?] ~> LabelWidgetF[?]) {
    def apply[A](env: EnvT[PosAttr, LabelWidgetF, A]): LabelWidgetF[A] = {
      env.lower
    }
  }

  // calc child offset vectors and total bounding box
  def computeOffsets(
    relativePositions: List[(LabelWidget, PosAttr)],
    offsetFn: (LTBounds, PosAttr) => PositionVector
  ): (LTBounds, List[PositionVector]) = {
    val newpositions = relativePositions
      .foldLeft((zeroLTBounds, List[PositionVector]()))({
        case (acc@(currBounds, childVecs), (_, oldpos)) =>
          val newChildVec = offsetFn(currBounds, oldpos)
          val newpos = oldpos.translate(newChildVec)
          val newBounds = currBounds union newpos.widgetBounds

          (newBounds, newChildVec :: childVecs)
      })

    (newpositions._1, newpositions._2.reverse)
  }

  def layoutWidgetPositions(lwidget: LabelWidget): List[PosAttr] = {
    val idgen = utils.IdGenerator[RegionID]()

    val F = LabelWidgetFunctor
    // Bottom-up first pass evaluator
    def positionAttrs: GAlgebra[(LabelWidget, ?), LabelWidgetF, PosAttr] = fwa => {
      fwa match {
        case flw @ TargetOverlay(under, overs)  =>
          val selfPosition = under.bbox.toPoint(CDir.NW)
          val bbox = under.bbox.moveToOrigin
          val (_, childAdjustVecs) = computeOffsets(overs,
            {(cbbox, overpos) =>
              val childPosition = overpos.selfOffset
              childPosition - selfPosition
            })

          PosAttr(F.void(flw), bbox, idgen.nextId, zeroPosVector, childAdjustVecs)

        case flw @ LabeledTarget(target, label, score)   =>
          val bbox = target.bbox.moveToOrigin()
          val positionVec = target.bbox.toPoint(CDir.NW)
          PosAttr(F.void(flw), bbox, idgen.nextId, positionVec)

        case flw @ Col(attrs) =>
          val (bbox, childAdjustVecs) = computeOffsets(attrs, {(bbox, childPos)=> bbox.toPoint(CDir.SW)  })
          PosAttr(F.void(flw), bbox, idgen.nextId, zeroPosVector, childAdjustVecs)

        case flw @ Row(attrs) =>
          val (bbox, childAdjustVecs) = computeOffsets(attrs, {(bbox, childPos)=> bbox.toPoint(CDir.NE)  })
          PosAttr(F.void(flw), bbox, idgen.nextId, zeroPosVector, childAdjustVecs)

        case flw @ Pad(p@(a, attr), padding) =>
          val (chbbox, childAdjustVecs) = computeOffsets(List(p), {(bbox, childPos)=> bbox.toPoint(CDir.NE)  })

          val bbox = LTBounds(
            chbbox.left, chbbox.top,
            chbbox.width + padding.left + padding.right,
            chbbox.height + padding.top + padding.bottom
          )

          PosAttr(F.void(flw), bbox, idgen.nextId, zeroPosVector, childAdjustVecs)

        case flw @ Reflow(textReflow) =>
          val bounds = textReflow.bounds()
          val bbox = bounds.moveToOrigin

          PosAttr(F.void(flw), bbox, idgen.nextId, zeroPosVector, List())

        case flw @ TextBox(box) =>
          val str = box.toString
          val lines = str.split("\n")
          val height = lines.length
          val maxwidth = lines.map(_.length).max
          val bbox: LTBounds = LTBounds(0, 0, maxwidth*6d, height*16d)

          PosAttr(F.void(flw), bbox, idgen.nextId, zeroPosVector, List())

        // case flw @ Button(action) =>
        //   val width = (action.length+1) * 6d
        //   val height = 18d
        //   val bbox: LTBounds = LTBounds(0, 0, width, height)

        //   PosAttr(F.void(flw), bbox, idgen.nextId, zeroPosVector, List())


        // case flw @ Panel(p@(content, attr)) =>
        //   val (bbox, childAdjustVecs) = computeOffsets(List(p), {(bbox, childPos)=> bbox.toPoint(CDir.NW)  })

        //   PosAttr(F.void(flw), bbox, idgen.nextId, zeroPosVector, childAdjustVecs)


      }
    }


    def putStrLn[S](str: => String): State[S, Unit] =
      State.state[S, Unit]( println(str) )

    def adjustPositions(
      selfAttr: PosAttr, ft:LabelWidgetF[_]
    ): State[PosAttr, PosAttr] = {

      for {
        // Initial state passed from parent
        init       <- State.get[PosAttr]
        // Initial stack passed from parent
        initStack   = init.childOffsets

        // Current offset vector is the top of initial stack
        headOffsetVec  = initStack.head
        tailOffsetVecs = initStack.tail

        // Relative offset vectors for children of the current node
        selfOffsetVecs   = selfAttr.childOffsets

        // Translated current-node offset vectors from Relative -> Absolute positioning
        selfAbsOffsetVecs = selfOffsetVecs.map(_.translate(headOffsetVec))

        // Adjusted current-node bounding box to Absolute positioning
        newSelf = selfAttr.copy(
          widgetBounds=selfAttr.widgetBounds.translate(headOffsetVec)
        )

        // Update State monad to include repositioned offset vectors for current-node's children
        newState = init.copy(
          childOffsets =  selfAbsOffsetVecs ++ tailOffsetVecs
        )

        _      <- State.modify[PosAttr](_ => newState)
      } yield {
        newSelf
      }
    }


    val relativePositioned: Cofree[LabelWidgetF, PosAttr] =
      lwidget.cata(attributePara(positionAttrs))

    val zero = PosAttr(TextBox("dummy"), zeroLTBounds, RegionID(0), Point(0, 0), List(Point(0, 0)))

    val adjusted: Cofree[LabelWidgetF, PosAttr] = relativePositioned
      .attributeTopDownM[State[PosAttr, ?], PosAttr](zero)({
        case e => adjustPositions(e._2.ask, e._2.lower)
      })
      .eval(zero)
      .mapBranching(stripLWEnv)


      adjusted.universe.map({
        case cof => cof.head
      })

  }

}
