import akka.stream.scaladsl._
import reactivemongo.api._
import reactivemongo.api.collections.default.BSONCollection
import play.api.libs.json._
import reactivemongo.bson.BSONDocument
import reactivemongo.core.commands.LastError
import reactivemongo.api.gridfs.ReadFile
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Properties
import scala.util.{Try,Success,Failure}
import java.util.stream.Streams
import reactivemongo.bson.BSONValue
import reactivemongo.api.gridfs.DefaultFileToSave
import play.api.libs.iteratee.Iteratee
import akka.util.ByteString
import org.reactivestreams.Subscriber
import reactivemongo.api.gridfs.GridFS
import play.api.libs.streams.Streams
import org.reactivestreams.Publisher
import scala.concurrent.Await


object Database {

  import Marshallers._
  import reactivemongo.api.gridfs.Implicits._

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

  def upload(in: Publisher[Array[Byte]]): Future[ReadFile[BSONValue]] = {
    
    val uri = Properties.envOrElse("MONGOLAB_URI", "mongodb://localhost/akka")

    val driver = new MongoDriver

    val parsedURI = MongoConnection.parseURI(uri) match {
      case Success(parsedURI) if parsedURI.db.isDefined =>
        parsedURI
    }

    val connection = driver.connection(parsedURI)
    val db = DB(parsedURI.db.get, connection)
    val gfs = GridFS(db)

    /* setup storage */
    val metadata = DefaultFileToSave(filename = "foo.dat")
    val enumerator = Streams.publisherToEnumerator(in)

    gfs.save(enumerator, metadata)
  }
}