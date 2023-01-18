package org.openkilda.functionaltests.spec.switches

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.YFlowHelper
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.model.FlowPathDirection
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2
import org.openkilda.northbound.dto.v2.flows.FlowMirrorPointPayload
import org.openkilda.testing.model.topology.TopologyDefinition
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.Unroll

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE

@Narrative("Verifies feature to retrieve list of flows passing the switch grouped by port number. Details: #5015")
class SwitchesFlowsV2Spec extends HealthCheckSpecification {
    @Shared
    String yFlowId
    @Shared
    String yFlowSubFlow1Id
    @Shared
    String yFlowSubFlow2Id
    @Shared
    String flowId
    @Shared
    SwitchTriplet switchTriplet
    @Shared
    SwitchPair switchPair
    @Shared
    @Autowired
    YFlowHelper yFlowHelper
    @Shared
    TopologyDefinition.Switch switchFlowGoesThrough
    @Shared
    TopologyDefinition.Switch switchProtectedPathGoesThrough

    def setupSpec() {
        /* Topology used to test features in this spec looks like this:
          2 subflows of Y-flow                                      subflow 1 + usual flow
                    🡾                                                   🡾
        (Shared SW)☴☴☴(switch flow goes through)☴☴☴☴☴☴☴☴☴☴☴☴=============(Endpoint1 SW)
                    🡽  \-----(SW protected path goes through)---/   \------------(Endpoint2 SW)
            usual flow                  🡹                                   🡹
                                   usual flow protected path           subflow 2
         */
        switchTriplet = topologyHelper.getSwitchTriplets(true, false).find {
            it.shared != it.ep1 && it.pathsEp1.min { it.size() }?.size() > 2
                    && it.pathsEp1.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }.size() >= 2
        }
        assumeTrue(switchTriplet != null, "Couldn't find appropriate switch triplet")
        switchPair = topologyHelper.getSwitchPairs(false).find {
            [it.getSrc(), it.getDst()].toSet() == ([switchTriplet.getShared(), switchTriplet.getEp1()].toSet())
        }
        def flowDefinition = flowHelperV2.randomFlow(switchPair, false).tap { allocateProtectedPath = true }
        flowId = flowHelperV2.addFlow(flowDefinition).getFlowId()
        switchFlowGoesThrough = pathHelper.getInvolvedSwitches(flowId).find {
            ![switchPair.getSrc(), switchPair.getDst()].contains(it)
        }
        switchProtectedPathGoesThrough = pathHelper.getInvolvedSwitchesForProtectedPath(flowId).find {
            ![switchPair.getSrc(), switchPair.getDst()].contains(it)
        }

        def yFlow = yFlowHelper.addYFlow(yFlowHelper.randomYFlow(switchTriplet))
        yFlowId = yFlow.getYFlowId()
        yFlowSubFlow1Id = yFlow.getSubFlows().get(0).getFlowId()
        yFlowSubFlow2Id = yFlow.getSubFlows().get(1).getFlowId()
    }

    @Tidy
    @Tags([SMOKE])
    def "System allows to get flows on particular ports on switch"() {
        given: "YFlow subflow which starts on switch"
        and: "List of the ports that subflow uses on switch, received from flow path"
        def usedPortsList = flowHelper."get ports that flow uses on switch from path"(yFlowSubFlow2Id,
                switchTriplet.getShared().getDpId())

        when: "Get all flows on the switch ports used by subflow under test"
        def response = switchHelper.getFlowsV2(switchTriplet.getShared(), usedPortsList)

        then: "Each port in response has information about subflow"
        response.flowsByPort.every {
            usedPortsList.contains(it.key) && it.value*.flowId.contains(yFlowSubFlow2Id)
        }
    }

    @Tidy
    @Unroll
    def "System allows to get a flow that #switchRole switch"() {
        given: "Flow that #switchRole switch"
        when: "Get all flows going through the switch"
        def flows = switchHelper.getFlowsV2(switchUnderTest, [])

        then: "The created flows (including both y-flow subflows) are in the response list from the switch"
        flows.flowsByPort.collectMany { it.value }*.flowId
                .containsAll([flowId, yFlowSubFlow1Id, yFlowSubFlow2Id])

        where:
        switchRole      | switchUnderTest
        "flows through" | switchFlowGoesThrough
        "starts from"   | switchPair.getSrc()
        "ends on"       | switchPair.getDst()
    }

    @Tidy
    @Unroll
    def "System allows to get a flow which protected path that goes through switch"() {
        given: "Flow which protected path goes through switch"
        when: "Get all flows going through the switch"
        def flows = switchHelper.getFlowsV2(switchProtectedPathGoesThrough, [])

        then: "The created flows (including both y-flow subflows) are in the response list from the switch"
        flows.flowsByPort.collectMany { it.value }*.flowId
                .contains(flowId)
    }



    @Tidy
    @Tags([LOW_PRIORITY])
    def "Mirror sink endpoint port is not listed in list of the ports used"() {
        given: "Switch with flow on it and a free port"
        def switchUnderTest = switchPair.getDst()
        def usedPortsList = switchHelper."get used ports"(switchUnderTest.getDpId())
        def freePort = (new ArrayList<>(1..1000).asList()
                - usedPortsList
                - topology.getBusyPortsForSwitch(switchUnderTest)).first()

        when: "Create mirror point on switch with sink pointing to free port"
        def mirrorEndpoint = FlowMirrorPointPayload.builder()
                .mirrorPointId(flowHelperV2.generateFlowId())
                .mirrorPointDirection(FlowPathDirection.REVERSE.toString().toLowerCase())
                .mirrorPointSwitchId(switchUnderTest.getDpId())
                .sinkEndpoint(FlowEndpointV2.builder().switchId(switchUnderTest.getDpId()).portNumber(freePort)
                        .vlanId(flowHelperV2.randomVlan())
                        .build())
                .build()
        northboundV2.createMirrorPoint(flowId, mirrorEndpoint)

        then: "Mirror sink endpoint port is listed in the ports list"
        switchHelper.getFlowsV2(switchUnderTest, [freePort]).getFlowsByPort().isEmpty()

        cleanup:
        Wrappers.silent(northboundV2.deleteMirrorPoint(flowId, mirrorEndpoint.getMirrorPointId()))
    }

    @Tidy
    @Tags([LOW_PRIORITY])
    def "Empty list is returned if none of requested ports is busy with any flow"() {
        given: "Switch with flow on it and ports this flow uses"
        def switchUnderTest = switchPair.getDst()
        def usedPortsList = switchHelper."get used ports"(switchUnderTest.getDpId())

        when: "Request flows on several unused ports"
        def unusedPortsList = new ArrayList<>(1..1000).asList() - usedPortsList

        then: "Response is empty, but without errors"
        switchHelper.getFlowsV2(switchUnderTest, unusedPortsList.subList(0, 3)).getFlowsByPort().isEmpty()
    }

    @Tidy
    @Tags([LOW_PRIORITY])
    def "One-switch YFlow subflows are listed in flows list"() {
        given: "One switch YFlow"

        def yFlow = yFlowHelper.addYFlow(yFlowHelper.singleSwitchYFlow(switchProtectedPathGoesThrough, false))
        when: "Request flows on switch"
        def flows = switchHelper.getFlowsV2(switchProtectedPathGoesThrough, [])

        then: "Ports used by subflows on the switch are in response"
        flows.flowsByPort.collectMany { it.value }*.flowId
                .containsAll(yFlow.subFlows*.flowId)
    }

    def cleanupSpec() {
        Wrappers.silent {
            flowHelperV2.deleteFlow(flowId)
            yFlowHelper.deleteYFlow(yFlowId)
        }
    }
}
