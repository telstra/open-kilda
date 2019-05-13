package org.openkilda.performancetests.spec

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.performancetests.BaseSpecification
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.tools.SoftAssertions

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Value
import spock.lang.Narrative

import java.util.concurrent.TimeUnit

@Slf4j
@Narrative("This spec to hold tests that exercise system's ability to continuously survive under certain conditions.")
class EnduranceSpec extends BaseSpecification {
    static final eventEstimatedTime = 0.5 //seconds
    @Value('${antiflap.min}')
    int antiflapMin
    def r = new Random()
    List<FlowPayload> flows = []

    /**
     * Deploy topology and create certain amount of flows in the system. Define amount of events to happen during the
     * test and their chances to happen.
     * An event can be one of the following: flow creation, flow deletion, isl blink or just being idle.
     * At the end of the test verify that all flows are valid and switches don't have any missing or excess entities
     */
    def "Simulate live environment with random events happening"() {
        def switchesAmount = 30
        int islsAmount = switchesAmount * 2.5
        def eventsAmount = 50
        def flowsToStartWith = 50
        def pauseBetweenEvents = 3 //seconds
        //chances of certain events to appear, sum should be equal to 100
        def deleteFlowChance = 35
        def createFlowChance = 37
        def blinkIslChance = 28
        def idleChance = 0

        assert [deleteFlowChance, createFlowChance, blinkIslChance, idleChance].sum() == 100
        def amount = { chance -> (chance * eventsAmount / 100).round() }
        int estimatedExecutionTime = (flowsToStartWith * eventEstimatedTime + eventsAmount * (pauseBetweenEvents +
                eventEstimatedTime)) / 60
        log.info("Expect: execution time of more than ${estimatedExecutionTime} minutes.\n" +
                "Average of ${flowsToStartWith + amount(createFlowChance) - amount(deleteFlowChance)} " +
                "flows at the end of the test.\n" +
                "Around ${amount(blinkIslChance)} ISLs to blink and produce reroute events on related flows")

        setup: "Create a topology"
        def topo = topoHelper.createRandomTopology(switchesAmount, islsAmount)
        topoHelper.setTopology(topo)

        and: "As starting point, create some amount of random flows in it"
        flowsToStartWith.times { createFlow() }

        when: "With certain probability one of the following events occurs: flow creation, flow deletion, isl blink, \
idle. Step repeats pre-defined number of times"
        eventsAmount.times {
            log.debug("running event #$it")
            def dice = r.nextInt(100)
            if (dice < deleteFlowChance) {
                deleteFlow()
            } else if (dice < createFlowChance + deleteFlowChance) {
                createFlow()
            } else if (dice < blinkIslChance + createFlowChance + deleteFlowChance) {
                blinkIsl(topo.isls)
            } else if (dice < idleChance + blinkIslChance + createFlowChance + deleteFlowChance) {
                sleep((int) (eventEstimatedTime * 1000))
            }
            TimeUnit.SECONDS.sleep(pauseBetweenEvents)
        }

        then: "All flows remain up and valid, with no missing rules on switches"
        Wrappers.wait(WAIT_OFFSET + switchesAmount) {
            def soft = new SoftAssertions()
            flows.each { flow ->
                soft.checkSucceeds { assert northbound.getFlowStatus(flow.id).status == FlowState.UP }
                soft.checkSucceeds { northbound.validateFlow(flow.id).each { assert it.asExpected } }
            }
            topo.switches.each { sw ->
                def validation = northbound.switchValidate(sw.dpId)
                soft.checkSucceeds { assert validation.rules.missing.empty, sw }
                soft.checkSucceeds { assert validation.rules.excess.empty, sw }
                soft.checkSucceeds { assert validation.meters.missing.empty, sw }
                soft.checkSucceeds { assert validation.meters.misconfigured.empty, sw }
                soft.checkSucceeds { assert validation.meters.excess.empty, sw }
            }
            soft.verify()
        }

        cleanup: "delete flows and purge topology"
        flows.each { northbound.deleteFlow(it.id) }
        topoHelper.purgeTopology(topo)
    }

    def createFlow() {
        Wrappers.silent {
            def flow = flowHelper.randomFlow(*topoHelper.getRandomSwitchPair(), false, flows)
            log.info "creating flow $flow.id"
            northbound.addFlow(flow)
            flows << flow
            return flow
        }
    }

    def deleteFlow() {
        Wrappers.silent {
            def flowToDelete = flows.remove(r.nextInt(flows.size()))
            log.info "deleting flow $flowToDelete.id"
            northbound.deleteFlow(flowToDelete.id)
            return flowToDelete
        }
    }

    def blinkIsl(List<Isl> isls) {
        def isl = isls[r.nextInt(isls.size())]
        log.info "blink isl $isl"
        northbound.portDown(isl.srcSwitch.dpId, isl.srcPort)
        new Thread({
            def sleepBeforePortUp = antiflapMin + r.nextInt(5)
            log.debug("Decide to sleep for $sleepBeforePortUp seconds before portUp on $isl.srcSwitch.dpId-$isl.srcPort")
            TimeUnit.SECONDS.sleep(sleepBeforePortUp)
            northbound.portUp(isl.srcSwitch.dpId, isl.srcPort)
        }).start()
    }
}
