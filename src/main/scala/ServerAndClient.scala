import java.nio.ByteOrder

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.io.Framing
import akka.stream.scaladsl._
import akka.stream.scaladsl.Tcp.{ServerBinding, IncomingConnection}
import akka.util.ByteString
import org.msgpack.core.MessagePack
import org.msgpack.value.{ValueFactory ⇒ VF, Value, NilValue, StringValue, MapValue}

import sangria.parser.QueryParser
import sangria.execution.Executor
import sangria.marshalling.msgpack._

import scala.concurrent.Future
import scala.util.{Success, Failure}

object ServerAndClient extends App {
  implicit val system = ActorSystem("sangria-server")
  implicit val materializer = ActorMaterializer()
  implicit val byteOrder = ByteOrder.LITTLE_ENDIAN

  import system.dispatcher

  def server() = {
    def executeQuery(query: String, operation: Option[String], vars: Value) =
      QueryParser.parse(query) match {
        case Success(queryAst) ⇒
          Executor.execute(SchemaDefinition.StarWarsSchema, queryAst,
            operationName = operation,
            variables = vars,
            userContext = new CharacterRepo,
            deferredResolver = new FriendsResolver)

        case Failure(error) ⇒
          Future.successful(
            VF.newMap(VF.newString("errors"), VF.newArray(
              VF.newMap(VF.newString("message"), VF.newString(error.getMessage))
            )))
      }

    val connections: Source[IncomingConnection, Future[ServerBinding]] =
      Tcp().bind("0.0.0.0", 3000)

    connections runForeach { connection =>
      println(s"New connection from: ${connection.remoteAddress}")

      val echo = Flow[ByteString]
        .via(Framing.lengthField(
          fieldLength = 4,
          maximumFrameLength = Integer.MAX_VALUE))
        .mapAsync(20) { request ⇒
          val unpacker = MessagePack.newDefaultUnpacker(request.drop(4).toByteBuffer.array)

          unpacker.unpackValue() match {
            case obj: MapValue ⇒
              val operation = obj.map.get(VF.newString("operation")) match {
                case s: StringValue ⇒ Some(s.asString)
                case _: NilValue ⇒ None
              }

              val query = obj.map.get(VF.newString("query")).asInstanceOf[StringValue].asString

              val vars = obj.map.get(VF.newString("variables")) match {
                case _: NilValue ⇒ VF.emptyMap
                case other ⇒ other
              }

              executeQuery(query, operation, vars)
          }
        }
        .map { executionResult ⇒
          val packer = MessagePack.newDefaultBufferPacker()

          packer.packValue(executionResult)
          packer.close()
          packer.toByteArray
        }
        .map { bytes ⇒
          ByteString.newBuilder
            .putInt(bytes.length)
            .putBytes(bytes)
            .result
        }

      connection.handleWith(echo)
    }
  }

  def client() = {
    case class Request(query: String, operation: Option[String] = None)

    val queries = List(
      Request(
        """
          query HeroAndFriends {
            hero {
              name
              friends {
                name
              }
            }
          }
        """),

      Request(operation = Some("FragmentExample"), query =
        """
          query FragmentExample {
            human(id: "1003") {
              ...Common
              homePlanet
            }

            droid(id: "2001") {
              ...Common
              primaryFunction
            }
          }

          fragment Common on Character {
            name
            appearsIn
          }
        """
      )
    )

    val querySource = Source(queries)
      .map { request ⇒
        val packer = MessagePack.newDefaultBufferPacker()

        packer.packMapHeader(3)
        packer.packString("operation")

        request.operation match {
          case Some(op) ⇒ packer.packString(op)
          case None ⇒ packer.packNil()
        }

        packer.packString("query")
        packer.packString(request.query)

        packer.packString("variables")
        packer.packNil()

        packer.close()

        packer.toByteArray
      }
      .map { bytes ⇒
        ByteString.newBuilder
          .putInt(bytes.length)
          .putBytes(bytes)
          .result
      }

    val sink = Flow[ByteString]
      .via(Framing.lengthField(
        fieldLength = 4,
        maximumFrameLength = Integer.MAX_VALUE))
      .to(Sink.foreach{ x ⇒
        val unpacker = MessagePack.newDefaultUnpacker(x.drop(4).toByteBuffer.array)

        println("Server Response " + unpacker.unpackValue().toJson)
      })

    Tcp().outgoingConnection("127.0.0.1", 3000)
      .to(sink)
      .runWith(querySource)
  }

  server()
  client()
}