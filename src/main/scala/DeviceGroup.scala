import DeviceGroup.{ReplyDeviceList, RequestAllValues, RequestDeviceList, RespondAllValues}
import DeviceManager.RegisterDevice
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}

import scala.concurrent.duration._

object DeviceGroup {
  def props(groupId: String): Props =
    Props(new DeviceGroup(groupId))

  final case class RequestDeviceList(requestId: Long)
  final case class ReplyDeviceList(requestId: Long, ids: Set[String])

  //#query-protocol
  final case class RequestAllValues(requestId: Long)
  final case class RespondAllValues(requestId: Long, values: Map[String, ValuesReading])

  sealed trait ValuesReading
  final case class Values[T](value: T) extends ValuesReading
  case object ValuesNotAvailable extends ValuesReading
  case object DeviceNotAvailable extends ValuesReading
  case object DeviceTimedOut extends ValuesReading
  //#query-protocol
}

//#query-added
class DeviceGroup(groupId: String) extends Actor with ActorLogging {
  var deviceIdToActor = Map.empty[String, ActorRef]
  var actorToDeviceId = Map.empty[ActorRef, String]
  var nextCollectionId = 0L

  override def preStart(): Unit = log.info("DeviceGroup {} started", groupId)

  override def postStop(): Unit = log.info("DeviceGroup {} stopped", groupId)

  override def receive: Receive = {

    case trackMsg @ RegisterDevice(device, `groupId`, _) ⇒
      deviceIdToActor.get(trackMsg.deviceId) match {
        case Some(ref) ⇒
          ref forward trackMsg
        case None ⇒
          log.info("Creating device actor for {}", trackMsg.deviceId)
          val deviceActor = context.actorOf(DeviceActor.props(device, groupId, trackMsg.deviceId), "device-" + trackMsg.deviceId)
          context.watch(deviceActor)
          deviceActor forward trackMsg
          deviceIdToActor += trackMsg.deviceId -> deviceActor
          actorToDeviceId += deviceActor -> trackMsg.deviceId
      }

    case RegisterDevice(device, groupId, deviceId) ⇒
      log.warning(
        "Ignoring TrackDevice request for {}. This actor is responsible for {}.",
        groupId, this.groupId
      )

    case RequestDeviceList(requestId) ⇒
      sender() ! ReplyDeviceList(requestId, deviceIdToActor.keySet)

    case Terminated(deviceActor) =>
      val deviceId = actorToDeviceId(deviceActor)
      log.info("Device actor for {} has been terminated", deviceId)
      actorToDeviceId -= deviceActor
      deviceIdToActor -= deviceId

    case RequestAllValues(requestId) =>
      context.actorOf(DeviceGroupQuery.props(
        actorToDeviceId = actorToDeviceId,
        requestId = requestId,
        requester = sender(),
        4.seconds
      ))
  }
}