package circuitbreaker

/**
  * Created by tgalappathth on 10/8/17.
  */
case class PseudoCircuitBreakerException(message: String) extends RuntimeException(message: String)
