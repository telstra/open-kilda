package org.openkilda.functionaltests.spec.flows

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.ResourceLockConstants.FLOW_MON_TOGGLE
import static org.openkilda.functionaltests.ResourceLockConstants.S42_TOGGLE
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.VIRTUAL
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.REROUTE_ACTION
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.REROUTE_FAIL
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.REROUTE_SUCCESS
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.model.system.FeatureTogglesDto
import org.openkilda.model.PathComputationStrategy
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.tools.SoftAssertions

import org.springframework.beans.factory.annotation.Value
import spock.lang.Isolated
import spock.lang.ResourceLock
import spock.lang.See
import spock.lang.Shared

import java.util.concurrent.TimeUnit

@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/flow-monitoring")
@Tags([VIRTUAL, LOW_PRIORITY])
@Isolated //s42 toggle affects all switches in the system, may lead to excess rules during sw validation in other tests
class FlowMonitoringSpec extends HealthCheckSpecification {
    @Shared
    List<PathNode> mainPath, alternativePath
    @Shared
    List<Isl> mainIsls, alternativeIsls, islsToBreak
    @Shared
    SwitchPair switchPair
    @Shared
    @Value('${opentsdb.metric.prefix}')
    String metricPrefix

    /** System tries to reroute a flow in case latency on a path is (flowLatency + flowLatency * 0.05);
     * NOTE: There is some possible latency calculation error in virtual lab(ovs/linux) after applying 'tc' command
     * that's why '0.6' is used. */
    @Shared
    def flowLatencySlaThresholdPercent = 0.6 //kilda_flow_latency_sla_threshold_percent: 0.05
    @Shared //kilda_flow_sla_check_interval_seconds: 60
    @Value('${flow.sla.check.interval.seconds}')
    Integer flowSlaCheckIntervalSeconds
    @Shared
    def flowLatencySlaTimeoutSeconds = 30 //kilda_flow_latency_sla_timeout_seconds: 30

    def setupSpec() {
        //setup: Two active switches with two diverse paths
        List<List<PathNode>> paths
        switchPair = topologyHelper.switchPairs.find {
            paths = it.paths.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }
            paths.size() >= 2
        } ?: assumeTrue(false, "No suiting switches found")
        mainPath = paths[0]
        alternativePath = paths[1]
        mainIsls = pathHelper.getInvolvedIsls(mainPath)
        alternativeIsls = pathHelper.getInvolvedIsls(alternativePath)
        //deactivate other paths for more clear experiment
        def isls = mainIsls + alternativeIsls
        islsToBreak = switchPair.paths.findAll { !paths.contains(it) }
                .collect { pathHelper.getInvolvedIsls(it).find { !isls.contains(it) && !isls.contains(it.reversed) } }
                .unique { [it, it.reversed].sort() }
        islsToBreak.each { antiflap.portDown(it.srcSwitch.dpId, it.srcPort) }
    }

    @Tidy
    @ResourceLock(S42_TOGGLE)
    @ResourceLock(FLOW_MON_TOGGLE)
    def "Able to detect and reroute a flow with MAX_LATENCY strategy when main path does not satisfy latency SLA"() {
        given: "2 non-overlapping paths with 200 and 250 latency"
        setLatencyForPaths(200, 250)

        and: "flowLatencyMonitoringReactions is enabled in featureToggle"
        and: "Disable s42 in featureToggle for generating flow-monitoring stats"
        def initFeatureToggle = northbound.getFeatureToggles()
        northbound.toggleFeature(FeatureTogglesDto.builder()
                .flowLatencyMonitoringReactions(true)
                .server42FlowRtt(false)
                .build())

        and : "A flow with max_latency 210"
        def createFlowTime = new Date()
        def flow1 = flowHelperV2.randomFlow(switchPair).tap {
            flowId = "fflow1"
            maxLatency = 210
            pathComputationStrategy = PathComputationStrategy.MAX_LATENCY.toString()
        }
        def flow2 = flowHelperV2.randomFlow(switchPair, false, [flow1]).tap {
            flowId = "fflow2"
            maxLatency = 210
            pathComputationStrategy = PathComputationStrategy.MAX_LATENCY.toString()
        }
        def flow3 = flowHelperV2.randomFlow(switchPair, false, [flow1, flow2]).tap {
            flowId = "fflow3"
            maxLatency = 210
            pathComputationStrategy = PathComputationStrategy.MAX_LATENCY.toString()
        }
        flowHelperV2.addFlow(flow1)
        flowHelperV2.addFlow(flow2)
        flowHelperV2.addFlow(flow3)
        //wait for generating some flow-monitoring stats
        wait(flowSlaCheckIntervalSeconds + WAIT_OFFSET) {
         assert !otsdb.query(createFlowTime, metricPrefix + "flow.rtt",
                 [flowid   : flow1.flowId,
                  direction: "forward",
                  origin   : "flow-monitoring"]).dps.isEmpty()
        assert !otsdb.query(createFlowTime, metricPrefix + "flow.rtt",
                [flowid   : flow1.flowId,
                 direction: "reverse",
                 origin   : "flow-monitoring"]).dps.isEmpty()
        }

        [flow1, flow2, flow3].each {
            def path = northbound.getFlowPath(it.flowId)
            assert pathHelper.convert(path) == mainPath
        }

        when: "Main path does not satisfy SLA(update isl latency via db)"
        def isl = pathHelper.getInvolvedIsls(mainPath).first()
        String srcInterfaceName = isl.srcSwitch.name + "-" + isl.srcPort
        String dstInterfaceName = isl.dstSwitch.name + "-" + isl.dstPort
        def newLatency = (flow1.maxLatency + (flow1.maxLatency * flowLatencySlaThresholdPercent)).toInteger()
        lockKeeper.setLinkDelay(srcInterfaceName, newLatency)
        lockKeeper.setLinkDelay(dstInterfaceName, newLatency)
        def updateLatencyTime = new Date()
        wait(flowSlaCheckIntervalSeconds + WAIT_OFFSET) {
            verifyLatencyInOpenTSDB(updateLatencyTime, flow1.flowId, newLatency)
        }

        then: "System detects that flowPathLatency doesn't satisfy max_latency and reroute the flow"
        and: "Flow history contains information that flow was rerouted due to SLA check"
        /** Steps before rerouting: check latency for a flow every 'kilda_flow_sla_check_interval_seconds';
         * wait 'kilda_flow_latency_sla_timeout_seconds';
         * recheck the flowLatency 'kilda_flow_sla_check_interval_seconds';
         * and then reroute the flow.
         */

        def assertions = new SoftAssertions()
        TimeUnit.SECONDS.sleep(flowSlaCheckIntervalSeconds * 4 + flowLatencySlaTimeoutSeconds + WAIT_OFFSET)
        //"Reason: Flow latency become unhealthy" or ""Reason: Flow latency become healthy""
        def history1 = northbound.getFlowHistory(flow1.flowId).last()
        def history2 = northbound.getFlowHistory(flow2.flowId).last()
        def history3 = northbound.getFlowHistory(flow3.flowId).last()
        assertions.checkSucceeds {
            assert history1.details.contains("healthy") &&
                    (history1.payload.last().action in [REROUTE_SUCCESS, REROUTE_FAIL]) //just check 'reroute'
        }
        assertions.checkSucceeds {
            assert history2.details.contains("healthy") &&
                    (history2.payload.last().action in [REROUTE_SUCCESS, REROUTE_FAIL]) //just check 'reroute'
        }
        assertions.checkSucceeds {
            assert history3.details.contains("healthy") &&
                    (history3.payload.last().action in [REROUTE_SUCCESS, REROUTE_FAIL]) //just check 'reroute'
        }
        assertions.verify()

        cleanup:
        [flow1, flow2, flow3].each { it && flowHelperV2.deleteFlow(it.flowId) }
        srcInterfaceName && lockKeeper.cleanupLinkDelay(srcInterfaceName)
        dstInterfaceName && lockKeeper.cleanupLinkDelay(dstInterfaceName)
        initFeatureToggle && northbound.toggleFeature(initFeatureToggle)

        where:
        i << (1..3)
    }

    @Tidy
    @ResourceLock(S42_TOGGLE)
    @ResourceLock(FLOW_MON_TOGGLE)
    def "System doesn't try to reroute a MAX_LATENCY flow when a flow path doesn't satisfy latency SLA \
and flowLatencyMonitoringReactions is disabled in featureToggle"() {
        given: "2 non-overlapping paths with 200 and 250 latency"
        setLatencyForPaths(200, 250)

        and: "flowLatencyMonitoringReactions is disabled in featureToggle"
        and: "Disable s42 in featureToggle for generating flow-monitoring stats"
        def initFeatureToggle = northbound.getFeatureToggles()
        northbound.toggleFeature(FeatureTogglesDto.builder()
                .flowLatencyMonitoringReactions(false)
                .server42FlowRtt(false)
                .build())

        and : "A flow with max_latency 210"
        def createFlowTime = new Date()
        def flow = flowHelperV2.randomFlow(switchPair).tap {
            maxLatency = 210
            pathComputationStrategy = PathComputationStrategy.MAX_LATENCY.toString()
        }
        flowHelperV2.addFlow(flow)
        //wait for generating some flow-monitoring stats
        wait(flowSlaCheckIntervalSeconds + WAIT_OFFSET) {
            assert !otsdb.query(createFlowTime, metricPrefix + "flow.rtt",
                    [flowid   : flow.flowId,
                     direction: "forward",
                     origin   : "flow-monitoring"]).dps.isEmpty()
            assert !otsdb.query(createFlowTime, metricPrefix + "flow.rtt",
                    [flowid   : flow.flowId,
                     direction: "reverse",
                     origin   : "flow-monitoring"]).dps.isEmpty()
        }
        pathHelper.convert(northbound.getFlowPath(flow.flowId)) == mainPath

        when: "Main path does not satisfy SLA(update isl latency via db)"
        def isl = pathHelper.getInvolvedIsls(mainPath).first()
        String srcInterfaceName = isl.srcSwitch.name + "-" + isl.srcPort
        String dstInterfaceName = isl.dstSwitch.name + "-" + isl.dstPort
        def newLatency = (flow.maxLatency + (flow.maxLatency * flowLatencySlaThresholdPercent)).toInteger()
        lockKeeper.setLinkDelay(srcInterfaceName, newLatency)
        lockKeeper.setLinkDelay(dstInterfaceName, newLatency)
        def updateLatencyTime = new Date()
        wait(flowSlaCheckIntervalSeconds + WAIT_OFFSET) {
            verifyLatencyInOpenTSDB(updateLatencyTime, flow.flowId, newLatency)
        }

        then: "Flow is not rerouted because flowLatencyMonitoringReactions is disabled in featureToggle"
        sleep((flowSlaCheckIntervalSeconds * 2 + flowLatencySlaTimeoutSeconds) * 1000)
        northbound.getFlowHistory(flow.flowId).findAll { it.action == REROUTE_ACTION }.empty

        and: "Flow path is not changed"
        pathHelper.convert(northbound.getFlowPath(flow.flowId)) == mainPath

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)
        srcInterfaceName && lockKeeper.cleanupLinkDelay(srcInterfaceName)
        dstInterfaceName && lockKeeper.cleanupLinkDelay(dstInterfaceName)
        initFeatureToggle && northbound.toggleFeature(initFeatureToggle)
    }

    def setLatencyForPaths(int mainPathLatency, int alternativePathLatency) {
        def nanoMultiplier = 1000000
        def mainIslLatency = mainPathLatency.intdiv(mainIsls.size()) * nanoMultiplier
        def alternativeIslLatency = alternativePathLatency.intdiv(alternativeIsls.size()) * nanoMultiplier
        [mainIsls[0], mainIsls[0].reversed].each {
            database.updateIslLatency(it, mainIslLatency + (mainPathLatency % mainIsls.size()) * nanoMultiplier)
        }
        mainIsls.tail().each { [it, it.reversed].each { database.updateIslLatency(it, mainIslLatency) } }
        [alternativeIsls[0], alternativeIsls[0].reversed].each {
            database.updateIslLatency(it, alternativeIslLatency + (alternativePathLatency % alternativeIsls.size()) * nanoMultiplier)
        }
        alternativeIsls.tail().each { [it, it.reversed].each { database.updateIslLatency(it, alternativeIslLatency) } }
    }

    void verifyLatencyInOpenTSDB(date, flowId, expectedMs) {
        def actual = otsdb.query(date, metricPrefix + "flow.rtt",
                [flowid   : flowId,
                 direction: "forward",
                 origin   : "flow-monitoring"]).dps.values().last()
        def nanoMultiplier = 1000000
        def expectedNs = expectedMs * nanoMultiplier
        assert Math.abs(expectedNs - actual) <= expectedNs * 0.3 //less than 0.3 is unstable on jenkins
    }

    def cleanupSpec() {
        islsToBreak.each { getAntiflap().portUp(it.srcSwitch.dpId, it.srcPort) }
        wait(getDiscoveryInterval() + WAIT_OFFSET) {
            assert getNorthbound().getActiveLinks().size() == getTopology().islsForActiveSwitches.size() * 2
        }
        getDatabase().resetCosts(getTopology().isls)
    }
}
