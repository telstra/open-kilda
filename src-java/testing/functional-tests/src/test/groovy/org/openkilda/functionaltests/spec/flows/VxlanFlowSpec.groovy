package org.openkilda.functionaltests.spec.flows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeFalse
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.testing.Constants.PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.IterationTag
import org.openkilda.functionaltests.extension.tags.IterationTags
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.SwitchHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.DetectConnectedDevicesPayload
import org.openkilda.messaging.payload.flow.FlowEndpointPayload
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.model.cookie.Cookie
import org.openkilda.northbound.dto.v1.flows.PingInput
import org.openkilda.northbound.dto.v1.switches.SwitchPropertiesDto
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.traffexam.FlowNotApplicableException
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.tools.FlowTrafficExamBuilder

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative

import java.time.Instant
import javax.inject.Provider

@Narrative("""This spec checks basic functionality(simple flow(rules, ping, traffic, validate), pinned flow,
flow with protected path, default flow) for a flow with VXLAN encapsulation.

NOTE: A flow with the 'VXLAN' encapsulation is supported on a Noviflow switches.
So, flow can be created on a Noviflow(src/dst/transit) switches only.""")
class VxlanFlowSpec extends HealthCheckSpecification {
    static Logger logger = LoggerFactory.getLogger(VxlanFlowSpec.class)

    @Autowired
    Provider<TraffExamService> traffExamProvider

    @Tidy
    @IterationTags([
            @IterationTag(tags = [SMOKE_SWITCHES], iterationNameRegex = /TRANSIT_VLAN -> VXLAN/)
    ])
    def "System allows to create/update encapsulation type for a flow\
[#data.encapsulationCreate.toString() -> #data.encapsulationUpdate.toString(), #swPair.hwSwString()]"(Map data, SwitchPair swPair) {
        when: "Create a flow with #encapsulationCreate.toString() encapsulation type"
        def flow = flowHelperV2.randomFlow(swPair)
        flow.encapsulationType = data.encapsulationCreate
        flowHelperV2.addFlow(flow)

        then: "Flow is created with the #encapsulationCreate.toString() encapsulation type"
        def flowInfo = northboundV2.getFlow(flow.flowId)
        flowInfo.encapsulationType == data.encapsulationCreate.toString().toLowerCase()

        and: "Correct rules are installed"
        def vxlanRule = (flowInfo.encapsulationType == FlowEncapsulationType.VXLAN.toString().toLowerCase())
        def flowInfoFromDb = database.getFlow(flow.flowId)
        // ingressRule should contain "pushVxlan"
        // egressRule should contain "tunnel-id"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            verifyAll(northbound.getSwitchRules(swPair.src.dpId).flowEntries) { rules ->
                rules.find {
                    it.cookie == flowInfoFromDb.forwardPath.cookie.value
                }.instructions.applyActions.pushVxlan as boolean == vxlanRule
                rules.find {
                    it.cookie == flowInfoFromDb.reversePath.cookie.value
                }.match.tunnelId as boolean == vxlanRule
            }

            verifyAll(northbound.getSwitchRules(swPair.dst.dpId).flowEntries) { rules ->
                rules.find {
                    it.cookie == flowInfoFromDb.forwardPath.cookie.value
                }.match.tunnelId as boolean == vxlanRule
                rules.find {
                    it.cookie == flowInfoFromDb.reversePath.cookie.value
                }.instructions.applyActions.pushVxlan as boolean == vxlanRule
            }
        }

        and: "Flow is valid"
        Wrappers.wait(PATH_INSTALLATION_TIME) {
            northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }
        }

        //todo remove in case no traffic on jenkins
        and: "Flow is pingable"
        verifyAll(northbound.pingFlow(flow.flowId, new PingInput())) {
            forward.pingSuccess
            reverse.pingSuccess
        }

        and: "The flow allows traffic"
        def traffExam = traffExamProvider.get()
        def exam
        if (swPair.isTraffExamCapable()) {
            exam = new FlowTrafficExamBuilder(topology, traffExam).buildBidirectionalExam(toFlowPayload(flow), 1000, 5)
            withPool {
                [exam.forward, exam.reverse].eachParallel { direction ->
                    def resources = traffExam.startExam(direction)
                    direction.setResources(resources)
                    assert traffExam.waitExam(direction).hasTraffic()
                }
            }
        }

        and: "Flow is pingable"
        verifyAll(northbound.pingFlow(flow.flowId, new PingInput())) {
            forward.pingSuccess
            reverse.pingSuccess
        }

        when: "Try to update the encapsulation type to #encapsulationUpdate.toString()"
        northboundV2.updateFlow(flowInfo.flowId,
                flowHelperV2.toRequest(flowInfo.tap { it.encapsulationType = data.encapsulationUpdate }))

        then: "The encapsulation type is changed to #encapsulationUpdate.toString()"
        def flowInfo2 = northboundV2.getFlow(flow.flowId)
        flowInfo2.encapsulationType == data.encapsulationUpdate.toString().toLowerCase()

        and: "Flow is valid"
        Wrappers.wait(PATH_INSTALLATION_TIME) {
            northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }
        }

        and: "The flow allows traffic"
        if(exam) {
            withPool {
                [exam.forward, exam.reverse].eachParallel { direction ->
                    def resources = traffExam.startExam(direction)
                    direction.setResources(resources)
                    assert traffExam.waitExam(direction).hasTraffic()
                }
            }
        }

        and: "Flow is pingable"
        verifyAll(northbound.pingFlow(flow.flowId, new PingInput())) {
            forward.pingSuccess
            reverse.pingSuccess
        }

        and: "Rules are recreated"
        def flowInfoFromDb2 = database.getFlow(flow.flowId)
        [flowInfoFromDb.forwardPath.cookie.value, flowInfoFromDb.reversePath.cookie.value].sort() !=
                [flowInfoFromDb2.forwardPath.cookie.value, flowInfoFromDb2.reversePath.cookie.value].sort()

        and: "New rules are installed correctly"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            verifyAll(northbound.getSwitchRules(swPair.src.dpId).flowEntries) { rules ->
                rules.find {
                    it.cookie == flowInfoFromDb2.forwardPath.cookie.value
                }.instructions.applyActions.pushVxlan as boolean == !vxlanRule
                rules.find {
                    it.cookie == flowInfoFromDb2.reversePath.cookie.value
                }.match.tunnelId as boolean == !vxlanRule
            }

            verifyAll(northbound.getSwitchRules(swPair.dst.dpId).flowEntries) { rules ->
                rules.find {
                    it.cookie == flowInfoFromDb2.forwardPath.cookie.value
                }.match.tunnelId as boolean == !vxlanRule
                rules.find {
                    it.cookie == flowInfoFromDb2.reversePath.cookie.value
                }.instructions.applyActions.pushVxlan as boolean == !vxlanRule
            }
        }

        cleanup: "Delete the flow"
        flow && flowHelperV2.deleteFlow(flow.flowId)
        sleep(7000) //subsequent test fails due to traffexam. Was not able to track down the reason

        where:
        [data, swPair] << ([
                [
                        [
                                encapsulationCreate: FlowEncapsulationType.TRANSIT_VLAN,
                                encapsulationUpdate: FlowEncapsulationType.VXLAN
                        ],
                        [
                                encapsulationCreate: FlowEncapsulationType.VXLAN,
                                encapsulationUpdate: FlowEncapsulationType.TRANSIT_VLAN
                        ]
                ], getUniqueVxlanSwitchPairs()
        ].combinations() ?: assumeTrue(false, "Not enough VXLAN-enabled switches in topology"))
    }

    @Tidy
    def "Able to CRUD a pinned flow with 'VXLAN' encapsulation"() {
        when: "Create a flow"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { swP ->
            [swP.src, swP.dst].every { sw -> switchHelper.isVxlanEnabled(sw.dpId) }
        }
        assumeTrue(switchPair as boolean, "Unable to find required switches in topology")

        def flow = flowHelperV2.randomFlow(switchPair)
        flow.encapsulationType = FlowEncapsulationType.VXLAN
        flow.pinned = true
        flowHelperV2.addFlow(flow)

        then: "Flow is created"
        def flowInfo = northboundV2.getFlow(flow.flowId)
        flowInfo.pinned

        when: "Update the flow (pinned=false)"
        northboundV2.updateFlow(flowInfo.flowId, flowHelperV2.toRequest(flowInfo.tap { it.pinned = false }))

        then: "The pinned option is disabled"
        def newFlowInfo = northboundV2.getFlow(flow.flowId)
        !newFlowInfo.pinned
        Instant.parse(flowInfo.lastUpdated) < Instant.parse(newFlowInfo.lastUpdated)
        Wrappers.wait(PATH_INSTALLATION_TIME) {
            assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP
        }

        cleanup: "Delete the flow"
        flow && flowHelperV2.deleteFlow(flow.flowId)
    }

    @Tidy
    def "Able to CRUD a vxlan flow with protected path"() {
        given: "Two active VXLAN supported switches with two available path at least"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { swP ->
            [swP.src, swP.dst].every { sw -> switchHelper.isVxlanEnabled(sw.dpId) } && swP.paths.unique(false) {
                a, b -> a.intersect(b) == [] ? 1 : 0
            }.size() >= 2
        } ?: assumeTrue(false, "No suiting switches found")

        def availablePaths = switchPair.paths.findAll { path ->
            pathHelper.getInvolvedSwitches(path).every { switchHelper.isVxlanEnabled(it.dpId) }
        }
        assumeTrue(availablePaths.size() >= 2, "Unable to find required paths between switches")

        when: "Create a flow with protected path"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.allocateProtectedPath = true
        flow.encapsulationType = FlowEncapsulationType.VXLAN
        flowHelperV2.addFlow(flow)

        then: "Flow is created with protected path"
        def flowPathInfo = northbound.getFlowPath(flow.flowId)
        flowPathInfo.protectedPath
        northboundV2.getFlow(flow.flowId).statusDetails

        and: "Rules for main and protected paths are created"
        Wrappers.wait(WAIT_OFFSET) { flowHelper.verifyRulesOnProtectedFlow(flow.flowId) }

        def flowInfoFromDb = database.getFlow(flow.flowId)
        // ingressRule should contain "pushVxlan"
        // egressRule should contain "tunnel-id"
        // protected path creates engressRule
        def protectedForwardCookie = flowInfoFromDb.protectedForwardPath.cookie.value
        def protectedReverseCookie = flowInfoFromDb.protectedReversePath.cookie.value
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            verifyAll(northbound.getSwitchRules(switchPair.src.dpId).flowEntries) { rules ->
                rules.find {
                    it.cookie == flowInfoFromDb.forwardPath.cookie.value
                }.instructions.applyActions.pushVxlan
                rules.find {
                    it.cookie == flowInfoFromDb.reversePath.cookie.value
                }.match.tunnelId
                rules.find {
                    it.cookie == flowInfoFromDb.protectedReversePath.cookie.value
                }.match.tunnelId
            }

            verifyAll(northbound.getSwitchRules(switchPair.dst.dpId).flowEntries) { rules ->
                rules.find {
                    it.cookie == flowInfoFromDb.forwardPath.cookie.value
                }.match.tunnelId
                rules.find {
                    it.cookie == flowInfoFromDb.reversePath.cookie.value
                }.instructions.applyActions.pushVxlan
                rules.find {
                    it.cookie == flowInfoFromDb.protectedForwardPath.cookie.value
                }.match.tunnelId
            }
        }

        and: "Validation of flow must be successful"
        northbound.validateFlow(flow.flowId).each { direction ->
            assert direction.discrepancies.empty
        }

        when: "Update flow: disable protected path(allocateProtectedPath=false)"
        def flowData = northboundV2.getFlow(flow.flowId)
        def protectedFlowPath = northbound.getFlowPath(flow.flowId).protectedPath.forwardPath
        flowHelperV2.updateFlow(flowData.flowId, flowHelperV2.toRequest(flowData.tap { it.allocateProtectedPath = false }))

        then: "Protected path is disabled"
        !northbound.getFlowPath(flow.flowId).protectedPath
        !northboundV2.getFlow(flow.flowId).statusDetails

        and: "Rules for protected path are deleted"
        Wrappers.wait(RULES_DELETION_TIME) {
            protectedFlowPath.each { sw ->
                def rules = northbound.getSwitchRules(sw.switchId).flowEntries.findAll {
                    !new Cookie(it.cookie).serviceFlag
                }
                assert rules.every { it != protectedForwardCookie && it != protectedReverseCookie }
            }
        }

        and: "And rules for main path are recreacted"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            def flowInfoFromDb2 = database.getFlow(flow.flowId)
            assert [flowInfoFromDb.forwardPath.cookie.value, flowInfoFromDb.reversePath.cookie.value].sort() !=
                    [flowInfoFromDb2.forwardPath.cookie.value, flowInfoFromDb2.reversePath.cookie.value].sort()

            verifyAll(northbound.getSwitchRules(switchPair.src.dpId).flowEntries) { rules ->
                rules.find {
                    it.cookie == flowInfoFromDb2.forwardPath.cookie.value
                }.instructions.applyActions.pushVxlan
                rules.find {
                    it.cookie == flowInfoFromDb2.reversePath.cookie.value
                }.match.tunnelId
            }

            verifyAll(northbound.getSwitchRules(switchPair.dst.dpId).flowEntries) { rules ->
                rules.find {
                    it.cookie == flowInfoFromDb2.forwardPath.cookie.value
                }.match.tunnelId
                rules.find {
                    it.cookie == flowInfoFromDb2.reversePath.cookie.value
                }.instructions.applyActions.pushVxlan
            }
        }

        and: "Validation of flow must be successful"
        northbound.validateFlow(flow.flowId).each { direction ->
            assert direction.discrepancies.empty
        }

        cleanup: "Delete the flow and reset costs"
        flow && flowHelperV2.deleteFlow(flow.flowId)
    }

    @Tidy
    @Tags([SMOKE_SWITCHES])
    def "System allows tagged traffic via default flow(0<->0) with 'VXLAN' encapsulation"() {
        // we can't test (0<->20, 20<->0) because iperf is not able to establish a connection
        given: "Two active VXLAN supported switches connected to traffgen"
        def allTraffgenSwitchIds = topology.activeTraffGens*.switchConnected.findAll {
            switchHelper.isVxlanEnabled(it.dpId)
        }*.dpId ?: assumeTrue(false,
"Should be at least two active traffgens connected to VXLAN supported switches")
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find {
            allTraffgenSwitchIds.contains(it.src.dpId) && allTraffgenSwitchIds.contains(it.dst.dpId)
        } ?: assumeTrue(false, "Unable to find required switches in topology")

        when: "Create a default flow"
        def defaultFlow = flowHelperV2.randomFlow(switchPair)
        defaultFlow.source.vlanId = 0
        defaultFlow.destination.vlanId = 0
        defaultFlow.encapsulationType = FlowEncapsulationType.VXLAN
        flowHelperV2.addFlow(defaultFlow)

        def flow = flowHelperV2.randomFlow(switchPair)
        flow.source.vlanId = 10
        flow.destination.vlanId = 10

        then: "System allows tagged traffic on the default flow"
        def traffExam = traffExamProvider.get()
        def exam = new FlowTrafficExamBuilder(topology, traffExam).buildBidirectionalExam(toFlowPayload(flow), 1000, 5)
        withPool {
            [exam.forward, exam.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        }

        cleanup: "Delete the flow"
        flow && flowHelperV2.deleteFlow(defaultFlow.flowId)
    }

    @Tidy
    def "Unable to create a VXLAN flow when src and dst switches do not support it"() {
        given: "Src and dst switches do not support VXLAN"
        def switchPair = topologyHelper.switchPairs.first()
        Map<Switch, SwitchPropertiesDto> initProps = [switchPair.src, switchPair.dst].collectEntries {
            [(it): switchHelper.getCachedSwProps(it.dpId)]
        }
        initProps.each { sw, swProp ->
            SwitchHelper.updateSwitchProperties(sw, swProp.jacksonCopy().tap {
                it.supportedTransitEncapsulation = [FlowEncapsulationType.TRANSIT_VLAN.toString()]
            })
        }

        when: "Try to create a VXLAN flow"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.encapsulationType = FlowEncapsulationType.VXLAN.toString()
        def addedFlow = northboundV2.addFlow(flow)

        then: "Human readable error is returned"
        def createError = thrown(HttpClientErrorException)
        createError.rawStatusCode == 400
        def createErrorDetails = createError.responseBodyAsString.to(MessageError)
        createErrorDetails.errorMessage == "Could not create flow"
        createErrorDetails.errorDescription == getUnsupportedVxlanErrorDescription("source", switchPair.src.dpId,
                [FlowEncapsulationType.TRANSIT_VLAN])

        when: "Create a VLAN flow"
        flow.encapsulationType = FlowEncapsulationType.TRANSIT_VLAN.toString()
        addedFlow = flowHelperV2.addFlow(flow)

        and: "Try updated its encap type to VXLAN"
        northboundV2.updateFlow(flow.flowId, flow.tap { it.encapsulationType = FlowEncapsulationType.VXLAN.toString() })

        then: "Human readable error is returned"
        def updateError = thrown(HttpClientErrorException)
        updateError.rawStatusCode == 400
        def updateErrorDetails = updateError.responseBodyAsString.to(MessageError)
        updateErrorDetails.errorMessage == "Could not update flow"
        createErrorDetails.errorDescription == getUnsupportedVxlanErrorDescription("source", switchPair.src.dpId,
                [FlowEncapsulationType.TRANSIT_VLAN])


        cleanup:
        addedFlow && flowHelperV2.deleteFlow(addedFlow.flowId)
        initProps.each { sw, swProps ->
            SwitchHelper.updateSwitchProperties(sw, swProps)
        }
    }

    @Tidy
    @Tags(TOPOLOGY_DEPENDENT)
    def "System selects longer path if shorter path does not support required encapsulation type"() {
        given: "Shortest path transit switch does not support VXLAN and alt paths with VXLAN are available"
        List<PathNode> noVxlanPath
        Switch noVxlanSw
        def switchPair = topologyHelper.switchPairs.find {
            noVxlanPath = it.paths.find {
                def involvedSwitches = pathHelper.getInvolvedSwitches(it)
                noVxlanSw = involvedSwitches[1]
                involvedSwitches.size() == 3 && involvedSwitches[0,-1].every {switchHelper.isVxlanEnabled(it.dpId) }
            }
            List<PathNode> vxlanPath = it.paths.find {
                def involvedSwitches = pathHelper.getInvolvedSwitches(it)
                it != noVxlanPath && involvedSwitches.size() >= 3 && !involvedSwitches[1..-2].contains(noVxlanSw) &&
                        involvedSwitches[1..-2].every {switchHelper.isVxlanEnabled(it.dpId) }
            }
            noVxlanPath && vxlanPath
        }

        assumeTrue(switchPair as boolean, "Wasn't able to find suitable switches")
        //make a no-vxlan path to be the most preferred
        switchPair.paths.findAll { it != noVxlanPath }.each { pathHelper.makePathMorePreferable(noVxlanPath, it) }
        def initNoVxlanSwProps
        def isVxlanEnabledOnNoVxlanSw = switchHelper.isVxlanEnabled(noVxlanSw.dpId)
        if (isVxlanEnabledOnNoVxlanSw) {
            initNoVxlanSwProps = switchHelper.getCachedSwProps(noVxlanSw.dpId)
            switchHelper.updateSwitchProperties(noVxlanSw, initNoVxlanSwProps.jacksonCopy().tap {
                it.supportedTransitEncapsulation = [FlowEncapsulationType.TRANSIT_VLAN.toString()]
            })
        }

        when: "Create a VXLAN flow"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.encapsulationType = FlowEncapsulationType.VXLAN
        northboundV2.addFlow(flow)

        then: "Flow is built through vxlan-enabled path, even though it is not the shortest"
        pathHelper.convert(northbound.getFlowPath(flow.flowId)) != noVxlanPath

        cleanup: "Restore all the changed sw props and remove the flow"
        flow && flowHelperV2.deleteFlow(flow.flowId)
        isVxlanEnabledOnNoVxlanSw && initNoVxlanSwProps && switchHelper.updateSwitchProperties(noVxlanSw, initNoVxlanSwProps)
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))
    }

    @Tidy
    @Tags([LOW_PRIORITY, TOPOLOGY_DEPENDENT])
    def "Unable to create a vxlan flow when dst switch does not support it"() {
        given: "VXLAN supported and not supported switches"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find {
            switchHelper.isVxlanEnabled(it.src.dpId)
        }
        assumeTrue(switchPair as boolean, "Unable to find required switches in topology")
        def isVxlanEnabledOnDstSw = switchHelper.isVxlanEnabled(switchPair.dst.dpId)
        def originDstSwProps
        if (isVxlanEnabledOnDstSw) {
            originDstSwProps = switchHelper.getCachedSwProps(switchPair.dst.dpId)
            switchHelper.updateSwitchProperties(switchPair.dst, originDstSwProps.jacksonCopy().tap {
                it.supportedTransitEncapsulation = [FlowEncapsulationType.TRANSIT_VLAN.toString()]
            })
        }
        def dstSupportedEncapsulationTypes = northbound.getSwitchProperties(switchPair.dst.dpId)
                .supportedTransitEncapsulation.collect { it.toUpperCase() }

        when: "Try to create a flow"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.encapsulationType = FlowEncapsulationType.VXLAN
        northboundV2.addFlow(flow)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        def errorDetails = exc.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Could not create flow"
        errorDetails.errorDescription == getUnsupportedVxlanErrorDescription("destination", switchPair.dst.dpId,
                dstSupportedEncapsulationTypes)

        cleanup:
        !exc && flowHelperV2.deleteFlow(flow.flowId)
        isVxlanEnabledOnDstSw && switchHelper.updateSwitchProperties(switchPair.dst, originDstSwProps)
    }

    @Tidy
    def "System allows to create/update encapsulation type for a one-switch flow\
(#encapsulationCreate.toString() -> #encapsulationUpdate.toString())"() {
        when: "Try to create a one-switch flow"
        def sw = topology.activeSwitches.find { switchHelper.isVxlanEnabled(it.dpId) }
        assumeTrue(sw as boolean, "Require at least 1 VXLAN supported switch")
        def flow = flowHelperV2.singleSwitchFlow(sw)
        flow.encapsulationType = encapsulationCreate
        northboundV2.addFlow(flow)

        then: "Flow is created with the #encapsulationCreate.toString() encapsulation type"
        def flowInfo1 = northboundV2.getFlow(flow.flowId)
        flowInfo1.encapsulationType == encapsulationCreate.toString().toLowerCase()

        and: "Correct rules are installed"
        def flowInfoFromDb = database.getFlow(flow.flowId)
        // vxlan rules are not creating for a one-switch flow
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            verifyAll(northbound.getSwitchRules(sw.dpId).flowEntries) { rules ->
                !rules.find {
                    it.cookie == flowInfoFromDb.forwardPath.cookie.value
                }.instructions.applyActions.pushVxlan
                !rules.find {
                    it.cookie == flowInfoFromDb.reversePath.cookie.value
                }.match.tunnelId
            }
        }

        and: "Flow is valid"
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }

        and: "Unable to ping a one-switch vxlan flow"
        verifyAll(northbound.pingFlow(flow.flowId, new PingInput())) {
            !forward
            !reverse
            error == "Flow ${flow.flowId} should not be one-switch flow"
        }

        when: "Try to update the encapsulation type to #encapsulationUpdate.toString()"
        northboundV2.updateFlow(flowInfo1.flowId,
                flowHelperV2.toRequest(flowInfo1.tap { it.encapsulationType = encapsulationUpdate }))

        then: "The encapsulation type is changed to #encapsulationUpdate.toString()"
        def flowInfo2 = northboundV2.getFlow(flow.flowId)
        flowInfo2.encapsulationType == encapsulationUpdate.toString().toLowerCase()

        and: "Flow is valid"
        Wrappers.wait(PATH_INSTALLATION_TIME) {
            northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }
        }

        and: "Rules are recreated"
        def flowInfoFromDb2 = database.getFlow(flow.flowId)
        [flowInfoFromDb.forwardPath.cookie.value, flowInfoFromDb.reversePath.cookie.value].sort() !=
                [flowInfoFromDb2.forwardPath.cookie.value, flowInfoFromDb2.reversePath.cookie.value].sort()

        and: "New rules are installed correctly"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            verifyAll(northbound.getSwitchRules(sw.dpId).flowEntries) { rules ->
                !rules.find {
                    it.cookie == flowInfoFromDb2.forwardPath.cookie.value
                }.instructions.applyActions.pushVxlan
                !rules.find {
                    it.cookie == flowInfoFromDb2.reversePath.cookie.value
                }.match.tunnelId
            }
        }

        cleanup: "Delete the flow"
        flow && flowHelperV2.deleteFlow(flow.flowId)

        where:
        encapsulationCreate                | encapsulationUpdate
        FlowEncapsulationType.TRANSIT_VLAN | FlowEncapsulationType.VXLAN
        FlowEncapsulationType.VXLAN        | FlowEncapsulationType.TRANSIT_VLAN

    }

    FlowPayload toFlowPayload(FlowRequestV2 flow) {
        FlowEndpointV2 source = flow.source
        FlowEndpointV2 destination = flow.destination

        FlowPayload.builder()
                   .id(flow.flowId)
                   .source(new FlowEndpointPayload(source.switchId, source.portNumber, source.vlanId,
                new DetectConnectedDevicesPayload(false, false)))
                   .destination(new FlowEndpointPayload(destination.switchId, destination.portNumber, destination.vlanId,
                new DetectConnectedDevicesPayload(false, false)))
                   .maximumBandwidth(flow.maximumBandwidth)
                   .ignoreBandwidth(flow.ignoreBandwidth)
                   .build()
    }

    /**
     * Get minimum amount of switchPairs that will use every unique legal switch as src or dst at least once
     */
    List<SwitchPair> getUniqueVxlanSwitchPairs() {
        def vxlanEnabledSwitches = topology.activeSwitches.findAll { switchHelper.isVxlanEnabled(it.dpId) }
        def vxlanSwitchPairs = topologyHelper.getSwitchPairs().findAll { swPair ->
            swPair.paths.find { pathHelper.getInvolvedSwitches(it).every { it in vxlanEnabledSwitches } }
        }
        def switchesToPick = vxlanSwitchPairs.collectMany { [it.src, it.dst] }
                                             .unique { it.nbFormat().hardware + it.nbFormat().software }
        return vxlanSwitchPairs.inject([]) { r, switchPair ->
            if (switchPair.src in switchesToPick || switchPair.dst in switchesToPick ) {
                r << switchPair
                switchesToPick.remove(switchPair.src)
                switchesToPick.remove(switchPair.dst)
            }
            r
        } as List<SwitchPair>
    }

    def getUnsupportedVxlanErrorDescription(endpointName, dpId, supportedEncapsulationTypes) {
        return "Flow's $endpointName endpoint $dpId doesn't support requested encapsulation type " +
                "$FlowEncapsulationType.VXLAN. Choose one of the supported encapsulation types " +
                "$supportedEncapsulationTypes or update switch properties and add needed encapsulation type."
    }
}
