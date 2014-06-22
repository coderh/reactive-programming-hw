package nodescala

import scala.language.postfixOps
import scala.util.{ Try, Success, Failure }
import scala.collection._
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.async.Async.{ async, await }
import org.scalatest._
import NodeScala._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.jboss.netty.handler.timeout.TimeoutException

@RunWith(classOf[JUnitRunner])
class NodeScalaSuite extends FunSuite {

  //  test("A Future should always be created") {
  //    val always = Future.always(517)
  //    assert(Await.result(always, 0 nanos) == 517)
  //  }
  //
  //  test("A Future should never be completed") {
  //    val never = Future.never
  //
  //    try {
  //      Await.result(Future.never, 1 second)
  //      assert(false)
  //    } catch {
  //      case t: java.util.concurrent.TimeoutException => // ok
  //    }
  //  }
  //
  //  test("Any") {
  //    val lst = List(Future { Thread.sleep(1000); throw new Exception("this is an exception") }, Future.always(1), Future.always(2))
  //    //    val lst = List(Future.always(1), Future.always(2), Future.always(3))
  //    val any = Future.any(lst)
  //    val res = Await.result(any, 2 second)
  //    assert(res == 1 || res == 2 || res == 3)
  //  }
  //
  //  test("Delay:  A Future should complete after 3s when using a delay of 1s") {
  //    @volatile var test = 0;
  //    val fd = Future.delay(1 seconds)
  //    fd onComplete { case _ => test = 42 }
  //    Thread.sleep(2000)
  //    assert(test === 42)
  //  }
  //
  //  test("now with always") {
  //    //    val fd = Future.always(2)
  //    val fd = Future {
  //      2
  //    }
  //    assert(fd.now == 2)
  //  }
  //
  //  test("now with never") {
  //    val fd = Future.never
  //    try {
  //      val res = fd.now
  //    } catch {
  //      case t: NoSuchElementException => // ok!
  //    }
  //  }
  //
  //  test("continueWith") {
  //    @volatile var se = 2
  //    val f = future(List(1, 2, 3)(2))
  //    val s = f.continueWith(x =>
  //    	x.onComplete {
  //        case Success(x) => se = se + x
  //        case Failure(e) => throw e
  //      })
  //
  //    Thread.sleep(2000)
  //    assert(5 === se)
  //  }
  //
  //  test("continue") {
  //    val f = future { List(1, 2, 3)(1) }
  //    val s = f continue {
  //      case Success(x) => List(1, 2, 3)(1) + 1
  //      case Failure(t) => 0
  //    }
  //    assert(3 == Await.result(s, Duration("100 ms")))
  //  }

  //  test("CancellationTokenSource should allow stopping the computation") {
  //    val cts = CancellationTokenSource()
  //    val ct = cts.cancellationToken
  //    val p = Promise[String]()
  //
  //    async {
  //      while (ct.nonCancelled) {
  //         println("working")
  //      }
  //
  //      p.success("done")
  //    }
  //
  //    cts.unsubscribe()
  //    assert(Await.result(p.future, 1 second) == "done")
  //  }

//  test("Cancellation Test") {
//    val working = Future.run() { ct =>
//      Future {
//        while (ct.nonCancelled) {
//          println("working")
//        }
//        println("done")
//      }
//    }
//    Future.delay(3 seconds) onSuccess {
//      case _ => working.unsubscribe() // if loop still not completed, cancel it
//    }
//    Thread.sleep(4000)
//  }

  class DummyExchange(val request: Request) extends Exchange {
    @volatile var response = ""
    val loaded = Promise[String]()
    def write(s: String) {
      response += s
    }
    def close() {
      loaded.success(response)
    }
  }

  class DummyListener(val port: Int, val relativePath: String) extends NodeScala.Listener {
    self =>

    @volatile private var started = false
    var handler: Exchange => Unit = null

    def createContext(h: Exchange => Unit) = this.synchronized {
      assert(started, "is server started?")
      handler = h
    }

    def removeContext() = this.synchronized {
      assert(started, "is server started?")
      handler = null
    }

    def start() = self.synchronized {
      started = true
      new Subscription {
        def unsubscribe() = self.synchronized {
          started = false
        }
      }
    }

    def emit(req: Request) = {
      val exchange = new DummyExchange(req)
      if (handler != null) handler(exchange)
      exchange
    }
  }

  class DummyServer(val port: Int) extends NodeScala {
    self =>
    val listeners = mutable.Map[String, DummyListener]()

    def createListener(relativePath: String) = {
      val l = new DummyListener(port, relativePath)
      listeners(relativePath) = l
      l
    }

    def emit(relativePath: String, req: Request) = this.synchronized {
      val l = listeners(relativePath)
      l.emit(req)
    }
  }

  test("Listener should serve the next request as a future") {
    val dummy = new DummyListener(8191, "/test")
    val subscription = dummy.start()

    def test(req: Request) {
      val f = dummy.nextRequest()
      dummy.emit(req)
      val (reqReturned, xchg) = Await.result(f, 1 second)

      assert(reqReturned == req)
    }

    test(immutable.Map("StrangeHeader" -> List("StrangeValue1")))
    test(immutable.Map("StrangeHeader" -> List("StrangeValue2")))

    subscription.unsubscribe()
  }

  test("Server should serve requests") {
    val dummy = new DummyServer(8191)
    val dummySubscription = dummy.start("/testDir") {
      request => for (kv <- request.iterator) yield (kv + "\n").toString
    }

    // wait until server is really installed
    Thread.sleep(500)

    def test(req: Request) {
      val webpage = dummy.emit("/testDir", req)
      val content = Await.result(webpage.loaded.future, 1 second)
      val expected = (for (kv <- req.iterator) yield (kv + "\n").toString).mkString
      assert(content == expected, s"'$content' vs. '$expected'")
    }

    test(immutable.Map("StrangeRequest" -> List("Does it work?")))
    test(immutable.Map("StrangeRequest" -> List("It works!")))
    test(immutable.Map("WorksForThree" -> List("Always works. Trust me.")))

    dummySubscription.unsubscribe()
  }

}




