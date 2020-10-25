package part1.serialization

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import akka.actor.{ActorSystem, Props}
import akka.serialization.Serializer
import com.sksamuel.avro4s.{AvroInputStream, AvroOutputStream, AvroSchema}
import com.typesafe.config.ConfigFactory
import common.{SimpleActor, SimplePersistentActor}

// The avor library that allows us to create and validate schemas is "com.sksamuel.avro4s" %% "avro4s-core" % "2.0.4"

case class BankAccount(iban: String, bankCode: String, amount: Double, currency: String)

case class CompanyRegistry(name: String, accounts: Seq[BankAccount], activityCode: String, marketCap: Double)

object L3_AvroSerialization{
  val companyRegistryExample = CompanyRegistry(
    "Google",
    Seq(BankAccount("US-1234", "google-account1", 100.0, "USD"), BankAccount("UK-1234", "google-account2", 200.0, "GBP")),
    "ADS",
    1000.0)
}

class AvroSerializer extends Serializer {
  val companyRegistrySchema = AvroSchema[CompanyRegistry]

  println(companyRegistrySchema) // This is how you create an avro schema for the type class

  override def identifier: Int = 7237372

  override def toBinary(o: AnyRef): Array[Byte] =
    o match {
      case cr: CompanyRegistry =>
        println(s"Serializing the CompanyRegistry [${cr}]")
        val baos = new ByteArrayOutputStream()
        // Builder pattern to get the outputStream
        val avroOutputStream = AvroOutputStream.binary[CompanyRegistry].to(baos).build(companyRegistrySchema)
        avroOutputStream.write(cr)
        avroOutputStream.flush()
        avroOutputStream.close()
        baos.toByteArray
      case _ => throw new IllegalArgumentException("Only support CompanyRegistry")
    }

  override def includeManifest: Boolean = false

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val inputStream = AvroInputStream.binary[CompanyRegistry].from(new ByteArrayInputStream(bytes)).build(companyRegistrySchema)
    // The way we can read objects from this input stream is to access its iterator, because in our output stream
    // you can write a single object or you can write multiple objects.
    val it:Iterator[CompanyRegistry] = inputStream.iterator
    val cr = it.next()
    inputStream.close() // We need to close the stream to not leak resources
    cr
  }
}

//Look at the customSerialization.conf to find how to configure the serializers
object L3_CustomSerializationLocal extends App {
  val config = ConfigFactory.parseString("akka.remote.artery.canonical.port=2551")
    .withFallback(ConfigFactory.load("avroSerialization"))

  val system = ActorSystem("LocalSystem", config)
  val simpleActor = system.actorOf(Props[SimpleActor], "LocalActor")

  val remoteActor = system.actorSelection("akka://RemoteSystem@localhost:2552/user/RemoteActor")

  remoteActor ! L3_AvroSerialization.companyRegistryExample
}

object L3_CustomSerializationRemote extends App {
  val config = ConfigFactory.parseString("akka.remote.artery.canonical.port=2552")
    .withFallback(ConfigFactory.load("avroSerialization"))

  val system = ActorSystem("RemoteSystem", config)
  val simpleActor = system.actorOf(Props[SimpleActor], "RemoteActor")
}

// App to test persistence
object L3_CustomSerializationPersistent extends App {
  val config = ConfigFactory.load("persistentStores").getConfig("postgresStore")
    .withFallback(ConfigFactory.load("avroSerialization"))

  val system = ActorSystem("PersistenceSystem", config)
  val simplePersistenceActor =
    system.actorOf(SimplePersistentActor.props("avro-actor"), "PersonJsonActor")

  simplePersistenceActor ! L3_AvroSerialization.companyRegistryExample
}