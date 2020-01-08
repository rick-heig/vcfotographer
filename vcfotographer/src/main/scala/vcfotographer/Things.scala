package vcfotographer
import java.net.Socket
import java.io._
import java.lang.StringBuilder
import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global

object Things {

  case class IgvConnection(socket: Socket, out: PrintWriter, in: BufferedReader)

  def connectToIGV(port: Int = 60151) = {
    val socket = new Socket("127.0.0.1", port)
    val out = new PrintWriter(socket.getOutputStream(), true)
    val in = new BufferedReader(new InputStreamReader(socket.getInputStream()))

    IgvConnection(socket, out, in)
  }

  def commandToIGV(command: String, connection: IgvConnection, print: Boolean = false, send: Boolean = true) = {
    if (print) {println(command)}
    if (send) {connection.out.println(command)}
  }

  def sendCommandToIGV(command: String, connection: IgvConnection) = {
    commandToIGV(command, connection)
    val answer = connection.in.readLine()
    answer
  }

  def sendCommandToIGVFuture(command: String, connection: IgvConnection) = {
    commandToIGV(command, connection)
    val futureAnswer = Future(connection.in.readLine())
    futureAnswer
  }

  def sendCommandToIGVTimeOut(command: String, connection: IgvConnection) = {
    Await.result(sendCommandToIGVFuture(command, connection), 3.second)
  }
}