@InDev
Feature: Kafka's heart beat emitted by speaker
  Speaker emmit heart beat notifications to notify abouts its availability.

  @HeartBeat
  Scenario: Generic heart beat signals
    Given a clean controller
    And rewind heart beat kafka position to the end

    When 5 seconds passed
    Then got at least 2 heart beat event
