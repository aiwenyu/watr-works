package edu.umass.cs.iesl.watr
package watrcolors

import geometry._
import utils._
import labeling._
import watrmarks._
import textreflow._
import utils.ExactFloats._

object TypeTagPicklers {
  import upickle.default._, Aliases._
  import upickle.Js

  import TypeTags._

  implicit val Int_WorkflowID_Pickler: RW[String @@ WorkflowID] = RW[String @@ WorkflowID](
    {t => Js.Str(t.unwrap)},
    {case Js.Str(s) => WorkflowID(s)}
  )

  implicit val String_Username_Pickler: RW[String @@ Username] = RW[String @@ Username](
    {t => Js.Str(t.unwrap.toString)},
    {case Js.Str(s) => Username(s.toString)}
  )
  implicit val Int_ZoneID_Pickler: RW[Int @@ ZoneID] = RW[Int @@ ZoneID](
    {t => Js.Str(t.unwrap.toString)},
    {case Js.Str(s) => ZoneID(s.toInt)}
  )

  implicit val Int_RegionID_Pickler: RW[Int @@ RegionID] = RW[Int @@ RegionID](
    {t => Js.Str(t.unwrap.toString)},
    {case Js.Str(s) => RegionID(s.toInt)}
  )

  implicit val Int_CharID_Pickler: RW[Int @@ CharID] = RW[Int @@ CharID](
    {t => Js.Str(t.unwrap.toString)},
    {case Js.Str(s) => CharID(s.toInt)}
  )
  implicit val Int_PageID_Pickler: RW[Int @@ PageID] = RW[Int @@ PageID](
    {t => Js.Str(t.unwrap.toString)},
    {case Js.Str(s) => PageID(s.toInt)}
  )

  implicit val Int_PageNum_Pickler: RW[Int @@ PageNum] = RW[Int @@ PageNum](
    {t => Js.Str(t.unwrap.toString)},
    {case Js.Str(s) => PageNum(s.toInt)}
  )
  implicit val Int_LabelID_Pickler: RW[Int @@ LabelID] = RW[Int @@ LabelID](
    {t => Js.Str(t.unwrap.toString)},
    {case Js.Str(s) => LabelID(s.toInt)}
  )
  implicit val Int_DocumentID_Pickler: RW[String @@ DocumentID] = RW[String @@ DocumentID](
    {t => Js.Str(t.unwrap)},
    {case Js.Str(s) => DocumentID(s)}
  )
  implicit val Int_WidgetID_Pickler: RW[Int @@ WidgetID] = RW[Int @@ WidgetID](
    {t => Js.Str(t.unwrap.toString)},
    {case Js.Str(s) => WidgetID(s.toInt)}
  )

  implicit val Int_FloatRep_Pickler: RW[Int @@ FloatRep] = RW[Int @@ FloatRep](
    {t => Js.Str(t.unwrap.toString)},
    {case Js.Str(s) => FloatRep(s.toInt)}
  )

  // implicit val Int_FloatRep_Pickler: RW[Int @@ FloatRep] = RW[Int @@ FloatRep](
  //   {t => Js.Num(t.unwrap)},
  //   {case Js.Num(s) => FloatRep(s.toInt)}
  // )

}

object UPicklers {
  import upickle.default._, Aliases._
  import TypeTagPicklers._
  import TextReflowF._
  import LabelWidgetF._
  import WidgetMod._
  import Interaction._
  import LabelAction._


  implicit val Label_RW: RW[Label] = macroRW[Label]
  implicit val StablePage_RW: RW[StablePage] = macroRW[StablePage]
  implicit val PageRegion_RW: RW[PageRegion] = macroRW[PageRegion]
  implicit val PageGeometry_RW: RW[PageGeometry] = macroRW[PageGeometry]
  implicit val CharAtom_RW: RW[CharAtom] = macroRW[CharAtom]
  implicit val Zone_RW: RW[Zone] = macroRW[Zone]

  implicit val GeometricFigure_RW: RW[GeometricFigure] =
    macroRW[LTBounds]
      .merge(macroRW[LBBounds])
      .merge(macroRW[Point])
      .merge(macroRW[Line])
      .merge(macroRW[GeometricGroup])
      .merge(macroRW[Colorized])

  implicit val Padding_RW: RW[Padding] =
    macroRW[Padding]

  implicit val WidgetMod_RW: RW[WidgetMod] = macroRW[Added]
      .merge(macroRW[Removed])
      .merge(macroRW[Unmodified])

  implicit val UIState_RW: RW[UIState] = macroRW[UIState]
  implicit val UIRequest_RW: RW[UIRequest] = macroRW[UIRequest]
  implicit val UIResponse_RW: RW[UIResponse] = macroRW[UIResponse]
  implicit val PaginationInfo_RW: RW[PaginationInfo] = macroRW[PaginationInfo]
  implicit val Pagination_RW: RW[Pagination] = macroRW[Pagination]

  implicit val LabelerIdentifier_RW: RW[LabelerIdentifier] =
    macroRW[DocumentLabelerIdentifier]
      .merge(macroRW[WorkflowLabelerIdentifier])
      .merge(macroRW[NilLabelerIdentifier.type])

  implicit val TextReflowFU_RW: RW[TextReflowF[Unit]] =
    macroRW[Atom]
      .merge(macroRW[Insert])
      .merge(macroRW[Rewrite[Unit]])
      .merge(macroRW[Bracket[Unit]])
      .merge(macroRW[Flow[Unit]])
      .merge(macroRW[TextReflowF.Labeled[Unit]])


  implicit val LabelWidgetFU_RW: RW[LabelWidgetF[Unit]] =
    macroRW[RegionOverlay[Unit]]
      .merge(macroRW[Row[Unit]])
      .merge(macroRW[Col[Unit]])
      .merge(macroRW[Pad[Unit]])
      .merge(macroRW[ZStack[Unit]])
      .merge(macroRW[TextBox])
      .merge(macroRW[Reflow])
      .merge(macroRW[Figure])
      .merge(macroRW[LabelWidgetF.Labeled[Unit]])
      .merge(macroRW[Identified[Unit]])
      .merge(macroRW[Panel[Unit]])
      .merge(macroRW[Terminal.type])


  import upickle.Js

  implicit val Constraint_RW: RW[Constraint] = macroRW[Constraint]

  implicit def Interaction_RW: RW[Interaction] = RW[Interaction](
    {value => Js.Null},
    {case Js.Null => InteractNil}
  )

  implicit val LabelAction_RW: RW[LabelAction[Unit]] =
    macroRW[SelectZone]
      .merge(macroRW[ToggleZoneSelection])
      .merge(macroRW[DeleteZone])
      .merge(macroRW[MergeZones])
      .merge(macroRW[NavigateTo])


  implicit val Gesture_RW: RW[Gesture] =
    macroRW[SelectRegion]
      .merge(macroRW[Click])
      .merge(macroRW[DblClick])
      .merge(macroRW[MenuAction])


  implicit val Color_RW: RW[Color] =
    macroRW[RGBColor ]
      .merge(macroRW[CMYKColor])
      .merge(macroRW[HSVColor ])
      .merge(macroRW[HSLColor ])
      .merge(macroRW[Grayscale])

  implicit val AbsPosWidget_RW: RW[AbsPosWidget] = macroRW[AbsPosWidget]

  implicit val WorkflowTask_RW: RW[WorkflowTask] = macroRW[WorkflowTask]
  implicit val DocumentEntry_RW: RW[DocumentEntry] = macroRW[DocumentEntry]
  implicit val RemoteCall_RW: RW[RemoteCall] = macroRW[RemoteCall]
}
