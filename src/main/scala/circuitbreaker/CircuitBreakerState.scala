package circuitbreaker

import circuitbreaker.PseudoCircuitBreaker.PCBState

/**
  * Created by tgalappathth on 10/8/17.
  */
case class CircuitBreakerState(name: String, config: PseudoCircuitBreakerConfig, currentState: PCBState)

