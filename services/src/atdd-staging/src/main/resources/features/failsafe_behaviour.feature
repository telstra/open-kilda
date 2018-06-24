@Failsafe
Feature: Failsafe Suite

  Verify correct system behavior under unexpected conditions like isl going down etc.

  Background:
    Given the reference topology

  Scenario: ISL goes down, system is able to react in expected way and reroute
    Given Create 1 flow with A Switch used and at least 1 alternate path between source and destination switch and 500 bandwidth

    When ISL between switches goes down
    And Remains in this state for 30 seconds

    Then ISL status is DISCOVERED
    And ISL status changes to FAILED
    Then flow is in UP state
    And flow is valid per Northbound validation
    And all active switches have correct rules installed per Northbound validation
    And flow has traffic going with bandwidth not less than 450 and not greater than 550

    When Changed ISL goes up

    Then ISL status changes to DISCOVERED
    And flow is in UP state
    And flow is valid per Northbound validation
    And all active switches have correct rules installed per Northbound validation
    And flow has traffic going with bandwidth not less than 450 and not greater than 550
    And each flow can be deleted

