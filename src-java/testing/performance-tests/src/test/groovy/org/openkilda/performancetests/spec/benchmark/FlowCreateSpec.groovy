package org.openkilda.performancetests.spec.benchmark


import org.openkilda.functionaltests.helpers.model.FlowExtended
import org.openkilda.functionaltests.helpers.model.SwitchPortVlan
import org.openkilda.performancetests.BaseSpecification
import org.openkilda.performancetests.helpers.TopologyBuilder

class FlowCreateSpec extends BaseSpecification {

    def "Flow creation on mesh topology"() {
        given: "A mesh topology"
        def topo = new TopologyBuilder(flHelper.fls,
                preset.islandCount, preset.regionsPerIsland, preset.switchesPerRegion).buildMeshes()
        topoHelper.createTopology(topo)
        setTopologyInContext(topoHelper.topology)

        when: "A source switch"
        def srcSw = switches.all().first()
        def busyPorts = srcSw.getServicePorts()
        def allowedPorts = (1..(preset.flowCount + busyPorts.size())) - busyPorts

        and: "Create flows"
        List<FlowExtended> flows = []
        List<SwitchPortVlan> busyEndpoints = []
        allowedPorts.each { port ->
            def dstSw = pickRandom(switches.all().getListOfSwitches() - srcSw)
            def flow = flowFactory.getBuilder(srcSw, dstSw, false, busyEndpoints)
                    .withProtectedPath(false)
                    .withSourcePort(port).build()
                    .create()
            busyEndpoints.addAll(flow.occupiedEndpoints())
            flows << flow
        }

        then: "Flows are created"
        assert flows.size() == preset.flowCount
        assert northboundV2.getAllFlows().size() == flows.size()

        cleanup: "Remove all flows"
        deleteFlows(flows)

        where:
        preset << [
                [
                        islandCount      : 1,
                        regionsPerIsland : 3,
                        switchesPerRegion: 10,
                        flowCount        : 1000
                ]
        ]
    }

}
