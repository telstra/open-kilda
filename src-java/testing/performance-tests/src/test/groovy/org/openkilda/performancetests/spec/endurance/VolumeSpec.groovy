package org.openkilda.performancetests.spec.endurance

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
        flowFactory.setTopology(topo)

        and: "A switch under test"
        def sw = topo.switches.first()
        def busyPorts = topo.getBusyPortsForSwitch(sw)
        def allowedPorts = (1..200 + busyPorts.size()) - busyPorts //200 total

        when: "Create total 200 flows on a switch, each flow uses free non-isl port"
        List<FlowExtended> flows = []
        List<SwitchPortVlan> busyEndpoints = []
        allowedPorts.each { port ->
            def flow = flowFactory.getBuilder(sw, pickRandom(topo.switches - sw), false, busyEndpoints)
                    .withProtectedPath(r.nextBoolean()).withSourcePort(port).build().sendCreateRequest()
            busyEndpoints.addAll(flow.occupiedEndpoints())
            flows << flow
        }

        then: "Each flow passes flow validation"
        Wrappers.wait(flows.size()) {
            flows.forEach { assert it.validateAndCollectDiscrepancies().isEmpty() }
        }

        and: "Target switch passes switch validation"
        verifyAll(northbound.validateSwitch(sw.dpId)) {
            it.verifyRuleSectionsAreEmpty(["excess", "missing"])
            it.verifyMeterSectionsAreEmpty(["excess", "missing", "misconfigured"])
        }

        cleanup: "Remove all flows, delete topology"
        deleteFlows(flows)
        topoHelper.purgeTopology(topo)
    }
}
