import akka.actor.{ActorSystem, PoisonPill}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.duration._

class DeviceActorGroupQuerySpec() extends TestKit(ActorSystem("iot-system"))
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  "return temperature value for working devices" in {
    val requester = TestProbe()
    val device1 = TestProbe()
    val device2 = TestProbe()

    val queryActor = system.actorOf(DeviceGroupQuery.props(
      actorToDeviceId = Map(device1.ref -> "device1", device2.ref -> "device2"),
      requestId = 1,
      requester = requester.ref,
      timeout = 3.seconds
    ))

    device1.expectMsg(DeviceActor.ReadTemperature(requestId = 0))
    device2.expectMsg(DeviceActor.ReadTemperature(requestId = 0))

    queryActor.tell(DeviceActor.TemperatureRead(requestId = 0, Some(1.0)), device1.ref)
    queryActor.tell(DeviceActor.TemperatureRead(requestId = 0, Some(2.0)), device2.ref)

    requester.expectMsg(DeviceGroup.RespondAllTemperatures(
      requestId = 1,
      temperatures = Map(
        "device1" -> DeviceGroup.Temperature(1.0),
        "device2" -> DeviceGroup.Temperature(2.0)
      )
    ))
  }

  "return TemperatureNotAvailable for devices with no readings" in {
    val requester = TestProbe()
    val device1 = TestProbe()
    val device2 = TestProbe()

    val queryActor = system.actorOf(DeviceGroupQuery.props(
      actorToDeviceId = Map(device1.ref -> "device1", device2.ref -> "device2"),
      requestId = 1,
      requester = requester.ref,
      timeout = 3.seconds
    ))

    device1.expectMsg(DeviceActor.ReadTemperature(requestId = 0))
    device2.expectMsg(DeviceActor.ReadTemperature(requestId = 0))

    queryActor.tell(DeviceActor.TemperatureRead(requestId = 0, None), device1.ref)
    queryActor.tell(DeviceActor.TemperatureRead(requestId = 0, Some(2.0)), device2.ref)

    requester.expectMsg(DeviceGroup.RespondAllTemperatures(
      requestId = 1,
      temperatures = Map(
        "device1" -> DeviceGroup.TemperatureNotAvailable,
        "device2" -> DeviceGroup.Temperature(2.0)
      )
    ))
  }

  "return DeviceNotAvailable if device stops before answering" in {
    val requester = TestProbe()

    val device1 = TestProbe()
    val device2 = TestProbe()

    val queryActor = system.actorOf(DeviceGroupQuery.props(
      actorToDeviceId = Map(device1.ref -> "device1", device2.ref -> "device2"),
      requestId = 1,
      requester = requester.ref,
      timeout = 3.seconds
    ))

    device1.expectMsg(DeviceActor.ReadTemperature(requestId = 0))
    device2.expectMsg(DeviceActor.ReadTemperature(requestId = 0))

    queryActor.tell(DeviceActor.TemperatureRead(requestId = 0, Some(1.0)), device1.ref)
    device2.ref ! PoisonPill

    requester.expectMsg(DeviceGroup.RespondAllTemperatures(
      requestId = 1,
      temperatures = Map(
        "device1" -> DeviceGroup.Temperature(1.0),
        "device2" -> DeviceGroup.DeviceNotAvailable
      )
    ))
  }

  "return temperature reading even if device stops after answering" in {
    val requester = TestProbe()

    val device1 = TestProbe()
    val device2 = TestProbe()

    val queryActor = system.actorOf(DeviceGroupQuery.props(
      actorToDeviceId = Map(device1.ref -> "device1", device2.ref -> "device2"),
      requestId = 1,
      requester = requester.ref,
      timeout = 3.seconds
    ))

    device1.expectMsg(DeviceActor.ReadTemperature(requestId = 0))
    device2.expectMsg(DeviceActor.ReadTemperature(requestId = 0))

    queryActor.tell(DeviceActor.TemperatureRead(requestId = 0, Some(1.0)), device1.ref)
    queryActor.tell(DeviceActor.TemperatureRead(requestId = 0, Some(2.0)), device2.ref)
    device2.ref ! PoisonPill

    requester.expectMsg(DeviceGroup.RespondAllTemperatures(
      requestId = 1,
      temperatures = Map(
        "device1" -> DeviceGroup.Temperature(1.0),
        "device2" -> DeviceGroup.Temperature(2.0)
      )
    ))
  }

  "return DeviceTimedOut if device does not answer in time" in {
    val requester = TestProbe()

    val device1 = TestProbe()
    val device2 = TestProbe()

    val queryActor = system.actorOf(DeviceGroupQuery.props(
      actorToDeviceId = Map(device1.ref -> "device1", device2.ref -> "device2"),
      requestId = 1,
      requester = requester.ref,
      timeout = 1.second
    ))

    device1.expectMsg(DeviceActor.ReadTemperature(requestId = 0))
    device2.expectMsg(DeviceActor.ReadTemperature(requestId = 0))

    queryActor.tell(DeviceActor.TemperatureRead(requestId = 0, Some(1.0)), device1.ref)

    requester.expectMsg(DeviceGroup.RespondAllTemperatures(
      requestId = 1,
      temperatures = Map(
        "device1" -> DeviceGroup.Temperature(1.0),
        "device2" -> DeviceGroup.DeviceTimedOut
      )
    ))
  }

  "foo" in {
    val probe = TestProbe()
    val tempSensor = system.actorOf(DeviceActor.props("sensors", "temp sensor"))
    val pressureSensor = system.actorOf(DeviceActor.props("sensors", "pressure sensor"))

    val officeSwitch = system.actorOf(DeviceActor.props("switches", "office_switch"))
    val kitchenSwitch = system.actorOf(DeviceActor.props("switches", "kitchen_switch"))

    tempSensor.tell(DeviceManager.RegisterDevice("sensors", "temp sensor"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)
    tempSensor.tell(DeviceActor.RecordTemperature(12 , 73.45), probe.ref)
    probe.expectMsg(DeviceActor.TemperatureRecorded(12))

    pressureSensor.tell(DeviceManager.RegisterDevice("sensors", "pressure sensor"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)
    pressureSensor.tell(DeviceActor.RecordTemperature(222 , 12.45), probe.ref)
    probe.expectMsg(DeviceActor.TemperatureRecorded(222))

    officeSwitch.tell(DeviceManager.RegisterDevice("switches", "office_switch"), probe.ref)
    officeSwitch.tell(DeviceActor.RecordTemperature(222 , 12.45), probe.ref)



  }
}