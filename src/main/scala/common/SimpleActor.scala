package common

import akka.actor.{Actor, ActorLogging}

class SimpleActor extends Actor with ActorLogging{
  override def receive: Receive = {
    case msg => log.info(s"Received ${msg}")
  }
}
