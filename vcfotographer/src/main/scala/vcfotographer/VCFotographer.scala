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
  case class VCFotographerParameters(scalingFactor: Option[Double] = None, igvPort : Int = 60151)
  val DEFAULT_PARAMETERS = VCFotographerParameters()
  val parameters = DEFAULT_PARAMETERS

  // Logging
  val logger = Logger("Log")
  val p = getClass.getPackage
  val name = p.getImplementationTitle
  val version = p.getImplementationVersion
  logger.info("VCFotographer version " + version)

  val loadSession = false
  val session = ""
  val sleepIntervalms = 10 // ms

  val basePath = new File(".").getCanonicalPath() + "/"
  if (!basePath.matches("\\S+")) {
    logger.error("Path cannot contain whitespaces")
    throw new Exception("Path with whitespace")
  }

  //val file = "sandbox/test.vcf"
  val file = "sandbox/motherHC.vcf"
  val filePath = basePath + file
  val bamFile = "sandbox/bam.bam"
  // TODO : Check if bai is existing
  val bamFilePath = basePath + bamFile
  val photobook = "sandbox/photobook/"
  val photobookPath = photobook

  // Read input files (VCFs / BEDs etc).
  val entries = vcfotographer.VcfToolBox.extractEntriesFromFile(file)

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

          sendCommandToIGV("new", connectionToIGV)
          sendCommandToIGV("setSleepInterval " + sleepIntervalms.toString, connectionToIGV)

          // Set screenshot directory
          sendCommandToIGV("snapshotDirectory " + photobookPath, connectionToIGV)

          if (loadSession) {
            // Load Session
            sendCommandToIGV("load " + session, connectionToIGV)
          } else {
            // Load VCFs
            sendCommandToIGV("load " + filePath, connectionToIGV)

            // Load BAMs
            sendCommandToIGV("load " + bamFilePath, connectionToIGV)

            // Load other files
            //...
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
          //sendCommandToIGV("exit", connectionToIGV)
        } // IGV Responding
      } // IGV Responding ?
      connectionToIGV.socket.close()
    } // Connected to IGV
  } // Connection to IGV ?
  }
}