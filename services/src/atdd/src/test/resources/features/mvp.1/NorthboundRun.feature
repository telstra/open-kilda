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

    Then flow nbc creation request with de:ad:be:ef:00:00:00:03 1 100 and de:ad:be:ef:00:00:00:05 2 100 and 10000 is successful

  @MVP1
  Scenario: Flow Reading

  This scenario setups flows across the entire set of switches and checks that response was successful

    When flow nbr creation request with de:ad:be:ef:00:00:00:03 1 101 and de:ad:be:ef:00:00:00:05 2 101 and 10000 is successful
    Then flow nbr with de:ad:be:ef:00:00:00:03 1 101 and de:ad:be:ef:00:00:00:05 2 101 and 10000 could be read

  @MVP1
  Scenario: Flow Updating

  This scenario setups flows across the entire set of switches, then updates them and checks that response was successful

    When flow nbu creation request with de:ad:be:ef:00:00:00:03 1 102 and de:ad:be:ef:00:00:00:05 2 102 and 10000 is successful
    Then flow nbu with de:ad:be:ef:00:00:00:03 1 102 and de:ad:be:ef:00:00:00:05 2 102 and 10000 could be updated with 20000

  @MVP1
  Scenario: Flow Deletion

  This scenario setups flows across the entire set of switches, then deletes them and checks that response was successful

    When flow nbd creation request with de:ad:be:ef:00:00:00:03 1 103 and de:ad:be:ef:00:00:00:05 2 103 and 10000 is successful
    Then flow nbd with de:ad:be:ef:00:00:00:03 1 103 and de:ad:be:ef:00:00:00:05 2 103 and 10000 could be created
    Then flow nbd with de:ad:be:ef:00:00:00:03 1 103 and de:ad:be:ef:00:00:00:05 2 103 and 10000 could be deleted

  @MVP1
  Scenario: Flow Path

  This scenario setups flows across the entire set of switches and checks that these flows could be read from database

    When flow nbp creation request with de:ad:be:ef:00:00:00:03 1 104 and de:ad:be:ef:00:00:00:05 2 104 and 10000 is successful
    Then path of flow nbp could be read

  @MVP1
  Scenario: Flow Status

  This scenario setups flows across the entire set of switches and checks that these flows could be read from database

    When flow nbs creation request with de:ad:be:ef:00:00:00:03 1 105 and de:ad:be:ef:00:00:00:05 2 105 and 10000 is successful
    Then status of flow nbs could be read

  @MVP1
  Scenario: Dump flows

  This scenario setups flows across the entire set of switches and checks that these flows could be read from database

    Then flows dump contains 5 flows

  @MVP1
  Scenario: Delete non-default rules from a switch

  This scenario setups a flow through a switch, deletes non-default rules from the switch and checks that the traffic is not pingable

    Then flow nbdnr creation request with de:ad:be:ef:00:00:00:02 1 106 and de:ad:be:ef:00:00:00:03 2 106 and 1000 is successful
    And flow nbdnr in UP state
    And traffic through de:ad:be:ef:00:00:00:02 1 106 and de:ad:be:ef:00:00:00:03 2 106 and 1000 is pingable

    Then delete all non-default rules on de:ad:be:ef:00:00:00:03 switch
    And traffic through de:ad:be:ef:00:00:00:02 1 106 and de:ad:be:ef:00:00:00:03 2 106 and 1000 is not pingable

  @MVP1
  Scenario: Delete all rules from a switch

  This scenario setups a flow through a switch, deletes all rules from the switch and checks that the traffic is not pingable

    Then flow nbdar creation request with de:ad:be:ef:00:00:00:02 1 107 and de:ad:be:ef:00:00:00:03 2 107 and 1000 is successful
    And flow nbdar in UP state
    And traffic through de:ad:be:ef:00:00:00:02 1 107 and de:ad:be:ef:00:00:00:03 2 107 and 1000 is pingable

    Then delete all rules on de:ad:be:ef:00:00:00:03 switch
    And traffic through de:ad:be:ef:00:00:00:02 1 107 and de:ad:be:ef:00:00:00:03 2 107 and 1000 is not pingable

  @MVP1
  Scenario: Synchronize Flow Cache

  This scenario setups flows through NB, then deletes from DB and perform synchronization of the flow cache

    Given a clean flow topology
    And flow sfc1 creation request with de:ad:be:ef:00:00:00:03 1 108 and de:ad:be:ef:00:00:00:05 2 108 and 10000 is successful
    And flow sfc2 creation request with de:ad:be:ef:00:00:00:04 1 108 and de:ad:be:ef:00:00:00:06 2 108 and 10000 is successful
    And flows dump contains 2 flows

    When flow sfc1 could be deleted from DB
    And flows dump contains 2 flows

    Then synchronize flow cache is successful with 1 dropped flows
    And flows dump contains 1 flows

  @MVP1
  Scenario: Invalidate Flow Cache

  This scenario setups flows through NB, then delete a flow from DB and perform invalidation of the flow cache

    Given a clean flow topology
    And flow ifc1 creation request with de:ad:be:ef:00:00:00:03 1 109 and de:ad:be:ef:00:00:00:05 2 109 and 10000 is successful
    And flow ifc2 creation request with de:ad:be:ef:00:00:00:04 1 109 and de:ad:be:ef:00:00:00:06 2 109 and 10000 is successful
    And flow ifc3 creation request with de:ad:be:ef:00:00:00:05 1 109 and de:ad:be:ef:00:00:00:07 2 109 and 10000 is successful
    And flows dump contains 3 flows

    When flow ifc2 could be deleted from DB
    And flows dump contains 3 flows

    Then invalidate flow cache is successful with 1 dropped flows
    And flows dump contains 2 flows

  @MVP1
  Scenario: Validate flow rules

  This scenario setups a flow through NB, then delete rules from an intermediate switch and perform flow validation check

    Given a clean flow topology
    And flow vfr creation request with de:ad:be:ef:00:00:00:01 1 110 and de:ad:be:ef:00:00:00:04 2 110 and 1000 is successful
    And flow vfr in UP state
    And rules for flow vfr are installed with no discrepancies

    When delete all non-default rules on de:ad:be:ef:00:00:00:03 switch

    Then rules for flow vfr have discrepancies
