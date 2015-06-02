package quickcheck

import common._

import org.scalacheck._
import Arbitrary._
import Gen._
import Prop._

abstract class QuickCheckHeap extends Properties("Heap") with IntHeap {

  property("min1") = forAll { a: Int =>
    val h = insert(a, empty)
    findMin(h) == a
  }

  property("min2") = forAll { (a: Int, b: Int) =>
    val h = insert(b, insert(a, empty))
    findMin(h) == List(a, b).min
  }

  property("min3") = forAll { a: Int =>
    deleteMin(insert(a, empty)) == empty
  }

  lazy val genHeap: Gen[H] = for {
    v <- arbitrary[Int]
    h <- oneOf(genHeap, const(empty))
  } yield insert(v, h)

  property("gen1") = forAll { (h: H) =>
    val m = if (isEmpty(h)) 0 else findMin(h)
    findMin(insert(m, h)) == m

  }

  def sortHeap(h: H): List[A] = h match {
    case h if isEmpty(h) => Nil
    case h => findMin(h) :: sortHeap(deleteMin(h))
  }

  // Given any heap,
  // you should get a sorted sequence of elements when continually finding and deleting minima.
  property("gen2") = forAll { (h: H) =>
    val out = sortHeap(h)
    out.sorted == out
  }

  // Finding a minimum of the melding of any two heaps should return a minimum of one or the other.
  property("gen3") = forAll { (h1: H, h2: H) =>
    val min1 = findMin(meld(h1, h2))
    val min2 = List(findMin(h1), findMin(h2)).min
    min1 == min2
  }

  property("gen4") = forAll { (h1: H, h2: H) =>
    (sortHeap(h1) ::: sortHeap(h2)).sorted == sortHeap(meld(h1, h2))
  }

  implicit lazy val arbHeap: Arbitrary[H] = Arbitrary(genHeap)

}
