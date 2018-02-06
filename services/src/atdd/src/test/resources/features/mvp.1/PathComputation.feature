@PCE
Feature: Path Computation Engine

  The Path Computation Engine (PCE) is responsible for calculating the path between nodes.
  The following scenarios highlight the business requirements related to path computation.

  For details on how the tests are implemented, look at PathComputationTest.java

  @MVP1 @LINK_PROPS_CRUD
  Scenario: Cost Upload and Download

  Verify that we can upload costs to kilda and download costs as well.
  Costs can be anything - ie any field with a numeric (double) value.
  As part of cost upload, verify that links that should be updated are updated.

    Given a spider web topology with endpoints A and B
    And no link properties
    When link costs are uploaded through the NB API
    Then link costs can be downloaded
    And link properties reflect what is in the link properties table
    And link costs can be deleted
    And link properties reflect what is in the link properties table

  @MVP1 @LINK_PROPS_UPDATE
  Scenario: Cost Upload and Download

    Similar to CREATE, verify that update works, along with Read and Delete

    Given a spider web topology with endpoints A and B
    And no link properties
    When link costs are uploaded through the NB API
    And link costs are updated through the NB API
    Then link costs can be downloaded
    And link properties reflect what is in the link properties tablex`

  @MVP1 @POLICY
  Scenario Outline: The Path Matches the Policy

    This scenario exercises each of the policies, with the default policy getting tested twice.

    Given a spider web topology with endpoints A and B
    When a flow request is made between A and B with <policy>
    Then the path matches the <policy>
    And the path between A and B is pingable

    Examples:
      | policy    |
      | HOPS      |
      | LATENCY   |
      | COST      |
      | EXTERNAL1 |

  @MVP1 @LINK_CREATE
  Scenario: Link creation will add costing if it exists

    Verify that when we do have a cost table, if a link is added that matches a cost id,
    then the cost is added as a property to those links.

    Given a spider web topology with endpoints A and B
    When link costs are uploaded through the NB API
    And one or more links are added to the spider web topology
    Then the new links will have the cost properties added if the cost id matches

