package kvstore

import akka.actor.Props
import akka.actor.Actor
import akka.actor.ActorRef
import scala.concurrent.duration._

object Replicator {
  case class Replicate(key: String, valueOption: Option[String], id: Long)
  case class Replicated(key: String, id: Long)

  case class Snapshot(key: String, valueOption: Option[String], seq: Long)
  case class SnapshotAck(key: String, seq: Long)

  def props(replica: ActorRef): Props = Props(new Replicator(replica))
}

class Replicator(val replica: ActorRef) extends Actor {
  import Replicator._
  import Replica._
  import context.dispatcher

  /*
   * The contents of this actor is just a suggestion, you can implement it in any way you like.
   */

  // map from sequence number to pair of sender and request
  var acks = Map.empty[Long, (ActorRef, Replicate)]
  // a sequence of not-yet-sent snapshots (you can disregard this if not implementing batching)
  var pending = Vector.empty[Snapshot]

  var _seqCounter = 0L
  def nextSeq = {
    val ret = _seqCounter
    _seqCounter += 1
    ret
  }

  import context.dispatcher
  val tick =
    context.system.scheduler.schedule(100 millis, 100 millis, self, "repeat")

  override def postStop() = tick.cancel()

  /* TODO Behavior for the Replicator. */
  def receive: Receive = {
    case "repeat" =>
      if (!acks.isEmpty) {
        acks foreach {
          case (seq, (_, request)) =>
            replica ! Snapshot(request.key, request.valueOption, seq)
        }
      }

    case replic @ Replicate(key, valueOption, id) =>
      val seq = nextSeq
      acks += seq -> (sender, replic)
      replica ! Snapshot(key, valueOption, seq)

    case SnapshotAck(key, seq) =>
      acks.get(seq) match {
        case Some((sender, replic)) =>
          sender ! Replicated(key, replic.id)
          acks -= seq
        case None =>
      }

  }

}
