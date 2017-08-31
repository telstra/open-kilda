Feature: Initialisation of Basic Flow CRUD

  Cucumber framework does not support feature setup/teardown.
  This feature is run before the actual tests based on alphabetical order.

  @MVP1
  Scenario: Creation of Small Linear Network Topology

    This scenario creates small linear network topology and makes sure topology is learned.

    Given a clean controller
    And a nonrandom linear topology of 5 switches
