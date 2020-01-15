package vcfotographer

import java.nio.file.{Paths, Files}

object Utilities {

  def checkBamFile(file: String) = {
    if (!file.endsWith(".bam")) {
      println("ERROR: Bam file should have .bam extension, offending file : " + file)
      VCFotographer.printUsageAndQuit()
    }

    val possibleBaiFiles = List(file.replaceAll(".bam$", ".bai"), file + ".bai")
    val indexExists = possibleBaiFiles map {file => Files.exists(Paths.get(file))} reduce {_ || _}

    if (!indexExists) {
      println("ERROR: Index (.bai) for file " + file + " is missing")
      VCFotographer.printUsageAndQuit
    }
  }

}