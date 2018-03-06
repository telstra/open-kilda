package org.openkilda.atdd.staging.steps;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import cucumber.api.PendingException;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import cucumber.api.java8.En;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition;
import org.openkilda.atdd.staging.service.NorthboundService;
import org.openkilda.atdd.staging.service.TopologyEngineService;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;
import org.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class FlowCrudSteps implements En {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlowCrudSteps.class);

    private static final int FLOW_WAIT_ATTEMPTS = 10;
    private static final int FLOW_WAIT_DELAY = 2;

    @Autowired
    private NorthboundService northboundService;

    @Autowired
    private TopologyEngineService topologyEngineService;

    @Autowired
    private TopologyDefinition topologyDefinition;

    private List<FlowPayload> flows;

    @Given("^a reference topology$")
    public void checkTheTopology() {
        List<TopologyDefinition.Switch> referenceSwitches = topologyDefinition.getActiveSwitches();
        List<SwitchInfoData> actualSwitches = topologyEngineService.getActiveSwitches();
        assertEquals("Expected and discovered switches are different", referenceSwitches.size(),
                actualSwitches.size());
    }

    @Given("^flows over all switches$")
    public void defineFlowsOverAllSwitches() {
        final List<TopologyDefinition.Switch> switches = topologyDefinition.getActiveSwitches();
        // check each combination of active switches for a path between them and create a flow definition if the path exists
        flows = switches.stream()
                .flatMap(source -> switches.stream()
                        .filter(dest -> {
                            List<List<SwitchInfoData>> paths =
                                    topologyEngineService.getPaths(source.getDpId(), dest.getDpId());
                            return !paths.isEmpty();
                        })
                        .map(dest -> {
                                    String flowId = format("%s-%s", source.getName(), dest.getName());
                                    return new FlowPayload(flowId,
                                            new FlowEndpointPayload(source.getDpId(), 1, 1),
                                            new FlowEndpointPayload(dest.getDpId(), 1, 1),
                                            1, false,
                                            flowId, null);
                                }
                        ))
                .collect(toList());

        assertFalse(flows.isEmpty());
    }

    @And("^each flow has unique flow_id$")
    public void setUniqueFlowIdToEachFlow() {
        flows.forEach(flow ->
                flow.setId(format("%s-%s", flow.getId(), UUID.randomUUID().toString())));
    }

    @And("^each flow has allocated ports and unique vlan$")
    public void setPortsAndUniqueVlanToEachFlow() {
        //TODO: implement
        throw new PendingException();
    }

    @And("^each flow has max bandwidth set to (\\d+)$")
    public void setBandwidthToEachFlow(int bandwidth) {
        flows.forEach(flow ->
                flow.setMaximumBandwidth(bandwidth));
    }

    @When("^creation request for each flow is successful$")
    public void creationRequestForEachFlowIsSuccessful() {
        for (FlowPayload flow : flows) {
            FlowPayload result = northboundService.addFlow(flow);
            assertEquals(flow, result);
        }
    }

    @Then("^each flow is created and stored in TopologyEngine$")
    public void eachFlowIsCreatedAndStoredInTopologyEngine() throws InterruptedException {
        List<Flow> expextedFlows = flows.stream()
                .map(flow -> new Flow(flow.getId(),
                        flow.getMaximumBandwidth(),
                        flow.isIgnoreBandwidth(), 0,
                        flow.getId(), null,
                        flow.getSource().getSwitchId(),
                        flow.getDestination().getSwitchId(),
                        flow.getSource().getPortId(),
                        flow.getDestination().getPortId(),
                        flow.getSource().getVlanId(),
                        flow.getDestination().getVlanId(),
                        0, 0, null, null))
                .collect(toList());

        for (Flow expextedFlow : expextedFlows) {
            ImmutablePair<Flow, Flow> flowPair = null;
            for (int i = 0; i < FLOW_WAIT_ATTEMPTS; i++) {
                flowPair = topologyEngineService.getFlow(expextedFlow.getFlowId());
                if (flowPair != null) {
                    break;
                }
                TimeUnit.SECONDS.sleep(FLOW_WAIT_DELAY);
            }

            assertNotNull(format("The flow '%s' is missing.", expextedFlow.getFlowId()), flowPair);
            assertEquals(format("The flow '%s' is different.", expextedFlow.getFlowId()), expextedFlow,
                    flowPair.getLeft());
        }
    }

    @And("^each flow is in UP state$")
    public void eachFlowIsInUPState() throws InterruptedException {
        for (FlowPayload flow : flows) {
            FlowIdStatusPayload status = null;
            for (int i = 0; i < FLOW_WAIT_ATTEMPTS; i++) {
                status = northboundService.getFlowStatus(flow.getId());
                if (status != null && FlowState.UP == status.getStatus()) {
                    break;
                }
                TimeUnit.SECONDS.sleep(FLOW_WAIT_DELAY);
            }

            assertNotNull(status);
            assertEquals(flow.getId(), status.getId());
            assertEquals(FlowState.UP, status.getStatus());
        }
    }

    @And("^each flow can be read from Northbound$")
    public void eachFlowCanBeReadFromNorthbound() {
        for (FlowPayload flow : flows) {
            FlowPayload result = northboundService.getFlow(flow.getId());
            assertNotNull(result);
        }
    }

    @And("^each flow has rules installed$")
    public void eachFlowHasRulesInstalled() {
        //TODO: implement
        throw new PendingException();
    }

    @And("^each flow has traffic going with bandwidth not less than (\\d+)$")
    public void eachFlowHasTrafficGoingWithBandwidthNotLessThan(int bandwidth) {
        //TODO: implement
        throw new PendingException();
    }

    @Then("^each flow can be updated with (\\d+) max bandwidth$")
    public void eachFlowCanBeUpdatedWithBandwidth(int bandwidth) {
        for (FlowPayload flow : flows) {
            flow.setMaximumBandwidth(bandwidth);
            FlowPayload result = northboundService.updateFlow(flow.getId(), flow);
            assertNotNull(result);
        }
    }

    @And("^each flow has rules installed with (\\d+) max bandwidth$")
    public void eachFlowHasRulesInstalledWithBandwidth(int bandwidth) {
        //TODO: implement
        throw new PendingException();
    }

    @Then("^each flow can be deleted$")
    public void eachFlowCanBeDeleted() {
        for (FlowPayload flow : flows) {
            FlowPayload result = northboundService.deleteFlow(flow.getId());
            assertNotNull(result);
        }
    }

    @And("^each flow can not be read from Northbound$")
    public void eachFlowCanNotBeReadFromNorthbound() throws InterruptedException {
        for (FlowPayload flow : flows) {
            FlowPayload result = null;
            for (int i = 0; i < FLOW_WAIT_ATTEMPTS; i++) {
                result = northboundService.getFlow(flow.getId());
                if (result == null) {
                    break;
                }
                TimeUnit.SECONDS.sleep(FLOW_WAIT_DELAY);
            }

            assertNull(format("The flow '%s' exists.", flow.getId()), result);
        }
    }

    @And("^each flow can not be read from TopologyEngine$")
    public void eachFlowCanNotBeReadFromTopologyEngine() throws InterruptedException {
        for (FlowPayload flow : flows) {
            ImmutablePair<Flow, Flow> flowPair = null;
            for (int i = 0; i < FLOW_WAIT_ATTEMPTS; i++) {
                flowPair = topologyEngineService.getFlow(flow.getId());
                if (flowPair == null) {
                    break;
                }
                TimeUnit.SECONDS.sleep(FLOW_WAIT_DELAY);
            }

            assertNull(format("The flow '%s' exists.", flow.getId()), flowPair);
        }
    }

    @And("^each flow has no rules installed$")
    public void eachFlowHasNoRulesInstalled() {
        //TODO: implement
        throw new PendingException();
    }

    @And("^each flow has no traffic$")
    public void eachFlowHasNoTraffic() {
        //TODO: implement
        throw new PendingException();
    }
}
