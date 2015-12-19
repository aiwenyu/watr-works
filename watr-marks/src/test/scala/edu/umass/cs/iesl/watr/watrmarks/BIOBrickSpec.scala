package edu.umass.cs.iesl.watr
package watrmarks

import java.io.StringReader
import org.scalatest._



class BioBrickSpec extends FlatSpec {
  // perhaps add namespace as a valid constraint?

  import StandardLabels._

  behavior of "labels"

  it should "allow construction of BIOLU pins from label definitions" in {
    // lazy val ld = LabelDictionary.create(word, verb)
    assert(Verb.B === BPin(Verb))
  }

  it should "ensure label names are unique within a namespace" in {


  }

  val runLolaHeader =
    """|| |V   N    V   | {ns:pos, type: {verb: v, noun: n}, unit: word}
       || |w~$ w~~$ w~$P| {ns:tok, type: {word: w, punct: p}, unit: char}
       ||1|          1  |
       ||0|0123456789012|
       |""".stripMargin

  val runLolaFull =
    """|| |V   N    V   | {ns:pos, type: {verb: v, noun: n}, unit: word}
       || |w~$ w~~$ w~$P| {ns:tok, type: {word: w, punct: p}, unit: char}
       |xx>Run Lola run.<jkl
       |""".stripMargin
      // ||1|          1  |
      // ||0|0123456789012|


  it should "allow labeled spans" in {

    val fontInfo: Option[FontInfo] = None
    val textBounds: Option[TextBounds] = None

    val expectedSpan = LabeledSpan(List(
      LabeledColumn(Set(Word.B, Verb.U ), 'R', fontInfo, textBounds),
      LabeledColumn(Set(Word.I         ), 'u', fontInfo, textBounds),
      LabeledColumn(Set(Word.L         ), 'n', fontInfo, textBounds),
      LabeledColumn(Set(               ), ' ', fontInfo, textBounds),
      LabeledColumn(Set(Word.B, Noun.U ), 'L', fontInfo, textBounds),
      LabeledColumn(Set(Word.I         ), 'o', fontInfo, textBounds),
      LabeledColumn(Set(Word.I         ), 'l', fontInfo, textBounds),
      LabeledColumn(Set(Word.L         ), 'a', fontInfo, textBounds),
      LabeledColumn(Set(               ), ' ', fontInfo, textBounds),
      LabeledColumn(Set(Word.B, Verb.U ), 'r', fontInfo, textBounds),
      LabeledColumn(Set(Word.I         ), 'u', fontInfo, textBounds),
      LabeledColumn(Set(Word.L         ), 'n', fontInfo, textBounds),
      LabeledColumn(Set(Punct.U        ), '.', fontInfo, textBounds)
    ))


    val lspan = biolu.parseBioBrick(runLolaFull, bioDict, None)

    assert(lspan === expectedSpan)

  }

  val runBrick =
    """|| |V   | {ns:pos, type: {verb: v}, unit: word}
       || |w~$P| {ns:tok, type: {word: w, punct: p}, unit: char}
       |  >Run.<
       |""".stripMargin

  it should "accept bounds and info" in {

    val bounds = Some(List.fill(4)(TextBounds(1, 2, 3, 4)))
    val fonts = Some(List.fill(4)(FontInfo("f1", "1px")))

    def b(i: Int) = Option(bounds.get.apply(i))
    def f(i: Int) = Option(fonts.get.apply(i))

    val lspan = biolu.parseBioBrick(runBrick, bioDict, None, bounds, fonts)
    val expectedSpan = LabeledSpan(List(
      LabeledColumn(Set(Word.B, Verb.U ), 'R', f(0), b(0)),
      LabeledColumn(Set(Word.I         ), 'u', f(0), b(0)),
      LabeledColumn(Set(Word.L         ), 'n', f(0), b(0)),
      LabeledColumn(Set(Punct.U        ), '.', f(0), b(0))
    ))

  }

  it should "allow labeled locations, applying labels" in {}

  behavior of "bio brick cursors"
  val svgBrick1 =
    """| <svg version="1.1" width="612px" height="3168px" viewBox="0 0 612 3168">
       |   <g transform="matrix(1 0 0 -1 0 792)">
       |     <text transform="translate(136.8 669.12) scale(1, -1)">
       |       <tspan
       |         x="0 11.51 20.15 25.91 33.59 43.19"
       |         endX="112.43"
       |         y="0"
       |         font-size="17.2154px"
       |         font-family="Times"
       |         bio="| |V   | {ns:pos, type: {verb: v}, unit: word} % | |w~$P| {ns:tok, type: {word: w, punct: p}, unit: char}"
       |       >Run.</tspan>
       |     </text>
       |   </g>
       | </svg>
       |""".stripMargin

  // TextSpan functionality includes
  //  - chars (the actual text)
  //  - x,y position for each
  //  - font info: name/type/weight/height/etc
  //  - labels
  //    - serialize to/from brick format



  val brickTemplate =
    """| <svg version="1.1" width="612px" height="3168px" viewBox="0 0 612 3168">
       |   <g transform="matrix(1 0 0 -1 0 792)">
       |     <text transform="translate(136.8 669.12) scale(1, -1)">
       |       <tspan endX="112.43" font-size="17.2154px" font-family="Times" y="0"
       |         x="XXX"
       |bio="BIO"
       |       >TEXT</tspan>
       |     </text>
       |   </g>
       | </svg>
       |""".stripMargin

  it should "navigate chars" in {

    val bioSamples = List(
      "    "
    )

    val bioline = """| |ZZZ| {ns:pos, type: {token: t}, unit: char}"""

    val text = "abcde".toList.zipWithIndex

    bioSamples  foreach { bio =>

      val bl = "ZZZ".r.replaceAllIn(bioline, bio)
      val xs = bio.zipWithIndex.map(_._2).mkString(" ")

      val svg =
        brickTemplate
          .replaceAll("BIO", bl)
          .replaceAll("XXX", xs)
          .replaceAll("TEXT", text.take(bio.length).map(_._1).mkString)
          .replaceAll("!", "\\$")

      // println(svg)

      val doc = dom.readWatrDom(new StringReader(svg), bioDict)
      val charCursor = doc.toCursor(CharLabel)
      charCursor.foreach{ cur =>
        println("cc: " + cur.getText)
      }
    }

  }

  // it should "navigate labels" in {

  //   val bioSamples = List(
  //     "t!  "
  //   )

  //   val bioline = """| |ZZZ| {ns:pos, type: {token: t}, unit: char}"""

  //   val text = "abcde".toList.zipWithIndex

  //   bioSamples  foreach { bio =>

  //     val bl = "ZZZ".r.replaceAllIn(bioline, bio)
  //     val xs = bio.zipWithIndex.map(_._2).mkString(" ")

  //     val svg =
  //       brickTemplate
  //         .replaceAll("BIO", bl)
  //         .replaceAll("XXX", xs)
  //         .replaceAll("TEXT", text.take(bio.length).map(_._1).mkString)
  //         .replaceAll("!", "\\$")

  //     // println(svg)

  //     val doc = dom.readWatrDom(new StringReader(svg), bioDict)
  //     val charCursor = doc.toCursor(CharLabel)
  //     println("cc1: " + charCursor.getText)
  //   }

  // }

}