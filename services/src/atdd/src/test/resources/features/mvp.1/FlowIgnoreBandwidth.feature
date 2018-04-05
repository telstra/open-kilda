@MVP1
Feature: Flow can be marked as flow that ignore available ISL bandwidth.

  Such flow's paths must be computed without consideration of available ISL's bandwidth. Also this flow's bandwidth
  shouldn't be included in ISL's allocated bandwidth on flow creation and deallocated on flow deletion.

  @TestTest
  Scenario: create flow ignore ISL's bandwidth
    Given a clean controller
    And a nonrandom linear topology of 7 switches
    And topology contains 12 links
#    And available ISL's bandwidths between de:ad:be:ef:00:00:00:02 and de:ad:be:ef:00:00:00:03 is 9000000

    When flow ignore bandwidth between de:ad:be:ef:00:00:00:02 and de:ad:be:ef:00:00:00:03 with 1000000 bandwidth is created
#    Then available ISL's bandwidths between de:ad:be:ef:00:00:00:02 and de:ad:be:ef:00:00:00:03 is 9000000
    Then flow between de:ad:be:ef:00:00:00:02 and de:ad:be:ef:00:00:00:03 have ignore_bandwidth flag
    Then flow between de:ad:be:ef:00:00:00:02 and de:ad:be:ef:00:00:00:03 have ignore_bandwidth flag in TE

    When drop created flow between de:ad:be:ef:00:00:00:02 and de:ad:be:ef:00:00:00:03
#    Then available ISL's bandwidths between de:ad:be:ef:00:00:00:02 and de:ad:be:ef:00:00:00:03 is 9000000
