Feature: Flow failover, failure and recovery

    @MVP1
    Scenario: Failover followed by failure followed by recovery

       This scenario checks that failover and recovery happens orderly and that
       failures do not break things apart.

       Given a clean controller
       And a clean flow topology
       And basic multi-path topology
       And a flow is successfully created
       And traffic flows through this flow

       When a route in use fails
       And there is an alternative route
       Then traffic flows through this flow

       When a route in use fails
       And there is no alternative route
       Then traffic does not flow through this flow
       And system is operational

       When a failed route comes back up
       Then traffic flows through this flow

    @MVP1
    Scenario: ISL failover followed by failure followed by recovery

       Given a clean controller
       And a clean flow topology
       And basic multi-path topology
       And a flow is successfully created
       And traffic flows through this flow

       When an ISL in use fails
       And there is an alternative route
       Then traffic flows through this flow

       When an ISL in use fails
       And there is no alternative route
       Then traffic does not flow through this flow
       And system is operational

       When a failed ISL comes back up
       Then traffic flows through this flow

    @MVP1.2
    Scenario: Switch failover followed by failure followed by recovery

       Given a clean controller
       And a clean flow topology
       And basic multi-path topology
       And a flow is successfully created
       And traffic flows through this flow

       When a switch in use fails
       And there is an alternative route
       Then traffic flows through this flow

       When a switch in use fails
       And there is no alternative route
       Then traffic does not flow through this flow
       And system is operational

       When a failed switch comes back up
       Then traffic flows through this flow
