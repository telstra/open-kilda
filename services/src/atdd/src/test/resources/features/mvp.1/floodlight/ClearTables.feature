@CT
Feature: Validate Flow Rules Are Not Erased on Reboot

  This is part of being highly-resilient. We should be able to restart the speaker (floodlight)
  without worrying that the rules on all switches will be erased and need to be re-installed. If
  the rules are erased, then the dataplane will stop, which means customers are affected.

  This test is a critical part of Zero Down Time (ZDT)

  @MVP1 @ZDT
  Scenario: Flow rules are still alive

    Given started floodlight container
    And created simple topology from two switches
    And added custom flow rules
    When floodlight controller is reloaded
    Then flow rules should not be cleared up
