import reactivemongo.api._
import reactivemongo.api.collections.default.BSONCollection
import play.modules.reactivemongo.json.collection.JSONCollection
import play.api.libs.json._
import reactivemongo.bson.BSONDocument
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import scala.util.Properties
import scala.util.{Try,Success,Failure}



object Database {

  import JsonFormats._

  def collection: JSONCollection = connect()

  def connect(): JSONCollection = {

    val uri = Properties.envOrElse("MONGOLAB_URI", "mongodb://localhost/akka")

    val driver = new MongoDriver

    val parsedURI = MongoConnection.parseURI(uri) match {
      case Success(parsedURI) if parsedURI.db.isDefined =>
        parsedURI
    }

    val connection = driver.connection(parsedURI)
    val db = DB(parsedURI.db.get, connection)
    db.collection[JSONCollection]("posts")
  }

  def findAllPosts(): Future[List[Post]] = {
    val query = Json.obj()

    Database.collection
      .find(query)
      .cursor[Post]
      .collect[List]()
  }

  def findById(id: String) : Future[Option[Post]] = {
    val query = Json.obj("id" -> id)

    Database.collection
      .find(query)
      .one[Post]
  }
}