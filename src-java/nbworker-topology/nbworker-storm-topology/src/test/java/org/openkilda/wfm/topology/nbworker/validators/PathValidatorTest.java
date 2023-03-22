/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.nbworker.validators;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.openkilda.model.FlowEncapsulationType.TRANSIT_VLAN;
import static org.openkilda.model.FlowEncapsulationType.VXLAN;
import static org.openkilda.model.PathComputationStrategy.COST;
import static org.openkilda.model.PathComputationStrategy.COST_AND_AVAILABLE_BANDWIDTH;
import static org.openkilda.model.PathComputationStrategy.LATENCY;
import static org.openkilda.model.PathComputationStrategy.MAX_LATENCY;

import org.openkilda.config.provider.PropertiesBasedConfigurationProvider;
import org.openkilda.messaging.command.flow.PathValidateRequest;
import org.openkilda.messaging.info.network.PathValidationResult;
import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.messaging.payload.network.PathValidationPayload;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.Isl;
import org.openkilda.model.IslStatus;
import org.openkilda.model.KildaConfiguration;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.SwitchStatus;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.pce.PathComputerConfig;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.topology.nbworker.services.PathsService;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class PathValidatorTest extends InMemoryGraphBasedTest {
    private static final SwitchId SWITCH_ID_0 = new SwitchId(0);
    private static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    private static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    private static final SwitchId SWITCH_ID_3 = new SwitchId(3);
    private static final SwitchId SWITCH_ID_4 = new SwitchId(4);
    private static SwitchRepository switchRepository;
    private static SwitchPropertiesRepository switchPropertiesRepository;
    private static IslRepository islRepository;
    private static FlowRepository flowRepository;
    private static PathsService pathsService;

    private boolean isSetupDone;

    @BeforeClass
    public static void setUpOnce() {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        switchRepository = repositoryFactory.createSwitchRepository();
        switchPropertiesRepository = repositoryFactory.createSwitchPropertiesRepository();
        islRepository = repositoryFactory.createIslRepository();
        flowRepository = repositoryFactory.createFlowRepository();
        PathComputerConfig pathComputerConfig = new PropertiesBasedConfigurationProvider()
                .getConfiguration(PathComputerConfig.class);
        pathsService = new PathsService(repositoryFactory, pathComputerConfig);
        KildaConfiguration kildaConfiguration = repositoryFactory.createKildaConfigurationRepository().getOrDefault();
        kildaConfiguration.setFlowEncapsulationType(TRANSIT_VLAN);
    }

    @Before
    public void createTestTopology() {
        if (!isSetupDone) {
            Switch switch0 = Switch.builder().switchId(SWITCH_ID_0).status(SwitchStatus.ACTIVE).build();
            Switch switchA = Switch.builder().switchId(SWITCH_ID_1).status(SwitchStatus.ACTIVE).build();
            Switch switchB = Switch.builder().switchId(SWITCH_ID_2).status(SwitchStatus.ACTIVE).build();
            Switch switchC = Switch.builder().switchId(SWITCH_ID_4).status(SwitchStatus.ACTIVE).build();
            Switch switchTransit = Switch.builder().switchId(SWITCH_ID_3).status(SwitchStatus.ACTIVE).build();

            switchRepository.add(switch0);
            switchRepository.add(switchA);
            switchRepository.add(switchB);
            switchRepository.add(switchC);
            switchRepository.add(switchTransit);

            switchPropertiesRepository.add(SwitchProperties.builder()
                    .switchObj(switch0)
                    .supportedTransitEncapsulation(Sets.newHashSet(TRANSIT_VLAN))
                    .build());
            switchPropertiesRepository.add(SwitchProperties.builder()
                    .switchObj(switchA)
                    .supportedTransitEncapsulation(Sets.newHashSet(TRANSIT_VLAN))
                    .build());
            switchPropertiesRepository.add(SwitchProperties.builder()
                    .switchObj(switchB)
                    .supportedTransitEncapsulation(Sets.newHashSet(TRANSIT_VLAN))
                    .build());
            switchPropertiesRepository.add(SwitchProperties.builder()
                    .switchObj(switchTransit)
                    .supportedTransitEncapsulation(Sets.newHashSet(TRANSIT_VLAN))
                    .build());
            switchPropertiesRepository.add(SwitchProperties.builder()
                    .switchObj(switchC)
                    .supportedTransitEncapsulation(Sets.newHashSet(VXLAN))
                    .build());

            createOneWayIsl(switchA, 6, switchTransit, 6, 10, 2_000_000, 10_000, IslStatus.ACTIVE);
            createOneWayIsl(switchTransit, 6, switchA, 6, 10, 2_000_000, 10_000, IslStatus.ACTIVE);

            createOneWayIsl(switchB, 7, switchTransit, 7, 15, 3_000_000, 20_000, IslStatus.ACTIVE);
            createOneWayIsl(switchTransit, 7, switchB, 7, 15, 3_000_000, 20_000, IslStatus.ACTIVE);

            createOneWayIsl(switchA, 12, switch0, 12, 10, 2_000_000, 10_000, IslStatus.INACTIVE);
            createOneWayIsl(switch0, 12, switchA, 12, 10, 2_000_000, 10_000, IslStatus.INACTIVE);

            isSetupDone = true;
        }
    }

    @Test
    public void whenInactiveLink_validatePathReturnsErrorResponseTest() {
        List<PathNodePayload> nodes = new LinkedList<>();
        nodes.add(new PathNodePayload(SWITCH_ID_1, null, 12));
        nodes.add(new PathNodePayload(SWITCH_ID_0, 12, null));

        PathValidateRequest request = new PathValidateRequest(PathValidationPayload.builder()
                .nodes(nodes)
                .bandwidth(0L)
                .latencyMs(0L)
                .latencyTier2ms(0L)
                .build());
        List<PathValidationResult> responses = pathsService.validatePath(request);

        assertFalse(responses.isEmpty());
        assertFalse(responses.get(0).getIsValid());
        Set<String> actualErrorMessages = Sets.newHashSet(responses.get(0).getErrors());
        Set<String> expectedErrorMessages = Sets.newHashSet(
                "The ISL is not in ACTIVE state between end points: switch 00:00:00:00:00:00:00:00 port 12 and switch"
                        + " 00:00:00:00:00:00:00:01 port 12.",
                "The ISL is not in ACTIVE state between end points: switch 00:00:00:00:00:00:00:01 port 12 and switch"
                        + " 00:00:00:00:00:00:00:00 port 12."
        );

        assertEquals(expectedErrorMessages, actualErrorMessages);
    }

    @Test
    public void whenValidPath_validatePathReturnsValidResponseTest() {
        List<PathNodePayload> nodes = new LinkedList<>();
        nodes.add(new PathNodePayload(SWITCH_ID_1, null, 6));
        nodes.add(new PathNodePayload(SWITCH_ID_3, 6, 7));
        nodes.add(new PathNodePayload(SWITCH_ID_2, 7, null));
        PathValidateRequest request = new PathValidateRequest(PathValidationPayload.builder()
                .nodes(nodes)
                .bandwidth(0L)
                .latencyMs(0L)
                .latencyTier2ms(0L)
                .build());
        List<PathValidationResult> responses = pathsService.validatePath(request);

        assertFalse(responses.isEmpty());
        assertTrue(responses.get(0).getIsValid());
    }

    @Test
    public void whenValidPathButNotEnoughBandwidth_validatePathReturnsErrorResponseTest() {
        List<PathNodePayload> nodes = new LinkedList<>();
        nodes.add(new PathNodePayload(SWITCH_ID_1, null, 6));
        nodes.add(new PathNodePayload(SWITCH_ID_3, 6, null));
        PathValidateRequest request = new PathValidateRequest(PathValidationPayload.builder()
                .nodes(nodes)
                .bandwidth(1000000000L)
                .latencyMs(0L)
                .latencyTier2ms(0L)
                .build());
        List<PathValidationResult> responses = pathsService.validatePath(request);

        assertFalse(responses.isEmpty());
        assertFalse(responses.get(0).getIsValid());
        assertEquals("There must be 2 errors: forward and reverse paths",
                2, responses.get(0).getErrors().size());
        Collections.sort(responses.get(0).getErrors());
        assertEquals(responses.get(0).getErrors().get(0),
                "There is not enough bandwidth between end points: switch 00:00:00:00:00:00:00:01 port 6 and "
                        + "switch 00:00:00:00:00:00:00:03 port 6 (forward path). Requested bandwidth 1000000000,"
                        + " but the link supports 10000.");
        assertEquals(responses.get(0).getErrors().get(1),
                "There is not enough bandwidth between end points: switch 00:00:00:00:00:00:00:03 port 6 and "
                        + "switch 00:00:00:00:00:00:00:01 port 6 (reverse path). Requested bandwidth 1000000000,"
                        + " but the link supports 10000.");
    }

    @Test
    public void whenNoSwitchOnPath_validatePathReturnsErrorResponseTest() {
        List<PathNodePayload> nodes = new LinkedList<>();
        nodes.add(new PathNodePayload(new SwitchId("01:01:01:01"), null, 6));
        nodes.add(new PathNodePayload(new SwitchId("01:01:01:02"), 6, null));
        PathValidateRequest request = new PathValidateRequest(PathValidationPayload.builder()
                .nodes(nodes)
                .bandwidth(1000000000L)
                .latencyMs(0L)
                .latencyTier2ms(0L)
                .build());
        List<PathValidationResult> responses = pathsService.validatePath(request);

        assertFalse(responses.isEmpty());
        assertFalse(responses.get(0).getIsValid());

        Set<String> actualErrorMessages = Sets.newHashSet(responses.get(0).getErrors());
        Set<String> expectedErrorMessages = Sets.newHashSet(
                "The following switch has not been found: 00:00:00:00:01:01:01:01.",
                "The following switch properties have not been found: 00:00:00:00:01:01:01:01.",
                "The following switch has not been found: 00:00:00:00:01:01:01:02.",
                "The following switch properties have not been found: 00:00:00:00:01:01:01:02.",
                "There is no ISL between end points: switch 00:00:00:00:01:01:01:01 port 6"
                        + " and switch 00:00:00:00:01:01:01:02 port 6.",
                "There is no ISL between end points: switch 00:00:00:00:01:01:01:02 port 6"
                        + " and switch 00:00:00:00:01:01:01:01 port 6."
        );

        assertEquals(expectedErrorMessages, actualErrorMessages);
    }

    @Test
    public void whenValidPathButTooLowLatency_andLatencyStrategy_validatePathReturnsErrorResponseTest() {
        List<PathNodePayload> nodes = new LinkedList<>();
        nodes.add(new PathNodePayload(SWITCH_ID_1, null, 6));
        nodes.add(new PathNodePayload(SWITCH_ID_3, 6, 7));
        nodes.add(new PathNodePayload(SWITCH_ID_2, 7, null));
        PathValidateRequest request = new PathValidateRequest(PathValidationPayload.builder()
                .nodes(nodes)
                .bandwidth(0L)
                .latencyMs(1L)
                .latencyTier2ms(0L)
                .pathComputationStrategy(LATENCY)
                .build());
        List<PathValidationResult> responses = pathsService.validatePath(request);

        assertFalse(responses.isEmpty());
        assertFalse(responses.get(0).getIsValid());
        assertFalse(responses.get(0).getErrors().isEmpty());

        Set<String> expectedErrorMessages = Sets.newHashSet(
                "Requested latency is too low on the path between: switch 00:00:00:00:00:00:00:01 and"
                        + " switch 00:00:00:00:00:00:00:02. Requested 1 ms, but the sum on the path is 5 ms.",
                "Requested latency is too low on the path between: switch 00:00:00:00:00:00:00:02 and"
                        +  " switch 00:00:00:00:00:00:00:01. Requested 1 ms, but the sum on the path is 5 ms.");

        Set<String> actualErrorMessages = Sets.newHashSet(responses.get(0).getErrors());
        assertEquals(expectedErrorMessages, actualErrorMessages);
    }

    @Test
    public void whenValidPathButTooLowLatency_andMaxLatencyStrategy_validatePathReturnsErrorResponseTest() {
        List<PathNodePayload> nodes = new LinkedList<>();
        nodes.add(new PathNodePayload(SWITCH_ID_1, null, 6));
        nodes.add(new PathNodePayload(SWITCH_ID_3, 6, 7));
        nodes.add(new PathNodePayload(SWITCH_ID_2, 7, null));
        PathValidateRequest request = new PathValidateRequest(PathValidationPayload.builder()
                .nodes(nodes)
                .bandwidth(0L)
                .latencyMs(1L)
                .latencyTier2ms(0L)
                .pathComputationStrategy(MAX_LATENCY)
                .build());
        List<PathValidationResult> responses = pathsService.validatePath(request);

        assertFalse(responses.isEmpty());
        assertFalse(responses.get(0).getIsValid());
        assertFalse(responses.get(0).getErrors().isEmpty());

        Set<String> expectedErrorMessages = Sets.newHashSet(
                "Requested latency is too low on the path between: switch 00:00:00:00:00:00:00:01 and"
                        + " switch 00:00:00:00:00:00:00:02. Requested 1 ms, but the sum on the path is 5 ms.",
                "Requested latency is too low on the path between: switch 00:00:00:00:00:00:00:02 and"
                        +  " switch 00:00:00:00:00:00:00:01. Requested 1 ms, but the sum on the path is 5 ms.");

        Set<String> actualErrorMessages = Sets.newHashSet(responses.get(0).getErrors());
        assertEquals(expectedErrorMessages, actualErrorMessages);
    }

    @Test
    public void whenValidPathButTooLowLatencyTier2_andLatencyStrategy_validatePathReturnsErrorResponseTest() {
        List<PathNodePayload> nodes = new LinkedList<>();
        nodes.add(new PathNodePayload(SWITCH_ID_1, null, 6));
        nodes.add(new PathNodePayload(SWITCH_ID_3, 6, 7));
        nodes.add(new PathNodePayload(SWITCH_ID_2, 7, null));
        PathValidateRequest request = new PathValidateRequest(PathValidationPayload.builder()
                .nodes(nodes)
                .bandwidth(0L)
                .latencyMs(10000000000L)
                .latencyTier2ms(1L)
                .pathComputationStrategy(LATENCY)
                .build());
        List<PathValidationResult> responses = pathsService.validatePath(request);

        assertFalse(responses.isEmpty());
        assertFalse(responses.get(0).getIsValid());
        assertFalse(responses.get(0).getErrors().isEmpty());

        Set<String> expectedErrorMessages = Sets.newHashSet(
                "Requested latency tier 2 is too low on the path between: switch 00:00:00:00:00:00:00:01 and"
                + " switch 00:00:00:00:00:00:00:02. Requested 1 ms, but the sum on the path is 5 ms.",
                "Requested latency tier 2 is too low on the path between: switch 00:00:00:00:00:00:00:02 and"
                +  " switch 00:00:00:00:00:00:00:01. Requested 1 ms, but the sum on the path is 5 ms.");

        Set<String> actualErrors = Sets.newHashSet(responses.get(0).getErrors());
        assertEquals(expectedErrorMessages, actualErrors);
    }

    @Test
    public void whenSwitchDoesNotSupportEncapsulationType_validatePathReturnsErrorResponseTest() {
        List<PathNodePayload> nodes = new LinkedList<>();
        nodes.add(new PathNodePayload(SWITCH_ID_1, null, 6));
        nodes.add(new PathNodePayload(SWITCH_ID_3, 6, 7));

        PathValidateRequest request = new PathValidateRequest(PathValidationPayload.builder()
                .nodes(nodes)
                .flowEncapsulationType(org.openkilda.messaging.payload.flow.FlowEncapsulationType.VXLAN)
                .build());
        List<PathValidationResult> responses = pathsService.validatePath(request);

        assertFalse(responses.isEmpty());
        assertFalse(responses.get(0).getIsValid());
        assertFalse(responses.get(0).getErrors().isEmpty());
        Set<String> expectedErrors = Sets.newHashSet(
                "The switch 00:00:00:00:00:00:00:01 doesn't support the encapsulation type VXLAN.",
                "The switch 00:00:00:00:00:00:00:03 doesn't support the encapsulation type VXLAN.");
        Set<String> actualErrors = Sets.newHashSet(responses.get(0).getErrors());
        assertEquals(expectedErrors, actualErrors);
    }

    @Test
    public void whenNoEncapsulationTypeInRequest_useDefaultEncapsulationType_validatePathReturnsErrorResponseTest() {
        List<PathNodePayload> nodes = new LinkedList<>();
        nodes.add(new PathNodePayload(SWITCH_ID_1, null, 6));
        nodes.add(new PathNodePayload(SWITCH_ID_4, 6, 7));

        PathValidateRequest request = new PathValidateRequest(PathValidationPayload.builder()
                .nodes(nodes)
                .build());
        List<PathValidationResult> responses = pathsService.validatePath(request);

        assertFalse(responses.isEmpty());
        assertFalse(responses.get(0).getIsValid());
        assertFalse(responses.get(0).getErrors().isEmpty());
        Set<String> expectedErrors = Sets.newHashSet(
                "The switch 00:00:00:00:00:00:00:04 doesn't support the encapsulation type TRANSIT_VLAN.",
                "There is no ISL between end points: switch 00:00:00:00:00:00:00:01 port 6 and switch "
                        + "00:00:00:00:00:00:00:04 port 6.",
                "There is no ISL between end points: switch 00:00:00:00:00:00:00:04 port 6 and switch "
                        + "00:00:00:00:00:00:00:01 port 6.");
        Set<String> actualErrors = Sets.newHashSet(responses.get(0).getErrors());
        assertEquals(expectedErrors, actualErrors);
    }

    @Test
    public void whenNoLinkBetweenSwitches_validatePathReturnsErrorResponseTest() {
        List<PathNodePayload> nodes = new LinkedList<>();
        nodes.add(new PathNodePayload(SWITCH_ID_1, null, 1));
        nodes.add(new PathNodePayload(SWITCH_ID_2, 0, null));
        PathValidateRequest request = new PathValidateRequest(PathValidationPayload.builder()
                .nodes(nodes)
                .bandwidth(0L)
                .latencyMs(0L)
                .latencyTier2ms(0L)
                .build());
        List<PathValidationResult> responses = pathsService.validatePath(request);

        assertFalse(responses.isEmpty());
        assertFalse(responses.get(0).getIsValid());
        assertFalse(responses.get(0).getErrors().isEmpty());
        Set<String> expectedErrorMessages = Sets.newHashSet(
                "There is no ISL between end points: switch 00:00:00:00:00:00:00:02 port 0 and switch"
                + " 00:00:00:00:00:00:00:01 port 1.",
                "There is no ISL between end points: switch 00:00:00:00:00:00:00:01 port 1 and switch"
                + " 00:00:00:00:00:00:00:02 port 0.");

        Set<String> actualErrorMessages = Sets.newHashSet(responses.get(0).getErrors());
        assertEquals(expectedErrorMessages, actualErrorMessages);

    }

    @Test
    public void whenNoLinkBetweenSwitches_andValidateLatency_validatePathReturnsErrorResponseTest() {
        List<PathNodePayload> nodes = new LinkedList<>();
        nodes.add(new PathNodePayload(SWITCH_ID_1, null, 1));
        nodes.add(new PathNodePayload(SWITCH_ID_2, 0, null));
        PathValidateRequest request = new PathValidateRequest(PathValidationPayload.builder()
                .nodes(nodes)
                .bandwidth(0L)
                .latencyMs(1L)
                .latencyTier2ms(0L)
                .pathComputationStrategy(LATENCY)
                .build());
        List<PathValidationResult> responses = pathsService.validatePath(request);

        assertFalse(responses.isEmpty());
        assertFalse(responses.get(0).getIsValid());
        assertFalse(responses.get(0).getErrors().isEmpty());
        Set<String> expectedErrorMessages = Sets.newHashSet(
                "There is no ISL between end points: switch 00:00:00:00:00:00:00:02 port 0 and switch"
                + " 00:00:00:00:00:00:00:01 port 1.",
                "There is no ISL between end points: switch 00:00:00:00:00:00:00:01 port 1 and switch"
                + " 00:00:00:00:00:00:00:02 port 0.",
                "Path latency cannot be calculated because there is no link at least at one path segment.");

        Set<String> actualErrorMessages = Sets.newHashSet(responses.get(0).getErrors());
        assertEquals(expectedErrorMessages, actualErrorMessages);
    }

    @Test
    public void whenDiverseWith_andNoLinkExists_validatePathReturnsError() {
        Switch switch1 = Switch.builder().switchId(SWITCH_ID_1).build();
        Switch switch2 = Switch.builder().switchId(SWITCH_ID_3).build();
        createFlow("flow_1", switch1, 6, switch2, 6);

        assertTrue(flowRepository.findById("flow_1").isPresent());
        assertFalse(flowRepository.findById("flow_1").get().getData().getPaths().isEmpty());

        List<PathNodePayload> nodes = new LinkedList<>();
        nodes.add(new PathNodePayload(SWITCH_ID_1, null, 6));
        nodes.add(new PathNodePayload(SWITCH_ID_3, 6, 7));
        nodes.add(new PathNodePayload(SWITCH_ID_0, 7, null));
        PathValidateRequest request = new PathValidateRequest(PathValidationPayload.builder()
                .nodes(nodes)
                .bandwidth(0L)
                .latencyMs(0L)
                .latencyTier2ms(0L)
                .diverseWithFlow("flow_1")
                .build());
        List<PathValidationResult> responses = pathsService.validatePath(request);
        assertFalse(responses.isEmpty());
        assertFalse(responses.get(0).getIsValid());
        Set<String> expectedErrorMessages = Sets.newHashSet(
                "There is no ISL between end points: switch 00:00:00:00:00:00:00:03 port 7 and switch"
                + " 00:00:00:00:00:00:00:00 port 7.",
                "There is no ISL between end points: switch 00:00:00:00:00:00:00:00 port 7 and switch"
                + " 00:00:00:00:00:00:00:03 port 7.",
                "The following segment intersects with the flow flow_1: source switch 00:00:00:00:00:00:00:01"
                + " port 6 and destination switch 00:00:00:00:00:00:00:03 port 6.");
        Set<String> actualErrorMessages = Sets.newHashSet(responses.get(0).getErrors());
        assertEquals(expectedErrorMessages, actualErrorMessages);
    }

    @Test
    public void whenDiverseWith_andExistsIntersection_validatePathReturnsError() {
        Switch switch1 = Switch.builder().switchId(SWITCH_ID_1).build();
        Switch switch2 = Switch.builder().switchId(SWITCH_ID_3).build();
        createFlow("flow_1", switch1, 6, switch2, 6);

        assertTrue(flowRepository.findById("flow_1").isPresent());
        assertFalse(flowRepository.findById("flow_1").get().getData().getPaths().isEmpty());

        List<PathNodePayload> nodes = new LinkedList<>();
        nodes.add(new PathNodePayload(SWITCH_ID_1, null, 6));
        nodes.add(new PathNodePayload(SWITCH_ID_3, 6, 7));
        nodes.add(new PathNodePayload(SWITCH_ID_2, 7, null));
        PathValidateRequest request = new PathValidateRequest(PathValidationPayload.builder()
                .nodes(nodes)
                .bandwidth(0L)
                .latencyMs(0L)
                .latencyTier2ms(0L)
                .diverseWithFlow("flow_1")
                .build());
        List<PathValidationResult> responses = pathsService.validatePath(request);
        assertFalse(responses.isEmpty());
        assertFalse(responses.get(0).getIsValid());
        assertEquals(1, responses.get(0).getErrors().size());
        assertEquals(responses.get(0).getErrors().get(0),
                "The following segment intersects with the flow flow_1: source switch 00:00:00:00:00:00:00:01"
                        + " port 6 and destination switch 00:00:00:00:00:00:00:03 port 6.");
    }

    @Test
    public void whenMultipleProblemsOnPath_validatePathReturnsAllErrorResponseTest() {
        List<PathNodePayload> nodes = new LinkedList<>();
        nodes.add(new PathNodePayload(SWITCH_ID_1, null, 6));
        nodes.add(new PathNodePayload(SWITCH_ID_3, 6, 7));
        nodes.add(new PathNodePayload(new SwitchId("FF"), 7, null));
        PathValidateRequest request = new PathValidateRequest(PathValidationPayload.builder()
                .nodes(nodes)
                .bandwidth(1000000L)
                .latencyMs(1L)
                .latencyTier2ms(0L)
                .pathComputationStrategy(LATENCY)
                .build());
        List<PathValidationResult> responses = pathsService.validatePath(request);

        assertFalse(responses.isEmpty());
        assertFalse(responses.get(0).getIsValid());
        assertFalse(responses.get(0).getErrors().isEmpty());

        assertEquals("There must be 7 errors in total: 2 not enough bandwidth (forward and reverse paths), "
                        + "2 link is not present, 2 latency, and 1 switch is not found",
                7, responses.get(0).getErrors().size());
        Set<String> expectedErrorMessages = Sets.newHashSet(
                "The following switch has not been found: 00:00:00:00:00:00:00:ff.",
                "The following switch properties have not been found: 00:00:00:00:00:00:00:ff.",
                "There is not enough bandwidth between end points: switch 00:00:00:00:00:00:00:03"
                + " port 6 and switch 00:00:00:00:00:00:00:01 port 6 (reverse path). Requested bandwidth 1000000,"
                + " but the link supports 10000.",
                "There is not enough bandwidth between end points: switch 00:00:00:00:00:00:00:01"
                + " port 6 and switch 00:00:00:00:00:00:00:03 port 6 (forward path). Requested bandwidth 1000000,"
                + " but the link supports 10000.",
                "There is no ISL between end points: switch 00:00:00:00:00:00:00:03 port 7 and"
                + " switch 00:00:00:00:00:00:00:ff port 7.",
                "There is no ISL between end points: switch 00:00:00:00:00:00:00:ff port 7 and"
                + " switch 00:00:00:00:00:00:00:03 port 7.",
                "Path latency cannot be calculated because there is no link at least at one path segment.");
        assertEquals(expectedErrorMessages, Sets.newHashSet(responses.get(0).getErrors()));
    }

    @Test
    public void whenValidPathAndDiverseFlowDoesNotExist_validatePathReturnsErrorResponseTest() {
        List<PathNodePayload> nodes = new LinkedList<>();
        nodes.add(new PathNodePayload(SWITCH_ID_1, null, 6));
        nodes.add(new PathNodePayload(SWITCH_ID_3, 6, 7));
        nodes.add(new PathNodePayload(SWITCH_ID_2, 7, null));
        PathValidateRequest request = new PathValidateRequest(PathValidationPayload.builder()
                .nodes(nodes)
                .bandwidth(0L)
                .latencyMs(10L)
                .latencyTier2ms(0L)
                .pathComputationStrategy(COST)
                .diverseWithFlow("non_existing_flow_id")
                .build());
        List<PathValidationResult> responses = pathsService.validatePath(request);

        assertFalse(responses.isEmpty());
        assertFalse(responses.get(0).getIsValid());
        assertFalse(responses.get(0).getErrors().isEmpty());
        assertEquals("Could not find the diverse flow with ID non_existing_flow_id.",
                responses.get(0).getErrors().get(0));
    }

    @Test
    public void whenNonLatencyPathComputationStrategy_ignoreLatencyAnd_validatePathReturnsSuccessResponseTest() {
        List<PathNodePayload> nodes = new LinkedList<>();
        nodes.add(new PathNodePayload(SWITCH_ID_1, null, 6));
        nodes.add(new PathNodePayload(SWITCH_ID_3, 6, 7));
        nodes.add(new PathNodePayload(SWITCH_ID_2, 7, null));
        PathValidateRequest request = new PathValidateRequest(PathValidationPayload.builder()
                .nodes(nodes)
                .bandwidth(0L)
                .latencyMs(10L)
                .latencyTier2ms(0L)
                .pathComputationStrategy(COST)
                .build());
        List<PathValidationResult> responses = pathsService.validatePath(request);

        assertFalse(responses.isEmpty());
        assertTrue(responses.get(0).getIsValid());
    }

    @Test
    public void whenNoPathComputationStrategyInRequest_ignoreLatencyAnd_validatePathReturnsSuccessResponseTest() {
        List<PathNodePayload> nodes = new LinkedList<>();
        nodes.add(new PathNodePayload(SWITCH_ID_1, null, 6));
        nodes.add(new PathNodePayload(SWITCH_ID_3, 6, 7));
        nodes.add(new PathNodePayload(SWITCH_ID_2, 7, null));
        PathValidateRequest request = new PathValidateRequest(PathValidationPayload.builder()
                .nodes(nodes)
                .bandwidth(0L)
                .latencyMs(0L)
                .latencyTier2ms(0L)
                .build());
        List<PathValidationResult> responses = pathsService.validatePath(request);

        assertFalse(responses.isEmpty());
        assertTrue(responses.get(0).getIsValid());
    }

    @Test
    public void whenValidPathWithExistingFlowAndReuseResources_validatePathReturnsSuccessResponseTest() {
        List<PathNodePayload> nodes = new LinkedList<>();
        nodes.add(new PathNodePayload(SWITCH_ID_1, null, 6));
        nodes.add(new PathNodePayload(SWITCH_ID_3, 6, 7));
        nodes.add(new PathNodePayload(SWITCH_ID_2, 7, null));
        PathValidateRequest request = new PathValidateRequest(PathValidationPayload.builder()
                .nodes(nodes)
                .bandwidth(1000L)
                .latencyMs(0L)
                .latencyTier2ms(0L)
                .pathComputationStrategy(COST_AND_AVAILABLE_BANDWIDTH)
                .build());
        List<PathValidationResult> responsesBefore = pathsService.validatePath(request);

        assertFalse(responsesBefore.isEmpty());
        assertTrue("The path using default segments with bandwidth 1003 must be valid",
                responsesBefore.get(0).getIsValid());

        Optional<Isl> islForward = islRepository.findByEndpoints(SWITCH_ID_3, 7, SWITCH_ID_2, 7);
        assertTrue(islForward.isPresent());
        islForward.get().setAvailableBandwidth(100L);
        Optional<Isl> islReverse = islRepository.findByEndpoints(SWITCH_ID_2, 7, SWITCH_ID_3, 7);
        assertTrue(islReverse.isPresent());
        islReverse.get().setAvailableBandwidth(100L);

        String flowToReuse = "flow_3_2";
        createFlow(flowToReuse, Switch.builder().switchId(SWITCH_ID_3).build(), 2000,
                Switch.builder().switchId(SWITCH_ID_2).build(), 2000,
                false, 900L, islForward.get());

        List<PathValidationResult> responsesAfter = pathsService.validatePath(request);

        assertFalse(responsesAfter.isEmpty());
        assertFalse("The path must not be valid because the flow %s consumes bandwidth",
                responsesAfter.get(0).getIsValid());
        assertFalse(responsesAfter.get(0).getErrors().isEmpty());
        assertEquals("There must be 2 errors in total: not enough bandwidth on forward and reverse paths",
                2, responsesAfter.get(0).getErrors().size());
        assertEquals(responsesAfter.get(0).getErrors().get(0),
                "There is not enough bandwidth between end points: switch 00:00:00:00:00:00:00:02 port 7 and"
                        + " switch 00:00:00:00:00:00:00:03 port 7 (reverse path). Requested bandwidth 1000, but the"
                        + " link supports 100.");
        assertEquals(responsesAfter.get(0).getErrors().get(1),
                "There is not enough bandwidth between end points: switch 00:00:00:00:00:00:00:03 port 7 and"
                        + " switch 00:00:00:00:00:00:00:02 port 7 (forward path). Requested bandwidth 1000, but the"
                        + " link supports 100.");

        PathValidateRequest requestWithReuseResources = new PathValidateRequest(PathValidationPayload.builder()
                .nodes(nodes)
                .bandwidth(1000L)
                .latencyMs(0L)
                .latencyTier2ms(0L)
                .pathComputationStrategy(COST_AND_AVAILABLE_BANDWIDTH)
                .reuseFlowResources(flowToReuse)
                .build());

        List<PathValidationResult> responseWithReuseResources = pathsService.validatePath(requestWithReuseResources);

        assertFalse(responseWithReuseResources.isEmpty());
        assertTrue("The path must be valid because, although the flow %s consumes bandwidth, the validator"
                        + " includes the consumed bandwidth to available bandwidth",
                responseWithReuseResources.get(0).getIsValid());
    }

    private void createFlow(String flowId, Switch srcSwitch, int srcPort, Switch destSwitch, int destPort) {
        createFlow(flowId, srcSwitch, srcPort, destSwitch, destPort, null, null, null);
    }

    private void createFlow(String flowId, Switch srcSwitch, int srcPort, Switch destSwitch, int destPort,
                            Boolean ignoreBandwidth, Long bandwidth, Isl isl) {
        Flow flow = Flow.builder()
                .flowId(flowId)
                .srcSwitch(srcSwitch)
                .srcPort(srcPort)
                .destSwitch(destSwitch)
                .destPort(destPort)
                .build();
        Optional.ofNullable(ignoreBandwidth).ifPresent(flow::setIgnoreBandwidth);
        Optional.ofNullable(bandwidth).ifPresent(flow::setBandwidth);
        FlowPath forwardPath = FlowPath.builder()
                .pathId(new PathId("path_1"))
                .srcSwitch(srcSwitch)
                .destSwitch(destSwitch)
                .cookie(new FlowSegmentCookie(FlowPathDirection.FORWARD, 1L))
                .bandwidth(flow.getBandwidth())
                .ignoreBandwidth(false)
                .segments(Collections.singletonList(PathSegment.builder()
                        .pathId(new PathId("forward_segment"))
                        .srcSwitch(srcSwitch)
                        .srcPort(isl == null ? srcPort : isl.getSrcPort())
                        .destSwitch(destSwitch)
                        .destPort(isl == null ? destPort : isl.getDestPort())
                        .build()))
                .build();

        flow.setForwardPath(forwardPath);
        FlowPath reversePath = FlowPath.builder()
                .pathId(new PathId("path_2"))
                .srcSwitch(destSwitch)
                .destSwitch(srcSwitch)
                .cookie(new FlowSegmentCookie(FlowPathDirection.REVERSE, 1L))
                .bandwidth(flow.getBandwidth())
                .ignoreBandwidth(false)
                .segments(Collections.singletonList(PathSegment.builder()
                        .pathId(new PathId("reverse_segment"))
                        .srcSwitch(destSwitch)
                        .srcPort(isl == null ? destPort : isl.getDestPort())
                        .destSwitch(srcSwitch)
                        .destPort(isl == null ? srcPort : isl.getSrcPort())
                        .build()))
                .build();
        flow.setReversePath(reversePath);

        flowRepository.add(flow);
    }

    private void createOneWayIsl(Switch srcSwitch, int srcPort, Switch dstSwitch, int dstPort, int cost,
                                 long latency, int bandwidth, IslStatus islStatus) {
        islRepository.add(Isl.builder()
                .srcSwitch(srcSwitch)
                .srcPort(srcPort)
                .destSwitch(dstSwitch)
                .destPort(dstPort)
                .status(islStatus)
                .actualStatus(islStatus)
                .cost(cost)
                .availableBandwidth(bandwidth)
                .maxBandwidth(bandwidth)
                .latency(latency)
                .build());
    }
}
