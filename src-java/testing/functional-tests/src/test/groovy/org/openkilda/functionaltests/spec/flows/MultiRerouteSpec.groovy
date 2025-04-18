package org.openkilda.functionaltests.spec.flows

import static org.openkilda.functionaltests.extension.tags.Tag.ISL_PROPS_DB_RESET
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowExtended
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.tools.SoftAssertions

import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Shared

import java.util.concurrent.TimeUnit


class MultiRerouteSpec extends HealthCheckSpecification {

    @Autowired
    @Shared
    FlowFactory flowFactory

    @Tags([ISL_RECOVER_ON_FAIL, ISL_PROPS_DB_RESET])
    def "Simultaneous reroute of multiple flows should not oversubscribe any ISLs"() {
        given: "Many flows on the same path, with alt paths available"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNPaths(3).first()
        List<FlowExtended> flows = []
        def availablePaths = switchPair.retrieveAvailablePaths().collect { it.getInvolvedIsls() }
        def currentPathIsls = availablePaths.first()
        availablePaths.findAll { it != currentPathIsls }.each { islHelper.makePathIslsMorePreferable(currentPathIsls, it) }
        30.times {
            // do not use busyEndpoints argument here since the flows are not full-port flows, all flows are tagged
            def flow = flowFactory.getBuilder(switchPair, false)
                    .withBandwidth(10000)
                    .build().create()
            flows << flow
        }
        //ensure all flows are on the same path
        flows[1..-1].each {
            assert it.retrieveAllEntityPaths().getInvolvedIsls() == currentPathIsls
        }

        when: "Make another path more preferable"
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))
        def prefPathIsls = availablePaths.find { it != currentPathIsls }
        availablePaths.findAll { !it.containsAll(prefPathIsls) }.each { islHelper.makePathIslsMorePreferable(prefPathIsls, it) }

        and: "Make preferable path's ISL to have bandwidth to host only half of the rerouting flows"
        def notPrefIsls = availablePaths.findAll { !it.containsAll(prefPathIsls) }.flatten().unique()
                .collectMany { [it, it.reversed] } as List<Isl>
        def thinIsl = prefPathIsls.find { !notPrefIsls.contains(it) }
        def halfOfFlows = flows[0..flows.size() / 2 - 1]
        long newBw = halfOfFlows.sum { it.maximumBandwidth } as Long
        islHelper.setAvailableAndMaxBandwidth([thinIsl, thinIsl.reversed], newBw)

        and: "Init simultaneous reroute of all flows by bringing current path's ISL down"
        def notCurrentIsls = availablePaths.findAll { it != currentPathIsls }.flatten().unique()
                .collectMany { [it, it.reversed] } as List<Isl>
        def islToBreak = currentPathIsls.find { !notCurrentIsls.contains(it) }
        islHelper.breakIsl(islToBreak)
        TimeUnit.SECONDS.sleep(rerouteDelay - 1)

        then: "Half of the flows are hosted on the preferable path"
        def flowsOnPrefPath
        wait(WAIT_OFFSET * 3) {
            def assertions = new SoftAssertions()
            flowsOnPrefPath = flows.findAll {
                it.retrieveAllEntityPaths().getInvolvedIsls() == prefPathIsls
            }
            flowsOnPrefPath.each { flow ->
                assertions.checkSucceeds { assert flow.retrieveFlowStatus().status == FlowState.UP }
            }
            assertions.checkSucceeds { assert flowsOnPrefPath.size() == halfOfFlows.size() }
            assertions.verify()
        }

        and: "Rest of the flows are hosted on another alternative paths"
        def restFlows = flows.findAll { !flowsOnPrefPath*.flowId.contains(it.flowId) }
        wait(WAIT_OFFSET * 2) {
            def assertions = new SoftAssertions()
            restFlows.each { flow ->
                assertions.checkSucceeds { assert flow.retrieveFlowStatus().status == FlowState.UP }
                assertions.checkSucceeds { assert flow.retrieveAllEntityPaths().getInvolvedIsls() != prefPathIsls }
            }
            assertions.verify()
        }

        and: "None ISLs are oversubscribed"
        northbound.getAllLinks().each { assert it.availableBandwidth >= 0 }
    }
}
