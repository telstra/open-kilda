package org.openkilda.functionaltests.spec

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.FlowHelper
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.topology.TopologyEngineService
import org.openkilda.testing.tools.IslUtils
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Ignore

@Ignore
class BandwidthSpec extends BaseSpecification {
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
    @Autowired
    IslUtils islUtils

    def "Should not be able to reroute to a path with not enough bandwidth available"() {
        given: "Flow with alternate paths available"
        def switches = topology.getActiveSwitches()
        List<List<PathNode>> allPaths = []
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            allPaths = topologyEngineService.getPaths(src.dpId, dst.dpId)*.path
            allPaths.size() > 1
        }
        assert srcSwitch
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = 10000
        northboundService.addFlow(flow)
        def currentPath = PathHelper.convert(northboundService.getFlowPath(flow.id))

        when: "Make current path less preferable than alternatives"
        def alternativePaths = allPaths.findAll { it != currentPath }
        alternativePaths.each { pathHelper.makePathMorePreferable(it, currentPath) }

        and: "Make all alternative paths to have not enough bandwidth to handle the flow"
        def originalIsls = pathHelper.getInvolvedIsls(currentPath)
        alternativePaths.each { altPath ->
            def uniqueIsl = (pathHelper.getInvolvedIsls(altPath) - originalIsls).first()
            db.updateLinkProperty(uniqueIsl, "max_bandwidth", flow.maximumBandwidth - 1)
            db.updateLinkProperty(islUtils.reverseIsl(uniqueIsl), "max_bandwidth", flow.maximumBandwidth - 1)
        }

        and: "Init a reroute to a more preferable path"
        def rerouteResponse = northboundService.rerouteFlow(flow.id)

        then: "Flow is NOT rerouted"
        !rerouteResponse.rerouted
        PathHelper.convert(northboundService.getFlowPath(flow.id)) == currentPath

        and: "Remove flow"
        northboundService.deleteFlow(flow.id)

        //TODO: revert max_bandwidth to be equal to 'speed' (match ()-[i:isl]->() set i.max_bandwidth=i.speed return i).
        //TODO: Revert costs (match ()-[i:isl]->() set i.cost=700)
    }
}
