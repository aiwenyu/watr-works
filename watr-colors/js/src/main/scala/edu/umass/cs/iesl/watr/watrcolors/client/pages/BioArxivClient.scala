package edu.umass.cs.iesl.watr
package watrcolors
package client
package pages

import parts._
import wiring._

import scala.concurrent.Future

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.annotation._
import org.scalajs.dom.ext._


import labeling._
import geometry._
import geometry.syntax._
import watrmarks._

import scaladget.stylesheet.{all => sty}
import sty.{ctx => _, _}
import scalatags.JsDom.all._

import rx._
import scaladget.api._
import scaladget.tools.JsRxTags.{ctx => _, _}

import TypeTags._
// import dom.raw.MouseEvent

import BootstrapBits._
import org.singlespaced.d3js.d3

class ClientStateRx(
  initUIState: UIState,
  uiRequestCycle: (UIRequest) => Future[Unit]
)(implicit co: Ctx.Owner) extends BasicClientDefs {

  val UIState(uiContraint, activeLabel, selections, activeLabeler) =  initUIState
  val DocumentLabelerIdentifier(initStableId, initLabelerType, initPagination, initLabelColors) = activeLabeler

  val paginator = new Paginator(initPagination)

  val uiState_constraint: Var[Constraint] = Var(uiContraint)
  val uiState_selectedLabel: Var[Option[Label]] = Var(activeLabel)
  val uiState_selections: Var[Seq[Int@@ZoneID]] = Var(selections)


  // Active labeler vars
  val uiLabeler_stableId: Var[String@@DocumentID] = Var(initStableId)
  val uiLabeler_labelerType: Var[String] = Var(initLabelerType)
  val uiLabeler_labelColors: Var[Map[Label, utils.Color]] = Var(initLabelColors)


  def fromUIState(uiState: UIState): Unit = Rx {
    // val UIState(uiContraint, activeLabel, selections, activeLabeler) = uiState
    uiState_constraint() = uiState.selectionConstraint
    uiState_selectedLabel() = uiState.selectedLabel
    uiState_selections() = uiState.selections

    val DocumentLabelerIdentifier(id, lt, pg, colMap) = uiState.currentLabeler
    uiLabeler_stableId() =  id
    uiLabeler_labelerType() = lt
    uiLabeler_labelColors() = colMap
  }

  def toUIState(): UIState = UIState(
    uiState_constraint.now,
    uiState_selectedLabel.now,
    uiState_selections.now,
    DocumentLabelerIdentifier(
      uiLabeler_stableId.now,
      uiLabeler_labelerType.now,
      paginator.currentPagination,
      uiLabeler_labelColors.now
    )
  )

  uiState_selections.trigger {
    println(s"Current Selections: ${uiState_selections.now}")
  }

  uiState_selectedLabel.trigger {
    println(s"Active Label: ${uiState_selectedLabel.now}")
  }

  val triggerRepaginate = paginator.currentPage.triggerLater {

    uiRequestCycle(
      UIRequest(
        toUIState(),
        MenuAction(LabelAction.NavigateTo(paginator.currentPage.now))
      )
    )
  }
  def setupLabelChooser: RxModifier = Rx {
    val selectActiveLabel: SelectableButtons = radios("label-chooser".clazz)(
      (uiLabeler_labelColors().toList.zipWithIndex.map{ case ((lbl, clr), i) =>

        if(i==0) { uiState_selectedLabel() = Some(lbl) }

        selectableButton(
          lbl.fqn,
          (i==0),
          modifierSeq = (sty.btn_small +++ (backgroundColor:=clr.cssHash())),
          onclick = () => {uiState_selectedLabel() = Some(lbl)})
      }):_*
    )
    selectActiveLabel.render
  }



  val doMergeZones: Var[Boolean] = Var(false)
  val doDeleteZone: Var[Boolean] = Var(false)

  val rx2 = doDeleteZone.triggerLater{
    if (doDeleteZone.now) {
      println(s"Delete: uiRequestCycle()")
      uiRequestCycle(
        UIRequest(
          toUIState(),
          MenuAction(LabelAction.DeleteZone(ZoneID(0)))
        )
      ).map{ _ =>
        doDeleteZone() = false
      }
    }
  }

  val rx3 = doMergeZones.triggerLater{
    if (doMergeZones.now) {
      println("Merge: uiRequestCycle()")
      uiRequestCycle(
        UIRequest(
          toUIState(),
          MenuAction(LabelAction.MergeZones(List()))
        )
      ).map{_ =>
        doMergeZones() = false
      }
    }
  }


}

@JSExportTopLevel("WatrColors")
object WatrColors extends BasicClientDefs with UIUpdateCycle  {

  def doUIUpdateCycle(r: UIRequest): Future[UIResponse] = shell.uiRequest(r)

  object shell {
    import autowire._
    import UPicklers._

    val Client = new WebsideClient("shell")
    val api = Client[WatrShellApi]

    def uiRequest(r: UIRequest): Future[UIResponse] = {
      api.uiRequest(r).call()
    }

    def createDocumentLabeler(labelerRequest: LabelerIdentifier): Future[UIResponse] = {
      api.fetchDocumentLabeler(labelerRequest).call()
    }

  }

  def parseParams(): LabelerIdentifier = {
    val docId = param("doc").getOrElse { "" }
    val lt = param("lt").getOrElse { "" }
    val pg = param("pg").getOrElse("0").toInt
    DocumentLabelerIdentifier(DocumentID(docId), lt, Pagination(0, PageNum(pg), None))
  }


  var clientState: Option[ClientStateRx] = None

  def updateUIState(state: UIState): Unit = {
    clientState.foreach { currState =>
      currState.fromUIState(state)
    }
  }

  def getUIState(): UIState = clientState.map(_.toUIState())
    .getOrElse { sys.error("ui state not set") }

  val selectionRect: Var[Option[LTBounds]] = Var(None)
  // val mousePos: Var[Point] = Var(Point.zero)

  def selectionIndicator()(implicit co: Ctx.Owner): RxModifier = Rx {
    val curr = selectionRect()
    // val pt = mousePos()
    curr.map{ bbox =>
      <.span(bbox.prettyPrint)
    } getOrElse {
      <.span("<no-sel>")
    }
  }

  @JSExport
  def display(userName: String): Unit = {
    implicit val ctx: Ctx.Owner = Ctx.Owner.safe()
    val labelerId = parseParams()

    // TODO combine createDocumentLabeler with uiRequestCycle function
    shell.createDocumentLabeler(labelerId).foreach{ uiResponse =>

      val uiState = uiResponse.uiState
      val clientStateRx =  new ClientStateRx(uiState, uiRequestCycle(_))
      clientState = Option(clientStateRx)


      val selectorControls = SharedLayout.zoneSelectorControls(
        clientStateRx,
        clientStateRx.setupLabelChooser
      )


      val bodyContent =
        div("container-fluid".clazz)(
          div("row".clazz, pageStyles.controlClusterStyle)(
            div("col-lg-12".clazz)(
              selectorControls,
              clientStateRx.paginator.pageControls,
              selectionIndicator
            )
          ),
          div("row".clazz, pageStyles.labelerBodyStyle)(
            div("col-lg-12".clazz)(
              div(^.id:="d3-svg-container", pageStyles.canvasContainer)
            )
          )
        )

      withBootstrapNative {
        SharedLayout.pageSetup(Option(userName), bodyContent).render
      }

      d3.select("#d3-svg-container").append("svg")

      initDragSelectHandlers(
        bbox => selectionRect() = Some(bbox)
        // pt => mousePos() = pt
      )

      uiResponse.changes.foreach { mods =>
        joinResponseData(mods)
      }

    }

  }


}

