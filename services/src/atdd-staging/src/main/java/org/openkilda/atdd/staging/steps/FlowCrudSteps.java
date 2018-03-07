package org.openkilda.atdd.staging.steps;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import cucumber.api.java8.En;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition.OutPort;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition.Switch;
import org.openkilda.atdd.staging.service.FloodlightService;
import org.openkilda.atdd.staging.service.NorthboundService;
import org.openkilda.atdd.staging.service.TopologyEngineService;
import org.openkilda.atdd.staging.utils.TopologyChecker;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathInfoData;
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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class FlowCrudSteps implements En {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlowCrudSteps.class);

    // The retrier is used for repeating operations which depend on the system state and may change the result after delays.
    private final RetryPolicy retryPolicy = new RetryPolicy()
            .withDelay(2, TimeUnit.SECONDS)
            .withMaxRetries(10);

    @Autowired
    private NorthboundService northboundService;

    @Autowired
    private FloodlightService floodlightService;

    @Autowired
    private TopologyEngineService topologyEngineService;

    @Autowired
    private TopologyDefinition topologyDefinition;

    @VisibleForTesting
    List<FlowPayload> flows = emptyList();
    @VisibleForTesting
    RangeSet<Integer> allocatedVlans = TreeRangeSet.create();

    @Given("^a reference topology$")
    public void checkTheTopology() {
        List<TopologyDefinition.Switch> referenceSwitches = topologyDefinition.getActiveSwitches();
        List<SwitchInfoData> actualSwitches = topologyEngineService.getActiveSwitches();

        assertFalse("No switches were discovered", actualSwitches.isEmpty());
        assertTrue("Expected and discovered switches are different",
                TopologyChecker.matchSwitches(actualSwitches, referenceSwitches));

        List<TopologyDefinition.Isl> referenceLinks = topologyDefinition.getIslsForActiveSwitches();
        List<IslInfoData> actualLinks = topologyEngineService.getAllLinks();

        assertFalse("No links were discovered", actualLinks.isEmpty());
        assertTrue("Reference links were not discovered / not provided",
                TopologyChecker.matchLinks(actualLinks, referenceLinks));
    }

    @Given("^flows over all switches$")
    public void defineFlowsOverAllSwitches() {
        final List<TopologyDefinition.Switch> switches = topologyDefinition.getActiveSwitches();
        // check each combination of active switches for a path between them and create a flow definition if the path exists
        flows = switches.stream()
                .flatMap(srcSwitch -> switches.stream()
                        .filter(dstSwitch -> {
                            List<PathInfoData> paths =
                                    topologyEngineService.getPaths(srcSwitch.getDpId(), dstSwitch.getDpId());
                            return !paths.isEmpty();
                        })
                        .map(dstSwitch -> {
                            String flowId = format("%s-%s", srcSwitch.getName(), dstSwitch.getName());
                            return buildFlow(flowId, srcSwitch, dstSwitch);
                        })
                        .filter(Objects::nonNull))
                .collect(toList());
    }

    private FlowPayload buildFlow(String flowId, Switch srcSwitch, Switch destSwitch) {
        // Take the switch vlan ranges as the base
        RangeSet<Integer> srcRangeSet = TreeRangeSet.create();
        srcSwitch.getOutPorts().forEach(port -> srcRangeSet.addAll(port.getVlanRange()));
        RangeSet<Integer> destRangeSet = TreeRangeSet.create();
        destSwitch.getOutPorts().forEach(port -> destRangeSet.addAll(port.getVlanRange()));
        // Exclude already allocated vlans
        srcRangeSet.removeAll(allocatedVlans);
        destRangeSet.removeAll(allocatedVlans);

        if (srcRangeSet.isEmpty() || destRangeSet.isEmpty()) {
            LOGGER.warn("Unable to define a flow between {} and {} as no vlan available.", srcSwitch, destSwitch);
            return null;
        }

        // Calculate intersection of the ranges
        RangeSet<Integer> interRangeSet = TreeRangeSet.create(srcRangeSet);
        interRangeSet.removeAll(destRangeSet.complement());

        int srcVlan;
        int destVlan;
        if (!interRangeSet.isEmpty()) {
            // Same vlan flow
            Range<Integer> interRange = interRangeSet.asRanges().iterator().next();
            srcVlan = ContiguousSet.create(interRange, DiscreteDomain.integers()).first();
            destVlan = srcVlan;
        } else {
            // Cross vlan flow
            Range<Integer> srcRange = srcRangeSet.asRanges().iterator().next();
            srcVlan = ContiguousSet.create(srcRange, DiscreteDomain.integers()).first();
            Range<Integer> destRange = destRangeSet.asRanges().iterator().next();
            destVlan = ContiguousSet.create(destRange, DiscreteDomain.integers()).first();
        }

        boolean sameSwitchFlow = srcSwitch.getDpId().equals(destSwitch.getDpId());

        Optional<OutPort> srcPort = srcSwitch.getOutPorts().stream()
                .filter(p -> p.getVlanRange().contains(srcVlan))
                .findFirst();
        int srcPortId = srcPort
                .orElseThrow(() -> new IllegalStateException("Unable to locate a port in found vlan."))
                .getPort();

        Optional<OutPort> destPort = destSwitch.getOutPorts().stream()
                .filter(p -> p.getVlanRange().contains(destVlan))
                .filter(p -> !sameSwitchFlow || p.getPort() != srcPortId)
                .findFirst();
        if (!destPort.isPresent()) {
            LOGGER.warn("Unable to define a same switch flow for {} as no ports available.", srcSwitch);
            return null;

        }

        // Record used vlan to archive uniqueness
        allocatedVlans.add(Range.singleton(srcVlan));
        allocatedVlans.add(Range.singleton(destVlan));

        FlowEndpointPayload srcEndpoint = new FlowEndpointPayload(srcSwitch.getDpId(), srcPortId, srcVlan);
        FlowEndpointPayload destEndpoint = new FlowEndpointPayload(destSwitch.getDpId(), destPort.get().getPort(),
                destVlan);
        return new FlowPayload(flowId, srcEndpoint, destEndpoint,
                1, false, flowId, null);
    }

    @And("^each flow has unique flow_id$")
    public void setUniqueFlowIdToEachFlow() {
        flows.forEach(flow -> flow.setId(format("%s-%s", flow.getId(), UUID.randomUUID().toString())));
    }

    @And("^each flow has max bandwidth set to (\\d+)$")
    public void setBandwidthToEachFlow(int bandwidth) {
        flows.forEach(flow -> flow.setMaximumBandwidth(bandwidth));
    }

    @When("^creation request for each flow is successful$")
    public void creationRequestForEachFlowIsSuccessful() {
        for (FlowPayload flow : flows) {
            FlowPayload result = northboundService.addFlow(flow);
            assertEquals(flow, result);
        }
    }

    @Then("^each flow is created and stored in TopologyEngine$")
    public void eachFlowIsCreatedAndStoredInTopologyEngine() {
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
            ImmutablePair<Flow, Flow> flowPair = Failsafe.with(retryPolicy
                    .abortIf(Objects::nonNull))
                    .get(() -> topologyEngineService.getFlow(expextedFlow.getFlowId()));

            assertNotNull(format("The flow '%s' is missing.", expextedFlow.getFlowId()), flowPair);
            assertEquals(format("The flow '%s' is different.", expextedFlow.getFlowId()), expextedFlow,
                    flowPair.getLeft());
        }
    }

    @And("^each flow is in UP state$")
    public void eachFlowIsInUPState() {
        for (FlowPayload flow : flows) {
            FlowIdStatusPayload status = Failsafe.with(retryPolicy
                    .abortIf(p -> p != null && FlowState.UP == ((FlowIdStatusPayload) p).getStatus()))
                    .get(() -> northboundService.getFlowStatus(flow.getId()));

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
        //TODO: implement the check
    }

    @And("^each flow has traffic going with bandwidth not less than (\\d+)$")
    public void eachFlowHasTrafficGoingWithBandwidthNotLessThan(int bandwidth) {
        //TODO: implement the check
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
        //TODO: implement the check
    }

    @Then("^each flow can be deleted$")
    public void eachFlowCanBeDeleted() {
        for (FlowPayload flow : flows) {
            FlowPayload result = northboundService.deleteFlow(flow.getId());
            assertNotNull(result);
        }
    }

    @And("^each flow can not be read from Northbound$")
    public void eachFlowCanNotBeReadFromNorthbound() {
        for (FlowPayload flow : flows) {
            FlowPayload result = Failsafe.with(retryPolicy
                    .abortIf(Objects::isNull))
                    .get(() -> northboundService.getFlow(flow.getId()));

            assertNull(format("The flow '%s' exists.", flow.getId()), result);
        }
    }

    @And("^each flow can not be read from TopologyEngine$")
    public void eachFlowCanNotBeReadFromTopologyEngine() {
        for (FlowPayload flow : flows) {
            ImmutablePair<Flow, Flow> result = Failsafe.with(retryPolicy
                    .abortIf(Objects::isNull))
                    .get(() -> topologyEngineService.getFlow(flow.getId()));

            assertNull(format("The flow '%s' exists.", flow.getId()), result);
        }
    }

    @And("^each flow has no rules installed$")
    public void eachFlowHasNoRulesInstalled() {
        //TODO: implement the check
    }

    @And("^each flow has no traffic$")
    public void eachFlowHasNoTraffic() {
        //TODO: implement the check
    }
}
