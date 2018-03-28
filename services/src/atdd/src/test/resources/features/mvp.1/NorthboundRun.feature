@NB
Feature: Northbound tests

  This feature tests all Northbound operations.

  @MVP1
  Scenario: HealthCheck request

  This scenario checks controller HealthCheck status.

    Given health check

  @MVP1
  Scenario: Flow Creation

  This scenario setups flows across the entire set of switches and checks that response was successful

    Then flow nbc creation request with de:ad:be:ef:00:00:00:03 1 0 and de:ad:be:ef:00:00:00:05 2 0 and 10000 is successful

  @MVP1
  Scenario: Flow Reading

  This scenario setups flows across the entire set of switches and checks that response was successful

    When flow nbr creation request with de:ad:be:ef:00:00:00:03 1 0 and de:ad:be:ef:00:00:00:05 2 0 and 10000 is successful
    Then flow nbr with de:ad:be:ef:00:00:00:03 1 0 and de:ad:be:ef:00:00:00:05 2 0 and 10000 could be read

  @MVP1
  Scenario: Flow Updating

  This scenario setups flows across the entire set of switches, then updates them and checks that response was successful

    When flow nbu creation request with de:ad:be:ef:00:00:00:03 1 0 and de:ad:be:ef:00:00:00:05 2 0 and 10000 is successful
    Then flow nbu with de:ad:be:ef:00:00:00:03 1 0 and de:ad:be:ef:00:00:00:05 2 0 and 10000 could be updated with 20000

  @MVP1
  Scenario: Flow Deletion

  This scenario setups flows across the entire set of switches, then deletes them and checks that response was successful

    When flow nbd creation request with de:ad:be:ef:00:00:00:03 1 0 and de:ad:be:ef:00:00:00:05 2 0 and 10000 is successful
    Then flow nbd with de:ad:be:ef:00:00:00:03 1 0 and de:ad:be:ef:00:00:00:05 2 0 and 10000 could be created
    Then flow nbd with de:ad:be:ef:00:00:00:03 1 0 and de:ad:be:ef:00:00:00:05 2 0 and 10000 could be deleted

  @MVP1
  Scenario: Flow Path

  This scenario setups flows across the entire set of switches and checks that these flows could be read from database

    When flow nbp creation request with de:ad:be:ef:00:00:00:03 1 0 and de:ad:be:ef:00:00:00:05 2 0 and 10000 is successful
    Then path of flow nbp could be read

  @MVP1
  Scenario: Flow Status

  This scenario setups flows across the entire set of switches and checks that these flows could be read from database

    When flow nbs creation request with de:ad:be:ef:00:00:00:03 1 0 and de:ad:be:ef:00:00:00:05 2 0 and 10000 is successful
    Then status of flow nbs could be read

  @MVP1
  Scenario: Dump flows

  This scenario setups flows across the entire set of switches and checks that these flows could be read from database

    Then flows dump contains 5 flows

  @MVP1
  Scenario: Delete non-default rules from a switch

  This scenario setups a flow through a switch, deletes non-default rules from the switch and checks that the traffic is not pingable

    Then flow nbdnr creation request with de:ad:be:ef:00:00:00:02 1 0 and de:ad:be:ef:00:00:00:03 2 0 and 1000 is successful
    And flow nbdnr in UP state
    And traffic through de:ad:be:ef:00:00:00:02 1 0 and de:ad:be:ef:00:00:00:03 2 0 and 1000 is pingable

    Then delete all non-default rules on de:ad:be:ef:00:00:00:03 switch
    And traffic through de:ad:be:ef:00:00:00:02 1 0 and de:ad:be:ef:00:00:00:03 2 0 and 1000 is not pingable

  @MVP1
  Scenario: Delete all rules from a switch

  This scenario setups a flow through a switch, deletes all rules from the switch and checks that the traffic is not pingable

    Then flow nbdar creation request with de:ad:be:ef:00:00:00:02 1 0 and de:ad:be:ef:00:00:00:03 2 0 and 1000 is successful
    And flow nbdar in UP state
    And traffic through de:ad:be:ef:00:00:00:02 1 0 and de:ad:be:ef:00:00:00:03 2 0 and 1000 is pingable

    Then delete all rules on de:ad:be:ef:00:00:00:03 switch
    And traffic through de:ad:be:ef:00:00:00:02 1 0 and de:ad:be:ef:00:00:00:03 2 0 and 1000 is not pingable
