package org.openkilda.functionaltests.spec.flows

import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.messaging.payload.flow.FlowState

import spock.lang.Ignore

class FlowPriorityRerouteSpec extends BaseSpecification {

    def "System is able to reroute(automatically) flow in the correct order based on the priority field"() {
        given: "Three flows on the same path, with alt paths available"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { it.paths.size() > 1 } ?:
                assumeTrue("No suiting switches found", false)
        List<FlowPayload> flows = []

        def newPriority = 300

        3.times {
            def flow = flowHelper.randomFlow(switchPair)
            flow.maximumBandwidth = 10000
            flow.priority = newPriority
            flowHelper.addFlow(flow)
            newPriority -= 100
            flows << flow
        }
        def currentPath = pathHelper.convert(northbound.getFlowPath(flows[0].id))
        //ensure all flows are on the same path
        assert pathHelper.convert(northbound.getFlowPath(flows[1].id)) == currentPath
        assert pathHelper.convert(northbound.getFlowPath(flows[2].id)) == currentPath

        def altPath = switchPair.paths.find { it != currentPath }

        when: "Init simultaneous reroute for all flows by bringing current path's ISL down"
        def currentIsls = pathHelper.getInvolvedIsls(currentPath)
        def newIsls = pathHelper.getInvolvedIsls(altPath)
        def islToBreak = currentIsls.find { !newIsls.contains(it) }
        northbound.portDown(islToBreak.srcSwitch.dpId, islToBreak.srcPort)

        then: "Flows were rerouted"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            flows.each {
                assert northbound.getFlowStatus(it.id).status == FlowState.UP
                assert pathHelper.convert(northbound.getFlowPath(it.id)) != currentPath
            }
        }

        and: "Reroute procedure was done based on the priority field"
        flows.sort { it.priority }*.id == northbound.getAllFlows().sort { it.lastUpdated }*.id

        and: "Cleanup: revert system to original state"
        northbound.portUp(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        flows.each { flowHelper.deleteFlow(it.id) }
        Wrappers.wait(WAIT_OFFSET + discoveryInterval) {
            assert islUtils.getIslInfo(islToBreak).get().state == IslChangeType.DISCOVERED
        }
        database.resetCosts()
    }

    @Ignore("https://github.com/telstra/open-kilda/issues/2211")
    def "System is able to reroute(intentional) flow in the correct order based on the priority field"() {
        given: "Three flows on the same path, with alt paths available"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { it.paths.size() > 1 } ?:
                assumeTrue("No suiting switches found", false)
        List<FlowPayload> flows = []

        def newPriority = 300

        3.times {
            def flow = flowHelper.randomFlow(switchPair)
            flow.maximumBandwidth = 10000
            flow.priority = newPriority
            flowHelper.addFlow(flow)
            newPriority -= 100
            flows << flow
        }
        def currentPath = pathHelper.convert(northbound.getFlowPath(flows[0].id))
        //ensure all flows are on the same path
        assert pathHelper.convert(northbound.getFlowPath(flows[1].id)) == currentPath
        assert pathHelper.convert(northbound.getFlowPath(flows[2].id)) == currentPath

        when: "Make another path more preferable"
        def newPath = switchPair.paths.find { it != currentPath }
        switchPair.paths.findAll { it != newPath }.each { pathHelper.makePathMorePreferable(newPath, it) }

        and: "Init simultaneous reroute for all flows"
        def isl = pathHelper.getInvolvedIsls(currentPath).first()
        northbound.rerouteLinkFlows(isl.srcSwitch.dpId, isl.srcPort, isl.dstSwitch.dpId, isl.dstPort)

        then: "Flows were rerouted"
        Wrappers.wait(WAIT_OFFSET) {
            flows.each {
                assert northbound.getFlowStatus(it.id).status == FlowState.UP
                assert pathHelper.convert(northbound.getFlowPath(it.id)) == newPath
            }
        }

        and: "Reroute procedure was done based on the priority field"
        flows.sort { it.priority }*.id == northbound.getAllFlows().sort { it.lastUpdated }*.id

        and: "Cleanup: revert system to original state"
        flows.each { flowHelper.deleteFlow(it.id) }
        northbound.deleteLinkProps(northbound.getAllLinkProps())
        database.resetCosts()
    }
}
