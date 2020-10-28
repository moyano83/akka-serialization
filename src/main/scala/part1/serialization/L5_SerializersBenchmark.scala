package part1.serialization

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.serialization.Serializer
import com.sksamuel.avro4s.{AvroInputStream, AvroOutputStream, AvroSchema}
import com.typesafe.config.ConfigFactory
import common.SimplePersistentActor
import part1.serialization.VoteModel.ProtobufVote

import scala.util.Random

// Simulate a voting scenario, with a poll that receives millions of votes and we want to serialize the messages as fast
// as possible and be the smallest possible

case class Vote(ssn: String, candidate: String)

case object VoteEnd

object VoteGenerator {
  val random = new Random()
  val candidates = Seq("Alice", "Bob", "Charlie", "Tim", "Laura")

  def getRandomCandidate(): String = candidates(random.nextInt(candidates.size))

  def generateVotes(n: Int) = (1 to n).map(_ => Vote(UUID.randomUUID().toString, getRandomCandidate()))

  def generateProtobufVotes(n: Int) =
    (1 to n).map(_ => ProtobufVote.newBuilder().setCandidate(getRandomCandidate()).setSsn(UUID.randomUUID().toString).build())
}

class VoteAggregator extends Actor with ActorLogging {
  override def receive: Receive = ready

  def ready: Receive = {
    case _: Vote | _: ProtobufVote => context.become(online(1, System.currentTimeMillis()))
  }

  def online(voteCount: Int, originalTime: Long): Receive = {
    case _: Vote | _: ProtobufVote => context.become(online(voteCount + 1, originalTime))
    case VoteEnd =>
      val duration = ((System.currentTimeMillis() - originalTime) * 1.0) / 1000
      log.info(s"Received ${voteCount} votes in ${duration.formatted("%5.3f")} seconds")
      context.become(ready)
  }
}

class VoteAvroSerializer extends Serializer {
  val voteSchema = AvroSchema[Vote]

  override def identifier: Int = 1234567

  override def toBinary(o: AnyRef): Array[Byte] =
    o match {
      case vote: Vote =>
        println(s"Serializing the vote [${vote}]")
        val baos = new ByteArrayOutputStream()
        val avroOutputStream = AvroOutputStream.binary[Vote].to(baos).build(voteSchema)
        avroOutputStream.write(vote)
        avroOutputStream.flush()
        avroOutputStream.close()
        baos.toByteArray
      case _ => throw new IllegalArgumentException("Only support Vote")
    }

  override def includeManifest: Boolean = false

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val inputStream = AvroInputStream.binary[Vote].from(new ByteArrayInputStream(bytes)).build(voteSchema)
    val it: Iterator[Vote] = inputStream.iterator
    val v = it.next()
    inputStream.close() // We need to close the stream to not leak resources
    v
  }
}

object L5_VotingStation extends App {
  val config = ConfigFactory.parseString("akka.remote.artery.canonical.port=2551")
    .withFallback(ConfigFactory.load("serializersBenchmark.conf"))

  val system = ActorSystem("VotingStation", config)
  val remoteActor = system.actorSelection("akka://VotingCentralizer@localhost:2552/user/VotingAggregator")

  // VoteGenerator.generateVotes(100000).foreach(v => remoteActor ! v)
  VoteGenerator.generateProtobufVotes(10000000).foreach(v => remoteActor ! v)
  remoteActor ! VoteEnd
}

object L5_VotingCentralizer extends App {
  val config = ConfigFactory.parseString("akka.remote.artery.canonical.port=2552")
    .withFallback(ConfigFactory.load("serializersBenchmark.conf"))

  val system = ActorSystem("VotingCentralizer", config)
  val votingAggregator = system.actorOf(Props[VoteAggregator], "VotingAggregator")

}

// App to test persistence
object L5_VotingSerializationPersistent extends App {
  val config = ConfigFactory.load("persistentStores").getConfig("postgresStore")
    .withFallback(ConfigFactory.load("serializersBenchmark.conf"))

  val system = ActorSystem("PersistenceSystem", config)
  val simplePersistenceActor =
    system.actorOf(SimplePersistentActor.props("benchmark-proto", false), "BenchmarkActor")

  // VoteGenerator.generateVotes(10000).foreach(vote => simplePersistenceActor ! vote)
  VoteGenerator.generateProtobufVotes(10000).foreach(vote => simplePersistenceActor ! vote)
}

/**
 * Time results:
 * 100K
 * - Java serialization => 2.110 seconds
 * - Avro serialization => 2.114 seconds
 * - kryo serialization => 1.178 seconds
 * - protobuf serialization => 1.100 seconds
 * 1M
 * - Java serialization => 12.729 seconds
 * - Avro serialization => 9.971 seconds
 * - kryo serialization => 6.813 seconds
 * - protobuf serialization => 6.596 seconds
 * Memory test results (avg message length)
 * 10k
 * - Java serialization => 201.25
 * - Avro serialization => 134.24
 * - kryo serialization => 156.22
 * - protobuf serialization => 108.25
 */