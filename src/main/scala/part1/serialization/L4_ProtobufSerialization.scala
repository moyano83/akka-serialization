package part1.serialization

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import common.{SimpleActor, SimplePersistentActor}
import part1.serialization.DataModel.OnlineStoreUser

object L4_ProtobufSerialization{
  // Protobuf data is created with a builder
  val datamodel = OnlineStoreUser.newBuilder().setUserId(123).setUserName("Jorge").setUserEmail("jorge@test.com").build()
}
/**
 * The java class under java/part1/serialization/DataModel.java has been generated using the following command (from src):
 * $> ./main/exec/protoc --java_out=main/java main/proto/DataModel.proto
 */
object L4_ProtobufSerializationLocal extends App{
  val config = ConfigFactory.parseString("akka.remote.artery.canonical.port=2551")
    .withFallback(ConfigFactory.load("protobufSerialization.conf"))

  val system = ActorSystem("LocalSystem", config)
  val simpleActor = system.actorOf(Props[SimpleActor], "LocalActor")

  val remoteActor = system.actorSelection("akka://RemoteSystem@localhost:2552/user/RemoteActor")
  remoteActor ! L4_ProtobufSerialization.datamodel
}
object L4_ProtobufSerializationRemote extends App{
  val config = ConfigFactory.parseString("akka.remote.artery.canonical.port=2552")
    .withFallback(ConfigFactory.load("protobufSerialization.conf"))

  val system = ActorSystem("RemoteSystem", config)
  val simpleActor = system.actorOf(Props[SimpleActor], "RemoteActor")
}
// App to test persistence
object L4_ProtobufSerializationPersistent extends App{
  val config = ConfigFactory.load("persistentStores").getConfig("postgresStore")
    .withFallback(ConfigFactory.load("protobufSerialization.conf"))

  val system = ActorSystem("PersistenceSystem", config)
  val simplePersistenceActor =
    system.actorOf(SimplePersistentActor.props("protobuf-actor"), "ProtobufActor")

  simplePersistenceActor ! L4_ProtobufSerialization.datamodel
}
