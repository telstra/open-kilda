@FCRUD
Feature: Initialisation of Basic Flow CRUD

  Cucumber framework does not support feature setup/teardown.
  This feature is run before the actual tests based on alphabetical order.

  # TODO: This should be replaced by lazy creation of the topology within tests
  # An example of what can be done:
  #     Given a standard 5 switch 8 link topology
  #     And a clean flow topology
  #
  # The first line should check the current topology; if it doesn't match, tear down and replace.

  @MVP1 @SMOKE @INIT
  Scenario: Creation of Small Linear Network Topology

    This scenario creates small linear network topology and makes sure topology is learned.

    Given a clean controller
    And a nonrandom linear topology of 5 switches
    And topology contains 8 links
    And a clean flow topology
