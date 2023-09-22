package chipmunk

import svsim.Simulation._

package object tester {
  implicit class AddMethodsToPort(p: Port) {
    def #=(value: BigInt): Unit = p.set(value)
  }

  implicit class AddMethodsToValue(v: Value) {
    def asBool = v.asBigInt != 0
  }
}