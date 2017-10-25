# Pseudo Circuit Breaker
Simple implementation of a Circuit Breaker

You can wrap a function(`fn`) call in a `PseudoCircuitBreaker` object, and it will be triggered in case of failures of `fn`.
Triggering will happen once the failures reach a certain threshold, and all further calls to the circuit breaker return with an error, without the protected call being made at all. 

## State Diagram

![State transition](https://martinfowler.com/bliki/images/circuitBreaker/state.png "State Transition")

Reference [https://martinfowler.com/bliki/CircuitBreaker.html]
