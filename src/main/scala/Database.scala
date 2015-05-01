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

    val parsedURI = MongoConnection.parseURI(uri) match {
      case Success(parsedURI) if parsedURI.db.isDefined =>
        parsedURI
    }

    val connection = driver.connection(parsedURI)
    val db = DB(parsedURI.db.get, connection)
    db.collection("posts")
  }

  def findAllPosts(): Future[List[Post]] = {
    val query = BSONDocument()

    // which results in a Future[List[BSONDocument]]
    Database.collection
      .find(query)
      .cursor[Post]
      .collect[List]()
  }

  def findPost(id: String) : Future[Option[Post]] = {
    val query = BSONDocument("id" -> id)

    Database.collection
      .find(query)
      .one
  }

}