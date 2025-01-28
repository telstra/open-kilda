package org.openkilda.functionaltests.spec.flows

import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.messaging.payload.flow.FlowState.DEGRADED
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.flow.FlowNotCreatedWithMissingPathExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowActionType
import org.openkilda.functionaltests.helpers.model.PathComputationStrategy
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.functionaltests.model.cleanup.CleanupAfter
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.StatusInfo
import org.openkilda.testing.model.topology.TopologyDefinition.Isl

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.See
import spock.lang.Shared


@See(["https://github.com/telstra/open-kilda/blob/develop/docs/design/pce/design.md",
        "https://github.com/telstra/open-kilda/blob/develop/docs/design/pce/max-latency-issue/README.md"])
@Narrative("""
A flow with LATENCY strategy:
    - system tries to find the path with best latency, mL/mlT2 is not used during finding a path
    - system doesn't allow to create a flow in case flowPathLatency > max_latency_tier2
    - system moves flow to DOWN state after attempt to reroute in case flowPathLatency > max_latency_tier2
    - `max_latency` and `max_latency_tier2` fields are used for setting the status of the flow
    (for example: two paths with 11 and 15 latency and two flows flow_1: mL=10, mlT2=12; flow_2: mL=14, mlT2=16
     Both flows are build via the path with 11 latency, but the status is different: flow_1 - degraded, flow_2-up)
    - flowPathLatency <= max_latency - flow is in UP state after creating
    - flowPathLatency > max_latency - flow is in DEGRADED state after creating
    - max_latency_tier2 < max_latency - it is wrong, but the system allows it. In this case the system will consider
    that max_latency_tier2 = max_latency and inform us via kibana by warning message:
    log.warn("Bad flow params found: maxLatencyTier2 ({}) should be greater than maxLatency ({}). "
    + "Put maxLatencyTier2 = maxLatency during path calculation.", flow.getMaxLatencyTier2(), flow.getMaxLatency());

    Special cases(covered by unit tests('MaxLatencyPathComputationStrategyBaseTest.java')):
    - flow with MAX_LATENCY strategy and 'max-latency' set to 0 should pick path with least latency.
    - flow with MAX_LATENCY strategy and 'max-latency' being unset(null) should pick path with least latency.
""")

class MaxLatencySpec extends HealthCheckSpecification {
    @Shared
    List<Isl> mainIsls, alternativeIsls, islsToBreak
    @Shared
    SwitchPair switchPair
    @Autowired
    @Shared
    FlowFactory flowFactory


    def setupSpec() {
        //setup: Two active switches with two diverse paths
        switchPair = switchPairs.all(false).withAtLeastNNonOverlappingPaths(2).first()
        def paths = switchPair.retrieveAvailablePaths().unique(false) { a, b -> a.retrieveNodes().intersect(b.retrieveNodes()) == [] ? 1 : 0 }
        mainIsls = paths[0].getInvolvedIsls()
        alternativeIsls = paths[1].getInvolvedIsls()
        //deactivate other paths for more clear experiment
        def isls = mainIsls.collectMany { [it, it.reversed]} + alternativeIsls.collectMany { [it, it.reversed]}
        islsToBreak = switchPair.retrieveAvailablePaths().collect { it.getInvolvedIsls() }.findAll{ it != mainIsls && it != alternativeIsls}
                .flatten().unique().collectMany{ [it, it.reversed] }.findAll { !isls.contains(it)}
        islHelper.breakIsls(islsToBreak, CleanupAfter.CLASS)
    }

    def "Able to create protected flow with max_latency strategy if both paths satisfy SLA"() {
        given: "2 non-overlapping paths with 10 and 15 latency"
        setLatencyForPaths(10, 15)

        when: "Create a flow with protected path, max_latency 16 and max_latency_tier_2 18"
        def flow = flowFactory.getBuilder(switchPair)
                .withProtectedPath(true)
                .withMaxLatency(16)
                .withMaxLatencyTier2(18)
                .withPathComputationStrategy(PathComputationStrategy.MAX_LATENCY)
                .build().create()

        then: "Flow is created, main path is the 15 latency path, protected is 10 latency"
        def flowPath = flow.retrieveAllEntityPaths()
        flowPath.getMainPathInvolvedIsls() == alternativeIsls
        flowPath.getProtectedPathInvolvedIsls() == mainIsls
    }

    @Tags([LOW_PRIORITY])
    def "Unable to create protected flow with max_latency strategy if #condition"() {
        given: "2 non-overlapping paths with 10 and 9 latency"
        setLatencyForPaths(10, 9)

        when: "Create a flow with protected path and max_latency #testMaxLatency"
        def flow = flowFactory.getBuilder(switchPair)
                .withProtectedPath(true)
                .withMaxLatency(testMaxLatency)
                .withPathComputationStrategy(PathComputationStrategy.MAX_LATENCY)
                .build().create()

        then: "Flow is not created, error returned describing that no paths found"
        def e = thrown(HttpClientErrorException)
        new FlowNotCreatedWithMissingPathExpectedError(~/Not enough bandwidth or no path found. Can't find a path/).matches(e)

        where:
        testMaxLatency | condition
        10             | "only 1 path satisfies SLA"
        9              | "both paths do not satisfy SLA"
    }

    def "Able to create DEGRADED protected flow with max_latency strategy if maxLatency < protectedPathLatency < maxLatencyTier2"() {
        given: "2 non-overlapping paths with 10 and 15 latency"
        setLatencyForPaths(10, 15)

        when: "Create a flow with protected path, maxLatency 11 and maxLatencyTier2 16"
        def flow = flowFactory.getBuilder(switchPair)
                .withProtectedPath(true)
                .withMaxLatency(11)
                .withMaxLatencyTier2(16) // maxLatency < pathLatency < maxLatencyTier2
                .withPathComputationStrategy(PathComputationStrategy.MAX_LATENCY)
                .build().create(DEGRADED)

        then: "Flow is created, main path is the 10 latency path, protected is 15 latency"
        and: "Flow goes to DEGRADED state"
        def flowPath = flow.retrieveAllEntityPaths()
        flowPath.getMainPathInvolvedIsls() == mainIsls
        flowPath.getProtectedPathInvolvedIsls() == alternativeIsls
    }

    @Tags([LOW_PRIORITY])
    def "Able to create DEGRADED flow with max_latency strategy if maxLatencyTier2 > pathLatency > maxLatency"() {
        given: "2 non-overlapping paths with 11 and 15 latency"
        setLatencyForPaths(11, 15)

        when: "Create a flow with max_latency 11 and max_latency_tier2 16"
        def flow = flowFactory.getBuilder(switchPair)
                .withProtectedPath(false)
                .withMaxLatency(11)
                .withMaxLatencyTier2(16)
                .withPathComputationStrategy(PathComputationStrategy.MAX_LATENCY)
                .build().create(DEGRADED)

        then: "Flow is created, flow path is the 15 latency path"
        flow.retrieveAllEntityPaths().getInvolvedIsls() == alternativeIsls
    }

    @Tags([LOW_PRIORITY])
    def "Able to update DEGRADED flow with max_latency strategy if maxLatencyTier2 > pathLatency > maxLatency"() {
        given: "2 non-overlapping paths with 10 and 15 latency"
        setLatencyForPaths(10, 15)

        when: "Create a flow with max_latency 11 and max_latency_tier2 16"
        def flow = flowFactory.getBuilder(switchPair)
                .withProtectedPath(false)
                .withMaxLatency(11)
                .withMaxLatencyTier2(16)
                .withPathComputationStrategy(PathComputationStrategy.MAX_LATENCY)
                .build().create()
        //flow path is the 10 latency path
        assert flow.retrieveAllEntityPaths().getInvolvedIsls() == mainIsls

        and: "Update the flow(maxLatency: 10)"
        def newMaxLatency = 10
        def flowWithNewMaxLatency = flow.deepCopy().tap { it.maxLatency = 10}
        flow.update(flowWithNewMaxLatency, DEGRADED)

        then: "Flow is updated and goes to the DEGRADED state"
        wait(WAIT_OFFSET) {
            def flowInfo = flow.retrieveDetails()
            assert flowInfo.maxLatency == newMaxLatency
            assert flowInfo.status == DEGRADED
            assert flowInfo.statusInfo == StatusInfo.BACK_UP_STRATEGY_USED
            /*[0..1] - can be more than two statuses due to running this test in a parallel mode.
            for example: reroute can be triggered by blinking/activating any isl (not involved in flow path)*/
            assert northboundV2.getFlowHistoryStatuses(flow.flowId).historyStatuses*.statusBecome[0..1] == ["UP", "DEGRADED"]
        }
        assert flow.retrieveAllEntityPaths().getInvolvedIsls() == alternativeIsls
    }

    def "Able to reroute a MAX_LATENCY flow if maxLatencyTier2 > pathLatency > maxLatency"() {
        given: "2 non-overlapping paths with 10 and 15 latency"
        setLatencyForPaths(10, 15)

        when: "Create a flow with max_latency 11 and max_latency_tier2 16"
        def flow = flowFactory.getBuilder(switchPair)
                .withProtectedPath(false)
                .withMaxLatency(11)
                .withMaxLatencyTier2(16)
                .withPathComputationStrategy(PathComputationStrategy.MAX_LATENCY)
                .build().create()
        assert flow.retrieveAllEntityPaths().getInvolvedIsls() == mainIsls

        and: "Init auto reroute (bring port down on the src switch)"
        setLatencyForPaths(10, 15)
        def islToBreak = mainIsls.first()
        islHelper.breakIsl(islToBreak)

        then: "Flow is rerouted and goes to the DEGRADED state"
        wait(rerouteDelay + WAIT_OFFSET*2 ) {
            def flowLastHistoryEntry = flow.retrieveFlowHistory()
                    .getEntriesByType(FlowActionType.REROUTE).last()
            assert flowLastHistoryEntry.payload.last().action == "Flow reroute completed"
            // https://github.com/telstra/open-kilda/issues/4049
            flowLastHistoryEntry.payload.last().details == "Flow reroute completed with status DEGRADED and error: The primary path status is DEGRADED"
            def flowInfo = flow.retrieveDetails()
            assert flowInfo.status == DEGRADED
            assert flowInfo.statusInfo == StatusInfo.BACK_UP_STRATEGY_USED
        }
        flow.retrieveAllEntityPaths().getInvolvedIsls() == alternativeIsls
    }

    def "Able to create DEGRADED flow with LATENCY strategy if max_latency_tier_2 > flowPath > max_latency"() {
        given: "2 non-overlapping paths with 11 and 15 latency"
        setLatencyForPaths(11, 15)

        when: "Create a flow, maxLatency 10 and maxLatencyTier2 12"
        def flow = flowFactory.getBuilder(switchPair)
                .withProtectedPath(false)
                .withMaxLatency(10)
                .withMaxLatencyTier2(12)
                .withPathComputationStrategy(PathComputationStrategy.LATENCY)
                .build().create(DEGRADED)

        then: "Flow is created in DEGRADED state because flowPath doesn't satisfy max_latency value \
but satisfies max_latency_tier2"
        flow.retrieveAllEntityPaths().getInvolvedIsls() == mainIsls
    }

    @Tags([LOW_PRIORITY])
    def "Able to create a flow with LATENCY strategy when max_latency = pathLatency"() {
        given: "2 non-overlapping paths with 9 and 15 latency"
        setLatencyForPaths(9, 15)

        when: "Create a flow, maxLatency 9 and maxLatencyTier2 12"
        def flow = flowFactory.getBuilder(switchPair)
                .withProtectedPath(false)
                .withMaxLatency(9)
                .withMaxLatencyTier2(12)
                .withPathComputationStrategy(PathComputationStrategy.LATENCY)
                .build().create()

        then: "Flow is created in UP"
        flow.retrieveAllEntityPaths().getInvolvedIsls() == mainIsls
    }

    @Tags([LOW_PRIORITY])
    def "Unable to create a flow with LATENCY strategy when pathLatency > max_latency_tier2"() {
        given: "2 non-overlapping paths with 12 and 13 latency"
        setLatencyForPaths(12, 13)

        when: "Create a flow, maxLatency 10 and maxLatencyTier2 11"
        def flow = flowFactory.getBuilder(switchPair)
                .withProtectedPath(false)
                .withMaxLatency(10)
                .withMaxLatencyTier2(11)
                .withPathComputationStrategy(PathComputationStrategy.LATENCY)
                .build().create()

        then: "Flow is not created, human readable error is returned"
        def e = thrown(HttpClientErrorException)
        new FlowNotCreatedWithMissingPathExpectedError(~/Not enough bandwidth or no path found. Can't find a path/).matches(e)
    }

    @Tags([LOW_PRIORITY])
    def "A flow with LATENCY strategy is DOWN after attempt to reroute in case pathLatency > max_latency_tier2"() {
        given: "2 non-overlapping paths with 11 and 15 latency"
        setLatencyForPaths(11, 15)

        and: "A flow with maxLatency 11 and maxLatencyTier2 14 on the path with 11 latency"
        def flow = flowFactory.getBuilder(switchPair)
                .withProtectedPath(false)
                .withMaxLatency(11)
                .withMaxLatencyTier2(14)
                .withPathComputationStrategy(PathComputationStrategy.LATENCY)
                .build().create()

        assert flow.retrieveAllEntityPaths().getInvolvedIsls() == mainIsls

        when: "Break the flow path to init autoReroute"
        def islToBreak = mainIsls.first()
        islHelper.breakIsl(islToBreak)

        then: "Flow is not rerouted and moved to the DOWN state"
        wait(WAIT_OFFSET) {
            with(flow.retrieveDetails()) {
                it.status == FlowState.DOWN
                it.statusInfo.contains("No path found.")
            }
        }
        assert flow.retrieveAllEntityPaths().getInvolvedIsls() == mainIsls
    }

    def setLatencyForPaths(int mainPathLatency, int alternativePathLatency) {
        def nanoMultiplier = 1000000
        def mainIslCost = mainPathLatency.intdiv(mainIsls.size()) * nanoMultiplier
        def alternativeIslCost = alternativePathLatency.intdiv(alternativeIsls.size()) * nanoMultiplier
        [mainIsls[0], mainIsls[0].reversed].each {
            database.updateIslLatency(it, mainIslCost + (mainPathLatency % mainIsls.size()) * nanoMultiplier)
        }
        mainIsls.tail().each { [it, it.reversed].each { database.updateIslLatency(it, mainIslCost) } }
        [alternativeIsls[0], alternativeIsls[0].reversed].each {
            database.updateIslLatency(it, alternativeIslCost + (alternativePathLatency % alternativeIsls.size()) * nanoMultiplier)
        }
        alternativeIsls.tail().each { [it, it.reversed].each { database.updateIslLatency(it, alternativeIslCost) } }
    }
}
