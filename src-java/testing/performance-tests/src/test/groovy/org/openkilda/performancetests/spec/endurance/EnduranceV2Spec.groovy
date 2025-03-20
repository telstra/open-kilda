package org.openkilda.performancetests.spec.endurance

import static groovyx.gpars.GParsPool.withPool
import static groovyx.gpars.dataflow.Dataflow.task
import static org.hamcrest.CoreMatchers.equalTo
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

import org.openkilda.functionaltests.helpers.Dice
import org.openkilda.functionaltests.helpers.Dice.Face
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.FlowExtended
import org.openkilda.functionaltests.helpers.model.SwitchOfVersion
import org.openkilda.functionaltests.helpers.model.SwitchPortVlan
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.performancetests.BaseSpecification
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.tools.SoftAssertionsWrapper

import groovy.util.logging.Slf4j
import org.junit.Assume
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpStatusCodeException
import spock.lang.Ignore
import spock.lang.Narrative
import spock.lang.Shared

import java.util.concurrent.TimeUnit

@Slf4j
@Narrative("This spec to hold tests that exercise system's ability to continuously survive under certain conditions.")
class EnduranceV2Spec extends BaseSpecification {

    @Shared
    TopologyDefinition topology
    @Shared
    def r = new Random()
    @Shared
    List<Isl> brokenIsls
    @Shared
    List<FlowExtended> flows

    def setup() {
        brokenIsls = Collections.synchronizedList(new ArrayList<Isl>())
        flows = Collections.synchronizedList(new ArrayList<FlowExtended>())
    }

    /**
     * Deploy topology and create certain amount of flows in the system. Define amount of events to happen during the
     * test and their chances to happen.
     * An event can be one of the following: flow creation, flow deletion, isl blink, manual reroute of 25% of all flows
     * isl port down or just being idle.
     * At the end of the test verify that all UP flows are indeed pingable and all DOWN flows are indeed not pingable.
     * Verify no rule discrepancies across all switches
     *
     * Most of the test configuration is located at the bottom of the test in the 'where' block
     */
    def "Simulate live environment with random events happening#debugText"() {
        Assume.assumeThat(preset.debug, equalTo(debug))

        setup: "Create topology according to passed params"
        //cleanup any existing labs first
        northbound.getAllFlows().each { FlowPayload flow -> northboundV2.deleteFlow(flow.id) }
        topoHelper.purgeTopology()
        topology = topoHelper.createRandomTopology(preset.switchesAmount, preset.islsAmount)
        setTopologyInContext(topology)

        and: "As starting point, create some amount of random flows in it"
        preset.flowsToStartWith.times { createFlow(makeFlowPayload()) }
        Wrappers.wait(flows.size() / 2) {
            flows.each {
                assert it.retrieveFlowStatus().status == FlowState.UP
                assert it.validateAndCollectDiscrepancies().isEmpty()
            }
        }

        when: "With certain probability one of the following events occurs: flow creation, flow deletion, isl blink, \
idle, mass manual reroute, isl break. Step repeats pre-defined number of times"
        preset.eventsAmount.times {
            log.debug("running event #$it")
            dice.roll()
            TimeUnit.SECONDS.sleep(preset.pauseBetweenEvents)
        }

        and: "Wait for blinking isls to get UP and flows to finish rerouting"
        Wrappers.wait(WAIT_OFFSET + flows.size() * 1.5) {
            def isls = northbound.getAllLinks()
            (topology.isls - brokenIsls).each {
                assert islUtils.getIslInfo(isls, it).get().state == IslChangeType.DISCOVERED
            }
            assert northboundV2.getAllFlows().findAll {
                it.status == FlowState.IN_PROGRESS.toString() ||
                        it.statusDetails?.mainPath == FlowState.IN_PROGRESS.toString() ||
                        it.statusDetails?.protectedPath == FlowState.IN_PROGRESS.toString()
            }.empty
        }

        then: "All Up flows are pingable"
        def allFlows = northboundV2.getAllFlows()
        def assertions = new SoftAssertionsWrapper()
        def pingVerifications = new SoftAssertionsWrapper()

        def upFlowIds = allFlows.findAll { it.status == FlowState.UP.toString() }.flowId
        flows.findAll { it.flowId in upFlowIds }.forEach { flow ->
            pingVerifications.checkSucceeds {
                def ping = flow.ping()
                assert ping.forward.pingSuccess
                assert ping.reverse.pingSuccess
            }
        }

        and: "All Down flows are NOT pingable"
        def downFlowIds = allFlows.findAll { it.status == FlowState.DOWN.toString() }.flowId
        flows.findAll { it.flowId in downFlowIds }.forEach { flow ->
            pingVerifications.checkSucceeds {
                def ping = flow.ping()
                assert !ping.forward.pingSuccess
                assert !ping.reverse.pingSuccess
            }
        }
        assertions.checkSucceeds { pingVerifications.verify() } ?: true

        and: "There are no rule discrepancies on switches"
        assertions.checkSucceeds {
            Wrappers.wait(60 + preset.switchesAmount) {
                def soft = new SoftAssertionsWrapper()
                topology.switches.each { sw ->
                    def validation = northbound.validateSwitch(sw.dpId)
                    soft.checkSucceeds { assert validation.rules.missing.empty, sw }
                    soft.checkSucceeds { assert validation.rules.excess.empty, sw }
                    soft.checkSucceeds { assert validation.meters.missing.empty, sw }
                    soft.checkSucceeds { assert validation.meters.misconfigured.empty, sw }
                    soft.checkSucceeds { assert validation.meters.excess.empty, sw }
                }
                soft.verify()
            }
        } ?: true

        and: "There are no oversubscribed ISLs"
        northbound.getAllLinks().forEach { isl ->
            assertions.checkSucceeds { isl.availableBandwidth >= 0 }
        }
        assertions.verify()

        cleanup: "delete flows and purge topology"
        deleteFlows(flows)
        topology && topoHelper.purgeTopology(topology)

        where:
        preset << [
                [
                        debug             : true,
                        switchesAmount    : 30,
                        islsAmount        : 60,
                        eventsAmount      : 40,
                        flowsToStartWith  : 400,
                        pauseBetweenEvents: 3, //seconds
                ],
                [
                        debug             : false,
                        switchesAmount    : 60,
                        islsAmount        : 120,
                        eventsAmount      : 200,
                        flowsToStartWith  : 350,
                        pauseBetweenEvents: 1, //seconds
                ]
        ]
        //define payload generating method that will be called each time flow creation is issued

        makeFlowPayload = {
            List<SwitchPortVlan> busyEndpoints = flows.collect { it.occupiedEndpoints() }.flatten() as List<SwitchPortVlan>
            def srcSw = switches.all().first()
            def dstSw = pickRandom(switches.all().getListOfSwitches() - srcSw)
            FlowExtended flow = flowFactory.getBuilder(srcSw, dstSw, false, busyEndpoints)
                    .withBandwidth(200000)
                    .withProtectedPath(r.nextBoolean()).build()
            return flow
        }
        //'dice' below defines events and their chances to appear
        dice = new Dice([
                new Face(name: "delete flow", chance: 25, event: { deleteFlow() }),
                new Face(name: "update flow", chance: 0, event: { updateFlow() }),
                new Face(name: "create flow", chance: 25, event: { createFlow(makeFlowPayload(), true) }),
                new Face(name: "blink isl", chance: 15, event: { blinkIsl() }),
                new Face(name: "idle", chance: 20, event: { TimeUnit.SECONDS.sleep(3) }),
                new Face(name: "manual reroute 5% of flows", chance: 0, event: { massReroute() }),
                new Face(name: "break isl", chance: 15, event: { breakIsl() }),
                //switch blink cause missing rules due to https://github.com/telstra/open-kilda/issues/3398
                new Face(name: "blink switch", chance: 0, event: { blinkSwitch() })
        ])
        debugText = preset.debug ? " (debug mode)" : ""
    }

    @Ignore("Deal with new setupSpec which currently purges topology")
    def "Random events appear on existing env for certain period of time"() {
        Assume.assumeThat(preset.debug, equalTo(debug))

        given: "A live env with certain topology deployed and existing flows"
        topology = topoHelper.readCurrentTopology()
        setTopologyInContext(topology)
        topoHelper.setTopology(topology)
        def existingFlows = northboundV2.getAllFlows().collect {
            new FlowExtended(it, northbound, northboundV2, topology, flowFactory.cleanupManager, database)
        }
        flows.addAll(existingFlows)

        when: "With certain probability one of the following events occurs: flow creation, flow deletion, isl blink, \
idle, mass manual reroute, isl break. Step repeats for pre-defined amount of time"
        Wrappers.timedLoop(preset.durationMinutes * 60) {
            log.debug("running event #$it")
            dice.roll()
            TimeUnit.SECONDS.sleep(preset.pauseBetweenEvents)
        }

        and: "Wait for any blinking isls to get UP and flows to finish rerouting"
        //'WAIT_OFFSET * 3' is for unknown defect which is yet to be tracked down
        Wrappers.wait(antiflap.getAntiflapCooldown() + discoveryInterval + WAIT_OFFSET * 3) {
            def isls = northbound.getAllLinks()
            (topology.isls - brokenIsls).each {
                assert islUtils.getIslInfo(isls, it).get().state == IslChangeType.DISCOVERED
            }
        }
        Wrappers.benchmark("flows settling down") {
            //On bigger scale it takes more time to reroute all the flows. Use big wait that depends on flows amount
            Wrappers.wait(WAIT_OFFSET + flows.size()) {
                assert northbound.getAllFlows().findAll { it.status == FlowState.IN_PROGRESS.toString() }.empty
            }
        }

        then: "All Up flows are pingable"
        log.info "Starting validation phase"
        def allFlows = northbound.getAllFlows()
        def assertions = new SoftAssertionsWrapper()
        def pingVerifications = new SoftAssertionsWrapper()
        def upFlowIds = allFlows.findAll{ it.status == FlowState.UP.toString() }.id
        withPool(10) {
            flows.findAll { it.flowId in upFlowIds }.eachParallel { FlowExtended flow ->
                if (isFlowPingable(flow)) {
                    pingVerifications.checkSucceeds {
                        def ping = flow.ping()
                        assert ping.forward.pingSuccess
                        assert ping.reverse.pingSuccess
                    }
                }
            }
        } ?: true

        and: "All Down flows are NOT pingable"
        def downFlowIds = allFlows.findAll{ it.status == FlowState.DOWN.toString() }.id
        withPool(10) {
            flows.findAll{ it.flowId in downFlowIds }.eachParallel { FlowExtended flow ->
                if (isFlowPingable(flow)) {
                    pingVerifications.checkSucceeds {
                        def ping = flow.ping()
                        assert !ping.forward.pingSuccess
                        assert !ping.reverse.pingSuccess
                    }
                }
            }
        } ?: true
        assertions.checkSucceeds { pingVerifications.verify() } ?: true

        and: "There are no rule discrepancies on switches"
        def switches = northbound.getAllSwitches()
        assertions.checkSucceeds {
            Wrappers.wait(60 + switches.size()) {
                def soft = new SoftAssertionsWrapper()
                topology.switches.each { sw ->
                    def validation = northbound.validateSwitch(sw.dpId)
                    soft.checkSucceeds { assert validation.rules.missing.empty, sw }
                    soft.checkSucceeds { assert validation.rules.excess.empty, sw }
                    if (sw.ofVersion != "OF_12") {
                        soft.checkSucceeds { assert validation.meters.missing.empty, sw }
                        soft.checkSucceeds { assert validation.meters.misconfigured.empty, sw }
                        soft.checkSucceeds { assert validation.meters.excess.empty, sw }
                    }
                }
                soft.verify()
            }
        } ?: true

        and: "There are no oversubscribed ISLs"
        northbound.getAllLinks().forEach { isl ->
            assertions.checkSucceeds { isl.availableBandwidth >= 0 }
        }
        assertions.verify()

        cleanup: "Restore broken ISLs"
        withPool {
            brokenIsls.eachParallel { Isl isl ->
                antiflap.portUp(isl.srcSwitch.dpId, isl.srcPort)
                Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
                    assert northbound.getLink(isl).state == IslChangeType.DISCOVERED
                }
            }
        }

        where:
        preset << [
                [
                        debug             : true,
                        durationMinutes   : 10,
                        pauseBetweenEvents: 1, //seconds
                ]
        ]
        //'dice' below defines events and their chances to appear
        dice = new Dice([
                new Face(name: "blink isl", chance: 85, event: { blinkIsl() }),
                new Face(name: "idle", chance: 15, event: { TimeUnit.SECONDS.sleep(15) })
        ])
        debugText = preset.debug ? " (debug mode)" : ""
    }

    //TODO(rtretiak): test that continuously add/remove different switches. Ensure no memory leak over time

    def createFlow(FlowExtended flow, waitForRules = false) {
        Wrappers.silent {
            log.info "creating flow $flow.flowId"
            waitForRules ? flow.create() : flow.sendCreateRequest()
            flows << flow
            return flow
        }
    }

    def deleteFlow() {
        def flowToDelete = flows[r.nextInt(flows.size())]
        log.info "deleting flow $flowToDelete.flowId"
        try {
            flowToDelete.sendDeleteRequest()
        } catch (HttpStatusCodeException e) {
            if (e.statusCode == HttpStatus.NOT_FOUND) {
                //flow already removed, do nothing
            } else {
                log.error("", e)
                return null
            }
        }
        flows.remove(flowToDelete)
        return flowToDelete
    }

    def updateFlow() {
        def flowToUpdate = flows[r.nextInt(flows.size())]
        log.info "updating flow $flowToUpdate.flowId"
        Wrappers.silent {
            flowToUpdate.update(flowToUpdate.tap { it.maximumBandwidth = it.maximumBandwidth + r.nextInt(10000) })

        }
        return flowToUpdate
    }

    def blinkIsl() {
        def isl = topology.isls[r.nextInt(topology.isls.size())]
        log.info "blink isl $isl"
        northbound.portDown(isl.srcSwitch.dpId, isl.srcPort)
        def sleepBeforePortUp = r.nextInt(5) + 1
        task {
            TimeUnit.SECONDS.sleep(sleepBeforePortUp)
            northbound.portUp(isl.srcSwitch.dpId, isl.srcPort)
        }.then({ it }, { throw it })
    }

    def breakIsl() {
        def isl = topology.isls[r.nextInt(topology.isls.size())]
        log.info "break isl $isl"
        brokenIsls << isl
        antiflap.portDown(isl.srcSwitch.dpId, isl.srcPort)
    }

    def massReroute() {
        log.info "mass rerouting flows"
        //shuffle all isl costs
        def randomCost = { (r.nextInt(800) + 200).toString() }
        northbound.updateLinkProps(topology.isls.collect {
            islUtils.toLinkProps(it, [cost: randomCost()])
        })
        //call reroute on quarter of existing flows, randomly
        def flows = flows.findAll()
        Collections.shuffle(flows)
        task {
            withPool {
                flows[0..flows.size() / 20].eachParallel { FlowExtended flow ->
                    Wrappers.silent {
                        //may fail due to 'in progress' status, just retry
                        Wrappers.retry(5, 1, {}) {
                            flow.reroute()
                        }
                    }
                }
            }
        }
    }

    def blinkSwitch() {
        def sw = topology.activeSwitches[r.nextInt(topology.activeSwitches.size())]
        log.info "blink sw $sw.dpId"
        def blockData = lockKeeper.knockoutSwitch(sw, RW)
        def sleepBeforeSwUp = r.nextInt(5) + 1
        task {
            TimeUnit.SECONDS.sleep(sleepBeforeSwUp)
            lockKeeper.reviveSwitch(sw, blockData)
        }.then({ it }, { throw it })
    }

    boolean isFlowPingable(FlowExtended flow) {
        if (flow.source.switchId == flow.destination.switchId) {
            return false
        } else {
            def srcSw = switches.all().findSpecific(flow.source.switchId)
            def dstSw = switches.all().findSpecific(flow.destination.switchId)
            !(srcSw.isOf12Version() || srcSw.isCentec() || dstSw.isOf12Version() || dstSw.isCentec())
        }
    }
}
