@CT
Feature: Clearing up flow rules

  @MVP1
  Scenario: Flow rules are still alive

    Given started floodlight container
    And created simple topology from two switches
    And added custom flow rules
    When floodlight controller is restarted
    Then flow rules should not be cleared up
