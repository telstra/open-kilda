Feature: sample feature

  Scenario: retrive the flows from Northbound
    Given the reference topology
    When get flows from Northbound
    Then received the flows