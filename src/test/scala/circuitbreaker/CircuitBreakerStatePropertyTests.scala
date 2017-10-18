package circuitbreaker

import java.util.concurrent.TimeUnit

import circuitbreaker.PseudoCircuitBreaker._
import circuitbreaker.PseudoCircuitBreakerTestUtils._
import org.scalacheck.Arbitrary._
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.prop.PropertyChecks
import org.scalatest.{Matchers, PropSpec}

import scala.compat.Platform
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, Future}

/**
  * Created by tgalappathth on 10/9/17.
  */
class CircuitBreakerStatePropertyTests extends PropSpec with PropertyChecks with Matchers {

  val circuitBreaker = new PseudoCircuitBreaker[Int] {}

  // circuitBreakerState with success/failure functions
  property("when `circuitBreakerState` is run with an always successful function") {

    val resource = new MockExternalResource
    var previousSuccessCount = resource.successCounter.get()

    implicit val circuitBreakerState = simpleCircuitBreakerGen

    forAll { (cbs: CircuitBreakerState) =>
      val (newCBS, result) = circuitBreaker.circuitBreakerState(resource.readSharedState, cbs).run(cbs).value

      newCBS.name should be(cbs.name)
      newCBS.config should be(cbs.config)

      cbs.currentState match {
        case CLOSED(_) =>
          newCBS.currentState.getClass should be(CLOSED().getClass)

        case OPEN(_) =>
          newCBS.currentState.getClass should be(CLOSED().getClass)

        case HALF_OPEN =>
          newCBS.currentState.getClass should be(CLOSED().getClass)

        case s =>
          fail(s" $s - unintended state!")
      }

      result.isRight should be(true)
      result.right.get should be(resource.successCounter.get())
      result.right.get should be(previousSuccessCount + 1)
      previousSuccessCount = result.right.get
      resource.failureCounter.get() should be(0)
    }

  }

  property("when `circuitBreakerState` is run with an always failure function") {

    val ERROR_THRESHOLD = 3
    val resource = new MockExternalResource
    var previousFailureCount = resource.failureCounter.get()

    implicit val circuitBreakerState = Arbitrary[CircuitBreakerState] {
      for {
        name <- arbitrary[String]
        pcbState <- Gen.oneOf((0 to ERROR_THRESHOLD + 1).map(CLOSED) :+ OPEN() :+ HALF_OPEN)
      } yield {
        val config = PseudoCircuitBreakerConfig(ERROR_THRESHOLD, new FiniteDuration(5, TimeUnit.MINUTES))
        CircuitBreakerState(name, config, pcbState)
      }
    }

    forAll { (cbs: CircuitBreakerState) =>
      val (newCBS, result) = circuitBreaker.circuitBreakerState(resource.readSharedStateFailure, cbs).run(cbs).value

      newCBS.name should be(cbs.name)
      newCBS.config should be(cbs.config)

      cbs.currentState match {
        case CLOSED(errorCount) if errorCount < ERROR_THRESHOLD =>
          newCBS.currentState should be(CLOSED(errorCount + 1))

        case CLOSED(errorCount) if errorCount >= ERROR_THRESHOLD =>
          newCBS.currentState.getClass should be(OPEN().getClass)

        case OPEN(_) =>
          newCBS.currentState.getClass should be(OPEN().getClass)

        case HALF_OPEN =>
          newCBS.currentState.getClass should be(OPEN().getClass)

        case s =>
          fail(s" $s - unintended state!")
      }

      result.isLeft should be(true)
      result.left.get should be(PseudoCircuitBreakerTestUtils.testException)
      resource.failureCounter.get() should be(previousFailureCount + 1)
      previousFailureCount = resource.failureCounter.get()
    }

  }

  // circuitBreakerStateAsync with success/failure functions
  property("when `circuitBreakerStateAsync` is run with an always successful function") {

    val resource = new MockExternalResource
    var previousSuccessCount = resource.successCounter.get()

    implicit val circuitBreakerState = simpleCircuitBreakerGen

    forAll { (cbs: CircuitBreakerState) =>
      val (newCBSF, resultF) = circuitBreaker.circuitBreakerStateAsync(resource.readSharedStateAsync, cbs).run(Future.successful(cbs)).value

      val newCBS = Await.result(newCBSF, new FiniteDuration(5, TimeUnit.SECONDS))
      val result = Await.result(resultF, new FiniteDuration(5, TimeUnit.SECONDS))

      newCBS.name should be(cbs.name)
      newCBS.config should be(cbs.config)

      cbs.currentState match {
        case CLOSED(_) =>
          newCBS.currentState.getClass should be(CLOSED().getClass)

        case OPEN(_) =>
          newCBS.currentState.getClass should be(CLOSED().getClass)

        case HALF_OPEN =>
          newCBS.currentState.getClass should be(CLOSED().getClass)

        case s =>
          fail(s" $s - unintended state!")
      }

      result.isRight should be(true)
      result.right.get should be(resource.successCounter.get())
      result.right.get should be(previousSuccessCount + 1)
      previousSuccessCount = result.right.get
      resource.failureCounter.get() should be(0)
    }

  }

  property("when `circuitBreakerStateAsync` is run with an always failure function") {

    val ERROR_THRESHOLD = 3
    val resource = new MockExternalResource
    var previousFailureCount = resource.failureCounter.get()

    implicit val circuitBreakerState = Arbitrary[CircuitBreakerState] {
      for {
        name <- arbitrary[String]
        pcbState <- Gen.oneOf((0 to ERROR_THRESHOLD + 1).map(CLOSED) :+ OPEN() :+ HALF_OPEN)
      } yield {
        val config = PseudoCircuitBreakerConfig(ERROR_THRESHOLD, new FiniteDuration(5, TimeUnit.MINUTES))
        CircuitBreakerState(name, config, pcbState)
      }
    }

    forAll { (cbs: CircuitBreakerState) =>
      val (newCBSF, resultF) = circuitBreaker.circuitBreakerStateAsync(resource.readSharedStateFailureAsync, cbs).run(Future.successful(cbs)).value

      val newCBS = Await.result(newCBSF, new FiniteDuration(5, TimeUnit.SECONDS))
      val result = Await.result(resultF, new FiniteDuration(5, TimeUnit.SECONDS))

      newCBS.name should be(cbs.name)
      newCBS.config should be(cbs.config)

      cbs.currentState match {
        case CLOSED(errorCount) if errorCount < ERROR_THRESHOLD =>
          newCBS.currentState should be(CLOSED(errorCount + 1))

        case CLOSED(errorCount) if errorCount >= ERROR_THRESHOLD =>
          newCBS.currentState.getClass should be(OPEN().getClass)

        case OPEN(_) =>
          newCBS.currentState.getClass should be(OPEN().getClass)

        case HALF_OPEN =>
          newCBS.currentState.getClass should be(OPEN().getClass)

        case s =>
          fail(s" $s - unintended state!")
      }

      result.isLeft should be(true)
      result.left.get should be(PseudoCircuitBreakerTestUtils.testException)
      resource.failureCounter.get() should be(previousFailureCount + 1)
      previousFailureCount = resource.failureCounter.get()
    }
  }

  // circuitBreakerState with halfOpenDuration
  property("when `circuitBreakerState` is run with an always failure function within `halfOpenDuration`") {

    val ERROR_THRESHOLD = 3
    val resource = new MockExternalResource

    implicit val circuitBreakerState = circuitBreakerStateGenWithHalfOpenTime(100 to 200, List.fill[OPEN](10)(OPEN(Platform.currentTime - 200)), ERROR_THRESHOLD)

    forAll { (cbs: CircuitBreakerState) =>

      val (newCBS, result) = circuitBreaker.circuitBreakerState(resource.readSharedStateFailure, cbs).run(cbs).value

      newCBS.name should be(cbs.name)
      newCBS.config should be(cbs.config)

      cbs.currentState match {
        case OPEN(_) =>
          newCBS.currentState.getClass should be(OPEN().getClass)

        case s =>
          fail(s" $s - unintended state!")
      }

      result.isLeft should be(true)
      result.left.get.isInstanceOf[PseudoCircuitBreakerException] should be(true)
      resource.failureCounter.get() should be(0)
    }
  }

  property("when `circuitBreakerState` is run with an always failure function after `halfOpenDuration`") {

    val ERROR_THRESHOLD = 3
    val resource = new MockExternalResource
    var previousFailureCount = resource.failureCounter.get()

    implicit val circuitBreakerState = circuitBreakerStateGenWithHalfOpenTime(100 to 200, List.fill[OPEN](10)(OPEN(Platform.currentTime + 200)), ERROR_THRESHOLD)

    forAll { (cbs: CircuitBreakerState) =>

      val (newCBS, result) = circuitBreaker.circuitBreakerState(resource.readSharedStateFailure, cbs).run(cbs).value

      newCBS.name should be(cbs.name)
      newCBS.config should be(cbs.config)

      cbs.currentState match {
        case OPEN(_) =>
          newCBS.currentState.getClass should be(OPEN().getClass)

        case s =>
          fail(s" $s - unintended state!")
      }

      result.isLeft should be(true)
      result.left.get.isInstanceOf[PseudoCircuitBreakerException] should be(true)
      resource.failureCounter.get() should be(previousFailureCount + 1)
      previousFailureCount = resource.failureCounter.get()
    }
  }

  property("when `circuitBreakerState` is run with an always success function and after `halfOpenDuration`") {

    val ERROR_THRESHOLD = 3
    val resource = new MockExternalResource
    var previousSuccessCount = resource.successCounter.get()

    implicit val circuitBreakerState = circuitBreakerStateGenWithHalfOpenTime(100 to 200, List.fill[OPEN](10)(OPEN(Platform.currentTime + 200)), ERROR_THRESHOLD)

    forAll { (cbs: CircuitBreakerState) =>

      val (newCBS, result) = circuitBreaker.circuitBreakerState(resource.readSharedState, cbs).run(cbs).value

      newCBS.name should be(cbs.name)
      newCBS.config should be(cbs.config)

      cbs.currentState match {
        case OPEN(_) =>
          newCBS.currentState.getClass should be(CLOSED().getClass)

        case s =>
          fail(s" $s - unintended state!")
      }

      result.isRight should be(true)
      result.right.get should be(resource.successCounter.get())
      resource.successCounter.get() should be(previousSuccessCount + 1)
      previousSuccessCount = resource.successCounter.get()
    }
  }

  property("when `circuitBreakerState` is run with an always success function and within `halfOpenDuration`") {

    val ERROR_THRESHOLD = 3
    val resource = new MockExternalResource
    var previousFailureCount = resource.failureCounter.get()

    implicit val circuitBreakerState = circuitBreakerStateGenWithHalfOpenTime(100 to 200, List.fill[OPEN](10)(OPEN(Platform.currentTime - 200)), ERROR_THRESHOLD)

    forAll { (cbs: CircuitBreakerState) =>

      val (newCBS, result) = circuitBreaker.circuitBreakerState(resource.readSharedState, cbs).run(cbs).value

      newCBS.name should be(cbs.name)
      newCBS.config should be(cbs.config)

      cbs.currentState match {
        case OPEN(_) =>
          newCBS.currentState.getClass should be(OPEN().getClass)

        case s =>
          fail(s" $s - unintended state!")
      }

      result.isLeft should be(true)
      result.left.get.isInstanceOf[PseudoCircuitBreakerException] should be(true)
      resource.failureCounter.get() should be(0)
      previousFailureCount = resource.failureCounter.get()
    }
  }

  // circuitBreakerStateAsync with halfOpenDuration
  property("when `circuitBreakerStateAsync` is run with an always failure function within `halfOpenDuration`") {

    val ERROR_THRESHOLD = 3
    val resource = new MockExternalResource

    implicit val circuitBreakerState = circuitBreakerStateGenWithHalfOpenTime(100 to 200, List.fill[OPEN](10)(OPEN(Platform.currentTime - 200)), ERROR_THRESHOLD)

    forAll { (cbs: CircuitBreakerState) =>

      val (newCBSF, resultF) = circuitBreaker.circuitBreakerStateAsync(resource.readSharedStateFailureAsync, cbs).run(Future.successful(cbs)).value

      val newCBS = Await.result(newCBSF, new FiniteDuration(5, TimeUnit.SECONDS))
      val result = Await.result(resultF, new FiniteDuration(5, TimeUnit.SECONDS))

      newCBS.name should be(cbs.name)
      newCBS.config should be(cbs.config)

      cbs.currentState match {
        case OPEN(_) =>
          newCBS.currentState.getClass should be(OPEN().getClass)

        case s =>
          fail(s" $s - unintended state!")
      }

      result.isLeft should be(true)
      result.left.get.isInstanceOf[PseudoCircuitBreakerException] should be(true)
      resource.failureCounter.get() should be(0)
    }
  }

  property("when `circuitBreakerStateAsync` is run with an always failure function after `halfOpenDuration`") {

    val ERROR_THRESHOLD = 3
    val resource = new MockExternalResource
    var previousFailureCount = resource.failureCounter.get()

    implicit val circuitBreakerState = circuitBreakerStateGenWithHalfOpenTime(100 to 200, List.fill[OPEN](10)(OPEN(Platform.currentTime + 200)), ERROR_THRESHOLD)

    forAll { (cbs: CircuitBreakerState) =>

      val (newCBSF, resultF) = circuitBreaker.circuitBreakerStateAsync(resource.readSharedStateFailureAsync, cbs).run(Future.successful(cbs)).value

      val newCBS = Await.result(newCBSF, new FiniteDuration(5, TimeUnit.SECONDS))
      val result = Await.result(resultF, new FiniteDuration(5, TimeUnit.SECONDS))

      newCBS.name should be(cbs.name)
      newCBS.config should be(cbs.config)

      cbs.currentState match {
        case OPEN(_) =>
          newCBS.currentState.getClass should be(OPEN().getClass)

        case s =>
          fail(s" $s - unintended state!")
      }

      result.isLeft should be(true)
      result.left.get.isInstanceOf[PseudoCircuitBreakerException] should be(true)
      resource.failureCounter.get() should be(previousFailureCount + 1)
      previousFailureCount = resource.failureCounter.get()
    }
  }

  property("when `circuitBreakerStateAsync` is run with an always success function and after `halfOpenDuration`") {

    val ERROR_THRESHOLD = 3
    val resource = new MockExternalResource
    var previousSuccessCount = resource.successCounter.get()

    implicit val circuitBreakerState = circuitBreakerStateGenWithHalfOpenTime(100 to 200, List.fill[OPEN](10)(OPEN(Platform.currentTime + 200)), ERROR_THRESHOLD)

    forAll { (cbs: CircuitBreakerState) =>

      val (newCBSF, resultF) = circuitBreaker.circuitBreakerStateAsync(resource.readSharedStateAsync, cbs).run(Future.successful(cbs)).value

      val newCBS = Await.result(newCBSF, new FiniteDuration(5, TimeUnit.SECONDS))
      val result = Await.result(resultF, new FiniteDuration(5, TimeUnit.SECONDS))

      newCBS.name should be(cbs.name)
      newCBS.config should be(cbs.config)

      cbs.currentState match {
        case OPEN(_) =>
          newCBS.currentState.getClass should be(CLOSED().getClass)

        case s =>
          fail(s" $s - unintended state!")
      }

      result.isRight should be(true)
      result.right.get should be(resource.successCounter.get())
      resource.successCounter.get() should be(previousSuccessCount + 1)
      previousSuccessCount = resource.successCounter.get()
    }
  }

  property("when `circuitBreakerStateAsync` is run with an always success function and within `halfOpenDuration`") {

    val ERROR_THRESHOLD = 3
    val resource = new MockExternalResource
    var previousFailureCount = resource.failureCounter.get()

    implicit val circuitBreakerState = circuitBreakerStateGenWithHalfOpenTime(100 to 200, List.fill[OPEN](10)(OPEN(Platform.currentTime - 200)), ERROR_THRESHOLD)

    forAll { (cbs: CircuitBreakerState) =>

      val (newCBSF, resultF) = circuitBreaker.circuitBreakerStateAsync(resource.readSharedStateAsync, cbs).run(Future.successful(cbs)).value

      val newCBS = Await.result(newCBSF, new FiniteDuration(5, TimeUnit.SECONDS))
      val result = Await.result(resultF, new FiniteDuration(5, TimeUnit.SECONDS))

      newCBS.name should be(cbs.name)
      newCBS.config should be(cbs.config)

      cbs.currentState match {
        case OPEN(_) =>
          newCBS.currentState.getClass should be(OPEN().getClass)

        case s =>
          fail(s" $s - unintended state!")
      }

      result.isLeft should be(true)
      result.left.get.isInstanceOf[PseudoCircuitBreakerException] should be(true)
      resource.failureCounter.get() should be(0)
      previousFailureCount = resource.failureCounter.get()
    }
  }
}
