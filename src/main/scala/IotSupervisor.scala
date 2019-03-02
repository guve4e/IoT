import akka.actor.{ Actor, ActorLogging, Props }

/*
 * We use the recommended pattern for creating actors
 * by defining a props() method in the companion
 * object of the actor.
 */
object IotSupervisor {
  def props(): Props = Props(new IotSupervisor)
}

class IotSupervisor extends Actor with ActorLogging {
  override def preStart(): Unit = log.info("IoT Application started")
  override def postStop(): Unit = log.info("IoT Application stopped")

  // No need to handle any messages
  override def receive: Actor.emptyBehavior.type = Actor.emptyBehavior
}