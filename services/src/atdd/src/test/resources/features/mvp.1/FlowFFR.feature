@FFR
Feature: Flow failover, failure and recovery

  @MVP1.2
  Scenario: Port Failover followed by failure followed by recovery

    This scenario checks that failover and recovery happens orderly and that
    failures do not break things apart.

    Given a clean flow topology
    And a clean controller
    And a multi-path topology
    And topology contains 16 links
    And a flow ffr is successfully created
    And flow ffr in UP state
    And flow ffr path is shortest
    And traffic flows through ffr flow

    When a switch 00:00:00:00:00:00:00:03 port 1 is disabled
    And flow ffr in UP state
    And flow ffr path is alternate
    Then traffic flows through ffr flow

    When a switch 00:00:00:00:00:00:00:05 port 1 is disabled
    And flow ffr in DOWN state
    And flow ffr path is alternate
    Then traffic does not flow through ffr flow

    When a switch 00:00:00:00:00:00:00:05 port 1 is enabled
    Then traffic flows through ffr flow

  @MVP1.2
  Scenario: Port Failover followed by failure followed by recovery 2

    This scenario checks that failover and recovery happens orderly and that
    failures do not break things apart. Also it checks whether alternate route
    is picked after complete failure.

    Given a clean flow topology
    And a clean controller
    And a multi-path topology
    And topology contains 16 links
    And a flow ffr is successfully created
    And flow ffr in UP state
    And flow ffr path is shortest
    And traffic flows through ffr flow

    When a switch 00:00:00:00:00:00:00:03 port 1 is disabled
    And flow ffr in UP state
    And flow ffr path is alternate
    Then traffic flows through ffr flow

    When a switch 00:00:00:00:00:00:00:05 port 1 is disabled
    And flow ffr in DOWN state
    And flow ffr path is alternate
    Then traffic does not flow through ffr flow

    When a switch 00:00:00:00:00:00:00:03 port 1 is enabled
    And flow ffr in UP state
    And flow ffr path is shortest
    Then traffic flows through ffr flow

  @MVP1.2
  Scenario: ISL failover followed by failure followed by recovery

    Developer notes:
      1. The Given scenario is too long and consists of tests in itself, nedd to shorten.
      2. Interesting use of multiple When/Then blocs. Can we consolidate?

    Given a clean flow topology
    And a clean controller
    And a multi-path topology
    And topology contains 16 links
    And a flow ffr is successfully created
    And flow ffr in UP state
    And flow ffr path is shortest
    And traffic flows through ffr flow

    When an isl switch 00:00:00:00:00:00:00:03 port 1 is failed
    And flow ffr in UP state
    And flow ffr path is alternate
    Then traffic flows through ffr flow

    When an isl switch 00:00:00:00:00:00:00:05 port 1 is failed
    And flow ffr in DOWN state
    And flow ffr path is alternate
    Then traffic does not flow through ffr flow

    When an isl switch 00:00:00:00:00:00:00:03 port 1 is discovered
    And flow ffr in UP state
    And flow ffr path is shortest
    Then traffic flows through ffr flow

  @MVP1.2
  Scenario: Switch failover followed by failure followed by recovery

    Developer notes:
    1. This is broader than the ISL failover .. let's be clear what is being tested here (ie ensure
       we aren't duplicating tests unnecessarily)

    Given a clean flow topology
    And a clean controller
    And a multi-path topology
    And topology contains 16 links
    And a flow ffr is successfully created
    And flow ffr in UP state
    And flow ffr path is shortest
    And traffic flows through ffr flow

    When a switch 00:00:00:00:00:00:00:03 is disconnected
    And flow ffr in UP state
    And flow ffr path is alternate
    Then traffic flows through ffr flow

    When a switch 00:00:00:00:00:00:00:05 is disconnected
    And flow ffr in DOWN state
    And flow ffr path is alternate
    Then traffic does not flow through ffr flow

    When a switch 00:00:00:00:00:00:00:03 is connected
    And flow ffr in UP state
    And flow ffr path is shortest
    Then traffic flows through ffr flow

  @MVP1.2
  Scenario Outline: Flow status by switch ingress or egress disconnection

    This scenario checks that flows with disconnected switch as endpoint goes to down state without
    path re-computation and then switch connection brings flow state back to UP state with same
    path.

    Given a clean flow topology
    And a clean controller
    And a multi-path topology
    And topology contains 16 links
    When flow <flow_id> creation request with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is successful
    When flow <flow_id> with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> could be created
    Then flow <flow_id> in UP state

    When a switch <source_switch> is disconnected
    Then flow <flow_id> in DOWN state
    When a switch <destination_switch> is disconnected
    Then flow <flow_id> in DOWN state
    When a switch <source_switch> is connected
    Then flow <flow_id> in <split_state> state
    When a switch <destination_switch> is connected
    Then flow <flow_id> in UP state

  Examples:
    | flow_id |      source_switch      | source_port | source_vlan |   destination_switch    | destination_port | destination_vlan | bandwidth | split_state |
      # flow with transit vlans and intermediate switches
    | c3swap  | 00:00:00:00:00:00:00:02 |      11      |     103     | 00:00:00:00:00:00:00:07 |         12        |       203        |   10000   |    DOWN     |
      # flow with transit vlans and without intermediate switches
    | c2swap  | 00:00:00:00:00:00:00:05 |      11      |     102     | 00:00:00:00:00:00:00:06 |         12        |       202        |   10000   |    DOWN     |
      # flow without transit vlans and intermediate switches
    | c1swap  | 00:00:00:00:00:00:00:04 |      11      |     101     | 00:00:00:00:00:00:00:04 |         12        |       201        |   10000   |     UP      |
