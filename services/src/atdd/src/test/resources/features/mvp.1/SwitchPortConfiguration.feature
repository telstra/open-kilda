@MVP1 @FCRUD
Feature: Configure switch port

  Background:
    Given a clean controller
    And created simple topology from two switches
    And all port statuses for switch "00:01:00:00:00:00:00:01" are 'Up'

  Scenario: Change port status
    When change port status to 'Down' for switch "00:01:00:00:00:00:00:01" port "2"
    Then port status for switch "00:01:00:00:00:00:00:01" port "2" is 'Down'
    And all port statuses for switch "00:01:00:00:00:00:00:01" except for port "2" are 'Up'

    When change port status to 'Up' for switch "00:01:00:00:00:00:00:01" port "2"
    And all port statuses for switch "00:01:00:00:00:00:00:01" are 'Up'
