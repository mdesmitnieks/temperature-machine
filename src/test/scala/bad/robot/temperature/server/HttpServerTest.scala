package bad.robot.temperature.server

import java.io._

import bad.robot.logging._
import bad.robot.temperature.rrd.{Host, RrdFile}
import org.http4s.Method.GET
import org.http4s.client.blaze.BlazeClientConfig._
import org.http4s.client.blaze._
import org.http4s.{Request, Status, Uri}
import org.specs2.mutable.Specification

import scala.concurrent.duration._
import scalaz.concurrent.Task

class HttpServerTest extends Specification {

  "When the Http server has been started" >> {
    val server = HttpServer(8080, List(Host("example"))).unsafePerformSync
    val client = PooledHttp1Client(config = defaultConfig.copy(idleTimeout = 30 minutes, responseHeaderTimeout = 30 minutes))

    // todo wait for server to startup, not sure how.

    "index.html can be loaded" >> {
      assertOk(Request(GET, path("")))
    }

    "temperature.json can be loaded" >> {
      maybeCreateJsonFile()
      assertOk(Request(GET, path("/temperature.json")))
    }

    "temperature.csv can be loaded" >> {
      maybeCreateJsonFile()
      assertOk(Request(GET, path("/temperatures.csv")))
    }

    "RRD chart / image can be loaded" >> {
      maybeCreateFile("temperature-1-days.png")
      assertOk(Request(GET, path("/temperature-1-days.png")))
    }

    "Some java script can be loaded (note this changes with every UI deployment)" >> {
      val file = findFileIn("src/main/resources/static/js", startingWith("main", ".js"))
      assertOk(Request(GET, path("/static/js/" + file.head)))
    }

//    "Some css can be loaded (note this changes with every UI deployment)" >> {
//      val file = findFileIn("src/main/resources/static/css", startingWith("main", ".css"))
//      assertOk(Request(GET, path("/static/css/" + file.head)))
//    }

    "image can be loaded" >> {
      assertOk(Request(GET, path("/images/spinner.gif")))
    }

    "media can be loaded" >> {
      assertOk(Request(GET, path("/static/media/glyphicons-halflings-regular.f4769f9b.eot")))
    }

    "list of connections can be retrieved" >> {
      assertOk(Request(GET, path("/connections")))
    }

    "list of recent connections can be retrieved" >> {
      assertOk(Request(GET, path("/connections/active/within/5/mins")))
    }

    "get the local machine's log over http" >> {
      assertOk(Request(GET, path("/log")))
    }

    "get version info" >> {
      assertOk(Request(GET, path("/version")))
    }

    def assertOk(request: Request) = {
      val response = client.fetch(request)(Task.delay(_)).unsafePerformSync
      if (response.status != Status.Ok) {
        val body = response.as[String].unsafePerformSyncAttempt
        println(s"Non-200 body was:\n$body")
      }
      response.status must be_==(Status.Ok).eventually(60, 500 milliseconds)
    }

    def path(url: String): Uri = Uri.fromString(s"http://localhost:8080$url").getOrElse(throw new Exception(s"bad url $url"))

    def maybeCreateJsonFile() = if (!JsonFile.exists) {
      val exampleJson =
        """
          |[
          |  {
          |    "label": "bedroom1-sensor-1",
          |    "data": [
          |      {
          |        "x": 1507709610000,
          |        "y": "NaN"
          |      },
          |      {
          |        "x": 1507709640000,
          |        "y": "+2.2062500000E01"
          |      },
          |      {
          |        "x": 1507709680000,
          |        "y": "+2.2262500000E01"
          |      }
          |    ]
          |  }
          |]
        """.stripMargin

      val writer = new BufferedWriter(new FileWriter(JsonFile.file))
      writer.write(exampleJson)
      writer.close()
    }

    def maybeCreateFile(filename: String) = {
      val file = new File(s"${RrdFile.path}/$filename")
      if (!file.exists()) {
        val writer = new BufferedWriter(new FileWriter(file))
        writer.close()
      }
    }

    step {
      val shutdown = for {
        _ <- server.shutdown()
        _ <- Task.delay(Log.info(s"HTTP Server shutting down"))
      } yield ()
      shutdown.unsafePerformSync
    }
  }

  def startingWith(startsWith: String, extension: String): FileFilter = (file: File) => file.getName.startsWith(startsWith) && file.getName.endsWith(extension)

  def findFileIn(base: String, filter: FileFilter): List[String] = {
    val files = Option(new File(base).listFiles(filter))
    files.map(_.map(_.getName).toList).getOrElse(List())
  }

}
