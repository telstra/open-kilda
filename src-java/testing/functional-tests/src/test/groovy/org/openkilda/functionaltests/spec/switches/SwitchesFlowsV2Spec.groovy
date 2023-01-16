package org.openkilda.functionaltests.spec.switches

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.YFlowHelper
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.testing.model.topology.TopologyDefinition
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.Unroll

import static org.junit.jupiter.api.Assumptions.assumeTrue
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

    def setupSpec() {
        switchTriplet = topologyHelper.getSwitchTriplets(true, false).find {
            it.shared != it.ep1 && it.pathsEp1.min { it.size() }?.size() > 2
        }
        assumeTrue(switchTriplet != null, "Couldn't find appropriate switch triplet")
        switchPair = topologyHelper.getSwitchPairs(false).find {
            [it.getSrc(), it.getDst()].toSet() == ([switchTriplet.getShared(), switchTriplet.getEp1()].toSet())
        }
        flowId = flowHelperV2.addFlow(flowHelperV2.randomFlow(switchPair, false)).getFlowId()
        switchFlowGoesThrough = pathHelper.getInvolvedSwitches(flowId).find {
            ![switchPair.getSrc(), switchPair.getDst()].contains(it)
        }
        def yFlow = yFlowHelper.addYFlow(yFlowHelper.randomYFlow(switchTriplet))
        yFlowId = yFlow.getYFlowId()
        yFlowSubFlow1Id = yFlow.getSubFlows().get(0).getFlowId()
        yFlowSubFlow2Id = yFlow.getSubFlows().get(1).getFlowId()
    }

    @Tidy
    @Tags([SMOKE])
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
    def "Empty list is returned if none of requested ports is busy with any flow"() {
        given: "Switch with flow on it and ports this flow uses"
        def switchUnderTest = switchPair.getDst()
        def usedPortsList = switchHelper."get used ports"(switchUnderTest.getDpId())
        when: "Request flows on several unused ports"
        def unusedPortsList = new ArrayList<>(1..1000).asList() - usedPortsList
        then: "Response is empty, but without errors"
        switchHelper.getFlowsV2(switchUnderTest, unusedPortsList.subList(0, 3)).getFlowsByPort().isEmpty()
    }

    def cleanupSpec() {
        Wrappers.silent {
            flowHelperV2.deleteFlow(flowId)
            yFlowHelper.deleteYFlow(yFlowId)
        }
    }
}
