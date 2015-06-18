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
import play.api.libs.iteratee.Enumerator
import reactivemongo.bson.BSONObjectID
import akka.http.scaladsl.model.{ ContentType, MediaType, MediaTypes }


object Database {

  import Marshallers._
  import reactivemongo.api.gridfs.Implicits._

  def db: DefaultDB = connect()

  val gfs = new GridFS(db)
  
  def connect(): DefaultDB = {

    val uri = Properties.envOrElse("MONGOLAB_URI", "mongodb://localhost/akka")

    val driver = new MongoDriver

    val parsedURI = MongoConnection.parseURI(uri) match {
      case Success(parsedURI) if parsedURI.db.isDefined =>
        parsedURI
    }

    val connection = driver.connection(parsedURI)
    DefaultDB(parsedURI.db.get, connection)
  }

  def findAllPosts(): Future[List[Post]] = {
    val query = BSONDocument()

    db[BSONCollection]("posts").find(query)
      .cursor[Post]
      .collect[List]()
  }

  def findById(id: String) : Future[Option[Post]] = {
    val query = BSONDocument("id" -> id)

    db[BSONCollection]("posts").find(query).one[Post]
  }

  def create(post: Post) : Future[LastError] = {
   db[BSONCollection]("posts").insert(post)
  }

  def download(id: String) = {

    val query = BSONDocument("_id" -> BSONObjectID(id))
    gfs.find(query).headOption.map {
      case Some(file) => (file.contentType, file)       
    }.map {
      case (Some(ct), file) =>
       DownloadRequest(toContentType(ct), Source(stream(file)))
    }
  }

  private def toContentType(s: String) : ContentType = {
    val mediaType = s.split("/") match {
      case Array(mainType, subType) => MediaTypes.getForKey(mainType -> subType)
    }
    ContentType(mediaType.getOrElse(MediaTypes.`application/octet-stream`))
  }

  private def stream(file: ReadFile[BSONValue]): Publisher[ByteString] = {
    Streams.enumeratorToPublisher(gfs.enumerate(file).map(ByteString(_)) andThen Enumerator.eof)
  }


  def upload(uploadRequest: UploadRequest): Future[ReadFile[BSONValue]] = {
    /* setup storage */
    val metadata = DefaultFileToSave(uploadRequest.filename orNull, Some(uploadRequest.contentType.toString()))
    val enumerator = Streams.publisherToEnumerator(uploadRequest.data) andThen Enumerator.eof

    gfs.save(enumerator, metadata)
  }
}