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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.commons.collections4.ListValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition;
import org.openkilda.atdd.staging.service.floodlight.FloodlightService;
import org.openkilda.atdd.staging.service.floodlight.model.MeterEntry;
import org.openkilda.atdd.staging.service.floodlight.model.MetersEntriesMap;
import org.openkilda.atdd.staging.service.flowmanager.FlowManager;
import org.openkilda.atdd.staging.service.northbound.NorthboundService;
import org.openkilda.atdd.staging.service.topology.TopologyEngineService;
import org.openkilda.atdd.staging.service.traffexam.FlowNotApplicableException;
import org.openkilda.atdd.staging.service.traffexam.OperationalException;
import org.openkilda.atdd.staging.service.traffexam.TraffExamService;
import org.openkilda.atdd.staging.service.traffexam.model.Exam;
import org.openkilda.atdd.staging.service.traffexam.model.ExamReport;
import org.openkilda.atdd.staging.service.traffexam.model.ExamResources;
import org.openkilda.atdd.staging.steps.helpers.FlowTrafficExamBuilder;
import org.openkilda.atdd.staging.steps.helpers.TopologyUnderTest;
import org.openkilda.atdd.staging.tools.SoftAssertions;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.northbound.dto.flows.FlowValidationDto;

import com.google.common.annotations.VisibleForTesting;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import cucumber.api.java8.En;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

public class FlowCrudSteps implements En {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlowCrudSteps.class);

    @Autowired
    private NorthboundService northboundService;

    @Autowired
    private FloodlightService floodlightService;

    @Autowired
    private TopologyEngineService topologyEngineService;

    @Autowired
    private TopologyDefinition topologyDefinition;

    @Autowired
    private TraffExamService traffExam;

    @Autowired
    private FlowManager flowManager;

    @Autowired
    @Qualifier("topologyEngineRetryPolicy")
    private RetryPolicy retryPolicy;

    @VisibleForTesting
    Set<FlowPayload> flows;

    @Autowired
    @Qualifier("topologyUnderTest")
    private TopologyUnderTest topologyUnderTest;

    @Given("^flows defined over active switches in the reference topology$")
    public void defineFlowsOverActiveSwitches() {
        flows = flowManager.allActiveSwitchesFlows();
    }

    @Given("^flows defined over active traffgens in the reference topology$")
    public void defineFlowsOverActiveTraffgens() {
        flows = flowManager.allActiveTraffgenFlows();
    }

    @Given("Create (\\d+) flows? with A Switch used and at least (\\d+) alternate paths? between source and "
            + "destination switch and (\\d+) bandwidth")
    public void flowsWithAlternatePaths(int flowsAmount, int alternatePaths, int bw) {
        Map<FlowPayload, List<TopologyDefinition.Isl>> flowIsls = topologyUnderTest.getFlowIsls();
        flowIsls.putAll(flowManager.createFlowsWithASwitch(flowsAmount, alternatePaths, bw));
        //temporary resaving flows before refactoring all methods to work with topologyUnderTest
        flows = flowIsls.keySet();
    }

    @And("Create defined flows?")
    public void createFlows() {
        eachFlowIsCreatedAndStoredInTopologyEngine();
        eachFlowIsInUpState();
    }

    @And("^each flow has unique flow_id$")
    public void setUniqueFlowIdToEachFlow() {
        flows.forEach(flow -> flow.setId(format("%s-%s", flow.getId(), UUID.randomUUID().toString())));
    }

    @And("^each flow has flow_id with (.*) prefix$")
    public void buildFlowIdToEachFlow(String flowIdPrefix) {
        flows.forEach(flow -> flow.setId(format("%s-%s", flowIdPrefix, flow.getId())));
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
                    reflectEquals(flow, "lastUpdated", "status"));
            assertThat(format("Flow status for '%s' was not set to '%s'. Received status: '%s'",
                    flow.getId(), FlowState.ALLOCATED, result.getStatus()),
                    result.getStatus(), equalTo(FlowState.ALLOCATED.toString()));
            assertThat(format("The flow '%s' is missing lastUpdated field", flow.getId()), result,
                    hasProperty("lastUpdated", notNullValue()));
        }
    }

    @Then("^each flow is created and stored in TopologyEngine$")
    public void eachFlowIsCreatedAndStoredInTopologyEngine() {
        List<Flow> expextedFlows = flows.stream()
                .map(flow -> new Flow(flow.getId(),
                        flow.getMaximumBandwidth(),
                        flow.isIgnoreBandwidth(), 0,
                        flow.getDescription(), null,
                        flow.getSource().getSwitchDpId(),
                        flow.getDestination().getSwitchDpId(),
                        flow.getSource().getPortId(),
                        flow.getDestination().getPortId(),
                        flow.getSource().getVlanId(),
                        flow.getDestination().getVlanId(),
                        0, 0, null, null))
                .collect(toList());

        for (Flow expectedFlow : expextedFlows) {
            ImmutablePair<Flow, Flow> flowPair = Failsafe.with(retryPolicy
                    .retryWhen(null))
                    .get(() -> topologyEngineService.getFlow(expectedFlow.getFlowId()));

            assertNotNull(format("The flow '%s' is missing in TopologyEngine.", expectedFlow.getFlowId()), flowPair);
            assertThat(format("The flow '%s' in TopologyEngine is different from defined.", expectedFlow.getFlowId()),
                    flowPair.getLeft(), is(equalTo(expectedFlow)));
        }
    }

    @And("^(?:each )?flow is in UP state$")
    public void eachFlowIsInUpState() {
        for (FlowPayload flow : flows) {
            FlowIdStatusPayload status = Failsafe.with(retryPolicy
                    .retryIf(p -> !(p instanceof FlowIdStatusPayload)
                            || ((FlowIdStatusPayload) p).getStatus() != FlowState.UP))
                    .get(() -> northboundService.getFlowStatus(flow.getId()));

            assertNotNull(format("The flow status for '%s' can't be retrived from Northbound.", flow.getId()), status);
            assertThat(format("The flow '%s' in Northbound is different from defined.", flow.getId()),
                    status, hasProperty("id", equalTo(flow.getId())));
            assertThat(format("The flow '%s' has wrong status in Northbound.", flow.getId()),
                    status, hasProperty("status", equalTo(FlowState.UP)));
        }
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

    @And("^(?:each )?flow has traffic going with bandwidth not less than (\\d+) and not greater than (\\d+)$")
    public void eachFlowHasTrafficGoingWithBandwidthNotLessThan(int bandwidthLowLimit, int bandwidthHighLimit)
            throws Throwable {
        List<Exam> examsInProgress = buildAndStartTraffExams();
        SoftAssertions softAssertions = new SoftAssertions();
        List<String> issues = new ArrayList<>();

        for (Exam exam : examsInProgress) {
            String flowId = exam.getFlow().getId();

            ExamReport report = traffExam.waitExam(exam);
            softAssertions.checkThat(format("The flow %s had errors: %s",
                    flowId, report.getErrors()), report.hasError(), is(false));
            softAssertions.checkThat(format("The flow %s had no traffic.", flowId),
                    report.hasTraffic(), is(true));
            softAssertions.checkThat(format("The flow %s had unexpected bandwidth: %s", flowId, report.getBandwidth()),
                    report.getBandwidth().getKbps() > bandwidthLowLimit
                            && report.getBandwidth().getKbps() < bandwidthHighLimit, is(true));
        }
        softAssertions.verify();
    }

    @And("^each flow has no traffic$")
    public void eachFlowHasNoTraffic() {
        List<Exam> examsInProgress = buildAndStartTraffExams();

        List<ExamReport> hasTraffic = examsInProgress.stream()
                .map(exam -> traffExam.waitExam(exam))
                .filter(ExamReport::hasTraffic)
                .collect(toList());

        assertThat("Detected unexpected traffic.", hasTraffic, empty());
    }

    private List<Exam> buildAndStartTraffExams() {
        FlowTrafficExamBuilder examBuilder = new FlowTrafficExamBuilder(topologyDefinition, traffExam);

        List<Exam> result = flows.stream()
                .flatMap(flow -> {
                    try {
                        // Instruct TraffGen to produce traffic with maximum bandwidth.
                        return Stream.of(examBuilder.buildExam(flow, 0));
                    } catch (FlowNotApplicableException ex) {
                        LOGGER.info("Skip traffic exam. {}", ex.getMessage());
                        return Stream.empty();
                    }
                })
                .peek(exam -> {
                    try {
                        ExamResources resources = traffExam.startExam(exam);
                        exam.setResources(resources);
                    } catch (OperationalException ex) {
                        LOGGER.error("Unable to start traffic exam for {}.", exam.getFlow(), ex);
                        fail(ex.getMessage());
                    }
                })
                .collect(toList());

        LOGGER.info("{} of {} flow's traffic examination have been started", result.size(), flows.size());

        return result;
    }

    @Then("^each flow can be updated with (\\d+) max bandwidth$")
    public void eachFlowCanBeUpdatedWithBandwidth(int bandwidth) {
        List<String> updatedFlowIds = new ArrayList<>();

        for (FlowPayload flow : flows) {
            flow.setMaximumBandwidth(bandwidth);

            FlowPayload result = northboundService.updateFlow(flow.getId(), flow);
            if (result != null) {
                updatedFlowIds.add(result.getId());
            }
        }

        assertThat("Updated flows in Northbound don't match expected", updatedFlowIds, containsInAnyOrder(
                flows.stream().map(flow -> equalTo(flow.getId())).collect(toList())));
    }

    @And("^each flow has meters installed with (\\d+) max bandwidth$")
    public void eachFlowHasMetersInstalledWithBandwidth(long bandwidth) {
        for (FlowPayload flow : flows) {
            ImmutablePair<Flow, Flow> flowPair = topologyEngineService.getFlow(flow.getId());

            try {
                MetersEntriesMap forwardSwitchMeters = floodlightService
                        .getMeters(flowPair.getLeft().getSourceSwitch());
                int forwardMeterId = flowPair.getLeft().getMeterId();
                assertThat(forwardSwitchMeters, hasKey(forwardMeterId));
                MeterEntry forwardMeter = forwardSwitchMeters.get(forwardMeterId);
                assertThat(forwardMeter.getEntries(), contains(hasProperty("rate", equalTo(bandwidth))));

                MetersEntriesMap reverseSwitchMeters = floodlightService
                        .getMeters(flowPair.getRight().getSourceSwitch());
                int reverseMeterId = flowPair.getRight().getMeterId();
                assertThat(reverseSwitchMeters, hasKey(reverseMeterId));
                MeterEntry reverseMeter = reverseSwitchMeters.get(reverseMeterId);
                assertThat(reverseMeter.getEntries(), contains(hasProperty("rate", equalTo(bandwidth))));
            } catch (UnsupportedOperationException ex) {
                //TODO: a workaround for not implemented dumpMeters on OF_12 switches.
                LOGGER.warn("Switch doesn't support dumping of meters. {}", ex.getMessage());
            }
        }
    }

    @And("^all active switches have no excessive meters installed$")
    public void noExcessiveMetersInstalledOnActiveSwitches() {
        ListValuedMap<String, Integer> switchMeters = new ArrayListValuedHashMap<>();
        for (FlowPayload flow : flows) {
            ImmutablePair<Flow, Flow> flowPair = topologyEngineService.getFlow(flow.getId());
            if (flowPair != null) {
                switchMeters.put(flowPair.getLeft().getSourceSwitch(), flowPair.getLeft().getMeterId());
                switchMeters.put(flowPair.getRight().getSourceSwitch(), flowPair.getRight().getMeterId());
            }
        }

        List<TopologyDefinition.Switch> switches = topologyDefinition.getActiveSwitches();
        switches.forEach(sw -> {
            List<Integer> expectedMeters = switchMeters.get(sw.getDpId());
            try {
                List<Integer> actualMeters = floodlightService.getMeters(sw.getDpId()).values().stream()
                        .map(MeterEntry::getMeterId)
                        .collect(toList());

                if (!expectedMeters.isEmpty() || !actualMeters.isEmpty()) {
                    assertThat(format("Meters of switch %s don't match expected.", sw), actualMeters,
                            containsInAnyOrder(expectedMeters));
                }

            } catch (UnsupportedOperationException ex) {
                //TODO: a workaround for not implemented dumpMeters on OF_12 switches.
                LOGGER.warn("Switch doesn't support dumping of meters. {}", ex.getMessage());
            }
        });
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
            FlowPayload result = Failsafe.with(retryPolicy
                    .abortWhen(null)
                    .retryIf(Objects::nonNull))
                    .get(() -> northboundService.getFlow(flow.getId()));

            assertNull(format("The flow '%s' exists.", flow.getId()), result);
        }
    }

    @And("^each flow can not be read from TopologyEngine$")
    public void eachFlowCanNotBeReadFromTopologyEngine() {
        for (FlowPayload flow : flows) {
            ImmutablePair<Flow, Flow> result = Failsafe.with(retryPolicy
                    .abortWhen(null)
                    .retryIf(Objects::nonNull))
                    .get(() -> topologyEngineService.getFlow(flow.getId()));

            assertNull(format("The flow '%s' exists.", flow.getId()), result);
        }
    }
}
