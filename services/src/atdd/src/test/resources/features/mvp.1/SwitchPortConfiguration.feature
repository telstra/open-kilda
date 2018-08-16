@FCRUD
Feature: Configure switch port
  
  @MVP1 @CRUD_UPDATE
  Scenario Outline: Change port status to up
  
    Given a clean controller
    And a nonrandom linear topology of 7 switches
    Then switch "<switch>" with port name as "<port_no>" status from down to up and verify the state

    Examples: 
      | switch                  | port_no     |
      | de:ad:be:ef:00:00:00:02 | 2           |
    
  @MVP1 @CRUD_UPDATE
  Scenario Outline: Change port status to down
    
    Given a clean controller
    And a nonrandom linear topology of 7 switches
    Then switch "<switch>" with port name as "<port_no>" status from up to down and verify the state

    Examples: 
      | switch                  | port_no     |
      | de:ad:be:ef:00:00:00:02 | 2           |
