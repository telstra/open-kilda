package org.openkilda.performancetests.spec.benchmark

import static groovyx.gpars.GParsPool.withPool

import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.FlowExtended
import org.openkilda.functionaltests.helpers.model.SwitchPortVlan
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.performancetests.BaseSpecification
import org.openkilda.performancetests.helpers.TopologyBuilder

import groovy.util.logging.Slf4j

@Slf4j
class ConcurrentFlowRerouteSpec extends BaseSpecification {

    def "Flow reroute (concurrent) on mesh topology"() {
        given: "A mesh topology"
        def topo = new TopologyBuilder(flHelper.fls,
                preset.islandCount, preset.regionsPerIsland, preset.switchesPerRegion).buildMeshes()
        topoHelper.createTopology(topo)
        flowFactory.setTopology(topoHelper.topology)

        when: "A source switch"
        def srcSw = topo.switches.first()
        def busyPorts = topo.getBusyPortsForSwitch(srcSw)
        def allowedPorts = (1..(preset.flowCount + busyPorts.size())) - busyPorts
        def busyPortsFirstHalf = busyPorts.chop((int) (busyPorts.size() / 2))[0]

        and: "Create flows"
        northbound.updateLinkProps(topo.isls.findAll { isl ->
            isl.srcSwitch.dpId == srcSw.dpId
        }.collect { isl ->
            islUtils.toLinkProps(isl, [cost: busyPortsFirstHalf.contains(isl.srcPort) ? "1" : "5000"])
        })

        List<FlowExtended> flows = []
        List<SwitchPortVlan> busyEndpoints = []
        allowedPorts.each { port ->
            def flow = flowFactory.getBuilder(srcSw, pickRandom(topo.switches - srcSw), false, busyEndpoints)
                    .withProtectedPath(false).withSourcePort(port).build().create()
            busyEndpoints.addAll(flow.occupiedEndpoints())
            flows << flow
        }
        Collections.shuffle(flows)

        and: "Flows are created"
        assert flows.size() == preset.flowCount
        Wrappers.wait(flows.size()) {
            flows.forEach { assert it.retrieveFlowStatus().status == FlowState.UP }
        }

        then: "Reroute flows"
        (1..(int)(preset.maxConcurrentReroutes / 10)).each { iteration ->
            def concurrentReroutes = iteration * 10
            log.info("Running reroutes with #$concurrentReroutes concurrent requests")
            (1..preset.rerouteAttempts).each { attempt ->
                northbound.updateLinkProps(topo.isls.findAll { isl ->
                    isl.srcSwitch.dpId == srcSw.dpId
                }.collect { isl ->
                    islUtils.toLinkProps(isl, [cost: (busyPortsFirstHalf.contains(isl.srcPort) ^ attempt % 2 != 0) ? "1" : "5000"])
                })

                withPool(concurrentReroutes) {
                    flows[0..Math.min(flows.size() - 1, concurrentReroutes)].eachParallel { FlowExtended flow ->
                        Wrappers.wait(flows.size()) {
                            flow.reroute()
                        }
                    }
                }

                Wrappers.wait(flows.size()) {
                    flows.forEach { assert it.retrieveFlowStatus().status == FlowState.UP }
                }
            }
        }

        cleanup: "Remove all flows"
        deleteFlows(flows)

        where:
        preset << [
                [
                        islandCount       : 1,
                        regionsPerIsland  : 3,
                        switchesPerRegion : 10,
                        flowCount         : 300,
                        maxConcurrentReroutes: 100,
                        rerouteAttempts   : 10,
                ]
        ]
    }
}
