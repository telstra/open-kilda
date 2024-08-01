package org.openkilda.functionaltests.spec.server42

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.ResourceLockConstants.S42_TOGGLE
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.helpers.Wrappers.timedLoop
import static org.openkilda.functionaltests.helpers.model.FlowEncapsulationType.VXLAN
import static org.openkilda.functionaltests.model.stats.Direction.FORWARD
import static org.openkilda.functionaltests.model.stats.Direction.REVERSE
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.FLOW_RTT
import static org.openkilda.functionaltests.model.stats.Origin.FLOW_MONITORING
import static org.openkilda.functionaltests.model.stats.Origin.SERVER_42
import static org.openkilda.testing.Constants.PROTECTED_PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.SERVER42_STATS_LAG
import static org.openkilda.testing.Constants.STATS_FROM_SERVER42_LOGGING_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.builder.YFlowBuilder
import org.openkilda.functionaltests.helpers.model.FlowWithSubFlowsEntityPath
import org.openkilda.functionaltests.helpers.model.SwitchRulesFactory
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.functionaltests.helpers.model.YFlowExtended
import org.openkilda.functionaltests.helpers.model.YFlowFactory
import org.openkilda.functionaltests.model.stats.FlowStats
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchId
import org.openkilda.model.cookie.Cookie
import org.openkilda.model.cookie.CookieBase.CookieType
import org.openkilda.northbound.dto.v2.flows.FlowPatchEndpoint
import org.openkilda.northbound.dto.v2.yflows.SubFlowPatchPayload
import org.openkilda.northbound.dto.v2.yflows.YFlowPatchPayload
import org.openkilda.northbound.dto.v2.yflows.YFlowPatchSharedEndpointEncapsulation

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Isolated
import spock.lang.ResourceLock
import spock.lang.Shared

@ResourceLock(S42_TOGGLE)
@Isolated //s42 toggle affects all switches in the system, may lead to excess rules during sw validation in other tests
class Server42YFlowRttSpec extends HealthCheckSpecification {
    @Shared
    @Autowired
    FlowStats flowStats

    @Autowired
    @Shared
    YFlowFactory yFlowFactory

    @Shared
    @Value('${flow.sla.check.interval.seconds}')
    Integer flowSlaCheckIntervalSeconds

    @Autowired
    @Shared
    SwitchRulesFactory switchRulesFactory

    @Shared
    SwitchTriplet switchTripletWithYPointOnSharedEp

    @Shared
    SwitchTriplet switchTripletWithYPointOnSubFlowEnd

    def setupSpec() {
        switchTripletWithYPointOnSharedEp = topologyHelper.findSwitchTripletWithSharedEpInTheMiddleOfTheChainServer42Support()
        switchTripletWithYPointOnSubFlowEnd = topologyHelper.findSwitchTripletWithSharedEpEp1Ep2InChainServer42Support()
    }

    @Tags(TOPOLOGY_DEPENDENT)
    def "Create an Y-Flow (#description) with server42 Rtt feature and check datapoints in tsdb"() {
        given: "Three active switches with server42 connected"
        assumeTrue((topology.getActiveServer42Switches().size() >= 3), "Unable to find active server42")

        def swT = isSharedEndpointYPoint ? switchTripletWithYPointOnSharedEp : switchTripletWithYPointOnSubFlowEnd
        assert swT, "There is no switch triplet for the further Y-Flow creation"

        when: "Set server42FlowRtt toggle to true"
        !featureToggles.getFeatureToggles().server42FlowRtt && featureToggles.server42FlowRtt(true)
        switchHelper.waitForS42SwRulesSetup()

        and: "server42FlowRtt is enabled on all switches"
        def initialSwitchesProps = [swT.shared, swT.ep1, swT.ep2].collectEntries { sw -> [sw, switchHelper.setServer42FlowRttForSwitch(sw, true, true)] }

        and: "Create a Y-Flow"
        YFlowExtended yFlow = setupRequiredParams(yFlowFactory.getBuilder(swT)).create()
        assert isSharedEndpointYPoint ? yFlow.sharedEndpoint.switchId == yFlow.yPoint : yFlow.sharedEndpoint.switchId != yFlow.yPoint

        then: "Check if stats for FORWARD and REVERSE directions are available for the first sub-flow"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT + SERVER42_STATS_LAG, 1) {
            def subFlow1Stats = flowStats.of(yFlow.subFlows.first().flowId)
            assert subFlow1Stats.get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
            assert subFlow1Stats.get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValues()
        }

        and: "Check if stats for FORWARD and REVERSE directions are available for the second sub-flow"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT + SERVER42_STATS_LAG, 1) {
            def subFlow2Stats = flowStats.of(yFlow.subFlows.last().flowId)
            assert subFlow2Stats.get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
            assert subFlow2Stats.get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValues()
        }

        when: "Delete Y-Flow"
        yFlow && yFlow.delete()

        then: "All Server42 flow-related rules are deleted"
        withPool {
            Wrappers.wait(RULES_INSTALLATION_TIME) {
                initialSwitchesProps.keySet().eachParallel { sw ->
                    assert switchRulesFactory.get(sw.dpId).getRules().findAll {
                        new Cookie(it.cookie).getType() in [CookieType.SERVER_42_FLOW_RTT_INPUT,
                                                            CookieType.SERVER_42_FLOW_RTT_INGRESS]
                    }.empty
                }
            }
        }

        where:
        description                                                                        | isSharedEndpointYPoint | setupRequiredParams
        "ep1 and ep2 default port, shared ep is y-point, encapsulation TRANSIT_VLAN"       | true                   | { YFlowBuilder builder -> builder.withEp1Vlan(0).withEp2Vlan(0).build() }
        "ep1 and ep2 default port, ep1/ep2 is y-point, encapsulation VXLAN"                | false                  | { YFlowBuilder builder -> builder.withEp1Vlan(0).withEp2Vlan(0).withEncapsulationType(VXLAN).build() }
        "shared ep qnq, shared ep is y-point, encapsulation VXLAN"                         | true                   | { YFlowBuilder builder -> builder.withSharedEpQnQ().withEncapsulationType(VXLAN).build() }
        "tagged flow, shared ep is y-point, encapsulation VXLAN"                           | true                   | { YFlowBuilder builder -> builder.withEncapsulationType(VXLAN).build() }
        "ep1 and ep2 are same switch+port, ep1/ep2 is y-point, encapsulation TRANSIT_VLAN" | false                  | { YFlowBuilder builder -> builder.withEp1AndEp2SameSwitchAndPort().build() }
        "ep1 is the full port, ep1/ep2 is y-point, encapsulation TRANSIT_VLAN"             | false                  | { YFlowBuilder builder -> builder.withEp1Vlan(0).build() }
        "all endpoints qnq, shared ep is y-point, encapsulation TRANSIT_VLAN"              | true                   | { YFlowBuilder builder -> builder.withSharedEpQnQ().withEp1QnQ().withEp2QnQ().build() }
        "tagged flow, shared ep is y-point, protected path, encapsulation VXLAN"           | true                   | { YFlowBuilder builder -> builder.withProtectedPath(true).withEncapsulationType(VXLAN).build() }
        "ep1+ep2 qnq, ep1/ep2 is y-point, encapsulation TRANSIT_VLAN"                      | false                  | { YFlowBuilder builder -> builder.withEp1QnQ().withEp2QnQ().build() }
    }

    @Tags([TOPOLOGY_DEPENDENT])
    def "Y-Flow rtt stats are available if both endpoints are connected to the same server42(same pop)"() {
        given: "Three active switches with server42 connected"
        assumeTrue((topology.getActiveServer42Switches().size() >= 3), "Unable to find active server42")

        def swT = isSharedEndpointYPoint ? switchTripletWithYPointOnSharedEp : switchTripletWithYPointOnSubFlowEnd

        and: "server42FlowRtt feature enabled globally and on src/dst switch"
        !featureToggles.getFeatureToggles().server42FlowRtt && featureToggles.server42FlowRtt(true)
        switchHelper.waitForS42SwRulesSetup()

        [swT.shared, swT.ep1, swT.ep2].collectEntries { sw -> [sw, switchHelper.setServer42FlowRttForSwitch(sw, true)] }

        when: "Create a Y-Flow"
        def yFlow = yFlowFactory.getRandom(swT)
        assert isSharedEndpointYPoint ? yFlow.sharedEndpoint.switchId == yFlow.yPoint : yFlow.sharedEndpoint.switchId != yFlow.yPoint


        then: "Involved switches pass switch validation"
        List<SwitchId> involvedSwitches = yFlow.retrieveAllEntityPaths().getInvolvedSwitches()
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            switchHelper.validateAndCollectFoundDiscrepancies(involvedSwitches).isEmpty()
        }

        and: "Stats for both directions are available for the first sub-flow"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            verifyAll(flowStats.of(yFlow.subFlows.first().flowId)) { subFlow1Stats ->
                assert subFlow1Stats.get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
                assert subFlow1Stats.get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValues()
            }
        }

        and: "Stats for both directions are available for the second sub-flow"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            verifyAll(flowStats.of(yFlow.subFlows.last().flowId)) { subFlow2Stats ->
                assert subFlow2Stats.get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
                assert subFlow2Stats.get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValues()
            }
        }

        when: "Disable flow rtt on shared switch"
        //for y-flow shared switch is src sw
        switchHelper.setServer42FlowRttForSwitch(swT.shared, false)
        Wrappers.wait(RULES_INSTALLATION_TIME, 3) {
            assert !switchHelper.validateAndCollectFoundDiscrepancies(swT.shared.dpId).isPresent()
        }

        then: "Stats are available in REVERSE direction for both sub-flows"
        def checkpointTime = new Date().getTime() + SERVER42_STATS_LAG * 1000

        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            verifyAll() {
                assert flowStats.of(yFlow.subFlows.first().flowId).get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
                assert flowStats.of(yFlow.subFlows.last().flowId).get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
            }
        }

        and: "Stats are absent in FORWARD direction for both sub-flows"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            verifyAll() {
                assert !flowStats.of(yFlow.subFlows.first().flowId).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
                assert !flowStats.of(yFlow.subFlows.first().flowId).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
            }
        }

        where:
        isSharedEndpointYPoint << [true, false]
    }

    @Tags(LOW_PRIORITY)
    def "Rtt statistic is available for a Y-Flow in case switch is not connected to server42"() {
        given: "Three active switches with server42 connected"
        assumeTrue((topology.getActiveServer42Switches().size() >= 3), "Unable to find active server42")

        and: "Switches triplet with ONLY shared switch that supports server42 feature"
        def swT = topologyHelper.findSwitchTripletWithOnlySharedSwServer42Support()
        assumeTrue(swT as boolean, "Unable to find requested switchTriplet")

        and: "server42FlowRtt feature enabled globally and switch ON for appropriate switches(swT)"
        !featureToggles.getFeatureToggles().server42FlowRtt && featureToggles.server42FlowRtt(true)
        switchHelper.waitForS42SwRulesSetup()

        [swT.shared].collectEntries { sw -> [sw, switchHelper.setServer42FlowRttForSwitch(sw, true)] }

        when: "Create a Y-Flow"
        def yFlow = yFlowFactory.getRandom(swT)

        then: "Stats from server42 only for FORWARD direction are available for the first sub-flow"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            verifyAll(flowStats.of(yFlow.subFlows.first().flowId)) { subFlow1Stats ->
                assert subFlow1Stats.get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
                assert subFlow1Stats.get(FLOW_RTT, REVERSE, SERVER_42).isEmpty()
            }
        }

        and: "Stats from server42 only for FORWARD direction are available for the second sub-flow"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            verifyAll(flowStats.of(yFlow.subFlows.last().flowId)) { subFlow2Stats ->
                assert subFlow2Stats.get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
                assert subFlow2Stats.get(FLOW_RTT, REVERSE, SERVER_42).isEmpty()
            }
        }

        and: "Flow monitoring stats for REVERSE direction are available for the first sub-flow"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            verifyAll(flowStats.of(yFlow.subFlows.first().flowId)) { subFlow1Stats ->
                assert subFlow1Stats.get(FLOW_RTT, REVERSE, FLOW_MONITORING).hasNonZeroValues()
                assert subFlow1Stats.get(FLOW_RTT, FORWARD, FLOW_MONITORING).isEmpty()
            }
        }

        and: "Flow monitoring stats for REVERSE direction are available for the second sub-flow"
        Wrappers.wait(flowSlaCheckIntervalSeconds * 3, 1) {
            verifyAll(flowStats.of(yFlow.subFlows.last().flowId)) { subFlow2Stats ->
                assert subFlow2Stats.get(FLOW_RTT, REVERSE, FLOW_MONITORING).hasNonZeroValues()
                assert subFlow2Stats.get(FLOW_RTT, FORWARD, FLOW_MONITORING).isEmpty()
            }
        }

        when: "Disable server42FlowRtt on the src switch"
        switchHelper.setServer42FlowRttForSwitch(swT.shared, false)

        then: "Flow monitoring stats for FORWARD direction are available for both sub-flows"
        Wrappers.wait(flowSlaCheckIntervalSeconds * 3, 1) {
            verifyAll {
                assert flowStats.of(yFlow.subFlows.first().flowId).get(FLOW_RTT, FORWARD, FLOW_MONITORING).hasNonZeroValues()
                assert flowStats.of(yFlow.subFlows.last().flowId).get(FLOW_RTT, FORWARD, FLOW_MONITORING).hasNonZeroValues()
            }
        }
    }

    @Tags(LOW_PRIORITY)
    def "Able to swapEndpoint for a Y-Flow with enabled server42 on it"() {
        given: "Three active switches with server42 connected"
        assumeTrue((topology.getActiveServer42Switches().size() >= 3), "Unable to find active server42")

        and: "Switches triplet doesn't contain WB164 switch"
        def swT = switchTripletWithYPointOnSharedEp

        and: "server42FlowRtt feature enabled globally and switch ON for appropriate switches(swT)"
        !featureToggles.getFeatureToggles().server42FlowRtt && featureToggles.server42FlowRtt(true)
        switchHelper.waitForS42SwRulesSetup()

        def initialSwitchesProps = [swT.shared, swT.ep1, swT.ep2].collectEntries { sw -> [sw, switchHelper.setServer42FlowRttForSwitch(sw, true)] }

        and: "Create a Y-Flow"
        def yFlow = yFlowFactory.getBuilder(swT).withProtectedPath(true).build().create()
        assert yFlow.protectedPathYPoint
        String subFlow1 = yFlow.getSubFlows().first().flowId
        String subFlow2 = yFlow.getSubFlows().last().flowId

        def yFlowPathBeforeSwap = yFlow.retrieveAllEntityPaths()
        def subFlow1ProtectedPathBeforeSwap = yFlowPathBeforeSwap.subFlowPaths.find { it.flowId == subFlow1 }.protectedPath.forward.retrieveNodes()
        def subFlow2ProtectedPathBeforeSwap = yFlowPathBeforeSwap.subFlowPaths.find { it.flowId == subFlow2 }.protectedPath.forward.retrieveNodes()

        and: "Stats are available for both FORWARD and REVERSE directions are available for the first sub-flow"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            verifyAll(flowStats.of(yFlow.subFlows.first().flowId)) { subFlow1Stats ->
                assert subFlow1Stats.get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
                assert subFlow1Stats.get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValues()
            }
        }

        and: "Stats are available for both FORWARD and REVERSE directions are available for the second sub-flow"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            verifyAll(flowStats.of(yFlow.subFlows.last().flowId)) { subFlow2Stats ->
                assert subFlow2Stats.get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
                assert subFlow2Stats.get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValues()
            }
        }

        when: "Try to swap src endpoints for two flows"
        yFlow.swap()

        then: "Endpoints are successfully swapped"
        FlowWithSubFlowsEntityPath yFlowPathAfterSwap
        Wrappers.wait(PROTECTED_PATH_INSTALLATION_TIME) {
            yFlowPathAfterSwap = yFlow.retrieveAllEntityPaths()
            assert yFlowPathAfterSwap.subFlowPaths.find { it.flowId == subFlow1 }.path.forward.retrieveNodes() == subFlow1ProtectedPathBeforeSwap
            assert yFlowPathAfterSwap.subFlowPaths.find { it.flowId == subFlow2 }.path.forward.retrieveNodes() == subFlow2ProtectedPathBeforeSwap
            assert yFlow.retrieveDetails().status == FlowState.UP
        }

        and: "Y-Flow validation passes"
        yFlow.validate().asExpected

        and: "All switches are valid"
        def involvedSwitches = yFlowPathAfterSwap.getInvolvedSwitches()
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            involvedSwitches.each { swId ->
                switchHelper.validate(swId).isAsExpected()
            }
        }

        and: "Stats are available for both FORWARD and REVERSE directions are available for the first sub-flow"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            verifyAll(flowStats.of(yFlow.subFlows.first().flowId)) { subFlow1Stats ->
                assert subFlow1Stats.get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
                assert subFlow1Stats.get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValues()
            }
        }

        and: "Stats are available for both FORWARD and REVERSE directions are available for the second sub-flow"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            verifyAll(flowStats.of(yFlow.subFlows.last().flowId)) { subFlow2Stats ->
                assert subFlow2Stats.get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
                assert subFlow2Stats.get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValues()
            }
        }

        when: "Delete Y-Flow"
        yFlow && yFlow.delete()

        then: "All Server42 flow-related rules are deleted"
        withPool {
            Wrappers.wait(RULES_INSTALLATION_TIME) {
                initialSwitchesProps.keySet().eachParallel { sw ->
                    assert switchRulesFactory.get(sw.dpId).getRules().findAll {
                        new Cookie(it.cookie).getType() in [CookieType.SERVER_42_FLOW_RTT_INPUT,
                                                            CookieType.SERVER_42_FLOW_RTT_INGRESS]
                    }.empty
                }
            }
        }
    }

    @Tags(HARDWARE)
    //not supported on a local env (the 'stub' service doesn't send real traffic through a switch)
    def "Able to synchronize a Y-Flow (install missing server42 rules)"() {
        given: "Three active switches with server42 connected"
        assumeTrue((topology.getActiveServer42Switches().size() >= 3), "Unable to find active server42")

        def swT = isSharedEndpointYPoint ? switchTripletWithYPointOnSharedEp : switchTripletWithYPointOnSubFlowEnd

        and: "server42FlowRtt feature enabled globally and switch ON for appropriate switches(swT)"
        !featureToggles.getFeatureToggles().server42FlowRtt && featureToggles.server42FlowRtt(true)
        switchHelper.waitForS42SwRulesSetup()

        [swT.shared, swT.ep1, swT.ep2].collectEntries { sw -> [sw, switchHelper.setServer42FlowRttForSwitch(sw, true)] }

        and: "Create a Y-Flow"
        def yFlow = yFlowFactory.getRandom(swT)
        assert isSharedEndpointYPoint ? yFlow.sharedEndpoint.switchId == yFlow.yPoint : yFlow.sharedEndpoint.switchId != yFlow.yPoint

        and: "Stats for both directions are available for the first sub-flow"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            verifyAll(flowStats.of(yFlow.subFlows.first().flowId)) { subFlow1Stats ->
                assert subFlow1Stats.get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
                assert subFlow1Stats.get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValues()
            }
        }

        and: "Stats for both directions are available for the second sub-flow"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            verifyAll(flowStats.of(yFlow.subFlows.last().flowId)) { subFlow2Stats ->
                assert subFlow2Stats.get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
                assert subFlow2Stats.get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValues()
            }
        }

        when: "Delete ingress server42 rule related to the flow on the shared switches"
        def switchRules = switchRulesFactory.get(swT.shared.dpId)
        def cookiesToDelete = switchRules.getRulesByCookieType(CookieType.SERVER_42_FLOW_RTT_INGRESS).cookie
        cookiesToDelete.each { cookie -> switchRules.delete(cookie) }
        def timeWhenMissingRuleIsDetected = new Date().getTime() + SERVER42_STATS_LAG * 1000

        then: "System detects missing rule on the shared switch"
        Wrappers.wait(RULES_DELETION_TIME) {
            assert switchHelper.validateAndCollectFoundDiscrepancies(swT.shared.dpId).get()
                    .rules.missing*.getCookie().sort() == cookiesToDelete.sort()
        }

        and: "Y-Flow is valid and UP"
        verifyAll(yFlow.validate()) { validationResult ->
            assert !validationResult.asExpected
            validationResult.getSubFlowValidationResults().findAll { it.direction == "FORWARD" }.each {
                assert !it.asExpected
            }
            validationResult.getSubFlowValidationResults().findAll { it.direction == "REVERSE" }.each {
                assert it.asExpected
            }
        }

        yFlow.retrieveDetails().status == FlowState.UP

        then: "Stats are available in REVERSE direction for both sub-flows"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            verifyAll() {
                assert flowStats.of(yFlow.subFlows.first().flowId).get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValuesAfter(timeWhenMissingRuleIsDetected)
                assert flowStats.of(yFlow.subFlows.last().flowId).get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValuesAfter(timeWhenMissingRuleIsDetected)
            }
        }

        and: "Stats are absent in FORWARD direction for both sub-flows"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            verifyAll() {
                assert !flowStats.of(yFlow.subFlows.first().flowId).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValuesAfter(timeWhenMissingRuleIsDetected)
                assert !flowStats.of(yFlow.subFlows.last().flowId).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValuesAfter(timeWhenMissingRuleIsDetected)
            }
        }

        when: "Synchronize the Y-Flow"
        yFlow.sync()

        then: "Missing ingress server42 rule is reinstalled on the shared switch"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            assert !switchHelper.validateAndCollectFoundDiscrepancies(swT.shared.dpId).isPresent()
            assert switchRules.getRulesByCookieType(CookieType.SERVER_42_FLOW_RTT_INGRESS).cookie.size() == 2
        }
        def timeWhenMissingRuleIsReinstalled = new Date().getTime()

        then: "Server42 stats for FORWARD direction are available again"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            verifyAll() {
                assert flowStats.of(yFlow.subFlows.first().flowId).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValuesAfter(timeWhenMissingRuleIsReinstalled)
                assert flowStats.of(yFlow.subFlows.last().flowId).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValuesAfter(timeWhenMissingRuleIsReinstalled)
            }
        }

        where:
        isSharedEndpointYPoint << [true, false]
    }

    @Tags(HARDWARE)
    //not supported on a local env (the 'stub' service doesn't send real traffic through a switch)
    def "Y-Flow rtt stats are still available after updating Y-Flow: #description"() {
        given: "Three active switches with server42 connected"
        assumeTrue((topology.getActiveServer42Switches().size() >= 3), "Unable to find active server42")

        and: "Switches triplet doesn't contain WB164 switch"
        def swT = topologyHelper.findSwitchTripletServer42SupportWithSharedEpInTheMiddleOfTheChainExceptWBSw()

        and: "server42FlowRtt feature enabled globally and switch ON for appropriate switches(swT)"
        !featureToggles.getFeatureToggles().server42FlowRtt && featureToggles.server42FlowRtt(true)
        switchHelper.waitForS42SwRulesSetup()

        [swT.shared, swT.ep1, swT.ep2].collectEntries { sw -> [sw, switchHelper.setServer42FlowRttForSwitch(sw, true)] }

        and: "Create a Y-Flow"
        def yFlow = yFlowFactory.getRandom(swT)

        when: "Update the Y-Flow: #description"
        //Y-Flow has been modified in the scope of an update request preparation
        YFlowPatchPayload yFlowUpdateParams = updateRequest(yFlow)
        yFlow.partialUpdate(yFlowUpdateParams)

        then: "Y-Flow is 'Up' after updating"
        verifyAll(yFlow.retrieveDetails()) { yFlowAfterUpdate ->
            assert yFlowAfterUpdate.encapsulationType == yFlow.encapsulationType
            assert yFlowAfterUpdate.subFlows.endpoint.sort() == yFlow.subFlows.endpoint.sort()
            assert yFlowAfterUpdate.subFlows.sharedEndpoint.sort() == yFlow.subFlows.sharedEndpoint.sort()
        }

        and: "Stats for both directions are available for the first sub-flow"
        def flowUpdateTime = new Date().getTime()
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            verifyAll(flowStats.of(yFlow.subFlows.first().flowId)) { subFlow1Stats ->
                assert subFlow1Stats.get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValuesAfter(flowUpdateTime)
                assert subFlow1Stats.get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValuesAfter(flowUpdateTime)
            }
        }

        and: "Stats for both directions are available for the second sub-flow"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            verifyAll(flowStats.of(yFlow.subFlows.last().flowId)) { subFlow2Stats ->
                assert subFlow2Stats.get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValuesAfter(flowUpdateTime)
                assert subFlow2Stats.get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValuesAfter(flowUpdateTime)
            }
        }

        and: "Y-Flow is valid"
        yFlow.validate().asExpected

        and: "Each switch from triplet is valid"
        [swT.shared, swT.ep1, swT.ep2].each {
            !switchHelper.synchronizeAndCollectFixedDiscrepancies(it.dpId).isPresent()
        }

        where:
        description       | updateRequest
        "update to VXLAN" | { YFlowExtended flow ->
            flow.tap { it.encapsulationType = VXLAN }
            return YFlowPatchPayload.builder().encapsulationType(VXLAN.toString()).build()
        }

        "update to qnq"   | { YFlowExtended flow ->
            def updateRequest = YFlowPatchPayload.builder()
                    .subFlows([SubFlowPatchPayload.builder()
                                       .endpoint(FlowPatchEndpoint.builder()
                                               .innerVlanId(new Random().nextInt(4095))
                                               .build())
                                       .sharedEndpoint(YFlowPatchSharedEndpointEncapsulation.builder()
                                               .innerVlanId(new Random().nextInt(4095))
                                               .build())
                                       .flowId(flow.subFlows.flowId.first())
                                       .build(),
                               SubFlowPatchPayload.builder()
                                       .endpoint(FlowPatchEndpoint.builder()
                                               .innerVlanId(new Random().nextInt(4095))
                                               .build())
                                       .sharedEndpoint(YFlowPatchSharedEndpointEncapsulation.builder()
                                               .innerVlanId(new Random().nextInt(4095))
                                               .build())
                                       .flowId(flow.subFlows.flowId.last())
                                       .build()])
                    .build()
            updateRequest.subFlows.each { newParam ->
                flow.subFlows.find { subFlow -> subFlow.flowId == newParam.flowId }.tap {
                    it.endpoint.innerVlanId = newParam.endpoint.innerVlanId
                    it.sharedEndpoint.innerVlanId = newParam.sharedEndpoint.innerVlanId
                }
            }
            return updateRequest

        }
    }

    @Tags(LOW_PRIORITY)
    def "Y-Flow rtt stats are available only if both global and switch toggles are 'ON' on both endpoints"() {
        given: "Three active switches with server42 connected"
        assumeTrue((topology.getActiveServer42Switches().size() >= 3), "Unable to find active server42")

        def swT = isSharedEndpointYPoint ? switchTripletWithYPointOnSharedEp : switchTripletWithYPointOnSubFlowEnd
        def statsWaitSeconds = 4

        and: "server42FlowRtt toggle is turned off"
        featureToggles.getFeatureToggles().server42FlowRtt && featureToggles.server42FlowRtt(false)
        switchHelper.waitForS42SwRulesSetup(false)

        and: "server42FlowRtt is turned off on all switches"
        def initialSwitchesProps = [swT.shared, swT.ep1, swT.ep2].collectEntries { sw -> [sw, switchHelper.setServer42FlowRttForSwitch(sw, false, false)] }

        when: "Create a Y-Flow"
        def yFlow = yFlowFactory.getRandom(swT)
        assert isSharedEndpointYPoint ? yFlow.sharedEndpoint.switchId == yFlow.yPoint : yFlow.sharedEndpoint.switchId != yFlow.yPoint

        then: "Involved switches pass switch validation"
        List<SwitchId> involvedSwitches = yFlow.retrieveAllEntityPaths().getInvolvedSwitches()
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            assert switchHelper.validateAndCollectFoundDiscrepancies(involvedSwitches).isEmpty()
        }

        and: "Expect no Y-Flow rtt stats for FORWARD and REVERSE directions"
        timedLoop(statsWaitSeconds) {
            [flowStats.of(yFlow.subFlows.first().flowId), flowStats.of(yFlow.subFlows.last().flowId)].each { stats ->
                assert stats.get(FLOW_RTT, FORWARD, SERVER_42).isEmpty()
                assert stats.get(FLOW_RTT, REVERSE, SERVER_42).isEmpty()
            }
        }

        when: "Enable global rtt toggle"
        featureToggles.server42FlowRtt(true)
        switchHelper.waitForS42SwRulesSetup()

        then: "Expect no flow rtt stats for FORWARD and REVERSE direction for the first sub-flow"
        verifyAll(flowStats.of(yFlow.subFlows.first().flowId)) { subFlow1Stats ->
            assert subFlow1Stats.get(FLOW_RTT, FORWARD, SERVER_42).isEmpty()
            assert subFlow1Stats.get(FLOW_RTT, REVERSE, SERVER_42).isEmpty()
        }

        and: "Expect no flow rtt stats for FORWARD and REVERSE direction for the second sub-flow"
        verifyAll(flowStats.of(yFlow.subFlows.last().flowId)) { subFlow2Stats ->
            assert subFlow2Stats.get(FLOW_RTT, FORWARD, SERVER_42).isEmpty()
            assert subFlow2Stats.get(FLOW_RTT, REVERSE, SERVER_42).isEmpty()
        }

        when: "Enable switch rtt toggle on src and dst"
        [swT.shared, swT.ep1, swT.ep2].each {
            switchHelper.setServer42FlowRttForSwitch(it, true, true)
        }
        def checkpointTime = new Date().getTime()

        then: "Stats for FORWARD and REVERSE directions are available for the first sub-flow"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            verifyAll(flowStats.of(yFlow.subFlows.first().flowId)) { subFlow1Stats ->
                assert subFlow1Stats.get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
                assert subFlow1Stats.get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
            }
        }

        and: "Stats for FORWARD and REVERSE directions are available for the second sub-flow"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            verifyAll(flowStats.of(yFlow.subFlows.last().flowId)) { subFlow2Stats ->
                assert subFlow2Stats.get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
                assert subFlow2Stats.get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
            }
        }

        when: "Disable switch rtt toggle on ep1 and ep2 ends"
        switchHelper.setServer42FlowRttForSwitch(swT.ep1, false, true)
        switchHelper.setServer42FlowRttForSwitch(swT.ep2, false, true)
        checkpointTime = new Date().getTime()

        then: "Stats for FORWARD direction are available for both sub-flows"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT + WAIT_OFFSET, 1) {
            assert flowStats.of(yFlow.subFlows.first().flowId).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
            assert flowStats.of(yFlow.subFlows.last().flowId).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
        }

        when: "Disable global toggle"
        featureToggles.server42FlowRtt(false)
        switchHelper.waitForS42SwRulesSetup(false)

        and: "Wait for several seconds"
        checkpointTime = new Date().getTime()

        then: "Expect no flow rtt stats for FORWARD and REVERSE direction for the first sub-flow"
        verifyAll(flowStats.of(yFlow.subFlows.first().flowId)) { subFlow1Stats ->
            assert !subFlow1Stats.get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
            assert !subFlow1Stats.get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
        }

        and: "Expect no flow rtt stats for FORWARD and REVERSE direction for the second sub-flow"
        verifyAll(flowStats.of(yFlow.subFlows.last().flowId)) { subFlow2Stats ->
            !subFlow2Stats.get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
            !subFlow2Stats.get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
        }

        where:
        isSharedEndpointYPoint << [true, false]
    }

}
