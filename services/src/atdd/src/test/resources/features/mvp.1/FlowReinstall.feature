@FREINSTALL
Feature: Flow re-reinstalling after switch comes back up.

  @MVP1.1
  Scenario: Re-installing Ingress and Egress flows.

  This scenario creates simple network and ingress/egress flows. Next this scenario turns off and on back, then checks whether
  flows were re-installed again.

    Given a clean flow topology
    And a clean controller
    And a clean flow topology
    And created simple topology from two switches
    And topology contains 2 links
    And flow pcet creation request with 00:01:00:00:00:00:00:01 1 0 and 00:01:00:00:00:00:00:02 2 0 and 1000000 is successful
    And flow pcet in UP state

    When switch switch1 is turned off
    Then flow pcet in DOWN state

    When switch switch1 is turned on
    Then flow pcet in UP state

  @MVP1.1
  Scenario: Re-installing transit flow when no other path is available.

  This scenario created topology and builds flow through target switch. This switch will be turned off and on,
  then we check whether is re-installed on the target switch after come back to UP state.

    Given a clean flow topology
    And a clean controller
    And a clean flow topology
    And a random linear topology of 3 switches
    And topology contains 8 links
    When flow pcet creation request with de:ad:be:ef:00:00:00:01 2 0 and de:ad:be:ef:00:00:00:03 2 0 and 100 is successful
    And flow pcet in UP state

    When switch 00000001 is turned off
    Then flow pcet in DOWN state

    When switch 00000001 is turned on
    Then flow pcet in UP state

  @MVP1.1
  Scenario: Re-installing transit flow after changing path.

  This scenario creates topology with two paths for flow and creates flow through the switch, which will be turned off.
  After that switch goes up again and flow should be recreated through that switch.

    Given a clean flow topology
    And a clean controller
    And a clean flow topology
    And a multi-path topology
    And topology contains 16 links
    And flow fr_multipath creation request with 00:00:00:00:00:00:00:01 1 0 and 00:00:00:00:00:00:00:08 1 0 and 100 is successful
    And flow fr_multipath is built through 00:00:00:00:00:00:00:03 switch

    When switch s3 is turned off
    Then flow fr_multipath in UP state
    And flow fr_multipath is not built through 00:00:00:00:00:00:00:03 switch

    When switch s3 is turned on
    Then flow fr_multipath in UP state
    And flow fr_multipath is built through 00:00:00:00:00:00:00:03 switch
