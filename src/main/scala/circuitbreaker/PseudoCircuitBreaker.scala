package circuitbreaker

import cats.data.State
import circuitbreaker.PseudoCircuitBreaker._

import scala.annotation.tailrec
import scala.compat.Platform
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/**
  * Created by Tharindu Galappaththi on 10/8/17.
  */

/**
  * Simple implementation of the CircuitBreaker idea introduced in: https://martinfowler.com/bliki/CircuitBreaker.html
  * The state transition is represented in this image: https://martinfowler.com/bliki/images/circuitBreaker/state.png
  *
  * @tparam A Return type of the function upon which the `PseudoCircuitBreaker` is triggered
  */
trait PseudoCircuitBreaker[A] {

  /** Execute `fn` and Circuit Break based on the result of `fn`
    *
    * @param fn function to circuit break
    * @param state `CircuitBreakerState` based on the previous executions for this circuit breaker. This is the globally stored circuit breaker state
    * @param syncCBState Side effecting function that mutate & sync the global `CircuitBreakerState` with the new `CircuitBreakerState`. This side effect
    *                    could be executed in a synchronized context to preserve the consistency of global `CircuitBreakerState`
    * @return `Tuple2` of new `CircuitBreakerState` and the result of the execution of `fn`
    */
  def triggerFor(fn: => A, state: CircuitBreakerState)(syncCBState: CircuitBreakerState => CircuitBreakerState): (CircuitBreakerState, Either[Throwable, A]) = {

    val cbStateM = circuitBreakerState(fn, state).modify(cbs => {
      syncCBState(cbs)
    })

    cbStateM.run(state).value
  }


  /** Execute `fn` and Circuit Break based on the result of `fn`.
    *
    * @param fn function to circuit break
    * @param state `CircuitBreakerState` based on the previous executions for this circuit breaker. This is the globally stored circuit breaker state
    * @param syncCBState Side effecting function that mutate & sync the global `CircuitBreakerState` with the new `CircuitBreakerState`. This side effect
    *                    could be executed in a synchronized context to preserve the consistency of global `CircuitBreakerState`
    * @return `Tuple2` of new `CircuitBreakerState` and the result of the execution of `fn`
    */
  def triggerForAsync(fn: => Future[A], state: CircuitBreakerState)(syncCBState: CircuitBreakerState => CircuitBreakerState)
                     (implicit executionContext: ExecutionContext): (Future[CircuitBreakerState], Future[Either[Throwable, A]]) = {

    val cbStateM = circuitBreakerStateAsync(fn, state).modify(cbs => {
      cbs.map(syncCBState)
    })

    cbStateM.run(Future.successful(state)).value
  }

  /** Creates the State monad for circuit breaker that takes a `fn` and returns `A`
    *
    * @param fn function to circuit break
    * @param state `CircuitBreakerState` based on the previous executions for this circuit breaker. This is the globally stored circuit breaker state
    * @return `cats.data.State` monad
    */
  def circuitBreakerState(fn: => A, state: CircuitBreakerState): State[CircuitBreakerState, Either[Throwable, A]] = {
    val cbStateM = State[CircuitBreakerState, Either[Throwable, A]] {
      cbs => updateCBState(fn, cbs)
    }

    cbStateM
  }

  /** Creates the State monad for circuit breaker that takes a `fn` and return `Future[A]`
    *
    * @param fn function to circuit break
    * @param state `CircuitBreakerState` based on the previous executions for this circuit breaker. This is the globally stored circuit breaker state
    * @return `cats.data.State` monad
    */
  def circuitBreakerStateAsync(fn: => Future[A], state: CircuitBreakerState)
                              (implicit executionContext: ExecutionContext): State[Future[CircuitBreakerState], Future[Either[Throwable, A]]] = {

    val cbStateM = State[Future[CircuitBreakerState], Future[Either[Throwable, A]]] {
      cbs => {
        val updatedCBS = updateCBStateAsync(fn, cbs)
        val cBState = updatedCBS.map(_._1)
        val result = updatedCBS.map(_._2)

        (cBState, result)
      }
    }

    cbStateM
  }

  @tailrec
  private def updateCBState(fn: => A, state: CircuitBreakerState): (CircuitBreakerState, Either[Throwable, A]) = {

    state.currentState match {
      case CLOSED(err) =>
        val result = Try(fn) match {
          case Success(value) =>
            (state.copy(currentState = CLOSED()), Right(value))

          case Failure(ex) =>
            if (err < state.config.errorThreshold)
              (state.copy(currentState = CLOSED(err + 1)), Left(ex))
            else
              (state.copy(currentState = OPEN()), Left(ex))
        }

        result

      case HALF_OPEN =>
        val result = Try(fn) match {
          case Success(value) =>
            (state.copy(currentState = CLOSED()), Right(value))

          case Failure(ex) =>
            (state.copy(currentState = OPEN()), Left(ex))
        }

        result

      case OPEN(timestamp) =>
        if ((timestamp + state.config.halfOpenDuration.toMillis) <= Platform.currentTime)
          (state, Left(PseudoCircuitBreakerException(s"Circuit breaker is in open state. Current `CircuitBreakerState`: $state")))

        else
          updateCBState(fn, state.copy(currentState = HALF_OPEN))
    }
  }

  private def updateCBStateAsync(fn: => Future[A], state: Future[CircuitBreakerState])
                                (implicit executionContext: ExecutionContext): Future[(CircuitBreakerState, Either[Throwable, A])] = {
    state.flatMap(s => {
      s.currentState match {
        case CLOSED(err) =>
          val triggeredFn = fn

          val result: Future[(CircuitBreakerState, Either[Throwable, A])] = triggeredFn.map(value => {
            (s.copy(currentState = CLOSED()), Right(value))
          }).recoverWith({
            case NonFatal(ex) =>
              if (err < s.config.errorThreshold)
                Future.successful((s.copy(currentState = CLOSED(err + 1)), Left(ex)))
              else
                Future.successful((s.copy(currentState = OPEN()), Left(ex)))
          })

          result

        case HALF_OPEN =>
          val triggeredFn = fn

          val result = triggeredFn.map(value => {
            (s.copy(currentState = CLOSED()), Right(value))
          }).recoverWith({
            case NonFatal(ex) =>
              Future.successful((s.copy(currentState = OPEN()), Left(ex)))
          })

          result

        case OPEN(timestamp) =>
          if (timestamp + s.config.halfOpenDuration.toMillis < Platform.currentTime)
            Future.successful((s, Left(PseudoCircuitBreakerException("circuit breaker is in open state"))))
          else
            updateCBStateAsync(fn, Future.successful(s.copy(currentState = HALF_OPEN)))
      }
    })
  }
}

object PseudoCircuitBreaker {

  /**
    * Base for `CircuitBreakerState`
    */
  sealed trait PCBState

  /**
    * Represents the open state( _ \_ ) of the circuit breaker
    * @param timestamp time at which it bacame open
    */
  case class OPEN(timestamp: Long = Platform.currentTime) extends PCBState

  /**
    * Represents the closed state( __ ) of the circuit breaker
    * @param errorCount current error count after transitioning to closed stated
    */
  case class CLOSED(errorCount: Int = 0) extends PCBState

  /**
    * Represents the half open state( _-_ ) of the circuit breaker.
    * This state is reached before transitioning from open to closed state.
    */
  case object HALF_OPEN extends PCBState
}
