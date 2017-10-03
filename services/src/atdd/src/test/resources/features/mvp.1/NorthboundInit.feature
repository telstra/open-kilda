@NB
Feature: Initialisation of Northbound tests

  Cucumber framework does not support feature setup/teardown.
  This feature is run before the actual tests based on alphabetical order.

  @MVP1
  Scenario: Creation of Small Linear Network Topology

    This scenario creates small linear network topology and makes sure topology is learned.

    Given a clean flow topology
    And a clean controller
    And a nonrandom linear topology of 5 switches
    And topology contains 8 links
