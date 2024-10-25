package org.openkilda.functionaltests.spec.flows

import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowRuleEntity
import org.openkilda.functionaltests.helpers.model.SwitchRulesFactory
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchId
import org.openkilda.model.cookie.Cookie
import org.openkilda.model.cookie.CookieBase.CookieType

import groovy.time.TimeCategory
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Shared

class FlowSyncSpec extends HealthCheckSpecification {

    @Shared
    int flowRulesCount = 2
    @Autowired
    @Shared
    FlowFactory flowFactory
    @Autowired
    @Shared
    SwitchRulesFactory switchRulesFactory

    @Tags([SMOKE_SWITCHES, SMOKE])
    def "Able to synchronize a flow (install missing flow rules, reinstall existing) without rerouting"() {
        given: "An intermediate-switch flow with deleted rules on src switch"
        def switchPair = switchPairs.all().nonNeighbouring().random()

        def flow = flowFactory.getRandom(switchPair)
        def flowPath = flow.retrieveAllEntityPaths()

        def involvedSwitches = flowPath.getInvolvedSwitches()
        List<Long> rulesToDelete = getFlowRules(switchPair.src.dpId)*.cookie
        rulesToDelete.each { switchHelper.deleteSwitchRules(switchPair.src.dpId, it) }
        Wrappers.wait(RULES_DELETION_TIME) {
            assert getFlowRules(switchPair.src.dpId).size() == flowRulesCount - rulesToDelete.size()
        }
        assert  !flow.validateAndCollectDiscrepancies().isEmpty()

        when: "Synchronize the flow"
        def syncTime = new Date()
        def rerouteResponse = flow.sync()
        Wrappers.wait(WAIT_OFFSET) { assert flow.retrieveFlowStatus().status == FlowState.UP }

        then: "The flow is not rerouted"
        int seqId = 0

        !rerouteResponse.rerouted
        rerouteResponse.path.path == flowPath.getPathNodes()
        rerouteResponse.path.path.each { assert it.seqId == seqId++ }

        flow.retrieveAllEntityPaths() == flowPath

        and: "Missing flow rules are installed (existing ones are reinstalled) on all switches"
        involvedSwitches.each { sw ->
            Wrappers.wait(RULES_INSTALLATION_TIME) {
                def flowRules = getFlowRules(sw)
                assert flowRules.size() == flowRulesCount
                flowRules.each { rule ->
                    assert rule.durationSeconds < TimeCategory.minus(new Date(), syncTime).toMilliseconds() / 1000.0
                }
            }
        }

        and: "Flow is valid"
        flow.validateAndCollectDiscrepancies().isEmpty()
    }

    List<FlowRuleEntity> getFlowRules(SwitchId swId) {
        switchRulesFactory.get(swId).getRules().findAll { def cookie = new Cookie(it.cookie)
            cookie.type == CookieType.SERVICE_OR_FLOW_SEGMENT && !cookie.serviceFlag
        }.sort()
    }
}
