package org.openkilda.functionaltests.spec.switches

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.helpers.SwitchHelper.randomVlan
import static org.openkilda.functionaltests.model.cleanup.CleanupAfter.CLASS
import static org.openkilda.messaging.payload.flow.FlowState.UP

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.functionaltests.helpers.model.YFlowExtended
import org.openkilda.functionaltests.helpers.factory.YFlowFactory
import org.openkilda.model.FlowPathDirection
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowExtended

import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Shared

@Narrative("Verifies feature to retrieve list of flows passing the switch grouped by port number. Details: #5015")

class SwitchesFlowsV2Spec extends HealthCheckSpecification {
    @Shared
    YFlowExtended yFlow
    @Shared
    String yFlowSubFlow1Id
    @Shared
    String yFlowSubFlow2Id
    @Shared
    FlowExtended flow
    @Shared
    String flowId
    @Shared
    SwitchTriplet switchTriplet
    @Shared
    SwitchPair switchPair
    @Shared
    @Autowired
    YFlowFactory yFlowFactory
    @Shared
    @Autowired
    FlowFactory flowFactory
    @Shared
    Switch switchFlowGoesThrough
    @Shared
    Switch switchProtectedPathGoesThrough

    def setupSpec() {
        /* Topology used to test features in this spec looks like this:
          2 subflows of Y-flow                                      subflow 1 + usual flow use the same src and dst
                    🡾                                                   🡾
        (Shared SW)☴☴☴(switch flow goes through)☴☴☴☴☴☴☴☴☴☴☴☴=============(Endpoint1 SW)
                    🡽  \-----(SW protected path goes through)---/   \------------(Endpoint2 SW)
            usual flow                  🡹                                   🡹
                                   usual flow protected path           subflow 2 ends on the switch that is not used by other flows
         */
        switchTriplet = switchTriplets.all(true, false).nonNeighbouring().getSwitchTriplets().find {
            def yPoints = topologyHelper.findPotentialYPoints(it)
            yPoints[0] != it.shared
        }
        assumeTrue(switchTriplet != null, "Couldn't find appropriate switch triplet")

        yFlow = yFlowFactory.getRandom(switchTriplet, false, [], CLASS)
        def yFlowPath = yFlow.retrieveAllEntityPaths()
        def subFlow1Path =  yFlowPath.subFlowPaths.first()
        def subFlow2Path =  yFlowPath.subFlowPaths.last()
        // sub-flow1 shares switches with regular flow
        yFlowSubFlow1Id = subFlow1Path.path.forward.getInvolvedSwitches().size() >  subFlow2Path.path.forward.getInvolvedSwitches().size() ?
                subFlow2Path.flowId : subFlow1Path.flowId

        //sub-flow2 ends on the switch that is not used by the sub-flow1 and regular flow
        yFlowSubFlow2Id = yFlow.subFlows.find { it.flowId != yFlowSubFlow1Id }.flowId
        def flowDstSwitch = topology.activeSwitches.find {
            it.dpId == yFlow.subFlows.find { it.flowId == yFlowSubFlow1Id }.endpoint.switchId
        }

        switchPair = switchPairs.all().specificPair(switchTriplet.shared, flowDstSwitch)
        flow = flowFactory.getBuilder(switchPair, false)
                .withProtectedPath(true)
                .build().create(UP, CLASS)
        flowId = flow.flowId
        def flowPathInfo = flow.retrieveAllEntityPaths()
        switchFlowGoesThrough =  topology.activeSwitches.find { it.dpId == flowPathInfo.flowPath.path.forward.transitInvolvedSwitches.first() }
        switchProtectedPathGoesThrough =  topology.activeSwitches.find { it.dpId == flowPathInfo.flowPath.protectedPath.forward.transitInvolvedSwitches.first() }
    }

    @Tags([SMOKE])
    def "System allows to get flows on particular ports on switch(several flows on the same port)"() {
        given: "Y-Flow subflow which starts on switch"
        and: "List of the ports that subflow uses on switch, received from flow path"
        def usedPortsList = yFlow.retrieveAllEntityPaths().subFlowPaths.find { it.flowId == yFlowSubFlow2Id }
                .collect {
                    (it.path.forward.getNodes().nodes + it?.protectedPath?.forward?.getNodes()?.nodes)
                            .findAll { it?.switchId == switchTriplet.shared.dpId }.portNo
                }.flatten()

        def sharedEpPort = yFlow.sharedEndpoint.portNumber

        when: "Get all flows on the switch ports used by subflow under test"
        def response = switchHelper.getFlowsV2(switchTriplet.shared, usedPortsList)

        then: "Each port in response has information about subflow"
        response.flowsByPort.every {
            usedPortsList.contains(it.key) && it.value*.flowId.contains(yFlowSubFlow2Id)
        }

        and: "Used port on shared endpoint shows both sub-flows"
        response.flowsByPort.get(sharedEpPort).flowId.sort() == [yFlowSubFlow1Id, yFlowSubFlow2Id].sort()
    }

    @Tags([SMOKE])
    def "System allows to get flows on particular ports on switch"() {
        given: "Y-Flow subflow which ends on switch that is not in the path of another sub-flow or regular flow"
        and: "List of the ports that subflow uses on switch, received from flow path"
        def switchWithOnlyOneSubFlow = switchPair.dst == switchTriplet.ep1 ? switchTriplet.ep2 : switchTriplet.ep1

        def usedPortsList = yFlow.retrieveAllEntityPaths().subFlowPaths.find { it.flowId == yFlowSubFlow2Id }
                .collect { it.path.forward.retrieveNodes().findAll { it.switchId == switchWithOnlyOneSubFlow.dpId }.portNo }.flatten()

        when: "Get all flows on the switch ports used by subflow under test"
        def response = switchHelper.getFlowsV2(switchWithOnlyOneSubFlow, usedPortsList)

        then: "Each port in response has information about the subflow"
        response.flowsByPort.every {
            usedPortsList.contains(it.key) && it.value.flowId.unique() == [yFlowSubFlow2Id]
        }
    }

    def "System allows to get a flow that #switchRole switch"() {
        given: "Flow that #switchRole switch"
        when: "Get all flows going through the switch"
        def flows = switchHelper.getFlowsV2(switchUnderTest, [])

        then: "The created flows (including both y-flow subflows) are in the response list from the switch"
        flows.flowsByPort.collectMany { it.value.flowId }.unique().sort() == [flowId, yFlowSubFlow1Id, yFlowSubFlow2Id].sort()

        where:
        switchRole      | switchUnderTest
        "flows through" | switchFlowGoesThrough
        "starts from"   | switchPair.src
        "ends on"       | switchPair.dst
    }

    def "System allows to get a flow which protected path that goes through switch"() {
        given: "Flow which protected path goes through switch"
        when: "Get all flows going through the switch"
        def flows = switchHelper.getFlowsV2(switchProtectedPathGoesThrough, [])

        then: "The flow's protected path is in the response list from the switch"
        flows.flowsByPort.collectMany { it.value.flowId }.unique() == [flowId]
    }

    @Tags([LOW_PRIORITY])
    def "Mirror sink endpoint port is not listed in list of the ports used"() {
        given: "Switch with flow on it and a free port"
        def switchUnderTest = switchPair.getDst()
        def usedPortsList = switchHelper.getUsedPorts(switchUnderTest.dpId)
        def freePort = (new ArrayList<>(1..1000).asList()
                - usedPortsList
                - topology.getBusyPortsForSwitch(switchUnderTest)).first()

        when: "Create mirror point on switch with sink pointing to free port"
        flow.createMirrorPoint(
                switchUnderTest.dpId, freePort, randomVlan(),
                FlowPathDirection.REVERSE
        )

        then: "Mirror sink endpoint port is not listed in the ports list"
        switchHelper.getFlowsV2(switchUnderTest, [freePort]).getFlowsByPort().isEmpty()
    }

    @Tags([LOW_PRIORITY])
    def "Empty list is returned if none of requested ports is busy with any flow"() {
        given: "Switch with flow on it and ports this flow uses"
        def switchUnderTest = switchPair.dst
        def usedPortsList = switchHelper.getUsedPorts(switchUnderTest.dpId)

        when: "Request flows on several unused ports"
        def unusedPortsList = new ArrayList<>(1..1000).asList() - usedPortsList

        then: "Response is empty, but without errors"
        switchHelper.getFlowsV2(switchUnderTest, unusedPortsList.subList(0, 3)).getFlowsByPort().isEmpty()
    }

    @Tags([LOW_PRIORITY])
    def "One-switch Y-Flow subflows are listed in flows list"() {
        given: "One switch Y-Flow"
        def swT = switchTriplets.all(false, true)
                .withSpecificSingleSwitch(switchProtectedPathGoesThrough)
        def yFlow = yFlowFactory.getRandom(swT, false)

        when: "Request flows on switch"
        def flows = switchHelper.getFlowsV2(switchProtectedPathGoesThrough, [])

        then: "Ports used by subflows on the switch are in response"
        flows.flowsByPort.collectMany { it.value }*.flowId
                .containsAll(yFlow.subFlows*.flowId)

    }
}
