import mill._
import scalalib._
import scalafmt._
import publish._

object Dependencies {
  val scalaVersion = "2.13.14"

  val chiselVersion = "6.3.0"
  val chisel        = ivy"org.chipsalliance::chisel:$chiselVersion"
  val chiselPlugin  = ivy"org.chipsalliance:::chisel-plugin:$chiselVersion"

  val scalaTest = ivy"org.scalatest::scalatest:3.2.18"
}

trait CommonModule extends ScalaModule with ScalafmtModule {
  override def scalaVersion = Dependencies.scalaVersion

  override def scalacOptions = T {
    super.scalacOptions() ++
      Agg(
        "-deprecation",
        "-feature",
        "-Xcheckinit",
        "-Xfatal-warnings",
        "-language:existentials",
        "-language:higherKinds",
        "-language:reflectiveCalls" // Required by Chisel
      )
  }

  override def ivyDeps             = Agg(Dependencies.chisel)
  override def scalacPluginIvyDeps = Agg(Dependencies.chiselPlugin)

  object test extends ScalaTests with TestModule.ScalaTest {
    override def ivyDeps = super.ivyDeps() ++ Agg(Dependencies.scalaTest)
  }
}

object chipmunk extends CommonModule with PublishModule {
  def publishVersion = "0.1-SNAPSHOT"

  def pomSettings = PomSettings(
    description = "CHIPMUNK: Enhance CHISEL for Smooth and Comfortable Chip Design",
    organization = "com.zhutmost",
    url = "https://github.com/zhutmost/chipmunk",
    licenses = Seq(License.MIT),
    versionControl = VersionControl.github("zhutmost", "chipmunk"),
    developers = Seq(Developer("zhutmost", "Haozhe Zhu", "https://github.com/zhutmost"))
  )
}

object mylib extends CommonModule {
  override def moduleDeps: Seq[ScalaModule] = Seq(chipmunk)
}
