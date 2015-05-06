import akka.actor._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
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

import java.net.{InetSocketAddress, InetAddress}

import play.api.libs.json._

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol

/**
 * Simple Object that starts an HTTP server using akka-http. All requests are handled
 * through an Akka flow.
 */

trait Protocols extends DefaultJsonProtocol {
  implicit val imageFormat = jsonFormat3(Image.apply)
  implicit val statsFormat = jsonFormat3(Stats.apply)
  implicit val postFormat = jsonFormat3(Post.apply)
}

object Boot extends App with Directives with Protocols {

  // the actor system to use. Required for flowmaterializer and HTTP.
  // passed in implicit
  implicit val system = ActorSystem("Streams")
  implicit val materializer = ActorFlowMaterializer()

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
  idActor ! "start"

  val postsDirective = pathPrefix("posts") {
    pathEnd {
      complete {
        Database.findAllPosts
      }
    } ~
    path(Segment) { id =>
      complete {
        Database.findById(id)
      }
    }
  }

  server.to(Sink.foreach { connection =>
    connection.handleWith(Flow[HttpRequest].mapAsync(4)(Route.asyncHandler(postsDirective))) //Had to add parellelism here
//    idActor ! "start"
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