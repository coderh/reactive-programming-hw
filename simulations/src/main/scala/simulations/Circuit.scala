package simulations

import common._

class Wire {
  private var sigVal = false
  private var actions: List[Simulator#Action] = List()

  def getSignal: Boolean = sigVal

  def setSignal(s: Boolean) {
    if (s != sigVal) {
      sigVal = s
      actions.foreach(action => action())
    }
  }

  def addAction(a: Simulator#Action) {
    actions = a :: actions
    a()
  }
}

abstract class CircuitSimulator extends Simulator {

  val InverterDelay: Int
  val AndGateDelay: Int
  val OrGateDelay: Int

  def probe(name: String, wire: Wire) {
    wire addAction {
      () =>
        afterDelay(0) {
          println(
            "  " + currentTime + ": " + name + " -> " + wire.getSignal)
        }
    }
  }

  def inverter(input: Wire, output: Wire) {
    def invertAction() {
      val inputSig = input.getSignal
      afterDelay(InverterDelay) { output.setSignal(!inputSig) }
    }
    input addAction invertAction
  }

  def andGate(a1: Wire, a2: Wire, output: Wire) {
    def andAction() {
      val a1Sig = a1.getSignal
      val a2Sig = a2.getSignal
      afterDelay(AndGateDelay) { output.setSignal(a1Sig & a2Sig) }
    }
    a1 addAction andAction
    a2 addAction andAction
  }

  //
  // to complete with orGates and demux...
  //

  def orGate(a1: Wire, a2: Wire, output: Wire) {
    def orAction() {
      val a1Sig = a1.getSignal
      val a2Sig = a2.getSignal
      afterDelay(OrGateDelay) { output.setSignal(a1Sig | a2Sig) }
    }
    a1 addAction orAction
    a2 addAction orAction
  }

  def orGate2(a1: Wire, a2: Wire, output: Wire) {
    val c, d, e = new Wire
    inverter(a1, c)
    inverter(a2, d)
    andGate(c, d, e)
    inverter(e, output)
  }

  def demux(in: Wire, c: List[Wire], out: List[Wire]): Unit = c match {
    case Nil => orGate(in, new Wire, out.head)
    case x :: xs =>
      val out1, out0 = new Wire
      val (high, low) = out.splitAt(out.length / 2)

      val xinv = new Wire
      inverter(x, xinv)
      andGate(in, xinv, out0)
      andGate(in, x, out1)

      demux(out1, xs, high)
      demux(out0, xs, low)
  }
}

object Circuit extends CircuitSimulator {
  val InverterDelay = 1
  val AndGateDelay = 3
  val OrGateDelay = 5

  def andGateExample {
    val in1, in2, out = new Wire
    andGate(in1, in2, out)
    probe("in1", in1)
    probe("in2", in2)
    probe("out", out)
    in1.setSignal(false)
    in2.setSignal(false)
    run

    in1.setSignal(true)
    run

    in2.setSignal(true)
    run
  }

  //
  // to complete with orGateExample and demuxExample...
  //

  def orGate2Example {
    val in1, in2, out = new Wire
    orGate2(in1, in2, out)
    probe("out", out)
    probe("in1", in1)
    probe("in2", in2)
    in1.setSignal(true)
    run
    in2.setSignal(false)
    run

    in1.setSignal(false)
    run

    in2.setSignal(true)
    run
  }

  def demuxExample {
    val in, c0, c1, out0, out1, out2, out3 = new Wire
    val c = List[Wire](c1, c0)
    val out = List[Wire](out3, out2, out1, out0)
    demux(in, c, out)
    probe("in", in)
    probe("c1", c1)
    probe("c0", c0)
    probe("out3", out3)
    probe("out2", out2)
    probe("out1", out1)
    probe("out0", out0)

    in.setSignal(true)
    c1.setSignal(false)
    c0.setSignal(true)
    run

    in.setSignal(true)
    c1.setSignal(true)
    c0.setSignal(true)
    run

  }
}

object CircuitMain extends App {
  // You can write tests either here, or better in the test class CircuitSuite.
  // Circuit.andGateExample
  Circuit.demuxExample
}
