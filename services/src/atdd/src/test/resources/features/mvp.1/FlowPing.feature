@MVP1
Feature: Test flow integrity by sending special discovery packet by flow's path

  Scenario: Verify healthy flow
    Given a clean controller
    And a nonrandom linear topology of 7 switches
    And a clean flow topology
    And topology contains 12 links
    And flow 00:00:00:00:00:00:00:01(4) and 00:00:00:00:00:00:00:07(4) with id="positive-flow-verify" is created

    Then use flow verification for flow id="positive-flow-verify"
    And flow verification for flow id="positive-flow-verify" is ok ok

  Scenario: Verify unhealthy flow
    Given a clean controller
    And a nonrandom linear topology of 7 switches
    And a clean flow topology
    And topology contains 12 links
    And flow 00:00:00:00:00:00:00:01(4) and 00:00:00:00:00:00:00:07(4) with id="half-fail-flow-verify" is created
    And forward flow path is broken

    Then use flow verification for flow id="half-fail-flow-verify"
    And flow verification for flow id="half-fail-flow-verify" is fail ok
