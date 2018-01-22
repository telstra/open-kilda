@PCE
Feature: Path Computation Engine

  The Path Computation Engine (PCE) is responsible for calculating the path between nodes.
  The following scenarios highlight the business requirements related to path computation.

  For details on how the tests are implemented, look at PathComputationTest.java

  @MVP1 @SMOKE
  Scenario Outline: The Path Matches the Policy

    This scenario exercises each of the policies, with the default policy getting tested twice.

    Given a spider web topology with endpoints A and B
    When a flow request is made between A and B with <policy>
    Then the path matches the <policy>
    And the path between A and B is pingable

    Examples:
      | policy  |
      | default |
      | hops    |
      | cost1   |
      | cost2   |

