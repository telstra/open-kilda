Feature: Advanced Topology Discovery

  Scenario Outline: Large Scale Network, Partial Mesh, Discovery Time

    Large scale (>1000) switch network won't be linear or full mesh. It'll have
      some sort of partial mesh / hub-and-spoke / star architecteure.  These tests will
      still validate that the network is discovered, accurately, within a certain period
      of time.

    Given a clean controller
    And a random star topology of <num> switches
    When the controller learns the topology
    Then the controller should converge within <discovery_time> milliseconds
    And the learned topology should match the real topology

    Examples:
      |   num | discovery_time |
      |    50 |         120000 |
      |   100 |         240000 |
      |  1000 |          60000 |
      |  5000 |         120000 |
      | 10000 |         240000 |
