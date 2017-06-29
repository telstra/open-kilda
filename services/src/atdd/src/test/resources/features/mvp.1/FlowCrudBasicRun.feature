Feature: Basic Flow CRUD

  Description

  @MVP1
  Scenario Outline: Flow Creation on Small Linear Network Topology

    This scenario setups flows across the entire set of switches and checks that these flows were stored in database

    When flow creation request with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is successful
    Then flow with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> could be created
    And rules with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> are installed
    And traffic through <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is forwarded

    Examples:
      |      source_switch      | source_port | source_vlan |   destination_switch    | destination_port | destination_vlan | bandwidth |
      # flows with transit vlans and intermediate switches
      | de:ad:be:ef:00:00:00:02 |      1      |      0      | de:ad:be:ef:00:00:00:04 |         2        |        0         |   10000   |
      | de:ad:be:ef:00:00:00:02 |      1      |      0      | de:ad:be:ef:00:00:00:04 |         2        |       100        |   10000   |
      | de:ad:be:ef:00:00:00:02 |      1      |     100     | de:ad:be:ef:00:00:00:04 |         2        |        0         |   10000   |
      | de:ad:be:ef:00:00:00:02 |      1      |     100     | de:ad:be:ef:00:00:00:04 |         2        |       200        |   10000   |
      # flows with transit vlans and without intermediate switches
      | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:04 |         2        |        0         |   10000   |
      | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:04 |         2        |       100        |   10000   |
      | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:04 |         2        |        0         |   10000   |
      | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:04 |         2        |       200        |   10000   |
      # flows without transit vlans and intermediate switches
      | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:03 |         2        |        0         |   10000   |
      | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:03 |         2        |       100        |   10000   |
      | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:03 |         2        |        0         |   10000   |
      | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:03 |         2        |       200        |   10000   |

  @MVP1
  Scenario Outline: Flow Reading on Small Linear Network Topology

    This scenario setups flows across the entire set of switches and checks that these flows could be read from database

    When flow creation request with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is successful
    And flow with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> could be created
    Then flow with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> could be read

    Examples:
      |      source_switch      | source_port | source_vlan |   destination_switch    | destination_port | destination_vlan | bandwidth |
      # flows with transit vlans and intermediate switches
      | de:ad:be:ef:00:00:00:02 |      1      |      0      | de:ad:be:ef:00:00:00:04 |         2        |        0         |   10000   |
      | de:ad:be:ef:00:00:00:02 |      1      |      0      | de:ad:be:ef:00:00:00:04 |         2        |       100        |   10000   |
      | de:ad:be:ef:00:00:00:02 |      1      |     100     | de:ad:be:ef:00:00:00:04 |         2        |        0         |   10000   |
      | de:ad:be:ef:00:00:00:02 |      1      |     100     | de:ad:be:ef:00:00:00:04 |         2        |       200        |   10000   |
      # flows with transit vlans and without intermediate switches
      | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:04 |         2        |        0         |   10000   |
      | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:04 |         2        |       100        |   10000   |
      | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:04 |         2        |        0         |   10000   |
      | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:04 |         2        |       200        |   10000   |
      # flows without transit vlans and intermediate switches
      | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:03 |         2        |        0         |   10000   |
      | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:03 |         2        |       100        |   10000   |
      | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:03 |         2        |        0         |   10000   |
      | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:03 |         2        |       200        |   10000   |

  @MVP1
  Scenario Outline: Flow Updating on Small Linear Network Topology

  This scenario setups flows across the entire set of switches, then updates them and checks that flows were updated in database

    When flow creation request with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is successful
    And flow with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> could be created
    Then flow with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> could be updated with <new_bandwidth>
    And rules with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> are updated with <new_bandwidth>
    And traffic through <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is forwarded

    Examples:
      |      source_switch      | source_port | source_vlan |   destination_switch    | destination_port | destination_vlan | bandwidth | new_bandwidth |
      # flows with transit vlans and intermediate switches
      | de:ad:be:ef:00:00:00:02 |      1      |      0      | de:ad:be:ef:00:00:00:04 |         2        |        0         |   10000   |     20000     |
      | de:ad:be:ef:00:00:00:02 |      1      |      0      | de:ad:be:ef:00:00:00:04 |         2        |       100        |   10000   |     20000     |
      | de:ad:be:ef:00:00:00:02 |      1      |     100     | de:ad:be:ef:00:00:00:04 |         2        |        0         |   10000   |     20000     |
      | de:ad:be:ef:00:00:00:02 |      1      |     100     | de:ad:be:ef:00:00:00:04 |         2        |       200        |   10000   |     20000     |
      # flows with transit vlans and without intermediate switches
      | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:04 |         2        |        0         |   10000   |     20000     |
      | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:04 |         2        |       100        |   10000   |     20000     |
      | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:04 |         2        |        0         |   10000   |     20000     |
      | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:04 |         2        |       200        |   10000   |     20000     |
      # flows without transit vlans and intermediate switches
      | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:03 |         2        |        0         |   10000   |     20000     |
      | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:03 |         2        |       100        |   10000   |     20000     |
      | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:03 |         2        |        0         |   10000   |     20000     |
      | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:03 |         2        |       200        |   10000   |     20000     |

  @MVP1
  Scenario Outline: Flow Deletion on Small Linear Network Topology

  This scenario setups flows across the entire set of switches, then deletes them and checks that flows were deleted from database

    When flow creation request with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is successful
    And flow with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> could be created
    Then flow with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> could be deleted
    And rules with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> are deleted
    And traffic through <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is not forwarded

    Examples:
      |      source_switch      | source_port | source_vlan |   destination_switch    | destination_port | destination_vlan | bandwidth |
     # flows with transit vlans and intermediate switches
      | de:ad:be:ef:00:00:00:02 |      1      |      0      | de:ad:be:ef:00:00:00:04 |         2        |        0         |   10000   |
      | de:ad:be:ef:00:00:00:02 |      1      |      0      | de:ad:be:ef:00:00:00:04 |         2        |       100        |   10000   |
      | de:ad:be:ef:00:00:00:02 |      1      |     100     | de:ad:be:ef:00:00:00:04 |         2        |        0         |   10000   |
      | de:ad:be:ef:00:00:00:02 |      1      |     100     | de:ad:be:ef:00:00:00:04 |         2        |       200        |   10000   |
      # flows with transit vlans and without intermediate switches
      | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:04 |         2        |        0         |   10000   |
      | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:04 |         2        |       100        |   10000   |
      | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:04 |         2        |        0         |   10000   |
      | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:04 |         2        |       200        |   10000   |
      # flows without transit vlans and intermediate switches
      | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:03 |         2        |        0         |   10000   |
      | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:03 |         2        |       100        |   10000   |
      | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:03 |         2        |        0         |   10000   |
      | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:03 |         2        |       200        |   10000   |
