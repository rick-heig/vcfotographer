package vcfotographer

import java.io.FileOutputStream
import java.io.BufferedOutputStream
import java.util.zip.GZIPOutputStream
import java.io.FileInputStream
import java.io.BufferedInputStream
import java.util.zip.GZIPInputStream
import java.io.File
import java.io.PrintWriter

package object Basic {
  def time[R](block: => R): R = {
    val nano2milli: Int = 1_000_000

    val t0 = System.nanoTime()
    val result = block // Call-by-name
    val t1 = System.nanoTime()
    val locale = new java.util.Locale("fr", "CH")
    val formatter = java.text.NumberFormat.getIntegerInstance(locale)

    println("Elapsed time : " + formatter.format((t1 - t0) / nano2milli) + "ms")

    result
  }

  def gzis(fileName: String) = new GZIPInputStream(new BufferedInputStream(new FileInputStream(fileName)))

  def getLines(fileName: String) = {
    (if (fileName.endsWith(".gz") || fileName.endsWith(".gzip")) {
      io.Source.fromInputStream(gzis(fileName))
    } else {
      io.Source.fromFile(fileName)
    }).getLines()
  }

  def gzos(fileName: String) = new GZIPOutputStream(new BufferedOutputStream( new FileOutputStream(fileName)))

  def printWriter(fileName: String): java.io.PrintWriter = {
    val writer = {
      if (fileName.endsWith(".gz") || fileName.endsWith(".gzip")) {
          new PrintWriter(gzos(fileName))
      } else {
          new PrintWriter(new File(fileName))
      }
    }
    writer
  }
}