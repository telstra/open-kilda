Feature: Validate kilda behaviour during connectivity issues with speaker
  @MVP1
  Scenario Outline: Lost connection between speaker and kafka (idle)
    Given a clean controller
    And a nonrandom linear topology of 7 switches
    And topology contains 12 links

    When link between controller and kafka are lost
    And 12 seconds passed
    And link between controller and kafka restored

    Then flow <flow_id> creation request with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is successful
    And flow <flow_id> with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> could be created
    And flow <flow_id> in UP state
    And traffic through <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is pingable

    Examples:
      | flow_id |      source_switch      | source_port | source_vlan |   destination_switch    | destination_port | destination_vlan | bandwidth |
      | c3none  | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:05 |         2        |        0         |   10000   |

  @ignore
  Scenario: Speaker goes down for a long time, kilda is able to recover
    Given a clean controller
    And a clean flow topology
    And created simple topology from two switches
    And topology contains 2 links

    When stop floodlight container
    And wait for 30 seconds
    And flow fl-restart creation request with 00:01:00:00:00:00:00:01 2 102 and 00:01:00:00:00:00:00:02 2 102 and 10000 is successful
    And start floodlight container

    Then flow fl-restart in UP state

  @InDev
  Scenario Outline: Switches lost between switches to speaker (idle)
    Given a clean controller
    And a nonrandom linear topology of 7 switches
    And topology contains 12 links

    When link between all switches and controller are lost
    # we need to wait at least 3 * "health check interval seconds"
    And 12 seconds passed
    And link between all switches and controller restored

    Then flow <flow_id> creation request with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is successful
    And flow <flow_id> with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> could be created
    And flow <flow_id> in UP state
    And traffic through <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is pingable

    Examples:
      | flow_id |      source_switch      | source_port | source_vlan |   destination_switch    | destination_port | destination_vlan | bandwidth |
      | c3none  | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:05 |         2        |        0         |   10000   |

#  Scenario: New switch added when speaker is unreachable
#    Given a clean controller
#
#  Scenario: Remove switch when speaker is unreachable
#    Given a clean controller
