package edu.umass.cs.iesl.watr
package segment


import ammonite.{ops => fs}, fs._
// import java.io.InputStream
import spindex._

import scala.collection.JavaConversions._

import textboxing.{TextBoxing => TB}, TB._
import watrmarks.{StandardLabels => LB}

import geometry._

import textreflow.data._
import geometry.syntax._

import ComponentOperations._
import PageComponentImplicits._

import utils.{CompassDirection => CDir, _}
import tracing.VisualTracer._
import EnrichNumerics._
import SlicingAndDicing._
import TypeTags._
import corpora._
import extract.PdfTextExtractor
import utils.ExactFloats._

case class LineDimensionBins(
  pageNum: Int@@PageNum,
  widthBin: Seq[((FloatExact, Double), Seq[Component])],
  unBinned: Seq[Component]
)

case class PageSegAccumulator(
  commonLineDimensions: Seq[Point] = Seq(),
  lineDimensionBins: Seq[LineDimensionBins] = Seq(),
  commonFocalJumps: Map[String, Seq[String]] = Map(),
  docWideModalVerticalLineDist: FloatExact = 0.0.toFloatExact
)


object DocumentSegmenter {

  def approxSortYX(charBoxes: Seq[Component]): Seq[Component] = {
    charBoxes.sortBy({ c =>
      (c.bounds.top, c.bounds.left)
    })
  }


  def squishb(charBoxes: Seq[Component]): String = {
    approxSortYX(charBoxes)
      .map({ cbox => cbox.chars })
      .mkString
  }


  def pairwiseSpaces(cs: Seq[CharAtom]): Seq[Double] = {
    val cpairs = cs.sliding(2).toList

    val dists = cpairs.map({
      case Seq(c1, c2)  => (c2.bbox.left - c1.bbox.right).asDouble
      case _  => 0d
    })

    dists :+ 0d
  }

  // (charBoxesBounds(sortedXLine), sortedXLine)
  def approximateLineBins(charBoxes: Seq[AtomicComponent]): Seq[Seq[AtomicComponent]] = {
    val sortedYPage = charBoxes
      .groupBy{ charCC =>
        charCC.bounds.bottom.unwrap
      }
      .toSeq
      .sortBy{ case (bottomY, atomCcs) =>
        bottomY
      }

    // charBoxes.sortBy(_.id)
    sortedYPage
      .map({case (bottomY, charBoxes) =>
        charBoxes.sortBy(_.bounds.left)
      })

  }

  def compareDouble(d1: Double, d2: Double, precision: Double): Int = {
    if (d1.isNaN() || d2.isNaN()) {
      d1.compareTo(d2)
    } else {
      val p:Double = if (precision == 0) 0 else 1

      val i1: Long = math.round(d1 / p);
      val i2: Long = math.round(d2 / p);
      i1.compareTo(i2)
    }
  }

  def compareDoubleWithTolerance(d1: Double, d2: Double, tolerance: Double): Int = {
    if (math.abs(d1 - d2) < tolerance) 0
    else if (d1 < d2) -1
    else 1
  }

  def filterAngle(direction: Double, tolerance: Double): (Double) => Boolean = {
    val filter = angleFilter(direction: Double, tolerance: Double)
      (angle: Double) => filter.matches(angle)
  }

  def angleFilter(direction: Double, tolerance: Double): AngleFilter = {
    val t2 = tolerance / 2
    AngleFilter(direction - t2, direction + t2)
  }

  import shapeless.lens

  def createSegmenter(stableId: String@@DocumentID, pdfPath: Path, docStore: DocumentZoningApi): DocumentSegmenter = {
    val pageAtomsAndGeometry = PdfTextExtractor.extractChars(stableId, pdfPath)
    val mpageIndex = new MultiPageIndex(stableId, docStore)

    val pageIdL = lens[CharAtom].charRegion.page.pageId

    val docId = docStore.addDocument(stableId)
    pageAtomsAndGeometry.foreach { case(regions, geom)  =>
      val pageId = docStore.addPage(docId, geom.id)
      println(s"added page ${pageId}")
      docStore.setPageGeometry(pageId, geom.bounds)
      mpageIndex.addPage(geom)

      regions.foreach {
        case cb:CharAtom if !cb.isNonPrintable =>
          // modify the pageId to match the one assigned by docStore
          val update = pageIdL.modify(cb){_ => pageId}
          mpageIndex.addCharAtom(update)

        case cb => println(s"error adding ${cb}")
      }
    }

    new DocumentSegmenter(mpageIndex)
  }


  def candidateCrossesLineBounds(cand: Component, line: Component): Boolean = {
    val slopFactor = 0.31d

    val linex0 = line.bounds.toWesternPoint.x-slopFactor
    val linex1 = line.bounds.toEasternPoint.x+slopFactor
    val candx0 = cand.bounds.toWesternPoint.x
    val candx1 = cand.bounds.toEasternPoint.x
    val candRightInside = linex0 <= candx1 && candx1 <= linex1
    val candLeftOutside = candx0 < linex0
    val candLeftInside = linex0 <= candx0 && candx0 <= linex1
    val candRightOutside = linex1 < candx1

    val crossesLeft = candRightInside && candLeftOutside
    val crossesRight = candLeftInside && candRightOutside


    crossesLeft || crossesRight
  }

  def isOverlappedVertically(line1: Component, line2: Component): Boolean = {
    !(isStrictlyAbove(line1, line2) || isStrictlyBelow(line1, line2))
  }

  def isStrictlyAbove(line1: Component, line2: Component): Boolean = {
    val y1 = line1.bounds.toPoint(CDir.S).y
    val y2 = line2.bounds.toPoint(CDir.N).y
    y1 < y2
  }
  def isStrictlyBelow(line1: Component, line2: Component): Boolean = {
    val y1 = line1.bounds.toPoint(CDir.N).y
    val y2 = line2.bounds.toPoint(CDir.S).y
    y1 > y2
  }

  def isStrictlyLeftToRight(cand: Component, line: Component): Boolean = {
    val linex0 = line.bounds.toWesternPoint.x
    val candx1 = cand.bounds.toEasternPoint.x
    candx1 < linex0
  }

  def isStrictlyRightToLeft(cand: Component, line: Component): Boolean = {
    val linex1 = line.bounds.toEasternPoint.x
    val candx0 = cand.bounds.toWesternPoint.x
    candx0 > linex1
  }
  def candidateIsOutsideLineBounds(cand: Component, line: Component): Boolean = {
    isStrictlyLeftToRight(cand, line) ||
      isStrictlyRightToLeft(cand, line)
  }
}


class DocumentSegmenter(
  val mpageIndex: MultiPageIndex
) {
  val docStore = mpageIndex.docStore
  val stableId = mpageIndex.getStableId

  val docId = docStore.getDocument(stableId)
    .getOrElse(sys.error(s"DocumentSegmenter trying to access non-existent document ${stableId}"))

  def vtrace = mpageIndex.vtrace

  import DocumentSegmenter._

  var docOrientation: Double = 0d

  var pageSegAccum: PageSegAccumulator = PageSegAccumulator(Seq())

  def runLineDetermination(): Unit = {
    val pageRegions = for {
      (pageId, pagenum) <- docStore.getPages(docId).zipWithIndex
    } yield {

      println(s"Page ${pagenum} id=${pageId}")
      // print(s".")
      runLineDeterminationOnPage(pageId, PageNum(pagenum))

      val pageGeometry = docStore.getPageGeometry(pageId)
      docStore.getTargetRegion(
        docStore.addTargetRegion(pageId, pageGeometry)
      ).toPageRegion()
    }

    docStore.labelRegions(LB.DocumentPages, pageRegions)
  }

  def runLineDeterminationOnPage(pageId: Int@@PageID, pageNum: Int@@PageNum): Unit = {
    val atomicComponents = mpageIndex.getPageAtoms(pageNum)
    vtrace.trace(message(s"runLineDetermination() on page ${pageNum} w/ ${atomicComponents.length} char atoms"))

    determineLines(pageId, pageNum, atomicComponents)
  }

  // def findDocWideModalLineVSpacing(alignedBlocksPerPage: Seq[Seq[Seq[Component]]]): Unit = {
  //   val allVDists = for {
  //     groupedBlocks <- alignedBlocksPerPage
  //     block <- groupedBlocks
  //   } yield {
  //     block
  //       .sliding(2).toSeq
  //       .map({
  //         case Seq(a1, a2) =>
  //           val upperLowerLeft = a1.bounds.toPoint(CDir.SW).y
  //           val lowerTopLeft = a2.bounds.toPoint(CDir.NW).y
  //           val vdist = math.abs((lowerTopLeft-upperLowerLeft).asDouble()).toFloatExact
  //           vdist

  //         case Seq(a1) => -1.toFloatExact
  //         case Seq() => -1.toFloatExact()
  //       }).filter(_ > 1.0d.toFloatExact())
  //   }
  //   val vdists = allVDists.flatten

  //   val modalVDist = if (!vdists.isEmpty) {
  //     vtrace.trace(message("Compute docWideModalLineVSpacing"))
  //     getMostFrequentValues(vtrace)(vdists, leftBinHistResolution)
  //       .headOption
  //       .getOrElse(12.0.toFloatExact())
  //   } else {
  //     0d.toFloatExact()
  //   }


  //   pageSegAccum = pageSegAccum.copy(docWideModalVerticalLineDist = modalVDist)
  // }

  val leftBinHistResolution = 1.0d


  // def findLeftAlignedBlocksPerPage(): Seq[Seq[Seq[Component]]] = {
  //   vtrace.trace(begin("findLeftAlignedBlocksPerPage"))

  //   val alignedBlocksPerPage = for {
  //     page <- visualLineOnPageComponents
  //   } yield {

  //     val lefts = page.zipWithIndex
  //       .map({case (l, i) => (l.bounds.left, i)})

  //     vtrace.trace(message(s"Most frequent left-edge text alignment"))
  //     val leftsAndFreqs = getMostFrequentValuesAndFreqs(vtrace)(lefts.map(_._1), leftBinHistResolution)

  //     val commonLeftEdges = leftsAndFreqs.takeWhile(_._2 > 1.0)


  //     def valueIsWithinHistBin(bin: FloatExact, res: Double)(value: FloatExact): Boolean = {
  //       bin-res <= value && value <= bin+res
  //     }


  //     def minAtomId(c: Component): Int = {
  //       c.atoms.map(_.id.unwrap).min
  //     }

  //     // var lineGrouping = Grid.widthAligned(
  //     //   (4, AlignLeft),  // join indicator
  //     //   (7, AlignRight), // left
  //     //   (7, AlignRight), // height
  //     //   (1, AlignRight), // spacing
  //     //   (80, AlignLeft)  // text
  //     // )

  //     val sortedBlocks = page
  //       .sortBy(minAtomId(_))

  //     // vtrace.ifTrace({
  //     //   sortedBlocks.headOption.foreach { line =>
  //     //     lineGrouping = lineGrouping.addRow(
  //     //       "+++",
  //     //       line.left.pp,
  //     //       line.height.pp,
  //     //       " ",
  //     //       line.getTextReflow.map(_.toText.box).getOrElse("?")
  //     //     )
  //     //   }
  //     // })

  //     val groupedBlocks = sortedBlocks
  //       .groupByPairs({ case (line1, line2) =>


  //         val linesAreClustered = commonLeftEdges.exists({ case (leftBin, _) =>
  //           val line1InBin = valueIsWithinHistBin(leftBin, leftBinHistResolution)(line1.left)
  //           val line2InBin = valueIsWithinHistBin(leftBin, leftBinHistResolution)(line2.left)

  //           line1InBin && line2InBin
  //         })

  //         val h1 = line1.height
  //         val h2 = line2.height

  //         val similarLineHeights = h2.withinRange(
  //           h1.plusOrMinus(3.percent)
  //         )

  //         val verticalJump = line2.bounds.bottom - line1.bounds.bottom
  //         val largeVerticalJump = verticalJump > line1.bounds.height*2.0
  //         // val largeVerticalJump = line2.bounds.bottom.withinRange(
  //         //   line1.bounds
  //         // )

  //         val areGrouped = linesAreClustered && similarLineHeights && !largeVerticalJump

  //         // vtrace.ifTrace({
  //         //   lineGrouping = lineGrouping.addRow(
  //         //     if (areGrouped) "  |" else "+++",
  //         //     line2.left.pp, h2.pp,
  //         //     " ",
  //         //     line2.getTextReflow.map(_.toText.box).getOrElse("?")
  //         //   )
  //         // })

  //         areGrouped
  //       })

  //     // vtrace.trace({
  //     //   "block structure" withInfo lineGrouping.toBox()
  //     // })

  //     groupedBlocks
  //   }

  //   vtrace.trace(end("findLeftAlignedBlocksPerPage"))

  //   alignedBlocksPerPage
  // }

  def splitBlocksWithLargeVGaps(
    alignedBlocksPerPage: Seq[Seq[Seq[Component]]]
  ): Seq[Seq[Seq[Component]]] = {
    val modalVDist = pageSegAccum.docWideModalVerticalLineDist
    val maxVDist = modalVDist * 1.4d
    // One last pass through block to split over-large vertical line jumps
    for {
      blocksOnPage <- alignedBlocksPerPage
    } yield {
      // var lineGrouping = Grid.widthAligned(
      //   (4, AlignLeft),  // join indicator
      //   (7, AlignRight), // left
      //   (7, AlignRight), // height
      //   (1, AlignRight), // spacing
      //   (80, AlignLeft)  // text
      // )

      val splitBlocks = for {
        block       <- blocksOnPage
        blockPart   <- block.splitOnPairs({ case (block1, block2) =>
          val b1BottomY = block1.bounds.toPoint(CDir.S).y
          val b2TopY = block2.bounds.toPoint(CDir.N).y
          val vdist = b2TopY - b1BottomY
          val willSplit = vdist > maxVDist
          vtrace.traceIf(willSplit)(message(
            s"splitting TextBlock at w/vdist=${vdist.pp} : ${block1.bounds} / ${block2.bounds} "
          ))

          willSplit
        })
      } yield blockPart

      splitBlocks
    }

  }


  // def joinTextblockReflow(textBlockRegion: Component): Unit = {
  //   val visualLines = for {
  //     vline       <- textBlockRegion.getChildren(LB.VisualLine)
  //     textReflow  <- vline.getTextReflow
  //   } yield textReflow

  //   val joined = visualLines.reduce { joinTextLines(_, _)(utils.EnglishDictionary.global) }
  //   // textBlockRegion.setTextReflow(joined)

  //   // vtrace.trace("Text Block TextReflow" withInfo {
  //   //   s"Joining ${visualLines.length} VisualLines".box atop
  //   //   joined.toText().box
  //   //   // (joined.toText().box atop prettyPrintTree(joined))
  //   // })

  // }

  // def findTextBlocksPerPage(): Seq[RegionComponent] = {
  //   vtrace.trace(begin("findTextBlocks"))

  //   val alignedBlocksPerPage = findLeftAlignedBlocksPerPage()

  //   findDocWideModalLineVSpacing(alignedBlocksPerPage)

  //   val finalBlockStructure = splitBlocksWithLargeVGaps(alignedBlocksPerPage)

  //   val pages = for {
  //     pageBlocks <- finalBlockStructure
  //   } yield{

  //     val pageTextBlockCCs = for {
  //       textBlock <- pageBlocks
  //       (textBlockRegionCC, textBlockTargeetRegion) <- mpageIndex.labelRegion(textBlock, LB.TextBlock)
  //     } yield {
  //       assert(textBlock.forall(_.hasLabel(LB.VisualLine)))
  //       textBlockRegionCC.setChildren(LB.VisualLine, textBlock)

  //       // joinTextblockReflow(textBlockRegion)
  //       textBlockRegionCC
  //     }

  //     sortPageTextBlocks(pageTextBlockCCs)
  //   }


  //   vtrace.trace(end("findTextBlocks"))
  //   pages.flatten
  // }

  def sortPageTextBlocks(pageTextBlocks: Seq[Component]): Option[RegionComponent] = {
    // TODO: actually sort these?
    mpageIndex
      .labelRegion(pageTextBlocks, LB.PageTextBlocks)
      .map({ case (page, pageTargetRegionn) =>
        page.setChildren(LB.TextBlock, pageTextBlocks)
        page
      })
  }



  def runPageSegmentation(): Unit = {
    runLineDetermination()
  }

  val withinAngle = filterAngle(docOrientation, math.Pi / 3)

  def fillInMissingChars(pageNum: Int@@PageNum, lineBinChars: Seq[AtomicComponent]): Seq[AtomicComponent] = {
    // try to fill in short gaps in char id sequences, as a rough stop-gap (no pun intended)
    //  to compensate for any obvious missing
    val ids = lineBinChars.map(_.id.unwrap)

    val __debug = false
    var __num_filled_in = 0
    val maybeFilledInPairs = for {
      idpair <- ids.sorted.sliding(2)
    } yield {
      val min = idpair.min
      val max = idpair.max
      if (max-min > 5) {
        Seq(min, max)
      } else {
        if (__debug) {
          __num_filled_in += math.min(0, (min to max).length-2)
        }
        (min to max)
      }
    }

    val res = maybeFilledInPairs
      .toList
      .flatten
      .toSet
      .toList
      .map{ id: Int =>
        mpageIndex.getComponent(ComponentID(id), pageNum).asInstanceOf[AtomicComponent]
      }
      .map(x => (x.bounds.left.unwrap, x))
      .sortBy(_._1)
      .map(_._2)


    if (__debug) {
      if (ids.length != res.length) {
        println(s"""fillInMissingChars: starting char count=${ids.length}""")
        println(s"""                   > added              + ${__num_filled_in}""")
        println(s"""                   > final count        ${res.length} """)

        val startingLine = lineBinChars.map(_.char).mkString
        val endingLine = res.map(_.char).mkString

        println(s""" >>>${startingLine}<<""")
        println(s""" >>>${endingLine}<<""")
      }
    }


    res
  }


  def splitRunOnLines(lineBins: Seq[Seq[AtomicComponent]]): Seq[Seq[AtomicComponent]] = {
    for {
      lineBin <- lineBins
      shortLine <- lineBin.splitOnPairs({ (ch1, ch2) =>
        ch2.id.unwrap - ch1.id.unwrap > 10
      })
    } yield shortLine
  }

  // def gutterDetection(
  //   pageId: Int@@PageNum,
  //   components: Seq[AtomicComponent]
  // ): Unit = {
  //   vtrace.trace(begin("GutterDetection"))
  //   vtrace.trace(message(s"page ${pageId}"))
  //   // Find most common left Xs
  //   // starting w/most frequent left-x (lx0), try to construct the largest whitespace boxes
  //   // with right-x=lx0-epsilon, left=?
  //   // line-bin coarse segmentation
  //   val lefts = components.map(_.left)
  //   val hist = histogram(lefts, 0.1d)
  //   val freqLefts = hist.getFrequencies
  //     .sortBy(_.frequency)
  //     .reverse
  //     .takeWhile(_.frequency > 2.0)

  //   vtrace.trace(vtraceHistogram(hist))

  //   // val epsilon = 0.01d
  //   freqLefts.foreach({leftXBin =>
  //     // val leftX = leftXBin.value
  //     // LTBounds(
  //     //   left=leftX-epsilon,
  //     //   top=pageMin,
  //     //   width=
  //     // )
  //   })

  //   vtrace.trace(end("GutterDetection"))
  // }

  // import utils.{Debugging => Dbg}

  def determineLines(
    pageId: Int@@PageID,
    pageNum: Int@@PageNum,
    components: Seq[AtomicComponent]
  ): Unit = {

    def minRegionId(ccs: Seq[AtomicComponent]): Int@@CharID =  ccs.map(_.charAtom.id).min


    // line-bin coarse segmentation
    val lineBinsx = approximateLineBins(components)


    val shortLines = splitRunOnLines(lineBinsx)

    // if (dbgmode) {
    //   println("short lines")
    //   println(
    //     shortLines.map(_.map(_.char).mkString)
    //       .mkString("\n  ", "\n  ", "\n")
    //   )
    // }

    val lineIdSets = new DisjointSets[Int](components.map(_.id.unwrap))
    for {
      line <- shortLines.map(fillInMissingChars(pageNum, _))
      if !line.isEmpty
      firstChar = line.head.id.unwrap
      char <- line.tail
    } {
      lineIdSets.union(char.id.unwrap, firstChar)
    }

    val shortLinesFilledIn = lineIdSets.iterator().toSeq
      .map{  line =>
        line.map{ id =>
          mpageIndex.getComponent(ComponentID(id), pageNum).asInstanceOf[AtomicComponent]
        }
      }
      .map(_.toSeq.sortBy(c => (c.bounds.left, c.bounds.top)))
      .sortBy(line => minRegionId(line))


    // if (dbgmode) {
    //   println("short lines filled in")
    //   println(
    //     shortLinesFilledIn.map(_.map(_.char).mkString)
    //       .mkString("\n  ", "\n  ", "\n")
    //   )
    // }


    val longLines = shortLinesFilledIn
      .groupByPairs({ (linePart1, linePart2) =>
        val l1 = linePart1.last
        val l2 = linePart2.head
        val l1max = l1.charAtom.id.unwrap
        val l2min = l2.charAtom.id.unwrap
        val idgap = l2min - l1max
        val leftToRight = isStrictlyLeftToRight(l1, l2)
        val overlapped = isOverlappedVertically(l1, l2)
        val smallIdGap = idgap < 5

        val shouldJoin = leftToRight && overlapped && smallIdGap

        vtrace.traceIf(shouldJoin)("joining line parts" withInfo {
          val chs1 = linePart1.map(_.chars).mkString
          val chs2 = linePart2.map(_.chars).mkString
          s"""$chs1  <->  $chs2 """
        })


        shouldJoin
      })
      .sortBy {  lineGroups =>
        val bounds = lineGroups.head.head.bounds
        (bounds.bottom, bounds.left)
      }

    val visualLineLabelId = docStore.ensureLabel(LB.VisualLine)

    val pageLines = longLines.map({ lineGroups =>

      val line = lineGroups.reduce(_ ++ _)

      // Glue together page atoms into a VisualLine/TextSpan
      mpageIndex.labelRegion(line, LB.VisualLine)
        .map ({ case (visualLine, targetRegion) =>
          visualLine.setChildren(LB.PageAtom, line.sortBy(_.bounds.left))
          visualLine.cloneAndNest(LB.TextSpan)

          val regionId = targetRegion.id

          val alreadyZoned = docStore.getZoneForRegion(regionId, LB.VisualLine).isDefined

          if (alreadyZoned) {
            vtrace.trace("WARNING" withInfo s"VisualLine Zone already exists for ${targetRegion}, skipping")
            None
          } else {
            val zoneId = docStore.createZone(regionId, visualLineLabelId)

            visualLine.tokenizeLine.foreach { textReflow =>
              vtrace.trace{ "Final Tokenization" withInfo textReflow.toText }

              docStore.setTextReflowForZone(zoneId, textReflow)
            }

            Some(visualLine)
          }
        })
    }).flatten.flatten


    val ySortedLines = pageLines

    mpageIndex
      .labelRegion(ySortedLines, LB.PageLines)
      .map{ case (comp, targetRegion) =>
        comp.setChildren(LB.VisualLine, ySortedLines)
      }

  }


  def lineWidthMatches(line: Component, width: FloatExact): Boolean  = {
    // line.determineNormalTextBounds.width.eqFuzzy(0.5d)(width)
    line.width.eqFuzzy(0.5d)(width)
  }
  def lineHeightMatches(line: Component, height: FloatExact): Boolean  = {
    // line.determineNormalTextBounds.height.eqFuzzy(0.5d)(height)
    line.height.eqFuzzy(0.5d)(height)
  }

  def lineDimensionsMatch(line: Component, hw: Point): Boolean = {
    lineWidthMatches(line, hw.x) && lineHeightMatches(line, hw.y)
  }


  def printPageLineBins(bin: LineDimensionBins, indent: Int=0): Unit = {
    bin.widthBin
      .sortBy(_._1)
      .filter({ case (width, lines) => !lines.isEmpty })
      .foreach({ case (width, lines) =>
        println(" "*indent + s"Lines within width ${width}")
        lines.sortBy(_.bounds.top).foreach{ line =>
          println("  "*indent + s"w:${line.bounds.width.pp}, h:${line.bounds.height.pp} ${line.bounds.prettyPrint} > ${line.chars}")
        }
      })
  }

  def printCommonLineBins(lineBins: Seq[LineDimensionBins]): Unit = {
    lineBins.zipWithIndex.toList.foreach{ case (bin, pnum) =>
      // println(s"Page $pnum")
      printPageLineBins(bin, 2)
    }
  }

  def visualLineOnPageComponents: Seq[Seq[Component]] =
    mpageIndex
      .getDocumentVisualLines
      .map(_.sortBy(_.bounds.top))

  // } yield {
  //   val page = mpageIndex.getPageIndex(pageId)
  //   val pageLiness = page.getComponentsWithLabel(LB.PageLines)
  //   assert(pageLiness.length==1)
  //   val pageLines = pageLiness.head
  //   val lls = pageLines.getChildren(LB.VisualLine)

  //   lls.sortBy { _.bounds.top }
  // }


  def focalJump(c1: Component, c2: Component): Double = {
    val c1East = c1.bounds.toPoint(CDir.E)
    val c2West = c2.bounds.toPoint(CDir.W)
    c1East.dist(c2West)
  }

  def findMostFrequentFocalJumps():  Unit = {
    val allWidthsAndDists = for {
      pageBin <- pageSegAccum.lineDimensionBins
      ((width, widthFreq), linesWithFreq)  <- pageBin.widthBin

    } yield {

      val sameCenterGroups = linesWithFreq.clusterBy((a, b) => a.hasSameLeftEdge(0.01d)(b))
      /// print out a list of components
      // val grouped = sameCenterGroups.map({ group =>
      //   group.map({comp =>
      //     s"""comp:> ${comp.toText}"""
      //   }).mkString("\n   ", "\n   ", "\n")
      // }).mkString("---------")
      // println(s"width=${width} freq=${widthFreq}, groups page ${pageBin.page}")
      // println(grouped)

      val widthsAndDists = sameCenterGroups.map{ group =>
        val sorted = group
          .sortBy(_.bounds.top)

        val dists = sorted
          .sliding(2).toSeq
          .map({
            case Seq(c1, c2)  => focalJump(c1, c2)
            case _            => 0d
          })

        dists :+ 0d

        sorted.map(_.bounds.width).zip(dists)
      }
      widthsAndDists.flatten
    }

    val focalJumps = allWidthsAndDists
      .flatten
      .groupBy(_._1)
      .map({case (k, v) => (k.pp, v.map(_._2.pp))})

    // val jstr = focalJumps.map({case (k, v) => s"""w:${k} = [${v.mkString(", ")}]"""})
    // val jcol = jstr.mkString("\n   ", "\n   ",  "\n")

    // println(s""" focalJumps: ${jcol} """)

    pageSegAccum = pageSegAccum.copy(
      commonFocalJumps = focalJumps
    )
  }



  // def findMostFrequentLineDimensions():  Unit = {

  //   val allPageLines = for {
  //     p <- visualLineOnPageComponents; l <- p
  //   } yield l

  //   val allDocumentWidths = allPageLines.map(_.bounds.width)

  //   val topWidths = getMostFrequentValuesAndFreqs(vtrace)(allDocumentWidths, 0.2d).toList
  //   val topNWidths = topWidths.takeWhile(_._2 > 1.0)
  //   // Common width meaning from largest->smallest:
  //   //    left/right justified line width
  //   //    paragraph-starting indented line width
  //   //    reference width(s), for hanging-indent first line, remaining lines
  //   //    other l/r justified blocks (abstract, e.g)

  //   // println(s"""common widths = ${topNWidths.mkString(", ")}""")

  //   // bin each page by line widths
  //   val commonLineBins = for {
  //     (plines, pagenum) <- visualLineOnPageComponents.zipWithIndex
  //   } yield {

  //     val remainingLines = mutable.ListBuffer[Component](plines:_*)
  //     val widthBins = mutable.ArrayBuffer[((FloatExact, Double), Seq[Component])]()

  //     topNWidths.foreach{ case (width, wfreq) =>
  //       val mws = remainingLines.filter(lineWidthMatches(_, width))
  //       widthBins.append(((width -> wfreq), mws))
  //       remainingLines --= mws
  //     }

  //     LineDimensionBins(PageNum(pagenum), widthBins, remainingLines)
  //   }
  //   printCommonLineBins(commonLineBins)
  //   pageSegAccum = pageSegAccum.copy(
  //     lineDimensionBins = commonLineBins
  //   )
  // }

  def labelAbstract(): Unit = {
    vtrace.trace(begin("LabelAbstract"))

    // // find the word "abstract" in some form, then,
    // // if the text block containing "abstract" is a single line,
    // //    take subsequent text blocks until we take a multiline
    // // else if the text block is multi-line, take that block to be the entire abstract
    // val lineBioLabels = mpageIndex.bioLabeling("LineBioLabels")
    // val blocks = selectBioLabelings(LB.TextBlock, lineBioLabels)

    // vtrace.trace(message(s"TextBlock count: ${blocks.length}"))

    // val maybeLookingAtAbstract = blocks.dropWhile { tblines =>
    //   tblines.headOption.exists { l1 =>
    //     val lineComp = l1.component
    //     val lineText = lineComp.chars
    //     val isAbstractHeader =  """^(?i:(abstract|a b s t r a c t))""".r.findAllIn(lineText).length > 0
    //       !isAbstractHeader
    //   }
    // }


    // if (maybeLookingAtAbstract.length > 0) {
    //   val firstBlockIsMultiline = maybeLookingAtAbstract.headOption.exists(_.length > 1)
    //   if (firstBlockIsMultiline) {
    //     // label this as the abstract
    //     maybeLookingAtAbstract.headOption.foreach { abs =>
    //       mpageIndex.addBioLabels(LB.Abstract, abs)

    //       vtrace.trace("Found Abstract in multi-line block" withTrace
    //         all(abs.map(b => showComponent(b.component))))
    //     }
    //   } else {
    //     val singleLines = maybeLookingAtAbstract.takeWhile { tblines =>
    //       tblines.length == 1
    //     }
    //     val absBlock = maybeLookingAtAbstract.drop(singleLines.length).headOption.getOrElse(Seq())
    //     val totalABs = singleLines :+ absBlock

    //     mpageIndex.addBioLabels(LB.Abstract, totalABs.flatten)

    //     vtrace.trace("Found Abstract in single-line block" withTrace all(
    //       totalABs.flatMap(_.map(b => showComponent(b.component)))
    //     ))


    //   }
    // } else {
    //   // println("abstract is unlabled")
    //   // abstract is unlabled, look for the first multi-line text block before the intro?
    //   var found = false
    //   val allBlocksBeforeIntro = blocks.takeWhile { tblines =>
    //     tblines.headOption.exists { l1 =>
    //       // l1 is a BioNode
    //       val lineComp = l1.component
    //       val lineText = lineComp.chars
    //       //todo: might accidentally mistake title for introduction - throwing out multi-work lines to fix this
    //       val isIntroHeader =
    //       {"""introduction""".r.findAllIn(lineText.toLowerCase).length > 0 && lineText.split(" ").length == 1}
    //       if(isIntroHeader){
    //         //println("found intro header")
    //         found = true
    //       }
    //       !isIntroHeader
    //     }
    //   }
    //   // todo: fix so that it checks for other labels
    //   // todo: handle case where the intro isn't labeled either (should we attempt to label things in this case?)
    //   // find first multiline block before intro and label it as abstract, if it has no other labelings
    //   if (found && allBlocksBeforeIntro.length > 0) {
    //     //todo: this is for testing, remove eventually
    //     allBlocksBeforeIntro.foreach(block => {
    //       //block.foreach(node => println(node.component.toText))
    //       //println
    //     })

    //     val lastMultilineBeforeIntro = allBlocksBeforeIntro.lastIndexWhere(_.length > 3)
    //     if (lastMultilineBeforeIntro != -1) {
    //       // label this as the abstract
    //       allBlocksBeforeIntro.get(lastMultilineBeforeIntro).foreach { abs =>
    //         mpageIndex.addBioLabels(LB.Abstract, abs)
    //         //println("labeling as part of abstract: " + abs.component.toText)
    //         // vtrace.trace(
    //         //   vtrace.link(
    //         //     vtrace.all(
    //         //       abs.map(b => vtrace.showComponent(b.component))
    //         //     ),
    //         //     vtrace.showLabel(LB.Abstract)
    //         //   )
    //         // )


    //       }
    //     }
    //   }
    // }

    vtrace.trace(end("LabelAbstract"))
  }

  def labelSectionHeadings(): Unit = {
    vtrace.trace(begin("LabelSectionHeadings"))
    // val lineBioLabels = mpageIndex.bioLabeling("LineBioLabels")
    // for {
    //   lineBioNode <- lineBioLabels
    //   lineComp = lineBioNode.component
    //   numbering = lineComp.chars.takeWhile(c => c.isDigit || c=='.')
    //   nexts = lineComp.chars.drop(numbering.length) //.takeWhile(c => c.isLetter)
    //   isTOCLine = """\.+""".r.findAllIn(nexts).toSeq.sortBy(_.length).lastOption.exists(_.length > 4)

    //   if !numbering.isEmpty && !nexts.isEmpty()
    //   if numbering.matches("^[1-9]+\\.([1-9]\\.)*")
    //   if !isTOCLine
    // } {

    //   // vtrace.trace("Labeled section heading" withInfo
    //   //   show(lineBioNode.component))

    //   mpageIndex.addBioLabels(LB.SectionHeadingLine, lineBioNode)
    // }

    vtrace.trace(end("LabelSectionHeadings"))

    // What are the predominant fonts per 'level'?
    // Distinguish between TOC and in-situ section IDs
    // look for rectangular blocks of text (plus leading/trailing lines)
    // look for for left-aligned (column-wise) single or double numbered text lines w/ large gaps
    // filter out figure/caption/footnotes based on embedded images and page locations
  }

  def labelTitle(): Unit = {
    // val lineBioLabels = mpageIndex.bioLabeling("LineBioLabels")
    // // look for lines with biggest font within first [x] lines of paper
    // // get rid of lines with length less than some arbitrary length (to weed out some weird cases)
    // val biggestLineAtBeginning = lineBioLabels.take(50)
    //   .filter(_.component.chars.length() > 5)
    //   .sortWith(_.component.height > _.component.height)

    // // for debugging, print out all the lines sorted in order from largest to smallest
    // //    biggestLineAtBeginning.foreach(node => println(node.component.chars))
    // //    println

    // if(biggestLineAtBeginning.headOption.isDefined) {
    //   // println("Title candidate: " + biggestLineAtBeginning.headOption.get.component.chars)
    //   mpageIndex.addBioLabels(LB.Title, biggestLineAtBeginning.headOption.get)
    //   println
    // } else {
    //   println("there isn't a biggest line?")
    // }
  }




  // def groupVisualTextBlocks(
  //   colX: Double, textRectCandidates: Seq[Component], remainingPageLines: Seq[Component]
  // ): Seq[Seq[Component]] = {
  //   val unusedLines = mutable.ArrayBuffer[Component](remainingPageLines:_*)
  //   val usedLines = mutable.ArrayBuffer[Component]()

  //   val ySortedLines = textRectCandidates.sortBy(_.bounds.top)
  //   val topLine = ySortedLines.head
  //   // val bottomLine = ySortedLines.last


  //   val possibleCand = remainingPageLines
  //     .diff(ySortedLines)
  //     .filterNot(candidateIsOutsideLineBounds(_, topLine))

  //   val commonJumps = pageSegAccum.commonFocalJumps


  //   val totalLinesSorted =  (possibleCand ++ ySortedLines).sortBy(_.bounds.top)

  //   totalLinesSorted.
  //     sliding(2).toSeq
  //     .map({
  //       case Seq(upper, lower) =>
  //         val jump = focalJump(upper, lower)
  //         val ujumps = commonJumps(upper.bounds.width.pp)
  //         val ljumps = commonJumps(lower.bounds.width.pp)
  //         val jumps = ujumps ++ ljumps

  //         if (jumps contains jump.pp) {
  //           println("common jump found")
  //         }

  //       case Seq(lone) => 0d
  //       case Seq() => 0d
  //     })

  //   // Walk down column lines pair-wise, and take while diagonal distances match


  //   // mpageIndex.connectComponents(totalLineSorted, LB.Block)
  //   // totalLineSorted

  //   ???
  // }

}



  // def determineTextBlockOrdering(): Unit = {
  //   vtrace.trace(begin("determineTextBlockOrdering"))
  //   textBlocks.splitOnPairs({
  //     // case (aNodes: Seq[BioNode], bNodes: Seq[BioNode]) =>
  //     case (block1, block2) =>
  //       val block1Lines = block1.getChildren(LB.VisualLine)
  //       val block2Lines = block2.getChildren(LB.VisualLine)
  //       // if, for consecutive blocks a, b features include
  //       //   - a is length 1
  //       //   - a is indented (determine std indents)
  //       //   - a's width falls strictly within b's width
  //       //   - dist a -> b is near doc-wide standard line distance
  //       //   - a is at end of column, b is higher on page, or next page
  //       val onSamePage = true
  //       val aIsSingeLine = block1Lines.length == 1
  //       val aIsAboveB = isStrictlyAbove(block1Lines.last, block2Lines.head)
  //       val aComp = block1Lines.last
  //       val bComp = block2Lines.head
  //       val aWidth = aComp.bounds.width
  //       val bWidth = bComp.bounds.width

  //       val vdist = aComp.vdist(bComp)
  //       val withinCommonVDist = vdist.eqFuzzy(1.0)(pageSegAccum.docWideModalVerticalLineDist)
  //       val aWithinBsColumn = bComp.columnContains(aComp)

  //       val aIsIndented = aWidth < bWidth - 4.0

  //       // println("comparing for para begin: ")
  //       // println(s"  a> ${aComp.chars}")
  //       // println(s"  b> ${bComp.chars}")

  //       // println(s"     a.bounds=${aComp.bounds.prettyPrint} b.bounds=${bComp.bounds.prettyPrint}")
  //       // println(s"     a.right=${aComp.bounds.right.pp} b.right=${bComp.bounds.right.pp}")
  //       // println(s"     a is indented = ${aIsIndented}")
  //       // println(s"     a strictly above = ${aIsAboveB}")
  //       // println(s"     b columnContains a = ${aWithinBsColumn}")
  //       // println(s"     a/b within modal v-dist = ${withinCommonVDist} = ${modalVDist}")

  //       if (onSamePage &&
  //         aIsSingeLine &&
  //         aIsAboveB &&
  //         aIsIndented &&
  //         withinCommonVDist &&
  //         aWithinBsColumn
  //       ) {
  //         // mpageIndex.addBioLabels(LB.ParaBegin, block1Lines)
  //       }

  //       true
  //   })


  //   vtrace.trace({
  //     val blockStrs = textBlocks.map{ block =>
  //       block.getChildren(LB.VisualLine).map(b => b.getTextReflow.map(_.toText()).getOrElse("<no text>"))
  //         .mkString("Block\n    ", "\n    ", "\n")
  //     }
  //     val allBlocks = blockStrs.mkString("\n  ", "\n  ", "\n")

  //     "Final Block Structure" withTrace message(allBlocks)
  //   })
  //   vtrace.trace(end("determineTextBlockOrdering"))

  // }