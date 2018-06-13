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
