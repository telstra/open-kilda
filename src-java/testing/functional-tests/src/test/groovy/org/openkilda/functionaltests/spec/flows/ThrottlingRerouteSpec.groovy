package org.openkilda.functionaltests.spec.flows

import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.VIRTUAL
import static org.openkilda.functionaltests.helpers.model.Switches.validateAndCollectFoundDiscrepancies
import static org.openkilda.testing.Constants.PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowActionType
import org.openkilda.functionaltests.helpers.model.FlowEntityPath
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Ignore
import spock.lang.Narrative
import spock.lang.Shared

import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

@Narrative("""
This test verifies that we do not perform a reroute as soon as we receive a reroute request (we talk only about
automatic reroutes here; manual reroutes are still performed instantly). Instead, system waits for 'reroute.delay'
seconds and if no new reroute requests are issued, it performs ONE reroute for each requested flowId. If ANY new reroute
is issued during 'reroute.delay' the timer is refreshed. 
System should stop refreshing the timer if 'reroute.hardtimeout' is reached and perform all the queued reroutes (unique 
for each flowId).
""")
@Slf4j
@Tags([VIRTUAL]) //may be unstable on hardware. not tested
class ThrottlingRerouteSpec extends HealthCheckSpecification {

    @Autowired
    @Shared
    FlowFactory flowFactory


    @Value('${reroute.hardtimeout}')
    int rerouteHardTimeout

    @Tags([SMOKE, ISL_RECOVER_ON_FAIL])
    def "Reroute is not performed while new reroutes are being issued"() {
        given: "Multiple flows that can be rerouted independently (use short unique paths)"
        /* Here we will pick only short flows that consist of 2 switches, so that we can maximize amount of unique
        flows found*/
        def swPairs = switchPairs.all(false).neighbouring().getSwitchPairs()

        assumeTrue(swPairs.size() > 4, "Topology is too small to run this test")
        def flows = swPairs.take(5).collect { switchPair ->
            def flow = flowFactory.getRandom(switchPair)
            flow
        }
        def flowPaths = flows.collect { it.retrieveAllEntityPaths() }

        when: "All flows break one by one"
        flowPaths.collect { breakFlow(it, false) }
        // Don't sleep here, since there is already an antiFlapMin delay between actual port downs
        def rerouteTriggersEnd = new Date().time
        /*At this point all reroute triggers have happened. Save this time in order to calculate when the actual
        reroutes will happen (time triggers stopped + reroute delay seconds)*/

        then: "The oldest broken flow is still not rerouted before rerouteDelay run out"
        Wrappers.wait(rerouteDelay * 3) {
            assert flows.first().retrieveFlowHistory().entries.last().action == FlowActionType.REROUTE.value
            // wait till reroute starts
        }
        def rerouteTimestamp = flows.first().retrieveFlowHistory().entries.last().timestampIso
        // check time diff between the time when reroute was triggered and the first action of reroute in history
        def rerouteStarts = ZonedDateTime.parse(rerouteTimestamp, ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli()
        def differenceInMillis = rerouteStarts - rerouteTriggersEnd
        // reroute starts not earlier than the expected reroute delay
        assert differenceInMillis > (rerouteDelay) * 1000
        // reroute starts not later than 2 seconds later than the expected delay
        assert differenceInMillis < (rerouteDelay + 2.5) * 1000

        and: "The oldest broken flow is rerouted when the rerouteDelay runs out"
        def untilReroutesBegin = { rerouteTriggersEnd + rerouteDelay * 1000 - new Date().time }
        def waitTime = untilReroutesBegin() / 1000.0 + PATH_INSTALLATION_TIME * 2
        Wrappers.wait(waitTime) {
            // Flow should go DOWN or change path on reroute.
            // In our case it doesn't matter which of these happen.
            def flow1 = flows.first()
            assert (flow1.retrieveFlowStatus().status == FlowState.DOWN &&
                    flow1.retrieveFlowHistory().getEntriesByType(FlowActionType.REROUTE)
                            .find { it.taskId =~ (/.+ : retry #1/) })||
                    flow1.retrieveAllEntityPaths().getPathNodes() != flowPaths.find{ it.flowPath.flowId == flow1.flowId}
        }

        and: "The rest of the flows are rerouted too"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            flows.subList(1, flows.size()).each { flow ->
                def currentFlowStatus = flow.retrieveFlowStatus().status
                assert (currentFlowStatus == FlowState.DOWN &&
                        flow.retrieveFlowHistory().getEntriesByType(FlowActionType.REROUTE)
                                .find { it.taskId =~ (/.+ : retry #1/)}) ||
                        (flow.retrieveAllEntityPaths().getPathNodes() != flowPaths.find{ it.flowPath.flowId == flow.flowId} &&
                                currentFlowStatus == FlowState.UP)
            }
        }
    }

    @Ignore("Unstable")
    @Tags(ISL_RECOVER_ON_FAIL)
    def "Reroute is performed after hard timeout even though new reroutes are still being issued"() {
        given: "Multiple flows that can be rerouted independently (use short unique paths)"
        /* Here we will pick only short flows that consist of 2 switches, so that we can maximize amount of unique
        flows found*/
        def switchPairs = switchPairs.all().neighbouring().getSwitchPairs()

        /*due to port anti-flap we cannot continuously quickly reroute one single flow until we reach hardTimeout,
        thus we need certain amount of flows to continuously provide reroute triggers for them in a loop.
        We can re-trigger a reroute on the same flow after antiflapCooldown + antiflapMin seconds*/
        int minFlowsRequired = (int) Math.min(rerouteHardTimeout / antiflapMin, antiflapCooldown / antiflapMin + 1) + 1
        assumeTrue(switchPairs.size() >= minFlowsRequired, "Topology is too small to run this test")
        def flows = switchPairs.collect { switchPair ->
            def flow = flowFactory.getRandom(switchPair)
            flow
        }
        def flowPaths = flows.collect { it.retrieveAllEntityPaths() }

        when: "All flows begin to continuously reroute in a loop"
        def stop = false //flag to abort all reroute triggers
        def rerouteTriggers = flowPaths.collect { flowPath ->
            new Thread({
                while (!stop) {
                    def brokenIsl = breakFlow(flowPath)
                    antiflap.portUp(brokenIsl.srcSwitch.dpId, brokenIsl.srcPort)
                    Wrappers.wait(antiflapCooldown + WAIT_OFFSET) {
                        assert northbound.getLink(brokenIsl).state == IslChangeType.DISCOVERED
                    }
                }
            })
        }
        //do no start simultaneously, so that reroutes are triggered every X seconds (not at once)
        def starter = new Thread({
            rerouteTriggers.each {
                it.start()
                TimeUnit.SECONDS.sleep(rerouteDelay)
            }
        })
        starter.start()
        def rerouteTriggersStart = new Date()
        def hardTimeoutTime = rerouteTriggersStart.time + (antiflapMin + rerouteHardTimeout) * 1000
        def untilHardTimeoutEnds = { hardTimeoutTime - new Date().time }
        log.debug("Expect hard timeout at ${new Date(hardTimeoutTime)}")

        then: "Right until hard timeout should run out no flow reroutes happen"
        //check until 80% of hard timeout runs out
        while (System.currentTimeMillis() < rerouteTriggersStart.time + rerouteHardTimeout * 1000 * 0.8) {
            flows.each { flow ->
                def initialFlowPath = flowPaths.find { it.flowPath.flowId == flow.flowId }
                assert flow.retrieveFlowStatus().status == FlowState.UP &&
                        flow.retrieveAllEntityPaths().getPathNodes() == initialFlowPath
            }
        }

        and: "Flows should start to reroute after hard timeout, even though reroutes are still being triggered"
        rerouteTriggers.any { it.alive }
        def flowPathsClone = flowPaths.collect()
        Wrappers.wait(untilHardTimeoutEnds() + WAIT_OFFSET) {
            flowPathsClone.removeAll { flowPath ->
                def flow = flows.find { it.flowId == flowPath.flowPath.flowId }
                def lastFlowAction = flow.retrieveFlowHistory().getEntriesByType(FlowActionType.REROUTE_FAILED).last()
                (flow.retrieveFlowStatus().status == FlowState.DOWN && lastFlowAction.payload.last().action == FlowActionType.REROUTE_FAILED.payloadLastAction)
                        || flow.retrieveAllEntityPaths().getPathNodes() != flowPath
            }
            assert flowPathsClone.empty
        }
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "Flow can be safely deleted while it is in the reroute window waiting for reroute"() {
        given: "A flow"
        def swPair = switchPairs.all().random()
        def flow = flowFactory.getRandom(swPair)
        def path = flow.retrieveAllEntityPaths()

        when: "Init a flow reroute by breaking current path"
        breakFlow(path)

        and: "Immediately remove the flow before reroute delay runs out and flow is actually rerouted"
        flow.delete()

        then: "The flow is not present in NB"
        !northboundV2.getAllFlows().find { it.flowId == flow.flowId}

        and: "Related switches have no excess rules, though need to wait until server42 rules are deleted"
        def involvedSwitches = switches.all().findSwitchesInPath(path)
        Wrappers.wait(RULES_DELETION_TIME) {
            validateAndCollectFoundDiscrepancies(involvedSwitches).isEmpty()
        }
    }

    /**
     * Breaks certain flow path. Ensures that the flow is indeed broken by waiting for ISL to actually get FAILED.
     * @param flowPath path to break
     * @return ISL which 'src' was brought down in order to break the path
     */
    Isl breakFlow(FlowEntityPath flowPath, boolean waitForBrokenIsl = true) {
        def islToBreak = flowPath.flowPath.path.forward.getInvolvedIsls().first()
        assert islToBreak, "This should not be possible. Trying to switch port on ISL which is not present in config?"
        antiflap.portDown(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        if (waitForBrokenIsl) {
            Wrappers.wait(WAIT_OFFSET, 0) {
                assert northbound.getLink(islToBreak).state == IslChangeType.FAILED
            }
        }
        return islToBreak
    }
}
