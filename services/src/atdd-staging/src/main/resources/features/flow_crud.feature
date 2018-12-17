@Flow
Feature: Flow CRUD
  This feature tests flow CRUD operations.

  Background:
    Given the reference topology
    And all defined switches are discovered
    And all defined links are detected
    And a clean topology with no flows and no discrepancies in switch rules and meters

  @CRUD
  Scenario: Create, read, update and delete flows across the entire set of defined switches

    Given flows defined over active switches in the reference topology
    And each flow has unique flow_id
    And each flow has max bandwidth set to 750

    When initialize creation of given flows

    Then each flow is in UP state
    And each flow can be read from Northbound
    And each flow is valid per Northbound validation
    And all active switches have correct rules installed per Northbound validation
    And each flow has meters installed with 750 max bandwidth
    # Kilda shapes traffic inaccurately when the bandwidth is too low due to a high burst rate on Centec switches.
    # Avoid using values less than 512 kbps or use higher tolerance intervals.
    And each flow has traffic going with bandwidth not less than 700 and not greater than 820

    Then each flow can be updated with 1250 max bandwidth and new vlan
    And each flow is in UP state
    And each flow is valid per Northbound validation
    And all active switches have correct rules installed per Northbound validation
    And each flow has meters installed with 1250 max bandwidth
    And each flow has traffic going with bandwidth not less than 1200 and not greater than 1350

    Then each flow can be deleted
    And each flow can not be read from Northbound
    And each flow can not be read from TopologyEngine
    And all active switches have correct rules installed per Northbound validation
    And all active switches have no excessive meters installed
    And each flow has no traffic

  Scenario: Update bandwidth
    Given random flow aliased as 'flow1'
    And change bandwidth of flow1 flow to 1000
    And create flow 'flow1'
    And 'flow1' flow is in UP state
    And get available bandwidth and maximum speed for flow flow1 and alias them as 'flow1_available_bw' and 'flow1_speed' respectively
    And get path of 'flow1' and alias it as 'flow1path'

    When change bandwidth of flow1 flow to 'flow1_speed'
    And update flow flow1
    Then 'flow1' flow is in UP state
    When get info about flow flow1
    Then response flow has bandwidth equal to 'flow1_speed'
    And flow1 flow's path equals to 'flow1path'

    When change bandwidth of flow1 flow to 1000
    And update flow flow1
    Then 'flow1' flow is in UP state
    When get info about flow flow1
    Then response flow has bandwidth equal to 1000
    And flow1 flow's path equals to 'flow1path'

    And delete flow flow1
