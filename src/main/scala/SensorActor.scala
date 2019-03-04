import java.io.{File, FileInputStream}
import groovy.lang.{Binding, GroovyShell}

class SensorActor {

  val shell = new GroovyShell()
  val script = shell.parse(new File("src/main/scala/Main.groovy"))

  print(script.invokeMethod("get", 12))

}

object App {

  def main(args: Array[String]): Unit = {
    val s = new SensorActor
  }

}