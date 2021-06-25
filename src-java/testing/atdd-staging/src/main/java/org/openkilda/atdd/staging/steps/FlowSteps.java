/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.atdd.staging.steps;

import static com.nitorcreations.Matchers.reflectEquals;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.openkilda.atdd.staging.helpers.FlowSet;
import org.openkilda.atdd.staging.helpers.TopologyUnderTest;
import org.openkilda.atdd.staging.service.flowmanager.FlowManager;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.dto.v1.flows.FlowValidationDto;
import org.openkilda.testing.model.topology.TopologyDefinition;
import org.openkilda.testing.model.topology.TopologyDefinition.Switch;
import org.openkilda.testing.service.database.Database;
import org.openkilda.testing.service.floodlight.FloodlightService;
import org.openkilda.testing.service.floodlight.model.MeterEntry;
import org.openkilda.testing.service.floodlight.model.MetersEntriesMap;
import org.openkilda.testing.service.northbound.NorthboundService;

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
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.HttpClientErrorException;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
public class FlowSteps implements En {

    @Autowired @Qualifier("northboundServiceImpl")
    private NorthboundService northboundService;

    @Autowired
    private FloodlightService floodlightService;

    @Autowired
    private Database db;

    @Autowired
    private TopologyDefinition topologyDefinition;

    @Autowired
    private FlowManager flowManager;

    @Autowired
    @Qualifier("topologyUnderTest")
    private TopologyUnderTest topologyUnderTest;

    private Set<FlowPayload> flows;
    private FlowPayload flowResponse;

    @Given("^flows defined over active switches in the reference topology$")
    public void defineFlowsOverActiveSwitches() {
        flows = flowManager.allActiveSwitchesFlows();
    }

    @And("^each flow has unique flow_id$")
    public void setUniqueFlowIdToEachFlow() {
        flows.forEach(flow -> flow.setId(format("%s-%s", flow.getId(), UUID.randomUUID().toString())));
    }

    @And("^(?:each )?flow has max bandwidth set to (\\d+)$")
    public void setBandwidthToEachFlow(int bandwidth) {
        flows.forEach(flow -> flow.setMaximumBandwidth(bandwidth));
    }

    @When("^initialize creation of given flows$")
    public void creationRequestForEachFlowIsSuccessful() {
        for (FlowPayload flow : flows) {
            FlowPayload result = northboundService.addFlow(flow);
            assertThat(format("A flow creation request for '%s' failed.", flow.getId()), result,
                    reflectEquals(flow, "created", "lastUpdated", "status"));
            assertThat(format("Flow status for '%s' was not set to '%s'. Received status: '%s'",
                    flow.getId(), FlowState.IN_PROGRESS, result.getStatus()),
                    result.getStatus(), equalTo(FlowState.IN_PROGRESS.toString()));
            assertThat(format("The flow '%s' is missing 'created' field", flow.getId()), result,
                    hasProperty("created", notNullValue()));
            assertThat(format("The flow '%s' is missing 'lastUpdated' field", flow.getId()), result,
                    hasProperty("lastUpdated", notNullValue()));
        }
    }

    @And("^(?:each )?flow is in UP state$")
    public void eachFlowIsInUpState() {
        eachFlowIsUp(flows);
    }

    @And("^each flow can be read from Northbound$")
    public void eachFlowCanBeReadFromNorthbound() {
        for (FlowPayload flow : flows) {
            FlowPayload result = northboundService.getFlow(flow.getId());

            assertNotNull(format("The flow '%s' is missing in Northbound.", flow.getId()), result);
            assertEquals(format("The flow '%s' in Northbound is different from defined.", flow.getId()), flow.getId(),
                    result.getId());
        }
    }

    @And("^(?:each )?flow is valid per Northbound validation$")
    public void eachFlowIsValid() {
        flows.forEach(flow -> {
            List<FlowValidationDto> validations = northboundService.validateFlow(flow.getId());
            validations.forEach(flowValidation -> {
                assertEquals(flow.getId(), flowValidation.getFlowId());
                assertTrue(format("The flow '%s' has discrepancies: %s", flow.getId(),
                        flowValidation.getDiscrepancies()), flowValidation.getDiscrepancies().isEmpty());
                assertTrue(format("The flow '%s' didn't pass validation.", flow.getId()),
                        flowValidation.getAsExpected());
            });

        });
    }

    @Then("^each flow can be updated with (\\d+) max bandwidth( and new vlan)?$")
    public void eachFlowCanBeUpdatedWithBandwidth(int bandwidth, String newVlanStr) {
        final boolean newVlan = newVlanStr != null;
        for (FlowPayload flow : flows) {
            flow.setMaximumBandwidth(bandwidth);
            if (newVlan) {
                flow.getDestination().setVlanId(getAllowedVlan(flows, flow.getDestination().getSwitchDpId()));
                flow.getSource().setVlanId(getAllowedVlan(flows, flow.getSource().getSwitchDpId()));
            }
            FlowPayload result = northboundService.updateFlow(flow.getId(), flow);
            assertThat(format("A flow update request for '%s' failed.", flow.getId()), result,
                    reflectEquals(flow, "created", "lastUpdated", "status"));
        }
    }

    @Then("^each flow can be deleted$")
    public void eachFlowCanBeDeleted() {
        List<String> deletedFlowIds = new ArrayList<>();

        for (FlowPayload flow : flows) {
            FlowPayload result = northboundService.deleteFlow(flow.getId());
            if (result != null) {
                deletedFlowIds.add(result.getId());
            }
        }

        assertThat("Deleted flows from Northbound don't match expected", deletedFlowIds, containsInAnyOrder(
                flows.stream().map(flow -> equalTo(flow.getId())).collect(toList())));
    }

    @And("^each flow can not be read from Northbound$")
    public void eachFlowCanNotBeReadFromNorthbound() {
        for (FlowPayload flow : flows) {
            FlowPayload result = null;
            try {
                result = Failsafe.with(retryPolicy()
                        .abortWhen(null)
                        .handleResultIf(Objects::nonNull))
                        .get(() -> northboundService.getFlow(flow.getId()));
            } catch (HttpClientErrorException ex) {
                log.info(format("The flow '%s' doesn't exist. It is expected.", flow.getId()));
            }

            assertNull(format("The flow '%s' exists.", flow.getId()), result);
        }
    }

    @And("^create flow between '(.*)' and '(.*)' and alias it as '(.*)'$")
    public void createFlowBetween(String srcAlias, String dstAlias, String flowAlias) {
        Switch srcSwitch = topologyUnderTest.getAliasedObject(srcAlias);
        Switch dstSwitch = topologyUnderTest.getAliasedObject(dstAlias);
        FlowPayload flow = new FlowSet().buildWithAnyPortsInUniqueVlan("auto" + getTimestamp(),
                srcSwitch, dstSwitch, 1000);
        northboundService.addFlow(flow);
        topologyUnderTest.addAlias(flowAlias, flow);
    }

    @And("^'(.*)' flow is in UP state$")
    public void flowIsUp(String flowAlias) {
        FlowPayload flow = topologyUnderTest.getAliasedObject(flowAlias);
        eachFlowIsUp(Collections.singleton(flow));
    }

    @When("^request all switch meters for switch '(.*)' and alias results as '(.*)'$")
    public void requestMeters(String switchAlias, String meterAlias) {
        Switch sw = topologyUnderTest.getAliasedObject(switchAlias);
        topologyUnderTest.addAlias(meterAlias, floodlightService.getMeters(sw.getDpId()));

    }

    @And("^select first meter of '(.*)' and alias it as '(.*)'$")
    public void selectFirstMeter(String metersAlias, String newMeterAlias) {
        MetersEntriesMap meters = topologyUnderTest.getAliasedObject(metersAlias);
        Entry<Integer, MeterEntry> firstMeter = meters.entrySet().iterator().next();
        topologyUnderTest.addAlias(newMeterAlias, firstMeter);
    }

    @Then("^meters '(.*)' does not have '(.*)'$")
    public void doesNotHaveMeter(String metersAlias, String meterAlias) {
        MetersEntriesMap meters = topologyUnderTest.getAliasedObject(metersAlias);
        Entry<Integer, MeterEntry> meter = topologyUnderTest.getAliasedObject(meterAlias);
        assertFalse(meters.containsKey(meter.getKey()));
    }

    @Given("^random flow aliased as '(.*)'$")
    public void randomFlowAliasedAsFlow(String flowAlias) {
        topologyUnderTest.addAlias(flowAlias, flowManager.randomFlow());
    }

    @And("^change bandwidth of (.*) flow to (\\d+)$")
    public void changeBandwidthOfFlow(String flowAlias, int newBw) {
        FlowPayload flow = topologyUnderTest.getAliasedObject(flowAlias);
        flow.setMaximumBandwidth(newBw);
    }

    @When("^change bandwidth of (.*) flow to '(.*)'$")
    public void changeBandwidthOfFlow(String flowAlias, String bwAlias) {
        FlowPayload flow = topologyUnderTest.getAliasedObject(flowAlias);
        long bw = topologyUnderTest.getAliasedObject(bwAlias);
        flow.setMaximumBandwidth(bw);
    }

    @And("^create flow '(.*)'$")
    public void createFlow(String flowAlias) {
        FlowPayload flow = topologyUnderTest.getAliasedObject(flowAlias);
        flowResponse = northboundService.addFlow(flow);
    }

    @And("^get available bandwidth and maximum speed for flow (.*) and alias them as '(.*)' "
            + "and '(.*)' respectively$")
    public void getAvailableBandwidthAndSpeed(String flowAlias, String bwAlias, String speedAlias) {
        FlowPayload flow = topologyUnderTest.getAliasedObject(flowAlias);
        List<PathNodePayload> flowPath = northboundService.getFlowPath(flow.getId()).getForwardPath();
        List<IslInfoData> allLinks = northboundService.getAllLinks();
        long minBw = Long.MAX_VALUE;
        long minSpeed = Long.MAX_VALUE;

        /*
        Take flow path and all links. Now for every pair in flow path find a link.
        Take minimum available bandwidth and minimum available speed from those links
        (flow's speed and left bandwidth depends on the weakest isl)
        */
        for (int i = 1; i < flowPath.size(); i++) {
            PathNodePayload from = flowPath.get(i - 1);
            PathNodePayload to = flowPath.get(i);
            IslInfoData isl = allLinks.stream().filter(link ->
                    link.getSource().getSwitchId().equals(from.getSwitchId())
                            && link.getDestination().getSwitchId().equals(to.getSwitchId()))
                    .findFirst().orElseThrow(() -> new IllegalStateException(
                            format("Isl from %s to %s not found.", from.getSwitchId(), to.getSwitchId())));
            minBw = Math.min(isl.getAvailableBandwidth(), minBw);
            minSpeed = Math.min(isl.getSpeed(), minSpeed);
        }
        topologyUnderTest.addAlias(bwAlias, minBw);
        topologyUnderTest.addAlias(speedAlias, minSpeed);
    }

    @And("^update flow (.*)$")
    public void updateFlow(String flowAlias) {
        FlowPayload flow = topologyUnderTest.getAliasedObject(flowAlias);
        flowResponse = northboundService.updateFlow(flow.getId(), flow);
    }

    @When("^get info about flow (.*)$")
    public void getInfoAboutFlow(String flowAlias) {
        FlowPayload flow = topologyUnderTest.getAliasedObject(flowAlias);
        flowResponse = northboundService.getFlow(flow.getId());
    }

    @Then("^response flow has bandwidth equal to '(.*)'$")
    public void responseFlowHasBandwidth(String bwAlias) {
        long expectedBw = topologyUnderTest.getAliasedObject(bwAlias);
        assertThat(flowResponse.getMaximumBandwidth(), equalTo(expectedBw));
    }

    @Then("^response flow has bandwidth equal to (\\d+)$")
    public void responseFlowHasBandwidth(long expectedBw) {
        assertThat(flowResponse.getMaximumBandwidth(), equalTo(expectedBw));
    }

    @And("^delete flow (.*)$")
    public void deleteFlow(String flowAlias) {
        FlowPayload flow = topologyUnderTest.getAliasedObject(flowAlias);
        northboundService.deleteFlow(flow.getId());
    }

    @And("^get path of '(.*)' and alias it as '(.*)'$")
    public void getPathAndAlias(String flowAlias, String pathAlias) {
        FlowPayload flow = topologyUnderTest.getAliasedObject(flowAlias);
        topologyUnderTest.addAlias(pathAlias, northboundService.getFlowPath(flow.getId()));
    }

    @And("^(.*) flow's path equals to '(.*)'$")
    public void verifyFlowPath(String flowAlias, String pathAlias) {
        FlowPayload flow = topologyUnderTest.getAliasedObject(flowAlias);
        FlowPathPayload expectedPath = topologyUnderTest.getAliasedObject(pathAlias);
        FlowPathPayload actualPath = northboundService.getFlowPath(flow.getId());
        assertThat(actualPath, equalTo(expectedPath));
    }

    private int getAllowedVlan(Set<FlowPayload> flows, SwitchId switchDpId) {
        RangeSet<Integer> allocatedVlans = TreeRangeSet.create();
        flows.forEach(f -> {
            allocatedVlans.add(Range.singleton(f.getSource().getVlanId()));
            allocatedVlans.add(Range.singleton(f.getDestination().getVlanId()));
        });
        RangeSet<Integer> availableVlansRange = TreeRangeSet.create();
        Switch theSwitch = topologyDefinition.getSwitches().stream()
                .filter(sw -> sw.getDpId().equals(switchDpId)).findFirst()
                .orElseThrow(() -> new IllegalStateException(format("Switch %s not found.", switchDpId)));
        theSwitch.getOutPorts().forEach(port -> availableVlansRange.addAll(port.getVlanRange()));
        availableVlansRange.removeAll(allocatedVlans);
        return availableVlansRange.asRanges().stream()
                .flatMap(range -> ContiguousSet.create(range, DiscreteDomain.integers()).stream())
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        format("Couldn't found available Vlan for switch %s.", switchDpId)));
    }

    private void eachFlowIsUp(Set<FlowPayload> flows) {
        for (FlowPayload flow : flows) {
            FlowIdStatusPayload status = Failsafe.with(retryPolicy()
                    .handleResultIf(p -> p == null || ((FlowIdStatusPayload) p).getStatus() != FlowState.UP))
                    .get(() -> northboundService.getFlowStatus(flow.getId()));

            assertNotNull(format("The flow status for '%s' can't be retrived from Northbound.", flow.getId()), status);
            assertThat(format("The flow '%s' in Northbound is different from defined.", flow.getId()),
                    status, hasProperty("id", equalTo(flow.getId())));
            assertThat(format("The flow '%s' has wrong status in Northbound.", flow.getId()),
                    status, hasProperty("status", equalTo(FlowState.UP)));
        }
    }

    private <T> RetryPolicy<T> retryPolicy() {
        return new RetryPolicy<T>()
                .withDelay(Duration.ofSeconds(2))
                .withMaxRetries(10);
    }

    private String getTimestamp() {
        return new SimpleDateFormat("ddMMMHHmm", Locale.US).format(new Date());
    }
}
