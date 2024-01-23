package org.openkilda.functionaltests.spec.flows

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.rule.FlowEntry
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.cookie.Cookie
import org.openkilda.model.cookie.CookieBase.CookieType
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import groovy.time.TimeCategory
import spock.lang.Ignore
import spock.lang.Shared

class FlowSyncSpec extends HealthCheckSpecification {

    @Shared
    int flowRulesCount = 2

    @Tags([SMOKE_SWITCHES, SMOKE])
    def "Able to synchronize a flow (install missing flow rules, reinstall existing) without rerouting"() {
        given: "An intermediate-switch flow with deleted rules on src switch"
        def switchPair = switchPairs.all().nonNeighbouring().random()

        def flow = flowHelperV2.randomFlow(switchPair)
        flowHelperV2.addFlow(flow)
        def flowPath = PathHelper.convert(northbound.getFlowPath(flow.flowId))

        def involvedSwitches = pathHelper.getInvolvedSwitches(flow.flowId)
        List<Long> rulesToDelete = getFlowRules(switchPair.src)*.cookie
        rulesToDelete.each { northbound.deleteSwitchRules(switchPair.src.dpId, it) }
        Wrappers.wait(RULES_DELETION_TIME) {
            assert getFlowRules(switchPair.src).size() == flowRulesCount - rulesToDelete.size()
        }

        when: "Synchronize the flow"
        def syncTime = new Date()
        def rerouteResponse = northbound.synchronizeFlow(flow.flowId)
        Wrappers.wait(WAIT_OFFSET) { assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP }

        then: "The flow is not rerouted"
        int seqId = 0

        !rerouteResponse.rerouted
        rerouteResponse.path.path == flowPath
        rerouteResponse.path.path.each { assert it.seqId == seqId++ }

        PathHelper.convert(northbound.getFlowPath(flow.flowId)) == flowPath

        and: "Missing flow rules are installed (existing ones are reinstalled) on all switches"
        involvedSwitches.each { sw ->
            Wrappers.wait(RULES_INSTALLATION_TIME) {
                def flowRules = getFlowRules(sw)
                assert flowRules.size() == flowRulesCount
                flowRules.each {
                    assert it.durationSeconds < TimeCategory.minus(new Date(), syncTime).toMilliseconds() / 1000.0
                }
            }
        }

        and: "Flow is valid"
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }

        cleanup: "Delete the flow"
        flow && flowHelperV2.deleteFlow(flow.flowId)
    }

    List<FlowEntry> getFlowRules(Switch sw) {
        northbound.getSwitchRules(sw.dpId).flowEntries.findAll { def cookie = new Cookie(it.cookie)
            cookie.type == CookieType.SERVICE_OR_FLOW_SEGMENT && !cookie.serviceFlag
        }.sort()
    }
}
