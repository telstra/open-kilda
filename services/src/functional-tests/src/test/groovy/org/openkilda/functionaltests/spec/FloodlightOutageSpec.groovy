package org.openkilda.functionaltests.spec

import org.openkilda.functionaltests.BaseSpecification

import spock.lang.Ignore

@Ignore("not implemented yet")
class FloodlightOutageSpec extends BaseSpecification {

    def "Flows can survive Floodlight restart"() {
        given: "A valid flow"
        when: "Floodlight restarts"
        then: "Flow is still up, valid and on the same path"
    }

    def "Flow successfully reroutes if switch goes down during Floodlight outage"() {
        given: "A valid flow with alternative paths"
        when: "Floodlight goes down"
        and: "Some time passes"
        then: "Storm logs messages saying that it acknowledge FL being down"
        and: "All switches are still Activated"
        and: "There are no Failed ISLs"
        and: "Flow is still up and on the same path"

        when: "One of the flow's switches disconnects"
        and: "Floodlight goes back up"
        then: "Switch is marked as Deactivated"
        and: "Flow is rerouted away from broken switch"
        and: "Flow is Up"
    }

    def "It is still possible to create a flow even if flow creation was requested during Floodlight outage"() {
        given: "Floodlight is down"
        when: "Request a new flow"
        then: "Flow status is 'In Progress'"
        when: "Floodlight goes up"
        then: "Flow status becomes 'Up'"
        and: "Flow is valid"
    }
}
