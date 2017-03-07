Feature: Basic Topology Discovery

  Basic Topology Discovery involes the basics at small scale:
  - 100 switches or less
  - Simple configuration (links)
  - Simple discovery time (ie not too aggressive - X milliseconds per Switch?)


  Scenario Outline: Linear Network Discovery Time

    Verify topology discovery happens within acceptable time lengths.
    Initial assumption is that discovery time is non-linear; e.g. logarithmic.

    Given a new controller
    And a random linear topology of <switches>
    When the controller learns the topology
    Then the controller should converge within <discovery_time> milliseconds

    Examples:
      | switches | discovery_time |
      |       10 |          30000 |
      |       50 |         120000 |
      |      100 |         240000 |



  Scenario Outline: Full-mesh Network Discovery Time

    Verify full mesh discovery time is acceptable

    Given a new controller
    And a random full-mesh topology of <switches>
    When the controller learns the topology
    Then the controller should converge within <discovery_time> milliseconds

    Examples:
      | switches | discovery_time |
      |       10 |          40000 |
      |       50 |         150000 |
      |      100 |         450000 |
