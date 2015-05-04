package calculator

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{FunSuite, _}

import TweetLength.MaxTweetLength

@RunWith(classOf[JUnitRunner])
class CalculatorSuite extends FunSuite with ShouldMatchers {

  /** ****************
    * * TWEET LENGTH **
    * *****************/

  def tweetLength(text: String): Int =
    text.codePointCount(0, text.length)

  test("tweetRemainingCharsCount with a constant signal") {
    val result = TweetLength.tweetRemainingCharsCount(Var("hello world"))
    assert(result() == MaxTweetLength - tweetLength("hello world"))

    val tooLong = "foo" * 200
    val result2 = TweetLength.tweetRemainingCharsCount(Var(tooLong))
    assert(result2() == MaxTweetLength - tweetLength(tooLong))
  }

  test("tweetRemainingCharsCount with a supplementary char") {
    val result = TweetLength.tweetRemainingCharsCount(Var("foo blabla \uD83D\uDCA9 bar"))
    assert(result() == MaxTweetLength - tweetLength("foo blabla \uD83D\uDCA9 bar"))
  }

  test("colorForRemainingCharsCount with a constant signal") {
    val resultGreen1 = TweetLength.colorForRemainingCharsCount(Var(52))
    assert(resultGreen1() == "green")
    val resultGreen2 = TweetLength.colorForRemainingCharsCount(Var(15))
    assert(resultGreen2() == "green")

    val resultOrange1 = TweetLength.colorForRemainingCharsCount(Var(12))
    assert(resultOrange1() == "orange")
    val resultOrange2 = TweetLength.colorForRemainingCharsCount(Var(0))
    assert(resultOrange2() == "orange")

    val resultRed1 = TweetLength.colorForRemainingCharsCount(Var(-1))
    assert(resultRed1() == "red")
    val resultRed2 = TweetLength.colorForRemainingCharsCount(Var(-5))
    assert(resultRed2() == "red")
  }

  test("calculator grader test case") {
    val m1 = Map(
      "a" -> Signal[Expr](Plus(Ref("a"), Literal(1.0))),
      "d" -> Signal[Expr](Minus(Literal(5.0), Literal(3.0)))
    )

    val m2 = Map(
      "a" -> Signal[Expr](Plus(Ref("b"), Literal(1.0))),
      "b" -> Signal[Expr](Divide(Ref("c"), Ref("d"))),
      "c" -> Signal[Expr](Times(Literal(5.0), Ref("a"))),
      "d" -> Signal[Expr](Minus(Literal(5.0), Literal(3.0)))
    )

    val m3 = Map(
      "a" -> Signal[Expr](Plus(Ref("b"), Literal(1.0))),
      "d" -> Signal[Expr](Minus(Literal(5.0), Literal(3.0)))
    )

    val m4 = Map(
      "a" -> Signal[Expr](Plus(Ref("b"), Literal(1.0))),
      "b" -> Signal[Expr](Times(Ref("c"), Ref("d"))),
      "c" -> Signal[Expr](Plus(Literal(5.0), Ref("d"))),
      "d" -> Signal[Expr](Minus(Literal(4.0), Literal(3.0)))
    )

    val m5 = Map(
      "a" -> Signal[Expr](Plus(Ref("b"), Literal(1.0))),
      "b" -> Signal[Expr](Times(Ref("c"), Ref("d"))),
      "c" -> Signal[Expr](Plus(Literal(4.0), Literal(3.0))),
      "d" -> Signal[Expr](Minus(Literal(4.0), Literal(3.0)))
    )

    val m6 = Map(
      "a" -> Signal[Expr](Minus(Literal(4.0), Literal(3.0))),
      "b" -> Signal[Expr](Plus(Ref("a"), Literal(1.0)))
    )

    val m7 = Map(
      "a" -> Signal[Expr](Minus(Literal(4.0), Literal(3.0))),
      "b" -> Signal[Expr](Literal(1.0))
    )

    val m8 = Map(
      "a" -> Signal[Expr](Literal(5.0)),
      "b" -> Signal[Expr](Literal(1.0))
    )

    val m9 = Map(
      "a" -> Signal[Expr](Plus(Ref("b"), Literal(1.0))),
      "b" -> Signal[Expr](Times(Literal(5.0), Ref("d"))),
      "c" -> Signal[Expr](Plus(Literal(5.0), Ref("d"))),
      "d" -> Signal[Expr](Minus(Literal(4.0), Literal(3.0)))
    )

    val m10 = Map(
      "a" -> Signal[Expr](Plus(Ref("b"), Literal(1.0))),
      "b" -> Signal[Expr](Times(Literal(5.0), Ref("d"))),
      "c" -> Signal[Expr](Plus(Literal(4.0), Literal(3.0))),
      "d" -> Signal[Expr](Minus(Literal(4.0), Literal(3.0)))
    )

    val cal = Calculator.computeValues(m4)
    cal.map(p => (p._1, p._2())) foreach println
  }

}