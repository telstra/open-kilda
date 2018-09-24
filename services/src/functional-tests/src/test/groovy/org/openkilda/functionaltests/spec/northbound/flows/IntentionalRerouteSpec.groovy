package org.openkilda.functionaltests.spec.northbound.flows

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.FlowHelper
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.topology.TopologyEngineService
import org.openkilda.testing.tools.IslUtils

import org.springframework.beans.factory.annotation.Autowired

class IntentionalRerouteSpec extends BaseSpecification {
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
                .findAll { src, dst -> src != dst }.unique { it.sort() }.find { Switch src, Switch dst ->
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
        def currentIsls = pathHelper.getInvolvedIsls(currentPath)
        def changedIsls = alternativePaths.collect { altPath ->
            def thinIsl = pathHelper.getInvolvedIsls(altPath).find { !currentIsls.contains(it) }
            def newBw = flow.maximumBandwidth - 1
            db.updateLinkProperty(thinIsl, "max_bandwidth", newBw)
            db.updateLinkProperty(islUtils.reverseIsl(thinIsl), "max_bandwidth", newBw)
            db.updateLinkProperty(thinIsl, "available_bandwidth", newBw)
            db.updateLinkProperty(islUtils.reverseIsl(thinIsl), "available_bandwidth", newBw)
            thinIsl
        }

        and: "Init a reroute to a more preferable path"
        def rerouteResponse = northboundService.rerouteFlow(flow.id)

        then: "Flow is NOT rerouted because of not enough bandwidth on alternative paths"
        !rerouteResponse.rerouted
        PathHelper.convert(northboundService.getFlowPath(flow.id)) == currentPath

        and: "Remove flow, restore bw"
        northboundService.deleteFlow(flow.id)
        changedIsls.each { 
            db.revertIslBandwidth(it)
            db.revertIslBandwidth(islUtils.reverseIsl(it))
        }
    }

    def "Should be able to reroute to a better path if it has enough bandwidth"() {
        given: "Flow with alternate paths available"
        def switches = topology.getActiveSwitches()
        List<List<PathNode>> allPaths = []
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.unique { it.sort() }.find { Switch src, Switch dst ->
            allPaths = topologyEngineService.getPaths(src.dpId, dst.dpId)*.path
            allPaths.size() > 1
        }
        assert srcSwitch
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = 10000
        northboundService.addFlow(flow)
        def currentPath = PathHelper.convert(northboundService.getFlowPath(flow.id))

        when: "Make some alternative path to be the most preferable among all others"
        def preferableAltPath = allPaths.find { it != currentPath }
        allPaths.findAll { it != preferableAltPath }.each { pathHelper.makePathMorePreferable(preferableAltPath, it) }

        and: "Make future path to have exact bandwidth to handle the flow"
        def currentIsls = pathHelper.getInvolvedIsls(currentPath)
        def thinIsl = pathHelper.getInvolvedIsls(preferableAltPath).find { !currentIsls.contains(it) }
        db.updateLinkProperty(thinIsl, "max_bandwidth", flow.maximumBandwidth)
        db.updateLinkProperty(islUtils.reverseIsl(thinIsl), "max_bandwidth", flow.maximumBandwidth)
        db.updateLinkProperty(thinIsl, "available_bandwidth", flow.maximumBandwidth)
        db.updateLinkProperty(islUtils.reverseIsl(thinIsl), "available_bandwidth", flow.maximumBandwidth)

        and: "Init a reroute to a more preferable path"
        def rerouteResponse = northboundService.rerouteFlow(flow.id)

        then: "Flow is successfully rerouted through a more preferable path"
        rerouteResponse.rerouted
        def newPath = PathHelper.convert(northboundService.getFlowPath(flow.id))
        newPath == preferableAltPath
        pathHelper.getInvolvedIsls(newPath).contains(thinIsl)

        and: "'Thin' ISL has 0 available bandwidth left"
        Wrappers.wait(3) { islUtils.getIslInfo(thinIsl).get().availableBandwidth == 0 }

        and: "Remove flow, restore bw, remove costs"
        Wrappers.wait(5) { northboundService.getFlowStatus(flow.id).status == FlowState.UP }
        northboundService.deleteFlow(flow.id)
        [thinIsl, islUtils.reverseIsl(thinIsl)].each { db.revertIslBandwidth(it) }
    }

    def cleanup() {
        northboundService.deleteLinkProps(northboundService.getAllLinkProps())
        db.resetCosts()
    }
}
