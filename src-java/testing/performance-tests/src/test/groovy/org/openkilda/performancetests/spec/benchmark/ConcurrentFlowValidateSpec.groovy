package org.openkilda.performancetests.spec.benchmark

import static groovyx.gpars.GParsPool.withPool

import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.FlowExtended
import org.openkilda.functionaltests.helpers.model.SwitchPortVlan
import org.openkilda.performancetests.BaseSpecification
import org.openkilda.performancetests.helpers.TopologyBuilder

class ConcurrentFlowValidateSpec extends BaseSpecification {

    def "Flow validation (concurrent) on mesh topology"() {
        given: "A mesh topology"
        def topo = new TopologyBuilder(flHelper.fls,
                preset.islandCount, preset.regionsPerIsland, preset.switchesPerRegion).buildMeshes()
        topoHelper.createTopology(topo)
        flowFactory.setTopology(topoHelper.topology)

        when: "A source switch"
        def srcSw = topo.switches.first()
        def busyPorts = topo.getBusyPortsForSwitch(srcSw)
        def allowedPorts = (1..(preset.flowCount + busyPorts.size())) - busyPorts

        and: "Create flows"
        List<FlowExtended> flows = []
        List<SwitchPortVlan> busyEndpoints = []
        allowedPorts.each { port ->
            def flow = flowFactory.getBuilder(srcSw, pickRandom(topo.switches - srcSw), false, busyEndpoints)
                    .withProtectedPath(false)
                    .withSourcePort(port).build()
                    .create()
            busyEndpoints.addAll(flow.occupiedEndpoints())
            flows << flow
        }

        then: "Validate flows"
        withPool {
            (1..preset.validateAttempts).each {
                Wrappers.wait(flows.size()) {
                    flows.eachParallel { FlowExtended flow -> assert flow.validateAndCollectDiscrepancies().isEmpty() }
                }
            }
        }

        cleanup: "Remove all flows"
        deleteFlows(flows)

        where:
        preset << [
                [
                        islandCount      : 1,
                        regionsPerIsland : 3,
                        switchesPerRegion: 10,
                        flowCount        : 300,
                        validateAttempts  : 100
                ]
        ]
    }
}
