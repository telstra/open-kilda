Feature: Flow status tests

  @MVP1
  Scenario: Flow status is correctly set for working flows
  
    This scenario makes sure that successfully created flows obtain proper
    statuses.

    Given a clean controller
    And a clean flow topology
    And a multi-path topology
    When a flow is successfully created
    Then flow status is UP

  @MVP1
  Scenario: Flow status is correctly set for broken flows
  
    This scenario makes sure that unsuccessfully created flows obtain proper
    statuses.

    Given a clean controller
    And a clean flow topology
    And a multi-path topology
    When a flow creation has failed
    Then flow status is DOWN

  @MVP1
  Scenario: Flow status is correctly updated on link failure
  
    This scenario makes sure that a flow through failed link changes status
    properly.

    Given a clean controller
    And a clean flow topology
    And a multi-path topology
    When a flow is successfully created
    And flow status is UP
    And single point of failure has failed
    Then flow status is DOWN

  @MVP1
  Scenario: Flow status is unaffected by non-critical failure
  
    This scenario makes sure that non-critical failure does not affect flow
    status.

    Given a clean controller
    And a clean flow topology
    And a multi-path topology
    When a flow is successfully created
    And flow status is UP
    And random non-critical link has failed
    Then flow status is UP
