import akka.actor._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers._

import scala.concurrent.ExecutionContext

import akka.stream.scaladsl._
import akka.stream.{ActorFlowMaterializer, UniformFanOutShape}
import play.modules.reactivemongo.json.BSONFormats
import reactivemongo.bson.BSONDocument
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json._
import akka.http.scaladsl.Http


import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.server.{ Directives, Route }

import scala.util.Properties
import scala.util.Success

import java.net.{ InetSocketAddress, InetAddress }

import play.api.libs.json._

import akka.event.{ LoggingAdapter, Logging }

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol
import akka.http.scaladsl.marshalling.{ Marshaller, ToResponseMarshaller }

import akka.util.ByteString

import org.reactivestreams.Publisher
import reactivemongo.api.gridfs.ReadFile
import reactivemongo.bson.BSONValue
import scala.util.{ Failure, Success }
import play.api.libs.iteratee.Enumerator


/**
 * Simple Object that starts an HTTP server using akka-http. All requests are handled
 * through an Akka flow.
 */

trait Protocols extends DefaultJsonProtocol {
  implicit val imageFormat = jsonFormat3(Image.apply)
  implicit val statsFormat = jsonFormat3(Stats.apply)
  implicit val postFormat = jsonFormat3(Post.apply)
  implicit val imagePostRequestFormat = jsonFormat2(ImagePostRequest.apply)
  implicit val uploadResponseFormat = jsonFormat4(UploadResponse.apply)
}

object Boot extends App with Directives with Protocols {

  // the actor system to use. Required for flowmaterializer and HTTP.
  // passed in implicit
  implicit val system = ActorSystem("Streams")
  implicit val materializer = ActorFlowMaterializer()

  val log = Logging(system, getClass)

  // get the environment info.
  val port = Properties.envOrElse("PORT", "8091").toInt
  val localhost = InetAddress.getLocalHost
  val localIpAddress = localhost.getHostAddress
  println(s"Starting servce on on $localIpAddress:$port")

  /* setup the server */
  val server: Source[Http.IncomingConnection, Future[Http.ServerBinding]] =
    Http(system).bind(localIpAddress, port)

  // helper actor for some logging
  val idActor = system.actorOf(Props[IDActor], "idActor");
  //idActor ! "start"

  implicit def stringStreamMarshaller(implicit ec: ExecutionContext): ToResponseMarshaller[Source[String, Any]] =
    Marshaller.withFixedCharset(MediaTypes.`text/plain`, HttpCharsets.`UTF-8`) { s =>
      HttpResponse(entity = HttpEntity.CloseDelimited(MediaTypes.`text/plain`, s.map(ByteString(_))))
    }

    /*
  implicit def uploadMarshaller(implicit ec: ExecutionContext): ToResponseMarshaller[Source[ReadFile[BSONValue], Any]] =
    Marshaller.withFixedCharset(MediaTypes.`text/plain`, HttpCharsets.`UTF-8`) { f =>
      HttpResponse(entity = HttpEntity.CloseDelimited(MediaTypes.`text/plain`, f.map(_.filename).map(ByteString(_))))
    }
  */
  implicit def downloadMarshaller(implicit ec: ExecutionContext): ToResponseMarshaller[Source[Publisher[ByteString], Unit]] =
    Marshaller.opaque { s =>
      HttpResponse(
        entity = HttpEntity.CloseDelimited(MediaTypes.`image/jpeg`,
            s.map(Source(_))
            .flatten(FlattenStrategy.concat)))
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
            val stats = Stats(0, 0, 0)
            val post = Post(java.util.UUID.randomUUID.toString, image, stats)

            Database.create(post).map(_ => Created -> post)
          }
        }
    } ~
      path(Segment) { id =>
        get {
          complete {
            Database.findById(id)
          }
        }
      }
  }

  val uploadRequestFlow = Flow() { implicit b =>
    import FlowGraph.Implicits._

    val broadcast = b.add(Broadcast[Multipart.General.BodyPart](3))

    val f1 = b.add(Flow[Multipart.General.BodyPart]
              .map(_.entity.dataBytes)
              .flatten(FlattenStrategy.concat)  // flatten to Source[ByteString]
              .map(_.toArray[Byte])             // map to Array[Byte]
              .map(Enumerator(_)))

    /* get the filename */
    val f2 = b.add(Flow[Multipart.General.BodyPart].mapConcat(_.headers).map {
      case `Content-Disposition`(_,params) => params get "filename"
      case _ => None
    })

    /* get the content type */
    val f3 = b.add(Flow[Multipart.General.BodyPart].map(_.entity.contentType))

    val f4 = b.add(Flow[UploadRequest].map(Database.upload(_).map(r => UploadResponse(r.id.toString(), r.filename, r.contentType, r.md5))))

    val zip = b.add(ZipWith[Enumerator[Array[Byte]], Option[String], ContentType, UploadRequest]({ case (d, f, c) => UploadRequest(d, f, c) }))

    broadcast.out(0) ~> f1 ~> zip.in0
    broadcast.out(1) ~> f2 ~> zip.in1
    broadcast.out(2) ~> f3 ~> zip.in2

    zip.out ~> f4

    (broadcast.in, f4.outlet)
  }

  val uploadDirective = pathPrefix("uploads") {
    pathEnd {
      post {
        entity(as[Multipart.General]) { formData =>
          complete {

            val content: Source[Multipart.General.BodyPart, Any] = formData.parts
                .filter { part => part.headers.map {
                  case `Content-Disposition`(_,params) => params.exists( _ == "name" -> "metadata" )
                  case _ => false
                  }.contains(true)
                }
                .map { elem => log.info(elem.toString()); elem }  //Debug logging
            
            val resp = (content via uploadRequestFlow).runWith(Sink.head)
            resp
          }
        }
      }
    } ~ path(Segment) { id =>
      get {
        complete {
          Source(Database.download(id))
        }
      }
    }
  }

  val directives: Route = postsDirective ~ uploadDirective
  
  server.to(Sink.foreach { connection =>
    connection.handleWith(Flow[HttpRequest].mapAsync(4)(Route.asyncHandler(directives))) //Had to add parallelism here
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