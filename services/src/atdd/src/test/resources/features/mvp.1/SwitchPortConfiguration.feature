@MVP1 @FCRUD
Feature: Configure switch port

  Background:
    Given a clean controller
    And created simple topology from two switches
    And port status for switch "00:01:00:00:00:00:00:01" port "2" is 'Up'

  Scenario: Change port status
    When change port status to 'Down' for switch "00:01:00:00:00:00:00:01" port "2"
    Then port status for switch "00:01:00:00:00:00:00:01" port "2" is 'Down'

    When change port status to 'Up' for switch "00:01:00:00:00:00:00:01" port "2"
    Then port status for switch "00:01:00:00:00:00:00:01" port "2" is 'Up'

