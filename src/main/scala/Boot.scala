import akka.actor._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers._

import scala.concurrent.ExecutionContext

import akka.stream.scaladsl._
import akka.stream.scaladsl.Flow
import play.modules.reactivemongo.json.BSONFormats
import reactivemongo.bson.BSONDocument
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json._
import akka.http.scaladsl.Http
import akka.stream.ActorFlowMaterializer

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.server.{Directives, Route}

import scala.util.Properties
import scala.util.Success

import java.net.{InetSocketAddress, InetAddress}

import play.api.libs.json._

import akka.event.{LoggingAdapter, Logging}

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol
import akka.http.scaladsl.marshalling.{ Marshaller, ToResponseMarshaller }

import akka.util.ByteString

/**
 * Simple Object that starts an HTTP server using akka-http. All requests are handled
 * through an Akka flow.
 */

trait Protocols extends DefaultJsonProtocol {
  implicit val imageFormat = jsonFormat3(Image.apply)
  implicit val statsFormat = jsonFormat3(Stats.apply)
  implicit val postFormat = jsonFormat3(Post.apply)
  implicit val imagePostRequestFormat = jsonFormat2(ImagePostRequest.apply)
}


trait FileIO {

  val countBytesSink: Sink[Int, Future[Int]] = Sink.fold[Int, Int](0)(_ + _)

}

// trait Marshallers { 
//   implicit def stringStreamMarshaller(implicit ec: ExecutionContext): ToResponseMarshaller[Source[String, Unit]] =
//     Marshaller.withFixedCharset(MediaTypes.`text/plain`, HttpCharsets.`UTF-8`) { s =>
//       HttpResponse(entity = HttpEntity.CloseDelimited(MediaTypes.`text/plain`, s.map(ByteString(_))))
//     }
// }

object Boot extends App with Directives with Protocols {

  // the actor system to use. Required for flowmaterializer and HTTP.
  // passed in implicit
  implicit val system = ActorSystem("Streams")
  implicit val materializer = ActorFlowMaterializer()

  val logger = Logging(system, getClass)

  // get the environment info.
  val port = Properties.envOrElse("PORT", "8091").toInt
  val localhost = InetAddress.getLocalHost
  val localIpAddress = localhost.getHostAddress  
  println(s"Starting servce on on $localIpAddress:$port")

  /* setup the server */
  val server:  Source[Http.IncomingConnection, Future[Http.ServerBinding]] =
                        Http(system).bind(localIpAddress, port)


  // helper actor for some logging
  val idActor = system.actorOf(Props[IDActor],"idActor");
  //idActor ! "start"

  implicit def stringStreamMarshaller(implicit ec: ExecutionContext): ToResponseMarshaller[Source[String, Any]] =
    Marshaller.withFixedCharset(MediaTypes.`text/plain`, HttpCharsets.`UTF-8`) { s =>
      HttpResponse(entity = HttpEntity.CloseDelimited(MediaTypes.`text/plain`, s.map(ByteString(_))))
    }

  val postsDirective = pathPrefix("posts") {
    pathEnd {
      get {
        complete {
          Database.findAllPosts
        }
      } ~
      (post & entity(as[ImagePostRequest])) { ipr =>
        complete {
          val image = Image(ipr.id, ipr.url, ipr.url)
          val stats = Stats(0,0,0)
          val post = Post( java.util.UUID.randomUUID.toString, image, stats)

          Database.create(post).map( _ => Created -> post)
        }
      }
    } ~
    path(Segment) { id =>
      get {
        complete {
          Database.findById(id)
        }
      }
    } ~
    path("upload") {
      post {        
        entity(as[Multipart.General]) { formData =>
          complete {       
           
            val content: Source[String, Any] =
              formData.parts.filter {
                case Multipart.General.BodyPart(entity, headers) =>
                  headers.exists(_.name == "Content-Disposition") //{ header => header.name.contains("name" -> "content") } 
              }.map(_.entity.dataBytes).flatten(FlattenStrategy.concat).map(_ mkString "-")

            /*
            val details: Source[String, Any] = formData.parts.map { 
              case Multipart.General.BodyPart(entity, headers) =>
                val ct = entity.contentType
                val h = headers mkString ","

                val cd = headers.find { h => h.name == "Content-Disposition" }

                val name = cd.get.value()

                s"""{ Handling entity with $ct and $cd and $name }"""
            details //s"""{"status": "Processed POST request, details=$details" }"""
            */
            content 
          }
        }
      }
    }
  }

  server.to(Sink.foreach { connection =>
    connection.handleWith(Flow[HttpRequest].mapAsync(4)(Route.asyncHandler(postsDirective))) //Had to add parallelism here
    idActor ! "start"
  }).run()
}

class IDActor extends Actor with ActorLogging {

 def receive = {
    case "start" =>
      log.info("Current Actors in system:")
      self ! ActorPath.fromString("akka://Streams/user/")

    case path: ActorPath =>
      context.actorSelection(path / "*") ! Identify(())

    case ActorIdentity(_, Some(ref)) =>
      log.info(ref.toString())
      self ! ref.path
  }
}