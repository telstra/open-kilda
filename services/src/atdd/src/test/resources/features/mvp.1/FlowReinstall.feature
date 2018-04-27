@FREINSTALL
Feature: Flow re-reinstalling after switch comes back up.

  @MVP2
  Scenario: Re-installing Ingress and Egress flows.

  This scenario creates the simple network(switch1 linked with switch2) and ingress/egress flows. Next this scenario
  turns off switch1 and turns it on back, then checks whether flows were re-installed again .

    Given a clean flow topology
    And a clean controller
    And created simple topology from two switches
    And topology contains 2 links
    And flow pcet creation request with 00:01:00:00:00:00:00:01 1 0 and 00:01:00:00:00:00:00:02 2 0 and 1000000 is successful
    And flow pcet in UP state

    When switch switch1 is turned off
    Then flow pcet in DOWN state

    When switch switch1 is turned on
    Then flow pcet in UP state

  @MVP2
  Scenario: Re-installing transit flow when no other path is available.

  This scenario creates linear topology of three switches(00000001, 00000002, 00000003), links between them
  and builds flow through target switch. The medium (00000002) switch will be turned off and on,
  then we check whether flows were re-installed on the this switch after come back to UP state.

    Given a clean flow topology
    And a clean controller
    And a random linear topology of 3 switches
    And topology contains 8 links
    When flow pcet creation request with de:ad:be:ef:00:00:00:01 2 0 and de:ad:be:ef:00:00:00:03 2 0 and 100 is successful
    And flow pcet in UP state

    When switch 00000002 is turned off
    Then flow pcet in DOWN state

    When switch 00000002 is turned on
    Then flow pcet in UP state

  @MVP2
  Scenario: Re-installing transit flow when no other path is available and reflow feature is turned off.

  This scenario creates linear topology of three switches(00000001, 00000002, 00000003), and create from from switch
  00000001 to 00000003. There is only 1 possible path for this flow. So when when switch 00000002 is turned off, flow
  should switch to DOWN state. When switch 00000002 will be recovered, flow must stay in DOWN state due to disabled
  flows reroute on switch activation.

    Given a clean flow topology
    And a clean controller
    And a random linear topology of 3 switches
    And topology contains 8 links
    When flow disableReflowTest creation request with de:ad:be:ef:00:00:00:01 2 0 and de:ad:be:ef:00:00:00:03 2 0 and 100 is successful
    And flow disableReflowTest in UP state

    And flow reroute feature is off
    When switch 00000002 is turned off
    Then flow disableReflowTest in DOWN state

    When switch 00000002 is turned on
    And topology contains 8 links
    Then flow disableReflowTest in DOWN state

  @MVP2
  Scenario: Re-installing transit flow after changing path.

  This scenario creates topology with two paths for flow and creates flow through the switch, which will be turned off.
  After that switch goes up again and flow should be recreated through that switch.

    Given a clean flow topology
    And a clean controller
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
