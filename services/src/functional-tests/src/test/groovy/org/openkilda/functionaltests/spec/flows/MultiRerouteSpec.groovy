package org.openkilda.functionaltests.spec.flows

import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.messaging.payload.flow.FlowState

import java.util.concurrent.TimeUnit

class MultiRerouteSpec extends HealthCheckSpecification {

    def "Simultaneous reroute of multiple flows should not oversubscribe any ISLs"() {
        given: "Two flows on the same path, with alt paths available"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { it.paths.size() > 1 } ?:
                assumeTrue("No suiting switches found", false)
        List<FlowPayload> flows = []
        2.times {
            def flow = flowHelper.randomFlow(switchPair)
            flow.maximumBandwidth = 10000
            flowHelper.addFlow(flow)
            flows << flow
        }
        def currentPath = pathHelper.convert(northbound.getFlowPath(flows[0].id))
        //ensure both flows are on the same path
        assert pathHelper.convert(northbound.getFlowPath(flows[1].id)) == currentPath

        when: "Make another path more preferable"
        def newPath = switchPair.paths.find { it != currentPath }
        switchPair.paths.findAll { it != newPath }.each { pathHelper.makePathMorePreferable(newPath, it) }

        and: "Make preferable path's ISL to have not enough bandwidth to handle 2 flows together, but enough for 1 flow"
        def currentIsls = pathHelper.getInvolvedIsls(currentPath)
        def newIsls = pathHelper.getInvolvedIsls(newPath)
        def thinIsl = newIsls.find { !currentIsls.contains(it) }
        long newBw = flows.sum { it.maximumBandwidth } - 1
        [thinIsl, thinIsl.reversed].each { database.updateIslMaxBandwidth(it, newBw) }
        [thinIsl, thinIsl.reversed].each { database.updateIslAvailableBandwidth(it, newBw) }

        and: "Init simultaneous reroute of both flows by bringing current path's ISL down"
        def islToBreak = currentIsls.find { !newIsls.contains(it) }
        antiflap.portDown(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        TimeUnit.SECONDS.sleep(rerouteDelay - 1)

        then: "Both flows change their paths (or go Down if no path)"
        Wrappers.wait(PATH_INSTALLATION_TIME) {
            flows.each {
                def status = northbound.getFlowStatus(it.id).status
                assert status != FlowState.IN_PROGRESS
                assert pathHelper.convert(northbound.getFlowPath(it.id)) != currentPath ||
                        status == FlowState.DOWN
            }
        }

        and: "'Thin' ISL is not oversubscribed"
        islUtils.getIslInfo(thinIsl).get().availableBandwidth == newBw - flows.first().maximumBandwidth

        and: "Only one flow goes through a preferred path"
        flows.count { pathHelper.convert(northbound.getFlowPath(it.id)) == newPath } == 1

        and: "Cleanup: revert system to original state"
        antiflap.portUp(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        flows.each { flowHelper.deleteFlow(it.id) }
        [thinIsl, thinIsl.reversed].each { database.resetIslBandwidth(it) }
        northbound.deleteLinkProps(northbound.getAllLinkProps())
        database.resetCosts()
        Wrappers.wait(WAIT_OFFSET + discoveryInterval) {
            assert islUtils.getIslInfo(islToBreak).get().state == IslChangeType.DISCOVERED
        }
    }
}
