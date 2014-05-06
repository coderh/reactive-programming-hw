package kvstore

import akka.actor.{ OneForOneStrategy, Props, ActorRef, Actor }
import kvstore.Arbiter._
import scala.collection.immutable.Queue
import akka.actor.SupervisorStrategy.Restart
import scala.annotation.tailrec
import akka.pattern.{ ask, pipe }
import akka.actor.Terminated
import scala.concurrent.duration._
import akka.actor.PoisonPill
import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy
import akka.util.Timeout

object Replica {
  sealed trait Operation {
    def key: String
    def id: Long
  }
  case class Insert(key: String, value: String, id: Long) extends Operation
  case class Remove(key: String, id: Long) extends Operation
  case class Get(key: String, id: Long) extends Operation

  sealed trait OperationReply
  case class OperationAck(id: Long) extends OperationReply
  case class OperationFailed(id: Long) extends OperationReply
  case class GetResult(key: String, valueOption: Option[String], id: Long) extends OperationReply

  def props(arbiter: ActorRef, persistenceProps: Props): Props = Props(new Replica(arbiter, persistenceProps))
}

class Replica(val arbiter: ActorRef, persistenceProps: Props) extends Actor {
  import Replica._
  import Replicator._
  import Persistence._
  import context.dispatcher

  /*
   * The contents of this actor is just a suggestion, you can implement it in any way you like.
   */

  var expectedSeq = 0L

  var kv = Map.empty[String, String]
  // a map from secondary replicas to replicators
  var secondaries = Map.empty[ActorRef, ActorRef]
  // the current set of replicators
  var replicators = Set.empty[ActorRef]

  def receive = {
    case JoinedPrimary => context.become(leader)
    case JoinedSecondary => context.become(replica)
  }

  // TODO:
  import Arbiter._
  arbiter ! Join
  val persistActor = context.actorOf(persistenceProps)

  override val supervisorStrategy = OneForOneStrategy() {
    case _: PersistenceException => Restart
  }

  import context.dispatcher
  val tick =
    context.system.scheduler.schedule(100 millis, 100 millis, self, "repeat")

  override def postStop() = tick.cancel()

  var persistAcks = Map.empty[Long, (ActorRef, Persist)]
  var cancellables = Map.empty[Long, akka.actor.Cancellable]

  var primaryPersisted: Boolean = false

  /* TODO Behavior for  the leader role. */

  def updating(requester: ActorRef, pcxl: akka.actor.Cancellable, ucxl: akka.actor.Cancellable): Receive = {

    case Persisted(key, id) =>
      primaryPersisted = true
      pcxl.cancel
      self ! id

    case Replicated(key, id) =>
      replicators -= sender
      self ! id

    case id: Long =>
      println
      println("replicators.size = " + replicators.size)
      if (primaryPersisted && replicators.isEmpty) {
        println
        println("Doing right thing")
        requester ! OperationAck(id)
        ucxl.cancel
        context become leader
      }
  }

  val leader: Receive = {

    case Replicas(replicas) =>
      replicas filter (rep => !secondaries.contains(rep) && rep != self) foreach {
        case af: ActorRef =>
          val newReplicator = context.actorOf(Props(new Replicator(af)))
          secondaries += af -> newReplicator
          replicators += newReplicator
      }

    case Insert(key, value, id) =>
      try {
        kv += key -> value
        replicators foreach (_ ! Replicate(key, Some(value), id))
        val persistCXL = context.system.scheduler.schedule(100 millis, 100 millis, persistActor, Persist(key, Some(value), id))
        val updateCXL = context.system.scheduler.scheduleOnce(1 seconds, sender, OperationFailed(id))
        context become updating(sender, persistCXL, updateCXL)
      } catch {
        case e: Exception => sender ! OperationFailed(id)
      }

    case Remove(key, id) =>
      try {
        kv -= key
        replicators foreach (_ ! Replicate(key, None, id))
        val persistCXL = context.system.scheduler.schedule(100 millis, 100 millis, persistActor, Persist(key, None, id))
        val updateCXL = context.system.scheduler.scheduleOnce(1 seconds, sender, OperationFailed(id))
        context become updating(sender, persistCXL, updateCXL)
      } catch {
        case e: Exception => sender ! OperationFailed(id)
      }

    case Get(key, id) => sender ! GetResult(key, kv.get(key), id)

  }

  /* TODO Behavior for the replica role. */
  val replica: Receive = {
    case "repeat" =>
      if (!persistAcks.isEmpty) {
        persistAcks foreach {
          case (seq, (_, persist)) =>
            persistActor ! persist
        }
      }
    case Get(key, id) => sender ! GetResult(key, kv.get(key), id)
    case Snapshot(key, valueOption, seq) =>
      if (seq == expectedSeq) {
        try {
          valueOption match {
            case Some(v) => kv += key -> v
            case None => kv -= key
          }
          implicit val timeout = Timeout(1 seconds)
          val f = persistActor ? Persist(key, valueOption, seq)
          f pipeTo self
          persistAcks += seq -> (sender, Persist(key, valueOption, seq))
          expectedSeq = scala.math.max(seq + 1, expectedSeq)
        } catch {
          case e: Exception => throw e
        }
      } else if (seq < expectedSeq) {
        sender ! SnapshotAck(key, seq)
        expectedSeq = scala.math.max(seq + 1, expectedSeq)
      }
    case Persisted(key, seq) =>
      persistAcks.get(seq) match {
        case Some((replicator, persist)) =>
          replicator ! SnapshotAck(key, seq)
          persistAcks -= seq
        case None =>
      }
    case _: akka.actor.Status.Failure => println("Timeout \n\n\n")

  }

}
