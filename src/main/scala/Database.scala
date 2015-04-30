import reactivemongo.api._
import reactivemongo.api.collections.default.BSONCollection
import reactivemongo.bson.BSONDocument
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import scala.util.Properties

object Database {

  val collection = connect()


  def connect(): BSONCollection = {

    val uri = Properties.envOrElse("MONGOLAB_URI", "localhost")


    val driver = new MongoDriver
    val connection = driver.connection(List(uri))

    val db = connection("akka")
    db.collection("stocks")
  }

  def findAllTickers(): Future[List[BSONDocument]] = {
    val query = BSONDocument()
    val filter = BSONDocument("Company" -> 1, "Country" -> 1, "Ticker" -> 1)

    // which results in a Future[List[BSONDocument]]
    Database.collection
      .find(query, filter)
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