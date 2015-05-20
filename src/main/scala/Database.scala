import reactivemongo.api._
import reactivemongo.api.collections.default.BSONCollection
import play.api.libs.json._
import reactivemongo.bson.BSONDocument
import reactivemongo.core.commands.LastError
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import scala.util.Properties
import scala.util.{Try,Success,Failure}

object Database {

  import Marshallers._

  def collection: BSONCollection = connect()

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

    Database.collection
      .find(query)
      .cursor[Post]
      .collect[List]()
  }

  def findById(id: String) : Future[Option[Post]] = {
    val query = BSONDocument("id" -> id)

    Database.collection
      .find(query)
      .one[Post]
  }

  def create(post: Post) : Future[LastError] = {
    Database.collection.insert(post)
  }
}