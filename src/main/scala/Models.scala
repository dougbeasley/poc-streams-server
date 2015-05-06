
import reactivemongo.bson.BSONDocumentReader
import reactivemongo.bson.BSONObjectID
import reactivemongo.bson.Macros

import play.api.libs.json._

case class Image(id : String, url : String, cdn : String)
case class Stats(reported : Int, favorites : Int, found : Int)
case class Post(id: String, image: Image, stats: Stats)

case class ImagePostRequest(id: String, url : String)

object Marshallers {
	implicit val imageBSONHandler = Macros.handler[Image]
	implicit val statsBSONHandler = Macros.handler[Stats]
	implicit val postBSONHandler = Macros.handler[Post]
}