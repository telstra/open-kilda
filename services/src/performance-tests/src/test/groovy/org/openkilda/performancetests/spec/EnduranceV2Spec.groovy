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
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.openkilda.testing.tools.SoftAssertions

import groovy.util.logging.Slf4j
import org.junit.Assume
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.Unroll

import java.util.concurrent.TimeUnit

@Slf4j
@Narrative("This spec to hold tests that exercise system's ability to continuously survive under certain conditions.")
class EnduranceV2Spec extends BaseSpecification {
    @Autowired
    NorthboundServiceV2 northboundV2
    @Autowired
    FlowHelperV2 flowHelperV2

    @Shared
    List<FlowRequestV2> flows = Collections.synchronizedList(new ArrayList<FlowRequestV2>())
    @Shared
    TopologyDefinition topology
    @Shared
    def r = new Random()
    @Shared
    List<Isl> brokenIsls = Collections.synchronizedList(new ArrayList<Isl>())

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
    @Unroll
    def "Simulate live environment with random events happening(#preset.name)"() {
        Assume.assumeThat(preset.debug, equalTo(debug))

        setup: "Create topology according to passed params"
        setTopologies(topoHelper.createRandomTopology(preset.switchesAmount, preset.islsAmount))

        and: "As starting point, create some amount of random flows in it"
        preset.flowsToStartWith.times { createFlow(makeFlowPayload()) }
        Wrappers.wait(flows.size() / 2) {
            flows.each {
                assert northbound.getFlowStatus(it.flowId).status == FlowState.UP
                northbound.validateFlow(it.flowId).each { direction -> assert direction.asExpected }
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
        Wrappers.wait(WAIT_OFFSET + flows.size() * 0.5) {
            def isls = northbound.getAllLinks()
            (topology.isls - brokenIsls).each {
                assert islUtils.getIslInfo(isls, it).get().state == IslChangeType.DISCOVERED
            }
            assert northbound.getAllFlows().findAll { it.status == FlowState.IN_PROGRESS.toString() }.empty
        }

        then: "All Up flows are pingable"
        def pingVerifications = new SoftAssertions()
        def allFlows = northbound.getAllFlows()
        allFlows.findAll { it.status == FlowState.UP.toString() }.every { flow ->
            pingVerifications.checkSucceeds {
                def ping = northbound.pingFlow(flow.id, new PingInput())
                assert ping.forward.pingSuccess
                assert ping.reverse.pingSuccess
            }
            true
        }

        and: "All Down flows are NOT pingable"
        allFlows.findAll { it.status == FlowState.DOWN.toString() }.every { flow ->
            pingVerifications.checkSucceeds {
                def ping = northbound.pingFlow(flow.id, new PingInput())
                assert !ping.forward.pingSuccess
                assert !ping.reverse.pingSuccess
            }
            true
        }
        pingVerifications.verify()

        and: "There are no rule discrepancies on switches"
        Wrappers.wait(60 + preset.switchesAmount) {
            def soft = new SoftAssertions()
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

        cleanup: "delete flows and purge topology"
        flows.each { northbound.deleteFlow(it.flowId) }
        topology && topoHelper.purgeTopology(topo)

        where:
        preset << [
                [
                        debug             : true,
                        switchesAmount    : 30,
                        islsAmount        : 60,
                        eventsAmount      : 40,
                        flowsToStartWith  : 200,
                        pauseBetweenEvents: 1, //seconds
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
        makeFlowPayload = {
            def flow = flowHelperV2.randomFlow(*topoHelper.getRandomSwitchPair(), false, flows)
            flow.maximumBandwidth = 500000
            return flow
        }
        dice = new Dice([
                new Face(name: "delete flow", chance: 19, event: { deleteFlow() }),
                new Face(name: "create flow", chance: 19, event: { createFlow(makeFlowPayload(), true) }),
                new Face(name: "blink isl", chance: 22, event: { blinkIsl() }),
                new Face(name: "idle", chance: 0, event: { TimeUnit.SECONDS.sleep(3) }),
                new Face(name: "manual reroute 25% of flows", chance: 12, event: { massReroute() }),
                new Face(name: "break isl", chance: 28, event: { breakIsl() })
        ])
    }

    //TODO(rtretiak): test that continuously add/remove different switches. Ensure no memory leak over time

    def setTopologies(TopologyDefinition topology) {
        this.topology = topology
        topoHelper.setTopology(topology)
        flowHelper.setTopology(topology)
        flowHelperV2.setTopology(topology)
    }

    def createFlow(FlowRequestV2 flow, waitForRules = false) {
        Wrappers.silent {
            log.info "creating flow $flow.flowId"
            waitForRules ? flowHelperV2.addFlow(flow) : northboundV2.addFlow(flow)
            flows << flow
            return flow
        }
    }

    def deleteFlow() {
        def flowToDelete = flows.remove(r.nextInt(flows.size()))
        log.info "deleting flow $flowToDelete.flowId"
        Wrappers.silent { northbound.deleteFlow(flowToDelete.flowId) }
        return flowToDelete
    }

    def blinkIsl() {
        def isl = topology.isls[r.nextInt(topology.isls.size())]
        log.info "blink isl $isl"
        antiflap.portDown(isl.srcSwitch.dpId, isl.srcPort)
        def sleepBeforePortUp = r.nextInt(5)
        task {
            TimeUnit.SECONDS.sleep(sleepBeforePortUp)
            antiflap.portUp(isl.srcSwitch.dpId, isl.srcPort)
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
