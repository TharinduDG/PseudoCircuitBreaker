package circuitbreaker

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import circuitbreaker.PseudoCircuitBreaker._
import org.scalacheck._
import org.scalacheck.Arbitrary._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
/**
  * Created by tgalappathth on 10/8/17.
  */
object PseudoCircuitBreakerTestUtils {

  val testException = PseudoCircuitBreakerException("text exception")

  def circuitBreakerStateGenWithHalfOpenTime(halfOpenTimeRange: Seq[Int], pcbStateRange: Seq[PCBState], errorThreshold: Int, timeUnit: TimeUnit = TimeUnit.MILLISECONDS): Arbitrary[CircuitBreakerState] = {

    Arbitrary[CircuitBreakerState] {
      for {
        name <- arbitrary[String]
        halfOpenTime <- Gen.oneOf(halfOpenTimeRange)
        pcbState <- Gen.oneOf(pcbStateRange)
      } yield {
        val config = PseudoCircuitBreakerConfig(errorThreshold, new FiniteDuration(halfOpenTime, timeUnit))
        CircuitBreakerState(name, config, pcbState)
      }
    }
  }

  def simpleCircuitBreakerGen: Arbitrary[CircuitBreakerState] = {
    val ERROR_THRESHOLD = 3

    Arbitrary[CircuitBreakerState] {
      for {
        name <- arbitrary[String]
        errThreshold <- Gen.choose(0, ERROR_THRESHOLD)
        pcbState <- Gen.oneOf((0 to ERROR_THRESHOLD + 1).map(CLOSED) :+ OPEN() :+ HALF_OPEN)
      } yield {
        val config = PseudoCircuitBreakerConfig(errThreshold, new FiniteDuration(5, TimeUnit.MINUTES))
        CircuitBreakerState(name, config, pcbState)
      }
    }
  }

  def syncGlobalCBState(globalCBState: CircuitBreakerState, newCbs: CircuitBreakerState): CircuitBreakerState = {
    // code to sync the updated CircuitBreakerState with the global state
    newCbs
  }

  def mutateGlobalCBStateOfEncapsulatedObject(globalCBState: MockGlobalCBState, newCbs: CircuitBreakerState): CircuitBreakerState = {
    // this could be executed in a synchronized context to preserve the consistency of the `globalCBState`
    globalCBState.setCircuitBreakerState(newCbs)
    newCbs
  }
}


class MockExternalResource {

  val successCounter = new AtomicInteger()
  val failureCounter = new AtomicInteger()

  def readSharedState: Int = {
    successCounter.incrementAndGet()
  }

  def readSharedStateAsync: Future[Int] = {
    Future {
      readSharedState
    }
  }

  def readSharedStateFailure: Int = {
    failureCounter.incrementAndGet()
    throw PseudoCircuitBreakerTestUtils.testException
  }

  def readSharedStateFailureAsync: Future[Int] = {
    Future {
      readSharedStateFailure
    }
  }
}


class MockGlobalCBState {

  var cbState: CircuitBreakerState = CircuitBreakerState("dumbExtResourceCB", PseudoCircuitBreakerConfig(4, new FiniteDuration(1, TimeUnit.MINUTES)), CLOSED())

  def setCircuitBreakerState(cbs: CircuitBreakerState): Unit = {
    cbState = cbs
  }

  def setPCBState(cBState: PCBState): Unit = {
    cbState = cbState.copy(currentState = cBState)
  }

  def setErrorThreshold(threshold: Int): Unit = {
    cbState = cbState.copy(config = cbState.config.copy(errorThreshold = threshold))
  }
}