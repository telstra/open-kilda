package org.openkilda.performancetests.spec.benchmark

import static groovyx.gpars.GParsPool.withPool

import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.FlowExtended
import org.openkilda.functionaltests.helpers.model.SwitchPortVlan
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.performancetests.BaseSpecification
import org.openkilda.performancetests.helpers.TopologyBuilder

class ConcurrentFlowCreateSpec extends BaseSpecification {

    def "Flow creation (concurrent) on mesh topology"() {
        given: "A mesh topology"
        def topo = new TopologyBuilder(flHelper.fls,
                preset.islandCount, preset.regionsPerIsland, preset.switchesPerRegion).buildMeshes()
        topoHelper.createTopology(topo)
        setTopologyInContext(topoHelper.topology)

        and: "A source switch"
        def srcSw = switches.all().first()
        def busyPorts = srcSw.getServicePorts()
        def allowedPorts = (1..(preset.flowCount + busyPorts.size())) - busyPorts

        when: "Create flows"
        List<FlowExtended> flows = []
        List<SwitchPortVlan> busyEndpoints = []
        withPool {
            allowedPorts.eachParallel { port ->
                def dstSw = pickRandom(switches.all().getListOfSwitches() - srcSw)
                def flow = flowFactory.getBuilder(srcSw, dstSw, false, busyEndpoints)
                        .withProtectedPath(false)
                        .withSourcePort(port).build()
                        .sendCreateRequest()
                busyEndpoints.addAll(flow.occupiedEndpoints())
                flows << flow
            }
        }

        then: "Flows are created"
        Wrappers.wait(flows.size()) {
            flows.forEach { assert it.retrieveFlowStatus().status == FlowState.UP }
        }

        cleanup: "Remove all flows"
        deleteFlows(flows)

        where:
        preset << [
                [
                        islandCount      : 1,
                        regionsPerIsland : 3,
                        switchesPerRegion: 10,
                        flowCount        : 300
                ]
        ]
    }
}
