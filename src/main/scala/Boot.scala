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

import scala.util.Properties

import java.net.{InetSocketAddress, InetAddress}

/**
 * Simple Object that starts an HTTP server using akka-http. All requests are handled
 * through an Akka flow.
 */
object Boot extends App {

  // the actor system to use. Required for flowmaterializer and HTTP.
  // passed in implicit
  implicit val system = ActorSystem("Streams")
  implicit val materializer = ActorFlowMaterializer()

  // start the server on the specified interface and port.
  /*
  val serverBinding1:  Source[Http.IncomingConnection, Future[Http.ServerBinding]] =
                        Http(system).bind(interface = "localhost", port = 8090)
  */
  val port = Properties.envOrElse("PORT", "8091").toInt
  val localhost = InetAddress.getLocalHost
  val localIpAddress = localhost.getHostAddress  
  println(s"Starting servce on on $localIpAddress:$port")

  val serverBinding2:  Source[Http.IncomingConnection, Future[Http.ServerBinding]] =
                        Http(system).bind(localIpAddress, port)


  // helper actor for some logging
  val idActor = system.actorOf(Props[IDActor],"idActor");
  idActor ! "start"

  // but we can also construct a flow from scratch and use that. For this
  // we first define some basic building blocks

  // broadcast sends the incoming event to multiple targets
  /* had to add parallelism here */
  val bCast = Broadcast[HttpRequest](3)

  // some basic steps that each retrieve a different ticket value (as a future)
  /* had to add parallelism here */
  val step1 = Flow[HttpRequest].mapAsync[String](1, getTickerHandler("GOOG"))
  val step2 = Flow[HttpRequest].mapAsync[String](1, getTickerHandler("AAPL"))
  val step3 = Flow[HttpRequest].mapAsync[String](1, getTickerHandler("MSFT"))

  // We'll use the source and output provided by the http endpoint
  //val in = UndefinedSource[HttpRequest]
  //val out = UndefinedSink[HttpResponse]

  // waits for events on the three inputs and returns a response
  val zip = ZipWith[String, String, String, HttpResponse] (
    (inp1, inp2, inp3) => new HttpResponse(status = StatusCodes.OK,entity = inp1 + inp2 + inp3)
  )

  // when an element is available on one of the inputs, take
  // that one, igore the rest

  // since merge doesn't output a HttpResponse add an additional map step.
  val mapToResponse = Flow[String].map[HttpResponse](
    (inp:String) => HttpResponse(status = StatusCodes.OK, entity = inp)
  )

  val broadCastZipFlow = Flow() { implicit b =>
    import FlowGraph.Implicits._
    
    val broadcast = b.add(Broadcast[HttpRequest](3))
    val zipit = b.add(zip)
    
    broadcast.out(0) ~> step1 ~> zipit.in0
    broadcast.out(1) ~> step2 ~> zipit.in1
    broadcast.out(2) ~> step3 ~> zipit.in2
    
    (broadcast.in, zipit.out)
  }
  

  // define another flow. This uses the merge function which
  // takes the first available response
  val broadCastMergeFlow = Flow() { implicit b =>
    import FlowGraph.Implicits._
    
    val broadcast = b.add(Broadcast[HttpRequest](3))
    val merge = b.add(Merge[String](3))
    val toResponse = b.add(mapToResponse)
    
    broadcast.out(0) ~> step1 ~> merge
    broadcast.out(1) ~> step2 ~> merge ~> toResponse 
    broadcast.out(2) ~> step3 ~> merge

    (broadcast.in, toResponse.outlet)
  }

/*
  // Handles port 8090
  serverBinding1.to(Sink.foreach { connection =>
    connection.handleWith(broadCastMergeFlow) //We can switch the flow here
//    idActor ! "start"
  }).run()
*/
  // Handles port 8091
  serverBinding2.to(Sink.foreach { connection =>
    connection.handleWith(Flow[HttpRequest].mapAsync(1, asyncHandler)) //Had to add parellelism here
//    idActor ! "start"
  }).run()

  def getTickerHandler(tickName: String)(request: HttpRequest): Future[String] = {
    // query the database
    val ticker = Database.findTicker(tickName)

    Thread.sleep(Math.random() * 1000 toInt)

    // use a simple for comprehension, to make
    // working with futures easier.
    for {
      t <- ticker
    } yield  {
      t match {
        case Some(bson) => convertToString(bson)
        case None => ""
      }
    }
  }

  // With an async handler, we use futures. Threads aren't blocked.
  def asyncHandler(request: HttpRequest): Future[HttpResponse] = {

    // we match the request, and some simple path checking
    request match {

      // match specific path. Returns all the avaiable tickers
      case HttpRequest(GET, Uri.Path("/posts"), _, _, _) => {

        // make a db call, which returns a future.
        // use for comprehension to flatmap this into
        // a Future[HttpResponse]
        for {
          input <- Database.findAllPosts
        } yield {
          HttpResponse(entity = convertToString(input))
        }
      }

      // match GET pat. Return a single ticker
      case HttpRequest(GET, Uri.Path("/get"), _, _, _) => {

        // next we match on the query paramter
        request.uri.query.get("ticker") match {

            // if we find the query parameter
            case Some(queryParameter) => {

              // query the database
              val ticker = Database.findTicker(queryParameter)

              // use a simple for comprehension, to make
              // working with futures easier.
              for {
                t <- ticker
              } yield  {
                t match {
                  case Some(bson) => HttpResponse(entity = convertToString(bson))
                  case None => HttpResponse(status = StatusCodes.OK)
                }
              }
            }

            // if the query parameter isn't there
            case None => Future(HttpResponse(status = StatusCodes.OK))
          }
      }

      // Simple case that matches everything, just return a not found
      case HttpRequest(_, _, _, _, _) => {
        Future[HttpResponse] {
          HttpResponse(status = StatusCodes.NotFound)
        }
      }
    }
  }


  def convertToString(input: List[BSONDocument]) : String = {
    input
      .map(f => convertToString(f))
      .mkString("[", ",", "]")
  }

  def convertToString(input: BSONDocument) : String = {
    Json.stringify(BSONFormats.toJSON(input))
  }
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