import mill._
import scalalib._
import scalafmt._
import publish._

object Deps {
  val scalaVersion = "2.13.11"

  val chiselVersion = "5.0.0"
  val chisel        = ivy"org.chipsalliance::chisel:$chiselVersion"
  val chiselPlugin  = ivy"org.chipsalliance:::chisel-plugin:$chiselVersion"

  val scalaTest = ivy"org.scalatest::scalatest:3.2.16"
}

trait CommonModule extends ScalaModule with ScalafmtModule {
  override def scalaVersion = Deps.scalaVersion

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

  override def ivyDeps             = Agg(Deps.chisel)
  override def scalacPluginIvyDeps = Agg(Deps.chiselPlugin)

  object test extends Tests {
    override def ivyDeps = super.ivyDeps() ++ Agg(Deps.scalaTest)
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
