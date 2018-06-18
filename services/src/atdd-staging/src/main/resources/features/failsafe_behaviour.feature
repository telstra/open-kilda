@Failsafe
Feature: Failsafe Suite

  Verify correct system behavior under unexpected conditions like isl going down etc.

  Background:
    Given the reference topology

  Scenario: ISL connectivity is lost, system is able to react in expected way and reroute after some timeout
    Given Create 1 flow with A Switch used and at least 1 alternate path between source and destination switch and 500 bandwidth

    When ISL between switches loses connectivity
    And Remains in this state for 30 seconds
    Then ISL status is DISCOVERED
    And ISL status changes to FAILED
    And flow is in UP state
    And flow is valid per Northbound validation
    And all active switches have correct rules installed per Northbound validation
    And flow has traffic going with bandwidth not less than 450 and not greater than 550

    When Changed ISL restores connectivity
    Then ISL status changes to DISCOVERED
    And flow is in UP state
    And flow is valid per Northbound validation
    And all active switches have correct rules installed per Northbound validation
    And flow has traffic going with bandwidth not less than 450 and not greater than 550
    And each flow can be deleted

  Scenario: Flickering port down events should cause related ISLs to increase cost
    Given select a random ISL with A-Switch and alias it as 'aswitchIsl'
    And select a reverse path ISL for 'aswitchIsl' and alias it as 'aswitchIslReverse'
    And set cost of 'aswitchIsl' ISL to 1000
    And set cost of 'aswitchIslReverse' ISL to 1000

    When source port for ISL 'aswitchIsl' goes down
    Then ISL status changes to FAILED for ISLs: aswitchIsl, aswitchIslReverse
    And property 'cost' of aswitchIsl ISL equals to '11000'
    And property 'cost' of aswitchIslReverse ISL equals to '11000'

    When source port for ISL 'aswitchIsl' goes up
    And source port for ISL 'aswitchIsl' goes down
    Then ISL status is FAILED for ISLs: aswitchIsl, aswitchIslReverse
    And property 'cost' of aswitchIsl ISL equals to '11000'
    And property 'cost' of aswitchIslReverse ISL equals to '11000'

    When destination port for ISL 'aswitchIsl' goes down
    Then ISL status is FAILED for ISLs: aswitchIsl, aswitchIslReverse
    And property 'cost' of aswitchIsl ISL equals to '11000'
    And property 'cost' of aswitchIslReverse ISL equals to '11000'

    When source port for ISL 'aswitchIsl' goes up
    Then ISL status is FAILED for ISLs: aswitchIsl, aswitchIslReverse
    And destination port for ISL 'aswitchIsl' goes up
    Then ISL status changes to DISCOVERED for ISLs: aswitchIsl, aswitchIslReverse
    And property 'cost' of aswitchIsl ISL equals to '11000'
    And property 'cost' of aswitchIslReverse ISL equals to '11000'

    And delete all link properties
