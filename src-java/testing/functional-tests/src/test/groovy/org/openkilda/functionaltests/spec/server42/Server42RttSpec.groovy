package org.openkilda.functionaltests.spec.server42

import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.STATS_LOGGING_TIMEOUT

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.model.system.FeatureTogglesDto
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.northbound.dto.v1.switches.SwitchPropertiesDto

import groovy.time.TimeCategory
import org.springframework.beans.factory.annotation.Value
import spock.lang.Ignore
import spock.lang.Narrative
import spock.lang.Shared
import spock.util.mop.Use

import java.util.concurrent.TimeUnit

@Use(TimeCategory)
@Narrative("Verify that statistic is collected from server42 Rtt")
class Server42RttSpec extends HealthCheckSpecification {
    @Shared
    @Value('${opentsdb.metric.prefix}')
    String metricPrefix

    @Ignore("fix ASAP")
    def "Create two flow with server42 Rtt feature and check datapoints in opentsdb"() {
        given: "Two active neighboring switches one with server42 and one without"

        def server42switches = topology.getActiveServer42Switches();

        def server42switchesDpIds = server42switches*.dpId;

        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find {
            it.src.dpId in server42switchesDpIds && !server42switchesDpIds.contains(it.dst.dpId)
        }

        assumeTrue("Was not able to find a switch with a server42 connected", switchPair != null)

        def flowRttFeatureStartState = isEnabledFlowRtt();

        when: "Set server42FlowRtt toggle to true"
        enableFlowRtt()

        and: "Set server 42 src switch enabled"
        def server42Switch = switchPair.src
        def flowRttSwitchFeatureStartState = isEnabledFlowRttForSwitch(server42Switch);
        enableFlowRttForSwitch(server42Switch)

        and: "Flow for forward metric created"
        def flowCreateTime = new Date()
        def flow = flowHelperV2.randomFlow(switchPair)
        flowHelperV2.addFlow(flow)

        and: "Reversed flow for backward metric created"
        def reversedFlow = flowHelperV2.randomFlow(switchPair.reversed)
        flowHelperV2.addFlow(reversedFlow)

        then: "Check if stats for forward is available"
        def statsData = null
        Wrappers.wait(STATS_LOGGING_TIMEOUT, 1) {
            statsData = otsdb.query(flowCreateTime, metricPrefix + "flow.rtt",
                    [flowid   : flow.flowId,
                     direction: "forward"]).dps
            assert statsData && !statsData.empty
        }
        and: "Check if stats for reverse is available"
        Wrappers.wait(STATS_LOGGING_TIMEOUT, 1) {
            statsData = otsdb.query(flowCreateTime, metricPrefix + "flow.rtt",
                    [flowid   : reversedFlow.flowId,
                     direction: "reverse"]).dps
            assert statsData && !statsData.empty
        }
        and: "Cleanup: revert system to original state"
        revertToOrigin(flow, reversedFlow, flowRttFeatureStartState, flowRttSwitchFeatureStartState, server42Switch)
    }

    def "Create two flow with server42 Rtt feature and check feature togglers"() {
        given: "Two active neighboring switches one with server42 and one without"

        def server42switches = topology.getActiveServer42Switches();

        def server42switchesDpIds = server42switches*.dpId;

        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find {
            it.src.dpId in server42switchesDpIds && !server42switchesDpIds.contains(it.dst.dpId)
        }

        assumeTrue("Was not able to find a switch with a server42 connected", switchPair != null)

        def server42Switch = switchPair.src

        when: "Set server42FlowRtt toggle to false"
        def flowRttFeatureStartState = isEnabledFlowRtt();
        disableFlowRtt()

        and: "Set server42Switch FlowRtt deactivated"
        def flowRttSwitchFeatureStartState = isEnabledFlowRttForSwitch(server42Switch);
        disableFlowRttForSwitch(server42Switch)

        and: "Flow for forward metric created"
        def checkpointTime = new Date()
        def flow = flowHelperV2.randomFlow(switchPair)
        flowHelperV2.addFlow(flow)

        and: "Reversed flow for backward metric created"
        def reversedFlow = flowHelperV2.randomFlow(switchPair.reversed)
        flowHelperV2.addFlow(reversedFlow)

        sleep(TimeUnit.SECONDS.toMillis(15))

        def statsData = null

        and: "Check if stats for forward is not available"
        statsData = otsdb.query(checkpointTime, metricPrefix + "flow.rtt",
                [flowid   : flow.flowId,
                 direction: "forward"]).dps
        then: "No data points for forward flow found"
        statsData == null || statsData.size() == 0

        when: "Check if stats for reverse is not available"
        statsData = otsdb.query(checkpointTime, metricPrefix + "flow.rtt",
                [flowid   : reversedFlow.flowId,
                 direction: "reverse"]).dps
        then: "No data points for reverse flow found"
        statsData == null || statsData.size() == 0

        when: "We enable global toggler"
        enableFlowRtt()
        checkpointTime = new Date()
        sleep(TimeUnit.SECONDS.toMillis(15))
        and: "Check if stats for forward is not available"
        statsData = otsdb.query(checkpointTime, metricPrefix + "flow.rtt",
                [flowid   : flow.flowId,
                 direction: "forward"]).dps
        then: "No data points for forward flow found"
        statsData == null || statsData.size() == 0

        when: "Check if stats for reverse is not available"
        statsData = otsdb.query(checkpointTime, metricPrefix + "flow.rtt",
                [flowid   : reversedFlow.flowId,
                 direction: "reverse"]).dps
        then: "No data points for reverse flow found"
        statsData == null || statsData.size() == 0

        when: "We enable switch toggler"
        enableFlowRttForSwitch(server42Switch)
        checkpointTime = new Date()
        then: "Check if stats for forward is available"
        Wrappers.wait(STATS_LOGGING_TIMEOUT, 1) {
            statsData = otsdb.query(checkpointTime, metricPrefix + "flow.rtt",
                    [flowid   : flow.flowId,
                     direction: "forward"]).dps
            assert statsData && !statsData.empty
        }
        and: "Check if stats for reverse is available"
        Wrappers.wait(STATS_LOGGING_TIMEOUT, 1) {
            statsData = otsdb.query(checkpointTime, metricPrefix + "flow.rtt",
                    [flowid   : reversedFlow.flowId,
                     direction: "reverse"]).dps
            assert statsData && !statsData.empty
        }

        when: "We disable global toggler"
        disableFlowRtt()
        then: "Check if stats for forward is not available"
        Wrappers.wait(STATS_LOGGING_TIMEOUT, 1) {
            statsData = otsdb.query(10.seconds.ago, metricPrefix + "flow.rtt",
                    [flowid   : flow.flowId,
                     direction: "forward"]).dps
            assert statsData.empty == null || statsData.empty
        }

        and: "No data points for reverse flow found"
        Wrappers.wait(STATS_LOGGING_TIMEOUT, 1) {
            statsData = otsdb.query(10.seconds.ago, metricPrefix + "flow.rtt",
                    [flowid   : reversedFlow.flowId,
                     direction: "reverse"]).dps
            assert statsData.empty == null || statsData.empty
        }

        when: "We enable global toggler"
        enableFlowRtt()

        then: "Check if stats for forward is available"
        Wrappers.wait(STATS_LOGGING_TIMEOUT, 1) {
            statsData = otsdb.query(10.seconds.ago, metricPrefix + "flow.rtt",
                    [flowid   : flow.flowId,
                     direction: "forward"]).dps
            assert statsData && !statsData.empty
        }
        and: "Check if stats for reverse is available"
        Wrappers.wait(STATS_LOGGING_TIMEOUT, 1) {
            statsData = otsdb.query(10.seconds.ago, metricPrefix + "flow.rtt",
                    [flowid   : reversedFlow.flowId,
                     direction: "reverse"]).dps
            assert statsData && !statsData.empty
        }

        when: "We disable switch toggler"
        disableFlowRttForSwitch(server42Switch)
        then: "Check if stats for forward is not available"
        Wrappers.wait(STATS_LOGGING_TIMEOUT, 1) {
            statsData = otsdb.query(10.seconds.ago, metricPrefix + "flow.rtt",
                    [flowid   : flow.flowId,
                     direction: "forward"]).dps
            assert statsData.empty == null || statsData.empty
        }

        and: "No data points for reverse flow found"
        Wrappers.wait(STATS_LOGGING_TIMEOUT, 1) {
            statsData = otsdb.query(10.seconds.ago, metricPrefix + "flow.rtt",
                    [flowid   : reversedFlow.flowId,
                     direction: "reverse"]).dps
            assert statsData.empty == null || statsData.empty
        }

        when: "We enable switch toggler"
        enableFlowRttForSwitch(server42Switch)

        then: "Check if stats for forward is available"
        Wrappers.wait(STATS_LOGGING_TIMEOUT, 1) {
            statsData = otsdb.query(10.seconds.ago, metricPrefix + "flow.rtt",
                    [flowid   : flow.flowId,
                     direction: "forward"]).dps
            assert statsData && !statsData.empty
        }
        and: "Check if stats for reverse is available"
        Wrappers.wait(STATS_LOGGING_TIMEOUT, 1) {
            statsData = otsdb.query(10.seconds.ago, metricPrefix + "flow.rtt",
                    [flowid   : reversedFlow.flowId,
                     direction: "reverse"]).dps
            assert statsData && !statsData.empty
        }

        and: "Cleanup: revert system to original state"
        revertToOrigin(flow, reversedFlow, flowRttFeatureStartState, flowRttSwitchFeatureStartState, server42Switch)
    }

    def isEnabledFlowRttForSwitch(server42Switch) {
        return northbound.getSwitchProperties(server42Switch.dpId).server42FlowRtt
    }

    def disableFlowRttForSwitch(server42Switch) {
        SwitchPropertiesDto switchProperties = new SwitchPropertiesDto()
        switchProperties.server42FlowRtt = false
        switchProperties.supportedTransitEncapsulation = [FlowEncapsulationType.TRANSIT_VLAN.toString().toLowerCase()]
        northbound.updateSwitchProperties(server42Switch.dpId, switchProperties)
    }

    def enableFlowRttForSwitch(server42Switch) {
        SwitchPropertiesDto switchProperties = new SwitchPropertiesDto()
        switchProperties.server42FlowRtt = true
        switchProperties.server42MacAddress = server42Switch.prop.server42MacAddress
        switchProperties.server42Port = server42Switch.prop.server42Port
        switchProperties.server42Vlan = server42Switch.prop.server42Vlan
        switchProperties.supportedTransitEncapsulation = [FlowEncapsulationType.TRANSIT_VLAN.toString().toLowerCase()]
        northbound.updateSwitchProperties(server42Switch.dpId, switchProperties)
    }

    def isEnabledFlowRtt() {
        return northbound.featureToggles.server42FlowRtt
    }

    def enableFlowRtt() {
        northbound.toggleFeature(FeatureTogglesDto.builder().server42FlowRtt(true).build())
    }

    def disableFlowRtt() {
        northbound.toggleFeature(FeatureTogglesDto.builder().server42FlowRtt(false).build())
    }

    def revertToOrigin(flow, reversedFlow, flowRttFeatureStartState, flowRttSwitchFeatureStartState, server42Switch) {
        flowHelperV2.deleteFlow(flow.flowId)
        flowHelperV2.deleteFlow(reversedFlow.flowId)
        if (flowRttFeatureStartState) {
            enableFlowRtt()
        } else {
            disableFlowRtt()
        }
        if (flowRttSwitchFeatureStartState) {
            enableFlowRttForSwitch(server42Switch)
        } else {
            disableFlowRttForSwitch(server42Switch)
        }
    }
}
