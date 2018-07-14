@Northbound
Feature: Northbound endpoints
  This feature verifies that Northbound endpoints are working as expected

  Scenario: HealthCheck endpoint
    When request Northbound health check
    Then all healthcheck components are operational

  @Switches
  Scenario: Get all available switches
    When request all available switches from Northbound
    Then response has at least 2 switches

  @Switches
  Scenario: Get switch rules
    Given select a random switch and alias it as 'switch1'
    When request all switch rules for switch 'switch1'
    Then response switch_id matches id of 'switch1'
    And response has at least 1 rule installed

  @Switches
  Scenario: Remove meter on switch
    Given select a random switch and alias it as 'srcSwitch'
    And select a random switch and alias it as 'dstSwitch'
    And create flow between 'srcSwitch' and 'dstSwitch' and alias it as 'flow1'
    And 'flow1' flow is in UP state
    When request all switch meters for switch 'srcSwitch' and alias results as 'srcSwitchMeters'
    And select first meter of 'srcSwitchMeters' and alias it as 'meterToDelete'
    And remove 'meterToDelete' from 'srcSwitch'
    Then remove meter response is successful

    When request all switch meters for switch 'srcSwitch' and alias results as 'srcSwitchMeters'
    Then meters 'srcSwitchMeters' does not have 'meterToDelete'


  @Links
  Scenario: Get all links
    When request all available links from Northbound
    Then response has at least 2 links

  @FeatureToggles
  Scenario: Get and update feature toggles
    When get all feature toggles
    And create feature toggles request based on the response
    And update request: switch each toggle to an opposite state
    And send update request to feature toggles
    And get all feature toggles
    Then feature toggles response matches request

    When create feature toggles request based on the response
    And update request: switch each toggle to an opposite state
    And send update request to feature toggles

  @Links
  Scenario: CRUD link properties
    Given select a random isl and alias it as 'isl1'

    When create link properties request for ISL 'isl1'
    And update request: add link property 'test_property' with value 'test value'
    And send update link properties request
    Then response has 0 failures and 1 success

    When get all properties
    Then response has link properties from request
    And response link properties from request has property 'test_property' with value 'test value'

    When update request: add link property 'test_property' with value 'test value updated'
    And send update link properties request
    Then response has 0 failures and 1 success
    When get all properties
    Then response has link properties from request
    And response link properties from request has property 'test_property' with value 'test value updated'

    When send delete link properties request
    Then response has 0 failures and 1 success

    When get all properties
    Then response has no link properties from request
    And link props response has 0 results

  @Links
  Scenario: Search link properties
    Given select a random isl and alias it as 'isl1'
    And create link properties request for ISL 'isl1'
    And update request: change src_switch to 'link search test'
    And update request: change src_port to '888'
    And send update link properties request
    And update request: change src_port to '999'
    And send update link properties request

    When create empty link properties request
    And update request: change src_switch to 'link search test'
    And get link properties for defined request
    Then link props response has 2 results

    When create empty link properties request
    And update request: change src_port to '888'
    And get link properties for defined request
    Then link props response has 1 result

    When create empty link properties request
    And update request: change src_switch to 'link search test'
    And update request: change src_port to '999'
    And get link properties for defined request
    Then link props response has 1 result

    And delete all link properties
    