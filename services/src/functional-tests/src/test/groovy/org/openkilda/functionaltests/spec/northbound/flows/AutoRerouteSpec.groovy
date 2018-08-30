package org.openkilda.functionaltests.spec.northbound.flows

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.FlowHelper
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.topology.TopologyEngineService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value

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

    @Value('${reroute.delay}')
    int rerouteTimeout
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
        def altPaths = allPaths.findAll { it != currentPath }
        List<PathNode> broughtDownPorts = []
        altPaths.unique { it.first() }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            northboundService.portDown(src.switchId, src.portNo)
        }

        when: "One of the flow's ISLs goes down"
        def isl = pathHelper.getInvolvedIsls(currentPath).first()
        northboundService.portDown(isl.dstSwitch.dpId, isl.dstPort)

        then: "Flow becomes 'Down'"
        Wrappers.wait(rerouteTimeout + 2) { northboundService.getFlowStatus(flow.id).status == FlowState.DOWN }

        when: "ISL goes back up"
        northboundService.portUp(isl.dstSwitch.dpId, isl.dstPort)

        then: "Flow becomes 'Up'"
        Wrappers.wait(rerouteTimeout + discoveryInterval + 3) {
            northboundService.getFlowStatus(flow.id).status == FlowState.UP
        }

        and: "Restore topology to original state, remove flow"
        broughtDownPorts.each { northboundService.portUp(it.switchId, it.portNo) }
        northboundService.deleteFlow(flow.id)
        //TODO(rtretiak): restore costs that were changed due to portdowns
    }
}
