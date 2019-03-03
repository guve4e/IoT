import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActors, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.duration._

class DeviceSpec() extends TestKit(ActorSystem("iot-system"))
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "reply to registration requests" in {
    val probe = TestProbe()
    val deviceActor = system.actorOf(Device.props("group", "device"))

    deviceActor.tell(DeviceManager.RequestTrackDevice("group", "device"), probe.ref)

    probe.expectMsg(DeviceManager.DeviceRegistered)
    probe.lastSender should === (deviceActor)
  }

  "ignore wrong registration requests" in {
    val probe = TestProbe()
    val deviceActor = system.actorOf(Device.props("group", "device"))

    deviceActor.tell(DeviceManager.RequestTrackDevice("wrongGroup", "device"), probe.ref)
    probe.expectNoMessage(500.milliseconds)

    deviceActor.tell(DeviceManager.RequestTrackDevice("group", "Wrongdevice"), probe.ref)
    probe.expectNoMessage(500.milliseconds)
  }
}