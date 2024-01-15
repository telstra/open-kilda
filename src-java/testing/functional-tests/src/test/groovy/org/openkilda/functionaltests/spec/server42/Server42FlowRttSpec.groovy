package org.openkilda.functionaltests.spec.server42

import org.openkilda.functionaltests.extension.tags.IterationTag
import org.openkilda.functionaltests.helpers.model.SwitchPairs
import org.openkilda.functionaltests.model.switches.Manufacturer

import static groovyx.gpars.GParsPool.withPool
import static java.util.concurrent.TimeUnit.SECONDS
import static org.assertj.core.api.Assertions.assertThat
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.ResourceLockConstants.S42_TOGGLE
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.model.stats.Direction.FORWARD
import static org.openkilda.functionaltests.model.stats.Direction.REVERSE
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.FLOW_RTT
import static org.openkilda.functionaltests.model.stats.Origin.FLOW_MONITORING
import static org.openkilda.functionaltests.model.stats.Origin.SERVER_42
import static org.openkilda.functionaltests.model.switches.Manufacturer.WB5164
import static org.openkilda.model.FlowEncapsulationType.VXLAN
import static org.openkilda.model.SwitchFeature.KILDA_OVS_PUSH_POP_MATCH_VXLAN
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.SERVER42_STATS_LAG
import static org.openkilda.testing.Constants.STATS_FROM_SERVER42_LOGGING_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.functionaltests.model.stats.FlowStats
import org.openkilda.messaging.model.system.FeatureTogglesDto
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchFeature
import org.openkilda.model.cookie.Cookie
import org.openkilda.model.cookie.CookieBase.CookieType
import org.openkilda.northbound.dto.v2.flows.FlowPatchEndpoint
import org.openkilda.northbound.dto.v2.flows.FlowPatchV2
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2
import org.openkilda.northbound.dto.v2.flows.SwapFlowPayload
import org.openkilda.northbound.dto.v2.switches.LagPortRequest
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import groovy.time.TimeCategory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Ignore
import spock.lang.Isolated
import spock.lang.Narrative
import spock.lang.ResourceLock
import spock.lang.Shared
import spock.util.mop.Use

@Use(TimeCategory)
@Narrative("Verify that statistic is collected from server42 Rtt")
/* On local environment these tests will use stubs without sending real rtt packets across the network.
see server42-control-server-stub.
Note that on hardware env it is very important for switch to have correct time, since data in otsdb it posted using
switch timestamps, thus we may see no stats in otsdb if time on switch is incorrect
 */
@ResourceLock(S42_TOGGLE)
@Isolated //s42 toggle affects all switches in the system, may lead to excess rules during sw validation in other tests
class Server42FlowRttSpec extends HealthCheckSpecification {
    @Shared
    @Autowired
    FlowStats flowStats

    @Shared
    @Value('${flow.sla.check.interval.seconds}')
    Integer flowSlaCheckIntervalSeconds

    @Tags(TOPOLOGY_DEPENDENT)
    @IterationTag(tags = [HARDWARE], iterationNameRegex = /(NS|WB)/)
    def "Create a #flowDescription flow with server42 Rtt feature and check datapoints in tsdb"() {
        given: "Two active switches, src has server42 connected"
        def switchPair = switchPairFilter(switchPairs.all().withBothSwitchesConnectedToServer42()).random()

        when: "Set server42FlowRtt toggle to true"
        def flowRttFeatureStartState = changeFlowRttToggle(true)

        and: "server42FlowRtt is enabled on src and dst switches"
        def server42Switch = switchPair.src
        def initialSwitchRtt = [server42Switch, switchPair.dst]
                .collectEntries { [it, changeFlowRttSwitch(it, true)] }

        and: "Create a flow"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.tap(flowTap)
        flowHelperV2.addFlow(flow)

        then: "Check if stats for forward are available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            flowStats.of(flow.getFlowId()).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
        }

        cleanup: "Revert system to original state"
        revertToOrigin([flow], flowRttFeatureStartState, initialSwitchRtt)

        where:
        flowDescription           | switchPairFilter                   | flowTap
        "default flow"            | { SwitchPairs swPairs -> swPairs } | { FlowRequestV2 fl ->
                                                                                            fl.source.vlanId = 0
                                                                                            fl.destination.vlanId = 0
                                                                         }
        "protected flow"          | { SwitchPairs swPairs -> swPairs
                                    .withAtLeastNNonOverlappingPaths(2)}| { FlowRequestV2 fl ->
                                                                                    fl.allocateProtectedPath = true }
        "vxlan flow on NS switch" | { SwitchPairs swPairs -> swPairs
                                        .withBothSwitchesVxLanEnabled()
                                        .withSourceSwitchNotManufacturedBy(WB5164)
                                    }                                   | { FlowRequestV2 fl ->
                                                                                        fl.encapsulationType = VXLAN }
        "qinq flow"               | { SwitchPairs swPairs -> swPairs }  | { FlowRequestV2 fl ->
                                                                                fl.source.vlanId = 10
                                                                                fl.source.innerVlanId = 100
                                                                                fl.destination.vlanId = 20
                                                                                fl.destination.innerVlanId = 200
                                                                          }
        "vxlan flow on WB switch" | { SwitchPairs swPairs -> swPairs
                                .withBothSwitchesVxLanEnabled()
                                .withSourceSwitchManufacturedBy(WB5164)
                                    }                                   | { FlowRequestV2 fl ->
                                                                                        fl.encapsulationType = VXLAN }
    }

    def "Flow rtt stats are available in forward and reverse directions for new flows"() {
        given: "Two active switches with switch having server42"
        SwitchPair switchPair = switchPairs.all().withBothSwitchesConnectedToServer42().random()

        and: "server42FlowRtt feature toggle is set to true"
        def flowRttFeatureStartState = changeFlowRttToggle(true)

        and: "server42FlowRtt is enabled on src and dst switches"
        def server42Switch = switchPair.src
        def initialSwitchRtt = [server42Switch, switchPair.dst].collectEntries { [it, changeFlowRttSwitch(it, true)] }

        when: "Create a flow for forward metric"
        def flowCreateTime = new Date()
        def flow = flowHelperV2.randomFlow(switchPair)
        flowHelperV2.addFlow(flow)

        and: "Create a reversed flow for backward metric"
        def reversedFlow = flowHelperV2.randomFlow(switchPair.reversed, false, [flow]).tap {
            //don't pick same ports as flow1 in order to get expected amount of s42_input rules
            source.portNumber = (topology.getAllowedPortsForSwitch(switchPair.dst) - flow.destination.portNumber)[0]
            destination.portNumber = (topology.getAllowedPortsForSwitch(switchPair.src) - flow.source.portNumber)[0]
        }
        flowHelperV2.addFlow(reversedFlow)

        then: "Server42 input/ingress rules are installed"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            [switchPair.src, switchPair.dst].each {
                /** - one rule of each type for one flow;
                 * - no SERVER_42_FLOW_RTT_INGRESS cookie in singleTable;
                 * - SERVER_42_FLOW_RTT_INGRESS is installed for each different flow port
                 * (if there are 10 flows on port number 5, then there will be installed one INPUT rule);
                 * - SERVER_42_FLOW_RTT_INGRESS is installed for each flow.
                 */
                assert northbound.getSwitchRules(it.dpId).flowEntries.findAll {
                    new Cookie(it.cookie).getType() in  [CookieType.SERVER_42_FLOW_RTT_INPUT,
                                                         CookieType.SERVER_42_FLOW_RTT_INGRESS]
                }.size() == 4
            }
        }

        and: "Involved switches pass switch validation"
        switchHelper.validateAndGetFixedEntries(pathHelper.getInvolvedSwitches(flow.flowId)*.getDpId()).isEmpty()

        and: "Check if stats for forward and reverse flows are available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            assert flowStats.of(flow.getFlowId()).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
            assert flowStats.of(flow.getFlowId()).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
        }

        cleanup: "Revert system to original state"
        revertToOrigin([flow, reversedFlow], flowRttFeatureStartState, initialSwitchRtt)
    }

    def "Flow rtt stats are available only if both global and switch toggles are 'on' on both endpoints"() {
        given: "Two active switches with having server42"
        def switchPair = switchPairs.all().withBothSwitchesConnectedToServer42().random()
        def statsWaitSeconds = 4

        and: "server42FlowRtt toggle is turned off"
        def flowRttFeatureStartState = changeFlowRttToggle(false)

        and: "server42FlowRtt is turned off on src and dst"
        def initialSwitchRtt = [switchPair.src, switchPair.dst].collectEntries { [it, changeFlowRttSwitch(it, false)] }

        and: "Flow for forward metric is created"
        def flow = flowHelperV2.randomFlow(switchPair)
        flowHelperV2.addFlow(flow)

        and: "Reversed flow for backward metric is created"
        def reversedFlow = flowHelperV2.randomFlow(switchPair.reversed, false, [flow])
        flowHelperV2.addFlow(reversedFlow)

        expect: "Involved switches pass switch validation"
        Wrappers.wait(RULES_INSTALLATION_TIME) { //wait for s42 rules
            switchHelper.synchronizeAndGetFixedEntries(pathHelper.getInvolvedSwitches(flow.flowId)*.getDpId()).isEmpty()
        }

        when: "Wait for several seconds"
        SECONDS.sleep(statsWaitSeconds)

        then: "Expect no flow rtt stats for forward flow"
        flowStats.of(flow.getFlowId()).get(FLOW_RTT, FORWARD, SERVER_42).isEmpty()

        and: "Expect no flow rtt stats for reversed flow"
        flowStats.of(flow.getFlowId()).get(FLOW_RTT, REVERSE, SERVER_42).isEmpty()

        when: "Enable global rtt toggle"
        changeFlowRttToggle(true)

        and: "Wait for several seconds"
        def checkpointTime = new Date().getTime()
        SECONDS.sleep(statsWaitSeconds)

        then: "Expect no flow rtt stats for forward flow"
        flowStats.of(flow.getFlowId()).get(FLOW_RTT, FORWARD, SERVER_42).isEmpty()

        and: "Expect no flow rtt stats for reversed flow"
        flowStats.of(flow.getFlowId()).get(FLOW_RTT, REVERSE, SERVER_42).isEmpty()

        when: "Enable switch rtt toggle on src and dst"
        changeFlowRttSwitch(switchPair.src, true)
        changeFlowRttSwitch(switchPair.dst, true)
        checkpointTime = new Date().getTime()

        then: "Stats for forward and reverse flow are available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            assert flowStats.of(flow.getFlowId()).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
            //https://github.com/telstra/open-kilda/issues/4678
            //assert flowStats.of(flow.getFlowId()).get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
        }

        when: "Disable switch rtt toggle on dst (still enabled on src)"
        changeFlowRttSwitch(switchPair.dst, false)
        checkpointTime = new Date().getTime()

        then: "Stats for forward and reverse flow are available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT + WAIT_OFFSET, 1) {
            def stats = flowStats.of(flow.getFlowId())
            assert stats.get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
            //https://github.com/telstra/open-kilda/issues/4678
            //assert stats.get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
        }

        when: "Disable global toggle"
        changeFlowRttToggle(false)

        and: "Wait for several seconds"
        checkpointTime = new Date().getTime()
        SECONDS.sleep(statsWaitSeconds)

        then: "Expect no flow rtt stats for forward flow"
        !flowStats.of(flow.getFlowId()).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValuesAfter(checkpointTime)

        and: "Expect no flow rtt stats for reversed flow"
        !flowStats.of(flow.getFlowId()).get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValuesAfter(checkpointTime)

        cleanup: "Revert system to original state"
        revertToOrigin([flow, reversedFlow], flowRttFeatureStartState, initialSwitchRtt)
    }

    @Tags([TOPOLOGY_DEPENDENT])
    def "Flow rtt stats are available if both endpoints are connected to the same server42, same pop"() {
        given: "Two active switches connected to the same server42 instance"
        def switchPair = switchPairs.all().withBothSwitchesConnectedToSameServer42Instance().random()

        and: "server42FlowRtt feature enabled globally and on src/dst switch"
        def flowRttFeatureStartState = changeFlowRttToggle(true)
        def initialSwitchRtt = [switchPair.src, switchPair.dst].collectEntries { [it, changeFlowRttSwitch(it, true)] }

        when: "Create a flow"
        def checkpointTime = new Date()
        def flow = flowHelperV2.randomFlow(switchPair)
        flowHelperV2.addFlow(flow)

        then: "Involved switches pass switch validation"
        Wrappers.wait(RULES_INSTALLATION_TIME) {  //wait for s42 rules
            switchHelper.validateAndGetFixedEntries(pathHelper.getInvolvedSwitches(flow.flowId)*.getDpId()).isEmpty()
        }

        and: "Stats for both directions are available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            assert flowStats.of(flow.getFlowId()).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
            assert flowStats.of(flow.getFlowId()).get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValues()
        }

        when: "Disable flow rtt on dst switch"
        changeFlowRttSwitch(switchPair.dst, false)
        Wrappers.wait(RULES_INSTALLATION_TIME, 3) {
            assert !switchHelper.validateAndGetFixedEntries(switchPair.dst.dpId).isPresent()
        }
        checkpointTime = new Date().getTime() + SERVER42_STATS_LAG * 1000

        then: "Stats are available in forward direction"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            assert flowStats.of(flow.getFlowId()).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValuesAfter(checkpointTime)

        }

        and: "Stats are not available in reverse direction"
        !flowStats.of(flow.getFlowId()).get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValuesAfter(checkpointTime)

        cleanup: "Revert system to original state"
        revertToOrigin([flow], flowRttFeatureStartState, initialSwitchRtt)
    }

    @Tags(HARDWARE) //not supported on a local env (the 'stub' service doesn't send real traffic through a switch)
    def "Able to synchronize a flow (install missing server42 rules)"() {
        given: "A switch pair connected to server42"
        def switchPair = switchPairs.all().withBothSwitchesConnectedToServer42().random()
        //enable server42 in featureToggle and on the switches
        def flowRttFeatureStartState = changeFlowRttToggle(true)
        def server42Switch = switchPair.src
        def initialSwitchRtt = [server42Switch, switchPair.dst].collectEntries { [it, changeFlowRttSwitch(it, true)] }

        and: "A flow on the given switch pair"
        def flowCreateTime = new Date()
        def flow = flowHelperV2.randomFlow(switchPair)
        flowHelperV2.addFlow(flow)

        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            assert flowStats.of(flow.getFlowId()).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
            assert flowStats.of(flow.getFlowId()).get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValues()
        }

        when: "Delete ingress server42 rule related to the flow on the src switch"
        def cookieToDelete = northbound.getSwitchRules(switchPair.src.dpId).flowEntries.find {
            new Cookie(it.cookie).getType() == CookieType.SERVER_42_FLOW_RTT_INGRESS
        }.cookie
        northbound.deleteSwitchRules(switchPair.src.dpId, cookieToDelete)

        then: "System detects missing rule on the src switch"
        Wrappers.wait(RULES_DELETION_TIME) {
            assert switchHelper.validateAndGetFixedEntries(switchPair.src.dpId).get()
                    .rules.missing*.getCookie() == [cookieToDelete]
        }
        def timeWhenMissingRuleIsDetected = new Date().getTime()

        and: "Flow is valid and UP"
        northbound.validateFlow(flow.flowId).each { validationInfo ->

           if (validationInfo.direction == "forward") {
               assert !validationInfo.asExpected
           }
           else {
            assert validationInfo.asExpected
           }
        }

        northbound.getFlowStatus(flow.flowId).status == FlowState.UP

        and: "server42 stats for forward direction are not increased"
        !flowStats.of(flow.getFlowId()).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValuesAfter(timeWhenMissingRuleIsDetected)

        and: "server42 stats for reverse direction are increased"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT + WAIT_OFFSET) {
            flowStats.of(flow.getFlowId()).get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValuesAfter(timeWhenMissingRuleIsDetected)
        }

        when: "Synchronize the flow"
        with(northbound.synchronizeFlow(flow.flowId)) { !it.rerouted }

        then: "Missing ingress server42 rule is reinstalled on the src switch"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            assert !switchHelper.validateAndGetFixedEntries(switchPair.src.dpId).isPresent()
            assert northbound.getSwitchRules(switchPair.src.dpId).flowEntries.findAll {
                new Cookie(it.cookie).getType() == CookieType.SERVER_42_FLOW_RTT_INGRESS
            }*.cookie.size() == 1
        }
        def timeWhenMissingRuleIsReinstalled = new Date().getTime()

        then: "server42 stats for forward direction are available again"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT + WAIT_OFFSET, 1) {
            flowStats.of(flow.getFlowId()).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValuesAfter(timeWhenMissingRuleIsReinstalled)
        }

        cleanup: "Revert system to original state"
        revertToOrigin([flow], flowRttFeatureStartState, initialSwitchRtt)
    }

    @Tags(LOW_PRIORITY)
    def "Able to swapEndpoint for a flow with enabled server42 on it"() {
        given: "Two switch pairs with different src switches and the same dst switch"
        def fl1SwPair = switchPairs.all().withOnlySourceSwitchConnectedToServer42().random()
        def fl2SwPair = switchPairs.all()
                .excludeSwitches(topology.getActiveServer42Switches())
                .includeSourceSwitch(fl1SwPair.getDst())
                .excludeDestinationSwitches([fl1SwPair.getSrc()])
                .random()
                .getReversed()

        and: "server42 is enabled on the src sw of the first switch pair"
        def flowRttFeatureStartState = changeFlowRttToggle(true)
        changeFlowRttSwitch(fl1SwPair.src, true)

        and: "Two flows on the given switch pairs"
        def flowCreateTime = new Date()
        def flow1 = flowHelperV2.randomFlow(fl1SwPair)
        def flow2 = flowHelperV2.randomFlow(fl2SwPair)
        flowHelperV2.addFlow(flow1)
        flowHelperV2.addFlow(flow2)

        //make sure stats for the flow1 in forward directions are available and not available for the flow2
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            assert flowStats.of(flow1.getFlowId()).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
            assert flowStats.of(flow2.getFlowId()).get(FLOW_RTT, FORWARD, SERVER_42).isEmpty()
        }

        when: "Try to swap src endpoints for two flows"
        def flow1Src = flow2.source
        def flow1Dst = flow1.destination
        def flow2Src = flow1.source
        def flow2Dst = flow2.destination
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.flowId, flow1Src, flow1Dst),
                new SwapFlowPayload(flow2.flowId, flow2Src, flow2Dst))
        def timeWhenEndpointWereSwapped = new Date().getTime()

        then: "Endpoints are successfully swapped"
        with(response) {
            it.firstFlow.source == flow1Src
            it.firstFlow.destination == flow1Dst
            it.secondFlow.source == flow2Src
            it.secondFlow.destination == flow2Dst
        }

        def flow1Updated = northboundV2.getFlow(flow1.flowId)
        def flow2Updated = northboundV2.getFlow(flow2.flowId)
        flow1Updated.source == flow1Src
        flow1Updated.destination == flow1Dst
        flow2Updated.source == flow2Src
        flow2Updated.destination == flow2Dst

        and: "Flows validation doesn't show any discrepancies"
        [flow1, flow2].each {
            northbound.validateFlow(it.flowId).each { direction -> assert direction.asExpected }
        }

        and: "All switches are valid"
        def involvedSwitches = [fl1SwPair.src, fl1SwPair.dst, fl2SwPair.src, fl2SwPair.dst]*.dpId.unique()
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            assert switchHelper.validateAndGetFixedEntries(involvedSwitches).isEmpty()
        }

        and: "server42 stats are available for the flow2 in the forward direction"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            assert flowStats.of(flow2.getFlowId()).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
        }

        and: "server42 stats are not available any more for the flow1 in the forward direction"
        //give one second extra after swap
        !flowStats.of(flow1.getFlowId()).get(FLOW_RTT, FORWARD, SERVER_42)
                .hasNonZeroValuesAfter(timeWhenEndpointWereSwapped + 1000)

        cleanup:
        flow1 && flowHelperV2.deleteFlow(flow1.flowId)
        flow2 && flowHelperV2.deleteFlow(flow2.flowId)
        flowRttFeatureStartState && changeFlowRttToggle(flowRttFeatureStartState)
        fl1SwPair && changeFlowRttSwitch(fl1SwPair.src, true)
    }

    def "Rtt statistic is available for a flow in case switch is not connected to server42"() {
        given: "Two active switches, only src has server42 connected"
        def switchPair = switchPairs.all().withOnlySourceSwitchConnectedToServer42().random()

        when: "Set server42FlowRtt toggle to true"
        def flowRttFeatureStartState = changeFlowRttToggle(true)

        and: "server42FlowRtt is enabled on src switch"
        def initialSrcSwS42Props = switchHelper.getCachedSwProps(switchPair.src.dpId).server42FlowRtt
        changeFlowRttSwitch(switchPair.src, true)

        and: "Create a flow"
        def flow = flowHelperV2.randomFlow(switchPair)
        flowHelperV2.addFlow(flow)

        then: "Stats from server42 only for forward direction are available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            assert flowStats.of(flow.getFlowId()).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
            assert flowStats.of(flow.getFlowId()).get(FLOW_RTT, REVERSE, SERVER_42).isEmpty()
        }

        and: "Stats from flow monitoring feature for reverse direction only are available"
        Wrappers.wait(flowSlaCheckIntervalSeconds * 3, 1) {
            assert flowStats.of(flow.getFlowId()).get(FLOW_RTT, FORWARD, FLOW_MONITORING).isEmpty()
            assert flowStats.of(flow.getFlowId()).get(FLOW_RTT, REVERSE, FLOW_MONITORING).hasNonZeroValues()
        }

        when: "Disable server42FlowRtt on the src switch"
        changeFlowRttSwitch(switchPair.src, false)

        then: "Stats from flow monitoring feature for forward direction are available"
        Wrappers.wait(flowSlaCheckIntervalSeconds + WAIT_OFFSET * 2, 1) {
            assert flowStats.of(flow.getFlowId()).get(FLOW_RTT, FORWARD, FLOW_MONITORING).hasNonZeroValues()
        }

        cleanup: "Revert system to original state"
        flow && flowHelperV2.deleteFlow(flow.flowId)
        flowRttFeatureStartState && changeFlowRttToggle(flowRttFeatureStartState)
        initialSrcSwS42Props && changeFlowRttSwitch(switchPair.src, initialSrcSwS42Props)
    }

    @Tags(HARDWARE) //not supported on a local env (the 'stub' service doesn't send real traffic through a switch)
    def "Flow rtt stats are still available after updating a #data.flowDescription flow"() {
        given: "Two active switches, connected to the server42"
        def server42switches = topology.getActiveServer42Switches()
        assumeTrue((server42switches.size() > 1), "Unable to find active server42")
        def server42switchesDpIds = server42switches*.dpId;
        def switchPair = data.switchPair(server42switchesDpIds)
        assumeTrue(switchPair != null, "Was not able to find a switchPair with a server42 connection")

        and: "server42FlowRtt toggle is set to true"
        def flowRttFeatureStartState = changeFlowRttToggle(true)

        and: "server42FlowRtt is enabled on src and dst switches"
        def server42Switch = switchPair.src
        def initialSwitchRtt = [server42Switch, switchPair.dst].collectEntries { [it, changeFlowRttSwitch(it, true)] }

        and: "A flow"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.tap(data.flowTap)
        flowHelperV2.addFlow(flow)

        when: "Update the flow(vlan/innerVlan) via partialUpdate on the src/dst endpoint"
        def newSrcInnerVlanId = (flow.source.innerVlanId == 0) ? 0 : flow.source.innerVlanId + 1
        def newVlanId = flow.source.vlanId + 1
        def newDstInnerVlanId = (flow.destination.innerVlanId == 0) ? 0 : flow.destination.innerVlanId + 1
        def updateRequest = new FlowPatchV2().tap {
            source = new FlowPatchEndpoint().tap {
                vlanId = newVlanId
                innerVlanId = newSrcInnerVlanId
            }
            destination = new FlowPatchEndpoint().tap {
                innerVlanId = newDstInnerVlanId
            }
        }
        flowHelperV2.partialUpdate(flow.flowId, updateRequest)
        def flowUpdateTime = new Date().getTime()

        then: "Check if stats for forward/reverse directions are available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT + WAIT_OFFSET, 1) {
            assert flowStats.of(flow.getFlowId()).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValuesAfter(flowUpdateTime)
            assert flowStats.of(flow.getFlowId()).get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValuesAfter(flowUpdateTime)
        }

        and: "Flow is valid"
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }

        and: "The src switch is valid"
        switchHelper.synchronizeAndGetFixedEntries(switchPair.toList()*.getDpId()).isEmpty()

        cleanup: "Revert system to original state"
        revertToOrigin([flow], flowRttFeatureStartState, initialSwitchRtt)

        where:
        data << [
                 [
                         flowDescription: "vxlan",
                         switchPair     : switchPairs.all()
                                 .withBothSwitchesConnectedToServer42()
                                 .withBothSwitchesVxLanEnabled()
                                 .withSourceSwitchNotManufacturedBy(WB5164)
                                 .random(),
                         flowTap        : { FlowRequestV2 fl -> fl.encapsulationType = VXLAN }
                 ],
                 [
                         flowDescription: "qinq",
                         switchPair     : switchPairs.all().withBothSwitchesConnectedToServer42().random(),
                         flowTap        : { FlowRequestV2 fl ->
                             fl.source.vlanId = 10
                             fl.source.innerVlanId = 100
                             fl.destination.vlanId = 20
                             fl.destination.innerVlanId = 200
                         }
                 ]
        ]
    }

    @Ignore("https://github.com/telstra/open-kilda/issues/3814")
    @Tags(HARDWARE) //not supported on a local env (the 'stub' service doesn't send real traffic through a switch)
    def "Flow rtt stats are available after updating switch properties related to server42"(){
        given: "Two active switches, src has server42 connected with incorrect config in swProps"
        def switchPair = switchPairs.all().withOnlySourceSwitchConnectedToServer42().random()

        def flowRttFeatureStartState = changeFlowRttToggle(true)
        def initialFlowRttSw = changeFlowRttSwitch(switchPair.src, true)

        when: "Update the server42 in switch properties on ths src switch(incorrect port)"
        def newS42Port = topology.getAllowedPortsForSwitch(topology.activeSwitches.find {
            it.dpId == switchPair.src.dpId
        }).last()
        def originalSrcSwPros = switchHelper.getCachedSwProps(switchPair.src.dpId)
        northbound.updateSwitchProperties(switchPair.src.dpId, originalSrcSwPros.jacksonCopy().tap {
            server42Port = newS42Port
        })
        def swPropIsWrong = true

        then: "server42 rules on the switch are updated"
        def amountOfS42Rules = (switchPair.src.features.contains(SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN)
                || switchPair.src.features.contains(KILDA_OVS_PUSH_POP_MATCH_VXLAN)) ? 2 : 1
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            def s42Rules = northbound.getSwitchRules(switchPair.src.dpId).flowEntries.findAll {
                it.cookie in  [SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE, SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE]
            }
            assert s42Rules.size() == amountOfS42Rules
            assert s42Rules*.instructions.applyActions.flowOutput.unique() == [newS42Port.toString()]
        }

        and: "The src switch is valid"
        !switchHelper.synchronizeAndGetFixedEntries(switchPair.src.dpId).isPresent()

        when: "Create a flow on the given switch pair"
        def flowCreateTime = new Date()
        def flow = flowHelperV2.randomFlow(switchPair)
        flowHelperV2.addFlow(flow)

        then: "Flow rtt stats are not available due to incorrect s42 port on the src switch"
        Wrappers.timedLoop(STATS_FROM_SERVER42_LOGGING_TIMEOUT / 2) {
            assert flowStats.of(flow.getFlowId()).get(FLOW_RTT, FORWARD, SERVER_42).isEmpty()
            assert flowStats.of(flow.getFlowId()).get(FLOW_RTT, REVERSE, SERVER_42).isEmpty()
        }

        when: "Set correct config for the server42 on the src switch"
        northbound.updateSwitchProperties(switchPair.src.dpId, originalSrcSwPros)
        swPropIsWrong = false

        then: "server42 related rules are updated according to the new config"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            def swRules = northbound.getSwitchRules(switchPair.src.dpId).flowEntries
            def flowS42Rules = swRules.findAll {
                new Cookie(it.cookie).getType() in [CookieType.SERVER_42_INPUT, CookieType.SERVER_42_INGRESS]
            }
            def swS42Rules = swRules.findAll {
                it.cookie in [SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE, SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE]
            }
            assert swS42Rules.size() == amountOfS42Rules
            assert swS42Rules*.instructions.applyActions.flowOutput.unique() == [newS42Port.toString()]
            assert flowS42Rules.size() == 2
            assert flowS42Rules*.match.inPort.unique() == [originalSrcSwPros.server42Port.toString()]
        }

        and: "The src switch is valid"
        !switchHelper.synchronizeAndGetFixedEntries(switchPair.src.dpId).isPresent()

        and: "Flow rtt stats are available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            assert flowStats.of(flow.getFlowId()).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
            assert flowStats.of(flow.getFlowId()).get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValues()
        }

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)
        flowRttFeatureStartState && changeFlowRttToggle(flowRttFeatureStartState)
        switchPair && changeFlowRttSwitch(switchPair.src, initialFlowRttSw)
        swPropIsWrong && northbound.updateSwitchProperties(switchPair.src.dpId, originalSrcSwPros)
    }

    @Tags(HARDWARE)
    def "Rtt statistic is available for a flow on a LAG port"() {
        given: "Two active switches, both have server42 connected"
        def switchPair = switchPairs.all().withBothSwitchesConnectedToServer42().random()

        and: "server42FlowRtt toggle is set to true"
        def flowRttFeatureStartState = changeFlowRttToggle(true)

        and: "server42FlowRtt is enabled on src/dst switches"
        def initialSrcSwS42Props = switchHelper.getCachedSwProps(switchPair.src.dpId).server42FlowRtt
        def initialDstSwS42Props = switchHelper.getCachedSwProps(switchPair.dst.dpId).server42FlowRtt
        changeFlowRttSwitch(switchPair.src, true)
        changeFlowRttSwitch(switchPair.dst, true)

        when: "Create a LAG port on the src switch"
        def portsForLag = topology.getAllowedPortsForSwitch(switchPair.src)[-2, -1]
        def payload = new LagPortRequest(portNumbers: portsForLag)
        def lagPort = northboundV2.createLagLogicalPort(switchPair.src.dpId, payload).logicalPortNumber

        and: "Create a flow"
        def flowCreateTime = new Date()
        def flow = flowHelperV2.randomFlow(switchPair).tap {
            it.source.portNumber = lagPort
        }
        flowHelperV2.addFlow(flow)

        then: "Stats from server42 for forward/reverse directions are available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            assert flowStats.of(flow.getFlowId()).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
            assert flowStats.of(flow.getFlowId()).get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValues()
        }

        cleanup: "Revert system to original state"
        flow && flowHelperV2.deleteFlow(flow.flowId)
        lagPort && northboundV2.deleteLagLogicalPort(switchPair.src.dpId, lagPort)
        flowRttFeatureStartState && changeFlowRttToggle(flowRttFeatureStartState)
        initialSrcSwS42Props && changeFlowRttSwitch(switchPair.src, initialSrcSwS42Props)
        initialDstSwS42Props && changeFlowRttSwitch(switchPair.dst, initialSrcSwS42Props)
    }

    def changeFlowRttSwitch(Switch sw, boolean requiredState) {
        def originalProps = northbound.getSwitchProperties(sw.dpId)
        if (originalProps.server42FlowRtt != requiredState) {
            def s42Config = sw.prop
            northbound.updateSwitchProperties(sw.dpId, originalProps.jacksonCopy().tap {
                server42FlowRtt = requiredState
                server42MacAddress = s42Config ? s42Config.server42MacAddress : null
                server42Port = s42Config ? s42Config.server42Port : null
                server42Vlan = s42Config ? s42Config.server42Vlan : null
            })
        }
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            def amountOfS42Rules = (sw.features.contains(SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN)
                    || sw.features.contains(KILDA_OVS_PUSH_POP_MATCH_VXLAN)) ? 2 : 1
            def s42Rules = northbound.getSwitchRules(sw.dpId).flowEntries.findAll {
                it.cookie in  [SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE,
                               SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE]
            }
            assert requiredState ? (s42Rules.size() == amountOfS42Rules) : s42Rules.empty
        }
        return originalProps.server42FlowRtt
    }

    def changeFlowRttToggle(boolean requiredState) {
        def originalState = northbound.featureToggles.server42FlowRtt
        if (originalState != requiredState) {
            northbound.toggleFeature(FeatureTogglesDto.builder().server42FlowRtt(requiredState).build())
        }
        //not going to check rules on every switch in the system. sleep does the trick fine
        sleep(3000)
        return originalState
    }

    def revertToOrigin(flows,  flowRttFeatureStartState, initialSwitchRtt) {
        flows.each { flowHelperV2.deleteFlow(it.flowId) }
        //make sure that s42 rules are deleted
        withPool {
            Wrappers.wait(RULES_INSTALLATION_TIME) {
                initialSwitchRtt.keySet().eachParallel { sw ->
                    assert northbound.getSwitchRules(sw.dpId).flowEntries.findAll {
                        new Cookie(it.cookie).getType() in [CookieType.SERVER_42_FLOW_RTT_INPUT,
                                                            CookieType.SERVER_42_FLOW_RTT_INGRESS]
                    }.empty
                }
            }
        }
        flowRttFeatureStartState != null && changeFlowRttToggle(flowRttFeatureStartState)
        initialSwitchRtt.each { sw, state -> changeFlowRttSwitch(sw, state)  }
        initialSwitchRtt.keySet().each { Switch sw ->
            Wrappers.wait(RULES_INSTALLATION_TIME) {
                assertThat(northbound.getSwitchRules(sw.dpId).flowEntries*.cookie.toArray()).as(sw.dpId.toString())
                        .containsExactlyInAnyOrder(*sw.defaultCookies).as(sw.dpId.toString())
            }
        }
    }
}
