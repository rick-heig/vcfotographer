package vcfotographer

import vcfotographer.Things._
import vcfotographer.VcfToolBox._
import vcfotographer.CommonGenomics._

import java.io.EOFException

import scala.annotation.tailrec

import vcfotographer.BedSignalToolBox.BedEntry

object CheckingVariantsInIGV {

  case class Parameters(scalingFactor: Option[Double], minBasesShown: Int = 500)
  val DEFAULT_PARAMS = Parameters(Some(10.0))

  def genericInteractiveViewSession[T](it: Iterator[T], conv: (T) => GenomicWindow, port: Int = 60151, parameters: Parameters = DEFAULT_PARAMS) = {
    val con = connectToIGV(port)

    //lazy val entries = extractEntriesFromFile(fileName)
    //lazy val coordinates = entries map extractEventCoordinates

    @tailrec
    def goThroughAndAsk(it: Iterator[T], acc: Int): Unit = {
      it.nextOption match {
        // If there is no next entry close the socket and return
        case None => {
          println("No more entries")
          con.socket.close()
        }
        case Some(entry) => {
          println(acc.toString + " : " + entry)
          print("Go to variant ? [y/n/others] entering a number will skip that amount, entering a chromosome will go to that chromosome : ")
          val line = io.StdIn.readLine
          println()
          if (line.isEmpty || line.startsWith("y")) {
            val command = "goto " + {
              parameters.scalingFactor match {
                case Some(value) => stringFromGenomicWindow(scaleGenomicWindow(conv(entry), value))
                case None => stringFromGenomicWindow(conv(entry))
              }
            }
            println("command : " + command)
            sendCommandToIGV(command, con)
            goThroughAndAsk(it, acc + 1) // Continue
          } else if (line.startsWith("chr")) {
            goThroughAndAsk(it.dropWhile(e => conv(e).region != line), 0)
          } else {
            line.toIntOption match {
              case Some(n) => {
                println("skipping " + n)
                goThroughAndAsk(it.drop(n), acc + n) // Skip n and Continue
              }
              case None => {
                // If the entry cannot be parsed to Int (E.g., "n" or any other string) close socket and return
                con.socket.close()
              }
            }
          }
        }
      }
    }

    goThroughAndAsk(it, 0)
  }

  // Conversion fgrom VcfEntry to Genomic Window
  def vcfEntryToGenomicWindow(entry: VcfEntry) = {
    extractEventCoordinates(entry) match {
      case EventCoordinates(chr, start, stop, optEventLength) => {
        optEventLength match {
          case Some(value) => GenomicWindow(chr, scala.math.max(1, (start + (stop-start)/2 - value / 2) -10), stop - (stop-start)/2 + value / 2 + 10)
          case None => {
            if ((stop - start) > 100) {
              GenomicWindow(chr, scala.math.max(1, start-10), stop + 10)
            } else {
              val add = (100 - (stop - start)) / 2
              GenomicWindow(chr, scala.math.max(1, start-add), stop + add)
            }
          }
        }
      }
    }
  }

  def genericInteractiveViewSessionFromVcfFile(fileName: String, port: Int = 6666, parameters: Parameters = DEFAULT_PARAMS) = {
    val it = extractEntriesFromFile(fileName)

    // Conversion fgrom VcfEntry to Genomic Window
    val conv = (entry: VcfEntry) => {
      extractEventCoordinates(entry) match {
        case EventCoordinates(chr, start, stop, optEventLength) => {
          optEventLength match {
            case Some(value) => GenomicWindow(chr, scala.math.max(1, start + (stop-start)/2 - value / 2), stop - (stop-start)/2 + value / 2)
            case None => GenomicWindow(chr, start, stop)
          }
        }
      }
    }

    genericInteractiveViewSession(it, conv, port, parameters)
  }

  def interactiveViewSession(fileName: String, port: Int = 6666) = {
    genericInteractiveViewSessionFromVcfFile(fileName, port)
  }

  def scaledInteractiveViewSession(fileName: String, port: Int = 6666, scalingFactor: Double) = {
    genericInteractiveViewSessionFromVcfFile(fileName, port, Parameters(Some(scalingFactor)))
  }

}