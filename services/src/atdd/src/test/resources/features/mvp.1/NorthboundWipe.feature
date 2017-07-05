Feature: Deinitialisation of Northbound tests

  Cucumber framework does not support feature setup/teardown.
  This feature is run after the actual tests based on alphabetical order.

  @MVP1
  Scenario: Clearing Network Topology

    This scenario clears network topology.

    Given a clean flow topology
    And a clean controller
