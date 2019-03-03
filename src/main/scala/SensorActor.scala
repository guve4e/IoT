import java.io.{File, FileInputStream}
import groovy.lang.{Binding, GroovyShell}

class SensorActor {

  val shell = new GroovyShell()
  val script = shell.parse(new File("src/main/scala/MyScript.groovy"))

  script.invokeMethod("method", 12)
  script.invokeMethod("foo", 12)

}

object App {

  def main(args: Array[String]): Unit = {
    val s = new SensorActor
  }

}