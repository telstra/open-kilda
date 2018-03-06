Feature: Flow CRUD
  This feature tests flow CRUD operations.

  Background:
    Given a reference topology

  Scenario: Create, read, update and delete flows across the entire set of switches

    Given flows over all switches
    And each flow has unique flow_id
    And each flow has allocated ports and unique vlan
    And each flow has max bandwidth set to 10000

    When creation request for each flow is successful

    Then each flow is created and stored in TopologyEngine
    And each flow is in UP state
    And each flow can be read from Northbound
    And each flow has rules installed
    And each flow has traffic going with bandwidth not less than 10000

    Then each flow can be updated with 5000 max bandwidth
    And each flow is in UP state
    And each flow has rules installed with 5000 max bandwidth
    And each flow has traffic going with bandwidth not less than 5000

    Then each flow can be deleted
    And each flow can not be read from Northbound
    And each flow can not be read from TopologyEngine
    And each flow has no rules installed
    And each flow has no traffic
