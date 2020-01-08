package vcfotographer

import vcfotographer.Basic._

import java.io.PrintWriter
import java.io.File
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.BufferedOutputStream

import scala.io.Source
import java.util.zip.GZIPOutputStream
import java.io.OutputStream
import java.io.FileOutputStream

// Warning : Entries are zero indexed in the bed file

object BedSignalToolBox {

    val THRESHOLD_JUMP = 10
    val THRESHOLD_FALL = 10
    val THRESHOLD      = 10

    case class BedSpan(chr: String, start: Int, stop: Int)
    case class BedEntry[T](span: BedSpan, value: T)
    //case class BedEntry[Int](span: BedSpan, value: Int)
    //case class BedSpanS(chr: String, start: Int, stop: Int)
    //case class BedEntry[Double](span: BedSpanS, value: Double)
    case class Jump(span: BedSpan, height: Int)
    case class Fall(span: BedSpan, height: Int)

    /** Compares two entries if their value differs by more than a threshold
     *  Returns a value if so else none */
    def compareSeqEntries(e: Seq[BedEntry[Int]]): Any = {
        e match {
            case Seq(e1, e2) => {
                val sub = e1.value - e2.value
                val up = if (sub < 0) true else false
                val diff = Math.abs(sub)
                val span = BedSpan(e2.span.chr, e2.span.start, e2.span.start + 1)

                if (up && (diff > THRESHOLD_JUMP)) {
                    Jump(span, diff)
                } else if (!up && (diff > THRESHOLD_FALL)) {
                    Fall(span, diff)
                } else
                    None
            }
        }
    }

    /** Will append an entry to a list if there is a jump else returns the list */
    def appendEntryIf(e: Seq[BedEntry[Int]], acc: List[BedEntry[Int]]) = {
        compareSeqEntries(e) match {
            case j: Jump => BedEntry[Int](j.span, j.height) :: acc
            case f: Fall => BedEntry[Int](f.span, f.height) :: acc
            case _ => acc
        }
    }


    def appendEntryIfJump(e: Seq[BedEntry[Int]], acc: List[BedEntry[Int]], threhsold: Int = THRESHOLD) = {
        e match {
            case Seq(e1, e2) => {
                // The entries must be contiguous (this test may be removed if the entries are dense)
                if ((e1.span.chr == e2.span.chr) && (e1.span.stop == e2.span.start)) {
                    val sub = e2.value - e1.value

                    if (Math.abs(sub) >= threhsold) {
                        val span = BedSpan(e2.span.chr, e2.span.start, e2.span.start + 1)
                        BedEntry[Int](span, sub) :: acc
                    } else {
                        acc
                    }
                } else {
                    acc
                }
            }
        }
    }

    /** Reads a string line and creates a Bedentry from it
     *  This may fail because of malformed entries */
    def intBedEntryFromLine(line: String) = {
        lazy val split = line.split("\\t")
        BedEntry(BedSpan(split(0), split(1).toInt, split(2).toInt), split(3).toInt)
    }

    /** Entry with a double valued value */
    def doubleBedEntryFromLine(line: String) = {
        lazy val split = line.split("\\s+") // Split on any whitespaces
        BedEntry(BedSpan(split(0), split(1).toInt, split(2).toInt), split(3).toDouble)
    }

    /** Entry with string for everything after position */
    def stringBedEntryFromLine(line: String) = {
        lazy val split = line.split("\\t")
        BedEntry(BedSpan(split(0), split(1).toInt, split(2).toInt), split.drop(3).mkString("\t"))
    }

    /** Format a bedSpanS */
    def stringFromBedSpan(span: BedSpan) = {
        span.chr + "\t" + span.start.toString + "\t" + span.stop.toString
    }

    /** Format the entry above */
    def stringFromBedEntry(entry: BedEntry[Any]) = {
        stringFromBedSpan(entry.span) + "\t" + entry.value.toString
    }

    /** Reads bed entries from a file */
    def readBedEntriesFromFile[T](file: String, extractor: (String) => BedEntry[T]) = {
        lazy val lines = getLines(file)

        lines filterNot {line => line.startsWith("#") || line.startsWith("track")} map extractor
    }

    def findLowCoverage(file: String, threshold: Int) = {
        lazy val entries = readBedEntriesFromFile(file, intBedEntryFromLine)

        entries filterNot {_.value > threshold}
        //entries foreach {e => println(e.value)}
    }

    def generateLowCoverageWarningFile(l: Iterator[BedEntry[Int]], fileName: String) = {
        val writer = new PrintWriter(new File(fileName))

        l foreach { e =>
            writer.write(e.span.chr.toString + "\t" + e.span.start.toString +
            "\t" + e.span.stop.toString + "\t" + "Low Coverage !" + "\n")
        }
        writer.close()
    }

    def generateJumpsFromFile(file: String, threshold: Int = THRESHOLD) = {
        (readBedEntriesFromFile(file, intBedEntryFromLine) sliding 2).foldLeft(List(): List[BedEntry[Int]])((acc, e) => appendEntryIfJump(e, acc, threshold)).reverse
    }

    def generateFileFromBedEntries[T](l: List[BedEntry[T]], fileName: String) = {
        val writer = new PrintWriter(new File(fileName))

        l foreach { e =>
            writer.write(e.span.chr.toString + "\t" + e.span.start.toString +
                "\t" + e.span.stop.toString + "\t" + e.value.toString + "\n")
        }

        writer.close()

    //        Source.fromFile(fileName) foreach {print(_)}
    }

    /** Extract jumps from mosdepth coverage count (per-base) */
    def extractJumpsToFile(inputFile: String, outputFile: String, threshold: Int) = {
        val bw = new BufferedWriter(new FileWriter(new File(outputFile)))

        (((getLines(inputFile) filterNot {_.startsWith("#")}) map intBedEntryFromLine) sliding(2)) foreach {
            s => s match {
                case Seq(first, second) => {
                    if ((first.span.chr == second.span.chr) && (first.span.stop == second.span.start)) {
                        // Contiguous
                        val diff = second.value - first.value
                        if (Math.abs(diff) > threshold) {
                            // Jump Found
                            bw.write(second.span.chr + "\t" + second.span.start.toString + "\t" + (second.span.start + 1).toString + "\t" + diff.toString + "\n")
                        }
                    }
                    else {
                        // Non contiguous (results will be marked with "*")

                        // We suppose that the coverage is 0 after the region
                        if (first.value > threshold) {
                            // Start found
                            bw.write(first.span.chr + "\t" + (first.span.stop - 1).toString + "\t" + first.span.stop.toString + "\t" + (-first.value).toString + " * \n")
                        }
                        // We suppose coverage is 0 before region
                        if (second.value > threshold) {
                            // Stop found
                            bw.write(second.span.chr + "\t" + second.span.start.toString + "\t" + (second.span.start + 1).toString + "\t" + second.value.toString + " * \n")
                        }
                    }
                }
            }
        }
        bw.close
    }

    /** Extracts regions in read count that satisfy a predicate */
    def filterRegions[A](inputFile: String, outputFile: String, pred: (BedEntry[Int]) => Boolean) = {
        // Example of usage : filterRegions("input.bed", "output.bed", e => e.value < 20)
        val bw = new BufferedWriter(new FileWriter(new File(outputFile)))

        (getLines(inputFile) filterNot {_.startsWith("#")}) foreach {
            line => {
                if (pred(intBedEntryFromLine(line))) {
                    bw.write(line + "\n")
                }
            }
        }
    }

    /** Create chromosome read count sorted map */
    def createChrRCMap(bedPerBaseFile: String, chr: String) = {
        // The read count is indexed by position, (start -> count)
        // Each time the count changes there is a new entry (start -> count)
        lazy val sm = scala.collection.SortedMap.from(io.Source.fromFile(bedPerBaseFile).getLines filterNot {_.startsWith("#")} map {
            intBedEntryFromLine(_)
        } filter {_.span.chr == chr} map {
            e => (e.span.start, e.value)
        })
        sm
        // This can be queried with sm.range(start, stop)
        // Uses the same indexing as the bed file (e.g., from mosdepth, zero indexed)
        // TODO: Make indexing consistent
    }

    /** Find jumps in map (1-indexed)
     *  TODO: Fix chromosome entry (this is passed as an argument for now)
    */
    def findJumpsInRCMap(rcmap: scala.collection.SortedMap[Int, Int], start: Int, stop: Int, threhsold: Int, chr: String) = {
        // This takes the counts two-by-two and if they differ by more than the threshold we generate a bed entry
        (rcmap.range(start, stop).toSeq sliding 2) map {
            x => x match {
                case Seq(x,y) => (y._1, Math.abs(x._2 - y._2) > threhsold, y._2 - x._2)
            }
        } filter {_._2} map {
            x => BedEntry[Int](BedSpan(chr, x._1, x._1 + 1), x._3)
        }
    }

    /** Extract regions for which a predicate is true */
    def filterRegionsOnPredicate[T](it: Iterator[BedEntry[T]], pred: (BedEntry[T]) => Boolean) = {
        it filter pred
    }

    /** Join contiguous regions and replacing the values by a comment */
    // Warning ! this does not seem to be lazy !
    // Warning ! this does not seem to work correctly !
    def joinContiguousRegions[T](it: Iterator[BedEntry[T]], comment: String = "Joined") = {
        // Note : This merging algorithm could be done by joining entries two by two when they are contiguous
        // however that would be an O(nlogn) algorithm where n is the number of entries.
        // Here an accumulator is created by traversing the list only once and the accumulator is traversed
        // to make the new list, this is an O(n) algorithm where n is the number of entries.
        case class Pos(chr: String, pos: Int)
        case class Brk(startingPoints: List[Pos], stoppingPoints: List[Pos])
        case class Acc(breakPoints: Brk, lastEntry: Option[BedEntry[T]])

        def updateBreakPoints(acc: Acc, elem: BedEntry[T]) = {
            (acc.lastEntry, elem) match {
                case (None, elem) => Brk(Pos(elem.span.chr, elem.span.start) :: acc.breakPoints.startingPoints, acc.breakPoints.stoppingPoints)
                case (Some(entry), elem) if ((entry.span.chr != elem.span.chr) || (entry.span.stop != elem.span.start)) =>
                    Brk(Pos(elem.span.chr, elem.span.start) :: acc.breakPoints.startingPoints, Pos(entry.span.chr, entry.span.stop) :: acc.breakPoints.stoppingPoints)
                case _ => acc.breakPoints
            }
        }

        lazy val results = it.foldLeft(Acc(Brk(List(), List()), None))((acc, elem) => {
            Acc(updateBreakPoints(acc, elem), Some(elem))
        })

        lazy val startIt = results.breakPoints.startingPoints.reverseIterator
        lazy val stopIt = {
            (results.lastEntry match {
                case Some(entry) => Pos(entry.span.chr, entry.span.stop) :: results.breakPoints.stoppingPoints
                case None => results.breakPoints.stoppingPoints
            }).reverseIterator
        }

        (startIt zip stopIt) map {
            _ match {
                case (start, stop) => {
                    // Make sure the regions are coherent
                    assert(start.chr == stop.chr)

                    BedEntry(BedSpan(start.chr, start.pos, stop.pos), comment)
                }
            }
        }
    }

    /** Joins contiguous regions based on a predicate */
    def joinFilteredContiguousRegions[T](it: Iterator[BedEntry[T]], pred: (BedEntry[T]) => Boolean, comment: String = "Joined") = {
        joinContiguousRegions(filterRegionsOnPredicate(it, pred), comment)
    }

    /** Extracts regions when a predicate becomes true, ends regions predicate becomes false or when chromosome changes
     *
     *  This function will merge regions even if not contiguous and does suppose regions are in ascending order, if not
     *  the behavior may not be as expected
    */
    @deprecated("this method will be removed", "BedSignalToolBox")
    def extractRegionsOnPredicateX[T](it: Iterator[BedEntry[T]], pred: (BedEntry[T]) => Boolean) = {
        case class Pos(chr: String, pos: Int)
        case class Acc(startingPoints: List[Pos], stoppingPoints: List[Pos], lastPred: Boolean, lastEntry: Option[BedEntry[T]])

        // Starting conditions
        def startingPoints(acc: Acc, elem: BedEntry[T]) = {
            (acc.lastPred, pred(elem), acc.lastEntry) match {
                // If predicate becomes true or if true and on new chromosome we add a starting point
                case (false, true, _) => Pos(elem.span.chr, elem.span.start) :: acc.startingPoints
                case (true, true, Some(entry)) if (entry.span.chr != elem.span.chr) => Pos(elem.span.chr, elem.span.start) :: acc.startingPoints
                // Else we don't
                case _ => acc.startingPoints
            }
        }

        // Stopping conditions
        def stoppingPoints(acc: Acc, elem: BedEntry[T]) = {
            (acc.lastPred, pred(elem), acc.lastEntry) match {
                // If predicate becomes false or if new chromosome we add a stopping point
                case (true, false, Some(entry)) => Pos(entry.span.chr, entry.span.stop) :: acc.stoppingPoints
                case (true, true, Some(entry)) if (entry.span.chr != elem.span.chr) => Pos(entry.span.chr, entry.span.stop) :: acc.stoppingPoints
                // Else we don't
                case _ => acc.stoppingPoints
            }
        }

        // Fold over ranges
        lazy val results = it.foldLeft(Acc(List(), List(), false, None))((acc, elem) => {
            Acc(startingPoints(acc, elem), stoppingPoints(acc, elem), pred(elem), Some(elem))
        })

        // This may be removed when the function is validated
        //println("Starting points size : " + results.startingPoints.size + " Stopping points size : " + results.stoppingPoints.size)
        assert(results.startingPoints.size - results.stoppingPoints.size <= 1)

        lazy val (startIt, stopIt) = {
            // The last stopping point may be missing
            (results.startingPoints.reverseIterator,
                (if (results.startingPoints.size > results.stoppingPoints.size) {
                    results.lastEntry match {
                        // either use the last entry
                        case Some(entry) => Pos(entry.span.chr, entry.span.stop) :: results.stoppingPoints
                        // or duplicate the last starting point (single base)
                        case None => results.startingPoints.head :: results.stoppingPoints
                    }
                } else {
                    results.stoppingPoints
                }).reverseIterator
            )
        }

        (startIt zip stopIt) map { region =>
            region match {
                case (start, stop) => {
                    // Make sure the regions are coherent
                    assert(start.chr == stop.chr)

                    BedSpan(start.chr, start.pos, stop.pos)
                }
            }
        }
    }

    def generateBedEntriesFromExtractedRegionsOnPredicate[T](it: Iterator[BedEntry[T]], pred: (BedEntry[T]) => Boolean, comment: String) = {
        val bedValue = comment.replaceAll("\\s", "¬")
        joinFilteredContiguousRegions(it, pred, bedValue)
    }

    def stringFromBedEntry[T](entry: BedEntry[T], sb: StringBuilder) = {
        lazy val string = {
            sb.setLength(0) // "Clear" the StringBuilder efficiently

            // Create the string
            sb.append(entry.span.chr)
            sb.append("\t")
            sb.append(entry.span.start)
            sb.append("\t")
            sb.append(entry.span.stop)
            sb.append("\t")
            sb.append(entry.value)

            sb.toString()
        }
        string
    }

    def bedGraphFromEntries[T](it: Iterator[BedEntry[T]], fileName: String, trackName: String = "track", description: String = "no description") = {
        val writer = {
            if (fileName.endsWith(".gz") || fileName.endsWith(".gzip")) {
                new PrintWriter(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(fileName))))
            } else {
                new PrintWriter(new File(fileName))
            }
        }
        val sb = new StringBuilder
        
        // Generate header with comment and track name
        val desc = description.replaceAll("\\s", "_")
        val track = trackName.replaceAll("\\s", "_")

        writer.println("# Generated from " + (new RuntimeException).getStackTrace.take(2).mkString("\n# "))
        writer.println("track type=bedGraph name=" + track + " description=" + desc)

        it foreach { e => writer.println(stringFromBedEntry(e, sb)) }
        writer.close()
    }

    def bedGraphFromEntriesAppend[T](it: Iterator[BedEntry[T]], fileName: String) = {
        val writer = {
            if (fileName.endsWith(".gz") || fileName.endsWith(".gzip")) {
                new PrintWriter(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(fileName, true))))
            } else {
                new PrintWriter(new FileWriter(fileName, true))
            }
        }
        val sb = new StringBuilder

        it foreach { e => writer.println(stringFromBedEntry(e, sb)) }
        writer.close()
    }

    //def bedGraphDoubleFromBedEntries[T](it: Iterator[BedEntry[T]], typeToDouble: (T) => Double) = {
//
    //}
//
    //def bedGraphIntFromBedEntries[T](it: Iterator[BedEntry[T]], typeToInt: (T) => Int) = {
//
    //}
}