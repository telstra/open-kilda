@FCRUD @FAT
Feature: Basic Flow CRUD

  This feature tests basic flow CRUD operations.
  These tests are Functional Acceptance Tests (FAT).

  @MVP1 @SMOKE
  Scenario Outline: Flow Creation on Small Linear Network Topology

    This scenario setups flows across the entire set of switches and checks that these flows were stored in database
    NB: ** vlan of 0 means match on port, not vlan **

    #
    # TODO - This "Given" can be reduced to a single statement. And network topo can be cached. (speed up tests)
    #
    Given a clean controller
    And a nonrandom linear topology of 7 switches
    And topology contains 12 links
    And a clean flow topology
    When flow <flow_id> creation request with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is successful
    Then flow <flow_id> with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> could be created
    And flow <flow_id> in UP state
    And validation of flow <flow_id> is successful with no discrepancies
    And rules with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> are installed
    # TEST the READ
    And flow <flow_id> with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> could be read
    # TEST the CONNECTION
    # And traffic through <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is forwarded
    And traffic through <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is pingable
    # TEST the DELETE
    Then flow <flow_id> with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> could be deleted
    And rules with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> are deleted
    # And traffic through <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is not forwarded
    And traffic through <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is not pingable

    Examples:
      | flow_id |      source_switch      | source_port | source_vlan |   destination_switch    | destination_port | destination_vlan | bandwidth |
      # flows with transit vlans and intermediate switches
      | c3none  | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:05 |         2        |        0         |   10000   |
      # flows with transit vlans and without intermediate switches
      | c2none  | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:04 |         2        |        0         |   10000   |
      # flows without transit vlans and intermediate switches
      | c1none  | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:03 |         2        |        0         |   10000   |



  @MVP1 @CRUD_CREATE
  Scenario Outline: Flow Creation - flows with transit vlans and intermediate switches

    This scenario setups flows across the entire set of switches and checks that these flows were stored in database
    It also checks READ and DELETE.
    # TODO: as part of FAT, verify intermediary caches are correct (created/cleared) (currently looks at DB only)
    # TODO: these tests don't check the switches/speaker and whether flows are removed
    # TODO: Consider tests to determine if there are any duplicates

    Given a clean controller
    And a nonrandom linear topology of 7 switches
    And topology contains 12 links
    And a clean flow topology
    When flow <flow_id> creation request with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is successful
    Then flow <flow_id> with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> could be created
    And flow <flow_id> in UP state
    And validation of flow <flow_id> is successful with no discrepancies
    And rules with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> are installed
    # TEST the READ
    And flow <flow_id> with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> could be read
    # TEST the CONNECTION
    #And traffic through <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is forwarded
    And traffic through <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is pingable
    # TEST the DELETE
    Then flow <flow_id> with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> could be deleted
    And rules with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> are deleted
    #And traffic through <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is not forwarded
    And traffic through <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is not pingable

    Examples:
      | flow_id |      source_switch      | source_port | source_vlan |   destination_switch    | destination_port | destination_vlan | bandwidth |
      # flows with transit vlans and intermediate switches
      | c3none  | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:05 |         2        |        0         |   10000   |
      | c3push  | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:05 |         2        |       100        |   10000   |
      | c3pop   | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:05 |         2        |        0         |   10000   |
      | c3swap  | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:05 |         2        |       200        |   10000   |

  @MVP1 @CRUD_CREATE
  Scenario Outline: Flow Creation - flows with transit vlans and without intermediate switches

    This scenario setups flows across the entire set of switches and checks that these flows were stored in database
    It also checks READ and DELETE.

    Given a clean controller
    And a nonrandom linear topology of 7 switches
    And topology contains 12 links
    And a clean flow topology
    When flow <flow_id> creation request with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is successful
    Then flow <flow_id> with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> could be created
    And flow <flow_id> in UP state
    And validation of flow <flow_id> is successful with no discrepancies
    And rules with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> are installed
    # TEST the READ
    And flow <flow_id> with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> could be read
    # TEST the CONNECTION
    #And traffic through <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is forwarded
    And traffic through <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is pingable
    # TEST the DELETE
    Then flow <flow_id> with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> could be deleted
    And rules with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> are deleted
    #And traffic through <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is not forwarded
    And traffic through <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is not pingable

    Examples:
      | flow_id |      source_switch      | source_port | source_vlan |   destination_switch    | destination_port | destination_vlan | bandwidth |
      # flows with transit vlans and without intermediate switches
      | c2none  | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:04 |         2        |        0         |   10000   |
      | c2push  | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:04 |         2        |       100        |   10000   |
      | c2pop   | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:04 |         2        |        0         |   10000   |
      | c2swap  | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:04 |         2        |       200        |   10000   |

  @MVP1 @CRUD_CREATE
  Scenario Outline: Flow Creation - flows without transit vlans and intermediate switches

    This scenario setups flows across the entire set of switches and checks that these flows were stored in database
    It also checks READ and DELETE.

    Given a clean controller
    And a nonrandom linear topology of 7 switches
    And topology contains 12 links
    And a clean flow topology
    When flow <flow_id> creation request with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is successful
    Then flow <flow_id> with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> could be created
    And flow <flow_id> in UP state
    And validation of flow <flow_id> is successful with no discrepancies
    And rules with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> are installed
    # TEST the READ
    And flow <flow_id> with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> could be read
    # TEST the CONNECTION
    #And traffic through <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is forwarded
    And traffic through <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is pingable
    # TEST the DELETE
    Then flow <flow_id> with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> could be deleted
    And rules with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> are deleted
    #And traffic through <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is not forwarded
    And traffic through <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is not pingable

    Examples:
      | flow_id |      source_switch      | source_port | source_vlan |   destination_switch    | destination_port | destination_vlan | bandwidth |
      # flows without transit vlans and intermediate switches
      | c1none  | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:03 |         2        |        0         |   10000   |
      | c1push  | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:03 |         2        |       100        |   10000   |
      | c1pop   | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:03 |         2        |        0         |   10000   |
      | c1swap  | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:03 |         2        |       200        |   10000   |


  @MVP1 @CRUD_UPDATE
  Scenario Outline: Flow Updating on Small Linear Network Topology

  This scenario setups flows across the entire set of switches, then updates them and checks that flows were updated in database

    Given a clean controller
    And a nonrandom linear topology of 7 switches
    And topology contains 12 links
    And a clean flow topology
    When flow <flow_id> creation request with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is successful
    And flow <flow_id> with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> could be created
    And flow <flow_id> in UP state
    Then flow <flow_id> with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> could be updated with <new_bandwidth>
    And flow <flow_id> in UP state
    And validation of flow <flow_id> is successful with no discrepancies
    And rules with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> are updated with <new_bandwidth>
    #And traffic through <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is forwarded
    And traffic through <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is pingable

    Examples:
      | flow_id  |      source_switch      | source_port | source_vlan |   destination_switch    | destination_port | destination_vlan | bandwidth  | new_bandwidth |
      # flows with transit vlans and intermediate switches
      | u3none   | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:05 |         2        |        0         |   10000    |     20000     |
      | u3push   | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:05 |         2        |       100        |   10000    |     20000     |
      | u3pop    | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:05 |         2        |        0         |   10000    |     20000     |
      | u3swap   | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:05 |         2        |       200        |   10000    |     20000     |
      | u3bwdown | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:05 |         2        |       200        |   10000000 |    1000000    |
      # flows with transit vlans and without intermediate switches
      | u2none   | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:04 |         2        |        0         |   10000    |     20000     |
      | u2push   | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:04 |         2        |       100        |   10000    |     20000     |
      | u2pop    | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:04 |         2        |        0         |   10000    |     20000     |
      | u2swap   | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:04 |         2        |       200        |   10000    |     20000     |
      | u2bwdown | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:04 |         2        |       200        |   10000000 |    1000000    |
      # flows without transit vlans and intermediate switches
      | u1none   | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:03 |         2        |        0         |   10000    |     20000     |
      | u1push   | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:03 |         2        |       100        |   10000    |     20000     |
      | u1pop    | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:03 |         2        |        0         |   10000    |     20000     |
      | u1swap   | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:03 |         2        |       200        |   10000    |     20000     |
      | u1bwdown | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:03 |         2        |       200        |   10000000 |    1000000    |


  @MVP1.1 @CRUD_NEGATIVE
  Scenario Outline: Flow Creation Negative Scenario for Vlan Conflicts

  This scenario setups flow across the entire set of switches and checks that no new flow with conflicting vlan could be installed

    Given a clean controller
    And a nonrandom linear topology of 7 switches
    And topology contains 12 links
    And a clean flow topology
    When flow <flow_id> creation request with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is successful
    And flow <flow_id> with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> could be created
    Then flow <flow_id>_conflict creation request with <source_switch> <source_port> 200 and <destination_switch> <destination_port> 200 and <bandwidth> is failed

    Examples:
      | flow_id  |      source_switch      | source_port | source_vlan |   destination_switch    | destination_port | destination_vlan | bandwidth |
      | vc2none  | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:04 |         2        |        0         |   10000   |
      | vc2push1 | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:04 |         2        |       100        |   10000   |
      | vc2push2 | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:04 |         2        |       200        |   10000   |
      | vc2pop1  | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:04 |         2        |        0         |   10000   |
      | vc2pop2  | de:ad:be:ef:00:00:00:03 |      1      |     200     | de:ad:be:ef:00:00:00:04 |         2        |        0         |   10000   |
      | vs2swap1 | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:04 |         2        |       200        |   10000   |
      | vs2swap2 | de:ad:be:ef:00:00:00:03 |      1      |     200     | de:ad:be:ef:00:00:00:04 |         2        |       100        |   10000   |
      | vs2swap3 | de:ad:be:ef:00:00:00:03 |      1      |     200     | de:ad:be:ef:00:00:00:04 |         2        |       200        |   10000   |

  @MVP1 @CRUD_CREATE
  Scenario Outline: Flow Creation - unmetered flows with transit vlans and intermediate switches

  This scenario setups unmetered flows across the entire set of switches and checks that these flows were stored in database

    Given a clean controller
    And a nonrandom linear topology of 7 switches
    And topology contains 12 links
    And a clean flow topology
    When flow <flow_id> creation request with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is successful
    Then flow <flow_id> with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> could be created
    And flow <flow_id> in UP state
    And validation of flow <flow_id> is successful with no discrepancies
    And rules with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> are installed
    # TEST the READ
    And flow <flow_id> with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> could be read
    # TEST the CONNECTION
    And traffic through <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is pingable
    # TEST the DELETE
    Then flow <flow_id> with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> could be deleted
    And rules with <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> are deleted
    And traffic through <source_switch> <source_port> <source_vlan> and <destination_switch> <destination_port> <destination_vlan> and <bandwidth> is not pingable

    Examples:
      | flow_id |      source_switch      | source_port | source_vlan |   destination_switch    | destination_port | destination_vlan | bandwidth |
      | uf3none | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:05 |         2        |        0         |     0     |
      | uf3push | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:05 |         2        |       100        |     0     |
      | uf3pop  | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:05 |         2        |        0         |     0     |
      | uf3swap | de:ad:be:ef:00:00:00:03 |      1      |     100     | de:ad:be:ef:00:00:00:05 |         2        |       200        |     0     |
