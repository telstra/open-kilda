@TOPO
Feature: Basic Topology Events

  Basic Topology Events includes the following types of events
  - Switch up / down
  - Link up / down

  The events should be propagated to the Topology Engine and they should stop link checks.

  Scenario: Link is Dropped

    Given a clean controller
    And a random linear topology of 5 switches
    When the controller learns the topology
    And multiple links exist between all switches
    And a link is dropped in the middle
    Then the link will have no health checks
    And the link disappears from the topology engine.

  Scenario: Link is Added

  This scenario will test link up events after the initial discovery period.

    Given a clean controller
    And a random linear topology of 5 switches
    When the controller learns the topology
    And multiple links exist between all switches
    And a link is added in the middle
    Then the link will have health checks
    And the link appears in the topology engine.

  @MVP1
  Scenario: Switch is Dropped

    Given a clean controller
    And a random linear topology of 5 switches
    When the controller learns the topology
    And multiple links exist between all switches
    And a switch is dropped in the middle
    Then all links through the dropped switch will have no health checks
    And the links disappear from the topology engine.
    And the switch disappears from the topology engine.

  Scenario: Switch is Added

    This scenario will test switch up events after the initial discovery period.

    Given a clean controller
    And a random linear topology of 5 switches
    When the controller learns the topology
    And multiple links exist between all switches
    And a switch is added at the edge
    And links are added between the new switch and its neighbor
    Then all links through the added switch will have health checks
    And the links appear in the topology engine.
    And the switch appears in the topology engine.

