package org.openkilda.functionaltests.spec.northbound.flows

import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.extension.fixture.rule.CleanupSwitches
import org.openkilda.functionaltests.helpers.FlowHelper
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.model.SwitchId
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.traffexam.FlowNotApplicableException
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.tools.FlowTrafficExamBuilder
import org.openkilda.testing.tools.IslUtils

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
    IslUtils islUtils
    @Autowired
    NorthboundService northboundService
    @Autowired
    TraffExamService traffExam

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

    @Unroll("Able to create a second flow if #data.description")
    def "Able to create multiple flows on certain combinations of switch-port-vlans"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topologyDefinition.activeSwitches
        def flow1 = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow1)

        and: "Try creating a second flow with #data.description"
        FlowPayload flow2 = data.getSecondFlow(flow1)
        flowHelper.addFlow(flow2)

        then: "Both flows are successfully created"
        northboundService.getAllFlows()*.id.containsAll([flow1.id, flow2.id])

        and: "cleanup: delete flows"
        [flow1, flow2].each { northboundService.deleteFlow(it.id) }

        where:
        data << [
                [
                        description  : "same switch-port but vlans on src and dst are swapped",
                        getSecondFlow: { FlowPayload existingFlow ->
                            return getFlowHelper().randomFlow(findSwitch(existingFlow.source.datapath),
                                    findSwitch(existingFlow.destination.datapath)).tap {
                                it.source.portNumber = existingFlow.source.portNumber
                                it.source.vlanId = existingFlow.destination.vlanId
                                it.destination.portNumber = existingFlow.destination.portNumber
                                it.destination.vlanId = existingFlow.source.vlanId
                            }
                        }
                ],
                [
                        description  : "same switch-port but vlans on src and dst are different",
                        getSecondFlow: { FlowPayload existingFlow ->
                            return getFlowHelper().randomFlow(findSwitch(existingFlow.source.datapath),
                                    findSwitch(existingFlow.destination.datapath)).tap {
                                it.source.portNumber = existingFlow.source.portNumber
                                it.source.vlanId = existingFlow.source.vlanId + 1
                                it.destination.portNumber = existingFlow.destination.portNumber
                                it.destination.vlanId = existingFlow.destination.vlanId + 1
                            }
                        }
                ],
                [
                        description  : "vlan-port of new src = vlan-port of existing dst (but different switches)",
                        getSecondFlow: { FlowPayload existingFlow ->
                            //src for new flow will be on different switch not related to existing flow
                            //thus two flows will have same dst but different src
                            def newSrcSwitch = getTopology().activeSwitches.find {
                                ![existingFlow.source.datapath, existingFlow.destination.datapath].contains(it.dpId)
                            }
                            return getFlowHelper().randomFlow(newSrcSwitch, findSwitch(
                                    existingFlow.destination.datapath)).tap {
                                it.source.vlanId = existingFlow.destination.vlanId
                                it.source.portNumber = existingFlow.destination.portNumber
                                it.destination.vlanId = existingFlow.destination.vlanId + 1
                            }
                        }
                ],
                [
                        description  : "vlan-port of new dst = vlan-port of existing src (but different switches)",
                        getSecondFlow: { FlowPayload existingFlow ->
                            def newSrcSwitch = getTopology().activeSwitches.find {
                                ![existingFlow.source.datapath, existingFlow.destination.datapath].contains(it.dpId)
                            }
                            return getFlowHelper().randomFlow(newSrcSwitch, findSwitch(
                                    existingFlow.destination.datapath)).tap {
                                it.destination.vlanId = existingFlow.source.vlanId
                                it.destination.portNumber = existingFlow.source.portNumber
                            }
                        }
                ],
                [
                        description  : "vlan of new dst = vlan of existing src and port of new dst = port of " +
                                "existing dst",
                        getSecondFlow: { FlowPayload existingFlow ->
                            def newSrcSwitch = getTopology().activeSwitches.find {
                                ![existingFlow.source.datapath, existingFlow.destination.datapath].contains(it.dpId)
                            }
                            return getFlowHelper().randomFlow(newSrcSwitch, findSwitch(
                                    existingFlow.destination.datapath)).tap {
                                it.destination.vlanId = existingFlow.source.vlanId
                                it.destination.portNumber = existingFlow.destination.portNumber
                            }
                        }
                ]
        ]
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
    def "Unable to create flow with conflicting vlans or flow IDs"() {
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

        where:
        data << getConflictingData() + [
                conflict            : "the same flow ID",
                makeFlowsConflicting: { FlowPayload dominantFlow, FlowPayload flowToConflict ->
                    flowToConflict.id = dominantFlow.id
                },
                getError            : { FlowPayload flowToError ->
                    "Could not create flow: Flow $flowToError.id already exists"
                }
        ]
    }

    @Unroll("Unable to update flow (#data.conflict)")
    def "Unable to update flow when there are conflicting vlans"() {
        given: "Two potential flows"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow1 = flowHelper.randomFlow(srcSwitch, dstSwitch, false)
        def flow2 = flowHelper.randomFlow(srcSwitch, dstSwitch, false)

        // TODO(ylobankov): Delete this code once we add such a functionality to flow helper.
        // It is a kind of dirty workaround to not get a conflict at the point of the second flow creation.
        def random = new Random()
        def srcSwitchAllowedPorts = topology.getAllowedPortsForSwitch(srcSwitch)
        def dstSwitchAllowedPorts = topology.getAllowedPortsForSwitch(dstSwitch)
        while (!(flow1.source.portNumber != flow2.source.portNumber &&
                flow1.destination.portNumber != flow2.destination.portNumber)) {
            flow1.source.portNumber = random.nextInt(srcSwitchAllowedPorts.size())
            flow1.destination.portNumber = random.nextInt(dstSwitchAllowedPorts.size())
            flow2.source.portNumber = random.nextInt(srcSwitchAllowedPorts.size())
            flow2.destination.portNumber = random.nextInt(dstSwitchAllowedPorts.size())
        }

        def conflictingFlow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        data.makeFlowsConflicting(flow1, conflictingFlow.tap { it.id = flow2.id })

        when: "Create two flows"
        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)

        and: "Try updating the second flow which should conflict with the first one"
        northbound.updateFlow(flow2.id, conflictingFlow)

        then: "Error is returned, stating a readable reason of conflict"
        def error = thrown(HttpClientErrorException)
        error.statusCode == HttpStatus.CONFLICT
        error.responseBodyAsString.to(MessageError).errorMessage == data.getError(flow1, "update")

        and: "Cleanup: delete flows"
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }

        where:
        data << getConflictingData()
    }

    def "A flow cannot be created with asymmetric forward and reverse paths"() {
        given: "Two active neighboring switches with two possible flow paths at least and different number of hops"
        List<List<PathNode>> possibleFlowPaths = []
        int pathNodeCount = 2
        def (Switch srcSwitch, Switch dstSwitch) = topology.getIslsForActiveSwitches().find {
            possibleFlowPaths = db.getPaths(it.srcSwitch.dpId, it.dstSwitch.dpId)*.path.sort { it.size() }
            possibleFlowPaths.size() > 1 && possibleFlowPaths.max { it.size() }.size() > pathNodeCount
        }.collect {
            [it.srcSwitch, it.dstSwitch]
        }.flatten() ?: assumeTrue("No suiting active neighboring switches with two possible flow paths at least and " +
                "different number of hops found", false)

        and: "Make all shorter forward paths not preferable. Shorter reverse paths are still preferable"
        possibleFlowPaths.findAll { it.size() == pathNodeCount }.each {
            pathHelper.getInvolvedIsls(it).each { db.updateLinkCost(it, Integer.MAX_VALUE) }
        }

        when: "Create a flow"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)

        then: "The flow is built through one of the long paths"
        def flowPath = northboundService.getFlowPath(flow.id)
        !(PathHelper.convert(flowPath) in possibleFlowPaths.findAll { it.size() == pathNodeCount })

        and: "The flow has symmetric forward and reverse paths even though there is a more preferable reverse path"
        def forwardIsls = pathHelper.getInvolvedIsls(PathHelper.convert(flowPath))
        def reverseIsls = pathHelper.getInvolvedIsls(PathHelper.convert(flowPath, "reversePath"))
        forwardIsls.collect { islUtils.reverseIsl(it) }.reverse() == reverseIsls

        and: "Delete the flow and reset costs"
        flowHelper.deleteFlow(flow.id)
        db.resetCosts()
    }

    @Shared
    def portError = { String operation, int port, SwitchId switchId, String flowId ->
        "Could not $operation flow: The port $port on the switch '$switchId' has already occupied " +
                "by the flow '$flowId'."
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

    Switch findSwitch(SwitchId swId) {
        topologyDefinition.activeSwitches.find { it.dpId == swId }
    }

    def getConflictingData() {
        [
                [
                        conflict            : "the same vlans on the same port on src switch",
                        makeFlowsConflicting: { FlowPayload dominantFlow, FlowPayload flowToConflict ->
                            flowToConflict.source.portNumber = dominantFlow.source.portNumber
                            flowToConflict.source.vlanId = dominantFlow.source.vlanId
                        },
                        getError            : { FlowPayload flowToError, String operation = "create" ->
                            portError(operation, flowToError.source.portNumber, flowToError.source.datapath,
                                    flowToError.id)
                        }
                ],
                [
                        conflict            : "the same vlans on the same port on dst switch",
                        makeFlowsConflicting: { FlowPayload dominantFlow, FlowPayload flowToConflict ->
                            flowToConflict.destination.portNumber = dominantFlow.destination.portNumber
                            flowToConflict.destination.vlanId = dominantFlow.destination.vlanId
                        },
                        getError            : { FlowPayload flowToError, String operation = "create" ->
                            portError(operation, flowToError.destination.portNumber, flowToError.destination.datapath,
                                    flowToError.id)
                        }
                ],
                [
                        conflict            : "no vlan vs vlan on the same port on src switch",
                        makeFlowsConflicting: { FlowPayload dominantFlow, FlowPayload flowToConflict ->
                            flowToConflict.source.portNumber = dominantFlow.source.portNumber
                            flowToConflict.source.vlanId = 0
                        },
                        getError            : { FlowPayload flowToError, String operation = "create" ->
                            portError(operation, flowToError.source.portNumber, flowToError.source.datapath,
                                    flowToError.id)
                        }
                ],
                [
                        conflict            : "no vlan vs vlan on the same port on dst switch",
                        makeFlowsConflicting: { FlowPayload dominantFlow, FlowPayload flowToConflict ->
                            flowToConflict.destination.portNumber = dominantFlow.destination.portNumber
                            flowToConflict.destination.vlanId = 0
                        },
                        getError            : { FlowPayload flowToError, String operation = "create" ->
                            portError(operation, flowToError.destination.portNumber, flowToError.destination.datapath,
                                    flowToError.id)
                        }
                ],
                [
                        conflict            : "vlan vs no vlan on the same port on src switch",
                        makeFlowsConflicting: { FlowPayload dominantFlow, FlowPayload flowToConflict ->
                            flowToConflict.source.portNumber = dominantFlow.source.portNumber
                            dominantFlow.source.vlanId = 0
                        },
                        getError            : { FlowPayload flowToError, String operation = "create" ->
                            portError(operation, flowToError.source.portNumber, flowToError.source.datapath,
                                    flowToError.id)
                        }
                ],
                [
                        conflict            : "vlan vs no vlan on the same port on dst switch",
                        makeFlowsConflicting: { FlowPayload dominantFlow, FlowPayload flowToConflict ->
                            flowToConflict.destination.portNumber = dominantFlow.destination.portNumber
                            dominantFlow.destination.vlanId = 0
                        },
                        getError            : { FlowPayload flowToError, String operation = "create" ->
                            portError(operation, flowToError.destination.portNumber, flowToError.destination.datapath,
                                    flowToError.id)
                        }
                ],
                [
                        conflict            : "no vlan, both flows are on the same port on src switch",
                        makeFlowsConflicting: { FlowPayload dominantFlow, FlowPayload flowToConflict ->
                            flowToConflict.source.portNumber = dominantFlow.source.portNumber
                            flowToConflict.source.vlanId = 0
                            dominantFlow.source.vlanId = 0
                        },
                        getError            : { FlowPayload flowToError, String operation = "create" ->
                            portError(operation, flowToError.source.portNumber, flowToError.source.datapath,
                                    flowToError.id)
                        }
                ],
                [
                        conflict            : "no vlan, both flows are on the same port on dst switch",
                        makeFlowsConflicting: { FlowPayload dominantFlow, FlowPayload flowToConflict ->
                            flowToConflict.destination.portNumber = dominantFlow.destination.portNumber
                            flowToConflict.destination.vlanId = 0
                            dominantFlow.destination.vlanId = 0
                        },
                        getError            : { FlowPayload flowToError, String operation = "create" ->
                            portError(operation, flowToError.destination.portNumber, flowToError.destination.datapath,
                                    flowToError.id)
                        }
                ]
        ]
    }
}
