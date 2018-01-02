@TOPO
Feature: Basic Topology Discovery

  Basic Topology Discovery involves the basics at small scale:
  - 100 switches or less
  - Simple configuration (links)
  - Simple discovery time (ie not too aggressive - X milliseconds per Switch?)

  @MVP1 @SMOKE
  Scenario Outline: Linear Network Discovery Smoke Test

  Verify topology discovery happens within acceptable time lengths.
  This is for a small numbe (3), useful in verifying that a really small network is discoverable.

    Given a clean controller
    And a random linear topology of <num> switches
    When the controller learns the topology
    Then the controller should converge within <discovery_time> milliseconds

    Examples:
      | num | discovery_time |
      |   3 |          60000 |


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
      |  10 |          60000 |
      |  20 |          70000 |

  @MVP1
  Scenario Outline: Full-mesh Network Discovery Time

    Verify full mesh discovery time is acceptable.
    Depth of 3 / Fanout of 4 = 21, 40

    Given a clean controller
    And a random tree topology with depth of <depth> and fanout of <fanout>
    When the controller learns the topology
    Then the controller should converge within <discovery_time> milliseconds

    Examples:
      |  depth | fanout | discovery_time |
      |      3 |      4 |          80000 |

  @MVP1.2 @SCALE
  Scenario Outline: Full-mesh Network Discovery Time

    Verify full mesh discovery time is acceptable.
    Depth of 4 / Fanout of 5 = 156 switches, 310 links.


    Given a clean controller
    And a random tree topology with depth of <depth> and fanout of <fanout>
    When the controller learns the topology
    Then the controller should converge within <discovery_time> milliseconds

    Examples:
      |  depth | fanout | discovery_time  |
      |      4 |      5 |          360000 |

  @MVP1 @SEC
  Scenario: Ignore not signed LLDP packets

    Verify only LLDP packets with right sign approved

    Given a clean controller
    And a random linear topology of 2 switches
    And the controller learns the topology
    When send malformed lldp packet
    Then the topology is not changed
