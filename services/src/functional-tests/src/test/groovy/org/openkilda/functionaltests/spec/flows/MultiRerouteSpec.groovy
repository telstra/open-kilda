package org.openkilda.functionaltests.spec.flows

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import java.util.concurrent.TimeUnit

class MultiRerouteSpec extends BaseSpecification {

    def "Simultaneous reroute of multiple flows should not oversubscribe any ISLs"() {
        given: "2 flows on the same path, with alt paths available"
        def switches = topology.activeSwitches
        List<List<PathNode>> allPaths = []
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.unique { it.sort() }.find { Switch src, Switch dst ->
            allPaths = database.getPaths(src.dpId, dst.dpId)*.path
            allPaths.size() > 2
        }
        List<FlowPayload> flows = []
        2.times {
            def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
            flow.maximumBandwidth = 10000
            flowHelper.addFlow(flow)
            flows << flow
        }
        def currentPath = pathHelper.convert(northbound.getFlowPath(flows[0].id))
        //ensure both flows are on the same path
        assert pathHelper.convert(northbound.getFlowPath(flows[1].id)) == currentPath

        when: "Make another path more preferable"
        def newPath = allPaths.find { it != currentPath }
        allPaths.findAll { it != newPath }.each { pathHelper.makePathMorePreferable(newPath, it) }

        and: "Make preferable path's ISL to have not enough bandwidth to handle 2 flows together, but enough for 1 flow"
        def currentIsls = pathHelper.getInvolvedIsls(currentPath)
        def newIsls = pathHelper.getInvolvedIsls(newPath)
        def thinIsl = newIsls.find { !currentIsls.contains(it) }
        long newBw = flows.sum { it.maximumBandwidth } - 1
        [thinIsl, thinIsl.reversed].each { database.updateIslMaxBandwidth(it, newBw) }
        [thinIsl, thinIsl.reversed].each { database.updateIslAvailableBandwidth(it, newBw) }

        and: "Init simultaneous reroute of both flows by bringing current path's ISL down"
        def islToBreak = currentIsls.find { !newIsls.contains(it) }
        northbound.portDown(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        TimeUnit.SECONDS.sleep(rerouteDelay - 1)

        then: "Both flows change their paths (or go Down if no path)"
        Wrappers.wait(WAIT_OFFSET) {
            flows.each {
                assert pathHelper.convert(northbound.getFlowPath(it.id)) != currentPath ||
                        northbound.getFlowStatus(it.id).status == FlowState.DOWN
            }
        }

        and: "'Thin' ISL is not oversubscribed"
        islUtils.getIslInfo(thinIsl).get().availableBandwidth == newBw - flows.first().maximumBandwidth

        and: "Only one flow goes through a preferred path"
        flows.count { pathHelper.convert(northbound.getFlowPath(it.id)) == newPath } == 1

        and: "Cleanup: revert system to original state"
        northbound.portUp(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        flows.each { flowHelper.deleteFlow(it.id) }
        [thinIsl, thinIsl.reversed].each { database.resetIslBandwidth(it) }
        northbound.deleteLinkProps(northbound.getAllLinkProps())
        database.resetCosts()
        Wrappers.wait(WAIT_OFFSET + discoveryInterval) {
            assert islUtils.getIslInfo(islToBreak).get().state == IslChangeType.DISCOVERED
        }
    }
}
