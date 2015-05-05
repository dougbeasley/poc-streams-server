
import reactivemongo.bson.BSONDocumentReader
import reactivemongo.bson.BSONObjectID
import reactivemongo.bson.Macros

import play.api.libs.json._

case class Post(id: String, image: Image, stats: Stats)
object Post {
  implicit val reader: BSONDocumentReader[Post] = Macros.reader[Post]
  implicit val postFormat = Json.writes[Post]
}

case class Image(id : String, url : String, cdn : String)
object Image {
  implicit val imageFormat = Json.writes[Image]
}

case class Stats(reported : Int, favorites : Int, found : Int)
object Stats {
  implicit val statsFormat = Json.writes[Stats]
}