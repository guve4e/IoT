import akka.actor.{ActorSystem, PoisonPill}
import akka.testkit.{ImplicitSender, TestActors, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class DeviceActorGroupSpec() extends TestKit(ActorSystem("iot-system"))
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  // Test Registering devices and
  // listing them
  "be able to list active devices" in {
    val probe = TestProbe()
    val groupActor = system.actorOf(DeviceGroup.props("group"))

    groupActor.tell(DeviceManager.RegisterDevice("group", "device1"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)

    groupActor.tell(DeviceManager.RegisterDevice("group", "device2"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)

    groupActor.tell(DeviceGroup.RequestDeviceList(requestId = 0), probe.ref)
    probe.expectMsg(DeviceGroup.ReplyDeviceList(requestId = 0, Set("device1", "device2")))
  }

  "be able to list active devices after one shuts down" in {
    val probe = TestProbe()
    val groupActor = system.actorOf(DeviceGroup.props("group"))

    groupActor.tell(DeviceManager.RegisterDevice("group", "device1"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)
    val toShutDown = probe.lastSender // save reference to this actor so we can shut it down

    groupActor.tell(DeviceManager.RegisterDevice("group", "device2"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)

    groupActor.tell(DeviceGroup.RequestDeviceList(requestId = 0), probe.ref)
    probe.expectMsg(DeviceGroup.ReplyDeviceList(requestId = 0, Set("device1", "device2")))

    // stop device2 actor
    probe.watch(toShutDown)
    toShutDown ! PoisonPill
    probe.expectTerminated(toShutDown)

    // using awaitAssert to retry because it might take longer for the groupActor
    // to see the Terminated, that order is undefined
    probe.awaitAssert {
      groupActor.tell(DeviceGroup.RequestDeviceList(requestId = 1), probe.ref)
      probe.expectMsg(DeviceGroup.ReplyDeviceList(requestId = 1, Set("device2")))
    }
  }

  // Test adding device actors and then sending messages
  // to update temperature
  "be able to collect temperatures from all active devices" in {
    val probe = TestProbe()
    val groupActor = system.actorOf(DeviceGroup.props("group"))

    groupActor.tell(DeviceManager.RegisterDevice("group", "device1"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)
    val deviceActor1 = probe.lastSender

    groupActor.tell(DeviceManager.RegisterDevice("group", "device2"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)
    val deviceActor2 = probe.lastSender

    groupActor.tell(DeviceManager.RegisterDevice("group", "device3"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)
    val deviceActor3 = probe.lastSender

    // Check that the device actors are working
    deviceActor1.tell(DeviceActor.RecordTemperature(requestId = 0, 1.0), probe.ref)
    probe.expectMsg(DeviceActor.TemperatureRecorded(requestId = 0))
    deviceActor2.tell(DeviceActor.RecordTemperature(requestId = 1, 2.0), probe.ref)
    probe.expectMsg(DeviceActor.TemperatureRecorded(requestId = 1))
    // No temperature for device3

    groupActor.tell(DeviceGroup.RequestAllTemperatures(requestId = 1), probe.ref)
    probe.expectMsg(
      DeviceGroup.RespondAllTemperatures(
        requestId = 1,
        temperatures = Map(
          "device1" -> DeviceGroup.Temperature(1.0),
          "device2" -> DeviceGroup.Temperature(2.0),
          "device3" -> DeviceGroup.TemperatureNotAvailable)
      ))
  }
}