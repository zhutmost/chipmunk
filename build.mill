package build

import mill._
import mill.scalalib._
import mill.scalalib.publish._
import mill.scalalib.scalafmt._

object Dependencies {
  val scalaVersion = "2.13.15"

  val chiselVersion = "6.6.0"
  val chisel        = ivy"org.chipsalliance::chisel:$chiselVersion"
  val chiselPlugin  = ivy"org.chipsalliance:::chisel-plugin:$chiselVersion"

  val scalaTest = ivy"org.scalatest::scalatest:3.2.19"
}

trait CommonModule extends ScalaModule with ScalafmtModule {
  override def scalaVersion = Dependencies.scalaVersion

  override def scalacOptions = super.scalacOptions() ++ Seq(
    "-deprecation", // Emit warning and location for usages of deprecated APIs.
    "-explaintypes", // Explain type errors in more detail.
    "-Werror", // Fail the compilation if there are any warnings.
    "-feature", // Emit warning and location for usages of features that should be imported explicitly.
    "-Xcheckinit", // Wrap field accessors to throw an exception on uninitialized access.
    "-language:higherKinds", // Allow higher-kinded types
    "-language:implicitConversions", // Allow definition of implicit functions called views
    "-language:reflectiveCalls", // Allow reflective access to members of structural types
    "-unchecked", // Enable additional warnings where generated code depends on assumptions.

    // Suppress DelayedInit warnings because it is still used by StateMachine.
    "-Xlint:_,-delayedinit-select",
    "-Wdead-code", // Warn when dead code is identified.
    "-Wextra-implicit", // Warn when more than one implicit parameter section is defined.
    "-Wmacros:both", // Lints code before and after applying a macro
    "-Wnumeric-widen", // Warn when numerics are widened.
    "-Woctal-literal", // Warn on obsolete octal syntax.
    "-Wunused:_", // Warn when imports, local and private vals, vars, defs, and types are unused.

    "-Ybackend-parallelism", "8", // Enable paralellisation — change to desired number!
    "-Ycache-plugin-class-loader:last-modified", // Enables caching of classloaders for compiler plugins
    "-Ycache-macro-class-loader:last-modified", // and macro definitions. This can lead to performance improvements.
  )

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
