package edu.umass.cs.iesl.watr
package segment

import spatial._
import extract._

// import watrmarks.{StandardLabels => LB}
import TypeTags._

class ZoneIndexConstructionTest extends DocsegTestUtil  {

  // TODO this test class belongs in watrmarks, but watrshed contains char extraction, so it is here for
  //   convenience

  behavior of "zone indexing"

  it should "allow labeling zones" in  {
    val pg = PageID(0)
    val bbox = LTBounds(166.0d, 549.0, 350.0, 15.0)
    val text = """Y. Adachi {^{a∗},} H. Morita {^{a},} T. Kanomata {^{b},} A. Sato {^{b},} H. Yoshida {^{c},}"""

    val paper = papers.`6376.pdf`
    val zoneIndex = ZoneIndexer.loadSpatialIndices(
      DocumentExtractor.extractChars(paper)
    )

    // Old method:
    val chars: Seq[CharBox] = zoneIndex.queryChars(pg, bbox)
    val found = chars.sortBy(_.bbox.left).map({ cbox => cbox.char }).toList.mkString
    val lineChars = chars.sortBy(_.bbox.left)
    val ccs = Component(lineChars.map(Component(_)), LB.Line)
    val tokenized = ccs.tokenizeLine().toText
    println(s"found chars: ${found}")
    println(s"tokenized  : ${tokenized}")
    // Line labeling process
    // val chars: Seq[PageComponent] = zoneIndex.queryChars(pg, bbox)
    // zoneIndex.addComponent()
    // zoneIndex.addLabels(ccs)
    // val ccRet = zoneIndex.query(LB.Line)
    // find chars with common baseline
    // label as 'line'

    // zoneIndex
  }

}
