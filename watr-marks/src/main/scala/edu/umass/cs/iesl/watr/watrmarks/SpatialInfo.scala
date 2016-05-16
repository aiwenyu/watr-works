package edu.umass.cs.iesl.watr
package watrmarks


import net.sf.jsi.Rectangle
import scala.collection.mutable

import net.sf.jsi.SpatialIndex
import net.sf.jsi.rtree.RTree
import net.sf.jsi

import play.api.libs.json
import json._
import scalaz.{Tag, @@}


case class FontInfo(
  // fontName: String,
  fontFamily: String,
  fontSize: String
)

object jsiRectangle {
  def apply(
    x: Double, y: Double, width: Double, height: Double
  ): Rectangle = apply(
    x.toFloat, y.toFloat, width.toFloat, height.toFloat
  )

  def apply(
    x: Float, y: Float, width: Float, height: Float
  ): Rectangle = new Rectangle(
    x, y, x+width, y+height
  )

  def toLTBounds(r: jsi.Rectangle): LTBounds = {
    LTBounds(
      left = r.minX.toDouble,
      top =  r.minY.toDouble,
      width = (r.maxX - r.minX).toDouble,
      height = (r.maxY - r.minY).toDouble
    )
  }
}



object Bounds {

  implicit class RicherLTBounds(val tb: LTBounds) extends AnyVal {

    def toJsiRectangle: jsi.Rectangle = {
       jsiRectangle(tb.left, tb.top, tb.width, tb.height)
    }

    def toLBBounds: LBBounds = {
      LBBounds(
        left = tb.left,
        bottom =  tb.top+tb.height,
        width = tb.width,
        height = tb.height
      )
    }
    def prettyPrint: String = {
      val left = tb.left
      val top=  tb.top
      val width = tb.width
      val height = tb.height
      def fmt = (d: Double) => f"${d}%1.2f"
      s"""(l:${fmt(left)}, t:${fmt(top)}, w:${fmt(width)}, h:${fmt(height)})"""
    }
  }

  implicit class RicherLBBounds(val tb: LBBounds) extends AnyVal {
    def toLTBounds: LTBounds = {
      LTBounds(
        left = tb.left,
        top =  tb.bottom-tb.height,
        width = tb.width,
        height = tb.height
      )

    }
    def prettyPrint: String = {
      val left = tb.left
      val bottom=  tb.bottom
      val width = tb.width
      val height = tb.height
      def fmt = (d: Double) => f"${d}%1.2f"
      s"""(l:${fmt(left)}, b:${fmt(bottom)}, w:${fmt(width)}, h:${fmt(height)})"""
    }
  }


}

sealed trait Bounds

case class LTBounds(
  left: Double,
  top: Double,
  width: Double,
  height: Double
) extends Bounds

case class LBBounds(
  left: Double,
  bottom: Double,
  width: Double,
  height: Double
) extends Bounds

case class Borders(
  bleft: Double,
  btop: Double,
  bright: Double,
  bbottom: Double
)

case class TargetedBounds(
  id: Int@@RegionID,
  target: Int@@PageID,
  bbox: LTBounds
)


sealed trait ZoneID
sealed trait LabelID
sealed trait RegionID

case class Zone(
  id: Int@@ZoneID,
  bboxes: List[TargetedBounds]
) {
  def withLabel(l: Label) = ZoneAndLabel(
    this.id, l
  )
}

// case class Label(
//   ns: String,
//   key: String,
//   value: Option[String]  = None
// ) {
//   val vstr = value.map(v => s"=${v}").getOrElse("")
//   override def toString = s"#${ns}::${key}${vstr}"
// }


sealed trait PageID

case class PageGeometry(
  id: Int@@PageID,
  bounds: LTBounds,
  borders:Option[Borders]
)


case class ZoneAndLabel(zoneId: Int@@ZoneID, label:Label)

case class ZoneRecords(
  id: String,
  target: String,
  pageGeometries: List[PageGeometry],
  zones: List[Zone],
  labels: List[ZoneAndLabel]
)



class ZoneIndexer  {

  val pageRIndexes = mutable.HashMap[Int@@PageID, SpatialIndex]()
  val pageGeometries = mutable.Map[Int@@PageID, PageGeometry]()

  val zoneMap = mutable.HashMap[Int@@ZoneID, Zone]()
  val zoneLabelMap = mutable.HashMap[Int@@ZoneID, mutable.ArrayBuffer[Label]]()

  val regionToZone = mutable.HashMap[Int@@RegionID, Zone]()



  def getZoneLabels(id: Int@@ZoneID): Seq[Label] = {
    zoneLabelMap.get(id).getOrElse(Seq())
  }

  def getPages(): List[Int@@PageID] = {
    pageRIndexes.keys.toList.sortBy(PageID.unwrap(_))
  }


  def addPage(p: PageGeometry): Unit = {

    if(pageGeometries.contains(p.id)) {
      sys.error("adding new page w/existing id")
    }

    pageGeometries.put(p.id, p)
    val si: SpatialIndex = new RTree()
    si.init(null)
    pageRIndexes.put(p.id, si)
  }

  // def createZone(zone: Zone): Unit = { todo

  def addZone(zone: Zone): Unit = {
    val zoneId = zone.id
    zoneMap.put(zoneId, zone).map(existing => sys.error(s"zone already exists in zoneMap"))
    zone.bboxes.foreach{ targetedBounds  =>
      regionToZone.put(targetedBounds.id, zone)
      val rindex = pageRIndexes(targetedBounds.target)
      rindex.add(
        targetedBounds.bbox.toJsiRectangle,
        RegionID.unwrap(targetedBounds.id)
      )
    }
  }

  def addLabels(zl: ZoneAndLabel): Unit = {
    val lls = zoneLabelMap.getOrElseUpdate(zl.zoneId, mutable.ArrayBuffer[Label]())
    lls.append(zl.label)
  }

  import gnu.trove.procedure.TIntProcedure

  class CollectRegionIds extends TIntProcedure {
    import scala.collection.mutable
    val ids = mutable.ArrayBuffer[Int]()

    override def execute(id: Int): Boolean = {
      ids.append(id)
      true
    }

    def getIDs: Seq[Int@@RegionID] = {
      ids.map(RegionID(_))
    }
  }

  // def getPageIterator(): Seq[PageItera]


  def query(page: Int@@PageID, q: LTBounds): Seq[Zone] = {
    println(s"ZoneIndex.query(${page}, ${q.prettyPrint})")
    val rindex = pageRIndexes(page)

    val collectRegions = new CollectRegionIds()
    rindex.contains(q.toJsiRectangle, collectRegions)
    // rindex.intersects(q.toJsiRectangle, collectRegions)
    val regions = collectRegions.getIDs
    val zones = collectRegions
      .getIDs.map{ regionId => regionToZone(regionId) }

    zones.sortBy { z => z.bboxes.head.bbox.left }
  }

}



object ZoneIndexer extends SpatialJsonFormat {
  def minMaxPairToPointWidth(minMax: (Double, Double)): (Double, Double) = {
    (minMax._1, minMax._2-minMax._1)
  }

  def vconcat(psis: ZoneIndexer*): ZoneIndexer = {

    ???
  }


  def loadSpatialIndices(zoneRec: ZoneRecords): ZoneIndexer = {

    val zindexer = new ZoneIndexer()

    println("added zone indexer")

    zoneRec.pageGeometries.foreach { p =>
      zindexer.addPage(p)
    }
    println("added zone pages")

    zoneRec.zones.foreach { z =>
      zindexer.addZone(z)
    }
    println("added zones")
    zoneRec.labels.foreach { zl =>
      zindexer.addLabels(zl)
    }
    println("added zone labels")

    zindexer
  }
}

object SpatialEnrichments {

  implicit class RicherZone(val zone: Zone) extends AnyVal {

    def area(): Double = {
      zone.bboxes.foldLeft(0d){ case (acc, a) =>
        a.bbox.area
      }
    }

  }

  implicit class RicherLTBounds(val bb: LTBounds) extends AnyVal {
    def area: Double = bb.width*bb.height
  }
}

trait SpatialJsonFormat {

  val ReadPageID: Reads[Int@@PageID]   = __.read[Int].map(i => Tag.of[PageID](i))
  val WritePageID: Writes[Int@@PageID] = Writes[Int@@PageID] { i => JsNumber(PageID.unwrap(i)) }
  implicit def FormatPageID            = Format(ReadPageID, WritePageID)

  val ReadZoneID: Reads[Int@@ZoneID]   = __.read[Int].map(i => Tag.of[ZoneID](i))
  val WriteZoneID: Writes[Int@@ZoneID] = Writes[Int@@ZoneID] { i => JsNumber(ZoneID.unwrap(i)) }
  implicit def FormatZoneID            = Format(ReadZoneID, WriteZoneID)

  val ReadRegionID: Reads[Int@@RegionID]   = __.read[Int].map(i => Tag.of[RegionID](i))
  val WriteRegionID: Writes[Int@@RegionID] = Writes[Int@@RegionID] { i => JsNumber(RegionID.unwrap(i)) }
  implicit def FormatRegionID            = Format(ReadRegionID, WriteRegionID)


  implicit def residentFormat = Json.format[LBBounds]
  implicit def LTBoundsFormat = Json.format[LTBounds]
  implicit def FormatTargetedBounds = Json.format[TargetedBounds]
  implicit def FormatBorders = Json.format[Borders]
  implicit def FormatLabel = Json.format[Label]
  implicit def FormatPageGeometry = Json.format[PageGeometry]
  implicit def FormatZone = Json.format[Zone]
  implicit def FormatZoneAndLabel = Json.format[ZoneAndLabel]

  implicit def FormatZoneRecords = Json.format[ZoneRecords]

  implicit def FormatLable = Json.format[Label]


  // __.read(
  //     (__ \ "id").read[Int@@ZoneID] and
  //     (__ \ "label").read[String] and
  //     (__ \ "bboxes").read[List[TargetedBounds]]
  //     tupled
  //   ).map{ rec => Zone(rec._1, rec._3) }


}
