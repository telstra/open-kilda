Feature: Basic Statistics Collection

  Scenario: Statistics gets collected

    This scenario makes sure statistics gets collected

    Given Clean setup
    When any topology is created
    Then data go to database

  Scenario: Statistics keeps getting collected

    This scenario makes sure statistics keeps getting collected

    Given Clean setup
    When any topology is created
    Then database keeps growing
