
import reactivemongo.bson.BSONDocumentReader
import reactivemongo.bson.BSONObjectID
import reactivemongo.bson.Macros

import play.api.libs.json._

case class Image(id : String, url : String, cdn : String)
case class Stats(reported : Int, favorites : Int, found : Int)
case class Post(id: String, image: Image, stats: Stats)

object JsonFormats {
	import play.api.libs.json.Json
	import play.api.data._

	implicit val imageFormat = Json.format[Image]
	implicit val statsFormat = Json.format[Stats]
	implicit val postFormat = Json.format[Post]
}