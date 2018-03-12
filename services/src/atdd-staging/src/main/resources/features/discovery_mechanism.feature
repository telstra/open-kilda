Feature: Discovery Tests

  This feature tests basic discovery mechanism that should find all required switches/links.
  We compare the granted topology of switches and links with the list of switches that were discovered by Kilda.
  Also there is a verification steps that check if floodlight detects more specified items than expected.

  Scenario: Test discovery mechanism

    Then all provided switches should be discovered
    And all provided links should be detected
    And floodlight should not find redundant switches
    And default rules for switches are installed
