package org.openkilda.functionaltests.spec.flows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.testing.Constants.PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.flow.FlowNotCreatedExpectedError
import org.openkilda.functionaltests.error.flow.FlowNotUpdatedExpectedError
import org.openkilda.functionaltests.extension.tags.IterationTag
import org.openkilda.functionaltests.extension.tags.IterationTags
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowEncapsulationType
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.functionaltests.helpers.model.SwitchRulesFactory
import org.openkilda.functionaltests.model.stats.Direction
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.cookie.Cookie
import org.openkilda.northbound.dto.v1.switches.SwitchPropertiesDto
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.traffexam.TraffExamService

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

import java.time.Instant
import jakarta.inject.Provider

@Narrative("""This spec checks basic functionality(simple flow(rules, ping, traffic, validate), pinned flow,
flow with protected path, default flow) for a flow with VXLAN encapsulation.

NOTE: A flow with the 'VXLAN' encapsulation is supported on a Noviflow switches.
So, flow can be created on a Noviflow(src/dst/transit) switches only.""")

class VxlanFlowSpec extends HealthCheckSpecification {
    static Logger logger = LoggerFactory.getLogger(VxlanFlowSpec.class)

    @Autowired
    @Shared
    Provider<TraffExamService> traffExamProvider
    @Autowired
    @Shared
    FlowFactory flowFactory
    @Autowired
    @Shared
    SwitchRulesFactory switchRulesFactory

    @IterationTags([
            @IterationTag(tags = [SMOKE_SWITCHES], iterationNameRegex = /TRANSIT_VLAN -> VXLAN/)
    ])
    def "System allows to create/update encapsulation type for a flow\
[#data.encapsulationCreate.toString() -> #data.encapsulationUpdate.toString(), #swPair.hwSwString()]"(Map data, SwitchPair swPair) {
        when: "Create a flow with #encapsulationCreate.toString() encapsulation type"
        sleep(10000) //subsequent test fails due to traffexam. Was not able to track down the reason
        def flow = flowFactory.getBuilder(swPair)
                .withEncapsulationType(data.encapsulationCreate)
                .build().create()

        then: "Flow is created with the #encapsulationCreate.toString() encapsulation type"
        flow.encapsulationType == data.encapsulationCreate

        and: "Correct rules are installed"
        def vxlanRule = (flow.encapsulationType == FlowEncapsulationType.VXLAN)
        def flowInfoFromDb = flow.retrieveDetailsFromDB()
        // ingressRule should contain "pushVxlan"
        // egressRule should contain "tunnel-id"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            verifyAll( switchRulesFactory.get(swPair.src.dpId).getRules() ) { rules ->
                rules.find {
                    it.cookie == flowInfoFromDb.forwardPath.cookie.value
                }.instructions.applyActions.pushVxlan as boolean == vxlanRule
                rules.find {
                    it.cookie == flowInfoFromDb.reversePath.cookie.value
                }.match.tunnelId as boolean == vxlanRule
            }

            verifyAll(switchRulesFactory.get(swPair.dst.dpId).getRules()) { rules ->
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
            flow.validateAndCollectDiscrepancies().isEmpty()
        }

        and: "Flow is pingable"
        verifyAll(flow.ping()) {
            forward.pingSuccess
            reverse.pingSuccess
        }

        and: "The flow allows traffic"
        def traffExam = traffExamProvider.get()
        def exam
        if (swPair.isTraffExamCapable()) {
            exam = flow.traffExam(traffExam,50, 5)
            withPool {
                assert [exam.forward, exam.reverse].collectParallel { direction ->
                    def resources = traffExam.startExam(direction)
                    direction.setResources(resources)
                    traffExam.waitExam(direction)
                }.every {
                    it.hasTraffic()
                }, switchRulesFactory.get(swPair.getSrc().getDpId())
            }
        }

        and: "Flow is pingable"
        verifyAll(flow.ping()) {
            forward.pingSuccess
            reverse.pingSuccess
        }

        when: "Try to update the encapsulation type to #encapsulationUpdate.toString()"
        def updateEntity = flow.deepCopy().tap {
            it.encapsulationType = data.encapsulationUpdate
        }
        flow.update(updateEntity)

        then: "The encapsulation type is changed to #encapsulationUpdate.toString()"
        def flowInfo2 = flow.retrieveDetails()
        flowInfo2.encapsulationType == data.encapsulationUpdate

        and: "Flow is valid"
        Wrappers.wait(PATH_INSTALLATION_TIME) {
            flow.validateAndCollectDiscrepancies().isEmpty()
        }

        and: "Flow is pingable (though sometimes we have to wait)"
        Wrappers.wait(WAIT_OFFSET) {
            verifyAll(flow.ping()) {
                forward.pingSuccess
                reverse.pingSuccess
            }
        }

        and: "Rules are recreated"
        def flowInfoFromDb2 = flow.retrieveDetailsFromDB()
        [flowInfoFromDb.forwardPath.cookie.value, flowInfoFromDb.reversePath.cookie.value].sort() !=
                [flowInfoFromDb2.forwardPath.cookie.value, flowInfoFromDb2.reversePath.cookie.value].sort()

        and: "New rules are installed correctly"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            verifyAll(switchRulesFactory.get(swPair.src.dpId).getRules()) { rules ->
                rules.find {
                    it.cookie == flowInfoFromDb2.forwardPath.cookie.value
                }.instructions.applyActions.pushVxlan as boolean == !vxlanRule
                rules.find {
                    it.cookie == flowInfoFromDb2.reversePath.cookie.value
                }.match.tunnelId as boolean == !vxlanRule
            }

            verifyAll(switchRulesFactory.get(swPair.dst.dpId).getRules()) { rules ->
                rules.find {
                    it.cookie == flowInfoFromDb2.forwardPath.cookie.value
                }.match.tunnelId as boolean == !vxlanRule
                rules.find {
                    it.cookie == flowInfoFromDb2.reversePath.cookie.value
                }.instructions.applyActions.pushVxlan as boolean == !vxlanRule
            }
        }

        and: "The flow allows traffic"
        if(exam) {
            withPool {
                assert [exam.forward, exam.reverse].collectParallel { direction ->
                    def resources = traffExam.startExam(direction)
                    direction.setResources(resources)
                    traffExam.waitExam(direction)
                }.every {it.hasTraffic()}, switchRulesFactory.get(swPair.getSrc().getDpId())
            }
        }

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

    def "Able to CRUD a pinned flow with 'VXLAN' encapsulation"() {
        when: "Create a flow"
        def switchPair = switchPairs.all().neighbouring().withBothSwitchesVxLanEnabled().random()
        def flow = flowFactory.getBuilder(switchPair)
                .withEncapsulationType(FlowEncapsulationType.VXLAN)
                .withPinned(true)
                .build().create()

        then: "Flow is created"
        flow.pinned

        when: "Update the flow (pinned=false)"
        def updateEntity = flow.deepCopy().tap {
            it.pinned = false
        }
        flow.update(updateEntity)

        then: "The pinned option is disabled"
        def newFlowInfo = flow.retrieveDetails()
        !newFlowInfo.pinned
        Instant.parse(flow.lastUpdated) < Instant.parse(newFlowInfo.lastUpdated)
        Wrappers.wait(PATH_INSTALLATION_TIME) {
            assert flow.retrieveFlowStatus().status == FlowState.UP
        }
    }

    def "Able to CRUD a vxlan flow with protected path"() {
        given: "Two active VXLAN supported switches with two available path at least"
        def switchPair = switchPairs.all().neighbouring()
                .withBothSwitchesVxLanEnabled()
                .withAtLeastNNonOverlappingPaths(2)
                .random()
        def availablePaths = switchPair.paths.findAll { path ->
            pathHelper.getInvolvedSwitches(path).every { switchHelper.isVxlanEnabled(it.dpId) }
        }
        assumeTrue(availablePaths.size() >= 2, "Unable to find required paths between switches")

        when: "Create a flow with protected path"
        def flow = flowFactory.getBuilder(switchPair)
                .withProtectedPath(true)
                .withEncapsulationType(FlowEncapsulationType.VXLAN)
                .build().create()

        then: "Flow is created with protected path"
        def flowPathInfo = flow.retrieveDetails()
        flowPathInfo.allocateProtectedPath
        flow.retrieveDetails().statusDetails

        and: "Rules for main and protected paths are created"
        Wrappers.wait(WAIT_OFFSET) { flowHelper.verifyRulesOnProtectedFlow(flow.flowId) }

        def flowInfoFromDb = flow.retrieveDetailsFromDB()
        // ingressRule should contain "pushVxlan"
        // egressRule should contain "tunnel-id"
        // protected path creates engressRule
        def protectedForwardCookie = flowInfoFromDb.protectedForwardPath.cookie.value
        def protectedReverseCookie = flowInfoFromDb.protectedReversePath.cookie.value
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            verifyAll(switchRulesFactory.get(switchPair.src.dpId).getRules()) { rules ->
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

            verifyAll(switchRulesFactory.get(switchPair.dst.dpId).getRules()) { rules ->
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
        flow.validateAndCollectDiscrepancies().isEmpty()

        when: "Update flow: disable protected path(allocateProtectedPath=false)"
        def flowData = flow.retrieveDetails()
        def protectedFlowPath = flow.retrieveAllEntityPaths().getPathNodes(Direction.FORWARD, true)
        def updateEntity = flow.deepCopy().tap { it.allocateProtectedPath = false }
        flow.update(updateEntity)

        then: "Protected path is disabled"
        !flow.retrieveAllEntityPaths().flowPath.protectedPath
        !flow.retrieveDetails().statusDetails

        and: "Rules for protected path are deleted"
        Wrappers.wait(RULES_DELETION_TIME) {
            protectedFlowPath.each { sw ->
                def rules = switchRulesFactory.get(sw.switchId).getRules().findAll {
                    !new Cookie(it.cookie).serviceFlag
                }
                assert rules.every { it.cookie != protectedForwardCookie && it.cookie != protectedReverseCookie }
            }
        }

        and: "And rules for main path are recreacted"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            def flowInfoFromDb2 = flow.retrieveDetailsFromDB()
            assert [flowInfoFromDb.forwardPath.cookie.value, flowInfoFromDb.reversePath.cookie.value].sort() !=
                    [flowInfoFromDb2.forwardPath.cookie.value, flowInfoFromDb2.reversePath.cookie.value].sort()

            verifyAll(switchRulesFactory.get(switchPair.src.dpId).getRules()) { rules ->
                rules.find {
                    it.cookie == flowInfoFromDb2.forwardPath.cookie.value
                }.instructions.applyActions.pushVxlan
                rules.find {
                    it.cookie == flowInfoFromDb2.reversePath.cookie.value
                }.match.tunnelId
            }

            verifyAll(switchRulesFactory.get(switchPair.dst.dpId).getRules()) { rules ->
                rules.find {
                    it.cookie == flowInfoFromDb2.forwardPath.cookie.value
                }.match.tunnelId
                rules.find {
                    it.cookie == flowInfoFromDb2.reversePath.cookie.value
                }.instructions.applyActions.pushVxlan
            }
        }

        and: "Validation of flow must be successful"
        flow.validateAndCollectDiscrepancies().isEmpty()
    }

    @Tags([SMOKE_SWITCHES])
    def "System allows tagged traffic via default flow(0<->0) with 'VXLAN' encapsulation"() {
        // we can't test (0<->20, 20<->0) because iperf is not able to establish a connection
        given: "Two active VXLAN supported switches connected to traffgen"
        def switchPair = switchPairs.all().neighbouring()
                .withBothSwitchesVxLanEnabled()
                .withTraffgensOnBothEnds()
                .random()
        when: "Create a default flow"
        def defaultFlow = flowFactory.getBuilder(switchPair)
                .withSourceVlan(0)
                .withDestinationVlan(0)
                .withEncapsulationType(FlowEncapsulationType.VXLAN)
                .build().create()

        def flow = flowFactory.getBuilder(switchPair)
                .withSourceVlan(10)
                .withDestinationVlan(10)
                .build().create()

        then: "System allows tagged traffic on the default flow"
        def traffExam = traffExamProvider.get()
        def exam = flow.traffExam(traffExam, 1000, 5)
        withPool {
            [exam.forward, exam.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        }
    }

    def "Unable to create a VXLAN flow when src and dst switches do not support it"() {
        given: "Src and dst switches do not support VXLAN"
        def switchPair = switchPairs.all().random()
        Map<Switch, SwitchPropertiesDto> initProps = [switchPair.src, switchPair.dst].collectEntries {
            [(it): switchHelper.getCachedSwProps(it.dpId)]
        }
        initProps.each { sw, swProp ->
            switchHelper.updateSwitchProperties(sw, swProp.jacksonCopy().tap {
                it.supportedTransitEncapsulation = [FlowEncapsulationType.TRANSIT_VLAN.toString()]
            })
        }

        when: "Try to create a VXLAN flow"
        def flowEntity = flowFactory.getBuilder(switchPair)
                .withEncapsulationType(FlowEncapsulationType.VXLAN)
                .build()
        flowEntity.create()

        then: "Human readable error is returned"
        def createError = thrown(HttpClientErrorException)
        def actualCreateError= getUnsupportedVxlanErrorDescription(
                "source", switchPair.src.dpId, [FlowEncapsulationType.TRANSIT_VLAN])
        new FlowNotCreatedExpectedError(~/$actualCreateError/).matches(createError)

        when: "Create a VLAN flow"
        flowEntity.tap {
            it.encapsulationType = FlowEncapsulationType.TRANSIT_VLAN
        }
        def flow = flowEntity.create()

        and: "Try updated its encap type to VXLAN"
        def updateFlowEntity = flow.tap {
            it.encapsulationType = FlowEncapsulationType.VXLAN
        }
        flow.update(updateFlowEntity)

        then: "Human readable error is returned"
        def updateError = thrown(HttpClientErrorException)
        def actualUpdateDesc= getUnsupportedVxlanErrorDescription(
                "source", switchPair.src.dpId, [FlowEncapsulationType.TRANSIT_VLAN])
        new FlowNotUpdatedExpectedError(~/$actualUpdateDesc/).matches(updateError)
    }

    @Tags(TOPOLOGY_DEPENDENT)
    def "System selects longer path if shorter path does not support required encapsulation type"() {
        given: "Shortest path transit switch does not support VXLAN and alt paths with VXLAN are available"
        List<PathNode> noVxlanPath
        Switch noVxlanSw
        def switchPair = switchPairs.all().getSwitchPairs().find {
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
        def flow = flowFactory.getBuilder(switchPair)
                .withEncapsulationType(FlowEncapsulationType.VXLAN)
                .build().create()

        then: "Flow is built through vxlan-enabled path, even though it is not the shortest"
        flow.retrieveAllEntityPaths().getPathNodes() != noVxlanPath
    }

    @Tags([LOW_PRIORITY, TOPOLOGY_DEPENDENT])
    def "Unable to create a vxlan flow when dst switch does not support it"() {
        given: "VXLAN supported and not supported switches"
        def switchPair = switchPairs.all().neighbouring().withBothSwitchesVxLanEnabled().random()
        def originDstSwProps = switchHelper.getCachedSwProps(switchPair.dst.dpId)
        switchHelper.updateSwitchProperties(switchPair.dst, originDstSwProps.jacksonCopy().tap {
            it.supportedTransitEncapsulation = [FlowEncapsulationType.TRANSIT_VLAN.toString()]
        })
        def dstSupportedEncapsulationTypes = northbound.getSwitchProperties(switchPair.dst.dpId)
                .supportedTransitEncapsulation.collect { it.toUpperCase() }

        when: "Try to create a flow"
        def flowEntity = flowFactory.getBuilder(switchPair)
                .withEncapsulationType(FlowEncapsulationType.VXLAN)
                .build()
        flowEntity.create()

        then: "Human readable error is returned"
        def err = thrown(HttpClientErrorException)
        def actualErrDesc= getUnsupportedVxlanErrorDescription("destination", switchPair.dst.dpId,
                dstSupportedEncapsulationTypes)
        new FlowNotCreatedExpectedError(~/$actualErrDesc/).matches(err)
    }

    def "System allows to create/update encapsulation type for a one-switch flow\
(#encapsulationCreate.toString() -> #encapsulationUpdate.toString())"() {
        when: "Try to create a one-switch flow"
        def sw = topology.activeSwitches.find { switchHelper.isVxlanEnabled(it.dpId) }
        assumeTrue(sw as boolean, "Require at least 1 VXLAN supported switch")
        def flow = flowFactory.getBuilder(sw, sw)
                .withEncapsulationType(encapsulationCreate)
                .build().create()

        then: "Flow is created with the #encapsulationCreate.toString() encapsulation type"
        def flowInfo1 = flow.retrieveDetails()
        flowInfo1.encapsulationType == encapsulationCreate

        and: "Correct rules are installed"
        def flowInfoFromDb = flow.retrieveDetailsFromDB()
        // vxlan rules are not creating for a one-switch flow
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            verifyAll(switchRulesFactory.get(sw.dpId).getRules()) { rules ->
                !rules.find {
                    it.cookie == flowInfoFromDb.forwardPath.cookie.value
                }.instructions.applyActions.pushVxlan
                !rules.find {
                    it.cookie == flowInfoFromDb.reversePath.cookie.value
                }.match.tunnelId
            }
        }

        and: "Flow is valid"
        flow.validateAndCollectDiscrepancies().isEmpty()

        and: "Unable to ping a one-switch vxlan flow"
        verifyAll(flow.ping()) {
            !forward
            !reverse
            error == "Flow ${flow.flowId} should not be one-switch flow"
        }

        when: "Try to update the encapsulation type to #encapsulationUpdate.toString()"
        def updateEntity = flowInfo1.tap {
            it.encapsulationType = encapsulationUpdate
        }
        flow.update(updateEntity)

        then: "The encapsulation type is changed to #encapsulationUpdate.toString()"
        def flowInfo2 = flow.retrieveDetails()
        flowInfo2.encapsulationType == encapsulationUpdate

        and: "Flow is valid"
        Wrappers.wait(PATH_INSTALLATION_TIME) {
            flow.validateAndCollectDiscrepancies().isEmpty()
        }

        and: "Rules are recreated"
        def flowInfoFromDb2 = flow.retrieveDetailsFromDB()
        [flowInfoFromDb.forwardPath.cookie.value, flowInfoFromDb.reversePath.cookie.value].sort() !=
                [flowInfoFromDb2.forwardPath.cookie.value, flowInfoFromDb2.reversePath.cookie.value].sort()

        and: "New rules are installed correctly"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            verifyAll(switchRulesFactory.get(sw.dpId).getRules()) { rules ->
                !rules.find {
                    it.cookie == flowInfoFromDb2.forwardPath.cookie.value
                }.instructions.applyActions.pushVxlan
                !rules.find {
                    it.cookie == flowInfoFromDb2.reversePath.cookie.value
                }.match.tunnelId
            }
        }

        where:
        encapsulationCreate                | encapsulationUpdate
        FlowEncapsulationType.TRANSIT_VLAN | FlowEncapsulationType.VXLAN
        FlowEncapsulationType.VXLAN        | FlowEncapsulationType.TRANSIT_VLAN

    }

    /**
     * Get minimum amount of switchPairs that will use every unique legal switch as src or dst at least once
     */
    List<SwitchPair> getUniqueVxlanSwitchPairs() {
        def vxlanSwitchPairs = switchPairs.all().withBothSwitchesVxLanEnabled().getSwitchPairs()
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
        String supportedEncTypes = supportedEncapsulationTypes.collect { it.toString().toUpperCase() }
                .toString().replace('[', '\\[').replace(']', '\\]')
        String vxlan = FlowEncapsulationType.VXLAN.toString().toUpperCase()
        return "Flow's $endpointName endpoint $dpId doesn't support requested encapsulation type " +
                "$vxlan. Choose one of the supported encapsulation types " +
                "$supportedEncTypes or update switch properties and add needed encapsulation type."
    }
}
