@TOPO
Feature: Basic Topology Events

  Basic Topology Events includes the following types of events
  - Switch up / down
  - Link up / down

  The events should be propagated to the Topology Engine and they should stop link checks.
  As part of the 1.0 release, we've added logic related to policies and the ability to augment
  the behavior of link drops / lifetime failure / etc. The tests below should reflect some of
  these types of tests - ie ensure we are honoring the policies, and have the ability to overwrite
  the policies.

  @MVP1
  Scenario: Link is Dropped

    Given a clean controller
    And a random linear topology of 5 switches
    When the controller learns the topology
    And multiple links exist between all switches
    And a link is dropped in the middle
    Then the link disappears from the topology engine in 60 seconds.

  @MVP1.1
  Scenario: Link is Added

  This scenario will test link up events after the initial discovery period.

    Given a clean controller
    And a random linear topology of 5 switches
    When the controller learns the topology
    And multiple links exist between all switches
    And a link is added in the middle
    Then the link appears in the topology engine.

  @MVP1.1
  Scenario: Switch is Dropped

    Given a clean controller
    And a random linear topology of 5 switches
    When the controller learns the topology
    And multiple links exist between all switches
    And a switch is dropped in the middle
    Then the links disappear from the topology engine.
    And now amount of switches is 4.

  @MVP1.1
  Scenario: Switch is Added

    This scenario will test switch up events after the initial discovery period.

    Given a clean controller
    And a random linear topology of 5 switches
    When the controller learns the topology
    And multiple links exist between all switches
    And a switch is added at the edge
    And links are added between the new switch and its neighbor
    Then multiple links exist between all switches
    And now amount of switches is 6.

  @MVP1.2
  Scenario: Exercise ISL policy

    This scenario tests the policy mechanisms of ISL discovery

    Given a clean controller
    And a random linear topology of 5 switches
    When the controller learns the topology
    And exhausts the isl discovery policy for a link
    And the isl discovery counters for that link is manually reset
    Then the controller sends discovery packets again
