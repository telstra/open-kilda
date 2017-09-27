@STATS
Feature: Basic Statistics Collection

  @MVP1
  Scenario: Statistics gets collected

    This scenario makes sure statistics gets collected

    Given a clean controller
    And a random linear topology of 5 switches
    When the controller learns the topology
    Then data go to database

  @MVP1
  Scenario: Statistics keeps getting collected

    This scenario makes sure statistics keeps getting collected

    Given a clean controller
    And a random linear topology of 5 switches
    When the controller learns the topology
    Then database keeps growing
