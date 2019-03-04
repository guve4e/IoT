import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props, Terminated}

import scala.concurrent.duration.FiniteDuration

object DeviceGroupQuery {
  case object CollectionTimeout

  def props(
             actorToDeviceId: Map[ActorRef, String],
             requestId:       Long,
             requester:       ActorRef,
             timeout:         FiniteDuration
           ): Props = {
    Props(new DeviceGroupQuery(actorToDeviceId, requestId, requester, timeout))
  }
}

class DeviceGroupQuery(
                        actorToDeviceIds: Map[ActorRef, String],
                        requestId: Long,
                        requester: ActorRef,
                        timeout: FiniteDuration
                      )
  extends Actor with ActorLogging {

  import DeviceGroupQuery._
  import context.dispatcher

  val queryTimeoutTimer: Cancellable =
    context.system.scheduler.scheduleOnce(timeout, self, CollectionTimeout)

  override def preStart(): Unit = {
    // Before start iterate trough the
    // map with actors injected and
    // start watching each actor
    actorToDeviceIds.keysIterator.foreach {
      deviceActor =>
        context.watch(deviceActor)
        deviceActor ! DeviceActor.ReadValues(0)
    }
  }

  override def postStop(): Unit = {
    // Before Stop, cancel the timer
    queryTimeoutTimer.cancel()
  }

  override def receive: Receive =
    waitingForReplies(Map.empty, actorToDeviceIds.keySet)

  def waitingForReplies(
                         repliesSoFar: Map[String, DeviceGroup.ValuesReading],
                         stillWaiting: Set[ActorRef]
                       ): Receive =
  {
    case DeviceActor.ValuesRead(0, valueOption) =>
      val deviceActor = sender()
      val reading = valueOption match {
        case Some(value) => DeviceGroup.Values(Nil)
        case None        => DeviceGroup.ValuesNotAvailable
      }
      receivedResponse(deviceActor, reading, stillWaiting, repliesSoFar)

    case Terminated(deviceActor) =>
      receivedResponse(deviceActor, DeviceGroup.DeviceNotAvailable, stillWaiting, repliesSoFar)

    case CollectionTimeout =>
      val timedOutReplies =
        stillWaiting.map { deviceActor =>
          val deviceId = actorToDeviceIds(deviceActor)
          deviceId -> DeviceGroup.DeviceTimedOut
        }
      requester ! DeviceGroup.RespondAllValues(requestId, repliesSoFar ++ timedOutReplies)
      context.stop(self)
  }
  //#query-state

  //#query-collect-reply
  def receivedResponse(
                        deviceActor:  ActorRef,
                        reading:      DeviceGroup.ValuesReading,
                        stillWaiting: Set[ActorRef],
                        repliesSoFar: Map[String, DeviceGroup.ValuesReading]
                      ): Unit = {
    context.unwatch(deviceActor)
    val deviceId = actorToDeviceIds(deviceActor)
    val newStillWaiting = stillWaiting - deviceActor

    val newRepliesSoFar = repliesSoFar + (deviceId -> reading)
    if (newStillWaiting.isEmpty) {
      requester ! DeviceGroup.RespondAllValues(requestId, newRepliesSoFar)
      context.stop(self)
    } else {
      context.become(waitingForReplies(newRepliesSoFar, newStillWaiting))
    }
  }
}