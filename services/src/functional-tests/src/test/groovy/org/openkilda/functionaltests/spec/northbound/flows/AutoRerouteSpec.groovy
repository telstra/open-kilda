package org.openkilda.functionaltests.spec.northbound.flows

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.FlowHelper
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.topology.TopologyEngineService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value

import static org.junit.Assume.assumeTrue

class AutoRerouteSpec extends BaseSpecification {
    @Autowired
    TopologyDefinition topology
    @Autowired
    TopologyEngineService topologyEngineService
    @Autowired
    FlowHelper flowHelper
    @Autowired
    PathHelper pathHelper
    @Autowired
    NorthboundService northboundService
    @Autowired
    Database db

    @Value('${reroute.delay}')
    int rerouteDelay
    @Value('${discovery.interval}')
    int discoveryInterval

    def "Flow should go Down when its link fails and there is no ability to reroute"() {
        given: "A flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.getActiveSwitches()[0..1]
        def allPaths = topologyEngineService.getPaths(srcSwitch.dpId, dstSwitch.dpId)*.path
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        northboundService.addFlow(flow)
        def currentPath = PathHelper.convert(northboundService.getFlowPath(flow.id))

        and: "Ports that lead to alternative paths are brought down to deny alternative paths"
        def altPaths = allPaths.findAll {it != currentPath}
        List<PathNode> broughtDownPorts = []
        altPaths.unique {it.first()}.each {path ->
            def src = path.first()
            broughtDownPorts.add(src)
            northboundService.portDown(src.switchId, src.portNo)
        }

        when: "One of the flow's ISLs goes down"
        def isl = pathHelper.getInvolvedIsls(currentPath).first()
        northboundService.portDown(isl.dstSwitch.dpId, isl.dstPort)

        then: "Flow becomes 'Down'"
        Wrappers.wait(rerouteDelay + 2) {northboundService.getFlowStatus(flow.id).status == FlowState.DOWN}

        when: "ISL goes back up"
        northboundService.portUp(isl.dstSwitch.dpId, isl.dstPort)

        then: "Flow becomes 'Up'"
        Wrappers.wait(rerouteDelay + discoveryInterval + 3) {
            northboundService.getFlowStatus(flow.id).status == FlowState.UP
        }

        and: "Restore topology to original state, remove flow"
        broughtDownPorts.each {northboundService.portUp(it.switchId, it.portNo)}
        northboundService.deleteFlow(flow.id)
        Wrappers.wait(5) {northboundService.getAllLinks().every {it.state != IslChangeType.FAILED}}
    }

    def cleanup() {
        northboundService.deleteLinkProps(northboundService.getAllLinkProps())
        db.resetCosts()
    }

    def "Flow in 'Down' status tries to reroute when discovering a new ISL"() {
        given: "Two active switches and flow with one alternate path at least"
        def switches = topology.getActiveSwitches()
        List<List<PathNode>> possibleFlowPaths = []
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            possibleFlowPaths = topologyEngineService.getPaths(src.dpId, dst.dpId)*.path.sort { it.size() }
            possibleFlowPaths.size() > 1
        }
        assumeTrue("No suiting switches found", srcSwitch && dstSwitch)

        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = 1000
        northboundService.addFlow(flow)
        def flowPath = PathHelper.convert(northboundService.getFlowPath(flow.id))
        assert Wrappers.wait(5) { northboundService.getFlowStatus(flow.id).status == FlowState.UP }

        when: "Bring all ports down on source switch that are involved in current and alternate paths"
        List<PathNode> broughtDownPorts = []
        possibleFlowPaths.unique { it.first() }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            northboundService.portDown(src.switchId, src.portNo)
        }

        then: "Flow goes to 'Down' status"
        Wrappers.wait(rerouteDelay + 2) { northboundService.getFlowStatus(flow.id).status == FlowState.DOWN }

        when: "Bring all ports up on source switch that are involved in alternate paths"
        broughtDownPorts.findAll {
            it.portNo != flowPath.first().portNo
        }.each {
            northboundService.portUp(it.switchId, it.portNo)
        }

        then: "Flow goes to 'Up' status"
        Wrappers.wait(rerouteDelay + discoveryInterval + 3) {
            northboundService.getFlowStatus(flow.id).status == FlowState.UP
        }

        and: "Flow was rerouted"
        def reroutedFlowPath = PathHelper.convert(northboundService.getFlowPath(flow.id))
        flowPath != reroutedFlowPath

        cleanup: "Bring port involved in original path up and delete flow"
        flow?.id && northboundService.deleteFlow(flow.id)
        flowPath?.first() && northboundService.portUp(flowPath.first().switchId, flowPath.first().portNo)
        Wrappers.wait(5) { northboundService.getAllLinks().every { it.state != IslChangeType.FAILED } }
    }
}
