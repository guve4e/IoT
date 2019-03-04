import akka.actor.{ Actor, ActorLogging, Props }

/**
  * @see https://doc.akka.io/docs/akka/2.5/guide/tutorial_4.html
  */
object DeviceActor {

  def props[T](device: T, groupId: String, deviceId: String): Props =
    Props(new DeviceActor(device, groupId, deviceId))

  // Messages: request / response

  final case class SendCommand[T](requestId: Long, value: T)
  final case class CommandSent(requestId: Long)

  final case class ReadValues(requestId: Long)
  final case class ValuesRead[T](requestId: Long, value: T)
}

class DeviceActor[T](device: T, groupId: String, deviceId: String)
  extends Actor with ActorLogging {

  import DeviceActor._

  override def preStart(): Unit =
    log.info("Device actor {}-{} started", groupId, deviceId)
  override def postStop(): Unit =
    log.info("Device actor {}-{} stopped", groupId, deviceId)

  override def receive: Receive = {
    case DeviceManager.RegisterDevice(device, `groupId`, `deviceId`) =>
      // if we receive track message that
      // matches existing device,
      // answer that device is registered already
      sender() ! DeviceManager.DeviceRegistered

    case DeviceManager.RegisterDevice(device, groupId, deviceId) =>
      log.warning(
        "Ignoring TrackDevice request for {}-{}.This actor is responsible for {}-{}.",
        groupId, deviceId, this.groupId, this.deviceId
      )

    case SendCommand(id, value) =>
      log.info("Recorded temperature reading {} with {}", value, id)

      // TODO what we do here
      sender() ! CommandSent(id)

    case ReadValues(id) =>

      sender() ! ValuesRead(id, Nil)
  }
}