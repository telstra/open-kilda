package org.openkilda.functionaltests.spec.flows

import static org.openkilda.functionaltests.ResourceLockConstants.FLOW_MON_TOGGLE
import static org.openkilda.functionaltests.ResourceLockConstants.S42_TOGGLE
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.VIRTUAL
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.functionaltests.model.stats.Direction.FORWARD
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.FLOW_RTT
import static org.openkilda.functionaltests.model.stats.Origin.FLOW_MONITORING
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowActionType
import org.openkilda.functionaltests.helpers.model.Path
import org.openkilda.functionaltests.helpers.model.PathComputationStrategy
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.functionaltests.model.cleanup.CleanupAfter
import org.openkilda.functionaltests.model.stats.FlowStats
import org.openkilda.messaging.model.system.FeatureTogglesDto
import org.openkilda.testing.model.topology.TopologyDefinition.Isl

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Isolated
import spock.lang.ResourceLock
import spock.lang.See
import spock.lang.Shared

@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/flow-monitoring")
@Tags([VIRTUAL, LOW_PRIORITY])
@Isolated //s42 toggle affects all switches in the system, may lead to excess rules during sw validation in other tests
class FlowMonitoringSpec extends HealthCheckSpecification {
    @Shared
    List<Isl> mainIsls, alternativeIsls, islsToBreak
    @Shared
    SwitchPair switchPair
    @Autowired
    @Shared
    FlowStats flowStats
    @Autowired
    @Shared
    FlowFactory flowFactory

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
        switchPair = switchPairs.all().withAtLeastNNonOverlappingPaths(2).random()
        List<List<Isl>> paths = switchPair.retrieveAvailablePaths().collect { it.getInvolvedIsls() }
        mainIsls = paths[0]
        alternativeIsls = paths[1]
        //deactivate other paths for more clear experiment
        def isls = mainIsls.collectMany { [it, it.reversed]} + alternativeIsls.collectMany { [it, it.reversed]}
        islsToBreak = paths.findAll{ it != mainIsls && it != alternativeIsls }
                .flatten().unique().collectMany{ [it, it.reversed] }.findAll { !isls.contains(it)}

        islHelper.breakIsls(islsToBreak, CleanupAfter.CLASS)
    }

    @ResourceLock(S42_TOGGLE)
    @ResourceLock(FLOW_MON_TOGGLE)
    def "Able to detect and reroute a flow with MAX_LATENCY strategy when main path does not satisfy latency SLA"() {
        given: "2 non-overlapping paths with 200 and 250 latency"
        setLatencyForPaths(200, 250)

        and: "flowLatencyMonitoringReactions is enabled in featureToggle"
        and: "Disable s42 in featureToggle for generating flow-monitoring stats"
        featureToggles.flowLatencyMonitoringReactions(true)
        featureToggles.server42FlowRtt(false)

        and : "A flow with max_latency 210"
        def flow = flowFactory.getBuilder(switchPair).withMaxLatency(210)
                .withPathComputationStrategy(PathComputationStrategy.MAX_LATENCY).build().create()

        //wait for generating some flow-monitoring stats
        wait(flowSlaCheckIntervalSeconds + WAIT_OFFSET * 3) {
            assert flowStats.of(flow.flowId).get(FLOW_RTT, FORWARD, FLOW_MONITORING).hasNonZeroValues()
        }

        assert flow.retrieveAllEntityPaths().getInvolvedIsls() == mainIsls

        when: "Main path does not satisfy SLA(update isl latency via db)"
        def isl = mainIsls.first()
        String srcInterfaceName = isl.srcSwitch.name + "-" + isl.srcPort
        String dstInterfaceName = isl.dstSwitch.name + "-" + isl.dstPort
        def newLatency = (flow.maxLatency + (flow.maxLatency * flowLatencySlaThresholdPercent)).toInteger()
        islHelper.setDelay(srcInterfaceName, newLatency)
        islHelper.setDelay(dstInterfaceName, newLatency)
        wait(flowSlaCheckIntervalSeconds + WAIT_OFFSET) {
            verifyLatencyInTsdb(flow.flowId, newLatency)
        }

        then: "System detects that flowPathLatency doesn't satisfy max_latency and reroute the flow"
        and: "Flow history contains information that flow was rerouted due to SLA check"
        /** Steps before rerouting: check latency for a flow every 'kilda_flow_sla_check_interval_seconds';
         * wait 'kilda_flow_latency_sla_timeout_seconds';
         * recheck the flowLatency 'kilda_flow_sla_check_interval_seconds';
         * and then reroute the flow.
         */
        wait(flowSlaCheckIntervalSeconds * 2 + flowLatencySlaTimeoutSeconds + WAIT_OFFSET) {
            def history = flow.retrieveFlowHistory().entries.last()
            // Flow sync or flow reroute with reason "Flow latency become unhealthy"
            assert history.getAction() == "Flow paths sync"
                || (history.details.contains("healthy") &&
                    (history.payload.last().action in [FlowActionType.REROUTE.payloadLastAction,
                                                       FlowActionType.REROUTE_FAILED.payloadLastAction])) //just check 'reroute'
        }
    }

    @ResourceLock(S42_TOGGLE)
    @ResourceLock(FLOW_MON_TOGGLE)
    def "System doesn't try to reroute a MAX_LATENCY flow when a flow path doesn't satisfy latency SLA \
and flowLatencyMonitoringReactions is disabled in featureToggle"() {
        given: "2 non-overlapping paths with 200 and 250 latency"
        setLatencyForPaths(200, 250)

        and: "flowLatencyMonitoringReactions is disabled in featureToggle"
        and: "Disable s42 in featureToggle for generating flow-monitoring stats"
        featureToggles.toggleMultipleFeatures(FeatureTogglesDto.builder()
                .flowLatencyMonitoringReactions(false)
                .server42FlowRtt(false).build())

        and : "A flow with max_latency 210"
        def flow = flowFactory.getBuilder(switchPair)
                .withMaxLatency(210)
                .withPathComputationStrategy(PathComputationStrategy.MAX_LATENCY).build()
                .create()

        //wait for generating some flow-monitoring stats
        wait(flowSlaCheckIntervalSeconds + WAIT_OFFSET * 3) {
            assert flowStats.of(flow.flowId).get(FLOW_RTT, FORWARD, FLOW_MONITORING).hasNonZeroValues()
        }
        assert flow.retrieveAllEntityPaths().getInvolvedIsls() == mainIsls

        when: "Main path does not satisfy SLA(update isl latency via db)"
        def isl = mainIsls.first()
        String srcInterfaceName = isl.srcSwitch.name + "-" + isl.srcPort
        String dstInterfaceName = isl.dstSwitch.name + "-" + isl.dstPort
        def newLatency = (flow.maxLatency + (flow.maxLatency * flowLatencySlaThresholdPercent)).toInteger()
        islHelper.setDelay(srcInterfaceName, newLatency)
        islHelper.setDelay(dstInterfaceName, newLatency)
        wait(flowSlaCheckIntervalSeconds + WAIT_OFFSET) {
            verifyLatencyInTsdb(flow.flowId, newLatency)
        }

        then: "Flow is not rerouted because flowLatencyMonitoringReactions is disabled in featureToggle"
        sleep((flowSlaCheckIntervalSeconds * 2 + flowLatencySlaTimeoutSeconds) * 1000)
        !flow.retrieveFlowHistory().getEntriesByType(FlowActionType.REROUTE)

        and: "Flow path is not changed"
        flow.retrieveAllEntityPaths().getInvolvedIsls() == mainIsls
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

    void verifyLatencyInTsdb(flowId, expectedMs) {
        def flowStatsResult = flowStats.of(flowId).get(FLOW_RTT, FORWARD, FLOW_MONITORING)
        def actual = flowStatsResult.getDataPoints().max {it.getKey()}.getValue()
        def nanoMultiplier = 1000000
        def expectedNs = expectedMs * nanoMultiplier
        assert Math.abs(expectedNs - actual) <= expectedNs * 0.3, flowStatsResult //less than 0.3 is unstable on jenkins
    }
}
