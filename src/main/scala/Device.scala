import akka.actor.{ Actor, ActorLogging, Props }

/**
  * @see https://doc.akka.io/docs/akka/2.5/guide/tutorial_4.html
  */
object Device {
  def props(groupId: String, deviceId: String): Props =
    Props(new Device(groupId, deviceId))

  // Messages: request / response

  final case class RecordTemperature(requestId: Long, value: Double)
  final case class TemperatureRecorded(requestId: Long)

  final case class ReadTemperature(requestId: Long)
  final case class RespondTemperature(requestId: Long, value: Option[Double])
}

class Device(groupId: String, deviceId: String)
  extends Actor with ActorLogging {

  import Device._
  var lastTemperatureReading: Option[Double] = None

  override def preStart(): Unit =
    log.info("Device actor {}-{} started", groupId, deviceId)
  override def postStop(): Unit =
    log.info("Device actor {}-{} stopped", groupId, deviceId)

  override def receive: Receive = {
    case DeviceManager.RequestTrackDevice(`groupId`, `deviceId`) =>
      // if we receive track message that
      // matches existing device,
      // answer that device is registered already
      sender() ! DeviceManager.DeviceRegistered

    case DeviceManager.RequestTrackDevice(groupId, deviceId) =>
      log.warning(
        "Ignoring TrackDevice request for {}-{}.This actor is responsible for {}-{}.",
        groupId, deviceId, this.groupId, this.deviceId
      )

    case RecordTemperature(id, value) =>
      log.info("Recorded temperature reading {} with {}", value, id)

      // if we receive message to record temperature
      lastTemperatureReading = Some(value)
      sender() ! TemperatureRecorded(id)

    case ReadTemperature(id) =>
      sender() ! RespondTemperature(id, lastTemperatureReading)
  }
}