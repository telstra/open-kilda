@FCRUD
Feature: Deinitialisation of Basic Flow CRUD

  Cucumber framework does not support feature setup/teardown.
  This feature is run after the actual tests based on alphabetical order.

  @MVP1 @SMOKE
  Scenario: Clearing Network Topology

    This scenario clears network topology.

    Given a clean flow topology
    And a clean controller
