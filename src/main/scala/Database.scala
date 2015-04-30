import reactivemongo.api._
import reactivemongo.api.collections.default.BSONCollection
import reactivemongo.bson.BSONDocument
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import scala.util.Properties
import scala.util.{Try,Success,Failure}

object Database {

  val collection = connect()


  def connect(): BSONCollection = {

    val uri = Properties.envOrElse("MONGOLAB_URI", "mongodb://localhost/akka")


    val driver = new MongoDriver
    //val connection = driver.connection(List(uri))

    // val connection: Try[MongoConnection] =
    //   MongoConnection.parseURI(uri).map { parsedUri =>
    //     driver.connection(parsedUri)
    //   }


    val parsedURI = MongoConnection.parseURI(uri) match {
      case Success(parsedURI) if parsedURI.db.isDefined =>
        parsedURI
    }

    val connection = driver.connection(parsedURI)
    val db = DB(parsedURI.db.get, connection)

    //val db = connection("akka")
    //db.collection("stocks")
    // val db = connection.get.db
    db.collection("posts")
  }

  def findAllPosts(): Future[List[BSONDocument]] = {
    val query = BSONDocument()

    // which results in a Future[List[BSONDocument]]
    Database.collection
      .find(query)
      .cursor[BSONDocument]
      .collect[List]()
  }

  def findTicker(ticker: String) : Future[Option[BSONDocument]] = {
    val query = BSONDocument("Ticker" -> ticker)

    Database.collection
      .find(query)
      .one
  }

}