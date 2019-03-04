import DeviceManager.RegisterDevice
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}

/**
  * Protocol both for registering a device and
  * for creating the group and device actors
  * that will be responsible for it
  *
  * When a DeviceManager receives a request with a group and device id:
  * - If the manager already has an actor for the device group,
  *   it forwards the request to it.
  *   Otherwise, it creates a new device group actor and then forwards
  *   the request. The DeviceGroup actor receives the request to register
  *   an actor for the given device:
  *
  * - If the group already has an actor for the device,
  *   the group actor forwards the request to the device actor.
  *
  *   Otherwise, the DeviceGroup actor first creates a device actor and
  *   then forwards the request.
  *   The device actor receives the request and sends an acknowledgement
  *   to the original sender.
  *
  *  Since the device actor acknowledges receipt (instead of the group actor),
  *  the sensor will now have the ActorRef to send messages directly to its actor.
  */
object DeviceManager {
  def props(): Props = Props(new DeviceManager)

  final case class RegisterDevice[T](device: T, groupId: String, deviceId: String)
  case object DeviceRegistered
}

class DeviceManager extends Actor with ActorLogging {

  var groupIdToActor = Map.empty[String, ActorRef]
  var actorToGroupId = Map.empty[ActorRef, String]

  override def preStart(): Unit = log.info("DeviceManager started")
  override def postStop(): Unit = log.info("DeviceManager stopped")

  override def receive: PartialFunction[Any, Unit] = {

    case trackMsg @ RegisterDevice(device, groupId, _) =>
      groupIdToActor.get(groupId) match {
        case Some(ref) =>
          ref forward trackMsg

        case None =>
          log.info("Creating device group actor for {}", groupId)

          val groupActor = context.actorOf(DeviceGroup.props(groupId), "group-" + groupId)

          context.watch(groupActor)

          groupActor forward trackMsg
          groupIdToActor += groupId -> groupActor
          actorToGroupId += groupActor -> groupId
      }

    case Terminated(groupActor) =>
      val groupId = actorToGroupId(groupActor)

      log.info("Device group actor for {} has been terminated", groupId)

      actorToGroupId -= groupActor
      groupIdToActor -= groupId
  }
}