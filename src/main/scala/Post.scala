
import reactivemongo.bson.BSONDocumentReader
import reactivemongo.bson.BSONObjectID
import reactivemongo.bson.Macros


case class Post(id: String, image: Image, stats: Stats)

object Post {
  implicit val reader: BSONDocumentReader[Post] = Macros.reader[Post]
}

case class Image(id : String, url : String, cdn : String)
case class Stats(reported : Int, favorites : Int, found : Int)