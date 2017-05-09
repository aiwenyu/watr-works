package edu.umass.cs.iesl.watr
package watrcolors
package client
package pages

import parts._
import wiring._

import scaladget.stylesheet.{all => sty}
import sty._

import scalatags.JsDom.all._

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.annotation._
import scala.concurrent.Future

import rx._
import scaladget.tools.JsRxTags._


@JSExportTopLevel("BrowseCorpus")
object BrowseCorpus extends BaseClientDefs {
  import BootstrapBits._


  val buttonStyle: ModifierSeq = Seq(
    sty.marginAll(right = 5, top = 5)
  )

  val docList: Var[Seq[DocumentEntry]] = Var(List())
  var currDocStart = Var(0)
  var docCount = Var(0)

  def prevPage() = {
    currDocStart() = currDocStart.now - 20
    currDocStart() = math.max(0, currDocStart.now)

    server.listDocuments(20, currDocStart.now).foreach { docs =>
      docList() = docs
    }
  }
  def nextPage() = {
    server.listDocuments(20, currDocStart.now).foreach { docs =>
      docList() = docs
    }
    currDocStart() = currDocStart.now + 20
  }

  val leftGlyph = glyph_chevron_left
  val rightGlyph = glyph_chevron_right


  def docPagination(implicit c : Ctx.Owner): RxHtmlTag = Rx {
    span(
      span(s"Displaying ${currDocStart()}-${currDocStart()+20} of ${docCount()} "),
      span(btnGroup,
        glyphSpan(leftGlyph, () => prevPage()),
        glyphSpan(rightGlyph, () => nextPage())
      )
    )
  }


  def documentLister(implicit c: Ctx.Owner): RxHtmlTags = Rx {

    for {entry <- docList()} yield {
      val docId = entry.urlStr
      val t = s"/label?doc=${docId}&lt=zzz"
      li(
        span(
          a(
            entry.urlStr,
            cursor := "pointer",
            href := t
          ) (
            entry.id.toString()
          )
        )
      )
    }
  }



  @JSExport
  def display(userName: String): Unit = {

    implicit val ctx: Ctx.Owner = Ctx.Owner.safe()

    server.documentCount()
      .foreach { c => docCount() = c }

    val bodyContent =
      div("container-fluid".clazz)(
        div("row".clazz, pageStyles.controlClusterStyle)(
          div("col-lg-12".clazz)(
            docPagination
          )
        ),
        div("row".clazz)(
          div("col-lg-12".clazz)(
            ul(documentLister)
          )
        )
      )

    withBootstrapNative {
      SharedLayout.pageSetup(Option(userName), bodyContent).render
    }

  }

  object server extends BrowseCorpusApi {
    import autowire._
    import UPicklers._

    val Client = new WebsideClient("browse")
    val api = Client[BrowseCorpusApi]

    def listLabelers(n: Int, skip: Int): Future[Seq[LabelerEntry]] = {
      api.listLabelers(n, skip).call()
    }

    def listDocuments(n: Int, skip: Int): Future[Seq[DocumentEntry]] = {
      println(s"BrowseCorpus: listDocuments()")
      api.listDocuments(n, skip).call()
    }

    def documentCount(): Future[Int] = {
      api.documentCount().call()
    }

  }


}
