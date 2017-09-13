@FFFR
Feature: Flow Reroute tests


  @MVP1
  Scenario: Flow restored after network topology up/down

    This scenario creates small multi-path network topology
    and checks that link available bandwidth is tracked.

    When flow restored creation request with 00:00:00:00:00:00:00:02 1 0 and 00:00:00:00:00:00:00:07 2 0 and 1000000 is successful
    And flow restored with 00:00:00:00:00:00:00:02 1 0 and 00:00:00:00:00:00:00:07 2 0 and 1000000 could be created
    And rules with 00:00:00:00:00:00:00:02 1 0 and 00:00:00:00:00:00:00:07 2 0 and 1000000 are installed
    And traffic through 00:00:00:00:00:00:00:02 1 0 and 00:00:00:00:00:00:00:07 2 0 and 1000000 is forwarded
    When a clean controller
    And a random linear topology of 5 switches
    And the controller learns the topology
    Then rules with 00:00:00:00:00:00:00:02 1 0 and 00:00:00:00:00:00:00:07 2 0 and 1000000 are installed
    And traffic through 00:00:00:00:00:00:00:02 1 0 and 00:00:00:00:00:00:00:07 2 0 and 1000000 is forwarded
