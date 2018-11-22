package org.openkilda.functionaltests.spec.northbound.flows

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.extension.fixture.rule.CleanupSwitches
import org.openkilda.functionaltests.helpers.FlowHelper
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.model.SwitchId
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.traffexam.FlowNotApplicableException
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.tools.FlowTrafficExamBuilder

import groovy.transform.Memoized
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.Unroll

@Slf4j
@CleanupSwitches
@Narrative("Verify CRUD operations and health of most typical types of flows on different types of switches.")
class FlowCrudSpec extends BaseSpecification {

    @Autowired
    TopologyDefinition topology
    @Autowired
    Database db
    @Autowired
    FlowHelper flowHelper
    @Autowired
    PathHelper pathHelper
    @Autowired
    NorthboundService northboundService
    @Autowired
    TraffExamService traffExam;

    @Shared
    FlowTrafficExamBuilder examBuilder

    def setupOnce() {
        examBuilder = new FlowTrafficExamBuilder(topologyDefinition, traffExam)
    }

    @Unroll("Valid #data.description has traffic and no rule discrepancies \
            (#flow.source.datapath - #flow.destination.datapath)")
    def "Valid flow has no rule discrepancies"() {
        given: "A flow"
        flowHelper.addFlow(flow)
        def path = PathHelper.convert(northboundService.getFlowPath(flow.id))
        def switches = pathHelper.getInvolvedSwitches(path)
        //for single-flow cases need to add switch manually here, since PathHelper.convert will return an empty path
        if (flow.source.datapath == flow.destination.datapath) {
            switches << topology.activeSwitches.find { it.dpId == flow.source.datapath }
        }

        expect: "No rule discrepancies on every switch of the flow"
        switches.each {
            verifySwitchRules(it.dpId)
        }

        and: "No discrepancies when doing flow validation"
        northboundService.validateFlow(flow.id).each { direction ->
            def discrepancies = profile == "virtual" ?
                    direction.discrepancies.findAll { it.field != "meterId" } : //unable to validate meters for virtual
                    direction.discrepancies
            assert discrepancies.empty
        }

        and: "The flow allows traffic (only applicable flows are checked)"
        try {
            def exam = examBuilder.buildBidirectionalExam(flow, 0)
            [exam.forward, exam.reverse].each { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        } catch (FlowNotApplicableException e) {
            //flow is not applicable for traff exam. That's fine, just inform
            log.warn(e.message)
        } catch (UnsupportedOperationException e) {
            log.warn("skipping traff exam for flow $flow.id. " +
                    "we are on virtual env and for now traff exam is not available here")
        }

        when: "Remove the flow"
        flowHelper.deleteFlow(flow.id)

        then: "The flow is not present in NB"
        !northboundService.getAllFlows().find { it.id == flow.id }

        and: "ISL bandwidth is restored"
        Wrappers.wait(WAIT_OFFSET) { northbound.getAllLinks().each { assert it.availableBandwidth == it.speed } }

        and: "No rule discrepancies on every switch of the flow"
        switches.each { sw ->
            Wrappers.wait(WAIT_OFFSET) {
                verifySwitchRules(sw.dpId)
            }
        }

        where:
        /*Some permutations may be missed, since at current implementation we only take 'direct' possible flows
        * without modifying the costs of ISLs.
        * I.e. if potential test case with transit switch between certain pair of unique switches will require change of
        * costs on ISLs (those switches are neighbors, but we want a path with transit switch between them), then
        * we will not test such case
        */
        data << flowsWithoutTransitSwitch + flowsWithTransitSwitch + singleSwitchFlows
        flow = data.flow as FlowPayload
    }

    @Unroll
    def "Able to create single switch single port flow with different vlan (#flow.source.datapath)"(FlowPayload flow) {
        given: "A flow"
        flowHelper.addFlow(flow)

        expect: "No rule discrepancies on the switch"
        verifySwitchRules(flow.source.datapath)

        and: "No discrepancies when doing flow validation"
        northboundService.validateFlow(flow.id).each { direction ->
            def discrepancies = profile == "virtual" ?
                    direction.discrepancies.findAll { it.field != "meterId" } : //unable to validate meters for virtual
                    direction.discrepancies
            assert discrepancies.empty
        }

        when: "Remove the flow"
        flowHelper.deleteFlow(flow.id)

        then: "The flow is not present in NB"
        !northboundService.getAllFlows().find { it.id == flow.id }

        and: "ISL bandwidth is restored"
        Wrappers.wait(WAIT_OFFSET) { northbound.getAllLinks().each { assert it.availableBandwidth == it.speed } }

        and: "No rule discrepancies on the switch after delete"
        Wrappers.wait(WAIT_OFFSET) {
            verifySwitchRules(flow.source.datapath)
        }

        where:
        flow << getSingleSwitchSinglePortFlows()
    }

    def "Unable to create single-switch flow with the same ports and vlans on both sides"() {
        given: "Potential single-switch flow with the same ports and vlans on both sides"
        def flow = flowHelper.singleSwitchSinglePortFlow(topology.activeSwitches.first())
        flow.destination.vlanId = flow.source.vlanId

        when: "Try creating such flow"
        northbound.addFlow(flow)

        then: "Error is returned, stating a readable reason"
        def error = thrown(HttpClientErrorException)
        error.statusCode == HttpStatus.BAD_REQUEST
        error.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not create flow: It is not allowed to create one-switch flow for the same ports and vlans"
    }

    @Unroll("Unable to create flow with #data.conflict")
    def "Unable to create flow with conflicting vlans or flowIds"() {
        given: "A potential flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)

        and: "Another potential flow with #data.conflict"
        def conflictingFlow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        data.makeFlowsConflicting(flow, conflictingFlow)

        when: "Create the first flow"
        flowHelper.addFlow(flow)

        and: "Try creating the second flow which conflicts"
        northbound.addFlow(conflictingFlow)

        then: "Error is returned, stating a readable reason of conflict"
        def error = thrown(HttpClientErrorException)
        error.statusCode == HttpStatus.CONFLICT
        error.responseBodyAsString.to(MessageError).errorMessage == data.getError(flow)

        and: "Cleanup: delete the dominant flow"
        flowHelper.deleteFlow(flow.id)

        where: data << [
            [
                conflict: "the same vlans on same port on src",
                makeFlowsConflicting: { FlowPayload dominantFlow, FlowPayload flowToConflict ->
                    flowToConflict.source.portNumber = dominantFlow.source.portNumber
                    flowToConflict.source.vlanId = dominantFlow.source.vlanId
                },
                getError: { FlowPayload flowToError ->
                    portError(flowToError.source.portNumber, flowToError.source.datapath, flowToError.id)
                }
            ],
            [
                conflict: "no vlan vs vlan on same port on dst",
                makeFlowsConflicting: { FlowPayload dominantFlow, FlowPayload flowToConflict ->
                    flowToConflict.destination.portNumber = dominantFlow.destination.portNumber
                    flowToConflict.destination.vlanId = 0
                },
                getError: { FlowPayload flowToError ->
                    portError(flowToError.destination.portNumber, flowToError.destination.datapath, flowToError.id)
                }
            ],
            [
                conflict: "no vlans both flows on same port on src",
                makeFlowsConflicting: { FlowPayload dominantFlow, FlowPayload flowToConflict ->
                    flowToConflict.source.portNumber = dominantFlow.source.portNumber
                    flowToConflict.source.vlanId = 0
                    dominantFlow.source.vlanId = 0
                },
                getError: { FlowPayload flowToError ->
                    portError(flowToError.source.portNumber, flowToError.source.datapath, flowToError.id)
                }
            ],
            [
                conflict: "the same flowId",
                makeFlowsConflicting: { FlowPayload dominantFlow, FlowPayload flowToConflict ->
                    flowToConflict.id = dominantFlow.id
                },
                getError: { FlowPayload flowToError ->
                    "Can not create flow: Flow $flowToError.id already exists"
                }
            ]
        ]
    }

    @Shared
    def portError = { int port, SwitchId switchId, String flowId ->
        "Could not create flow: The port $port on the switch '$switchId' has already occupied by the flow '$flowId'."
    }

    /**
     * Get list of all unique flows without transit switch (neighboring switches), permute by vlan presence. 
     * By unique flows it considers combinations of unique src/dst switch descriptions and OF versions.
     */
    def getFlowsWithoutTransitSwitch() {
        def switchPairs = [topology.activeSwitches, topology.activeSwitches].combinations()
            .findAll { src, dst -> src != dst } //non-single-switch
            .unique { it.sort() } //no reversed versions of same flows
            .findAll { Switch src, Switch dst ->
                getPreferredPath(src, dst).size() == 2 //switches are neighbors
            }
            .sort(taffgensPrioritized)
            .unique { it.collect { [getDescription(it), it.ofVersion] }.sort() }

        return switchPairs.inject([]) { r, switchPair ->
            r << [
                    description: "flow without transit switch and with random vlans",
                    flow       : flowHelper.randomFlow(switchPair[0], switchPair[1])
            ]
            r << [
                    description: "flow without transit switch and without vlans",
                    flow       : flowHelper.randomFlow(switchPair[0], switchPair[1]).tap {
                        it.source.vlanId = 0
                        it.destination.vlanId = 0
                    }
            ]
            r << [
                    description: "flow without transit switch and vlan only on src",
                    flow       : flowHelper.randomFlow(switchPair[0], switchPair[1]).tap { it.destination.vlanId = 0 }
            ]
            r
        }
    }

    /**
     * Get list of all unique flows with transit switch (not neighboring switches), permute by vlan presence. 
     * By unique flows it considers combinations of unique src/dst switch descriptions and OF versions.
     */
    def getFlowsWithTransitSwitch() {
        def switchPairs = [topology.activeSwitches, topology.activeSwitches].combinations()
            .findAll { src, dst -> src != dst } //non-single-switch
            .unique { it.sort() } //no reversed versions of same flows
            .findAll { Switch src, Switch dst ->
                getPreferredPath(src, dst).size() > 2 //switches are not neighbors
            }
            .sort(taffgensPrioritized)
            .unique { it.collect { [getDescription(it), it.ofVersion] }.sort() }

        return switchPairs.inject([]) { r, switchPair ->
            r << [
                    description: "flow with transit switch and random vlans",
                    flow       : flowHelper.randomFlow(switchPair[0], switchPair[1])
            ]
            r << [
                    description: "flow with transit switch and no vlans",
                    flow       : flowHelper.randomFlow(switchPair[0], switchPair[1]).tap {
                        it.source.vlanId = 0
                        it.destination.vlanId = 0
                    }
            ]
            r << [
                    description: "flow with transit switch and vlan only on dst",
                    flow       : flowHelper.randomFlow(switchPair[0], switchPair[1]).tap { it.source.vlanId = 0 }
            ]
            r
        }
    }

    /**
     * Get list of all unique single-switch flows, permute by vlan presence. By unique flows it considers
     * using all unique switch descriptions and OF versions.
     */
    def getSingleSwitchFlows() {
        topology.getActiveSwitches()
                .sort { sw -> topology.activeTraffGens.findAll { it.switchConnected == sw }.size() }.reverse()
                .unique { [getDescription(it), it.ofVersion].sort() }
                .inject([]) { r, sw ->
            r << [
                    description: "single-switch flow with vlans",
                    flow       : flowHelper.singleSwitchFlow(sw)
            ]
            r << [
                    description: "single-switch flow without vlans",
                    flow       : flowHelper.singleSwitchFlow(sw).tap {
                        it.source.vlanId = 0
                        it.destination.vlanId = 0
                    }
            ]
            r << [
                    description: "single-switch flow with vlan only on dst",
                    flow       : flowHelper.singleSwitchFlow(sw).tap {
                        it.source.vlanId = 0
                    }
            ]
            r
        }
    }

    /**
     * Get list of all unique single-switch flows. By unique flows it considers
     * using all unique switch descriptions and OF versions.
     */
    def getSingleSwitchSinglePortFlows() {
        topology.getActiveSwitches()
                .unique { [getDescription(it), it.ofVersion].sort() }
                .collect { flowHelper.singleSwitchSinglePortFlow(it) }
    }

    /**
     * Switch pairs with more traffgen-available switches will go first. Then the less tg-available switches there is
     * in the pair the lower score that pair will get.
     * During the subsequent 'unique' call the higher scored pairs will have priority over lower scored ones in case
     * if their uniqueness criteria will be equal.
     */
    @Shared
    def taffgensPrioritized = { List<Switch> switches ->
        switches.count { sw -> !topology.activeTraffGens.find { it.switchConnected == sw } }
    }

    @Memoized
    def getNbSwitches() {
        northboundService.getActiveSwitches()
    }

    @Memoized
    def getPreferredPath(Switch src, Switch dst) {
        def possibleFlowPaths = db.getPaths(src.dpId, dst.dpId)*.path
        return possibleFlowPaths.min {
            /*
              Taking the actual cost of every ISL for all the permutations takes ages. Since we assume that at the
              start of the test all isls have the same cost, just take the 'shortest' path and consider it will be
              preferred.
              Another option was to introduce cache for getIslCost calls, but implementation was too bulky.
             */
            it.size()
        }
    }

    String getDescription(Switch sw) {
        getNbSwitches().find { it.switchId == sw.dpId }.description
    }
}
