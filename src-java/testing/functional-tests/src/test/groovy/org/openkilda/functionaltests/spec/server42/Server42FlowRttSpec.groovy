package org.openkilda.functionaltests.spec.server42

import static groovyx.gpars.GParsPool.withPool
import static org.assertj.core.api.Assertions.assertThat
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.ResourceLockConstants.S42_TOGGLE
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.STATS_FROM_SERVER42_LOGGING_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.messaging.model.system.FeatureTogglesDto
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.model.SwitchFeature
import org.openkilda.model.SwitchId
import org.openkilda.model.cookie.Cookie
import org.openkilda.model.cookie.CookieBase.CookieType
import org.openkilda.northbound.dto.v2.flows.FlowPatchEndpoint
import org.openkilda.northbound.dto.v2.flows.FlowPatchV2
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2
import org.openkilda.northbound.dto.v2.flows.SwapFlowPayload
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import groovy.time.TimeCategory
import org.springframework.beans.factory.annotation.Value
import spock.lang.Ignore
import spock.lang.Isolated
import spock.lang.Narrative
import spock.lang.ResourceLock
import spock.lang.Shared
import spock.util.mop.Use

import java.util.concurrent.TimeUnit

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
    @Value('${opentsdb.metric.prefix}')
    String metricPrefix

    @Shared
    @Value('${flow.sla.check.interval.seconds}')
    Integer flowSlaCheckIntervalSeconds

    @Tidy
    def "Create a #data.flowDescription flow with server42 Rtt feature and check datapoints in opentsdb"() {
        given: "Two active switches, src has server42 connected"
        def server42switches = topology.getActiveServer42Switches();
        assumeTrue((server42switches.size() > 0), "Unable to find active server42")
        def server42switchesDpIds = server42switches*.dpId;
        def switchPair = data.switchPair(server42switchesDpIds)
        assumeTrue(switchPair != null, "Was not able to find a switch with a server42 connected")

        when: "Set server42FlowRtt toggle to true"
        def flowRttFeatureStartState = changeFlowRttToggle(true)

        and: "server42FlowRtt is enabled on src and dst switches"
        def server42Switch = switchPair.src
        def initialSwitchRtt = [server42Switch, switchPair.dst].collectEntries { [it, changeFlowRttSwitch(it, true)] }

        and: "Create a flow"
        def flowCreateTime = new Date()
        //take shorter flowid due to https://github.com/telstra/open-kilda/issues/3720
        def flow = flowHelperV2.randomFlow(switchPair).tap { it.flowId = it.flowId.take(25) }
        flow.tap(data.flowTap)
        flowHelperV2.addFlow(flow)

        then: "Check if stats for forward are available"
        def statsData = null
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            statsData = otsdb.query(flowCreateTime, metricPrefix + "flow.rtt",
                    [flowid   : flow.flowId,
                     direction: "forward",
                     origin   : "server42"]).dps
            assert statsData && !statsData.empty
        }

        cleanup: "Revert system to original state"
        revertToOrigin([flow], flowRttFeatureStartState, initialSwitchRtt)

        where:
        data << [[
                         flowDescription: "default flow",
                         switchPair     : { List<SwitchId> switchIds -> getSwPairConnectedToS42ForSimpleFlow(switchIds) },
                         flowTap        : { FlowRequestV2 fl ->
                             fl.source.vlanId = 0
                             fl.destination.vlanId = 0
                         }
                 ],
                 [
                         flowDescription: "protected flow",
                         switchPair     : { List<SwitchId> switchIds -> getSwPairConnectedToS42ForProtectedFlow(switchIds) },
                         flowTap        : { FlowRequestV2 fl -> fl.allocateProtectedPath = true }
                 ],
                 [
                         flowDescription: "vxlan flow on NS switch",
                         switchPair     : { List<SwitchId> switchIds ->
                             getSwPairConnectedToS42ForVxlanFlowOnNonWbSwitch(switchIds) },
                         flowTap        : { FlowRequestV2 fl -> fl.encapsulationType = FlowEncapsulationType.VXLAN }
                 ],
                 [
                         flowDescription: "qinq flow",
                         switchPair     : { List<SwitchId> switchIds -> getSwPairConnectedToS42ForQinQ(switchIds) },
                         flowTap        : { FlowRequestV2 fl ->
                             fl.source.vlanId = 10
                             fl.source.innerVlanId = 100
                             fl.destination.vlanId = 20
                             fl.destination.innerVlanId = 200
                         }
                 ],
                 [
                         flowDescription: "vxlan flow on WB switch",
                         switchPair     : { List<SwitchId> switchIds ->
                             getSwPairConnectedToS42ForVxlanFlowOnWbSwitch(switchIds) },
                         flowTap        : { FlowRequestV2 fl -> fl.encapsulationType = FlowEncapsulationType.VXLAN }
                 ],
        ]
    }

    @Tidy
    def "Flow rtt stats are available in forward and reverse directions for new flows"() {
        given: "Two active switches with switch having server42"
        def server42switches = topology.getActiveServer42Switches()
        def server42switchesDpIds = server42switches*.dpId
        SwitchPair switchPair = topologyHelper.switchPairs.collectMany { [it, it.reversed] }.find {
            [it.src, it.dst].every { it.dpId in server42switchesDpIds }
        }
        assumeTrue(switchPair != null, "Was not able to find a switch with a server42 connected")
        and: "server42FlowRtt feature toggle is set to true"
        def flowRttFeatureStartState = changeFlowRttToggle(true)

        and: "server42FlowRtt is enabled on src and dst switches"
        def server42Switch = switchPair.src
        def initialSwitchRtt = [server42Switch, switchPair.dst].collectEntries { [it, changeFlowRttSwitch(it, true)] }

        when: "Create a flow for forward metric"
        def flowCreateTime = new Date()
        def flow = flowHelperV2.randomFlow(switchPair).tap { it.flowId = it.flowId.take(25) }
        flowHelperV2.addFlow(flow)

        and: "Create a reversed flow for backward metric"
        def reversedFlow = flowHelperV2.randomFlow(switchPair.reversed, false, [flow]).tap {
            flowId = it.flowId.take(25)
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
                def amountOfRules = northbound.getSwitchProperties(it.dpId).multiTable ? 4 : 2
                assert northbound.getSwitchRules(it.dpId).flowEntries.findAll {
                    new Cookie(it.cookie).getType() in  [CookieType.SERVER_42_FLOW_RTT_INPUT,
                                                         CookieType.SERVER_42_FLOW_RTT_INGRESS]
                }.size() == amountOfRules
            }
        }

        and: "Involved switches pass switch validation"
        pathHelper.getInvolvedSwitches(flow.flowId).each { sw ->
            verifyAll(northbound.validateSwitch(sw.dpId)) {
                rules.missing.empty
                rules.excess.empty
                rules.misconfigured.empty
                meters.missing.empty
                meters.excess.empty
                meters.misconfigured.empty
            }
        }

        and: "Check if stats for forward are available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            def statsData = otsdb.query(flowCreateTime, metricPrefix + "flow.rtt",
                    [flowid   : flow.flowId,
                     direction: "forward",
                     origin   : "server42"]).dps
            assert statsData && !statsData.empty
        }

        and: "Check if stats for reverse are available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            def statsData = otsdb.query(flowCreateTime, metricPrefix + "flow.rtt",
                    [flowid   : reversedFlow.flowId,
                     direction: "reverse",
                     origin   : "server42"]).dps
            assert statsData && !statsData.empty
        }

        cleanup: "Revert system to original state"
        revertToOrigin([flow, reversedFlow], flowRttFeatureStartState, initialSwitchRtt)
    }

    @Tidy
    def "Flow rtt stats are available only if both global and switch toggles are 'on' on both endpoints"() {
        given: "Two active switches with having server42"
        def server42switches = topology.getActiveServer42Switches()
        def server42switchesDpIds = server42switches*.dpId
        def switchPair = topologyHelper.switchPairs.collectMany { [it, it.reversed] }.find {
            [it.src, it.dst].every { it.dpId in server42switchesDpIds }
        }
        assumeTrue(switchPair != null, "Was not able to find a switch with a server42 connected")
        def statsWaitSeconds = 4

        and: "server42FlowRtt toggle is turned off"
        def flowRttFeatureStartState = changeFlowRttToggle(false)

        and: "server42FlowRtt is turned off on src and dst"
        def initialSwitchRtt = [switchPair.src, switchPair.dst].collectEntries { [it, changeFlowRttSwitch(it, false)] }

        and: "Flow for forward metric is created"
        def checkpointTime = new Date()
        def flow = flowHelperV2.randomFlow(switchPair).tap { it.flowId = it.flowId.take(25) }
        flowHelperV2.addFlow(flow)

        and: "Reversed flow for backward metric is created"
        def reversedFlow = flowHelperV2.randomFlow(switchPair.reversed, false, [flow]).tap {
            it.flowId = it.flowId.take(25)
        }
        flowHelperV2.addFlow(reversedFlow)

        expect: "Involved switches pass switch validation"
        Wrappers.wait(RULES_INSTALLATION_TIME) { //wait for s42 rules
            pathHelper.getInvolvedSwitches(flow.flowId).each { sw ->
                verifyAll(northbound.validateSwitch(sw.dpId)) {
                    rules.missing.empty
                    rules.excess.empty
                    rules.misconfigured.empty
                    meters.missing.empty
                    meters.excess.empty
                    meters.misconfigured.empty
                }
            }
        }

        when: "Wait for several seconds"
        TimeUnit.SECONDS.sleep(statsWaitSeconds)

        then: "Expect no flow rtt stats for forward flow"
        otsdb.query(checkpointTime, metricPrefix + "flow.rtt",
                [flowid   : flow.flowId,
                 direction: "forward",
                 origin   : "server42"]).dps.isEmpty()

        and: "Expect no flow rtt stats for reversed flow"
        otsdb.query(checkpointTime, metricPrefix + "flow.rtt",
                [flowid   : reversedFlow.flowId,
                 direction: "reverse",
                 origin   : "server42"]).dps.isEmpty()

        when: "Enable global rtt toggle"
        changeFlowRttToggle(true)

        and: "Wait for several seconds"
        checkpointTime = new Date()
        TimeUnit.SECONDS.sleep(statsWaitSeconds)

        then: "Expect no flow rtt stats for forward flow"
        otsdb.query(checkpointTime, metricPrefix + "flow.rtt",
                [flowid   : flow.flowId,
                 direction: "forward",
                 origin   : "server42"]).dps.isEmpty()

        and: "Expect no flow rtt stats for reversed flow"
        otsdb.query(checkpointTime, metricPrefix + "flow.rtt",
                [flowid   : reversedFlow.flowId,
                 direction: "reverse",
                 origin   : "server42"]).dps.isEmpty()

        when: "Enable switch rtt toggle on src and dst"
        changeFlowRttSwitch(switchPair.src, true)
        changeFlowRttSwitch(switchPair.dst, true)
        checkpointTime = new Date()

        then: "Stats for forward flow are available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            def statsData = otsdb.query(checkpointTime, metricPrefix + "flow.rtt",
                    [flowid   : flow.flowId,
                     direction: "forward",
                     origin   : "server42"]).dps
            assert statsData && !statsData.empty
        }

        and: "Stats for reversed flow are available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            def statsData = otsdb.query(checkpointTime, metricPrefix + "flow.rtt",
                    [flowid   : reversedFlow.flowId,
                     direction: "reverse",
                     origin   : "server42"]).dps
            assert statsData && !statsData.empty
        }
        //behavior below varies between physical switches and virtual stub due to 'turning rule'
        //will be resolved as part of https://github.com/telstra/open-kilda/issues/3809
//        when: "Disable switch rtt toggle on dst (still enabled on src)"
//        changeFlowRttSwitch(switchPair.dst, false)
//        checkpointTime = new Date()
//
//        then: "Stats for forward flow are available"
//        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
//            def statsData = otsdb.query(checkpointTime, metricPrefix + "flow.rtt",
//                    [flowid   : flow.flowId,
//                     direction: "forward",
//                     origin   : "server42"]).dps
//            assert statsData && !statsData.empty
//        }
//
//        and: "Stats for reversed flow are available"
//        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
//            def statsData = otsdb.query(checkpointTime, metricPrefix + "flow.rtt",
//                    [flowid   : reversedFlow.flowId,
//                     direction: "reverse",
//                     origin   : "server42"]).dps
//            assert statsData && !statsData.empty
//        }
        when: "Disable global toggle"
        changeFlowRttToggle(false)

        and: "Wait for several seconds"
        checkpointTime = new Date()
        TimeUnit.SECONDS.sleep(statsWaitSeconds)

        then: "Expect no flow rtt stats for forward flow"
        otsdb.query(checkpointTime, metricPrefix + "flow.rtt",
                [flowid   : flow.flowId,
                 direction: "forward",
                 origin   : "server42"]).dps.isEmpty()

        and: "Expect no flow rtt stats for reversed flow"
        otsdb.query(checkpointTime, metricPrefix + "flow.rtt",
                [flowid   : reversedFlow.flowId,
                 direction: "reverse",
                 origin   : "server42"]).dps.isEmpty()

        cleanup: "Revert system to original state"
        revertToOrigin([flow, reversedFlow], flowRttFeatureStartState, initialSwitchRtt)
    }

    @Tidy
    @Tags([TOPOLOGY_DEPENDENT])
    def "Flow rtt stats are available if both endpoints are conected to the same server42 (same pop)"() {
        given: "Two active switches connected to the same server42 instance"
        def switchPair = topologyHelper.switchPairs.collectMany { [it, it.reversed] }.find {
            it.src.prop?.server42MacAddress != null && it.src.prop?.server42MacAddress == it.dst.prop?.server42MacAddress
        }
        assumeTrue(switchPair != null, "Was not able to find 2 switches on the same server42")
        and: "server42FlowRtt feature enabled globally and on src/dst switch"
        def flowRttFeatureStartState = changeFlowRttToggle(true)
        def initialSwitchRtt = [switchPair.src, switchPair.dst].collectEntries { [it, changeFlowRttSwitch(it, true)] }

        when: "Create a flow"
        def checkpointTime = new Date()
        def flow = flowHelperV2.randomFlow(switchPair).tap { it.flowId = it.flowId.take(25) }
        flowHelperV2.addFlow(flow)

        then: "Involved switches pass switch validation"
        Wrappers.wait(RULES_INSTALLATION_TIME) {  //wait for s42 rules
            pathHelper.getInvolvedSwitches(flow.flowId).each { sw ->
                verifyAll(northbound.validateSwitch(sw.dpId)) {
                    rules.missing.empty
                    rules.excess.empty
                    rules.misconfigured.empty
                    meters.missing.empty
                    meters.excess.empty
                    meters.misconfigured.empty
                }
            }
        }

        and: "Stats for both directions are available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            def fwData = otsdb.query(checkpointTime, metricPrefix + "flow.rtt",
                    [flowid   : flow.flowId,
                     direction: "forward",
                     origin   : "server42"]).dps
            assert fwData && !fwData.empty
            def reverseData = otsdb.query(checkpointTime, metricPrefix + "flow.rtt",
                    [flowid   : flow.flowId,
                     direction: "reverse",
                     origin   : "server42"]).dps
            assert reverseData && !reverseData.empty
        }

        when: "Disable flow rtt on dst switch"
        changeFlowRttSwitch(switchPair.dst, false)
        sleep(1000)
        checkpointTime = new Date()

        then: "Stats are available in forward direction"
        TimeUnit.SECONDS.sleep(4) //remove after removing workaround below
        //not until https://github.com/telstra/open-kilda/issues/3809
//        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
//            def fwData = otsdb.query(checkpointTime, metricPrefix + "flow.rtt",
//                    [flowid   : flow.flowId,
//                     direction: "forward",
//                     origin   : "server42"]).dps
//            assert fwData && !fwData.empty
//        }

        and: "Stats are not available in reverse direction"
        otsdb.query(checkpointTime, metricPrefix + "flow.rtt",
                [flowid   : flow.flowId,
                 direction: "reverse",
                 origin   : "server42"]).dps.isEmpty()

        cleanup: "Revert system to original state"
        revertToOrigin([flow], flowRttFeatureStartState, initialSwitchRtt)
    }

    @Tidy
    @Tags(HARDWARE) //not supported on a local env (the 'stub' service doesn't send real traffic through a switch)
    def "Able to synchronize a flow (install missing server42 rules)"() {
        given: "A switch pair connected to server42"
        def server42switches = topology.getActiveServer42Switches();
        def server42switchesDpIds = server42switches*.dpId;
        def switchPair = getSwPairConnectedToS42ForSimpleFlow(server42switchesDpIds)
        assumeTrue(switchPair != null, "Was not able to find a switch with a server42 connected")
        //enable server42 in featureToggle and on the switches
        def flowRttFeatureStartState = changeFlowRttToggle(true)
        def server42Switch = switchPair.src
        def initialSwitchRtt = [server42Switch, switchPair.dst].collectEntries { [it, changeFlowRttSwitch(it, true)] }

        and: "A flow on the given switch pair"
        def flowCreateTime = new Date()
        //take shorter flowid due to https://github.com/telstra/open-kilda/issues/3720
        def flow = flowHelperV2.randomFlow(switchPair).tap { it.flowId = it.flowId.take(25) }
        flowHelperV2.addFlow(flow)

        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            assert otsdb.query(flowCreateTime, metricPrefix + "flow.rtt",
                    [flowid   : flow.flowId,
                     direction: "forward",
                     origin   : "server42"]).dps.size() > 0
            assert otsdb.query(flowCreateTime, metricPrefix + "flow.rtt",
                    [flowid   : flow.flowId,
                     direction: "reverse",
                     origin   : "server42"]).dps.size() > 0
        }

        when: "Delete ingress server42 rule related to the flow on the src switch"
        def cookieToDelete = northbound.getSwitchRules(switchPair.src.dpId).flowEntries.find {
            new Cookie(it.cookie).getType() == CookieType.SERVER_42_FLOW_RTT_INGRESS
        }.cookie
        northbound.deleteSwitchRules(switchPair.src.dpId, cookieToDelete)

        then: "System detects missing rule on the src switch"
        Wrappers.wait(RULES_DELETION_TIME) {
            assert northbound.validateSwitch(switchPair.src.dpId).rules.missing == [cookieToDelete]
        }

        and: "Flow is valid and UP"
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }
        northbound.getFlowStatus(flow.flowId).status == FlowState.UP

        and: "server42 stats for forward direction are not increased"
        and: "server42 stats for reverse direction are increased"
        TimeUnit.SECONDS.sleep(STATS_FROM_SERVER42_LOGGING_TIMEOUT + WAIT_OFFSET)
        def statsDataForward = otsdb.query(flowCreateTime, metricPrefix + "flow.rtt",
                [flowid   : flow.flowId,
                 direction: "forward",
                 origin   : "server42"]).dps
        def statsDataReverse = otsdb.query(flowCreateTime, metricPrefix + "flow.rtt",
                [flowid   : flow.flowId,
                 direction: "reverse",
                 origin   : "server42"]).dps
        def newStatsDataReverse
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            newStatsDataReverse = otsdb.query(flowCreateTime, metricPrefix + "flow.rtt",
                    [flowid   : flow.flowId,
                     direction: "reverse",
                     origin   : "server42"]).dps
            assert newStatsDataReverse.size() > statsDataReverse.size()
        }
        otsdb.query(flowCreateTime, metricPrefix + "flow.rtt",
                [flowid   : flow.flowId,
                 direction: "forward",
                 origin   : "server42"]).dps.size() == statsDataForward.size()

        when: "Synchronize the flow"
        with(northbound.synchronizeFlow(flow.flowId)) { !it.rerouted }

        then: "Missing ingress server42 rule is reinstalled on the src switch"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            assert northbound.validateSwitch(switchPair.src.dpId).rules.missing.empty
            assert northbound.getSwitchRules(switchPair.src.dpId).flowEntries.findAll {
                new Cookie(it.cookie).getType() == CookieType.SERVER_42_FLOW_RTT_INGRESS
            }*.cookie.size() == 1
        }

        then: "server42 stats for forward direction are available again"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT + WAIT_OFFSET, 1) {
            assert otsdb.query(flowCreateTime, metricPrefix + "flow.rtt",
                    [flowid   : flow.flowId,
                     direction: "forward",
                     origin   : "server42"]).dps.size() > statsDataForward.size()
            assert otsdb.query(flowCreateTime, metricPrefix + "flow.rtt",
                    [flowid   : flow.flowId,
                     direction: "reverse",
                     origin   : "server42"]).dps.size() > newStatsDataReverse.size()
        }

        cleanup: "Revert system to original state"
        revertToOrigin([flow], flowRttFeatureStartState, initialSwitchRtt)
    }

    @Tidy
    @Tags(LOW_PRIORITY)
    def "Able to swapEndpoint for a flow with enabled server42 on it"() {
        given: "Two switch pairs with different src switches and the same dst switch"
        def switchIdsConnectedToS42 = topology.getActiveServer42Switches()*.dpId
        SwitchPair fl2SwPair = null
        SwitchPair fl1SwPair = topologyHelper.switchPairs.collectMany { [it, it.reversed] }.find { firstSwP ->
            def firstOk =
                    firstSwP.src.dpId in switchIdsConnectedToS42 && !switchIdsConnectedToS42.contains(firstSwP.dst.dpId)
            fl2SwPair = topologyHelper.switchPairs.collectMany { [it, it.reversed] }.find { secondSwP ->
                secondSwP.dst.dpId == firstSwP.dst.dpId && secondSwP.src.dpId != firstSwP.src.dpId &&
                        !switchIdsConnectedToS42.contains(secondSwP.src.dpId)
            }
            firstOk && fl2SwPair
        }
        assumeTrue(fl1SwPair.asBoolean() && fl2SwPair.asBoolean(),
                "Required switch pairs were not found in the given topology")

        and: "server42 is enabled on the src sw of the first switch pair"
        def flowRttFeatureStartState = changeFlowRttToggle(true)
        changeFlowRttSwitch(fl1SwPair.src, true)

        and: "Two flows on the given switch pairs"
        def flowCreateTime = new Date()
        //take shorter flowid due to https://github.com/telstra/open-kilda/issues/3720
        def flow1 = flowHelperV2.randomFlow(fl1SwPair).tap { it.flowId = it.flowId.take(25) }
        def flow2 = flowHelperV2.randomFlow(fl2SwPair).tap { it.flowId = it.flowId.take(25) }
        flowHelperV2.addFlow(flow1)
        flowHelperV2.addFlow(flow2)

        //make sure stats for the flow1 in forward directions are available and not available for the flow2
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            assert !otsdb.query(flowCreateTime, metricPrefix + "flow.rtt",
                    [flowid   : flow1.flowId,
                     direction: "forward",
                     origin   : "server42"]).dps.isEmpty()
            assert otsdb.query(flowCreateTime, metricPrefix + "flow.rtt",
                    [flowid   : flow2.flowId,
                     direction: "forward",
                     origin   : "server42"]).dps.isEmpty()
        }

        when: "Try to swap src endpoints for two flows"
        def flow1Src = flow2.source
        def flow1Dst = flow1.destination
        def flow2Src = flow1.source
        def flow2Dst = flow2.destination
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.flowId, flow1Src, flow1Dst),
                new SwapFlowPayload(flow2.flowId, flow2Src, flow2Dst))

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
            involvedSwitches.each { swId ->
                northbound.validateSwitch(swId).verifyRuleSectionsAreEmpty(swId, ["missing", "excess", "misconfigured"])
            }
        }
        def switchesAreValid = true

        and: "server42 stats are available for the flow2 in the forward direction"
        //TODO(andriidovhan) it should be reworked,
        // STATS_FROM_SERVER42_LOGGING_TIMEOUT is just a random number that was decided to be used as timeout,
        // it has no real application and we should not rely on it here
        TimeUnit.SECONDS.sleep(STATS_FROM_SERVER42_LOGGING_TIMEOUT + WAIT_OFFSET)
        def flow1Stat = otsdb.query(flowCreateTime, metricPrefix + "flow.rtt",
                [flowid   : flow1.flowId,
                 direction: "forward",
                 origin   : "server42"]).dps
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            assert !otsdb.query(flowCreateTime, metricPrefix + "flow.rtt",
                    [flowid   : flow2.flowId,
                     direction: "forward",
                     origin   : "server42"]).dps.isEmpty()
        }

        and: "server42 stats are not available any more for the flow1 in the forward direction"
        otsdb.query(flowCreateTime, metricPrefix + "flow.rtt",
                [flowid   : flow1.flowId,
                 direction: "forward",
                 origin   : "server42"]).dps.size() == flow1Stat.size()

        cleanup:
        flow1 && flowHelperV2.deleteFlow(flow1.flowId)
        flow2 && flowHelperV2.deleteFlow(flow2.flowId)
        flowRttFeatureStartState && changeFlowRttToggle(flowRttFeatureStartState)
        fl1SwPair && changeFlowRttSwitch(fl1SwPair.src, true)
        if (!switchesAreValid) {
            involvedSwitches.each { northbound.synchronizeSwitch(it, true) }
            Wrappers.wait(RULES_INSTALLATION_TIME) {
                involvedSwitches.each { swId ->
                    assert northbound.validateSwitch(swId).verifyRuleSectionsAreEmpty(
                            swId, ["missing", "excess", "misconfigured"])
                }
            }
        }
    }

    @Tidy
    def "Rtt statistic is available for a flow in case switch is not connected to server42"() {
        given: "Two active switches, only src has server42 connected"
        def server42SwIds = topology.getActiveServer42Switches()*.dpId
        def switchPair = topologyHelper.switchPairs.collectMany { [it, it.reversed] }.find {
            it.src.dpId in server42SwIds && !(it.dst.dpId in server42SwIds)
        }
        assumeTrue(switchPair != null, "Was not able to find a switch with needed connection")

        when: "Set server42FlowRtt toggle to true"
        def flowRttFeatureStartState = changeFlowRttToggle(true)

        and: "server42FlowRtt is enabled on src switch"
        def initialSrcSwS42Props = northbound.getSwitchProperties(switchPair.src.dpId).server42FlowRtt
        changeFlowRttSwitch(switchPair.src, true)

        and: "Create a flow"
        def flowCreateTime = new Date()
        //take shorter flowid due to https://github.com/telstra/open-kilda/issues/3720
        def flow = flowHelperV2.randomFlow(switchPair).tap { it.flowId = it.flowId.take(25) }
        flowHelperV2.addFlow(flow)

        then: "Stats from server42 only for forward direction are available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            !otsdb.query(flowCreateTime, metricPrefix + "flow.rtt",
                    [flowid   : flow.flowId,
                     direction: "forward",
                     origin   : "server42"]).dps.isEmpty()
            otsdb.query(flowCreateTime, metricPrefix + "flow.rtt",
                    [flowid   : flow.flowId,
                     direction: "reverse",
                     origin   : "server42"]).dps.isEmpty()
        }

        and: "Stats from flow monitoring feature for reverse direction only are available"
        Wrappers.wait(flowSlaCheckIntervalSeconds + WAIT_OFFSET, 1) {
            def flMonitoringReverseData = otsdb.query(flowCreateTime, metricPrefix + "flow.rtt",
                    [flowid   : flow.flowId,
                     direction: "reverse",
                     origin   : "flow-monitoring"]).dps
            def flMonitoringForwardData = otsdb.query(flowCreateTime, metricPrefix + "flow.rtt",
            [flowid   : flow.flowId,
             direction: "forward",
             origin   : "flow-monitoring"]).dps
            assert flMonitoringReverseData.size() >= 1
            assert flMonitoringForwardData.isEmpty()
        }

        when: "Disable server42FlowRtt on the src switch"
        changeFlowRttSwitch(switchPair.src, false)

        then: "Stats from flow monitoring feature for forward direction are available"
        Wrappers.wait(flowSlaCheckIntervalSeconds + WAIT_OFFSET, 1) {
            otsdb.query(flowCreateTime, metricPrefix + "flow.rtt",
                    [flowid   : flow.flowId,
                     direction: "forward",
                     origin   : "flow-monitoring"]).dps.size() >= 1
        }

        cleanup: "Revert system to original state"
        flow && flowHelperV2.deleteFlow(flow.flowId)
        flowRttFeatureStartState && changeFlowRttToggle(flowRttFeatureStartState)
        initialSrcSwS42Props && changeFlowRttSwitch(switchPair.src, initialSrcSwS42Props)
    }

    @Tidy
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
        //take shorter flowid due to https://github.com/telstra/open-kilda/issues/3720
        def flow = flowHelperV2.randomFlow(switchPair).tap { it.flowId = it.flowId.take(25) }
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
        def flowUpdateTime = new Date()

        then: "Check if stats for forward/reverse directions are available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            assert !otsdb.query(flowUpdateTime, metricPrefix + "flow.rtt",
                    [flowid   : flow.flowId,
                     direction: "forward",
                     origin   : "server42"]).dps.isEmpty()
            assert !otsdb.query(flowUpdateTime, metricPrefix + "flow.rtt",
                    [flowid   : flow.flowId,
                     direction: "reverse",
                     origin   : "server42"]).dps.isEmpty()
        }

        and: "Flow is valid"
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }

        and: "The src switch is valid"
        [switchPair.src, switchPair.dst].each {
            with(northbound.validateSwitch(it.dpId)) {
                it.verifyRuleSectionsAreEmpty(switchPair.src.dpId, ["missing", "misconfigured", "excess"])
                it.verifyMeterSectionsAreEmpty(switchPair.src.dpId, ["missing", "misconfigured", "excess"])
            }
        }
        def switchesAreValid = true

        cleanup: "Revert system to original state"
        revertToOrigin([flow], flowRttFeatureStartState, initialSwitchRtt)
        !switchesAreValid && [switchPair.src, switchPair.dst].each { northbound.synchronizeSwitch(it.dpId, true) }

        where:
        data << [
                 [
                         flowDescription: "vxlan",
                         switchPair     : { List<SwitchId> switchIds ->
                             getSwPairConnectedToS42ForVxlanFlowOnNonWbSwitch(switchIds) },
                         flowTap        : { FlowRequestV2 fl -> fl.encapsulationType = FlowEncapsulationType.VXLAN }
                 ],
                 [
                         flowDescription: "qinq",
                         switchPair     : { List<SwitchId> switchIds -> getSwPairConnectedToS42ForQinQ(switchIds) },
                         flowTap        : { FlowRequestV2 fl ->
                             fl.source.vlanId = 10
                             fl.source.innerVlanId = 100
                             fl.destination.vlanId = 20
                             fl.destination.innerVlanId = 200
                         }
                 ]
        ]
    }

    @Tidy
    @Ignore("https://github.com/telstra/open-kilda/issues/3814")
    @Tags(HARDWARE) //not supported on a local env (the 'stub' service doesn't send real traffic through a switch)
    def "Flow rtt stats are available after updating switch properties related to server42"(){
        given: "Two active switches, src has server42 connected with incorrect config in swProps"
        def server42switches = topology.getActiveServer42Switches()
        assumeTrue((server42switches.size() > 0), "Unable to find active server42")
        def server42switchesDpIds = server42switches*.dpId;
        def switchPair = topologyHelper.switchPairs.collectMany { [it, it.reversed] }.find {
            it.src.dpId in server42switchesDpIds && !server42switchesDpIds.contains(it.dst.dpId)
        }
        assumeTrue(switchPair != null, "Was not able to find a switch with a server42 connected")

        def flowRttFeatureStartState = changeFlowRttToggle(true)
        def initialFlowRttSw = changeFlowRttSwitch(switchPair.src, true)

        when: "Update the server42 in switch properties on ths src switch(incorrect port)"
        def newS42Port = topology.getAllowedPortsForSwitch(topology.activeSwitches.find {
            it.dpId == switchPair.src.dpId
        }).last()
        def originalSrcSwPros = northbound.getSwitchProperties(switchPair.src.dpId)
        northbound.updateSwitchProperties(switchPair.src.dpId, originalSrcSwPros.jacksonCopy().tap {
            server42Port = newS42Port
        })
        def swPropIsWrong = true

        then: "server42 rules on the switch are updated"
        def amountOfS42Rules = switchPair.src.features.contains(SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN) ? 2 : 1
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            def s42Rules = northbound.getSwitchRules(switchPair.src.dpId).flowEntries.findAll {
                it.cookie in  [SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE, SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE]
            }
            assert s42Rules.size() == amountOfS42Rules
            assert s42Rules*.instructions.applyActions.flowOutput.unique() == [newS42Port.toString()]
        }

        and: "The src switch is valid"
        with(northbound.validateSwitch(switchPair.src.dpId)) {
            it.verifyRuleSectionsAreEmpty(switchPair.src.dpId)
            it.verifyMeterSectionsAreEmpty(switchPair.src.dpId)
        }

        when: "Create a flow on the given switch pair"
        def flowCreateTime = new Date()
        //take shorter flowId due to https://github.com/telstra/open-kilda/issues/3720
        def flow = flowHelperV2.randomFlow(switchPair).tap { it.flowId = it.flowId.take(25) }
        flowHelperV2.addFlow(flow)

        then: "Flow rtt stats are not available due to incorrect s42 port on the src switch"
        Wrappers.timedLoop(STATS_FROM_SERVER42_LOGGING_TIMEOUT / 2) {
            assert otsdb.query(flowCreateTime, metricPrefix + "flow.rtt",
                    [flowid   : flow.flowId,
                     direction: "forward",
                     origin   : "server42"]).dps.isEmpty()
            assert otsdb.query(flowCreateTime, metricPrefix + "flow.rtt",
                    [flowid   : flow.flowId,
                     direction: "reverse",
                     origin   : "server42"]).dps.isEmpty()
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
        with(northbound.validateSwitch(switchPair.src.dpId)) {
            it.verifyRuleSectionsAreEmpty(switchPair.src.dpId, ["missing", "misconfigured", "excess"])
            it.verifyMeterSectionsAreEmpty(switchPair.src.dpId, ["missing", "misconfigured", "excess"])
        }
        def swIsValid = true

        and: "Flow rtt stats are available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            assert !otsdb.query(flowCreateTime, metricPrefix + "flow.rtt",
                    [flowid   : flow.flowId,
                     direction: "forward"]).dps.isEmpty()
            assert !otsdb.query(flowCreateTime, metricPrefix + "flow.rtt",
                    [flowid   : flow.flowId,
                     direction: "reverse"]).dps.isEmpty()
        }

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)
        flowRttFeatureStartState && changeFlowRttToggle(flowRttFeatureStartState)
        switchPair && changeFlowRttSwitch(switchPair.src, initialFlowRttSw)
        swPropIsWrong && northbound.updateSwitchProperties(switchPair.src.dpId, originalSrcSwPros)
        !swIsValid && northbound.synchronizeSwitch(switchPair.src.dpId, true)
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
            def amountOfS42Rules = sw.features.contains(SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN) ? 2 : 1
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
                assertThat(northbound.getSwitchRules(sw.dpId).flowEntries*.cookie.toArray())
                        .containsExactlyInAnyOrder(*sw.defaultCookies)
            }
        }
    }

    def "getSwPairConnectedToS42ForSimpleFlow"(List<SwitchId> switchIdsConnectedToS42) {
        getTopologyHelper().getSwitchPairs().collectMany { [it, it.reversed] }.find { swP ->
            [swP.dst, swP.src].every { it.dpId in switchIdsConnectedToS42 }
        }
    }

    def "getSwPairConnectedToS42ForProtectedFlow"(List<SwitchId> switchIdsConnectedToS42) {
        getTopologyHelper().getSwitchPairs().find {
            [it.dst, it.src].every { it.dpId in switchIdsConnectedToS42 } && it.paths.unique(false) {
                a, b -> a.intersect(b) == [] ? 1 : 0
            }.size() >= 2
        }
    }

    def "getSwPairConnectedToS42ForVxlanFlowOnNonWbSwitch"(List<SwitchId> switchIdsConnectedToS42) {
        getTopologyHelper().getSwitchPairs().find { swP ->
            [swP.dst, swP.src].every { it.dpId in switchIdsConnectedToS42 && !it.wb5164 } && swP.paths.findAll { path ->
                pathHelper.getInvolvedSwitches(path).every { switchHelper.isVxlanEnabled(it.dpId) }
            }
        }
    }

    def "getSwPairConnectedToS42ForQinQ"(List<SwitchId> switchIdsConnectedToS42) {
        getTopologyHelper().getSwitchPairs().find { swP ->
            [swP.dst, swP.src].every { it.dpId in switchIdsConnectedToS42 } && swP.paths.findAll { path ->
                pathHelper.getInvolvedSwitches(path).every { getNorthbound().getSwitchProperties(it.dpId).multiTable }
            }
        }
    }

    def "getSwPairConnectedToS42ForVxlanFlowOnWbSwitch"(List<SwitchId> switchIdsConnectedToS42) {
        getTopologyHelper().getSwitchPairs(true).find { swP ->
            swP.src.wb5164 && [swP.dst, swP.src].every { it.dpId in switchIdsConnectedToS42 && !it.wb5164 } &&
                    swP.paths.findAll { path ->
                pathHelper.getInvolvedSwitches(path).every {
                    getNorthbound().getSwitchProperties(it.dpId).supportedTransitEncapsulation
                            .contains(FlowEncapsulationType.VXLAN.toString().toLowerCase())
                }
            }
        }
    }
}
