package org.openkilda.atdd.staging.steps;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;

import cucumber.api.PendingException;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import cucumber.api.java8.En;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition;
import org.openkilda.atdd.staging.service.FloodlightService;
import org.openkilda.atdd.staging.service.TopologyEngineService;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

public class FlowCrudSteps implements En {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlowCrudSteps.class);

    @Autowired
    private FloodlightService floodlightService;

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

    @Given("^flows among all switches$")
    public void defineFlowsAmongAllSwitches() {
        final List<TopologyDefinition.Switch> switches = topologyDefinition.getActiveSwitches();
        // a cartesian product of all active switches
        flows = switches.stream()
                .flatMap(source -> switches.stream()
                        .map(dest -> {
                            final String name = format("%s-%s", source.getName(), dest.getName());
                            return new FlowPayload(name,
                                    new FlowEndpointPayload(source.getDpId(), 1, 1),
                                    new FlowEndpointPayload(dest.getDpId(), 1, 1),
                                    1, false,
                                    name, null);
                        }))
                .collect(toList());
    }

    @And("^each flow has unique flow_id$")
    public void setUniqueFlowIdToEachFlow() {
        flows.forEach(flow ->
                flow.setId(format("%s-%s", flow.getId(), UUID.randomUUID().toString())));
    }

    @And("^each flow has (\\d+) and (\\d+) ports and unique vlan \\(not less than (\\d+)\\)$")
    public void setPortsAndUniqueVlanToEachFlow(int sourcePort, int destPort, int minVlan) {
        int vlanIdx = minVlan;

        for (FlowPayload flow : flows) {
            flow.setId(format("%s-%s", flow.getId(), UUID.randomUUID().toString()));
            FlowEndpointPayload source = flow.getSource();
            source.setVlanId(vlanIdx);
            source.setPortId(sourcePort);
            FlowEndpointPayload dest = flow.getDestination();
            dest.setVlanId(vlanIdx);
            dest.setPortId(destPort);

            vlanIdx++;
        }
    }

    @And("^each flow has bandwidth set to (\\d+)$")
    public void setBandwidthToEachFlos(int bandwidth) {
        flows.forEach(flow ->
                flow.setMaximumBandwidth(bandwidth));
    }

    @When("^creation request for each flow is successful$")
    public void creationRequestForEachFlowIsSuccessful() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @Then("^each flow is created and stored in TopologyEngine$")
    public void eachFlowIsCreatedAndStoredInTopologyEngine() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @And("^each flow is in UP state$")
    public void eachFlowIsInUPState() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @And("^each flow can be read from Northbound$")
    public void eachFlowCanBeReadFromNorthbound() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @And("^each flow has rules installed$")
    public void eachFlowHasRulesInstalled() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @And("^traffic via each flow is pingable$")
    public void trafficViaEachFlowIsPingable() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @Then("^each flow can be updated with (\\d+) bandwidth$")
    public void eachFlowCanBeUpdatedWithBandwidth(int arg0) throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @And("^each flow has rules installed with (\\d+) bandwidth$")
    public void eachFlowHasRulesInstalledWithBandwidth(int arg0) throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @Then("^each flow can be deleted$")
    public void eachFlowCanBeDeleted() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @And("^each flow can not be read from Northbound$")
    public void eachFlowCanNotBeReadFromNorthbound() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @And("^traffic via each flow is not pingable$")
    public void trafficViaEachFlowIsNotPingable() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @And("^each flow can not be read from TopologyEngine$")
    public void eachFlowCanNotBeReadFromTopologyEngine() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }
}
