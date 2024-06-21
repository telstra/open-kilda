package org.openkilda.functionaltests.spec.server42

import static java.util.concurrent.TimeUnit.SECONDS
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.ResourceLockConstants.S42_TOGGLE
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.helpers.model.FlowEncapsulationType.VXLAN
import static org.openkilda.functionaltests.model.stats.Direction.FORWARD
import static org.openkilda.functionaltests.model.stats.Direction.REVERSE
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.FLOW_RTT
import static org.openkilda.functionaltests.model.stats.Origin.FLOW_MONITORING
import static org.openkilda.functionaltests.model.stats.Origin.SERVER_42
import static org.openkilda.functionaltests.model.switches.Manufacturer.WB5164
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.SERVER42_STATS_LAG
import static org.openkilda.testing.Constants.STATS_FROM_SERVER42_LOGGING_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.IterationTag
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.builder.FlowBuilder
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowDirection
import org.openkilda.functionaltests.helpers.model.FlowExtended
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.functionaltests.helpers.model.SwitchPairs
import org.openkilda.functionaltests.helpers.model.SwitchRulesFactory
import org.openkilda.functionaltests.model.stats.FlowStats
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.cookie.Cookie
import org.openkilda.model.cookie.CookieBase.CookieType
import org.openkilda.northbound.dto.v2.flows.FlowPatchEndpoint
import org.openkilda.northbound.dto.v2.flows.FlowPatchV2
import org.openkilda.northbound.dto.v2.flows.SwapFlowPayload

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

    @Autowired
    @Shared
    FlowFactory flowFactory

    @Shared
    @Autowired
    FlowStats flowStats
    @Shared
    @Value('${flow.sla.check.interval.seconds}')
    Integer flowSlaCheckIntervalSeconds

    @Autowired
    @Shared
    SwitchRulesFactory switchRulesFactory

    @Tags(TOPOLOGY_DEPENDENT)
    @IterationTag(tags = [HARDWARE], iterationNameRegex = /(NS|WB)/)
    def "Create a #flowDescription flow with server42 Rtt feature and check datapoints in tsdb"() {
        given: "Two active switches, src has server42 connected"
        SwitchPair switchPair = switchPairFilter(switchPairs.all().withBothSwitchesConnectedToServer42()).random()

        when: "Set server42FlowRtt toggle to true"
        !featureToggles.getFeatureToggles().server42FlowRtt && featureToggles.server42FlowRtt(true)
        switchHelper.waitForS42SwRulesSetup()

        and: "server42FlowRtt is enabled on src and dst switches"
        def server42Switch = switchPair.src
        [server42Switch, switchPair.dst].each { switchHelper.setServer42FlowRttForSwitch(it, true) }

        and: "Create a flow"
        FlowExtended flow = expectedFlowEntity(flowFactory.getBuilder(switchPair)).create()

        then: "Check if stats for forward are available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            flowStats.of(flow.flowId).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
        }

        where:
        flowDescription           | switchPairFilter                                 | expectedFlowEntity
        "default flow"            | { SwitchPairs swPairs -> swPairs }               | { FlowBuilder  builder -> builder.withSourceVlan(0).withDestinationVlan(0).build() }

        "protected flow"          | { SwitchPairs swPairs -> swPairs
                                    .withAtLeastNNonOverlappingPaths(2) }            | { FlowBuilder  builder -> builder.withProtectedPath(true).build() }

        "vxlan flow on NS switch" | { SwitchPairs swPairs -> swPairs
                                    .withBothSwitchesVxLanEnabled()
                                    .withSourceSwitchNotManufacturedBy(WB5164)  }     | { FlowBuilder  builder -> builder.withEncapsulationType(VXLAN).build() }

        "qinq flow"               | { SwitchPairs swPairs -> swPairs }                | { FlowBuilder  builder -> builder.withSourceVlan(10).withSourceInnerVlan(100)
                                                                                                       .withDestinationVlan(20).withDestinationInnerVlan(200).build() }
        "vxlan flow on WB switch" | { SwitchPairs swPairs -> swPairs
                                   .withBothSwitchesVxLanEnabled()
                                   .withSourceSwitchManufacturedBy(WB5164) }           | { FlowBuilder  builder -> builder.withEncapsulationType(VXLAN).build() }

    }

    def "Flow rtt stats are available in forward and reverse directions for new flows"() {
        given: "Two active switches with switch having server42"
        SwitchPair switchPair = switchPairs.all().withBothSwitchesConnectedToServer42().random()

        and: "server42FlowRtt feature toggle is set to true"
        !featureToggles.getFeatureToggles().server42FlowRtt && featureToggles.server42FlowRtt(true)
        switchHelper.waitForS42SwRulesSetup()

        and: "server42FlowRtt is enabled on src and dst switches"
        def server42Switch = switchPair.src
        [server42Switch, switchPair.dst].each { switchHelper.setServer42FlowRttForSwitch(it, true) }

        when: "Create a flow for forward metric"
        def flow = flowFactory.getRandom(switchPair)

        and: "Create a reversed flow for backward metric"
        def reversedFlow = flowFactory.getBuilder(switchPair.reversed, false, flow.occupiedEndpoints())
        //don't pick same ports as flow1 in order to get expected amount of s42_input rules
                .withSourcePort((topology.getAllowedPortsForSwitch(switchPair.dst) - flow.destination.portNumber)[0])
                .withDestinationPort((topology.getAllowedPortsForSwitch(switchPair.src) - flow.source.portNumber)[0]).build()
                .create()

        then: "Server42 input/ingress rules are installed"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            [switchPair.src, switchPair.dst].each { sw ->
                /** - one rule of each type for one flow;
                 * - no SERVER_42_FLOW_RTT_INGRESS cookie in singleTable;
                 * - SERVER_42_FLOW_RTT_INGRESS is installed for each different flow port
                 * (if there are 10 flows on port number 5, then there will be installed one INPUT rule);
                 * - SERVER_42_FLOW_RTT_INGRESS is installed for each flow.
                 */
                assert switchRulesFactory.get(sw.dpId).getServer42FlowRules().cookie.size() == 4
            }
        }

        and: "Involved switches pass switch validation"
        switchHelper.validateAndCollectFoundDiscrepancies(flow.retrieveAllEntityPaths().getInvolvedSwitches()).isEmpty()

        and: "Check if stats for forward and reverse flows are available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            assert flowStats.of(flow.flowId).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
            assert flowStats.of(reversedFlow.flowId).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
        }
    }

    def "Stats are available only if both global and switch toggles are 'on' on both endpoints"() {
        /*This test runs the last (by alphabet) on jenkins, because if it runs before other test,
        switchHelper.waitForS42SwRulesSetup() call in the next tests fails. No idea why.*/
        given: "Two active switches with having server42"
        def switchPair = switchPairs.all().withBothSwitchesConnectedToServer42().random()
        def statsWaitSeconds = 4

        and: "server42FlowRtt toggle is turned off"
        featureToggles.getFeatureToggles().server42FlowRtt && featureToggles.server42FlowRtt(false)
        switchHelper.waitForS42SwRulesSetup(false)

        and: "server42FlowRtt is turned off on src and dst"
        [switchPair.src, switchPair.dst].each{ sw -> switchHelper.setServer42FlowRttForSwitch(sw, false, false) }

        and: "Flow for forward metric is created"
        def flow = flowFactory.getRandom(switchPair)

        and: "Reversed flow for backward metric is created"
        def reversedFlow = flowFactory.getRandom(switchPair.reversed, false, FlowState.UP, flow.occupiedEndpoints())


        expect: "Involved switches pass switch validation"
        Wrappers.wait(RULES_INSTALLATION_TIME) { //wait for s42 rules
            switchHelper.synchronizeAndCollectFixedDiscrepancies(flow.retrieveAllEntityPaths().getInvolvedSwitches()).isEmpty()
        }

        when: "Wait for several seconds"
        SECONDS.sleep(statsWaitSeconds)

        then: "Expect no flow rtt stats for forward flow"
        flowStats.of(flow.flowId).get(FLOW_RTT, FORWARD, SERVER_42).isEmpty()

        and: "Expect no flow rtt stats for reversed flow"
        flowStats.of(reversedFlow.flowId).get(FLOW_RTT, REVERSE, SERVER_42).isEmpty()

        when: "Enable global rtt toggle"
        !featureToggles.getFeatureToggles().server42FlowRtt && featureToggles.server42FlowRtt(true)
        switchHelper.waitForS42SwRulesSetup()

        and: "Wait for several seconds"
        def checkpointTime = new Date().getTime()
        SECONDS.sleep(statsWaitSeconds)

        then: "Expect no flow rtt stats for forward flow"
        flowStats.of(flow.flowId).get(FLOW_RTT, FORWARD, SERVER_42).isEmpty()

        and: "Expect no flow rtt stats for reversed flow"
        flowStats.of(reversedFlow.flowId).get(FLOW_RTT, REVERSE, SERVER_42).isEmpty()

        when: "Enable switch rtt toggle on src and dst"
        switchHelper.setServer42FlowRttForSwitch(switchPair.src, true)
        switchHelper.setServer42FlowRttForSwitch(switchPair.dst, true)
        checkpointTime = new Date().getTime()

        then: "Stats for forward and reverse flow are available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT + SERVER42_STATS_LAG, 1) {
            assert flowStats.of(flow.flowId).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
            //https://github.com/telstra/open-kilda/issues/4678
            //assert flowStats.of(flow.flowId).get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
        }

        when: "Disable switch rtt toggle on dst (still enabled on src)"
        switchHelper.setServer42FlowRttForSwitch(switchPair.dst, false)
        checkpointTime = new Date().getTime()

        then: "Stats for forward and reverse flow are available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT + SERVER42_STATS_LAG, 1) {
            def stats = flowStats.of(flow.flowId)
            assert stats.get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
            //https://github.com/telstra/open-kilda/issues/4678
            //assert stats.get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
        }

        when: "Disable global toggle"
        featureToggles.getFeatureToggles().server42FlowRtt && featureToggles.server42FlowRtt(false)

        and: "Wait for several seconds"
        SECONDS.sleep(statsWaitSeconds)
        checkpointTime = new Date().getTime()

        then: "Expect no flow rtt stats for forward flow"
        !flowStats.of(flow.flowId).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValuesAfter(checkpointTime)

        and: "Expect no flow rtt stats for reversed flow"
        !flowStats.of(flow.flowId).get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
    }

    @Tags([TOPOLOGY_DEPENDENT])
    def "Flow rtt stats are available if both endpoints are connected to the same server42, same pop"() {
        given: "Two active switches connected to the same server42 instance"
        def switchPair = switchPairs.all().withBothSwitchesConnectedToSameServer42Instance().random()

        and: "server42FlowRtt feature enabled globally and on src/dst switch"
        !featureToggles.getFeatureToggles().server42FlowRtt && featureToggles.server42FlowRtt(true)
        switchHelper.waitForS42SwRulesSetup()

        [switchPair.src, switchPair.dst].each { sw -> switchHelper.setServer42FlowRttForSwitch(sw, true) }

        when: "Create a flow"
        def checkpointTime = new Date()
        def flow = flowFactory.getRandom(switchPair)

        then: "Involved switches pass switch validation"
        Wrappers.wait(RULES_INSTALLATION_TIME) {  //wait for s42 rules
            switchHelper.validateAndCollectFoundDiscrepancies(flow.retrieveAllEntityPaths().getInvolvedSwitches()).isEmpty()
        }

        and: "Stats for both directions are available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            assert flowStats.of(flow.flowId).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
            assert flowStats.of(flow.flowId).get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValues()
        }

        when: "Disable flow rtt on dst switch"
        switchHelper.setServer42FlowRttForSwitch(switchPair.dst, false)
        Wrappers.wait(RULES_INSTALLATION_TIME, 3) {
            assert !switchHelper.validateAndCollectFoundDiscrepancies(switchPair.dst.dpId).isPresent()
        }
        checkpointTime = new Date().getTime() + SERVER42_STATS_LAG * 1000

        then: "Stats are available in forward direction"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            assert flowStats.of(flow.flowId).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValuesAfter(checkpointTime)

        }

        and: "Stats are not available in reverse direction"
        !flowStats.of(flow.flowId).get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
    }

    @Tags(HARDWARE) //not supported on a local env (the 'stub' service doesn't send real traffic through a switch)
    def "Able to synchronize a flow (install missing server42 rules)"() {
        given: "A switch pair connected to server42"
        def switchPair = switchPairs.all().withBothSwitchesConnectedToServer42().random()
        //enable server42 in featureToggle and on the switches
        !featureToggles.getFeatureToggles().server42FlowRtt && featureToggles.server42FlowRtt(true)
        switchHelper.waitForS42SwRulesSetup()

        def server42Switch = switchPair.src
        [server42Switch, switchPair.dst].each { sw -> switchHelper.setServer42FlowRttForSwitch(sw, true) }

        and: "A flow on the given switch pair"
        def flow = flowFactory.getRandom(switchPair)

        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            assert flowStats.of(flow.flowId).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
            assert flowStats.of(flow.flowId).get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValues()
        }

        when: "Delete ingress server42 rule related to the flow on the src switch"
        def switchRules = switchRulesFactory.get(switchPair.src.dpId)
        def cookieToDelete = switchRules.getRulesByCookieType(CookieType.SERVER_42_FLOW_RTT_INGRESS).first().cookie
        switchRules.delete(cookieToDelete)

        then: "System detects missing rule on the src switch"
        Wrappers.wait(RULES_DELETION_TIME) {
            assert switchHelper.validateAndCollectFoundDiscrepancies(switchPair.src.dpId).get()
                    .rules.missing*.getCookie() == [cookieToDelete]
        }
        def timeWhenMissingRuleIsDetected = new Date().getTime()

        and: "Flow is valid and UP"
        def existingDiscrepancies = flow.validateAndCollectDiscrepancies()
        existingDiscrepancies.containsKey(FlowDirection.FORWARD)
        !existingDiscrepancies.containsKey(FlowDirection.REVERSE)

        flow.retrieveFlowStatus().status == FlowState.UP

        and: "server42 stats for forward direction are not increased"
        !flowStats.of(flow.flowId).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValuesAfter(timeWhenMissingRuleIsDetected)

        and: "server42 stats for reverse direction are increased"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT + WAIT_OFFSET) {
            flowStats.of(flow.flowId).get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValuesAfter(timeWhenMissingRuleIsDetected)
        }

        when: "Synchronize the flow"
        with(flow.sync()) { !it.rerouted }

        then: "Missing ingress server42 rule is reinstalled on the src switch"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            assert !switchHelper.validateAndCollectFoundDiscrepancies(switchPair.src.dpId).isPresent()
            assert switchRules.getRulesByCookieType(CookieType.SERVER_42_FLOW_RTT_INGRESS).cookie.size() == 1
        }
        def timeWhenMissingRuleIsReinstalled = new Date().getTime()

        then: "server42 stats for forward direction are available again"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT + WAIT_OFFSET, 1) {
            flowStats.of(flow.flowId).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValuesAfter(timeWhenMissingRuleIsReinstalled)
        }
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
        !featureToggles.getFeatureToggles().server42FlowRtt && featureToggles.server42FlowRtt(true)
        switchHelper.waitForS42SwRulesSetup()

        switchHelper.setServer42FlowRttForSwitch(fl1SwPair.src, true)

        and: "Two flows on the given switch pairs"
        def flow1 = flowFactory.getRandom(fl1SwPair)
        def flow2 = flowFactory.getRandom(fl2SwPair)

        //make sure stats for the flow1 in forward directions are available and not available for the flow2
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            assert flowStats.of(flow1.flowId).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
            assert flowStats.of(flow2.flowId).get(FLOW_RTT, FORWARD, SERVER_42).isEmpty()
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

        def flow1Updated = flow1.retrieveDetails()
        def flow2Updated = flow2.retrieveDetails()
        flow1Updated.source == flow1Src
        flow1Updated.destination == flow1Dst
        flow2Updated.source == flow2Src
        flow2Updated.destination == flow2Dst

        and: "Flows validation doesn't show any discrepancies"
        flow1.validateAndCollectDiscrepancies().isEmpty()
        flow2.validateAndCollectDiscrepancies().isEmpty()

        and: "All switches are valid"
        def involvedSwitches = [fl1SwPair.src, fl1SwPair.dst, fl2SwPair.src, fl2SwPair.dst]*.dpId.unique()
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            assert switchHelper.validateAndCollectFoundDiscrepancies(involvedSwitches).isEmpty()
        }

        and: "server42 stats are available for the flow2 in the forward direction"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            assert flowStats.of(flow2.flowId).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
        }

        and: "server42 stats are not available any more for the flow1 in the forward direction"
        //give one second extra after swap
        !flowStats.of(flow1.flowId).get(FLOW_RTT, FORWARD, SERVER_42)
                .hasNonZeroValuesAfter(timeWhenEndpointWereSwapped + 1000)
    }

    def "Rtt statistic is available for a flow in case switch is not connected to server42"() {
        given: "Two active switches, only src has server42 connected"
        def switchPair = switchPairs.all().withOnlySourceSwitchConnectedToServer42().random()

        when: "Set server42FlowRtt toggle to true"
        !featureToggles.getFeatureToggles().server42FlowRtt && featureToggles.server42FlowRtt(true)
        switchHelper.waitForS42SwRulesSetup()

        and: "server42FlowRtt is enabled on src switch"
        switchHelper.getCachedSwProps(switchPair.src.dpId).server42FlowRtt
        switchHelper.setServer42FlowRttForSwitch(switchPair.src, true)

        and: "Create a flow"
        def flow = flowFactory.getRandom(switchPair)

        then: "Stats from server42 only for forward direction are available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            assert flowStats.of(flow.flowId).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
            assert flowStats.of(flow.flowId).get(FLOW_RTT, REVERSE, SERVER_42).isEmpty()
        }

        and: "Stats from flow monitoring feature for reverse direction only are available"
        Wrappers.wait(flowSlaCheckIntervalSeconds * 3, 1) {
            assert flowStats.of(flow.flowId).get(FLOW_RTT, FORWARD, FLOW_MONITORING).isEmpty()
            assert flowStats.of(flow.flowId).get(FLOW_RTT, REVERSE, FLOW_MONITORING).hasNonZeroValues()
        }

        when: "Disable server42FlowRtt on the src switch"
        switchHelper.setServer42FlowRttForSwitch(switchPair.src, false)

        then: "Stats from flow monitoring feature for forward direction are available"
        Wrappers.wait(flowSlaCheckIntervalSeconds + WAIT_OFFSET * 2, 1) {
            assert flowStats.of(flow.flowId).get(FLOW_RTT, FORWARD, FLOW_MONITORING).hasNonZeroValues()
        }
    }

    @Tags(HARDWARE) //not supported on a local env (the 'stub' service doesn't send real traffic through a switch)
    def "Flow rtt stats are still available after updating a #data.flowDescription flow"() {
        given: "Two active switches, connected to the server42"
        SwitchPair switchPair = data.switchPair()
        assumeTrue(switchPair != null, "Was not able to find a switchPair with a server42 connection")

        and: "server42FlowRtt toggle is set to true"
        !featureToggles.getFeatureToggles().server42FlowRtt && featureToggles.server42FlowRtt(true)
        switchHelper.waitForS42SwRulesSetup()

        and: "server42FlowRtt is enabled on src and dst switches"
        def server42Switch = switchPair.src
        [server42Switch, switchPair.dst].each { sw -> switchHelper.setServer42FlowRttForSwitch(sw, true) }

        and: "A flow"
        FlowExtended flow = data.flowEntity(switchPair).create()

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
        flow.partialUpdate(updateRequest)
        def flowUpdateTime = new Date().getTime()

        then: "Check if stats for forward/reverse directions are available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT + WAIT_OFFSET, 1) {
            assert flowStats.of(flow.flowId).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValuesAfter(flowUpdateTime)
            assert flowStats.of(flow.flowId).get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValuesAfter(flowUpdateTime)
        }

        and: "Flow is valid"
        flow.validateAndCollectDiscrepancies().isEmpty()

        and: "The src switch is valid"
        switchHelper.synchronizeAndCollectFixedDiscrepancies(switchPair.toList()*.getDpId()).isEmpty()

        where:
        data << [
                 [
                         flowDescription: "vxlan",
                         switchPair     : {switchPairs.all()
                                 .withBothSwitchesConnectedToServer42()
                                 .withBothSwitchesVxLanEnabled()
                                 .withSourceSwitchNotManufacturedBy(WB5164)
                                 .random()} ,
                         flowEntity       : { SwitchPair swPair ->
                             flowFactory.getBuilder(swPair).withEncapsulationType(VXLAN).build() }

                 ],
                 [
                         flowDescription: "qinq",
                         switchPair     : {switchPairs.all().withBothSwitchesConnectedToServer42().random()},
                         flowEntity     : { SwitchPair swPair ->
                             flowFactory.getBuilder(swPair).withSourceVlan(10).withSourceInnerVlan(100)
                                     .withDestinationVlan(20).withDestinationInnerVlan(200).build()

                         }
                 ]
        ]
    }

    @Ignore("https://github.com/telstra/open-kilda/issues/3814")
    @Tags(HARDWARE) //not supported on a local env (the 'stub' service doesn't send real traffic through a switch)
    def "Flow rtt stats are available after updating switch properties related to server42"(){
        given: "Two active switches, src has server42 connected with incorrect config in swProps"
        def switchPair = switchPairs.all().withOnlySourceSwitchConnectedToServer42().random()

        !featureToggles.getFeatureToggles().server42FlowRtt && featureToggles.server42FlowRtt(true)
        switchHelper.waitForS42SwRulesSetup()
        switchHelper.setServer42FlowRttForSwitch(switchPair.src, true)

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
        def amountOfS42Rules = switchHelper.getExpectedS42SwitchRulesBasedOnVxlanSupport(switchPair.src.dpId)
        def switchRules = switchRulesFactory.get(switchPair.src.dpId)
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            def s42Rules = switchRules.getRules().findAll {
                it.cookie in  [SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE, SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE]
            }
            assert s42Rules.size() == amountOfS42Rules
            assert s42Rules*.instructions.applyActions.flowOutput.unique() == [newS42Port.toString()]
        }

        and: "The src switch is valid"
        !switchHelper.synchronizeAndCollectFixedDiscrepancies(switchPair.src.dpId).isPresent()

        when: "Create a flow on the given switch pair"
        def flow = flowFactory.getRandom(switchPair)

        then: "Flow rtt stats are not available due to incorrect s42 port on the src switch"
        Wrappers.timedLoop(STATS_FROM_SERVER42_LOGGING_TIMEOUT / 2) {
            assert flowStats.of(flow.flowId).get(FLOW_RTT, FORWARD, SERVER_42).isEmpty()
            assert flowStats.of(flow.flowId).get(FLOW_RTT, REVERSE, SERVER_42).isEmpty()
        }

        when: "Set correct config for the server42 on the src switch"
        northbound.updateSwitchProperties(switchPair.src.dpId, originalSrcSwPros)

        then: "server42 related rules are updated according to the new config"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            def swRules = switchRules.getRules()
            def flowS42Rules = swRules.findAll {
                new Cookie(it.cookie).getType() in [CookieType.SERVER_42_FLOW_RTT_INPUT, CookieType.SERVER_42_FLOW_RTT_INGRESS]
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
        !switchHelper.synchronizeAndCollectFixedDiscrepancies(switchPair.src.dpId).isPresent()

        and: "Flow rtt stats are available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            assert flowStats.of(flow.flowId).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
            assert flowStats.of(flow.flowId).get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValues()
        }
    }

    @Tags(HARDWARE)
    def "Rtt statistic is available for a flow on a LAG port"() {
        given: "Two active switches, both have server42 connected"
        def switchPair = switchPairs.all().withBothSwitchesConnectedToServer42().random()

        and: "server42FlowRtt toggle is set to true"
        !featureToggles.getFeatureToggles().server42FlowRtt && featureToggles.server42FlowRtt(true)
        switchHelper.waitForS42SwRulesSetup()

        and: "server42FlowRtt is enabled on src/dst switches"
        [switchPair.src, switchPair.dst].each { sw -> switchHelper.setServer42FlowRttForSwitch(sw, true) }

        when: "Create a LAG port on the src switch"
        def portsForLag = topology.getAllowedPortsForSwitch(switchPair.src)[-2, -1]
        def lagPort = switchHelper.createLagLogicalPort(switchPair.src.dpId, portsForLag as Set).logicalPortNumber

        and: "Create a flow"
        def flow = flowFactory.getBuilder(switchPair).withSourcePort(lagPort).build().create()

        then: "Stats from server42 for forward/reverse directions are available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            assert flowStats.of(flow.flowId).get(FLOW_RTT, FORWARD, SERVER_42).hasNonZeroValues()
            assert flowStats.of(flow.flowId).get(FLOW_RTT, REVERSE, SERVER_42).hasNonZeroValues()
        }
    }
}
