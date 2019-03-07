@Northbound
Feature: Northbound smoke tests

  This feature verifies that basic Northbound endpoints are working as expected

  @HealthCheck
  Scenario: HealthCheck endpoint
    When request Northbound health check
    Then all health check components are operational

  @Switches
  Scenario: Get all available switches
    When request all available switches from Northbound
    Then response has at least 2 switches

  @Switches
  Scenario: Get switch rules
    Given select a switch and alias it as 'switch1'

    When request all switch rules for switch 'switch1'
    Then response switch_id matches id of 'switch1'
    And response has at least 1 rule installed

  @Links
  Scenario: Get all links
    When request all available links from Northbound
    Then response has at least 2 links

  @Links
  Scenario: Create, read, update and delete link properties
    Given select a random ISL and alias it as 'isl1'

    When create link properties request for ISL 'isl1'
    And update request: add link property 'cost' with value '100'
    And send update link properties request
    Then response has 0 failures and 1 success

    When get all properties
    Then response has link properties from request
    And response link properties from request has property 'cost' with value '100'

    When update request: add link property 'cost' with value '101'
    And send update link properties request
    Then response has 0 failures and 1 success

    When get all properties
    Then response has link properties from request
    And response link properties from request has property 'cost' with value '101'

    When send delete link properties request
    Then response has 0 failures and 1 success

    When get all properties
    Then response has no link properties from request
    And link props response has 0 results

  @Links
  Scenario: Search link properties
    Given select a random ISL and alias it as 'isl1'
    And create link properties request for ISL 'isl1'
    And update request: change src_switch to '00:00:b0:d2:f5:00:5a:b3'
    And update request: change src_port to '888'
    And send update link properties request
    And update request: change src_port to '999'
    And send update link properties request

    When create empty link properties request
    And update request: change src_switch to '00:00:b0:d2:f5:00:5a:b3'
    And get link properties for defined request
    Then link props response has 2 results

    When create empty link properties request
    And update request: change src_port to '888'
    And get link properties for defined request
    Then link props response has 1 result

    When create empty link properties request
    And update request: change src_switch to '00:00:b0:d2:f5:00:5a:b3'
    And update request: change src_port to '999'
    And get link properties for defined request
    Then link props response has 1 result
    And delete all link properties

  @Flows
  Scenario: Create, read, update and delete flows across the entire set of defined switches
    Given the reference topology
    And flows defined over active switches in the reference topology
    And each flow has unique flow_id
    And each flow has max bandwidth set to 750

    When initialize creation of given flows

    Then each flow is in UP state
    And each flow can be read from Northbound
    And each flow is valid per Northbound validation
    And all active switches have correct rules installed per Northbound validation
    And each flow has meters installed with 750 max bandwidth

    Then each flow can be updated with 1250 max bandwidth and new vlan
    And each flow is in UP state
    And each flow is valid per Northbound validation
    And all active switches have correct rules installed per Northbound validation
    And each flow has meters installed with 1250 max bandwidth

    Then each flow can be deleted
    And each flow can not be read from Northbound
    And all active switches have correct rules installed per Northbound validation
    And all active switches have no excessive meters installed

  @Flows
  Scenario: Update flow bandwidth
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
