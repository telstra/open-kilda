package org.openkilda.performancetests.spec.endurance

import static groovyx.gpars.GParsPool.withPool
import static groovyx.gpars.dataflow.Dataflow.task
import static org.hamcrest.CoreMatchers.equalTo

import org.openkilda.functionaltests.helpers.Dice
import org.openkilda.functionaltests.helpers.Dice.Face
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.FlowExtended
import org.openkilda.functionaltests.helpers.model.SwitchPortVlan
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.northbound.dto.v1.flows.PingInput
import org.openkilda.performancetests.BaseSpecification
import org.openkilda.performancetests.helpers.FlowPinger
import org.openkilda.performancetests.model.CustomTopology
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.tools.SoftAssertions

import groovy.util.logging.Slf4j
import org.junit.Assume
import org.springframework.beans.factory.annotation.Value
import spock.lang.Ignore
import spock.lang.Narrative

import java.util.concurrent.TimeUnit

@Slf4j
@Narrative("This spec to hold tests that exercise system's ability to continuously survive under certain conditions.")
class EnduranceSpec extends BaseSpecification {
    @Value('${antiflap.min}')
    int antiflapMin
    @Value('${reroute.delay}')
    int rerouteDelay
    def r = new Random()
    List<FlowExtended> flows = Collections.synchronizedList(new ArrayList<FlowExtended>())

    def setup() {
        topoHelper.purgeTopology()
    }

    /**
     * Deploy topology and create certain amount of flows in the system. Define amount of events to happen during the
     * test and their chances to happen.
     * An event can be one of the following: flow creation, flow deletion, isl blink, manual reroute of 25% of all flows 
     * or just being idle.
     * At the end of the test verify that all flows are valid and switches don't have any missing or excess entities.
     * During the test all flows will be continuously 'pinged'. Any failed ping will be logged, any twice-failed ping
     * will fail the test at the end
     */
    def "Simulate live environment with random events happening#debugText"() {
        Assume.assumeThat(preset.debug, equalTo(debug))

        setup: "Create a topology and a 'dice' with random events"
        def topo = topoHelper.createRandomTopology(preset.switchesAmount, preset.islsAmount)
        setTopologyInContext(topo)
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
                assert it.retrieveFlowStatus().status == FlowState.UP
                assert it.validateAndCollectDiscrepancies().isEmpty()
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
        Wrappers.wait(30) {
            northbound.getAllLinks().every { it.state == IslChangeType.DISCOVERED }
        }
        Wrappers.wait(60 + preset.switchesAmount) {
            def soft = new SoftAssertions()
            flows.each { flow ->
                soft.checkSucceeds { assert flow.retrieveFlowStatus().status == FlowState.UP }
                soft.checkSucceeds {
                   flow.validateAndCollectDiscrepancies().isEmpty()
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
        flows.each { it.sendDeleteRequestV1() }
        topo && topoHelper.purgeTopology(topo)

        where:
        preset << [
                [
                        debug             : true,
                        switchesAmount    : 30,
                        islsAmount        : 70,
                        eventsAmount      : 40,
                        flowsToStartWith  : 200,
                        pauseBetweenEvents: 1, //seconds
                ],
                [
                        debug             : false,
                        switchesAmount    : 60,
                        islsAmount        : 150,
                        eventsAmount      : 100,
                        flowsToStartWith  : 350,
                        pauseBetweenEvents: 1, //seconds
                ]
        ]
        debugText = preset.debug ? " (debug mode)" : ""
    }

    @Ignore("https://github.com/telstra/open-kilda/issues/4224")
    def "Create 4094 flows"() {
        // system allows to create 4094 simple flows or 2047 protected flows
        def switchesAmount = 7
        int islsAmount = switchesAmount * 2.5
        int flowsAmount = 4094

        setup: "Create a topology"
        def topo = topoHelper.createRandomTopology(switchesAmount, islsAmount)
        setTopologyInContext(topo)

        when: "Create 4094 flows"
        flowsAmount.times {
            createFlow(false, false)
            def numberOfCreatedFlow = it + 1
            log.debug("Number of created flow: $numberOfCreatedFlow/$flowsAmount")

            if (it != 0 && it % 500 == 0) {
                TimeUnit.SECONDS.sleep(60)
            }
        }

        then: "Needed amount of flows are created"
        northbound.getAllFlows().size() == flowsAmount

        cleanup: "Delete flows and purge topology"
        flows.each { it.sendDeleteRequestV1() }
        topoHelper.purgeTopology(topo)
    }

    @Ignore("https://github.com/telstra/open-kilda/issues/4224")
    def "Create 2047 protected flows"() {
        def switchesAmount = 7
        int islsAmount = switchesAmount * 2.5
        int flowsAmount = 2047

        setup: "Create a topology"
        def topo = topoHelper.createRandomTopology(switchesAmount, islsAmount)
        setTopologyInContext(topo)

        when: "Try to create 2047 flows"
        flowsAmount.times {
            createFlow(false, true)
            def numberOfCreatedFlow = it + 1
            log.debug("Number of created flow: $numberOfCreatedFlow/$flowsAmount")

            if (it != 0 && it % 500 == 0) {
                TimeUnit.SECONDS.sleep(60)
            }
        }

        then: "Needed amount of flows are created"
        northbound.getAllFlows().size() == flowsAmount

        cleanup: "Delete flows and purge topology"
        flows.each { it.sendDeleteRequestV1() }
        topoHelper.purgeTopology(topo)
    }

    //TODO(rtretiak): test that continuously add/remove different switches. Ensure no memory leak over time

    def createFlow(waitForRules = false, boolean protectedPath = false) {
        List<SwitchPortVlan> busyEndpoints = flows.collect{ it.occupiedEndpoints() }.flatten() as List<SwitchPortVlan>
        def srcSw = switches.all().first()
        Wrappers.silent {
            def dstSw = pickRandom(switches.all().getListOfSwitches() - srcSw)
            def flow = flowFactory.getBuilder(srcSw, dstSw, false, busyEndpoints)
                    .withProtectedPath(protectedPath)
                    .build()
            log.info "creating flow $flow.flowId"
            waitForRules ? flow.createV1() : flow.sendCreateRequestV1()
            flows << flow
            return flow
        }
    }

    def deleteFlow() {
        Wrappers.silent {
            def flowToDelete = flows.remove(r.nextInt(flows.size()))
            log.info "deleting flow $flowToDelete.flowId"
            task { //delay the actual delete procedure to ensure no pings are in progress for the flow
                sleep(PingInput.DEFAULT_TIMEOUT)
                flowToDelete.sendDeleteRequestV1()
            }
            return flowToDelete
        }
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
        }
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
                flows[0..flows.size() / 4].eachParallel { FlowExtended flow -> Wrappers.silent { flow.rerouteV1() }
                }
            }
        }
    }
}
