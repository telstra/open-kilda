package org.openkilda.functionaltests.spec.flows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.YFlowHelper
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.northbound.dto.v2.yflows.YFlowCreatePayload
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.traffexam.FlowNotApplicableException
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.tools.FlowTrafficExamBuilder

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Ignore
import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.Unroll

import javax.inject.Provider

@Slf4j
@Narrative("Verify CRUD operations on y-flows.")
class YFlowCrudSpec extends HealthCheckSpecification {
    @Autowired @Shared
    YFlowHelper yFlowHelper
    @Autowired @Shared
    Provider<TraffExamService> traffExamProvider

    @Ignore
    @Tidy
    @Tags([TOPOLOGY_DEPENDENT])
    @Unroll("Valid #data.description has traffic and no rule discrepancies \
(#flow.source.switchId - #flow.destination.switchId)")
    def "Valid y-flow has no rule discrepancies"() {
        given: "A y-flow"
        assumeTrue(topology.activeTraffGens.size() >= 2,
"There should be at least two active traffgens for test execution")
        def traffExam = traffExamProvider.get()
        def allLinksInfoBefore = northbound.getAllLinks().collectEntries { [it.id, it.availableBandwidth] }.sort()
        flowHelperV2.addFlow(flow)
        def path = PathHelper.convert(northbound.getFlowPath(flow.flowId))
        def switches = pathHelper.getInvolvedSwitches(path)
        //for single-flow cases need to add switch manually here, since PathHelper.convert will return an empty path
        if (flow.source.switchId == flow.destination.switchId) {
            switches << topology.activeSwitches.find { it.dpId == flow.source.switchId }
        }

        expect: "No rule discrepancies on every switch of the flow"
        wait(WAIT_OFFSET) { //due to instability on jenkins in multiTable mode + server42
            switches.each { verifySwitchRules(it.dpId) }
        }

        and: "No discrepancies when doing flow validation"
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }

        and: "The flow allows traffic (only applicable flows are checked)"
        def beforeTraffic = new Date()
        def trafficApplicable = true
        try {
            def exam = new FlowTrafficExamBuilder(topology, traffExam).buildBidirectionalExam(toFlowPayload(flow), 1000, 3)
            withPool {
                [exam.forward, exam.reverse].eachParallel { direction ->
                    def resources = traffExam.startExam(direction)
                    direction.setResources(resources)
                    assert traffExam.waitExam(direction).hasTraffic()
                }
            }
        } catch (FlowNotApplicableException e) {
            //flow is not applicable for traff exam. That's fine, just inform
            log.warn(e.message)
            trafficApplicable = false
        }

        and: "Flow writes stats"
        if(trafficApplicable) { statsHelper.verifyFlowWritesStats(flow.flowId, beforeTraffic, true) }

        when: "Remove the flow"
        flowHelperV2.deleteFlow(flow.flowId)

        then: "The flow is not present in NB"
        !northboundV2.getAllFlows().find { it.flowId == flow.flowId }
        def flowIsDeleted = true

        and: "ISL bandwidth is restored"
        Wrappers.wait(WAIT_OFFSET) {
            def allLinksInfoAfter = northbound.getAllLinks().collectEntries { [it.id, it.availableBandwidth] }.sort()
            assert allLinksInfoBefore == allLinksInfoAfter
        }

        and: "No rule discrepancies on every switch of the flow"
        switches.each { sw -> Wrappers.wait(WAIT_OFFSET) { verifySwitchRules(sw.dpId) } }

        cleanup:
        !flowIsDeleted && flow && flowHelperV2.deleteFlow(flow.flowId)

        where:
        /*Some permutations may be missed, since at current implementation we only take 'direct' possible flows
        * without modifying the costs of ISLs.
        * I.e. if potential test case with transit switch between certain pair of unique switches will require change of
        * costs on ISLs (those switches are neighbors, but we want a path with transit switch between them), then
        * we will not test such case
        */
        data << flowsWithTransitSwitch
        flow = data.flow as YFlowCreatePayload
    }

    /**
     * Potential flows with more traffgen-available switches will go first. Then the less tg-available switches there is
     * in the pair the lower score that pair will get.
     * During the subsequent 'unique' call the higher scored pairs will have priority over lower scored ones in case
     * if their uniqueness criteria will be equal.
     */
    @Shared
    def traffgensPrioritized = { SwitchPair switchPair ->
        [switchPair.src, switchPair.dst].count { Switch sw ->
            !topology.activeTraffGens.find { it.switchConnected == sw }
        }
    }

    /**
     * Get list of all unique flows with transit switch (not neighboring switches), permute by vlan presence.
     * By unique flows it considers combinations of unique src/dst switch descriptions and OF versions.
     */
    def getFlowsWithTransitSwitch() {
        def switchPairs = topologyHelper.getAllNotNeighboringSwitchPairs().sort(traffgensPrioritized)
                .unique { [it.src, it.dst]*.description.sort() }

        return switchPairs.inject([]) { r, switchPair ->
            r << [
                    description: "y-flow with transit switch and random vlans",
                    flow       : yFlowHelper.randomYFlow(switchPair)
            ]
            r << [
                    description: "y-flow with transit switch and no vlans",
                    flow       : yFlowHelper.randomYFlow(switchPair).tap {
                        it.source.vlanId = 0
                        it.destination.vlanId = 0
                    }
            ]
            r << [
                    description: "y-flow with transit switch and vlan only on dst",
                    flow       : yFlowHelper.randomYFlow(switchPair).tap { it.source.vlanId = 0 }
            ]
            r
        }
    }
}
