package part1.serialization

import akka.actor.{ActorSystem, Props}
import akka.serialization.Serializer
import com.typesafe.config.ConfigFactory
import common.{SimpleActor, SimplePersistentActor}
import spray.json._

case class Person(name:String, age:Int)
/**
 * Class to serialize Persons, extends special
 */
class PersonSerializer extends Serializer{
  val separator = "//"
  override def identifier: Int = 71341267 // This needs to be constant through the app
  /**
   * This is a flag that would indicate wether to pass the manifest shown in the fromBinary, which will tell you the type
   * of class to convert to when deserializing
   * @return true/false
   */
  override def includeManifest: Boolean = false

  override def toBinary(o: AnyRef): Array[Byte] = {
    o match {
      case person @ Person(name, age) => {
        // Imagine that we serialize this like [name||age]
        println(s"Serializing $person")
        s"[${name}${separator}${age}]".getBytes
      }
      case _ => throw new IllegalArgumentException("Only persons are allowed for this serialization")
    }
  }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef ={
    val stringPerson = new String(bytes)
    val values = stringPerson.substring(1, stringPerson.length -1) // To remove the [ and ]
      .split(separator)
    if (values.length != 2)
      throw new IllegalArgumentException("Invalid number of values in Person class")
    else {
      println(s"Got a person with name=[${values(0)}] and age=[${values(1)}]")
      Person(values(0),values(1).toInt)
    }
  }
}

class PersonJsonSerializer extends Serializer with DefaultJsonProtocol {

  implicit val jsonFormat = jsonFormat2(Person)

  override def identifier: Int = 12412

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case person @ Person(name, age) => {
      println(s"Serializing person ${person} in JSON")
      person.toJson.prettyPrint.getBytes
    }
    case _ => throw new IllegalArgumentException("Only persons are allowed for this serialization")
  }

  override def includeManifest: Boolean = false

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val personString = new String(bytes)
    val person = personString.parseJson.convertTo[Person]
    println(s"Received json ${personString} and converted to person ${person}")
    person
  }
}

//Look at the customSerialization.conf to find how to configure the serializers
object L1_CustomSerializationLocal extends App{
  val config = ConfigFactory.parseString("akka.remote.artery.canonical.port=2551")
    .withFallback(ConfigFactory.load("customSerialization.conf"))

  val system = ActorSystem("LocalSystem", config)
  val simpleActor = system.actorOf(Props[SimpleActor], "LocalActor")

  val remoteActor = system.actorSelection("akka://RemoteSystem@localhost:2552/user/RemoteActor")
  remoteActor ! Person("Alice", 30)
}
object L1_CustomSerializationRemote extends App{
  val config = ConfigFactory.parseString("akka.remote.artery.canonical.port=2552")
    .withFallback(ConfigFactory.load("customSerialization.conf"))

  val system = ActorSystem("RemoteSystem", config)
  val simpleActor = system.actorOf(Props[SimpleActor], "RemoteActor")
}
// App to test persistence
object L1_CustomSerializationPersistent extends App{
  val config = ConfigFactory.load("persistentStores").getConfig("postgresStore")
    .withFallback(ConfigFactory.load("customSerialization"))

  val system = ActorSystem("PersistenceSystem", config)
  val simplePersistenceActor =
    system.actorOf(SimplePersistentActor.props("person-json"), "PersonJsonActor")

  // simplePersistenceActor ! Person("Jorge", 37)
}