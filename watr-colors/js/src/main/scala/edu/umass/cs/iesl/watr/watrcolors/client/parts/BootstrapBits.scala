package edu.umass.cs.iesl.watr
package watrcolors
package client
package parts

import scaladget.stylesheet.{all => sty}
import scaladget.api._
import scaladget.api.Alert.ExtraButton
import scaladget.api.SelectableButtons.{CheckBoxSelection, RadioSelection}
import Popup._
// import Selector._

import scalatags.JsDom
import JsDom.{TypedTag, tags}
import JsDom.all._
import sty.{ctx => _, _}


import org.scalajs.dom.html.Div
import org.scalajs.dom.raw._

import scala.scalajs.js.annotation.JSExportTopLevel
import scaladget.tools.JsRxTags._
import scaladget.stylesheet.{all => sheet}
import sheet.{ctx => _, _}
import rx._
// import scaladget.mapping.bootstrap.Popover
// import org.scalajs.dom

import scala.scalajs.js

@JSExportTopLevel("demo.BootstrapBits")
object BootstrapBits {
  bstags =>

  def withBootstrapNative[T <: HTMLElement](f: => T): Unit = {
    org.scalajs.dom.document.body.appendChild(f)
    org.scalajs.dom.document.body.appendChild(
      <.script(`type` := "text/javascript", src := "/assets/js/bootstrap-native.min.js"))
  }

  implicit def formTagToNode(tt: HtmlTag): org.scalajs.dom.Node = tt.render

  type BS = TypedTag[_ <: HTMLElement]

  type ID = String

  def uuID: ID = java.util.UUID.randomUUID.toString

  type Input = ConcreteHtmlTag[org.scalajs.dom.raw.HTMLInputElement]

  def input(content: String = "") = tags.input(formControl, scalatags.JsDom.all.value := content)

  def inputGroup(modifierSeq: ModifierSeq = emptyMod) = div(modifierSeq +++ sheet.inputGroup)

  def inputGroupButton = span(toClass("input-group-btn"))

  def inputGroupAddon = span(toClass("input-group-addon"))

  val input_group_lg = "input-group-lg"

  def fileInputMultiple(todo: HTMLInputElement ⇒ Unit) = {
    lazy val input: HTMLInputElement = tags.input(id := "fileinput", `type` := "file", multiple)(onchange := { () ⇒
      todo(input)
    }).render
    input
  }

  def fileInput(todo: HTMLInputElement ⇒ Unit) = {
    lazy val input: HTMLInputElement = tags.input(id := "fileinput", `type` := "file")(onchange := { () ⇒
      todo(input)
    }).render
    input
  }


  // CHECKBOX
  def checkbox(default: Boolean) = tags.input(`type` := "checkbox", if (default) checked)

  def checkboxes(modifierSeq: ModifierSeq = emptyMod)(checkBoxes: SelectableButton*): SelectableButtons =
    new SelectableButtons(modifierSeq, CheckBoxSelection, checkBoxes)

  def radios(modifierSeq: ModifierSeq = emptyMod)(radioButtons: SelectableButton*): SelectableButtons = {

    val allActive = radioButtons.toSeq.filter {
      _.active.now
    }.size

    val buttons = {
      if (radioButtons.size > 0) {
        if (allActive != 1) radioButtons.head.copy(defaultActive = true) +: radioButtons.tail.map {
          _.copy(defaultActive = false)
        }
        else radioButtons
      } else radioButtons
    }

    new SelectableButtons(modifierSeq, RadioSelection, buttons)
  }

  def selectableButton(text: String, defaultActive: Boolean = false, modifierSeq: ModifierSeq = btn_default, onclick: () => Unit = () => {}) =
    SelectableButton(text, defaultActive, modifierSeq, onclick)

  trait Displayable {
    def name: String
  }

  // BUTTONS
  // default button with default button style and displaying a text
  def button(content: String, todo: () ⇒ Unit): TypedTag[HTMLButtonElement] = button(content, btn_default, todo)

  // displaying a text, with a button style and an action
  def button(content: String, buttonStyle: ModifierSeq, todo: () ⇒ Unit): TypedTag[HTMLButtonElement] =
    tags.button(buttonStyle, `type` := "button", onclick := { () ⇒ todo() })(content)

  // displaying an HTHMElement, with a a button style and an action
  def button(content: TypedTag[HTMLElement], buttonStyle: ModifierSeq, todo: () ⇒ Unit): TypedTag[HTMLButtonElement] =
    button("", buttonStyle, todo)(content)

  // displaying a text with a button style and a glyphicon
  def button(text: String = "", buttonStyle: ModifierSeq = btn_default, glyphicon: ModifierSeq = Seq(), todo: () ⇒ Unit = () => {}): TypedTag[HTMLButtonElement] = {
    val iconStyle = if (text.isEmpty) paddingAll(top = 3, bottom = 3) else sheet.marginLeft(5)
    tags.button(btn +++ buttonStyle, `type` := "button", onclick := { () ⇒ todo() })(
      span(
        span(glyphicon +++ iconStyle),
        span(s" $text")
      )
    )
  }

  // Clickable span containing a glyphicon and a text
  def glyphSpan(glyphicon: ModifierSeq, onclickAction: () ⇒ Unit = () ⇒ {}, text: String = ""): TypedTag[HTMLSpanElement] =
    span(glyphicon +++ pointer, aria.hidden := "true", onclick := { () ⇒ onclickAction() })(text)


  // Close buttons
  def closeButton(dataDismiss: String, todo: () => Unit = () => {}) = button("", todo)(toClass("close"), aria.label := "Close", data.dismiss := dataDismiss)(
    span(aria.hidden := true)(raw("&#215"))
  )

  //Label decorators to set the label size
  implicit class TypedTagLabel(lab: TypedTag[HTMLLabelElement]) {
    def size1(modifierSeq: ModifierSeq = emptyMod) = h1(modifierSeq)(lab)

    def size2(modifierSeq: ModifierSeq = emptyMod) = h2(modifierSeq)(lab)

    def size3(modifierSeq: ModifierSeq = emptyMod) = h3(modifierSeq)(lab)

    def size4(modifierSeq: ModifierSeq = emptyMod) = h4(modifierSeq)(lab)

    def size5(modifierSeq: ModifierSeq = emptyMod) = h5(modifierSeq)(lab)

    def size6(modifierSeq: ModifierSeq = emptyMod) = h6(modifierSeq)(lab)
  }


  // PROGRESS BAR
  def progressBar(barMessage: String, ratio: Int): TypedTag[HTMLDivElement] =
    div(progress)(
      div(sheet.progressBar)(width := ratio.toString() + "%")(
        barMessage
      )
    )


  // BADGE
  def badge(badgeValue: String, badgeStyle: ModifierSeq = emptyMod) = span(toClass("badge") +++ badgeStyle +++ sheet.marginLeft(4))(badgeValue)

  //BUTTON GROUP
  def buttonGroup(mod: ModifierSeq = emptyMod) = div(mod +++ btnGroup)

  def buttonToolBar = div(btnToolbar)(role := "toolbar")

  //MODAL
  type ModalID = String


  object ModalDialog {
    def apply(modifierSeq: ModifierSeq = emptyMod,
              onopen: () => Unit = () => {},
              onclose: () => Unit = () => {}) = new ModalDialog(modifierSeq, onopen, onclose)

    val headerDialogShell = div(modalHeader +++ modalInfo)

    val bodyDialogShell = div(modalBody)

    val footerDialogShell = div(modalFooter)

    def closeButton(modalDialog: ModalDialog, modifierSeq: ModifierSeq, content: String) =
      tags.button(modifierSeq, content, onclick := { () =>
        modalDialog.hide
      })
  }

  class ModalDialog(modifierSeq: ModifierSeq, onopen: () => Unit, onclose: () => Unit) {

    val headerDialog: Var[TypedTag[_]] = Var(tags.div)
    val bodyDialog: Var[TypedTag[_]] = Var(tags.div)
    val footerDialog: Var[TypedTag[_]] = Var(tags.div)

    lazy val dialog = {
      val d = div(modal +++ fade)(`class` := "modal fade",
        tabindex := "-1", role := "dialog", aria.hidden := "true")(
        div(sheet.modalDialog +++ modifierSeq)(
          div(modalContent)(
            headerDialog.now,
            bodyDialog.now,
            footerDialog.now
          )
        )
      ).render

      org.scalajs.dom.document.body.appendChild(d)
      d
    }

    lazy val modalMapping = new scaladget.mapping.bootstrap.Modal(dialog)

    def header(hDialog: TypedTag[_]): Unit = headerDialog() = ModalDialog.headerDialogShell(hDialog)

    def body(bDialog: TypedTag[_]): Unit = bodyDialog() = ModalDialog.bodyDialogShell(bDialog)

    def footer(fDialog: TypedTag[_]): Unit = footerDialog() = ModalDialog.footerDialogShell(fDialog)

    def show() = {
      modalMapping.show
      onopen()
    }

    def hide() = {
      modalMapping.hide
      onclose()
    }

    def isVisible() = dialog.className.contains(" in")
  }

  sealed trait NavEntry[T <: HTMLElement] {
    def render: TypedTag[HTMLElement]
  }

  sealed trait NavActive {
    def active: Var[Boolean]
  }

  // NAVS
  case class NavSpan[T <: HTMLElement](
    contentDiv: TypedTag[T]
  ) extends NavEntry[T] {

    override val render: TypedTag[HTMLElement] =
      <.li(
        <.span(lineHeight := "35px")(
          contentDiv
        )
      )
  }

  class NavInfo[T <: HTMLElement](
    contentDiv: T,
    extraRenderPair: Seq[Modifier] = Seq()
  ) extends NavEntry[T] {


    override val render = li(
      tags.span(
        lineHeight := "35px",
        contentDiv
      )
    )(extraRenderPair: _*)
  }

  def navInfo[T <: HTMLElement](
    content: T,
    extraRenderPair: Seq[Modifier] = Seq()
  ) = { new NavInfo(content, extraRenderPair) }

  def navInfoString(
    content: String,
    extraRenderPair: Seq[Modifier] = Seq()
  ) = { navInfo(span(content).render, extraRenderPair) }


  // class NavLink[T <: HTMLElement](
  //   anchorElem: T,
  //   extraRenderPair: Seq[Modifier] = Seq()
  // ) extends NavEntry[T] {
  //   override val render = li(
  //     anchorElem(lineHeight := "35px"))
  //   )(extraRenderPair: _*)
  // }

  // def navLink[T0 <: HTMLElement, T <: HTMLAnchorElement](
  //   label: T0,
  //   uri: String,
  //   extraRenderPair: Seq[Modifier] = Seq()
  // ): NavLink[T] = { new NavLink(uri, extraRenderPair) }

  // def navAnchor(uri: String): HTMLAnchorElement = {
  //   tags.a(href := uri)(label)
  // }

  class NavItem[T <: HTMLElement](
    contentDiv: T,
    val todo: () ⇒ Unit = () ⇒ {},
    extraRenderPair: Seq[Modifier] = Seq(),
    activeDefault: Boolean = false
  ) extends NavEntry[T] with NavActive {

    implicit val ctx: Ctx.Owner = Ctx.Owner.safe()

    val active: Var[Boolean] = Var(activeDefault)

    override val render = li(
      tags.a(href := "#",
        lineHeight := "35px",
        onclick := { () =>
          todo()
          false
        })(
        contentDiv,
          Rx {
            if (active()) span(toClass("sr-only"))("(current)")
            else span()
        }),
      `class` := Rx {
        if (active()) "active" else ""
      }
    )(extraRenderPair: _*)
  }



  def navItem[T <: HTMLElement](
    content: T,
    todo: () => Unit = () => {},
    extraRenderPair: Seq[Modifier] = Seq(),
    activeDefault: Boolean = false) = {
    new NavItem(content, todo, extraRenderPair, activeDefault)
  }

  def stringNavItem(content: String, todo: () ⇒ Unit = () ⇒ {}, activeDefault: Boolean = false): NavItem[HTMLElement] =
    navItem(span(content).render, todo, activeDefault = activeDefault)

  def navBar(classPair: ModifierSeq, contents: NavEntry[_ <: HTMLElement]*): TypedTag[HTMLElement] = {

    <.nav(navbar +++ navbar_default +++ classPair)(
      div(toClass("container-fluid"))(
        div(toClass("collapse") +++ navbar_collapse)(
          ul(nav +++ navbar_nav)(
            contents.map {

              case c: NavActive =>
                c.render(scalatags.JsDom.attrs.onclick := { () ⇒
                  contents.collect {
                    case n: NavActive => n.active() = false
                  }
                  c.active() = true
                })

              case c  =>
                c.render
            }: _*)
        )
      )
    )
  }
  def navBarLeftRight(
    classPair: ModifierSeq,
    contents: Seq[NavEntry[_ <: HTMLElement]],
    contentsR: Seq[NavEntry[_ <: HTMLElement]]
  ): TypedTag[HTMLElement] = {

    <.nav(navbar +++ navbar_default +++ classPair)(
      div(toClass("container-fluid"))(
        div(toClass("collapse") +++ navbar_collapse)(
          ul(nav +++ navbar_nav)(
            contents.map {

              case c: NavActive =>
                c.render(scalatags.JsDom.attrs.onclick := { () ⇒
                  contents.collect {
                    case n: NavActive => n.active() = false
                  }
                  c.active() = true
                })

              case c  =>
                c.render
            }: _*
          ),
          ul(nav +++ navbar_nav +++ navbar_right)(
            contentsR.map {

              case c: NavActive =>
                c.render(scalatags.JsDom.attrs.onclick := { () ⇒
                  contents.collect {
                    case n: NavActive => n.active() = false
                  }
                  c.active() = true
                })

              case c  =>
                c.render
            }: _*)
        )
      )
    )
  }

  // def navbarLeft(classPair: ModifierSeq, contents: NavEntry[_ <: HTMLElement]*): TypedTag[HTMLElement] = {
  //   navbar(classPair, contents:_*)
  // }

  // def navbarRight(classPair: ModifierSeq, contents: NavEntry[_ <: HTMLElement]*): TypedTag[HTMLElement] = {
  //   navbar(classPair +++ navbar_right, contents:_*)
  // }


  // Nav pills
  case class NavPill(name: String, badge: Option[Int], todo: () => Unit)


  // POUPUS, TOOLTIPS
  class Popover(element: TypedTag[org.scalajs.dom.raw.HTMLElement],
    text: String,
    position: PopupPosition = Bottom,
    trigger: PopupType = HoverPopup,
    title: Option[String] = None,
    dismissible: Boolean = false) {

    lazy val render = {
      val p = element(
        data("toggle") := "popover",
        data("content") := text,
        data("placement") := position.value,
        data("trigger") := {
          trigger match {
            case ClickPopup => "click"
            case _ => "hover"
          }
        },
        title match {
          case Some(t: String) => data("title") := t
          case _ =>
        },
        data("dismissible") := {
          dismissible match {
            case true => "true"
            case _ => "false"
          }
        },
        trigger match {
          case ClickPopup => onclick := { () => show }
          case _ => onmouseover := { () => hide }
        }
      ).render

      org.scalajs.dom.document.body.appendChild(p)
      p
    }

    lazy val popover: scaladget.mapping.bootstrap.Popover = {
      val p = new scaladget.mapping.bootstrap.Popover(render)
      show
      p
    }

    def show() = {
      popover.show
      //onopen()
    }

    def hide() = {
      popover.hide
    }
  }

  object Tooltip {
    def cleanAll() = {
      val list = org.scalajs.dom.document.getElementsByClassName("tooltip")
      for (nodeIndex ← 0 to (list.length - 1)) {
        val element = list(nodeIndex)
        if (element != js.undefined) element.parentNode.removeChild(element)
      }
    }
  }

  class Tooltip(element: TypedTag[org.scalajs.dom.raw.HTMLElement],
                text: String,
                position: PopupPosition = Bottom,
                condition: () => Boolean = () => true) {

    val elementRender = {
      if (condition())
        element(
          data("placement") := position.value,
          data("toggle") := "tooltip",
          data("original-title") := text
        )
      else element
    }.render

    elementRender.onmouseover = (e: Event)=> {
      tooltip
    }

    lazy val tooltip = new scaladget.mapping.bootstrap.Tooltip(elementRender)

    def render() = elementRender
    def hide() = tooltip.hide
  }


  implicit class PopableTypedTag(element: TypedTag[org.scalajs.dom.raw.HTMLElement]) {

    def tooltip(text: String,
                position: PopupPosition = Bottom,
                condition: () => Boolean = () => true) = {
      new Tooltip(element, text, position, condition).render
    }

    def popover(text: String,
                position: PopupPosition = Bottom,
                trigger: PopupType = HoverPopup,
                title: Option[String] = None,
                dismissible: Boolean = false
               ) = {
      new Popover(element, text, position, trigger, title, dismissible).render
    }
  }


  //DROPDOWN
  implicit class SelectableSeqWithStyle[T](s: Seq[T]) {
    def options(defaultIndex: Int = 0,
                key: ModifierSeq = emptyMod,
                naming: T => String,
                onclose: () => Unit = () => {},
                onclickExtra: () ⇒ Unit = () ⇒ {},
                decorations: Map[T, ModifierSeq] = Map(),
                fixedTitle: Option[String] = None) = Selector.options(s, defaultIndex, key, naming, onclose, onclickExtra, decorations, fixedTitle)

  }

  implicit class SelectableTypedTag[T <: HTMLElement](tt: TypedTag[T]) {


    def dropdown(buttonText: String = "",
                 buttonModifierSeq: ModifierSeq = emptyMod,
                 buttonIcon: ModifierSeq = emptyMod,
                 allModifierSeq: ModifierSeq = emptyMod,
                 dropdownModifierSeq: ModifierSeq = emptyMod,
                 onclose: () => Unit = () => {}) = Selector.dropdown(tt, buttonText, buttonIcon, buttonModifierSeq, allModifierSeq, dropdownModifierSeq, onclose)

    def dropdownWithTrigger(trigger: TypedTag[_ <: HTMLElement],
                            allModifierSeq: ModifierSeq = emptyMod,
                            dropdownModifierSeq: ModifierSeq = emptyMod,
                            onclose: () => Unit = () => {}) = Selector.dropdown(tt, trigger, allModifierSeq, dropdownModifierSeq, onclose)

  }


  // JUMBOTRON
  def jumbotron(modifiers: ModifierSeq) =
    div(container +++ themeShowcase)(role := "main")(
      div(sheet.jumbotron)(
        p(modifiers)
      )
    )


  // SCROLL TEXT AREA
  // Define text area, which scrolling can be automated in function of content change:
  object ScrollableTextArea {

    implicit val ctx: Ctx.Owner = Ctx.Owner.safe()

    sealed trait AutoScroll

    //The scroll is always in top position
    object TopScroll extends AutoScroll

    //The scroll is always in bottom position
    object BottomScroll extends AutoScroll

    //The scroll is not set and remains at scrollHeight
    case class NoScroll(scrollHeight: Int) extends AutoScroll

  }


  import ScrollableTextArea._

  // TEXT AREA
  def textArea(nbRow: Int) = tags.textarea(formControl, rows := nbRow)

  def scrollableText(text: String = "", scrollMode: AutoScroll = TopScroll): ScrollableText = ScrollableText(text, scrollMode)

  def scrollableDiv(element: Div = div.render, scrollMode: AutoScroll = BottomScroll): ScrollableDiv = ScrollableDiv(element, scrollMode)

  trait Scrollable {

    def scrollMode: Var[AutoScroll]

    def sRender: HTMLElement

    def view: HTMLElement = div(sRender).render

    def setScrollMode() = {
      val scrollHeight = sRender.scrollHeight
      val scrollTop = sRender.scrollTop.toInt
      scrollMode() =
        if ((scrollTop + sRender.offsetHeight.toInt) > scrollHeight) BottomScroll
        else NoScroll(scrollTop)
    }

    def doScroll() = scrollMode.now match {
      case BottomScroll ⇒ sRender.scrollTop = sRender.scrollHeight.toDouble
      case n: NoScroll ⇒ sRender.scrollTop = n.scrollHeight.toDouble
      case _ ⇒
    }
  }

  case class ScrollableText(initText: String, _scrollMode: AutoScroll) extends Scrollable {
    val scrollMode: Var[AutoScroll] = Var(_scrollMode)
    val tA = textArea(20)(initText, spellcheck := false, onscroll := { (e: Event) ⇒ setScrollMode })
    val sRender = tA.render

    def setContent(out: String) = {
      sRender.value = out
    }
  }

  case class ScrollableDiv(_element: Div, _scrollMode: AutoScroll)(implicit ctx: Ctx.Owner) extends Scrollable {
    val scrollMode: Var[AutoScroll] = Var(_scrollMode)
    val child: Var[Node] = Var(div)

    val tA = div(height := "100%")(Rx {
      child()
    }, onscroll := { (e: Event) ⇒ setScrollMode })

    def setChild(d: Div) = child() = d

    val sRender = tA.render
  }


  // LABELED FIELD
  case class ElementGroup(e1: TypedTag[HTMLElement], e2: TypedTag[HTMLElement])

  def inLineForm(elements: ElementGroup*) = {
    val ID = uuID
    form(formInline)(
      for {
        e <- elements
      } yield {
        div(formGroup)(
          e.e1(`for` := ID, sheet.marginLeft(5)),
          e.e2(formControl, sheet.marginLeft(5), id := ID)
        )
      }
    )
  }

  def group(e1: TypedTag[HTMLElement], e2: TypedTag[HTMLElement]) = ElementGroup(e1, e2)


  // PANELS
  def panel(bodyContent: String = "", heading: Option[String] = None) =
    div(sheet.panel +++ panelDefault)(
      heading.map { h => div(panelHeading)(h) }.getOrElse(div),
      div(panelBody)(bodyContent)
    )


  // ALERTS
  def successAlerts(title: String, content: Seq[String], triggerCondition: Rx.Dynamic[Boolean] = Rx(true), todocancel: () ⇒ Unit = () => {})(otherButtons: ExtraButton*) =
    new Alert(alert_success, title, content, triggerCondition, todocancel)(otherButtons: _*).render

  def infoAlerts(title: String, content: Seq[String], triggerCondition: Rx.Dynamic[Boolean] = Rx(true), todocancel: () ⇒ Unit = () => {})(otherButtons: ExtraButton*) =
    new Alert(alert_info, title, content, triggerCondition, todocancel)(otherButtons: _*).render

  def warningAlerts(title: String, content: Seq[String], triggerCondition: Rx.Dynamic[Boolean] = Rx(true), todocancel: () ⇒ Unit = () => {})(otherButtons: ExtraButton*) =
    new Alert(alert_warning, title, content, triggerCondition, todocancel)(otherButtons: _*).render

  def dangerAlerts(title: String, content: Seq[String], triggerCondition: Rx.Dynamic[Boolean] = Rx(true), todocancel: () ⇒ Unit = () => {})(otherButtons: ExtraButton*) =
    new Alert(alert_danger, title, content, triggerCondition, todocancel)(otherButtons: _*).render

  def successAlert(title: String, content: String, triggerCondition: Rx.Dynamic[Boolean] = Rx(true), todocancel: () ⇒ Unit = () => {})(otherButtons: ExtraButton*) =
    successAlerts(title, Seq(content), triggerCondition, todocancel)(otherButtons.toSeq: _*)

  def infoAlert(title: String, content: String, triggerCondition: Rx.Dynamic[Boolean] = Rx(true), todocancel: () ⇒ Unit = () => {})(otherButtons: ExtraButton*) =
    infoAlerts(title, Seq(content), triggerCondition, todocancel)(otherButtons.toSeq: _*)

  def warningAlert(title: String, content: String, triggerCondition: Rx.Dynamic[Boolean] = Rx(true), todocancel: () ⇒ Unit = () => {})(otherButtons: ExtraButton*) =
    warningAlerts(title, Seq(content), triggerCondition, todocancel)(otherButtons.toSeq: _*)

  def dangerAlert(title: String, content: String, triggerCondition: Rx.Dynamic[Boolean] = Rx(true), todocancel: () ⇒ Unit = () => {})(otherButtons: ExtraButton*) =
    dangerAlerts(title, Seq(content), triggerCondition, todocancel)(otherButtons.toSeq: _*)


  implicit class TagCollapserOnClick[S <: TypedTag[HTMLElement]](triggerTag: S) {
    def expandOnclick[T <: TypedTag[HTMLElement]](inner: T) = {
      val collapser = new Collapser[T](inner)
      val triggerTagRender = triggerTag.render

      triggerTagRender.onclick = (e: MouseEvent) => {
        collapser.switch
      }

      div(
        triggerTagRender,
        collapser.tag
      )
    }
  }

  implicit class TagCollapserDynamicOnCondition(triggerCondition: Rx.Dynamic[Boolean]) {
    def expand[T <: TypedTag[HTMLElement]](inner: T) = {
      val collapser = new Collapser[T](inner, triggerCondition.now)
      Rx {
        collapser.switchTo(triggerCondition())
      }
      collapser.tag
    }
  }

  implicit class TagCollapserVarOnCondition(triggerCondition: Var[Boolean]) {
    def expand[T <: TypedTag[HTMLElement]](inner: T) = {
      val collapser = new Collapser[T](inner, triggerCondition.now)
      Rx {
        collapser.switchTo(triggerCondition())
      }
      collapser.tag
    }
  }

  // COLLAPSERS
  class Collapser[T <: TypedTag[HTMLElement]](innerTag: T, initExpand: Boolean = false) {
    val expanded = Var(initExpand)

    private val innerTagRender = innerTag.render

    val tag = div(collapseTransition)(innerTagRender).render


    private def setHeight() = {
      tag.style.height = {
        if (expanded.now) (innerTagRender.clientHeight + 15).toString
        else "0"
      }
    }

    def switch() = {
      expanded() = !expanded.now
      setHeight
    }

    def switchTo(b: Boolean) = {
      expanded() = b
      setHeight
    }

  }

  // TABS
  case class Tab(title: String, content: BS, active: Boolean) {
    val tabID = "t" + uuID.split('-').head
    val refID = "r" + uuID.split('-').head

    def activeClass = if (active) (ms("active"), ms("active in")) else (ms(""), ms(""))
  }

  case class Tabs(navStyle: NavStyle, tabs: Seq[Tab] = Seq()) {

    def add(title: String, content: BS, active: Boolean = false): Tabs = add(Tab(title, content, active))

    def add(tab: Tab): Tabs = copy(tabs = tabs :+ tab)


    lazy val render = {

      div(
        ul(navStyle, tab_list_role)(
          tabs.map { t =>
            li(presentation_role +++ t.activeClass._1)(
              a(id := t.tabID, href := s"#${t.refID}", tab_role, data("toggle") := "tab", data("height") := true, aria.controls := t.refID)(t.title)
            )
          }),
        div(tab_content +++ sheet.paddingTop(10))(
          tabs.map { t =>
            div(id := t.refID, tab_pane +++ fade +++ t.activeClass._2, tab_panel_role, aria.labelledby := t.tabID)(t.content)
          }
        )
      ).render
    }
  }

  def tabs(navStyle: NavStyle) = new Tabs(navStyle)


  // EXCLUSIVE BUTTON GROUPS
  def exclusiveButtonGroup(style: ModifierSeq, defaultStyle: ModifierSeq = btn_default, selectionStyle: ModifierSeq = btn_default)(buttons: ExclusiveButton*) = new ExclusiveGroup(style, defaultStyle, selectionStyle, buttons)

  def twoStatesGlyphButton(glyph1: ModifierSeq,
                           glyph2: ModifierSeq,
                           todo1: () ⇒ Unit,
                           todo2: () ⇒ Unit,
                           preGlyph: ModifierSeq = Seq()
                          ) = TwoStatesGlyphButton(glyph1, glyph2, todo1, todo2, preGlyph)

  def twoStatesSpan(glyph1: ModifierSeq,
                    glyph2: ModifierSeq,
                    todo1: () ⇒ Unit,
                    todo2: () ⇒ Unit,
                    preString: String,
                    buttonStyle: ModifierSeq = emptyMod
                   ) = TwoStatesSpan(glyph1, glyph2, todo1, todo2, preString, buttonStyle)

  sealed trait ExclusiveButton {
    def action: () ⇒ Unit
  }

  trait ExclusiveGlyphButton extends ExclusiveButton {
    def glyph: Glyphicon
  }

  trait ExclusiveStringButton extends ExclusiveButton {
    def title: String
  }

  sealed trait TwoStates extends ExclusiveButton

  case class TwoStatesGlyphButton(glyph: ModifierSeq,
                                  glyph2: ModifierSeq,
                                  action: () ⇒ Unit,
                                  action2: () ⇒ Unit,
                                  preGlyph: ModifierSeq
                                 ) extends TwoStates {
    val cssglyph = glyph +++ sheet.paddingLeft(3)

    lazy val div = {
      button("", preGlyph, cssglyph, action)
    }
  }

  case class TwoStatesSpan(glyph: ModifierSeq,
                           glyph2: ModifierSeq,
                           action: () ⇒ Unit,
                           action2: () ⇒ Unit,
                           preString: String,
                           buttonStyle: ModifierSeq = emptyMod
                          ) extends TwoStates {
    val cssglyph = glyph +++ sheet.paddingLeft(3)

    lazy val cssbutton: ModifierSeq = Seq(
      sheet.paddingTop(8),
      border := "none"
    )

    lazy val div = button(preString, buttonStyle +++ cssbutton +++ pointer, action)(
      span(cssglyph)
    )

  }

  object ExclusiveButton {
    def string(t: String, a: () ⇒ Unit) = new ExclusiveStringButton {
      def title = t

      def action = a
    }

    def glyph(g: Glyphicon, a: () ⇒ Unit) = new ExclusiveGlyphButton {
      def glyph = g

      def action = a
    }

    def twoGlyphButtonStates(
                              glyph1: ModifierSeq,
                              glyph2: ModifierSeq,
                              todo1: () ⇒ Unit,
                              todo2: () ⇒ Unit,
                              preGlyph: ModifierSeq
                            ) = twoStatesGlyphButton(glyph1, glyph2, todo1, todo2, preGlyph)

    def twoGlyphSpan(
                      glyph1: ModifierSeq,
                      glyph2: ModifierSeq,
                      todo1: () ⇒ Unit,
                      todo2: () ⇒ Unit,
                      preString: String,
                      buttonStyle: ModifierSeq = emptyMod
                    ) = twoStatesSpan(glyph1, glyph2, todo1, todo2, preString, buttonStyle)
  }

  class ExclusiveGroup(style: ModifierSeq,
                       defaultStyle: ModifierSeq,
                       selectionStyle: ModifierSeq,
                       buttons: Seq[ExclusiveButton]) {

    implicit val ctx: Ctx.Owner = Ctx.Owner.safe()

    val selected = Var(buttons.head)
    val selectedAgain = Var(false)

    def buttonBackground(b: ExclusiveButton) = (if (b == selected.now) btn +++ selectionStyle else btn +++ defaultStyle)

    def glyphButtonBackground(b: ExclusiveButton) = buttonBackground(b) +++ twoGlyphButton

    def stringButtonBackground(b: ExclusiveButton) = buttonBackground(b) +++ stringButton

    def glyphForTwoStates(ts: TwoStates, mod: ModifierSeq) = (ts == selected.now, mod, emptyMod)

    val div: Modifier = Rx {
      selected()
      tags.div(style +++ btnGroup)(
        for (b ← buttons) yield {
          b match {
            case s: ExclusiveStringButton ⇒ button(s.title, stringButtonBackground(s) +++ stringInGroup, action(b, s.action))
            case g: ExclusiveGlyphButton ⇒ button("", glyphButtonBackground(g), g.glyph, action(b, g.action))
            case ts: TwoStatesGlyphButton ⇒
              if (selectedAgain()) twoStatesGlyphButton(glyphForTwoStates(ts, ts.glyph2), ts.glyph, action(ts, ts.action2), action(ts, ts.action), glyphButtonBackground(ts) +++ ts.preGlyph).div
              else twoStatesGlyphButton(glyphForTwoStates(ts, ts.glyph), ts.glyph2, action(ts, ts.action), action(ts, ts.action2), glyphButtonBackground(ts) +++ ts.preGlyph).div
            case ts: TwoStatesSpan ⇒
              if (selectedAgain()) twoStatesSpan(glyphForTwoStates(ts, ts.glyph2), ts.glyph, action(ts, ts.action2), action(ts, ts.action), ts.preString, glyphButtonBackground(ts)).div
              else twoStatesSpan(glyphForTwoStates(ts, ts.glyph), ts.glyph2, action(ts, ts.action), action(ts, ts.action2), ts.preString, glyphButtonBackground(ts)).div
          }
        }
      )
    }

    private def action(b: ExclusiveButton, a: () ⇒ Unit) = () ⇒ {
      selectedAgain() = if (b == selected.now) !selectedAgain.now else false
      selected() = b
      a()
    }


    def reset() = selected() = buttons.head
  }


  // FORMS

  trait FormTag[+T <: HTMLElement] {
    def tag: T
  }

  trait LabeledFormTag[T <: HTMLElement] extends FormTag[T] {
    def label: TypedTag[HTMLLabelElement]
  }

  /*
  implicit def modifierToFormTag(m: Modifier): FormTag = new FormTag {
    val tag: T = m
  }*/
  implicit def htmlElementToFormTag[T <: HTMLElement](t: T): FormTag[T] = new FormTag[T] {
    val tag: T = t
  }


  implicit class LabelForModifiers[T <: HTMLElement](m: T) {
    def withLabel(title: String, labelStyle: ModifierSeq = emptyMod): LabeledFormTag[T] = new LabeledFormTag[T] {
      val label: TypedTag[HTMLLabelElement] = tags.label(title)(labelStyle +++ (sheet.paddingRight(5)))

      val tag: T = m
    }
  }

  private def insideForm[T <: HTMLElement](formTags: FormTag[T]*) =
    for {
      ft <- formTags
    } yield {
      div(formGroup +++ sheet.paddingRight(5))(
        ft match {
          case lft: LabeledFormTag[T] => lft.label
          case _ =>
        },
        ft.tag)
    }


  def vForm[T <: HTMLElement](formTags: FormTag[T]*): TypedTag[HTMLDivElement] = vForm(emptyMod)(formTags.toSeq: _*)

  def vForm[T <: HTMLElement](modifierSeq: ModifierSeq)(formTags: FormTag[T]*): TypedTag[HTMLDivElement] =
    div(modifierSeq +++ formVertical)(insideForm(formTags: _*))


  def hForm[T <: HTMLElement](formTags: FormTag[T]*): TypedTag[HTMLFormElement] = hForm(emptyMod)(formTags.toSeq: _*)

  def hForm[T <: HTMLElement](modifierSeq: ModifierSeq)(formTags: FormTag[T]*): TypedTag[HTMLFormElement] = {
    form(formInline +++ modifierSeq)(insideForm(formTags: _*))
  }

  //ACCORDION

  case class AccordionItem(title: String, content: BS)

  def accordionItem(title: String, content: BS) = AccordionItem(title, content)

  def accordion(accordionItems: AccordionItem*): TypedTag[HTMLDivElement] = accordion(emptyMod)(emptyMod)(accordionItems.toSeq: _*)

  def accordion(modifierSeq: ModifierSeq)(titleModifierSeq: ModifierSeq)(accordionItems: AccordionItem*): TypedTag[HTMLDivElement] = {
    val accordionID = uuID
    div(
      modifierSeq,
     // id := accordionID,
      role := "tablist",
      aria.multiselectable := "true",
      ms("panel-group"))(
      for {
        item <- accordionItems.toSeq
      } yield {
        val collapseID = uuID
        div(sheet.panel +++ sheet.panelDefault)(
          div(
            panelHeading,
            role := "tab"
          )(a(
            data("toggle") := "collapse",
       //     data("parent") := s"#$accordionID",
            href := collapseID,
            aria.expanded := true,
            aria.controls := collapseID,
            ms("collapsed"),
            display := "block",
            width := "100%",
            height := 25,
            titleModifierSeq
          )(item.title)),
          div(
            id := collapseID,
            ms("panel-collapse collapse"),
            role := "tabpanel",
            aria.expanded := false
          )(div(
            panelBody,
            item.content))
        )
      }
    )
  }

}
