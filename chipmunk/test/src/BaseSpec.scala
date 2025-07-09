package chipmunk.test

import chipmunk.tester.TesterAPI
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.Assertions
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

abstract class ChipmunkFlatSpec extends AnyFlatSpec with ChiselSim with TesterAPI with Assertions with Matchers

abstract class ChipmunkFreeSpec extends AnyFreeSpec with ChiselSim with TesterAPI with Assertions with Matchers

abstract class ChipmunkFunSpec extends AnyFunSpec with ChiselSim with TesterAPI with Assertions with Matchers
