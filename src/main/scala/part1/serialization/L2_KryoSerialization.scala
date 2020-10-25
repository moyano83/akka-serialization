package part1.serialization
import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import common.{SimpleActor, SimplePersistentActor}


case class Book(title: String, year: Int)

object L2_KryoSerializationLocal extends App {
  val config = ConfigFactory.parseString(
    """
      |akka.remote.artery.canonical.port = 2551
    """.stripMargin)
    .withFallback(ConfigFactory.load("kryoSerialization"))

  val system = ActorSystem("LocalSystem", config)
  val actorSelection = system.actorSelection("akka://RemoteSystem@localhost:2552/user/remoteActor")

  actorSelection ! Book("The Rock the JVM Experience", 2019)
}

object L2_KryoSerializationRemote extends App {
  val config = ConfigFactory.parseString(
    """
      |akka.remote.artery.canonical.port = 2552
    """.stripMargin)
    .withFallback(ConfigFactory.load("kryoSerialization"))

  val system = ActorSystem("RemoteSystem", config)
  val simpleActor = system.actorOf(Props[SimpleActor], "remoteActor")
}

object L2_KryoSerializationPersistence extends App {
  val config = ConfigFactory.load("persistentStores").getConfig("postgresStore")
    .withFallback(ConfigFactory.load("kryoSerialization"))

  val system = ActorSystem("PersistenceSystem", config)
  val simplePersistentActor = system.actorOf(SimplePersistentActor.props("kryo-actor"), "kryoBookActor")

  //  simplePersistentActor ! Book("The Rock the JVM Experience", 2019)
}