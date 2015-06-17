
import reactivemongo.bson.BSONDocumentReader
import reactivemongo.bson.BSONObjectID
import reactivemongo.bson.Macros
import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.libs.json._
import play.api.libs.iteratee.Enumerator
import akka.http.scaladsl.model._
import org.reactivestreams.Publisher

case class Image(id : String, url : String, cdn : String)
case class Stats(reported : Int, favorites : Int, found : Int)
case class Post(id: String, image: Image, stats: Stats)

case class ImagePostRequest(id: String, url : String)
case class UploadResponse(id: String, filename: String, contentType: Option[String], md5: Option[String])
case class UploadRequest(data: Publisher[Array[Byte]], filename: Option[String], contentType: akka.http.scaladsl.model.ContentType)
case class DownloadRequest(contentType: ContentType, data: Source[ByteString, Any])

object Marshallers {
	implicit val imageBSONHandler = Macros.handler[Image]
	implicit val statsBSONHandler = Macros.handler[Stats]
	implicit val postBSONHandler = Macros.handler[Post]
}