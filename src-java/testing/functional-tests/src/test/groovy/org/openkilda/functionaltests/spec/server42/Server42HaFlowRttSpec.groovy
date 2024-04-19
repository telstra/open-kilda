package org.openkilda.functionaltests.spec.server42

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.model.stats.Direction.FORWARD
import static org.openkilda.functionaltests.model.stats.Direction.REVERSE
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.FLOW_RTT
import static org.openkilda.messaging.payload.flow.FlowEncapsulationType.VXLAN
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.STATS_FROM_SERVER42_LOGGING_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.HaFlowFactory
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.HaFlowExtended
import org.openkilda.functionaltests.helpers.model.SwitchRulesFactory
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.functionaltests.model.stats.FlowStats
import org.openkilda.functionaltests.model.stats.Origin
import org.openkilda.messaging.model.system.FeatureTogglesDto
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.cookie.CookieBase.CookieType

import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Shared

class Server42HaFlowRttSpec extends HealthCheckSpecification {

    @Shared
    @Autowired
    HaFlowFactory haFlowFactory

    @Shared
    @Autowired
    FlowStats flowStats

    @Autowired
    @Shared
    SwitchRulesFactory switchRulesFactory

    @Tags(TOPOLOGY_DEPENDENT)
    def "Create an Ha-Flow (#description) with server42 Rtt feature and check datapoints in tsdb"() {
        given: "Three active switches with server42 connected"
        assumeTrue((topology.getActiveServer42Switches().size() >= 3), "Unable to find active server42")

        def swT = topologyHelper.findSwitchTripletWithSharedEpInTheMiddleOfTheChainServer42Support()
        assert swT, "There is no switch triplet for the further ha-flow creation"

        when: "Set server42FlowRtt toggle to true"
        def flowRttToggleInitialState = northbound.featureToggles.server42FlowRtt
        !flowRttToggleInitialState && northbound.toggleFeature(FeatureTogglesDto.builder().server42FlowRtt(true).build())
        switchHelper.waitForS42SwRulesSetup()

        and: "server42FlowRtt is enabled on all switches"
        def initialSwitchesProps = [swT.shared, swT.ep1, swT.ep2].collectEntries { sw -> [sw, switchHelper.setServer42FlowRttForSwitch(sw, true)] }

        and: "Create Ha-Flow"
        HaFlowExtended haFlow = haFlowBuilder(swT).build().waitForBeingInState(FlowState.UP)

        then: "Check if stats for FORWARD and REVERSE directions are available for the first sub-Flow"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            def subFlow1Stats = flowStats.of(haFlow.subFlows.first().flowId)
            assert subFlow1Stats.get(FLOW_RTT, FORWARD, Origin.SERVER_42).hasNonZeroValues()
            assert subFlow1Stats.get(FLOW_RTT, REVERSE, Origin.SERVER_42).hasNonZeroValues()
        }

        and: "Check if stats for FORWARD and REVERSE directions are available for the second sub-Flow"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            def subFlow2Stats = flowStats.of(haFlow.subFlows.last().flowId)
            assert subFlow2Stats.get(FLOW_RTT, FORWARD, Origin.SERVER_42).hasNonZeroValues()
            assert subFlow2Stats.get(FLOW_RTT, REVERSE, Origin.SERVER_42).hasNonZeroValues()
        }

        cleanup: "Revert system to original state"
        haFlow && haFlow.delete() && switchHelper.verifyAbsenceOfServer42FlowRttRules(initialSwitchesProps.keySet())
        flowRttToggleInitialState != null && northbound.toggleFeature(FeatureTogglesDto.builder().server42FlowRtt(flowRttToggleInitialState).build())
        initialSwitchesProps && switchHelper.revertToOriginSwitchSetup(initialSwitchesProps, flowRttToggleInitialState)

        where:
        description                                                                  | haFlowBuilder
        "default flow encapsulation TRANSIT_VLAN"                                    | { SwitchTriplet switchTriplet -> haFlowFactory.getBuilder(switchTriplet).withSharedEndpointFullPort().withEp1FullPort().withEp2FullPort() }
        "default flow encapsulation VXLAN"                                           | { SwitchTriplet switchTriplet -> haFlowFactory.getBuilder(switchTriplet).withSharedEndpointFullPort().withEp1FullPort().withEp2FullPort().withEncapsulationType(VXLAN) }
        "shared ep is the full port and encapsulation VXLAN"                         | { SwitchTriplet switchTriplet -> haFlowFactory.getBuilder(switchTriplet).withSharedEndpointFullPort().withEncapsulationType(VXLAN) }
        "shared ep qnq and encapsulation TRANSIT_VLAN"                               | { SwitchTriplet switchTriplet -> haFlowFactory.getBuilder(switchTriplet).withSharedEndpointQnQ() }
        "tagged flow encapsulation VXLAN"                                            | { SwitchTriplet switchTriplet -> haFlowFactory.getBuilder(switchTriplet).withEncapsulationType(VXLAN) }
        "ep1 and ep2 are on the same switch and port and encapsulation TRANSIT_VLAN" | { SwitchTriplet switchTriplet -> haFlowFactory.getBuilder(switchTriplet).withEp1AndEp2SameSwitchAndPort() }
        "ep1 is the full port and encapsulation TRANSIT_VLAN"                        | { SwitchTriplet switchTriplet -> haFlowFactory.getBuilder(switchTriplet).withEp1FullPort() }
        "all endpoints qnq  and encapsulation TRANSIT_VLAN"                          | { SwitchTriplet switchTriplet -> haFlowFactory.getBuilder(switchTriplet).withEp1QnQ().withEp2QnQ().withSharedEndpointQnQ() }
        "protected path"                                                             | { SwitchTriplet switchTriplet -> haFlowFactory.getBuilder(switchTriplet).withProtectedPath(true) }
        "ep1+ep2 qnq and encapsulation TRANSIT_VLAN"                                 | { SwitchTriplet switchTriplet -> haFlowFactory.getBuilder(switchTriplet).withEp2QnQ().withEp1QnQ() }
//        known issue(qnq and VXLAN(ovs)) https://github.com/telstra/open-kilda/issues/4572
//        "ep2 qnq and encapsulation VXLAN"                                            | { SwitchTriplet switchTriplet -> haFlowFactory.getBuilder(switchTriplet).withEp2QnQ().withEncapsulationType(VXLAN) }

    }

    @Tags(HARDWARE) //not supported on a local env (the 'stub' service doesn't send real traffic through a switch)
    def "Able to synchronize an Ha-Flow(shared path: #isHaFlowWithSharedPath) with the following installation of missing server42 rules"() {
        given: "Three active switches with server42 connected"
        assert swT, "There is no switch triplet for the ha-flow creation"

        and: "Set server42FlowRtt toggle to true"
        def flowRttToggleInitialState = northbound.featureToggles.server42FlowRtt
        !flowRttToggleInitialState && northbound.toggleFeature(FeatureTogglesDto.builder().server42FlowRtt(true).build())
        switchHelper.waitForS42SwRulesSetup()

        and: "server42FlowRtt is enabled on all switches"
        def initialSwitchesProps = [swT.shared, swT.ep1, swT.ep2].collectEntries { sw -> [sw, switchHelper.setServer42FlowRttForSwitch(sw, true)] }

        and: "Create Ha-Flow"
        HaFlowExtended haFlow = haFlowFactory.getRandom(swT)
        assert isHaFlowWithSharedPath ? northboundV2.getHaFlowPaths(haFlow.haFlowId).sharedPath.forward : !northboundV2.getHaFlowPaths(haFlow.haFlowId).sharedPath.forward

        and: "Verify server42 rtt stats are available for both sub-Flows in forward and reverse direction"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            def subFlow1Stats = flowStats.of(haFlow.subFlows.first().getFlowId())
            assert subFlow1Stats.get(FLOW_RTT, FORWARD, Origin.SERVER_42).hasNonZeroValues()
            assert subFlow1Stats.get(FLOW_RTT, REVERSE, Origin.SERVER_42).hasNonZeroValues()

            def subFlow2Stats = flowStats.of(haFlow.subFlows.last().getFlowId())
            assert subFlow2Stats.get(FLOW_RTT, FORWARD, Origin.SERVER_42).hasNonZeroValues()
            assert subFlow2Stats.get(FLOW_RTT, REVERSE, Origin.SERVER_42).hasNonZeroValues()
        }

        when: "Delete ingress server42 rule(s) related to the flow on the shared switch"
        //if ha-flow doesn't have shared path, shared endpoint is y-point and has server42 flow rtt ingress rule per sub-Flows
        def switchRules = switchRulesFactory.get(swT.shared.dpId)
        def cookiesToDelete = switchRules.getRulesByCookieType(CookieType.SERVER_42_FLOW_RTT_INGRESS).cookie
        cookiesToDelete.each { switchRules.delete(it) }

        then: "System detects missing rule on the shared switch"
        Wrappers.wait(RULES_DELETION_TIME) {
            assert northbound.validateSwitch(swT.shared.dpId).rules.missing.sort() == cookiesToDelete.sort()
        }

        and: "Ha-Flow is valid and UP"
        haFlow.validate().subFlowValidationResults.each { validationInfo ->
            if (validationInfo.direction == "forward") {
                assert !validationInfo.asExpected
            } else {
                assert validationInfo.asExpected
            }
        }
        haFlow.retrieveDetails().status == FlowState.UP

        def timeWhenMissingRuleIsDetected = new Date().getTime()

        and: "The server42 stats for both sub-Flows in forward direction are not increased"
        !flowStats.of(haFlow.subFlows.first().flowId).get(FLOW_RTT, FORWARD, Origin.SERVER_42).hasNonZeroValuesAfter(timeWhenMissingRuleIsDetected)
        !flowStats.of(haFlow.subFlows.last().flowId).get(FLOW_RTT, FORWARD, Origin.SERVER_42).hasNonZeroValuesAfter(timeWhenMissingRuleIsDetected)

        and: "The server42 stats for both sub-Flows in reverse direction are increased"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT + WAIT_OFFSET) {
            assert flowStats.of(haFlow.subFlows.first().flowId).get(FLOW_RTT, REVERSE, Origin.SERVER_42).hasNonZeroValuesAfter(timeWhenMissingRuleIsDetected)
            assert flowStats.of(haFlow.subFlows.last().flowId).get(FLOW_RTT, REVERSE, Origin.SERVER_42).hasNonZeroValuesAfter(timeWhenMissingRuleIsDetected)
        }

        when: "Synchronize the Ha-Flow"
        haFlow.sync()

        then: "Missing ingress server42 rules are reinstalled on the shared switch"
        int expectedCookiesNumber = isHaFlowWithSharedPath ? 1 : 2
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            assert northbound.validateSwitch(swT.shared.dpId).rules.missing.empty
            assert switchRules.getRulesByCookieType(CookieType.SERVER_42_FLOW_RTT_INGRESS).cookie.size() == expectedCookiesNumber
        }
        def timeWhenMissingRuleIsReinstalled = new Date().getTime()

        then: "The server42 stats for both FORWARD and REVERSE directions are available for the first sub-flow"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT + WAIT_OFFSET, 1) {
            def stats = flowStats.of(haFlow.subFlows.first().flowId)
            assert stats.get(FLOW_RTT, FORWARD, Origin.SERVER_42).hasNonZeroValuesAfter(timeWhenMissingRuleIsReinstalled)
            assert stats.get(FLOW_RTT, REVERSE, Origin.SERVER_42).hasNonZeroValuesAfter(timeWhenMissingRuleIsReinstalled)
        }

        and: "The server42 stats for both FORWARD and REVERSE directions are available for the second sub-flow"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT + WAIT_OFFSET) {
            def stats = flowStats.of(haFlow.subFlows.last().flowId)
            assert stats.get(FLOW_RTT, FORWARD, Origin.SERVER_42).hasNonZeroValuesAfter(timeWhenMissingRuleIsDetected)
            assert stats.get(FLOW_RTT, REVERSE, Origin.SERVER_42).hasNonZeroValuesAfter(timeWhenMissingRuleIsDetected)
        }

        cleanup: "Revert system to original state"
        haFlow && haFlow.delete() && switchHelper.verifyAbsenceOfServer42FlowRttRules(initialSwitchesProps.keySet())
        flowRttToggleInitialState != null && northbound.toggleFeature(FeatureTogglesDto.builder().server42FlowRtt(flowRttToggleInitialState).build())
        initialSwitchesProps && switchHelper.revertToOriginSwitchSetup(initialSwitchesProps, flowRttToggleInitialState)

        where:
        isHaFlowWithSharedPath | swT
//        This case is disabled due to changes in hardware env (switch replacement is required).
//        true                   | topologyHelper.findSwitchTripletWithSharedEpThatIsNotNeighbourToEp1AndEp2Server42Support()
        false                  | topologyHelper.findSwitchTripletWithSharedEpInTheMiddleOfTheChainServer42Support()
    }
}
