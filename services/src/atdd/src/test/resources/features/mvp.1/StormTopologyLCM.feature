@STORM_LCM
Feature: Storm topology life cycle

  For update code in Storm, we need to kill and load the new topology, and bolt loses all
  internal state. For restore data in bolt we send a messages to FL and TE and wait till callback
  message with network data arrive. We don't process common messages and mark it as fail before
  that. After all internal state of bolts will be restored we check all state via our Control
  protocol. The test will send a request and get a response from the internal state of all
  stateful bolts then check them.

  @MVP1
  Scenario: Kill and load Storm topology

    This scenario checks that we can kill and load storm topologies without failures.

    Given active simple network topology with two switches and flow
    When storm topologies are restarted
    Then network topology in the same state
    And all storm topologies in the same state
    And traffic flows through flow
