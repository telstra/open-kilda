package org.openkilda.performancetests.spec.endurance

import static org.openkilda.functionaltests.helpers.model.SwitchExtended.verifyMeterSectionsAreEmpty
import static org.openkilda.functionaltests.helpers.model.SwitchExtended.verifyRuleSectionsAreEmpty

import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.FlowExtended
import org.openkilda.functionaltests.helpers.model.SwitchPortVlan
import org.openkilda.performancetests.BaseSpecification

import spock.lang.Shared

class VolumeSpec extends BaseSpecification {

    @Shared
    def r = new Random()

    def setupSpec() {
        topoHelper.purgeTopology()
    }

    def "Able to validate a switch with a lot of flows on different ports"() {
        given: "A small topology"
        def topo = topoHelper.createRandomTopology(5, 15)
        setTopologyInContext(topo)

        and: "A switch under test"
        def sw = switches.all().first()
        def busyPorts = sw.getServicePorts()
        def allowedPorts = (1..200 + busyPorts.size()) - busyPorts //200 total

        when: "Create total 200 flows on a switch, each flow uses free non-isl port"
        List<FlowExtended> flows = []
        List<SwitchPortVlan> busyEndpoints = []
        allowedPorts.each { port ->
            def dstSw = pickRandom(switches.all().getListOfSwitches() - sw)
            def flow = flowFactory.getBuilder(sw, dstSw, false, busyEndpoints)
                    .withProtectedPath(r.nextBoolean()).withSourcePort(port).build().sendCreateRequest()
            busyEndpoints.addAll(flow.occupiedEndpoints())
            flows << flow
        }

        then: "Each flow passes flow validation"
        Wrappers.wait(flows.size()) {
            flows.forEach { assert it.validateAndCollectDiscrepancies().isEmpty() }
        }

        and: "Target switch passes switch validation"
        verifyAll(sw.validateV1()) {
            verifyRuleSectionsAreEmpty(it, ["excess", "missing"])
            verifyMeterSectionsAreEmpty(it, ["excess", "missing", "misconfigured"])
        }

        cleanup: "Remove all flows, delete topology"
        deleteFlows(flows)
        topoHelper.purgeTopology(topo)
    }
}
