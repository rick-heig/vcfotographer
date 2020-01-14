package vcfotographer

import java.io.File
import scala.util.Try
import com.typesafe.scalalogging._
import vcfotographer.Things._
import vcfotographer.CheckingVariantsInIGV._
import vcfotographer.CommonGenomics._
import scala.concurrent._
import scala.collection.mutable
import ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

object VCFotographer extends App {
  println(vcfotographer.TextAssets.logo)

  ////////////////
  // PARAMETERS //
  ////////////////
  case class VCFotographerParameters(scalingFactor: Option[Double] = None, igvPort : Int = 60151)
  val DEFAULT_PARAMETERS = VCFotographerParameters()

  // Logging
  val logger = Logger("Log")
  val p = getClass.getPackage
  val name = p.getImplementationTitle
  val version = p.getImplementationVersion
  logger.info("VCFotographer version " + version)

  val loadSession = false
  val session = ""
  val sleepIntervalms = 10 // ms

  def printUsageAndQuit() = {
    println(vcfotographer.TextAssets.usage)
    System.exit(1)
  }

  /////////////////////////
  // Input args handling //
  /////////////////////////
  if (args.length == 0) {
    printUsageAndQuit
  }

  // The way options are handled in this program is not the most efficient but needs only to be done once and on small parameter lists so don't worry about efficiency
  val singleOptions: Set[String] = Set()
  case class OptionPair(option: String, value: String)
  def extractOptions(args: Array[String]) = {
    def nextOption(ol: List[OptionPair], list: List[String]): List[OptionPair] = {
      list match {
        case Nil                          => ol.reverse
        case option :: tail if (singleOptions.contains(option))  => nextOption(OptionPair(option, "") :: ol, tail)
        case option :: value :: tail if (option.startsWith("-")) => nextOption(OptionPair(option, value) :: ol, tail)
        case _                            => printUsageAndQuit(); List()
      }
    }
    nextOption(List(), args.toList)
  }

  val options = extractOptions(args)

  ////////////////////////////
  // Extract args to values //
  ////////////////////////////
  val scalingFactor = (options filter {_.option.equals("--scaling")}).headOption match {
    case None => None
    case Some(op) => Some(op.value.toDouble)
  }
  val parameters = DEFAULT_PARAMETERS.copy(scalingFactor = scalingFactor)

  //val basePath = new File(".").getCanonicalPath() + "/"
  val basePath = ""
  if (!basePath.isEmpty && !basePath.matches("\\S+")) {
    logger.error("The path \"" + basePath + "\" has whitespaces")
    throw new Exception("Path with whitespace")
  }

  // Get input files
  val inputFiles = options filter {_.option.equals("-i")} map {basePath + _.value}
  if (inputFiles.isEmpty) {
    printUsageAndQuit()
  } else {
    logger.info("Variants will be captured from : ")
    inputFiles foreach {logger.info(_)}
  }

  // Get bam files
  val bamFiles = options filter {_.option.equals("-b")} map {basePath + _.value}
  if (!bamFiles.isEmpty) {
    logger.info("Loading reads from : ")
    bamFiles foreach {logger.info(_)}
  }

  val additionalTrackFiles = options filter {_.option.equals("-a")} map {basePath + _.value}

  // Outputpath
  val outputPath = (options filter {_.option.equals("--output-dir")} map {_.value}).headOption match {
    case None => "sandbox/photobook/"
    case Some(value) => value
  }
  val photobook = outputPath
  val photobookPath = photobook
  logger.info("Captured variants will go into : ")
  logger.info(photobookPath)

  // Read input files (VCFs / BEDs etc).
  val entries = (inputFiles map {vcfotographer.VcfToolBox.extractEntriesFromFile(_)}).flatten.sortBy(entry => entry.chr + entry.pos.toString)
  // Sorting is optional but more optimized for IGV to load in sorted order
  // Maybe do one photobook per VCF

  logger.info(entries.size + " variants will be captured")

  Basic.time {

  // Connect to IGV
  Try(connectToIGV(parameters.igvPort)).toOption match {
    case None => logger.error("Could not connect to IGV on port : " + parameters.igvPort)
    case Some(connectionToIGV) => {
      // Send commands to IGV

      // Check if IGV is responding
      Try(sendCommandToIGVTimeOut("echo", connectionToIGV)).toOption match {
        case None => logger.error("Could not get a response from IGV before time out")
        case Some(_) => {
          logger.info("IGV is responsive")

          sendCommandToIGVTimeOut("new", connectionToIGV)
          sendCommandToIGVTimeOut("setSleepInterval " + sleepIntervalms.toString, connectionToIGV)

          // Set screenshot directory
          sendCommandToIGV("snapshotDirectory " + photobookPath, connectionToIGV)

          if (loadSession) {
            // Load Session
            sendCommandToIGV("load " + session, connectionToIGV)
          } else {
            // Load VCFs
            inputFiles foreach {file => sendCommandToIGV("load " + file, connectionToIGV)}

            // Load BAMs
            bamFiles foreach {file => sendCommandToIGV("load " + file, connectionToIGV)}

            // Load other files
            additionalTrackFiles foreach {file => sendCommandToIGV("load " + file, connectionToIGV)}
          }

          // For all variants take a snapshot
          val commands: Vector[Future[String]] = Vector()
          val responses: scala.collection.mutable.ArrayBuffer[Future[String]] = mutable.ArrayBuffer()
          entries foreach {
            entry => {
              val gw = vcfEntryToGenomicWindow(entry)
              val command = "goto " + {
                parameters.scalingFactor match {
                  case Some(value) => stringFromGenomicWindow(scaleGenomicWindow(gw, value))
                  case None => stringFromGenomicWindow(gw)
                }
              }
              logger.debug("command : " + command)
              sendCommandToIGV(command, connectionToIGV)
              sendCommandToIGV("snapshot", connectionToIGV)
              //responses += sendCommandToIGVFuture(command, connectionToIGV)
              //responses += sendCommandToIGVFuture("snapshot", connectionToIGV)
            }
          }

          responses foreach {
            r => Await.result(r, Duration.Inf)
          }

          logger.info("Done taking snapshots")
          sendCommandToIGV("exit", connectionToIGV)
        } // IGV Responding
      } // IGV Responding ?
      connectionToIGV.socket.close()
    } // Connected to IGV
  } // Connection to IGV ?
  } // Time
}