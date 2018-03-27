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
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.google.common.annotations.VisibleForTesting;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import cucumber.api.java8.En;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.logging.log4j.util.Strings;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition;
import org.openkilda.atdd.staging.service.floodlight.FloodlightService;
import org.openkilda.atdd.staging.service.northbound.NorthboundService;
import org.openkilda.atdd.staging.service.topology.TopologyEngineService;
import org.openkilda.atdd.staging.service.traffexam.FlowNotApplicableException;
import org.openkilda.atdd.staging.service.traffexam.OperationalException;
import org.openkilda.atdd.staging.service.traffexam.TraffExamService;
import org.openkilda.atdd.staging.service.traffexam.model.Exam;
import org.openkilda.atdd.staging.service.traffexam.model.ExamReport;
import org.openkilda.atdd.staging.service.traffexam.model.FlowBidirectionalExam;
import org.openkilda.atdd.staging.steps.helpers.FlowSetBuilder;
import org.openkilda.atdd.staging.steps.helpers.FlowTrafficExamBuilder;
import org.openkilda.atdd.staging.steps.helpers.TopologyChecker;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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

    @Autowired
    private TraffExamService traffExam;

    @VisibleForTesting
    Set<FlowPayload> flows = emptySet();

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
        FlowSetBuilder builder = new FlowSetBuilder();

        final List<TopologyDefinition.Switch> switches = topologyDefinition.getActiveSwitches();
        // check each combination of active switches for a path between them and create a flow definition if the path exists
        switches.forEach(srcSwitch ->
                switches.forEach(dstSwitch -> {
                    if(srcSwitch.getDpId().equals(dstSwitch.getDpId())) {
                        return;
                    }

                    List<PathInfoData> paths =
                            topologyEngineService.getPaths(srcSwitch.getDpId(), dstSwitch.getDpId());
                    if (!paths.isEmpty()) {
                        String flowId = format("%s-%s", srcSwitch.getName(), dstSwitch.getName());
                        builder.addFlowInUniqueVlan(flowId, srcSwitch, dstSwitch);
                    }
                })
        );

        flows = builder.getFlows();
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
            assertThat(result, reflectEquals(flow, "lastUpdated"));
            assertThat(result, hasProperty("lastUpdated", notNullValue()));
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
                    .abortIf(Objects::nonNull))
                    .get(() -> topologyEngineService.getFlow(expectedFlow.getFlowId()));

            assertNotNull(format("The flow '%s' is missing.", expectedFlow.getFlowId()), flowPair);
            assertThat(format("The flow '%s' is different.", expectedFlow.getFlowId()),
                    flowPair.getLeft(), is(equalTo(expectedFlow)));
        }
    }

    @And("^each flow is in UP state$")
    public void eachFlowIsInUPState() {
        for (FlowPayload flow : flows) {
            FlowIdStatusPayload status = Failsafe.with(retryPolicy
                    .abortIf(p -> p != null && FlowState.UP == ((FlowIdStatusPayload) p).getStatus()))
                    .get(() -> northboundService.getFlowStatus(flow.getId()));

            assertNotNull(status);
            assertThat(format("The flow status for '%s' can't be retrived.", flow.getId()),
                    status, hasProperty("id", equalTo(flow.getId())));
            assertThat(format("The flow '%s' has wrong status.", flow.getId()),
                    status, hasProperty("status", equalTo(FlowState.UP)));
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
        FlowTrafficExamBuilder examBuilder = new FlowTrafficExamBuilder(topologyDefinition, traffExam);
        List<Exam> singleExams = new LinkedList<>();
        List<FlowBidirectionalExam> examsInProgress = new LinkedList<>();

        for (FlowPayload flow : flows) {
            try {
                FlowBidirectionalExam flowExam = examBuilder.makeBidirectionalExam(flow);

                List<Exam> createdExams = new ArrayList<>(2);
                try {
                    for (Exam current : flowExam.getExamPair()) {
                        createdExams.add(traffExam.startExam(current));
                    }
                    examsInProgress.add(flowExam);
                } catch (OperationalException e) {
                    LOGGER.warn("Unable to setup traffic exam (at least one) for flow {} - {}", flow.getId(), e);
                    singleExams.addAll(createdExams);
                    break;
                }
            } catch (FlowNotApplicableException e) {
                LOGGER.info(String.format("%s. Skip traffic exam.", e));
            }
        }

        LOGGER.info(String.format(
                "%d of %d flow's traffic examination have been started", examsInProgress.size(),
                flows.size()));

        if (0 < singleExams.size()) {
            LOGGER.warn(String.format("Kill %d incomplete(one direction) flow traffic exams.", singleExams.size()));
            for (Exam current : singleExams) {
                traffExam.stopExam(current);
            }
        }

        boolean issues = false;
        for (FlowBidirectionalExam current : examsInProgress) {
            List<Boolean> isError = new ArrayList<>(2);
            List<Boolean> isTraffic = new ArrayList<>(2);
            List<Boolean> isTrafficLose = new ArrayList<>(2);
            List<Boolean> isBandwidthMatch = new ArrayList<>(2);

            FlowPayload flow = current.getFlow();

            ExamReport forward = null;
            ExamReport reverse = null;
            for (ExamReport report : traffExam.waitExam(current.getExamPair())) {
                if (forward == null) {
                    forward = report;
                } else {
                    reverse = report;
                }

                isError.add(report.isError());
                isTraffic.add(report.isTraffic());
                isTrafficLose.add(report.isTrafficLose());

                Double bandwidthLimit = bandwidth * .95;
                isBandwidthMatch.add(report.getBandwidth().getKbps() < bandwidthLimit);
            }

            if (isError.stream().anyMatch(value -> value)) {
                List<String> errors = new ArrayList<>(2);
                if (forward != null) {
                    errors.addAll(
                            forward.getErrors().stream().map(
                                    message -> String.format("forward:%s", message)
                            ).collect(toList()));
                }
                if (reverse != null) {
                    errors.addAll(
                            reverse.getErrors().stream().map(
                                    message -> String.format("reverse:%s", message)
                            ).collect(toList()));
                }

                LOGGER.error(String.format(
                        "Flow's %s traffic exam ends with error\n%s",
                        flow.getId(),
                        Strings.join(errors, '\n')));
                issues = true;
            }

            if (! isTraffic.stream().allMatch(value -> value)) {
                LOGGER.error(String.format("Flow's %s traffic is missing", flow.getId()));
                issues = true;
            }

            if (isTrafficLose.stream().anyMatch(value -> value)) {
                LOGGER.warn(String.format("Flow %s is loosing packages", flow.getId()));
            }

            if (! isBandwidthMatch.stream().allMatch(value -> value)) {
                LOGGER.error("Flow %s does not provide requested bandwidth", flow.getId());
                issues = true;
            }
        }

        assertFalse("There is an issues with traffic on installed flows.", issues);
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
