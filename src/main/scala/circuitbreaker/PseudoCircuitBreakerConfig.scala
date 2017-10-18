package circuitbreaker

import java.util.concurrent.TimeUnit

import com.typesafe.config.{Config, ConfigFactory}
import utils.Settings

import scala.concurrent.duration._
import scala.util.Try

/**
  * Created by tgalappathth on 10/8/17.
  */

sealed case class PseudoCircuitBreakerConfig(errorThreshold: Int, halfOpenDuration: FiniteDuration)

object PseudoCircuitBreakerConfig extends Settings[PseudoCircuitBreakerConfig]("PseudoCircuitBreaker") {

  override def fromSubConfig(c: Config): PseudoCircuitBreakerConfig = {

    val halfOpenTime: FiniteDuration = Try(c.getDuration("halfOpenTime", TimeUnit.MINUTES).minutes).getOrElse(new FiniteDuration(5, TimeUnit.MINUTES))
    val closedStateErrorThreshold = Try(c.getInt("errorThreshold")).getOrElse(10)

    PseudoCircuitBreakerConfig(errorThreshold = closedStateErrorThreshold, halfOpenDuration = halfOpenTime)
  }

  def apply(): PseudoCircuitBreakerConfig = PseudoCircuitBreakerConfig.fromSubConfig(ConfigFactory.load())

}