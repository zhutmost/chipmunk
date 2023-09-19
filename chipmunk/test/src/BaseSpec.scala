package chipmunk.test

import org.scalatest.Assertions
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

abstract class ChipmunkFlatSpec extends AnyFlatSpec with Assertions with Matchers

abstract class ChipmunkFreeSpec extends AnyFreeSpec with Assertions with Matchers

abstract class ChipmunkFunSpec extends AnyFunSpec with Assertions with Matchers
