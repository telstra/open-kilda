@Flow
Feature: Flow CRUD
  This feature tests flow CRUD operations.

  Background:
    Given the reference topology
    And all defined switches are discovered
    And all defined links are detected
#    And a clean topology with no flows and no discrepancies

  @CRUD
  Scenario: Create, read, update and delete flows across the entire set of defined switches

    Given flows defined over active switches in the reference topology
    And each flow has unique flow_id
    And each flow has max bandwidth set to 10000

    When initialize creation of given flows

    Then each flow is created and stored in TopologyEngine
    And each flow is in UP state
    And each flow can be read from Northbound
    And each flow is valid per Northbound validation
    And all active switches have correct rules installed per Northbound validation
    And each flow has meters installed with 10000 max bandwidth
    And each flow has traffic going with bandwidth not less than 10000

    Then each flow can be updated with 5000 max bandwidth
    And each flow is in UP state
    And each flow has meters installed with 5000 max bandwidth
    And each flow has traffic going with bandwidth not less than 5000

    Then each flow can be deleted
    And each flow can not be read from Northbound
    And each flow can not be read from TopologyEngine
    And all active switches have correct rules installed per Northbound validation
    And each flow has no rules installed
    And all active switches have no excessive meters installed
    And each flow has no traffic
