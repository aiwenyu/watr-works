package edu.umass.cs.iesl.watr
package segment

import geometry._
import geometry.syntax._

import TypeTags._

import utils.ExactFloats._
import segment.{SegmentationLabels => LB}
import utils.SlicingAndDicing._

import org.dianahep.{histogrammar => HST}
import spindex._
import extract.ExtractedItem
import utils.FunctionalHelpers._
// import watrmarks._
// import utils.{RelativeDirection => Dir}



trait CharColumnFinding extends PageScopeSegmenter { self =>
  lazy val columnFinder = self

  import LB._

  def runPass1(): Unit = {
    initGridShapes()

    // initPageDividers()

    excludeImageRegionPoints()

    createBaselineClusters()

    createBaselineShapes()

    collectColumnEvidence()
  }

  def runPass2(): Unit = {

    subdivideColumnEvidence()

    collectLineText()


  }

  def collectLineText(): Unit = {
    val orderedLines = pageIndex.shapes.getOrdering(LB.VisualLine::Ordering)

    orderedLines.foreach { visualBaseline =>
      val baselineCluster = pageIndex.shapes.getRelation(visualBaseline, LB.HasCharRunBaselines).get

      val baselineMembers = pageIndex.shapes.getClusterMembers(LB.CharRunBaseline::Cluster, baselineCluster).get

      val extractedItems = getCharRunBaselineItems(baselineMembers.map(_.asInstanceOf[LineShape]))

      val clusterStr = extractedItems.map { extractedItems =>
        extractedItems.map(_.strRepr()).mkString
      }

      val lineText = clusterStr.mkString

      println(s"line>> ${lineText.mkString}")
    }

  }


  protected def getExtractedItemsForShape(shape: LabeledShape[GeometricFigure]): Seq[ExtractedItem] = {
    pageIndex.shapes.getShapeAttribute[Seq[ExtractedItem]](shape.id, LB.ExtractedItems).get
  }

  protected def getCharRunBaselineItems(baselineMembers: Seq[LineShape]): Seq[Seq[ExtractedItem]] = {
    baselineMembers.map {charRun =>
      println(s"charRun: ${charRun}")
      getExtractedItemsForShape(charRun)
    }
  }


  def excludeImageRegionPoints(): Unit = {
    // implicit val log = createFnLog

    pageIndex.pageItems.toSeq
      .filter { _.isInstanceOf[ExtractedItem.ImgItem] }
      .foreach { imageItem =>
        indexShape(imageItem.bbox, LB.Image)
        val baseLines = searchForLines(imageItem.bbox, LB.CharRunBaseline)
        deleteShapes(baseLines)
      }

    traceLog.drawPageShapes()
  }

  def createHPageRules(): Seq[Line] = {
    val pageRight = pageGeometry.right
    val pageLeft = pageGeometry.left

    val charRunBaselines = getLabeledLines(LB.CharRunBaseline)
      .sortBy(_.shape.p1.y)

    val hPageRules = charRunBaselines.map { charRunBaseline =>
      charRunBaseline.shape
        .extendLeftTo(pageLeft)
        .extendRightTo(pageRight)
    }.groupByPairs(_.p1.y == _.p1.y).map(_.head)


    hPageRules
  }


  def createBaselineClusters(): Unit = {

    pageIndex.shapes.ensureCluster(LB.CharRunBaseline::Cluster)

    val hPageRules = createHPageRules()

    hPageRules.foreach { hPageRule =>

      val ruleY = hPageRule.p1.y.asDouble()

      val queryRegion = pageHorizontalSlice(ruleY-2.0, 4.0).get

      // indexShape(queryRegion, Label("QueryRegion"))

      // Query horizontal slice of char baseline runs that might be part of the same line as this one
      val hPageRuleHits = searchForLines(queryRegion, LB.CharRunBaseline)
        .sortBy(_.shape.p1.x)

      hPageRuleHits.sliding(2).foreach { pairs =>
        pairs match {
          case Seq(runBaseline1, runBaseline2) =>

            cluster1(LB.CharRunBaseline::Cluster, runBaseline1)
            cluster1(LB.CharRunBaseline::Cluster, runBaseline2)

            val run1Items = getExtractedItemsForShape(runBaseline1)
            val run2Items = getExtractedItemsForShape(runBaseline2)

            val run1LastChar =  run1Items.last
            val run2FirstChar = run2Items.head
            val intermediateCharsIds = ((run1LastChar.id.unwrap+1) until run2FirstChar.id.unwrap ).toList


            val intermediateChars = intermediateCharsIds.map { i =>
              val item = pageIndex.extractedItems(i)
              (item.location, item)
            }

            val leftBounds = run1LastChar.location
            val rightBounds = run2FirstChar.location

            val (inlineIntermediates, nonInlineIntermediates) = intermediateChars.span { case (p, _) =>
              leftBounds.x <= p.x && p.x < rightBounds.x
            }

            inlineIntermediates.foreach { case (p, extractedItem) =>
              val intBaseline = pageIndex.shapes.extractedItemShapes.get(extractedItem.id, LB.CharRun)
              if (intBaseline != null) {
                cluster2(LB.CharRunBaseline::Cluster, runBaseline1, intBaseline)
              }
            }

            val allIntermediatesAreInlined = intermediateChars.length == inlineIntermediates.length

            if (allIntermediatesAreInlined) {
              cluster2(LB.CharRunBaseline::Cluster, runBaseline1, runBaseline2)
            } else {
              nonInlineIntermediates.foreach { case(p, item) =>
                val intBaseline = pageIndex.shapes.extractedItemShapes.get(item.id, LB.CharRun)
                if (intBaseline != null) {
                  cluster1(LB.CharRunBaseline::Cluster, intBaseline)
                }
              }

            }


          case Seq(run) =>
            cluster1(LB.CharRunBaseline::Cluster, run)

          case _ =>
        }
      }
    }

  }

  def createBaselineShapes(): Unit = {
    getClusteredLines(LB.CharRunBaseline::Cluster)
      .foreach { case (baselineClusterId,  baseLineMembers) =>

        unindexShapes(baseLineMembers)

        val sorted = baseLineMembers.sortBy(_.shape.p1.x)
        val totalBounds = sorted.head.shape.bounds.union(
          sorted.last.shape.bounds
        )

        val LTBounds(l, t, w, h) = totalBounds

        val (weight, runLines) = baseLineMembers
          .map { baseLineShape => (baseLineShape.shape.p1.y, baseLineShape.shape.length()) }
          .sortBy { _._1 }
          .groupByPairs { case (l1, l2) => l1._1 == l2._1}
          .map{ group => (group.map(_._2).sum, group) }
          .sortBy(_._1)
          .last

        runLines.headOption.map { case (yval, len) =>
          val likelyBaseline = Line(Point(l, yval), Point(l+w, yval))
          indexShape(likelyBaseline, LB.VisualBaseline)
        }
      }

   //  implicit val log = createFnLog
    traceLog.drawPageShapes()
  }


  protected def findVerticalOffsets(points: Seq[PointShape]): Seq[(Int@@FloatRep, Double, PointShape)] = {
    val vdists = points.sortBy { _.shape.y }
      .foldLeft(List[(Int@@FloatRep, Double, PointShape)]()){case (acc, e) =>
        acc.headOption match {
          case Some((_, _, lastShape)) =>
            val distExact = e.shape.y - lastShape.shape.y
            val dist = e.shape.y.asDouble() - lastShape.shape.y.asDouble()
              (distExact, dist, e) :: acc
          case None => List((0.toFloatExact(), 0d, e))
        }
      }
    vdists
  }

  // Divide Left-column lines into shorter lines that cover evenly spaced blocks of lines
  def subdivideColumnEvidence(): Unit = {
    // val bins = QuickNearestNeighbors.qnn(pageVdists)
    // println(bins.mkString("\n  ", "\n  ", "\n"))

    // val distDist  = dists.mkString("\n  ", "\n  ", "\n")

    // println(s"Page ${pageNum} --------------")
    // println("dists----")
    // println(distDist)
    // println()
    // vdists.foreach { case (dExact, dist, _) => if (dist > 0) { vspaceHist.fill(dist) } }
    // val lls = vspaceHist.ascii.mbox.lines.filter { l => l.contains("*") }
    // println(TB.linesToBox(lls))
    // println()
    // println(vspaceHist.ascii)


    val baselineShapeClusters = getClusteredLines(LB.LeftAlignedCharCol::Cluster)
    baselineShapeClusters.foreach { case (clusterReprId, baselineShapes) =>
      val leftXVals = baselineShapes.map(_.shape.p1.x)
      val leftX = leftXVals.min
      val yVals = baselineShapes.map(_.shape.p1.y)
      val topY = yVals.min
      val bottomY = yVals.max

      indexShape(Line(
        Point(leftX, topY),
        Point(leftX, bottomY)),
        LB.LeftAlignedCharCol
      )
    }

   //  implicit val log = createFnLog
    traceLog.drawPageShapes()
  }

  def collectColumnEvidence(): Unit = {
   //  implicit val log = createFnLog

    var pageVdists: Seq[Int@@FloatRep] = List()

    val colLeftHist = HST.SparselyBin.ing(1.0, {p: Point => p.x.asDouble()})
    val colRightHist = HST.SparselyBin.ing(2.0, {p: Point => p.x.asDouble()})

    getLabeledLines(LB.VisualBaseline).foreach { charRunLine =>
      val Line(p1, p2) = charRunLine.shape
      indexShape(p1, LB.ColLeftEvidence)
      indexShape(p2, LB.ColRightEvidence)
      colLeftHist.fill(p1)
      colRightHist.fill(p2)
    }
    val pageRight = pageGeometry.right

    pageIndex.shapes.ensureCluster(LB.LeftAlignedCharCol::Cluster)
    colLeftHist.bins.toList
      .filter { case (bin, counting) =>
        val binWidth = colLeftHist.binWidth
        val binRight = (bin+1)*binWidth
        counting.entries > 1 && binRight.toFloatExact() < pageRight
        true
      }
      .foreach{ case (bin, counting) =>
        val binWidth = colLeftHist.binWidth
        val binLeft = bin*binWidth
        val pageColumn = pageVerticalSlice(binLeft, binWidth).get
        val hits = searchForPoints(pageColumn, LB.ColLeftEvidence)

        deleteShapes(hits)

        if (hits.length > 1) {
          val yvals = hits.map(_.shape.y)
          val (maxy, miny) = (yvals.max,  yvals.min)
          val height = maxy - miny

          val colActual = pageColumn.getHorizontalSlice(miny, height).get

          val intersectingBaselines = searchForLines(colActual, LB.VisualBaseline)
            .sortBy(_.shape.p1.y)

          val hitsAndOverlaps = spanAllEithers(intersectingBaselines, { baselineShape: LineShape =>
            val hitLeftX = baselineShape.shape.p1.x
            colActual.left <= hitLeftX
          })


          hitsAndOverlaps.foreach{ _ match {
            case Right(baselineShapes) =>

              if (baselineShapes.length > 1) {
                clusterN(LB.LeftAlignedCharCol::Cluster, baselineShapes)
                // val yVals = baselineShapes.map(_.shape.p1.y)
                // val topY = yVals.min
                // val bottomY = yVals.max
                // indexShape(Line(
                //   Point(colActual.left, topY),
                //   Point(colActual.left, bottomY)),
                //   LB.LeftAlignedCharCol
                // )
              }

            case _ =>
          }}


          val vdists = findVerticalOffsets(hits)

          pageVdists = vdists.map(_._1) ++ pageVdists
        }
      }


    colRightHist.bins.toList
      .filter { case (bin, counting) =>
        val binWidth = colLeftHist.binWidth
        val binRight = (bin+1)*binWidth
        counting.entries > 1 && binRight.toFloatExact() < pageRight
      }
      .foreach{ case (bin, counting) =>
        val binWidth = colRightHist.binWidth
        val binLeft = bin*binWidth
        val pageColumn = pageVerticalSlice(binLeft, binWidth).get

        val hits = searchForPoints(pageColumn, LB.ColRightEvidence)

        deleteShapes(hits)

        if (hits.length > 1) {
          val yvals = hits.map(_.shape.y)
          val (maxy, miny) = (yvals.max,  yvals.min)
          val height = maxy - miny

          val colActual = pageColumn.getHorizontalSlice(miny, height).get

          val intersectedRuns = searchForLines(colActual, LB.VisualBaseline)
            .sortBy(_.shape.p1.y)

          val hitsAndOverlaps = spanAllEithers(intersectedRuns, { charRunShape: LineShape =>
            val hitRightX = charRunShape.shape.p2.x
            colActual.right >= hitRightX
          })

          hitsAndOverlaps.foreach{ _ match {
            case Right(baselineShapes) =>

              if (baselineShapes.length > 1) {
                clusterN(LB.RightAlignedCharCol::Cluster, baselineShapes)

                // val yVals = validCharRuns.map(_.shape.p2.y)
                // val topY = yVals.min
                // val bottomY = yVals.max
                // indexShape(Line(
                //   Point(colActual.right, topY),
                //   Point(colActual.right, bottomY)),
                //   LB.RightAlignedCharCol
                // )
              }

            case _ =>
          }}
        }
      }


    deleteLabeledShapes(LB.ColRightEvidence)
    deleteLabeledShapes(LB.ColLeftEvidence)

    traceLog.drawPageShapes()
  }


  def boundedHLine(bbox: LTBounds, atY: Int@@FloatRep): Line = {
    val LTBounds(x, y, w, h) = bbox
    Line(
      Point(x, atY),
      Point(x+w, atY)
    )
  }

  def boundedVerticalLine(bbox: LTBounds, atX: Int@@FloatRep): Line = {
    val LTBounds(x, y, w, h) = bbox
    Line(
      Point(atX, y),
      Point(atX, y+h)
    )
  }

  protected def findPageCharRuns(): Seq[Seq[ExtractedItem]] = {
    val charRuns = pageIndex.pageItems.toSeq
      .filter { _.isInstanceOf[ExtractedItem.CharItem] }
      .groupByPairsWithIndex {
        case (item1, item2, i) =>
          val consecutive = item1.id.unwrap+1 == item2.id.unwrap
          val sameLine = item1.bbox.bottom == item2.bbox.bottom
          consecutive && sameLine
      }

    charRuns
  }

  def createCharRunBaseline(charRun: Seq[ExtractedItem]): Line = {
    val runBeginPt =  Point(charRun.head.bbox.left, charRun.head.bbox.bottom)
    val runEndPt = Point(charRun.last.bbox.right, charRun.last.bbox.bottom)
    Line(runBeginPt, runEndPt)
  }

  def createHorizontalPageRules(charRuns: Seq[Seq[ExtractedItem]]): Seq[Line] = {

    val baselines = charRuns.map{ charRun =>
      val isChar  = charRun.head.isInstanceOf[ExtractedItem.CharItem]
      if (isChar) {
        createCharRunBaseline(charRun).some
      } else None
    }.flatten

    val hPageRules = baselines
      .sortBy(_.p1.y)
      .map { _.extendLeftTo(pageGeometry.left).extendRightTo(pageGeometry.right) }
      .groupByPairs(_.p1.y == _.p1.y) // uniquify
      .map(_.head)                    //  ...

    hPageRules
  }


  // private def initPageDividers(): Unit = {
  //   val ptop = pageGeometry.toPoint(Dir.Top)
  //   val pbot = pageGeometry.toPoint(Dir.Bottom)
  //   val vline = Line(ptop, pbot)
  //   indexShape(vline, Label("PageVertical"))
  // }

  private def initGridShapes(): Unit = {
    val pageCharRuns = findPageCharRuns()

    pageCharRuns.foreach { charRun =>
      val baseLine = createCharRunBaseline(charRun)
      val baselineShape = indexShape(baseLine, LB.CharRunBaseline)

      // val loc = charRun.head.location
      // val endLoc = charRun.last.bbox.toPoint(Dir.BottomRight)
      // indexShape(loc, LB.CharRunBegin)
      // indexShape(endLoc, Label("CharRunEnd"))
      pageIndex.shapes.setShapeAttribute[Seq[ExtractedItem]](baselineShape.id, LB.ExtractedItems, charRun)
    }

   //  implicit val log = createFnLog
    traceLog.drawPageShapes()
  }

  // private def initHRuleShapes(): Unit = {
  //   val pageCharRuns = findPageCharRuns()
  //   val hPageRules = createHorizontalPageRules(pageCharRuns)


  //   pageCharRuns.foreach { charRun =>


  //     val charRunY = charRun.head.location.y
  //     val matchingHRuler = hPageRules.find(_.p1.y == charRunY).getOrElse {
  //       sys.error("no matching h-page rule")
  //     }

  //     val initIndex = charRun.head.id.unwrap
  //     // Scan and align items left->right
  //     val endOffset = pageIndex.lastItemOffset

  //     var currIndex = initIndex+1
  //     var done = false

  //     while (!done) {
  //       val item = pageIndex.extractedItems(currIndex)
  //       val lastItem  = pageIndex.extractedItems(currIndex-1)
  //       val itemX = item.location.x
  //       val lastItemX = lastItem.location.x
  //       if (itemX >= lastItemX && currIndex < endOffset) {
  //         currIndex += 1
  //       } else {
  //         done = true
  //       }
  //     }
  //     val leftIncludes = pageIndex.extractedItems.slice(initIndex, currIndex)
  //     val leftStr = leftIncludes.map(_.strRepr()).mkString
  //     println(s"lft>${leftStr}")


  //     // val runBaseline = createCharRunBaseline(charRun)
  //     // val baselineShape = indexShape(runBaseline, LB.CharRunBaseline)

  //     // pageIndex.shapes.setShapeAttribute[Seq[ExtractedItem]](baselineShape.id, LB.ExtractedItems, charRun)

  //     // charRun.foreach { _ match  {

  //     //   case item:ExtractedItem.CharItem =>
  //     //     if (item.charProps.isRunBegin) {
  //     //       // pageIndex.components.appendToOrdering(LB.CharRunBegin, cc)
  //     //     }

  //     //     pageIndex.shapes.extractedItemShapes.put(item.id, LB.CharRun, baselineShape)


  //     //   case item:ExtractedItem.ImgItem =>
  //     //     // pageIndex.shapes.indexShape(item.bbox, LB.Image)
  //     //     // val underline = item.bbox.toLine(Dir.Bottom)
  //     //     // val pathUnderline = indexShape(underline, LB.CharRunBaseline)
  //     //     // pageIndex.shapes.extractedItemShapes.put(item.id, LB.CharRun, pathUnderline)
  //     //     // // pageIndex.shapes.setShapeAttribute[Seq[ExtractedItem]](pathUnderline.id, LB.ExtractedItems, run)

  //     //   case item:ExtractedItem.PathItem =>
  //     // }}
  //   }

  //   implicit val log = createFnLog
  //   traceLog.drawPageShapes()
  // }

  // private def initPageComponents(): Unit = {
  //   // import utils.{RelativeDirection => Dir}
  //   val pageCharRuns = findPageCharRuns()
  //   pageCharRuns.foreach { charRun =>
  //     val runBaseline = createCharRunBaseline(charRun)
  //     val baselineShape = indexShape(runBaseline, LB.CharRunBaseline)
  //     pageIndex.shapes.setShapeAttribute[Seq[ExtractedItem]](baselineShape.id, LB.ExtractedItems, charRun)
  //     charRun.foreach { _ match  {
  //       case item:ExtractedItem.CharItem =>
  //         if (item.charProps.isRunBegin) {
  //           // pageIndex.components.appendToOrdering(LB.CharRunBegin, cc)
  //         }
  //         pageIndex.shapes.extractedItemShapes.put(item.id, LB.CharRun, baselineShape)
  //       case item:ExtractedItem.ImgItem =>
  //         // pageIndex.shapes.indexShape(item.bbox, LB.Image)
  //         // val underline = item.bbox.toLine(Dir.Bottom)
  //         // val pathUnderline = indexShape(underline, LB.CharRunBaseline)
  //         // pageIndex.shapes.extractedItemShapes.put(item.id, LB.CharRun, pathUnderline)
  //         // // pageIndex.shapes.setShapeAttribute[Seq[ExtractedItem]](pathUnderline.id, LB.ExtractedItems, run)
  //       case item:ExtractedItem.PathItem =>
  //     }}
  //   }
  // }
}
