package org.openkilda.performancetests.spec

import static groovyx.gpars.GParsPool.withPool
import static groovyx.gpars.dataflow.Dataflow.task
import static org.hamcrest.CoreMatchers.equalTo
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.helpers.Dice
import org.openkilda.functionaltests.helpers.Dice.Face
import org.openkilda.functionaltests.helpers.FlowHelperV2
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.northbound.dto.v1.flows.PingInput
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2
import org.openkilda.performancetests.BaseSpecification
import org.openkilda.performancetests.helpers.FlowPinger
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.openkilda.testing.tools.SoftAssertions

import groovy.util.logging.Slf4j
import org.junit.Assume
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Narrative
import spock.lang.Unroll

import java.util.concurrent.TimeUnit

@Slf4j
@Narrative("This spec to hold tests that exercise system's ability to continuously survive under certain conditions.")
class EnduranceV2Spec extends BaseSpecification {
    @Value('${antiflap.min}')
    int antiflapMin
    @Value('${reroute.delay}')
    int rerouteDelay
    def r = new Random()
    List<FlowRequestV2> flows = Collections.synchronizedList(new ArrayList<FlowRequestV2>())
    @Autowired
    NorthboundServiceV2 northboundV2
    @Autowired
    FlowHelperV2 flowHelperV2

    /**
     * Deploy topology and create certain amount of flows in the system. Define amount of events to happen during the
     * test and their chances to happen.
     * An event can be one of the following: flow creation, flow deletion, isl blink, manual reroute of 25% of all flows 
     * or just being idle.
     * At the end of the test verify that all flows are valid and switches don't have any missing or excess entities.
     * During the test all flows will be continuously 'pinged'. Any failed ping will be logged, any twice-failed ping
     * will fail the test at the end
     */
    @Unroll
    def "Simulate live environment with random events happening(#preset.name)"() {
        Assume.assumeThat(preset.name, equalTo("local"))

        setup: "Create a topology and a 'dice' with random events"
        def topo = topoHelper.createRandomTopology(preset.switchesAmount, preset.islsAmount)
        topoHelper.setTopology(topo)
        flowHelper.setTopology(topo)
        flowHelperV2.setTopology(topo)
        def dice = new Dice([
                new Face(chance: 25, event: this.&deleteFlow),
                new Face(chance: 25, event: { createFlow(true) }),
                new Face(chance: 25, event: { blinkIsl(topo.isls) }),
                new Face(chance: 0, event: { TimeUnit.SECONDS.sleep(3) }),
                new Face(chance: 25, event: { massReroute(topo) })
        ])

        and: "As starting point, create some amount of random flows in it"
        preset.flowsToStartWith.times { createFlow() }
        Wrappers.wait(flows.size() * 1.5) {
            flows.each {
                assert northbound.getFlowStatus(it.flowId).status == FlowState.UP
                northbound.validateFlow(it.flowId).each { direction -> assert direction.asExpected }
            }
        }

        when: "With certain probability one of the following events occurs: flow creation, flow deletion, isl blink, \
idle, mass manual reroute. Step repeats pre-defined number of times"
        def pinger = new FlowPinger(northbound, flows.collect { it.flowId }, rerouteDelay)
        pinger.start()
        preset.eventsAmount.times {
            log.debug("running event #$it")
            dice.roll()
            TimeUnit.SECONDS.sleep(preset.pauseBetweenEvents)
        }
        def pingFailures = pinger.stop()

        then: "All flows remain up and valid, with no missing rules on switches"
        Wrappers.wait(WAIT_OFFSET * 2) {
            northbound.getAllLinks().every { it.state == IslChangeType.DISCOVERED }
        }
        Wrappers.wait(WAIT_OFFSET + preset.switchesAmount) {
            def soft = new SoftAssertions()
            flows.each { flow ->
                soft.checkSucceeds { assert northbound.getFlowStatus(flow.flowId).status == FlowState.UP }
                soft.checkSucceeds {
                    northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }
                }
            }
            topo.switches.each { sw ->
                def validation = northbound.validateSwitch(sw.dpId)
                soft.checkSucceeds { assert validation.rules.missing.empty, sw }
                soft.checkSucceeds { assert validation.rules.excess.empty, sw }
                soft.checkSucceeds { assert validation.meters.missing.empty, sw }
                soft.checkSucceeds { assert validation.meters.misconfigured.empty, sw }
                soft.checkSucceeds { assert validation.meters.excess.empty, sw }
            }
            soft.verify()
        }

        and: "Background ping results report no flows to fail ping twice in a row"
        pingFailures.empty

        cleanup: "delete flows and purge topology"
        pinger && !pinger.isStopped() && pinger.stop()
        flows.each { northbound.deleteFlow(it.flowId) }
        topo && topoHelper.purgeTopology(topo)

        where:
        preset << [
                [
                        name              : "local",
                        switchesAmount    : 30,
                        islsAmount        : 70,
                        eventsAmount      : 40,
                        flowsToStartWith  : 200,
                        pauseBetweenEvents: 1, //seconds
                ],
                [
                        name              : "stage",
                        switchesAmount    : 60,
                        islsAmount        : 150,
                        eventsAmount      : 200,
                        flowsToStartWith  : 350,
                        pauseBetweenEvents: 1, //seconds
                ]
        ]
    }

    //TODO(rtretiak): test that continuously add/remove different switches. Ensure no memory leak over time

    def createFlow(waitForRules = false) {
        Wrappers.silent {
            def flow = flowHelperV2.randomFlow(*topoHelper.getRandomSwitchPair(), false, flows)
            log.info "creating flow $flow.flowId"
            waitForRules ? flowHelperV2.addFlow(flow) : northboundV2.addFlow(flow)
            flows << flow
            return flow
        }
    }

    def deleteFlow() {
        def flowToDelete = flows.remove(r.nextInt(flows.size()))
        log.info "deleting flow $flowToDelete.flowId"
        task { //delay the actual delete procedure to ensure no pings are in progress for the flow
            sleep(PingInput.DEFAULT_TIMEOUT)
            Wrappers.silent { northbound.deleteFlow(flowToDelete.flowId) }
        }
        return flowToDelete
    }

    def blinkIsl(List<Isl> isls) {
        def isl = isls[r.nextInt(isls.size())]
        log.info "blink isl $isl"
        northbound.portDown(isl.srcSwitch.dpId, isl.srcPort)
        def sleepBeforePortUp = antiflapMin + r.nextInt(5)
        task {
            log.debug("Decide to sleep for $sleepBeforePortUp seconds before portUp on $isl.srcSwitch.dpId-$isl.srcPort")
            TimeUnit.SECONDS.sleep(sleepBeforePortUp)
            northbound.portUp(isl.srcSwitch.dpId, isl.srcPort)
        }.then({ it }, { throw it })
    }

    def massReroute(TopologyDefinition topo) {
        log.info "mass rerouting flows"
        //shuffle all isl costs
        def randomCost = { (r.nextInt(800) + 200).toString() }
        northbound.updateLinkProps(topo.isls.collect {
            islUtils.toLinkProps(it, [cost: randomCost()])
        })
        //call reroute on quarter of existing flows, randomly
        def flows = flows.findAll()
        Collections.shuffle(flows)
        task {
            withPool {
                flows[0..flows.size() / 4].eachParallel { flow ->
                    Wrappers.silent {
                        //may fail due to 'in progress' status, just retry
                        Wrappers.retry(5, 1, {}) {
                            northboundV2.rerouteFlow(flow.flowId)
                        }
                    }
                }
            }
        }
    }
}
