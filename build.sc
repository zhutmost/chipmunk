import mill._
import scalalib._
import scalafmt._

object Deps {
  val scalaVersion = "2.13.10"

  val chiselVersion = "3.6.0-RC1"
  val chisel        = ivy"edu.berkeley.cs::chisel3:$chiselVersion"
  val chiselPlugin  = ivy"edu.berkeley.cs:::chisel3-plugin:$chiselVersion"
  val chiselTest    = ivy"edu.berkeley.cs::chiseltest:0.5.6"
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
    override def ivyDeps = super.ivyDeps() ++ Agg(Deps.chiselTest)
  }
}

object chipmunk extends CommonModule

object mylib extends CommonModule {
  override def moduleDeps: Seq[ScalaModule] = Seq(chipmunk)
}
