package org.openkilda.atdd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.openkilda.flow.FlowUtils;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowStats;

import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

public class NorthBoundTest {
	 
	private static String sourceSwitchId = "de:ad:be:ef:00:00:00:02";
	private static String destinationSwitchId = "de:ad:be:ef:00:00:00:04";
 
	
	@When("^flow (.*) request for get flow stats$")
	public void checkFlowStats(String statsType) {
		 
		List<FlowPayload> flowPayloads =  FlowUtils.getFlowDump();
		FlowPayload flowPayload = flowPayloads.get(0);
		String flowId = flowPayload.getId();
		FlowStats payload = FlowUtils.getFlowStats(flowId, statsType);
		assertNotNull(payload);
		 
	}

	@Then("^flow (.*) all stats  nbp could be read$")
	public void checkAllFlowStats(String statsType) {

		 
		List<FlowPayload> flowPayloads = FlowUtils.getFlowDump();
		FlowPayload flowPayload = flowPayloads.get(0);
		String flowId = flowPayload.getId();
		sourceSwitchId = flowPayload.getSource().getSwitchId();
		sourceSwitchId = sourceSwitchId.replaceAll(":", "");
		destinationSwitchId = flowPayload.getDestination().getSwitchId();
		destinationSwitchId = destinationSwitchId.replaceAll(":", "");
		FlowStats payload = FlowUtils.getFlowStats(flowId, statsType);
		assertEquals(destinationSwitchId, payload.getForwardEgressFlow().getSwitchId());
		assertEquals(sourceSwitchId, payload.getReverseEgressFlow().getSwitchId());
		assertEquals(sourceSwitchId, payload.getForwardIngressFlow().getSwitchId());
		assertEquals(destinationSwitchId, payload.getReverseIngressFlow().getSwitchId());

	}

	@Then("^flow (.*) forward egress stats of nbp could be read$")
	public void checkForwardEgressStats(String statsType) {

		List<FlowPayload> flowPayloads = FlowUtils.getFlowDump();
		FlowPayload flowPayload = flowPayloads.get(0);
		String flowId = flowPayload.getId();
		sourceSwitchId = flowPayload.getSource().getSwitchId();
		sourceSwitchId = sourceSwitchId.replaceAll(":", "");
		destinationSwitchId = flowPayload.getDestination().getSwitchId();
		destinationSwitchId = destinationSwitchId.replaceAll(":", "");
		FlowStats payload = FlowUtils.getFlowStats(flowId, statsType);
		assertEquals(destinationSwitchId, payload.getForwardEgressFlow().getSwitchId());
	}

	@Then("^flow (.*) forward ingress stats of nbp could be read$")
	public void checkForwardIngressStats(String statsType) {

		List<FlowPayload> flowPayloads = FlowUtils.getFlowDump();
		FlowPayload flowPayload = flowPayloads.get(0);
		String flowId = flowPayload.getId();
		sourceSwitchId = flowPayload.getSource().getSwitchId();
		sourceSwitchId = sourceSwitchId.replaceAll(":", "");
		destinationSwitchId = flowPayload.getDestination().getSwitchId();
		destinationSwitchId = destinationSwitchId.replaceAll(":", "");
		FlowStats payload = FlowUtils.getFlowStats(flowId, statsType);
		assertEquals(sourceSwitchId, payload.getForwardIngressFlow().getSwitchId());
	}
	
	@Then("^flow (.*) reverse ingress stats of nbp could be read$")
	public void checkReverseIngressStats(String statsType) {

		List<FlowPayload> flowPayloads = FlowUtils.getFlowDump();
		FlowPayload flowPayload = flowPayloads.get(0);
		String flowId = flowPayload.getId();
		sourceSwitchId = flowPayload.getSource().getSwitchId();
		sourceSwitchId = sourceSwitchId.replaceAll(":", "");
		destinationSwitchId = flowPayload.getDestination().getSwitchId();
		destinationSwitchId = destinationSwitchId.replaceAll(":", "");
		FlowStats payload = FlowUtils.getFlowStats(flowId, statsType);
		assertEquals(destinationSwitchId, payload.getReverseIngressFlow().getSwitchId());
	}
	
	@Then("^flow (.*) reverse egress stats of nbp could be read$")
	public void checkReverseEgressStats(String statsType) {

		List<FlowPayload> flowPayloads = FlowUtils.getFlowDump();
		FlowPayload flowPayload = flowPayloads.get(0);
		String flowId = flowPayload.getId();
		sourceSwitchId = flowPayload.getSource().getSwitchId();
		sourceSwitchId = sourceSwitchId.replaceAll(":", "");
		destinationSwitchId = flowPayload.getDestination().getSwitchId();
		destinationSwitchId = destinationSwitchId.replaceAll(":", "");
		FlowStats payload = FlowUtils.getFlowStats(flowId, statsType);
		assertEquals(sourceSwitchId, payload.getReverseEgressFlow().getSwitchId());
	}
	
}