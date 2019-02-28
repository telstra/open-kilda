package org.openkilda.functionaltests.spec.flows

import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.payload.flow.FlowPathPayload
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Value
import spock.lang.Ignore
import spock.lang.Narrative

import java.util.concurrent.TimeUnit

@Ignore("Unstable. Under investigation.")
@Narrative("""
This test verifies that we do not perform a reroute as soon as we receive a reroute request (we talk only about
automatic reroutes here; manual reroutes are still performed instantly). Instead, system waits for 'reroute.delay'
seconds and if no new reroute requests are issued, it performs ONE reroute for each requested flowId. If ANY new reroute
is issued during 'reroute.delay' the timer is refreshed. 
System should stop refreshing the timer if 'reroute.hardtimeout' is reached and perform all the queued reroutes (unique 
for each flowId).
""")
@Slf4j
class ThrottlingRerouteSpec extends BaseSpecification {

    @Value('${reroute.hardtimeout}')
    int rerouteHardTimeout

    def setupOnce() {
        //TODO(rtretiak): unstable on 'hardware' atm, needs investigation
        requireProfiles("virtual")
    }

    def "Reroute is not performed while new reroutes are being issued"() {
        given: "Multiple flows that can be rerouted independently (use short unique paths)"
        /* Here we will pick only short flows that consist of 2 switches, so that we can maximize amount of unique
        flows found. Loop over ISLs(not switches), since it already ensures that src and dst of ISL are
        neighboring switches*/
        List<List<Switch>> switchPairs = []
        topology.islsForActiveSwitches.each {
            def pair = [it.srcSwitch, it.dstSwitch]
            if (!switchPairs.find { it.sort() == pair.sort() }) { //if such pair is not yet picked, ignore order
                switchPairs << pair
            }
        }

        assumeTrue("Topology is too small to run this test", switchPairs.size() > 3)
        def flows = switchPairs.take(5).collect { switchPair ->
            def flow = flowHelper.randomFlow(*switchPair)
            flowHelper.addFlow(flow)
            flow
        }
        def flowPaths = flows.collect { northbound.getFlowPath(it.id) }

        when: "All flows break one by one"
        def brokenIsls = flowPaths.collect {
            breakFlow(it)
            //don't sleep here, since there is already an antiFlapMin delay between actual port downs
        }
        /*At this point all reroute triggers have happened. Save this time in order to calculate when the actual
        reroutes will happen (time triggers stopped + reroute delay seconds)*/
        def rerouteTriggersEnd = new Date()
        def untilReroutesBegin = { rerouteTriggersEnd.time + rerouteDelay * 1000 - new Date().time }

        then: "The oldest broken flow is still not rerouted before rerouteDelay run out"
        sleep(untilReroutesBegin() - (long) (rerouteDelay * 1000 * 0.2)) //check after 80% of rerouteDelay has passed
        northbound.getFlowStatus(flows.first().id).status == FlowState.UP

        and: "The oldest broken flow is rerouted when the rerouteDelay runs out"
        Wrappers.wait(untilReroutesBegin() / 1000.0 + WAIT_OFFSET / 2.0) {
            //Flow should go DOWN or change path on reroute. In our case it doesn't matter which of these happen.
            assert northbound.getFlowStatus(flows.first().id).status == FlowState.DOWN ||
                    northbound.getFlowPath(flows.first().id) != flowPaths.first()
        }

        and: "The rest of the flows are rerouted too"
        Wrappers.wait(untilReroutesBegin() / 1000.0 + WAIT_OFFSET) {
            flowPaths[1..-1].each { flowPath ->
                assert northbound.getFlowStatus(flowPath.id).status == FlowState.DOWN ||
                        northbound.getFlowPath(flowPath.id) != flowPath
            }
        }

        and: "cleanup: restore broken paths and delete flows"
        flows.each { flowHelper.deleteFlow(it.id) }
        brokenIsls.each {
            northbound.portUp(it.srcSwitch.dpId, it.srcPort)
        }
        Wrappers.wait(WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
    }

    def "Reroute is performed after hard timeout even though new reroutes are still being issued"() {
        given: "Multiple flows that can be rerouted independently (use short unique paths)"
        /* Here we will pick only short flows that consist of 2 switches, so that we can maximize amount of unique
        flows found. Loop over ISLs(not switches), since it already ensures that src and dst of ISL are
        neighboring switches*/
        List<List<Switch>> switchPairs = []
        topology.islsForActiveSwitches.each {
            def pair = [it.srcSwitch, it.dstSwitch]
            if (!switchPairs.find { it.sort() == pair.sort() }) { //if such pair is not yet picked, ignore order
                switchPairs << pair
            }
        }
        /*due to port anti-flap we cannot continuously quickly reroute one single flow until we reach hardTimeout,
        thus we need certain amount of flows to continuously provide reroute triggers for them in a loop.
        We can re-trigger a reroute on the same flow after antiflapCooldown + antiflapMin seconds*/
        int minFlowsRequired = (int) Math.min(rerouteHardTimeout / antiflapMin, antiflapCooldown / antiflapMin + 1) + 1
        assumeTrue("Topology is too small to run this test", switchPairs.size() >= minFlowsRequired)
        def flows = switchPairs.take(minFlowsRequired).collect { switchPair ->
            def flow = flowHelper.randomFlow(*switchPair)
            flowHelper.addFlow(flow)
            flow
        }
        def flowPaths = flows.collect { northbound.getFlowPath(it.id) }

        when: "All flows begin to continuously reroute in a loop"
        def stop = false //flag to abort all reroute triggers
        def rerouteTriggers = flowPaths.collect { flowPath ->
            new Thread({
                while (!stop) {
                    def brokenIsl = breakFlow(flowPath)
                    northbound.portUp(brokenIsl.srcSwitch.dpId, brokenIsl.srcPort)
                    Wrappers.wait(antiflapCooldown + WAIT_OFFSET) {
                        assert islUtils.getIslInfo(brokenIsl).get().state == IslChangeType.DISCOVERED
                    }
                }
            })
        }
        //do no start simultaneously, so that reroutes are triggered every X seconds (not at once)
        def starter = new Thread({
            rerouteTriggers.each {
                it.start()
                TimeUnit.SECONDS.sleep(rerouteDelay - 1)
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
            flowPaths.each { flowPath ->
                assert northbound.getFlowStatus(flowPath.id).status == FlowState.UP &&
                        northbound.getFlowPath(flowPath.id) == flowPath
            }
        }

        and: "Flows should start to reroute after hard timeout, eventhough reroutes are still being triggered"
        rerouteTriggers.every { it.alive }
        def flowPathsClone = flowPaths.collect()
        Wrappers.wait(untilHardTimeoutEnds() + WAIT_OFFSET) {
            flowPathsClone.removeAll { flowPath ->
                northbound.getFlowStatus(flowPath.id).status == FlowState.DOWN ||
                        northbound.getFlowPath(flowPath.id) != flowPath
            }
            assert flowPathsClone.empty
        }

        and: "cleanup: delete flows"
        flows.each { flowHelper.deleteFlow(it.id) }

        cleanup: "revive all paths"
        stop = true
        starter.join()
        rerouteTriggers.each { it.join() } //each thread revives ISL after itself
        Wrappers.wait(WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
    }

    def "Flow can be safely deleted while it is in the reroute window waiting for reroute"() {
        given: "A flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)
        def path = northbound.getFlowPath(flow.id)

        when: "Init a flow reroute by breaking current path"
        def brokenIsl = breakFlow(path)

        and: "Immediately remove the flow before reroute delay runs out and flow is actually rerouted"
        flowHelper.deleteFlow(flow.id)

        then: "The flow is not present in NB"
        northbound.getAllFlows().empty

        and: "Related switches have no excess rules"
        pathHelper.getInvolvedSwitches(PathHelper.convert(path)).each {
            verifySwitchRules(it.dpId)
        }

        and: "cleanup: restore broken path"
        northbound.portUp(brokenIsl.srcSwitch.dpId, brokenIsl.srcPort)
        Wrappers.wait(WAIT_OFFSET) { assert islUtils.getIslInfo(brokenIsl).get().state == IslChangeType.DISCOVERED }
    }

    def cleanup() {
        database.resetCosts()
    }

    /**
     * Breaks certain flow path. Ensures that the flow is indeed broken by waiting for ISL to actually get FAILED.
     * @param flowpath path to break
     * @return ISL which 'src' was brought down in order to break the path
     */
    Isl breakFlow(FlowPathPayload flowpath) {
        def sw = flowpath.forwardPath.first().switchId
        def port = flowpath.forwardPath.first().outputPort
        def brokenIsl = (topology.islsForActiveSwitches +
                topology.islsForActiveSwitches.collect { it.reversed }).find {
            it.srcSwitch.dpId == sw && it.srcPort == port
        }
        assert brokenIsl, "This should not be possible. Trying to switch port on ISL which is not present in config?"
        northbound.portDown(sw, port)
        Wrappers.wait(WAIT_OFFSET, 0) {
            assert islUtils.getIslInfo(brokenIsl).get().state == IslChangeType.FAILED
        }
        return brokenIsl

    }
}
