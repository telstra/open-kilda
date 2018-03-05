Feature: Flow CRUD
  This feature tests flow CRUD operations.

  Background:
    Given a reference topology

  Scenario: Create, read, update and delete flows across the entire set of switches

    Given flows among all switches
    And each flow has unique flow_id
    And each flow has 20 and 21 ports and unique vlan (not less than 1)
    And each flow has bandwidth set to 10000

    When creation request for each flow is successful

    Then each flow is created and stored in TopologyEngine
    And each flow is in UP state
    And each flow can be read from Northbound
    And each flow has rules installed
    And traffic via each flow is pingable

    Then each flow can be updated with 5000 bandwidth
    And each flow is in UP state
    And each flow has rules installed with 5000 bandwidth
    And traffic via each flow is pingable

    Then each flow can be deleted
    And each flow can not be read from Northbound
    And traffic via each flow is not pingable
    And each flow can not be read from TopologyEngine
