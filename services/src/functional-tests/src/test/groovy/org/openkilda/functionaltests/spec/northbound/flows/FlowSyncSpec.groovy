package org.openkilda.functionaltests.spec.northbound.flows

import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.FlowHelper
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchId
import org.openkilda.testing.Constants.DefaultRule
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.tools.IslUtils

import groovy.time.TimeCategory
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Shared

class FlowSyncSpec extends BaseSpecification {

    @Autowired
    TopologyDefinition topology
    @Autowired
    FlowHelper flowHelper
    @Autowired
    NorthboundService northboundService
    @Autowired
    PathHelper pathHelper
    @Autowired
    IslUtils islUtils
    @Autowired
    Database db

    @Shared
    int flowRulesCount = 2

    def "Able to synchronize a flow (install missing flow rules, reinstall existing) without rerouting"() {
        given: "An intermediate-switch flow with one deleted rule on each switch"
        def switches = topology.getActiveSwitches()
        def allLinks = northboundService.getAllLinks()
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            allLinks.every { link ->
                def switchIds = [link.source.switchId, link.destination.switchId]
                !(switchIds.contains(src.dpId) && switchIds.contains(dst.dpId))
            }
        } ?: assumeTrue("No suiting switches found to build an intermediate-switch flow.", false)

        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)
        def flowPath = PathHelper.convert(northboundService.getFlowPath(flow.id))

        def involvedSwitches = pathHelper.getInvolvedSwitches(flow.id)
        def ruleToDelete = getFlowRules(involvedSwitches.first().dpId).first().cookie
        involvedSwitches.each { northboundService.deleteSwitchRules(it.dpId, ruleToDelete) }
        involvedSwitches.each { sw ->
            Wrappers.wait(RULES_DELETION_TIME) { assert getFlowRules(sw.dpId).size() == flowRulesCount - 1 }
        }

        when: "Synchronize the flow"
        Map<SwitchId, Long> rulesDurationMap = involvedSwitches.collectEntries {
            [(it.dpId): getFlowRules(it.dpId).first().durationSeconds]
        }

        def syncTime = new Date()
        def rerouteResponse = northboundService.synchronizeFlow(flow.id)

        then: "The flow is not rerouted"
        int seqId = 0

        !rerouteResponse.rerouted
        rerouteResponse.path.path == flowPath
        rerouteResponse.path.path.each { assert it.seqId == seqId++ }

        PathHelper.convert(northboundService.getFlowPath(flow.id)) == flowPath
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flow.id).status == FlowState.UP }

        and: "Missing flow rules are installed (existing ones are reinstalled) on all switches"
        involvedSwitches.each { sw ->
            Wrappers.wait(RULES_INSTALLATION_TIME) {
                def flowRules = getFlowRules(sw.dpId)
                assert flowRules.size() == flowRulesCount
                flowRules.each {
                    assert it.durationSeconds < rulesDurationMap[sw.dpId] +
                            TimeCategory.minus(new Date(), syncTime).toMilliseconds() / 1000.0
                }
            }
        }

        and: "Delete the flow"
        flowHelper.deleteFlow(flow.id)
    }

    def "Able to synchronize a flow (install missing flow rules, reinstall existing) with rerouting"() {
        given: "An intermediate-switch flow with two possible paths at least and one deleted rule on each switch"
        def switches = topology.getActiveSwitches()
        List<List<PathNode>> possibleFlowPaths = []
        def allLinks = northboundService.getAllLinks()
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            possibleFlowPaths = db.getPaths(src.dpId, dst.dpId)*.path.sort { it.size() }
            allLinks.every { link ->
                def switchIds = [link.source.switchId, link.destination.switchId]
                !(switchIds.contains(src.dpId) && switchIds.contains(dst.dpId))
            } && possibleFlowPaths.size() > 1
        } ?: assumeTrue("No suiting switches found to build an intermediate-switch flow with two possible paths " +
                "at least.", false)

        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)
        def flowPath = PathHelper.convert(northboundService.getFlowPath(flow.id))

        def involvedSwitches = pathHelper.getInvolvedSwitches(flow.id)
        def ruleToDelete = getFlowRules(involvedSwitches.first().dpId).first().cookie
        involvedSwitches.each { northboundService.deleteSwitchRules(it.dpId, ruleToDelete) }
        involvedSwitches.each { sw ->
            Wrappers.wait(RULES_DELETION_TIME) { assert getFlowRules(sw.dpId).size() == flowRulesCount - 1 }
        }

        and: "Make one of the alternative flow paths more preferable than the current one"
        possibleFlowPaths.findAll { it != flowPath }.each { pathHelper.makePathMorePreferable(it, flowPath) }

        when: "Synchronize the flow"
        Map<SwitchId, Long> rulesDurationMap = involvedSwitches.collectEntries {
            [(it.dpId): getFlowRules(it.dpId).first().durationSeconds]
        }

        def syncTime = new Date()
        def rerouteResponse = northboundService.synchronizeFlow(flow.id)

        then: "The flow is rerouted"
        def newFlowPath = PathHelper.convert(northboundService.getFlowPath(flow.id))
        int seqId = 0

        rerouteResponse.rerouted
        rerouteResponse.path.path == newFlowPath
        rerouteResponse.path.path.each { assert it.seqId == seqId++ }

        newFlowPath != flowPath
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flow.id).status == FlowState.UP }

        and: "Flow rules are installed/reinstalled on switches remained from the original flow path"
        def involvedSwitchesAfterSync = pathHelper.getInvolvedSwitches(flow.id)
        involvedSwitchesAfterSync.findAll { it in involvedSwitches }.each { sw ->
            Wrappers.wait(RULES_INSTALLATION_TIME) {
                def flowRules = getFlowRules(sw.dpId)
                assert flowRules.size() == flowRulesCount
                flowRules.each {
                    assert it.durationSeconds < rulesDurationMap[sw.dpId] +
                            TimeCategory.minus(new Date(), syncTime).toMilliseconds() / 1000.0
                }
            }
        }

        and: "Flow rules are installed on new switches involved in the current flow path"
        involvedSwitchesAfterSync.findAll { !(it in involvedSwitches) }.each { sw ->
            Wrappers.wait(RULES_INSTALLATION_TIME) { assert getFlowRules(sw.dpId).size() == flowRulesCount }
        }

        and: "Flow rules are deleted from switches that are NOT involved in the current flow path"
        involvedSwitches.findAll { !(it in involvedSwitchesAfterSync) }.each { sw ->
            Wrappers.wait(RULES_DELETION_TIME) { assert getFlowRules(sw.dpId).empty }
        } || true  // switches after sync may include all switches involved in the flow before sync

        and: "Delete the flow and link props, reset link costs"
        flowHelper.deleteFlow(flow.id)
        northboundService.deleteLinkProps(northboundService.getAllLinkProps())
        db.resetCosts()
    }

    def getFlowRules(SwitchId switchId) {
        def defaultCookies = DefaultRule.values()*.cookie
        northboundService.getSwitchRules(switchId).flowEntries.findAll { !(it.cookie in defaultCookies) }.sort()
    }
}
