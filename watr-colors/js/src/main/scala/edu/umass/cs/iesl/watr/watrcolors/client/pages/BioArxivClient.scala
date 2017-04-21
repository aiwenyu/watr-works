package edu.umass.cs.iesl.watr
package watrcolors
package client
package pages

import parts._
import wiring._

import scala.async.Async
import scala.concurrent.Future

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.annotation._
import org.scalajs.dom
import org.scalajs.dom.ext._

import autowire._
import upickle.{default => UPickle}
import UPickle._

import labeling._
import watrmarks._
import native.mousetrap._

import TypeTagPicklers._
import watrmarks.{StandardLabels => LB}

import scaladget.stylesheet.{all => sty}
import scalatags.JsDom.all._


import scalatags.JsDom.all._

import rx._

import TypeTags._
import dom.raw.MouseEvent

class ZoneSelectionRx {

  var selectionInProgress = false

  val labelerType: Var[Option[String]] = Var(None)
  val documentId: Var[Option[String]] = Var(None)
  val selectionConstraint: Var[Constraint] = Var(ByLine)
  val selectedLabel: Var[Option[Label]] = Var(None)
  val selections: Var[Seq[Int@@ZoneID]] = Var(Seq())

  val doMergeZones: Var[Boolean] = Var(false)
  val doDeleteZone: Var[Boolean] = Var(false)

  def toUIState = UIState(
    selectionConstraint.now,
    selectedLabel.now,
    selections.now
  )

}

@JSExportTopLevel("WatrColors")
object WatrColors extends  BaseClientDefs {
  import BootstrapBits._



  object states {
    // def modState(f: UIState => UIState): Unit = {
    //   uiState = f(uiState)
    // }

    // def selectByChar(): Unit = modState(_.copy(selectionConstraint = ByChar))
    // def selectByLine(): Unit = modState(_.copy(selectionConstraint = ByLine))
    // def selectByRegion(): Unit = modState(_.copy(selectionConstraint = ByRegion))
    // def setLabel(l: Label): Unit = modState(_.copy(selectedLabel=Option(l)))

  }

  val keybindings: List[(String, (MousetrapEvent) => Unit)] = List(
    // "l t" -> ((e: MousetrapEvent) => states.setLabel(LB.Title)),
    // "l a" -> ((e: MousetrapEvent) => states.setLabel(LB.Authors)),
    // "l b" -> ((e: MousetrapEvent) => states.setLabel(LB.Abstract)),
    // "l f" -> ((e: MousetrapEvent) => states.setLabel(LB.Affiliation)),
    // "l r" -> ((e: MousetrapEvent) => states.setLabel(LB.References)),

    // "s l" -> ((e: MousetrapEvent) => states.selectByLine()),
    // "s c" -> ((e: MousetrapEvent) => states.selectByChar()),
    // "s b" -> ((e: MousetrapEvent) => states.selectByRegion()),

    // "s s" -> ((e: MousetrapEvent) => startSelection())
  )

  def initKeybindings() = {
    Mousetrap.reset()
    keybindings.foreach { case (str, fn) =>
      val bindFunc: MousetrapEvent => Boolean = e => {
        fn(e); true
      }

      Mousetrap.bind(str, bindFunc, "keypress")
    }
  }

  def uiRequestCycle(req: UIRequest) = for {
    uiResponse  <- shell.uiRequest(req)
  } {
    // uiState = uiResponse.uiState
    val adds = uiResponse.changes
      .collect {
        case AddLw(wid, widget) => widget.get
      }
    val dels = uiResponse.changes
      .collect {
        case RmLw(wid, widget) => widget.get
      }

    renderLabelWidget(adds).foreach {
      case (bbox, fobjs) =>
        fabricCanvas.renderOnAddRemove = false
        fobjs.foreach{os => os.foreach(fabricCanvas.add(_)) }
        fabricCanvas.renderAll()
        fabricCanvas.renderOnAddRemove = true
    }
  }


  // @JSExport
  // def startSelection(): Unit = {
  //   zoneSelectionRx.selectionInProgress = true

  //   fabricCanvas.defaultCursor = "crosshair"
  //   fabricCanvas.renderAll()

  //   for {
  //     bbox <- getUserSelection(fabricCanvas)
  //   } yield {
  //     fabricCanvas.defaultCursor = "default"

  //     val req = UIRequest(zoneSelectionRx.toUIState, SelectRegion(bbox))
  //     uiRequestCycle(req)
  //     zoneSelectionRx.selectionInProgress = false
  //   }

  // }

  object shell {
    val Client = new WebsideClient("shell")
    val api = Client[WatrShellApi]

    def uiRequest(r: UIRequest): Future[UIResponse] = {
      api.uiRequest(r).call()
    }

    def createDocumentLabeler(stableId: String@@DocumentID, labelerType: String): Future[(Seq[AbsPosWidget], LabelOptions)] = {
      api.createDocumentLabeler(stableId, labelerType).call()
    }

  }

  def clear(): Unit = {
    fabricCanvas.clear()
  }

  def echoLabeler(lwidget: Seq[AbsPosWidget], labelOptions: LabelOptions): Unit = Async.async {
    println("echoLabeler()")
    renderLabelWidget(lwidget).foreach {
      case (bbox, fobjs) =>
        fabricCanvas.renderOnAddRemove = false
        clear()
        fabricCanvas.setWidth(bbox.width.toInt)
        fabricCanvas.setHeight(bbox.height.toInt)

        fabricCanvas.renderOnAddRemove = false
        fobjs.foreach{os => os.foreach(fabricCanvas.add(_)) }
        fabricCanvas.renderAll()
        fabricCanvas.renderOnAddRemove = true

        val controls = createLabelerControls(labelOptions)
        val c = controls.render
    }


  }


  def createLabeler(docId: String, lt: String): Unit = {
    shell.createDocumentLabeler(DocumentID(docId), lt)
      .foreach { case (lwidget, opts) =>
        echoLabeler(lwidget, opts)
      }
  }


  // @JSExport
  // def setupClickCatchers(enable: Boolean): Unit = {
  //   val clickcb: js.Function1[MouseEvent, Boolean] = { (event: MouseEvent) =>
  //     if (!zoneSelectionRx.selectionInProgress) {
  //       println("click")

  //       val clickPt = getCanvasPoint(event.pageX.toInt, event.pageY.toInt)

  //       val req = UIRequest(zoneSelectionRx.toUIState, Click(clickPt))
  //       uiRequestCycle(req)
  //     }
  //     true
  //   }


  //   val elem = dom.document
  //     .getElementById("canvas-container")

  //   elem.addEventListener("click", clickcb, useCapture=false)
  // }


  def initRx(zoneSelectionRx: ZoneSelectionRx)(implicit co: Ctx.Owner): Unit = Rx {
    (zoneSelectionRx.documentId(), zoneSelectionRx.labelerType()) match {
      case (Some(docId), Some(lt)) =>
        createLabeler(docId, lt)

      case _ => // do nothing
    }

    zoneSelectionRx.doDeleteZone.foreach{
      case doDelete =>
        println("Delete: uiRequestCycle()")
        uiRequestCycle(
          UIRequest(
            zoneSelectionRx.toUIState,
            MenuAction(LabelAction.deleteZone(ZoneID(0)))
          )
        )
    }

    zoneSelectionRx.doMergeZones.foreach{
      case doMerge =>
        println("Merge: uiRequestCycle()")
        uiRequestCycle(
          UIRequest(
            zoneSelectionRx.toUIState,
            MenuAction(LabelAction.mergeZones(List()))
          )
        )
    }
  }


  var clientState: Option[ZoneSelectionRx] = None

  @JSExport
  def display(): Unit = {
    implicit val ctx: Ctx.Owner = Ctx.Owner.safe()

    val zoneSelectionRx = new ZoneSelectionRx
    clientState = Option(zoneSelectionRx)

    Rx {

      withBootstrapNative {

        val selectorControls = SharedLayout.zoneSelectorControls(
          zoneSelectionRx,
          List(
            LB.Title,
            LB.Authors,
            LB.Abstract,
            LB.Affiliation,
            LB.References
          ))



        val navContent = div() // SharedLayout.initNavbar(List(
                               //           NavSpan(selectorControls)
                               //         ))


        // initKeybindings()


        val bodyContent =
          div(
            ^.id:="canvas-container",
            pageStyles.canvasContainer,
            sty.marginLeft(15), sty.marginTop(25)
          )(
            canvas(^.id:="canvas", pageStyles.fabricCanvas)
          )

        val sidebarContent =
          ul(`class`:="sidebar-nav")

        SharedLayout.pageSetup(navContent, bodyContent, sidebarContent).render
      }

      // initRx(zoneSelectionRx)
      val c = fabricCanvas
      // setupClickCatchers(true)
      zoneSelectionRx.documentId() = param("doc")
      zoneSelectionRx.labelerType() = param("lt")
    }

  }


}
