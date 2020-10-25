package common

import akka.actor.{ActorLogging, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted}

class SimplePersistentActor(override val persistenceId:String, shouldLog:Boolean) extends PersistentActor with ActorLogging{

  override def receiveRecover: Receive = {
    case msg => if(shouldLog) log.info(s"Recovered ${msg}")
  }
  override def receiveCommand: Receive = {
    case message  => persist(message) { _ =>
      if (shouldLog) log.info(s"Persisted message $message")
    }
  }
}

object SimplePersistentActor{
  def props(persistenceId:String, shouldLog:Boolean=true) = Props(new SimplePersistentActor(persistenceId, shouldLog))
}