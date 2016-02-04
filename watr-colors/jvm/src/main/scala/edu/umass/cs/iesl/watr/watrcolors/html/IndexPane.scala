package edu.umass.cs.iesl.watr
package watrcolors
package html

import scalatags.Text.all._


object CorpusExplorerPane {

  def init()  = {
    <.div(
      <.div()("Explore")(
        <.span(^.id:="currfile")
      )
    )
  }

}

import scalatags.stylesheet.{CascadingStyleSheet, StyleSheet, StyleSheetTags, Sheet, Selector}
object SvgOverviewPane {

  val SvgStyles = Sheet[SvgStyles]

  trait SvgStyles extends CascadingStyleSheet {

    def  overlayContainer = cls(
      position.relative
    )

    def svgImage = cls(
      position.absolute,
      left:="0",
      top:="0",
      border := "1ps solid black",
      backgroundColor := "ivory"
    )

    def fabricCanvas = cls(
      position.absolute,
      left:="0",
      top:="0"
    )


  }
  //     <style>
  //         #overlay-container {
  //         position: relative;
  //         }

  //         #svg-image {
  //         position: absolute;
  //         left: 0;
  //         top: 0;
  //         border: 1px solid black;
  //         background-color: ivory;
  //         }

  //         #fabric-canvas {
  //         position: absolute;
  //         left: 0;
  //         top: 0;
  //         }
  //     </style>

  def init(filename: String)  = {
    <.div()(
      <.style(^.`type`:="text/css",
        SvgStyles.styleSheetText
      ),
      <.form(^.`onsubmit`:="return false;")(
        <.input(^.`id`:="save-button", ^.`type`:="button", ^.`value`:="Save", ^.`title`:="Save the document's changes."),
        <.input(^.`id`:="revert-button", ^.`type`:="button", ^.`value`:="Revert", ^.`title`:="Discard changes and reload the document."),
        <.select(^.`id`:="type-select", ^.`title`:="Select the type to use for the next new rectangle.")(
          <.option(^.`value`:="title")("Title"),
          <.option(^.`value`:="abstract")("Abstract"),
          <.option(^.`value`:="author")("Author")
        ),
        <.input(^.`id`:="add-rect-button", ^.`type`:="button", ^.`value`:="+R", ^.`title`:="Add a rectangle using the type selected in the dropdown."),
        <.input(^.`id`:="delete-rect-button", ^.`type`:="button", ^.`value`:="-R", ^.`title`:="Remove a rectangle. Select one rect first."),
        <.input(^.`id`:="get-text-button", ^.`type`:="button", ^.`value`:="Text", ^.`title`:="Get text corresponding to a rectangle. Select one rect first."),
        <.input(^.`id`:="add-link-button", ^.`type`:="button", ^.`value`:="+L", ^.`title`:="Add a link between two rectangles. Select two of the same label that do not have a link between them."),
        <.input(^.`id`:="delete-link-button", ^.`type`:="button", ^.`value`:="-L", ^.`title`:="Remove a link between two rectangles. Select two that have a link between them."),
        <.div(^.style:="font-family: 'Arial'; font-size: 12px; color:red ; display:inline;")("1 Title"),
        <.div(^.style:="font-family: 'Arial'; font-size: 12px; color:blue ; display:inline")("2 Abstract"),
        <.div(^.style:="font-family: 'Arial'; font-size: 12px; color:green ; display:inline")("3 Author")
      ),
      <.div(^.id:="overlay-container", ^.style:="max-height:100%;overflow:auto;border:1px solid red;", SvgStyles.overlayContainer)(
        <.div(^.id:="svg-container", SvgStyles.svgImage),
        <.canvas(^.id:="fabric-canvas", SvgStyles.fabricCanvas),
        <.script(^.`type`:="text/javascript")(
          raw(s"""|var documentIconURI = "images/pdf_50x50.png";
                  |var fileRepositoryURI = "./svg-repo";
                  |var fileName = "$filename";
                  |""".stripMargin)),
        <.script(^.`type`:="text/javascript", ^.src:="js/edit-document.js" )
      )
    )
  }
}


          // // From edit.scala.html
          // @(doc : Document)

          // @main(s"Editing: $doc.fileName") {
          //     <h2 id="loading-header">Initializing...</h2>

            //     <hr/>

            //     <form onsubmit="return false;">
            //         <input id="save-button" type="button" value="Save" title="Save the document's changes.">
            //         <input id="revert-button" type="button" value="Revert" title="Discard changes and reload the document.">
            //         |
            //         <select id="type-select" title="Select the type to use for the next new rectangle.">
            //             <option value="title">Title</option>
            //             <option value="abstract">Abstract</option>
            //             <option value="author">Author</option>
            //         </select>
            //         <input id="add-rect-button" type="button" value="+R" title="Add a rectangle using the type selected in the dropdown.">
            //         <input id="delete-rect-button" type="button" value="-R" title="Remove a rectangle. Select one rect first.">
            //         <input id="get-text-button" type="button" value="Text" title="Get text corresponding to a rectangle. Select one rect first.">
            //         |
            //         <input id="add-link-button" type="button" value="+L" title="Add a link between two rectangles. Select two of the same label that do not have a link between them.">
            //         <input id="delete-link-button" type="button" value="-L" title="Remove a link between two rectangles. Select two that have a link between them.">
            //         |
            //         <div style="font-family: 'Arial'; font-size: 12px; color:red ; display:inline;">1 Title</div>
            //         <div style="font-family: 'Arial'; font-size: 12px; color:blue ; display:inline">2 Abstract</div>
            //         <div style="font-family: 'Arial'; font-size: 12px; color:green ; display:inline">3 Author</div>
            //     </form>

            //     <hr/>

            //     <div id="overlay-container">
            //         <!--<svg:svg id="svg-image" ...></svg:svg>-->   <!-- <svg:svg> element inserted by loadSvg() -->
            //         <canvas id="fabric-canvas"></canvas>            <!-- size set by loadSvg() -->
            //     </div>


            //     <style>
            //         #overlay-container {
            //         position: relative;
            //         }

            //         #svg-image {
            //         position: absolute;
            //         left: 0;
            //         top: 0;
            //         border: 1px solid black;
            //         background-color: ivory;
            //         }

            //         #fabric-canvas {
            //         position: absolute;
            //         left: 0;
            //         top: 0;
            //         }
            //     </style>

            //     <!-- per http://stackoverflow.com/questions/6672794/playframework-elegant-way-to-pass-values-to-javascript -->
//     <script type="text/javascript">
//         var documentIconURI = "@routes.Assets.at("images/pdf_50x50.png")";
//         var fileRepositoryURI = "@routes.Assets.at("svg-repo")";
//         var fileName= '@doc.fileName';
//     </script>

//     <script src="@routes.Assets.at("javascripts/edit-document.js")" type="text/javascript"></script>

// }
