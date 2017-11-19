@TOPO
Feature: Basic Topology Discovery

  Basic Topology Discovery involves the basics at small scale:
  - 100 switches or less
  - Simple configuration (links)
  - Simple discovery time (ie not too aggressive - X milliseconds per Switch?)

  @MVP1
  Scenario Outline: Linear Network Discovery Time

    Verify topology discovery happens within acceptable time lengths.
    Initial assumption is that discovery time is non-linear; e.g. logarithmic.

    Given a clean controller
    And a random linear topology of <num> switches
    When the controller learns the topology
    Then the controller should converge within <discovery_time> milliseconds

    Examples:
      | num | discovery_time |
      |  10 |          25000 |
      |  20 |          25000 |


  Scenario Outline: Full-mesh Network Discovery Time

    Verify full mesh discovery time is acceptable

    Given a clean controller
    And a random tree topology with depth of <depth> and fanout of <fanout>
    When the controller learns the topology
    Then the controller should converge within <discovery_time> milliseconds

    Examples:
      |  depth | fanout | discovery_time |
      |      3 |      4 |          30000 |
      |      4 |      5 |          30000 |

  @MVP1
  Scenario: Ignore not signed LLDP packets

    Verify only LLDP packets with right sign approved

    Given a clean controller
    And a random linear topology of 2 switches
    And the controller learns the topology
    When send malformed lldp packet
    Then the topology is not changed
