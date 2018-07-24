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

    Then flow nbc creation request with de:ad:be:ef:00:00:00:03 11 100 and de:ad:be:ef:00:00:00:05 12 100 and 10000 is successful

  @MVP1
  Scenario: Flow Reading

  This scenario setups flows across the entire set of switches and checks that response was successful

    When flow nbr creation request with de:ad:be:ef:00:00:00:03 11 101 and de:ad:be:ef:00:00:00:05 12 101 and 10000 is successful
    Then flow nbr with de:ad:be:ef:00:00:00:03 11 101 and de:ad:be:ef:00:00:00:05 12 101 and 10000 could be read

  @MVP1
  Scenario: Flow Updating

  This scenario setups flows across the entire set of switches, then updates them and checks that response was successful

    When flow nbu creation request with de:ad:be:ef:00:00:00:03 11 102 and de:ad:be:ef:00:00:00:05 12 102 and 10000 is successful
    Then flow nbu with de:ad:be:ef:00:00:00:03 11 102 and de:ad:be:ef:00:00:00:05 12 102 and 10000 could be updated with 20000

  @MVP1
  Scenario: Flow Deletion

  This scenario setups flows across the entire set of switches, then deletes them and checks that response was successful

    When flow nbd creation request with de:ad:be:ef:00:00:00:03 11 103 and de:ad:be:ef:00:00:00:05 12 103 and 10000 is successful
    Then flow nbd with de:ad:be:ef:00:00:00:03 11 103 and de:ad:be:ef:00:00:00:05 12 103 and 10000 could be created
    Then flow nbd with de:ad:be:ef:00:00:00:03 11 103 and de:ad:be:ef:00:00:00:05 12 103 and 10000 could be deleted

  @MVP1
  Scenario: Flow Path

  This scenario setups flows across the entire set of switches and checks that these flows could be read from database

    When flow nbp creation request with de:ad:be:ef:00:00:00:03 11 104 and de:ad:be:ef:00:00:00:05 12 104 and 10000 is successful
    Then path of flow nbp could be read

  @MVP1
  Scenario: Flow Status

  This scenario setups flows across the entire set of switches and checks that these flows could be read from database

    When flow nbs creation request with de:ad:be:ef:00:00:00:03 11 105 and de:ad:be:ef:00:00:00:05 12 105 and 10000 is successful
    Then status of flow nbs could be read

  @MVP1
  Scenario: Dump flows

  This scenario setups flows across the entire set of switches and checks that these flows could be read from database

    Then flows dump contains 5 flows

  @MVP1
  Scenario: Delete non-default rules from a switch

  This scenario setups a flow through a switch, deletes non-default rules from the switch and checks that the traffic is not pingable

    Given 8000000000000001,8000000000000002,8000000000000003 rules are installed on de:ad:be:ef:00:00:00:03 switch

    When flow nbdnr creation request with de:ad:be:ef:00:00:00:02 11 106 and de:ad:be:ef:00:00:00:03 12 106 and 1000 is successful
    And flow nbdnr in UP state
    And traffic through de:ad:be:ef:00:00:00:02 11 106 and de:ad:be:ef:00:00:00:03 12 106 and 1000 is pingable

    Then delete all non-default rules request on de:ad:be:ef:00:00:00:03 switch is successful with 2 rules deleted
    And traffic through de:ad:be:ef:00:00:00:02 11 106 and de:ad:be:ef:00:00:00:03 12 106 and 1000 is not pingable
    And 8000000000000001,8000000000000002,8000000000000003 rules are installed on de:ad:be:ef:00:00:00:03 switch

  @MVP1
  Scenario: Delete all rules from a switch

  This scenario setups a flow through a switch, deletes all rules from the switch and checks that the traffic is not pingable

    Given 8000000000000001,8000000000000002,8000000000000003 rules are installed on de:ad:be:ef:00:00:00:03 switch

    When flow nbdar creation request with de:ad:be:ef:00:00:00:02 11 107 and de:ad:be:ef:00:00:00:03 12 107 and 1000 is successful
    And flow nbdar in UP state
    And traffic through de:ad:be:ef:00:00:00:02 11 107 and de:ad:be:ef:00:00:00:03 12 107 and 1000 is pingable

    Then delete all rules request on de:ad:be:ef:00:00:00:03 switch is successful with 5 rules deleted
    And traffic through de:ad:be:ef:00:00:00:02 11 107 and de:ad:be:ef:00:00:00:03 12 107 and 1000 is not pingable
    And No rules installed on de:ad:be:ef:00:00:00:03 switch

  @MVP1
  Scenario: Delete rules by in-port from a switch

  This scenario setups a flow through a switch, deletes the specific rule by in-port from the switch and checks that the traffic is not pingable

    Given 8000000000000001,8000000000000002,8000000000000003 rules are installed on de:ad:be:ef:00:00:00:02 switch

    When flow nbdrip creation request with de:ad:be:ef:00:00:00:02 11 116 and de:ad:be:ef:00:00:00:03 12 116 and 1000 is successful
    And flow nbdrip in UP state
    And traffic through de:ad:be:ef:00:00:00:02 11 116 and de:ad:be:ef:00:00:00:03 12 116 and 1000 is pingable

    Then delete rules request by 1 in-port on de:ad:be:ef:00:00:00:02 switch is successful with 1 rules deleted
    And traffic through de:ad:be:ef:00:00:00:02 11 116 and de:ad:be:ef:00:00:00:03 12 116 and 1000 is not pingable
    And 8000000000000001,8000000000000002,8000000000000003 rules are installed on de:ad:be:ef:00:00:00:02 switch

  @MVP1
  Scenario: Delete rules by in-port from a switch (negative case)

  This scenario setups a flow through a switch, tries to delete by wrong in-port from the switch and checks that the traffic is not pingable

    Given 8000000000000001,8000000000000002,8000000000000003 rules are installed on de:ad:be:ef:00:00:00:02 switch

    When flow nbdripn creation request with de:ad:be:ef:00:00:00:02 11 121 and de:ad:be:ef:00:00:00:03 12 121 and 1000 is successful
    And flow nbdripn in UP state
    And traffic through de:ad:be:ef:00:00:00:02 11 121 and de:ad:be:ef:00:00:00:03 12 121 and 1000 is pingable

    Then delete rules request by 10 in-port on de:ad:be:ef:00:00:00:02 switch is successful with 0 rules deleted
    And traffic through de:ad:be:ef:00:00:00:02 11 121 and de:ad:be:ef:00:00:00:03 12 121 and 1000 is pingable
    And 8000000000000001,8000000000000002,8000000000000003 rules are installed on de:ad:be:ef:00:00:00:02 switch

  @MVP1
  Scenario: Delete rules by in-port and vlan from a switch

  This scenario setups a flow through a switch, deletes the specific rule by in-port and vlan from the switch and checks that the traffic is not pingable

    Given 8000000000000001,8000000000000002,8000000000000003 rules are installed on de:ad:be:ef:00:00:00:02 switch

    When flow nbdripv creation request with de:ad:be:ef:00:00:00:02 11 117 and de:ad:be:ef:00:00:00:03 12 117 and 1000 is successful
    And flow nbdripv in UP state
    And traffic through de:ad:be:ef:00:00:00:02 11 117 and de:ad:be:ef:00:00:00:03 12 117 and 1000 is pingable

    Then delete rules request by 1 in-port and 117 in-vlan on de:ad:be:ef:00:00:00:02 switch is successful with 1 rules deleted
    And traffic through de:ad:be:ef:00:00:00:02 11 117 and de:ad:be:ef:00:00:00:03 12 117 and 1000 is not pingable
    And 8000000000000001,8000000000000002,8000000000000003 rules are installed on de:ad:be:ef:00:00:00:02 switch

  @MVP1
  Scenario: Delete rules by in-port and vlan from a switch (negative case)

  This scenario setups a flow through a switch, deletes the specific rule by in-port and wrong vlan from the switch and checks that the traffic is not pingable

    Given 8000000000000001,8000000000000002,8000000000000003 rules are installed on de:ad:be:ef:00:00:00:02 switch

    When flow nbdripvn creation request with de:ad:be:ef:00:00:00:02 11 122 and de:ad:be:ef:00:00:00:03 12 122 and 1000 is successful
    And flow nbdripvn in UP state
    And traffic through de:ad:be:ef:00:00:00:02 11 122 and de:ad:be:ef:00:00:00:03 12 122 and 1000 is pingable

    Then delete rules request by 1 in-port and 200 in-vlan on de:ad:be:ef:00:00:00:02 switch is successful with 0 rules deleted
    And traffic through de:ad:be:ef:00:00:00:02 11 122 and de:ad:be:ef:00:00:00:03 12 122 and 1000 is pingable
    And 8000000000000001,8000000000000002,8000000000000003 rules are installed on de:ad:be:ef:00:00:00:02 switch

  @MVP1
  Scenario: Delete rules by in-vlan from a switch

  This scenario setups a flow through a switch, deletes the specific rule by in-port from the switch and checks that the traffic is not pingable

    Given 8000000000000001,8000000000000002,8000000000000003 rules are installed on de:ad:be:ef:00:00:00:02 switch

    When flow nbdriv creation request with de:ad:be:ef:00:00:00:02 11 119 and de:ad:be:ef:00:00:00:03 12 119 and 1000 is successful
    And flow nbdriv in UP state
    And traffic through de:ad:be:ef:00:00:00:02 11 119 and de:ad:be:ef:00:00:00:03 12 119 and 1000 is pingable

    Then delete rules request by 119 in-vlan on de:ad:be:ef:00:00:00:02 switch is successful with 1 rules deleted
    And traffic through de:ad:be:ef:00:00:00:02 11 119 and de:ad:be:ef:00:00:00:03 12 119 and 1000 is not pingable
    And 8000000000000001,8000000000000002,8000000000000003 rules are installed on de:ad:be:ef:00:00:00:02 switch

  @MVP1
  Scenario: Delete rules by out-port from a switch

  This scenario setups a flow through a switch, deletes the specific rule by in-port from the switch and checks that the traffic is not pingable

    Given 8000000000000001,8000000000000002,8000000000000003 rules are installed on de:ad:be:ef:00:00:00:03 switch

    When flow nbdrop creation request with de:ad:be:ef:00:00:00:02 11 118 and de:ad:be:ef:00:00:00:03 12 118 and 1000 is successful
    And flow nbdrop in UP state
    And traffic through de:ad:be:ef:00:00:00:02 11 118 and de:ad:be:ef:00:00:00:03 12 118 and 1000 is pingable

    Then delete rules request by 2 out-port on de:ad:be:ef:00:00:00:03 switch is successful with 1 rules deleted
    And traffic through de:ad:be:ef:00:00:00:02 11 118 and de:ad:be:ef:00:00:00:03 12 118 and 1000 is not pingable
    And 8000000000000001,8000000000000002,8000000000000003 rules are installed on de:ad:be:ef:00:00:00:03 switch

  @MVP1
  Scenario: Synchronize Flow Cache

  This scenario setups flows through NB, then deletes from DB and perform synchronization of the flow cache

    Given a clean flow topology
    And flow sfc1 creation request with de:ad:be:ef:00:00:00:03 11 108 and de:ad:be:ef:00:00:00:05 12 108 and 10000 is successful
    And flow sfc2 creation request with de:ad:be:ef:00:00:00:04 11 108 and de:ad:be:ef:00:00:00:06 12 108 and 10000 is successful
    And flows dump contains 2 flows

    When flow sfc1 could be deleted from DB
    And flows dump contains 2 flows

    Then synchronize flow cache is successful with 1 dropped flows
    And flows dump contains 1 flows

  @MVP1
  Scenario: Invalidate Flow Cache

  This scenario setups flows through NB, then deletes a flow from DB and performs invalidation of the flow cache

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
  Scenario: Validate flow rules with missing rules on intermediate switch

  This scenario setups a flow through NB, then deletes rules from an intermediate switch and performs flow validation check

    Given a clean flow topology
    And flow vfris creation request with de:ad:be:ef:00:00:00:01 11 110 and de:ad:be:ef:00:00:00:04 12 110 and 1000 is successful
    And flow vfris in UP state
    And validation of flow vfris is successful with no discrepancies

    When delete all non-default rules request on de:ad:be:ef:00:00:00:03 switch is successful with 2 rules deleted

    Then validation of flow vfris has completed with 2 discrepancies on de:ad:be:ef:00:00:00:03 switches found

  @MVP1
  Scenario: Validate flow rules with missing rules on ingress switch

  This scenario setups a flow through NB, then deletes rules from an ingress switch and performs flow validation check

    Given a clean flow topology
    And flow vfrins creation request with de:ad:be:ef:00:00:00:01 11 110 and de:ad:be:ef:00:00:00:04 12 110 and 1000 is successful
    And flow vfrins in UP state
    And validation of flow vfrins is successful with no discrepancies

    When delete all non-default rules request on de:ad:be:ef:00:00:00:01 switch is successful with 2 rules deleted

    Then validation of flow vfrins has completed with 2 discrepancies on de:ad:be:ef:00:00:00:01 switches found

  @MVP1
  Scenario: Validate flow rules with missing rules on egress switch

  This scenario setups a flow through NB, then deletes rules from an egress switch and performs flow validation check

    Given a clean flow topology
    And flow vfres creation request with de:ad:be:ef:00:00:00:01 11 110 and de:ad:be:ef:00:00:00:04 12 110 and 1000 is successful
    And flow vfres in UP state
    And validation of flow vfres is successful with no discrepancies

    When delete all non-default rules request on de:ad:be:ef:00:00:00:04 switch is successful with 2 rules deleted

    Then validation of flow vfres has completed with 2 discrepancies on de:ad:be:ef:00:00:00:04 switches found

  @MVP1
  Scenario: Validate flow rules with missing non-standard cookies on intermediate switch

  This scenario pushes a flow with non-standard cookies through NB, then deletes rules from an intermediate switch and performs flow validation check

    Given a clean flow topology
    And flow vfrnscis push request for /flows/non-standard-cookies-flow.json is successful
    And flow vfrnscis in UP state
    And validation of flow vfrnscis is successful with no discrepancies

    When delete all non-default rules request on de:ad:be:ef:00:00:00:02 switch is successful with 2 rules deleted

    Then validation of flow vfrnscis has completed with 2 discrepancies on de:ad:be:ef:00:00:00:02 switches found

  @MVP1
  Scenario: Validate flow rules with missing non-standard cookies on ingress switch

  This scenario pushes a flow with non-standard cookies through NB, then deletes rules from an ingress switch and performs flow validation check

    Given a clean flow topology
    And flow vfrnscins push request for /flows/non-standard-cookies-flow.json is successful
    And flow vfrnscins in UP state
    And validation of flow vfrnscins is successful with no discrepancies

    When delete all non-default rules request on de:ad:be:ef:00:00:00:01 switch is successful with 2 rules deleted

    Then validation of flow vfrnscins has completed with 2 discrepancies on de:ad:be:ef:00:00:00:01 switches found

  @MVP1
  Scenario: Validate flow rules with missing non-standard cookies on egress switch

  This scenario pushes a flow with non-standard cookies through NB, then deletes rules from an egress switch and performs flow validation check

    Given a clean flow topology
    And flow vfrnsces push request for /flows/non-standard-cookies-flow.json is successful
    And flow vfrnsces in UP state
    And validation of flow vfrnsces is successful with no discrepancies

    When delete all non-default rules request on de:ad:be:ef:00:00:00:03 switch is successful with 2 rules deleted

    Then validation of flow vfrnsces has completed with 2 discrepancies on de:ad:be:ef:00:00:00:03 switches found

  @MVP1
  Scenario: Validate intermediate switch with missing rules

  This scenario setups a flow through NB, then deletes rules from an intermediate switch and performs switch validation check

    Given a clean flow topology
    And flow vismr creation request with de:ad:be:ef:00:00:00:01 1 111 and de:ad:be:ef:00:00:00:04 2 111 and 1000 is successful
    And flow vismr in UP state
    And validation of rules on de:ad:be:ef:00:00:00:03 switch is successful with no discrepancies

    When delete all non-default rules request on de:ad:be:ef:00:00:00:03 switch is successful with 2 rules deleted

    Then validation of rules on de:ad:be:ef:00:00:00:03 switch has passed and 2 rules are missing

  @MVP1
  Scenario: Validate ingress switch with missing rules

  This scenario setups a flow through NB, then deletes rules from an ingress switch and performs switch validation check

    Given a clean flow topology
    And flow vinsmr creation request with de:ad:be:ef:00:00:00:01 11 123 and de:ad:be:ef:00:00:00:04 12 123 and 1000 is successful
    And flow vinsmr in UP state
    And validation of rules on de:ad:be:ef:00:00:00:01 switch is successful with no discrepancies

    When delete all non-default rules request on de:ad:be:ef:00:00:00:01 switch is successful with 2 rules deleted

    Then validation of rules on de:ad:be:ef:00:00:00:01 switch has passed and 2 rules are missing

  @MVP1
  Scenario: Validate egress switch with missing rules

  This scenario setups a flow through NB, then deletes rules from an egress switch and performs switch validation check

    Given a clean flow topology
    And flow vesmr creation request with de:ad:be:ef:00:00:00:01 11 124 and de:ad:be:ef:00:00:00:04 12 124 and 1000 is successful
    And flow vesmr in UP state
    And validation of rules on de:ad:be:ef:00:00:00:04 switch is successful with no discrepancies

    When delete all non-default rules request on de:ad:be:ef:00:00:00:04 switch is successful with 2 rules deleted

    Then validation of rules on de:ad:be:ef:00:00:00:04 switch has passed and 2 rules are missing

  @MVP1
  Scenario: Validate intermediate switch with non-standard cookies

  This scenario pushes a flow with non-standard cookies through NB, then deletes rules from an intermediate switch and performs switch validation check

    Given a clean flow topology
    And flow visnsc push request for /flows/non-standard-cookies-flow.json is successful
    And flow visnsc in UP state
    And validation of rules on de:ad:be:ef:00:00:00:02 switch is successful with no discrepancies

    When delete all non-default rules request on de:ad:be:ef:00:00:00:02 switch is successful with 2 rules deleted

    Then validation of rules on de:ad:be:ef:00:00:00:02 switch has passed and 2 rules are missing

  @MVP1
  Scenario: Validate ingress switch with non-standard cookies

  This scenario pushes a flow with non-standard cookies through NB, then deletes rules from an ingress switch and performs switch validation check

    Given a clean flow topology
    And flow vinsnsc push request for /flows/non-standard-cookies-flow.json is successful
    And flow vinsnsc in UP state
    And validation of rules on de:ad:be:ef:00:00:00:01 switch is successful with no discrepancies

    When delete all non-default rules request on de:ad:be:ef:00:00:00:01 switch is successful with 2 rules deleted

    Then validation of rules on de:ad:be:ef:00:00:00:01 switch has passed and 2 rules are missing

  @MVP1
  Scenario: Validate egress switch with non-standard cookies

  This scenario pushes a flow with non-standard cookies through NB, then deletes rules from an egress switch and performs switch validation check

    Given a clean flow topology
    And flow vesnsc push request for /flows/non-standard-cookies-flow.json is successful
    And flow vesnsc in UP state
    And validation of rules on de:ad:be:ef:00:00:00:03 switch is successful with no discrepancies

    When delete all non-default rules request on de:ad:be:ef:00:00:00:03 switch is successful with 2 rules deleted

    Then validation of rules on de:ad:be:ef:00:00:00:03 switch has passed and 2 rules are missing

  @MVP1
  Scenario: Synchronize switch rules

  This scenario setups a flow through NB, then deletes rules from an intermediate switch and performs switch synchronization

    Given a clean flow topology
    And flow ssr creation request with de:ad:be:ef:00:00:00:01 11 113 and de:ad:be:ef:00:00:00:04 12 113 and 1000 is successful
    And flow ssr in UP state
    And validation of rules on de:ad:be:ef:00:00:00:03 switch is successful with no discrepancies

    When delete all non-default rules request on de:ad:be:ef:00:00:00:03 switch is successful with 2 rules deleted

    Then synchronization of rules on de:ad:be:ef:00:00:00:03 switch is successful with 2 rules installed
    And validation of rules on de:ad:be:ef:00:00:00:03 switch is successful with no discrepancies

  @MVP1
  Scenario: Synchronize switch rules with non-standard cookies

  This scenario pushes a flow with non-standard cookies through NB, then deletes rules from an intermediate switch aand performs switch synchronization

    Given a clean flow topology

    And flow ssnsc push request for /flows/non-standard-cookies-flow.json is successful
    And flow ssnsc in UP state
    And validation of rules on de:ad:be:ef:00:00:00:03 switch is successful with no discrepancies

    When delete all non-default rules request on de:ad:be:ef:00:00:00:03 switch is successful with 2 rules deleted

    Then synchronization of rules on de:ad:be:ef:00:00:00:03 switch is successful with 2 rules installed
    And validation of rules on de:ad:be:ef:00:00:00:03 switch is successful with no discrepancies
