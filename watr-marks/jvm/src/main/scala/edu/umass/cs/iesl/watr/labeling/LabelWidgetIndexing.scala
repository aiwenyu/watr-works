package edu.umass.cs.iesl.watr
package labeling

import scala.collection.mutable

import textreflow.data._
import geometry._
import geometry.syntax._
import LabelWidgetF._
import corpora._
import rindex._
import watrmarks._
import LabelAction._

import scalaz.Free
import scalaz.~>
import scalaz.State
import shapeless._

import matryoshka._
import matryoshka.implicits._

// Provide a caching wrapper around TextReflow + precomputed page bbox
// Only valid for TextReflow that occupy a single Bbox (e.g., VisualLine)
case class IndexableTextReflow(
  id: Int@@TextReflowID,
  textReflow: TextReflow,
  pageRegion: PageRegion
)

case class QueryHit(
  positioned: WidgetPositioning,
  pageId: Int@@PageID,
  pageSpaceBounds: LTBounds,
  iTextReflows: Seq[IndexableTextReflow]
)

object LabelWidgetIndex extends LabelWidgetLayout {

  implicit object TextReflowIndexable extends SpatialIndexable[IndexableTextReflow] {
    def id(t: IndexableTextReflow): Int = t.id.unwrap
    def ltBounds(t: IndexableTextReflow): LTBounds = t.pageRegion.bbox
  }

  implicit object LabelWidgetIndexable extends SpatialIndexable[WidgetPositioning] {
    def id(t: WidgetPositioning): Int = t.id.unwrap
    def ltBounds(t: WidgetPositioning): LTBounds = t.widgetBounds
  }


  import textreflow.TextReflowJsonCodecs._

  def create(docStore0: DocumentCorpus, lwidget: LabelWidget): LabelWidgetIndex = {
    val lwIndex = SpatialIndex.createFor[WidgetPositioning]()

    val layout0 = layoutWidgetPositions(lwidget)

    layout0.positioning.foreach({pos =>
      // println(s"adding pos widget ${pos.widget} @ ${pos.widgetBounds}")
      lwIndex.add(pos)
    })

    val targetPageRIndexes = mutable.HashMap[Int@@PageID, SpatialIndex[IndexableTextReflow]]()

    layout0.positioning.foreach({pos => pos.widget match {

      case l @ RegionOverlay(under, overs) =>
        val pageId = under.pageId
        // val regionId = under.regionId
        // under.bbox
        // docStore0.getTargetRegion(under.regionId)
        if (!targetPageRIndexes.contains(pageId)) {
          val pageIndex = SpatialIndex.createFor[IndexableTextReflow]()
          targetPageRIndexes.put(pageId, pageIndex)

          // Put all visual lines into index
          for {
            vline <- docStore0.getPageVisualLines(pageId)
            reflow <- docStore0.getModelTextReflowForZone(vline.id)
          } {
            val textReflow = jsonStrToTextReflow(reflow.reflow)
            val indexable = IndexableTextReflow(
              reflow.prKey,
              textReflow,
              PageRegion(
                pageId,
                textReflow.targetRegion.bbox
              )
            )
            pageIndex.add(indexable)
          }
        }

      case _ =>

    }})


    new LabelWidgetIndex {
      def docStore: DocumentCorpus = docStore0
      def layout: WidgetLayout = layout0
      def index: SpatialIndex[WidgetPositioning] = lwIndex
      def pageIndexes: Map[Int@@PageID, SpatialIndex[IndexableTextReflow]] = targetPageRIndexes.toMap
    }
  }
}

case class InterpState(
  uiResponse: UIResponse,
  labelWidget: LabelWidget
)
object istate {

  val uiResponseL   = lens[InterpState].uiResponse
  val uiStateL      = lens[InterpState].uiResponse.uiState
  val selectionsL   = lens[InterpState].uiResponse.uiState.selections
  val changesL      = lens[InterpState].uiResponse.changes
  val labelWidgetL  = lens[InterpState].labelWidget


  def addSelection(zoneId: Int@@ZoneID): InterpState => InterpState =
    st => selectionsL.modify(st) {
      sels => zoneId +: sels
    }
}


trait LabelWidgetIndex {

  def docStore: DocumentCorpus
  def layout: WidgetLayout
  def index: SpatialIndex[WidgetPositioning]
  def pageIndexes: Map[Int@@PageID, SpatialIndex[IndexableTextReflow]]

  def queryForPanels(queryPoint: Point): Seq[(Panel[Unit], WidgetPositioning)] = {
    val queryBox = queryPoint
      .lineTo(queryPoint.translate(1, 1))
      .bounds
    // println(s"queryForPanels: queryPoint = ${queryBox}")


    val ret = index.queryForIntersects(queryBox)
      .map ({ pos => pos.widget match {
        case p : Panel[Unit] => Option { (p, pos) }
        case _ => None
      }})
      .flatten

    // println(s"queryForPanels: found = ${ret.mkString('\n'.toString)}")
    ret
  }

  def queryRegion(queryBounds: LTBounds): Seq[QueryHit] = {
    val hits = index
      .queryForIntersects(queryBounds)
      .map { pos => pos.widget match {

        case RegionOverlay(under, over) =>
          pos.widgetBounds
            .intersection(queryBounds)
            .map { ibbox =>
              val pageSpaceBounds = ibbox.translate(pos.translation)
              val pageIndex = pageIndexes(under.pageId)
              val pageHits = pageIndex.queryForIntersects(pageSpaceBounds)
              QueryHit(pos, under.pageId, pageSpaceBounds, pageHits)
            }

        case _ =>
          None
      }}

    hits.flatten
  }


  def labelConstrained(constraint: Constraint, queryHits: Seq[QueryHit], label: Label): Option[GeometricGroup] = {
    var changes = List[GeometricFigure]()

    val pageRegionsToBeLabeled = for {
      qhit <- queryHits
    } yield constraint match {
      case ByLabel(l) =>
        val regions = qhit.iTextReflows.map(_.pageRegion)
        changes = regions.map(_.bbox.translate(-qhit.positioned.translation)).toList
        regions

      case ByLine =>

        val regions = qhit.iTextReflows.map(_.pageRegion)
        changes = regions.map(_.bbox.translate(-qhit.positioned.translation)).toList
        regions

      case ByRegion =>
        val regions = Seq(PageRegion(qhit.pageId, qhit.pageSpaceBounds, None))
        changes = regions.map(_.bbox.translate(-qhit.positioned.translation)).toList
        regions

      case ByChar =>
        val regionss = for {
          iReflow <- qhit.iTextReflows
        } yield {

          iReflow.textReflow
            .clipToBoundingRegion(qhit.pageSpaceBounds)
            .map { case (clipped, _) =>
              val bbox = clipped.targetRegion().bbox
              PageRegion(qhit.pageId, bbox, None)
            }

        }

        val regions = regionss.flatten
        changes = regions.map(_.bbox.translate(-qhit.positioned.translation)).toList

        regions
    }

    if (pageRegionsToBeLabeled.isEmpty) Seq() else {
      // Ensure pageRegions are all cataloged in database
      val targetRegions = for {
        pageRegion <- pageRegionsToBeLabeled.flatten
      } yield {
        val regionId = docStore.addTargetRegion(pageRegion.pageId, pageRegion.bbox)
        docStore.getTargetRegion(regionId)
      }

      val docId = docStore.getDocument(targetRegions.head.stableId).get

      val newZone = docStore.createZone(docId)

      docStore.setZoneTargetRegions(newZone, targetRegions)

      docStore.addZoneLabel(newZone, label)

      Some(docStore.getZone(newZone))
    }

    if (changes.isEmpty) None else {
      val groupBbox = changes.map(totalBounds(_)).reduce(_ union _)
      Some(GeometricGroup(groupBbox, changes))
    }
  }

  def addLabel(queryBounds: LTBounds, constraint: Constraint, label: Label): Option[GeometricGroup] = {
    val queryHits = queryRegion(queryBounds)
    labelConstrained(constraint, queryHits, label)
  }


  val interpLabelAction: LabelAction ~> State[InterpState, ?] =
    new (LabelAction ~> State[InterpState, ?]) {

      def apply[A](fa: LabelAction[A]) =  {

        fa match {
          case act@ SelectZone(zoneId) =>

            println(s"SelectZone")
            for {
              _ <- State.modify[InterpState] { interpState =>
                // Add zoneId to client-side state
                val st0 = istate.selectionsL.modify(interpState) { sels => zoneId +: sels }

                // Traverse the widget tree, adding indicators around all of the zone boxes
                istate.labelWidgetL.modify(st0) { labelWidget =>
                  LabelWidgetTransforms.atEveryId(zoneId, labelWidget, { lw: LabelWidget =>
                    lw.project match {
                      case fa@ Figure(fig) =>
                        val ff = LabelWidgets.figure(
                          composeFigures(makeFringe(fig, Padding(10)), fig)
                        )

                        // println(s"match Figure: ${ff}")
                        ff
                      case _ => lw
                    }
                  })

                  // Add a "fringe" bbox around everything Identified as id=zoneId
                  // st.copy(changes = add :: st.changes)
                  // labelWidget

                }


              }
            } yield zoneId

          case act: LabelAction.SelectRegion       => for { init <- State.get[InterpState] } yield ()
          case act: UnselectRegion     => for { init <- State.get[InterpState] } yield ()
          case act: UnselectZone       => for { init <- State.get[InterpState] } yield ()
          case act: CreateZone         => for { init <- State.get[InterpState] } yield ()
          case act: DeleteZone         => for { init <- State.get[InterpState] } yield ()
          case act: LabelZone          => for { init <- State.get[InterpState] } yield ()
          case act: CreateFigure       => for { init <- State.get[InterpState] } yield ???
          case act: QueryForRegions    => for { init <- State.get[InterpState] } yield Seq[GeometricRegion]()
          case act: QueryForZones      => for { init <- State.get[InterpState] } yield Seq[Int@@ZoneID]()
        }
      }

    }


  def runLabelAction[A](program: Free[LabelAction, A], uiResponse: UIResponse, widget: LabelWidget): InterpState = {
    program.foldMap(interpLabelAction)
      .apply(InterpState(uiResponse, widget))
      ._1
  }

  implicit class LabelActionOps[A](ma: Free[LabelAction, A]) {
    def exec(uiResponse: UIResponse, widget: LabelWidget): InterpState = runLabelAction(ma, uiResponse, widget)
  }



  // TODO this part really needs to be confined to WatrColors front end codebase
  // map (UIState, Gesture) => (UIState, UIChanges)
  def userInteraction(uiState: UIState, gesture: Gesture): (UIResponse, LabelWidget) = {
    val initResponse = UIResponse(uiState, List())
    gesture match {

      case Click(point) =>

        queryForPanels(point)
          .foldLeft((initResponse, layout.labelWidget)) {
            case ((accResponse, accWidget), (panel, qhit))  =>

              panel.interaction match {
                case InteractProg(prog) =>
                  println(s"Interpreting ${prog} in panel ${panel}")

                  val run = prog.exec(accResponse, accWidget)

                  (run.uiResponse, run.labelWidget)

                case _ =>
                  // TODO
                  (accResponse, accWidget)
              }
          }



      case DblClick(point) =>
        (initResponse, layout.labelWidget)

      case SelectRegion(bbox) =>
        (initResponse, layout.labelWidget)

    }

  }

  // def userInteraction(gesture: Gesture): Unit = {
  //   gesture match {

  //     case Click(point) =>
  //       queryForPanels(point)
  //         .map{ case(panel, qhit)  =>
  //           panel.interaction match {
  //             case InteractProg(prog) =>
  //               println(s"Interpreting ${prog} in panel ${panel}")

  //               prog.exec()

  //             case _ =>
  //           }
  //         }

  //     case DblClick(point) =>

  //     case SelectRegion(bbox) =>

  //   }

  //   ???
  // }



  def debugPrint(query: Option[LTBounds] = None): Unit = {
    val fillers = "αßΓπΣσµτΦΘΩδ∞φε∩".toList
    var _filler = 0
    def nextFiller(): Char = {
      _filler = (_filler + 1) % fillers.length
      fillers(_filler)
    }

    val w: Int = (layout.layoutBounds.width).intValue()+1
    val h: Int = (layout.layoutBounds.height).intValue()+1

    val gridPaper = GridPaper.create(w, h)
    val gridPaper2 = GridPaper.create(w, h)

    layout.positioning.foreach { pos =>
      val gridbox = GridPaper.ltb2box(pos.widgetBounds)

      pos.widget match {
        case RegionOverlay(under, over) =>
          // println(s"RegionOverlay($over, $under)")
          val fill = under.regionId.map { id =>
            (id.unwrap + '0'.toInt).toChar
          }.getOrElse(nextFiller)

          gridPaper.fillFg(fill, gridbox)

        case _ =>
      }
    }

    layout.positioning.foreach { pos =>
      val gridbox = GridPaper.ltb2box(pos.widgetBounds)
      pos.widget match {
        // case l @ Pad(a, pd, clr)      => f(a).map(Pad(_, pd, clr))
        // case l : TextBox              => G.point(l.copy())
        // case l : Reflow               => G.point(l.copy())
        case l : LabeledTarget =>
          gridPaper2.shadeBackground(gridbox)

        case l : Figure =>
          // gridPaper2.fillFg(nextFiller(), gridbox)

        case l @ Panel(a, i) =>
          gridPaper2.fillFg(nextFiller(), gridbox)
          // gridPaper2.borderTopBottom(gridbox)

        case l @ Identified(a, id, cls)    =>
          // gridPaper2.fillFg(nextFiller(), gridbox)
          // gridPaper2.gradientHorizontal(gridbox)
          // gridPaper2.borderLeftRight(gridbox)
        case _ =>
      }
    }

    layout.positioning.foreach { pos =>
      val gridbox = GridPaper.ltb2box(pos.widgetBounds)
      pos.widget match {
        case Col(as) =>
          gridPaper.borderLeftRight(gridbox)
        case Row(as) =>
          gridPaper.borderTopBottom(gridbox)

        // case l : RegionOverlay[A]     => l.overs.traverse(f).map(ft => l.copy(overs=ft))
        // case l @ Pad(a, pd, clr)      => f(a).map(Pad(_, pd, clr))
        // case l : LabeledTarget        => G.point(l.copy())
        // case l : TextBox              => G.point(l.copy())
        // case l : Reflow               => G.point(l.copy())
        // case l : Figure               => G.point(l.copy())
        // case l @ Panel(a, i)          => f(a).map(Panel(_, i))
        // case l @ Identified(a, id, cls)    => f(a).map(Identified(_, id, cls))
        case _ =>
      }
    }
    query foreach { q =>
      gridPaper.shadeBackground(GridPaper.ltb2box(q))
      gridPaper2.shadeBackground(GridPaper.ltb2box(q))
    }


    val grid1 = gridPaper.asString()
    val grid2 = gridPaper2.asString()

    println(grid1)
    println
    println(grid2)
    println
  }
}
