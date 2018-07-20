@FPATH
Feature: Flow path computation tests


  @MVP1
  Scenario: Link Bandwidth - Tracking

    This scenario creates small multi-path network topology
    and checks that link available bandwidth is tracked.

    Given a clean flow topology
    And a clean controller
    And a clean flow topology
    And a multi-path topology
    And topology contains 16 links
    Then all links have available bandwidth 9000000
    When flow pcet creation request with 00:00:00:00:00:00:00:02 11 0 and 00:00:00:00:00:00:00:07 12 0 and 1000000 is successful
    And flow pcet with 00:00:00:00:00:00:00:02 11 0 and 00:00:00:00:00:00:00:07 12 0 and 1000000 could be created
    Then shortest path links available bandwidth have available bandwidth 8000000
    When flow pcet with 00:00:00:00:00:00:00:02 11 0 and 00:00:00:00:00:00:00:07 12 0 and 1000000 could be updated with 2000000
    Then shortest path links available bandwidth have available bandwidth 7000000
    When flow pcet with 00:00:00:00:00:00:00:02 11 0 and 00:00:00:00:00:00:00:07 12 0 and 2000000 could be deleted
    Then all links have available bandwidth 9000000


  @MVP1
  Scenario: Link Bandwidth - Limit

    This scenario creates small multi-path network topology
    and checks that in case of not enough available bandwidth on the shortest path,
    longer path is chosen.

    Given a clean flow topology
    And a clean controller
    And a clean flow topology
    And a multi-path topology
    And topology contains 16 links
    Then all links have available bandwidth 9000000
    When flow pcel1 creation request with 00:00:00:00:00:00:00:02 11 0 and 00:00:00:00:00:00:00:07 12 0 and 5000000 is successful
    And flow pcel1 with 00:00:00:00:00:00:00:02 11 0 and 00:00:00:00:00:00:00:07 12 0 and 5000000 could be created
    Then shortest path links available bandwidth have available bandwidth 4000000
    When flow pcel2 creation request with 00:00:00:00:00:00:00:02 11 0 and 00:00:00:00:00:00:00:07 12 0 and 5000000 is successful
    And flow pcel2 with 00:00:00:00:00:00:00:02 11 0 and 00:00:00:00:00:00:00:07 12 0 and 5000000 could be created
    Then alternative path links available bandwidth have available bandwidth 4000000


  @MVP1
  Scenario: Link Bandwidth - Not Enough Bandwidth

    This scenario creates small multi-path network topology
    and checks that in case of no enough available bandwidth on the both shortest and alternative paths,
    flow could not be created.

    Given a clean flow topology
    And a clean controller
    And a clean flow topology
    And a multi-path topology
    And topology contains 16 links
    Then all links have available bandwidth 9000000
    When flow pceb1 creation request with 00:00:00:00:00:00:00:02 11 0 and 00:00:00:00:00:00:00:07 12 0 and 9000000 is successful
    And flow pceb1 with 00:00:00:00:00:00:00:02 11 0 and 00:00:00:00:00:00:00:07 2 0 and 9000000 could be created
    When flow pceb2 creation request with 00:00:00:00:00:00:00:02 11 0 and 00:00:00:00:00:00:00:07 12 0 and 9000000 is successful
    And flow pceb2 with 00:00:00:00:00:00:00:02 11 0 and 00:00:00:00:00:00:00:07 12 0 and 9000000 could be created
    When flow pceb3_failed creation request with 00:00:00:00:00:00:00:02 11 0 and 00:00:00:00:00:00:00:07 12 0 and 1 is failed
    Then flows count is 2

  @MVP1
  Scenario: Flow Path

    Developer notes:
    1. Most topologies have 16 links .. is it necessary? Can we save setup time, yet still attain
    the same level of efficacy?

    Given a clean flow topology
    And a clean controller
    And a multi-path topology
    And topology contains 16 links
    Then all links have available bandwidth 9000000
    And flow pceb1 with 00:00:00:00:00:00:00:02 11 0 and 00:00:00:00:00:00:00:07 12 0 and 9000000 path correct
    When flow pceb1 creation request with 00:00:00:00:00:00:00:02 11 0 and 00:00:00:00:00:00:00:07 12 0 and 9000000 is successful
    And flow pceb1 with 00:00:00:00:00:00:00:02 11 0 and 00:00:00:00:00:00:00:07 12 0 and 9000000 could be created
