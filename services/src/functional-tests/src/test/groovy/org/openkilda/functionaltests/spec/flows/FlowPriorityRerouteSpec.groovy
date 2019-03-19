package org.openkilda.functionaltests.spec.flows


import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import java.util.concurrent.TimeUnit

class FlowPriorityRerouteSpec extends BaseSpecification {
    def "System is able to reroute(automatically) flow in the correct order based on the priority field"() {
        given: "3 flows on the same path, with alt paths available"
        def switches = topology.activeSwitches
        List<List<PathNode>> allPaths = []
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.unique { it.sort() }.find { Switch src, Switch dst ->
            allPaths = database.getPaths(src.dpId, dst.dpId)*.path
            allPaths.size() > 2
        }
        List<FlowPayload> flows = []

        def newPriority = 100

        3.times {
            def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
            flow.maximumBandwidth = 10000
            flow.priority = newPriority
            flowHelper.addFlow(flow)
            newPriority += 100
            flows << flow
        }
        def currentPath = pathHelper.convert(northbound.getFlowPath(flows[0].id))
        //ensure all flows are on the same path
        assert pathHelper.convert(northbound.getFlowPath(flows[1].id)) == currentPath
        assert pathHelper.convert(northbound.getFlowPath(flows[2].id)) == currentPath

        def newPath = allPaths.find { it != currentPath }

        when: "Init simultaneous reroute for all flows by bringing current path's ISL down"
        def currentIsls = pathHelper.getInvolvedIsls(currentPath)
        def newIsls = pathHelper.getInvolvedIsls(newPath)
        def islToBreak = currentIsls.find { !newIsls.contains(it) }
        northbound.portDown(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        TimeUnit.SECONDS.sleep(rerouteDelay - 1)

        then: "Reroute procedure was done based on the priority field"
        flows.sort { it.priority }*.id == northbound.getAllFlows().sort { it.lastUpdated }*.id

        and: "Cleanup: revert system to original state"
        northbound.portUp(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        flows.each { flowHelper.deleteFlow(it.id) }
        database.resetCosts()
        Wrappers.wait(WAIT_OFFSET + discoveryInterval) {
            assert islUtils.getIslInfo(islToBreak).get().state == IslChangeType.DISCOVERED
        }
    }

    def "System is able to reroute(intentional) flow in the correct order based on the priority field"() {
        given: "3 flows on the same path, with alt paths available"
        def switches = topology.activeSwitches
        List<List<PathNode>> allPaths = []
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.unique { it.sort() }.find { Switch src, Switch dst ->
            allPaths = database.getPaths(src.dpId, dst.dpId)*.path
            allPaths.size() > 2
        }
        List<FlowPayload> flows = []

        def newPriority = 100

        3.times {
            def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
            flow.maximumBandwidth = 10000
            flow.priority = newPriority
            flowHelper.addFlow(flow)
            newPriority += 100
            flows << flow
        }
        def currentPath = pathHelper.convert(northbound.getFlowPath(flows[0].id))
        //ensure all flows are on the same path
        assert pathHelper.convert(northbound.getFlowPath(flows[1].id)) == currentPath
        assert pathHelper.convert(northbound.getFlowPath(flows[2].id)) == currentPath

        when: "Make another path more preferable"
        def newPath = allPaths.find { it != currentPath }
        allPaths.findAll { it != newPath }.each { pathHelper.makePathMorePreferable(newPath, it) }

        and: "Init simultaneous reroute for all flows"
        flows.each { northbound.rerouteFlow(it.id) }

        then: "Reroute procedure was done based on the priority field"
        flows.sort { it.priority }*.id == northbound.getAllFlows().sort { it.lastUpdated }*.id

        and: "Cleanup: revert system to original state"
        flows.each { flowHelper.deleteFlow(it.id) }
        database.resetCosts()
    }
}
