import akka.actor.{ActorSystem, PoisonPill}
import akka.testkit.{ImplicitSender, TestActors, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class DeviceActorGroupSpec() extends TestKit(ActorSystem("iot-system"))
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  class WaterMeter(value: Double)

  // Test Registering devices and
  // listing them
  "be able to list active devices" in {
    val probe = TestProbe()
    val groupActor = system.actorOf(DeviceGroup.props("group"))

    groupActor.tell(DeviceManager.RegisterDevice(new WaterMeter(12.45), "group", "device1"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)

    groupActor.tell(DeviceManager.RegisterDevice(new WaterMeter(12.45), "group", "device2"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)

    groupActor.tell(DeviceGroup.RequestDeviceList(requestId = 0), probe.ref)
    probe.expectMsg(DeviceGroup.ReplyDeviceList(requestId = 0, Set("device1", "device2")))
  }

  "be able to list active devices after one shuts down" in {
    val probe = TestProbe()
    val groupActor = system.actorOf(DeviceGroup.props("group"))

    groupActor.tell(DeviceManager.RegisterDevice(new WaterMeter(12.45), "group", "device1"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)
    val toShutDown = probe.lastSender // save reference to this actor so we can shut it down

    groupActor.tell(DeviceManager.RegisterDevice(new WaterMeter(12.45), "group", "device2"), probe.ref)
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
//
  // Test adding device actors and then sending messages
  // to update temperature
  "be able to collect temperatures from all active devices" in {
    val probe = TestProbe()
    val groupActor = system.actorOf(DeviceGroup.props("group"))

    groupActor.tell(DeviceManager.RegisterDevice(new WaterMeter(12.45), "group", "device1"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)
    val deviceActor1 = probe.lastSender

    groupActor.tell(DeviceManager.RegisterDevice(new WaterMeter(12.45), "group", "device2"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)
    val deviceActor2 = probe.lastSender

    groupActor.tell(DeviceManager.RegisterDevice(new WaterMeter(12.45), "group", "device3"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)
    val deviceActor3 = probe.lastSender

    // Check that the device actors are working
    deviceActor1.tell(DeviceActor.SendCommand(requestId = 0, "Some Command"), probe.ref)
    probe.expectMsg(DeviceActor.CommandSent(requestId = 0))
    deviceActor2.tell(DeviceActor.SendCommand(requestId = 1, "Some Other Command"), probe.ref)
    probe.expectMsg(DeviceActor.CommandSent(requestId = 1))

    groupActor.tell(DeviceGroup.RequestAllValues(requestId = 1), probe.ref)
//    probe.expectMsg(
//      DeviceGroup.RespondAllValues(
//        requestId = 1,
//        values = Map(
//          "device1" -> DeviceGroup.ValuesNotAvailable,
//          "device2" -> DeviceGroup.ValuesNotAvailable,
//          "device3" -> DeviceGroup.ValuesNotAvailable)
//      ))
  }
}