# Network discovery (BFD management)

## Assumption
* "system" can produce several "BFD-enable" request, but all of them must
  refer same ISL(sw+ports pair). To replace/reconfigure remote BFD
  endpoint - "system" must send BFD-disable (for old/existing configuration)
  and BFD-enable (for new/desire configuration).

## Particular qualities
* BFD logical port manager/controller must trace logical port status (UP/DOWN).
  This tracking are done "outside" FSM, because if it done inside FSM become
  overcomplicated. So external component trace/keep actual port state and proxy
  update notifications into FSM. 
