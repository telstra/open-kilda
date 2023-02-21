/* Copyright 2020 Telstra Open Source
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

package org.openkilda.wfm.topology.nbworker.services;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.openkilda.model.FlowEncapsulationType.TRANSIT_VLAN;
import static org.openkilda.model.FlowEncapsulationType.VXLAN;
import static org.openkilda.model.PathComputationStrategy.COST;
import static org.openkilda.model.PathComputationStrategy.COST_AND_AVAILABLE_BANDWIDTH;
import static org.openkilda.model.PathComputationStrategy.LATENCY;
import static org.openkilda.model.PathComputationStrategy.MAX_LATENCY;

import org.openkilda.config.provider.PropertiesBasedConfigurationProvider;
import org.openkilda.messaging.command.flow.PathValidateRequest;
import org.openkilda.messaging.info.network.PathValidationResult;
import org.openkilda.messaging.info.network.PathsInfoData;
import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.messaging.payload.network.PathValidationDto;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.Isl;
import org.openkilda.model.IslStatus;
import org.openkilda.model.KildaConfiguration;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.SwitchStatus;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.pce.PathComputerConfig;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.KildaConfigurationRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.error.SwitchNotFoundException;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class PathsServiceTest extends InMemoryGraphBasedTest {
    private static final int MAX_PATH_COUNT = 500;
    private static final int SWITCH_COUNT = MAX_PATH_COUNT + 50;
    private static final int VXLAN_SWITCH_COUNT = MAX_PATH_COUNT / 2;

    private static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    private static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    private static final SwitchId SWITCH_ID_3 = new SwitchId(3);
    private static final SwitchId SWITCH_ID_4 = new SwitchId(4);
    public static final long BASE_LATENCY = 10000;
    public static final long MIN_LATENCY = BASE_LATENCY - SWITCH_COUNT;

    private static KildaConfigurationRepository kildaConfigurationRepository;
    private static SwitchRepository switchRepository;
    private static SwitchPropertiesRepository switchPropertiesRepository;
    private static IslRepository islRepository;
    private static FlowRepository flowRepository;
    private static PathsService pathsService;


    @BeforeClass
    public static void setUpOnce() {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        kildaConfigurationRepository = repositoryFactory.createKildaConfigurationRepository();
        switchRepository = repositoryFactory.createSwitchRepository();
        switchPropertiesRepository = repositoryFactory.createSwitchPropertiesRepository();
        islRepository = repositoryFactory.createIslRepository();
        flowRepository = repositoryFactory.createFlowRepository();
        kildaConfigurationRepository = repositoryFactory.createKildaConfigurationRepository();
        PathComputerConfig pathComputerConfig = new PropertiesBasedConfigurationProvider()
                .getConfiguration(PathComputerConfig.class);
        pathsService = new PathsService(repositoryFactory, pathComputerConfig, islRepository,
                repositoryFactory.createFlowRepository());
    }

    @Before
    public void init() {
        /* Topology:
         *
         *  +------3------+
         *  |             |
         *  1------4------2
         *  |             |
         *  +------5------+
         *  |             |
         *  ...          ...
         *  |             |
         *  +-----550-----+
         */

        Switch switchA = Switch.builder().switchId(SWITCH_ID_1).status(SwitchStatus.ACTIVE).build();
        Switch switchB = Switch.builder().switchId(SWITCH_ID_2).status(SwitchStatus.ACTIVE).build();

        switchRepository.add(switchA);
        switchRepository.add(switchB);

        createSwitchProperties(switchA, TRANSIT_VLAN, VXLAN);
        createSwitchProperties(switchB, TRANSIT_VLAN, VXLAN);

        for (int i = 3; i <= SWITCH_COUNT; i++) {
            Switch transitSwitch = Switch.builder().switchId(new SwitchId(i)).status(SwitchStatus.ACTIVE).build();
            switchRepository.add(transitSwitch);

            // first half of switches supports only TRANSIT_FLAN,
            // last half of switches supports TRANSIT_VLAN and VXLAN
            if (SWITCH_COUNT - i < VXLAN_SWITCH_COUNT) {
                createSwitchProperties(transitSwitch, TRANSIT_VLAN, VXLAN);
            } else {
                createSwitchProperties(transitSwitch, TRANSIT_VLAN);
            }

            createIsl(switchA, i * 2, transitSwitch, i * 2, i, BASE_LATENCY - i, 1000 + i);
            createIsl(transitSwitch, i * 2 + 1, switchB, i * 2 + 1, i, BASE_LATENCY - i, 1000 + i);
        }

        kildaConfigurationRepository.add(KildaConfiguration.builder()
                .flowEncapsulationType(TRANSIT_VLAN)
                .pathComputationStrategy(PathComputationStrategy.COST_AND_AVAILABLE_BANDWIDTH)
                .build());
    }

    @Test
    public void findNPathsByTransitVlanAndCost()
            throws SwitchNotFoundException, RecoverableException, UnroutableFlowException {
        List<PathsInfoData> paths = pathsService.getPaths(
                SWITCH_ID_1, SWITCH_ID_2, TRANSIT_VLAN, COST, null, null, MAX_PATH_COUNT);

        assertEquals(MAX_PATH_COUNT, paths.size());
        assertPathLength(paths);

        for (int i = 0; i < paths.size(); i++) {
            // switches with less switchIds has better cost
            assertTrue(paths.get(i).getPath().getNodes().get(1).getSwitchId().getId() <= MAX_PATH_COUNT + 3);

            // paths are sorted by bandwidth
            if (i > 0) {
                assertTrue(paths.get(i - 1).getPath().getBandwidth() >= paths.get(i).getPath().getBandwidth());
            }
        }
    }

    @Test
    public void findNPathsByTransitVlanIgnoreMaxLatency()
            throws SwitchNotFoundException, RecoverableException, UnroutableFlowException {
        List<PathsInfoData> paths = pathsService.getPaths(SWITCH_ID_1, SWITCH_ID_2, TRANSIT_VLAN, LATENCY,
                Duration.ofNanos(1L), Duration.ofNanos(2L), MAX_PATH_COUNT);
        assertTransitVlanAndLatencyPaths(paths);
    }

    @Test
    public void findNPathsByVxlanAndEnoughMaxLatencyZeroTier2Latency()
            throws SwitchNotFoundException, RecoverableException, UnroutableFlowException {
        Duration maxLatency = Duration.ofNanos(MIN_LATENCY * 2 + 1);
        List<PathsInfoData> paths = pathsService.getPaths(
                SWITCH_ID_1, SWITCH_ID_2, VXLAN, MAX_LATENCY, maxLatency, Duration.ZERO, MAX_PATH_COUNT);
        assertMaxLatencyPaths(paths, maxLatency, 1, VXLAN);
    }

    @Test
    public void findNPathsByTransitVlanAndTooSmallMaxLatency()
            throws SwitchNotFoundException, RecoverableException, UnroutableFlowException {
        List<PathsInfoData> paths = pathsService.getPaths(SWITCH_ID_1, SWITCH_ID_2, TRANSIT_VLAN,
                MAX_LATENCY, Duration.ofNanos(1L), Duration.ZERO, MAX_PATH_COUNT);
        assertTrue(paths.isEmpty());
    }

    @Test
    public void findNPathsByTransitVlanAndTooSmallMaxLatencyAndTier2Latency()
            throws SwitchNotFoundException, RecoverableException, UnroutableFlowException {
        List<PathsInfoData> paths = pathsService.getPaths(SWITCH_ID_1, SWITCH_ID_2, TRANSIT_VLAN,
                MAX_LATENCY, Duration.ofNanos(1L), Duration.ofNanos(2L), MAX_PATH_COUNT);
        assertTrue(paths.isEmpty());
    }

    @Test
    public void findNPathsByTransitVlanAndTooSmallMaxLatencyEnoughTier2Latency()
            throws SwitchNotFoundException, RecoverableException, UnroutableFlowException {
        Duration maxLatencyTier2 = Duration.ofNanos(MIN_LATENCY * 2 + 1);
        List<PathsInfoData> paths = pathsService.getPaths(SWITCH_ID_1, SWITCH_ID_2, TRANSIT_VLAN, MAX_LATENCY,
                Duration.ofNanos(1L), maxLatencyTier2, MAX_PATH_COUNT);
        assertMaxLatencyPaths(paths, maxLatencyTier2, 1, TRANSIT_VLAN);
    }

    @Test
    public void findNPathsByTransitVlanAndZeroMaxLatency()
            throws SwitchNotFoundException, RecoverableException, UnroutableFlowException {
        // as max latency param is 0 LATENCY starategy will be used instead. It means all 500 paths will be returned
        List<PathsInfoData> paths = pathsService.getPaths(
                SWITCH_ID_1, SWITCH_ID_2, TRANSIT_VLAN, MAX_LATENCY, Duration.ZERO, null, MAX_PATH_COUNT);
        assertTransitVlanAndLatencyPaths(paths);
    }

    @Test
    public void findNPathsByTransitVlanAndNullMaxLatency()
            throws SwitchNotFoundException, RecoverableException, UnroutableFlowException {
        // as max latency param is null LATENCY starategy will be used instead. It means all 500 paths will be returned
        List<PathsInfoData> paths = pathsService.getPaths(SWITCH_ID_1, SWITCH_ID_2, TRANSIT_VLAN, MAX_LATENCY, null,
                null, MAX_PATH_COUNT);
        assertTransitVlanAndLatencyPaths(paths);
    }

    @Test
    public void findNPathsByTransitVlanAndLatency()
            throws SwitchNotFoundException, RecoverableException, UnroutableFlowException {
        List<PathsInfoData> paths = pathsService.getPaths(SWITCH_ID_1, SWITCH_ID_2, TRANSIT_VLAN, LATENCY,
                null, null, MAX_PATH_COUNT);
        assertTransitVlanAndLatencyPaths(paths);
    }

    @Test
    public void findNPathsByVxlanAndCost()
            throws SwitchNotFoundException, RecoverableException, UnroutableFlowException {
        List<PathsInfoData> paths = pathsService.getPaths(SWITCH_ID_1, SWITCH_ID_2, VXLAN, COST, null,
                null, MAX_PATH_COUNT);
        assertVxlanAndCostPathes(paths);
    }

    @Test
    public void findNPathsByMaxPathCount()
            throws UnroutableFlowException, SwitchNotFoundException, RecoverableException {
        int maxPathCount = 3;
        List<PathsInfoData> paths = pathsService.getPaths(SWITCH_ID_1, SWITCH_ID_2, VXLAN, COST, null,
                null, maxPathCount);
        assertThat(paths.size(), equalTo(maxPathCount));
    }

    @Test(expected = IllegalArgumentException.class)
    public void checkMaxPathCountWhenFindNPaths()
            throws UnroutableFlowException, SwitchNotFoundException, RecoverableException {
        int maxPathCount = -1;
        pathsService.getPaths(SWITCH_ID_1, SWITCH_ID_2, VXLAN, COST, null, null, maxPathCount);
    }

    @Test(expected = IllegalArgumentException.class)
    public void checkForZeroMaxPathCountWhenFindNPaths()
            throws UnroutableFlowException, SwitchNotFoundException, RecoverableException {
        int maxPathCount = 0;
        pathsService.getPaths(SWITCH_ID_1, SWITCH_ID_2, VXLAN, COST, null, null, maxPathCount);
    }

    @Test(expected = IllegalArgumentException.class)
    public void findNPathsByVxlanSrcWithoutVxlanSupport()
            throws SwitchNotFoundException, RecoverableException, UnroutableFlowException {
        pathsService.getPaths(SWITCH_ID_3, SWITCH_ID_2, VXLAN, COST, null, null, MAX_PATH_COUNT);
    }

    @Test(expected = IllegalArgumentException.class)
    public void findNPathsByVxlanDstWithoutVxlanSupport()
            throws SwitchNotFoundException, RecoverableException, UnroutableFlowException {
        pathsService.getPaths(SWITCH_ID_2, SWITCH_ID_3, VXLAN, COST, null, null, MAX_PATH_COUNT);
    }

    @Test
    public void findNPathsByTransitVlanAndDefaultStrategy()
            throws SwitchNotFoundException, RecoverableException, UnroutableFlowException {
        // set LATENCY as default path computations strategy
        kildaConfigurationRepository.find().ifPresent(config -> {
            config.setPathComputationStrategy(LATENCY);
        });
        // find N paths without strategy
        List<PathsInfoData> paths = pathsService.getPaths(SWITCH_ID_1, SWITCH_ID_2, TRANSIT_VLAN,
                null, null, null, MAX_PATH_COUNT);
        assertTransitVlanAndLatencyPaths(paths);
    }

    @Test
    public void findNPathsByDefaultEncapsulationAndCost()
            throws SwitchNotFoundException, RecoverableException, UnroutableFlowException {
        // set VXLAN as default flow encapsulation type
        kildaConfigurationRepository.find().ifPresent(config -> {
            config.setFlowEncapsulationType(VXLAN);
        });
        // find N paths without encapsulation type
        List<PathsInfoData> paths = pathsService.getPaths(SWITCH_ID_1, SWITCH_ID_2, null, COST,
                null, null, MAX_PATH_COUNT);
        assertVxlanAndCostPathes(paths);
    }

    @Test
    public void whenValidPath_validatePathReturnsValidResponseTest() {
        List<PathNodePayload> nodes = new LinkedList<>();
        nodes.add(new PathNodePayload(SWITCH_ID_1, null, 6));
        nodes.add(new PathNodePayload(SWITCH_ID_3, 6, 7));
        nodes.add(new PathNodePayload(SWITCH_ID_2, 7, null));
        PathValidateRequest request = new PathValidateRequest(PathValidationDto.builder()
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
    public void whenValidPathButNotEnoughBandwidth_validateReturnsErrorResponseTest() {
        List<PathNodePayload> nodes = new LinkedList<>();
        nodes.add(new PathNodePayload(SWITCH_ID_1, null, 6));
        nodes.add(new PathNodePayload(SWITCH_ID_3, 6, null));
        PathValidateRequest request = new PathValidateRequest(PathValidationDto.builder()
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
        assertTrue(responses.get(0).getErrors().get(0)
                .startsWith("There is not enough Bandwidth between the source switch 00:00:00:00:00:00:00:03 port 6 "
                        + "and destination switch 00:00:00:00:00:00:00:01 port 6 (reverse path)."));
        assertTrue(responses.get(0).getErrors().get(1)
                .startsWith("There is not enough bandwidth between the source switch 00:00:00:00:00:00:00:01 port 6 and"
                        + " destination switch 00:00:00:00:00:00:00:03 port 6 (forward path)."));
    }

    @Test
    public void whenValidPathButTooLowLatency_andLatencyStrategy_validateReturnsErrorResponseTest() {
        List<PathNodePayload> nodes = new LinkedList<>();
        nodes.add(new PathNodePayload(SWITCH_ID_1, null, 6));
        nodes.add(new PathNodePayload(SWITCH_ID_3, 6, 7));
        nodes.add(new PathNodePayload(SWITCH_ID_2, 7, null));
        PathValidateRequest request = new PathValidateRequest(PathValidationDto.builder()
                .nodes(nodes)
                .bandwidth(0L)
                .latencyMs(10L)
                .latencyTier2ms(0L)
                .pathComputationStrategy(LATENCY)
                .build());
        List<PathValidationResult> responses = pathsService.validatePath(request);

        assertFalse(responses.isEmpty());
        assertFalse(responses.get(0).getIsValid());
        assertFalse(responses.get(0).getErrors().isEmpty());
        assertTrue(responses.get(0).getErrors().get(0)
                .contains("Requested latency is too low between source switch"));
    }

    @Test
    public void whenValidPathButTooLowLatency_andMaxLatencyStrategy_validateReturnsErrorResponseTest() {
        List<PathNodePayload> nodes = new LinkedList<>();
        nodes.add(new PathNodePayload(SWITCH_ID_1, null, 6));
        nodes.add(new PathNodePayload(SWITCH_ID_3, 6, 7));
        nodes.add(new PathNodePayload(SWITCH_ID_2, 7, null));
        PathValidateRequest request = new PathValidateRequest(PathValidationDto.builder()
                .nodes(nodes)
                .bandwidth(0L)
                .latencyMs(10L)
                .latencyTier2ms(0L)
                .pathComputationStrategy(MAX_LATENCY)
                .build());
        List<PathValidationResult> responses = pathsService.validatePath(request);

        assertFalse(responses.isEmpty());
        assertFalse(responses.get(0).getIsValid());
        assertFalse(responses.get(0).getErrors().isEmpty());
        assertTrue(responses.get(0).getErrors().get(0)
                .contains("Requested latency is too low between source switch"));
    }

    @Test
    public void whenValidPathButTooLowLatencyTier2_andLatencyStrategy_validateReturnsErrorResponseTest() {
        List<PathNodePayload> nodes = new LinkedList<>();
        nodes.add(new PathNodePayload(SWITCH_ID_1, null, 6));
        nodes.add(new PathNodePayload(SWITCH_ID_3, 6, 7));
        nodes.add(new PathNodePayload(SWITCH_ID_2, 7, null));
        PathValidateRequest request = new PathValidateRequest(PathValidationDto.builder()
                .nodes(nodes)
                .bandwidth(0L)
                .latencyMs(10000000000L)
                .latencyTier2ms(100L)
                .pathComputationStrategy(LATENCY)
                .build());
        List<PathValidationResult> responses = pathsService.validatePath(request);

        assertFalse(responses.isEmpty());
        assertFalse(responses.get(0).getIsValid());
        assertFalse(responses.get(0).getErrors().isEmpty());
        assertTrue(responses.get(0).getErrors().get(0)
                .contains("Requested latency tier 2 is too low"));
    }

    @Test
    public void whenSwitchDoesNotSupportEncapsulationType_validateReturnsErrorResponseTest() {
        List<PathNodePayload> nodes = new LinkedList<>();
        nodes.add(new PathNodePayload(SWITCH_ID_1, null, 6));
        nodes.add(new PathNodePayload(SWITCH_ID_3, 6, 7));
        // TODO investigate: when uncommenting the following line, switch 3 supports VXLAN. Why?
        //nodes.add(new PathNodePayload(SWITCH_ID_2, 7, null));
        PathValidateRequest request = new PathValidateRequest(PathValidationDto.builder()
                .nodes(nodes)
                .flowEncapsulationType(org.openkilda.messaging.payload.flow.FlowEncapsulationType.VXLAN)
                .build());
        List<PathValidationResult> responses = pathsService.validatePath(request);

        assertFalse(responses.isEmpty());
        assertFalse(responses.get(0).getIsValid());
        assertFalse(responses.get(0).getErrors().isEmpty());
        assertTrue(responses.get(0).getErrors().get(0)
                .contains("doesn't support encapsulation type VXLAN"));
    }

    @Test
    public void whenNoLinkBetweenSwitches_validatePathReturnsErrorResponseTest() {
        List<PathNodePayload> nodes = new LinkedList<>();
        nodes.add(new PathNodePayload(SWITCH_ID_1, null, 1));
        nodes.add(new PathNodePayload(SWITCH_ID_2, 0, null));
        PathValidateRequest request = new PathValidateRequest(PathValidationDto.builder()
                .nodes(nodes)
                .bandwidth(0L)
                .latencyMs(0L)
                .latencyTier2ms(0L)
                .build());
        List<PathValidationResult> responses = pathsService.validatePath(request);

        assertFalse(responses.isEmpty());
        assertFalse(responses.get(0).getIsValid());
        assertFalse(responses.get(0).getErrors().isEmpty());
        assertTrue(responses.get(0).getErrors().get(0)
                .startsWith("There is no ISL between source switch"));
    }

    @Test
    public void whenDiverseWith_andExistsIntersection_validateReturnsError() {
        Switch switch1 = Switch.builder().switchId(SWITCH_ID_1).build();
        Switch switch2 = Switch.builder().switchId(SWITCH_ID_3).build();
        createFlow("flow_1", switch1, 6, switch2, 6);

        assertTrue(flowRepository.findById("flow_1").isPresent());
        assertFalse(flowRepository.findById("flow_1").get().getData().getPaths().isEmpty());

        List<PathNodePayload> nodes = new LinkedList<>();
        nodes.add(new PathNodePayload(SWITCH_ID_1, null, 6));
        nodes.add(new PathNodePayload(SWITCH_ID_3, 6, 7));
        nodes.add(new PathNodePayload(SWITCH_ID_2, 7, null));
        PathValidateRequest request = new PathValidateRequest(PathValidationDto.builder()
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
        assertTrue(responses.get(0).getErrors().get(0).startsWith("The following segment intersects with the flow"));
    }

    @Test
    public void whenMultipleProblemsOnPath_validatePathReturnsAllErrorResponseTest() {
        List<PathNodePayload> nodes = new LinkedList<>();
        nodes.add(new PathNodePayload(SWITCH_ID_1, null, 6));
        nodes.add(new PathNodePayload(SWITCH_ID_3, 6, 7));
        nodes.add(new PathNodePayload(SWITCH_ID_4, 7, 7));
        nodes.add(new PathNodePayload(SWITCH_ID_2, 7, null));
        PathValidateRequest request = new PathValidateRequest(PathValidationDto.builder()
                .nodes(nodes)
                .bandwidth(1000000L)
                .latencyMs(10L)
                .latencyTier2ms(0L)
                .build());
        List<PathValidationResult> responses = pathsService.validatePath(request);

        assertFalse(responses.isEmpty());
        assertFalse(responses.get(0).getIsValid());
        assertFalse(responses.get(0).getErrors().isEmpty());
        assertEquals("There must be 5 errors in total: 2 bandwidth (forward and reverse paths), "
                + "2 links are not present, and 1 latency", 5, responses.get(0).getErrors().size());
    }

    @Test
    public void whenNonLatencyPathComputationStrategy_ignoreLatencyAnd_validatePathReturnsSuccessResponseTest() {
        List<PathNodePayload> nodes = new LinkedList<>();
        nodes.add(new PathNodePayload(SWITCH_ID_1, null, 6));
        nodes.add(new PathNodePayload(SWITCH_ID_3, 6, 7));
        nodes.add(new PathNodePayload(SWITCH_ID_2, 7, null));
        PathValidateRequest request = new PathValidateRequest(PathValidationDto.builder()
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
    public void whenValidPathWithExistingFlowAndReuseResources_validatePathReturnsSuccessResponseTest() {
        List<PathNodePayload> nodes = new LinkedList<>();
        nodes.add(new PathNodePayload(SWITCH_ID_1, null, 6));
        nodes.add(new PathNodePayload(SWITCH_ID_3, 6, 7));
        nodes.add(new PathNodePayload(SWITCH_ID_2, 7, null));
        PathValidateRequest request = new PathValidateRequest(PathValidationDto.builder()
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
        assertTrue(responsesAfter.get(0).getErrors().get(0).contains("There is not enough bandwidth"));
        assertTrue(responsesAfter.get(0).getErrors().get(1).contains("There is not enough bandwidth"));

        PathValidateRequest requestWithReuseResources = new PathValidateRequest(PathValidationDto.builder()
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

    private void assertMaxLatencyPaths(List<PathsInfoData> paths, Duration maxLatency, long expectedCount,
                                       FlowEncapsulationType encapsulationType) {
        assertEquals(expectedCount, paths.size());
        assertPathLength(paths);

        for (PathsInfoData path : paths) {
            assertTrue(path.getPath().getLatency().minus(maxLatency).isNegative());
            Optional<SwitchProperties> properties = switchPropertiesRepository
                    .findBySwitchId(path.getPath().getNodes().get(1).getSwitchId());
            assertTrue(properties.isPresent());
            assertTrue(properties.get().getSupportedTransitEncapsulation().contains(encapsulationType));
        }
    }

    private void assertTransitVlanAndLatencyPaths(List<PathsInfoData> paths) {
        assertEquals(MAX_PATH_COUNT, paths.size());
        assertPathLength(paths);

        for (int i = 0; i < paths.size(); i++) {
            // switches with low switchIds has better latency
            assertTrue(paths.get(i).getPath().getNodes().get(1).getSwitchId().getId()
                    > SWITCH_COUNT - MAX_PATH_COUNT);

            // paths are sorted by bandwidth
            if (i > 0) {
                assertTrue(paths.get(i - 1).getPath().getBandwidth() >= paths.get(i).getPath().getBandwidth());
            }
        }
    }

    private void assertVxlanAndCostPathes(List<PathsInfoData> paths) {
        assertEquals(VXLAN_SWITCH_COUNT, paths.size());
        assertPathLength(paths);

        for (int i = 0; i < paths.size(); i++) {

            // Only second half of switches support VXLAN
            assertTrue(paths.get(i).getPath().getNodes().get(1).getSwitchId().getId()
                    > SWITCH_COUNT - VXLAN_SWITCH_COUNT);

            // paths are sorted by bandwidth
            if (i > 0) {
                assertTrue(paths.get(i - 1).getPath().getBandwidth() >= paths.get(i).getPath().getBandwidth());
            }
        }
    }

    private void assertPathLength(List<PathsInfoData> paths) {
        for (PathsInfoData path : paths) {
            assertEquals(3, path.getPath().getNodes().size());
            assertEquals(SWITCH_ID_1, path.getPath().getNodes().get(0).getSwitchId());
            assertEquals(SWITCH_ID_2, path.getPath().getNodes().get(2).getSwitchId());
        }
    }


    private void createIsl(Switch srcSwitch, int srcPort, Switch dstSwitch, int dstPort, int cost, long latency,
                           int bandwidth) {
        createOneWayIsl(srcSwitch, srcPort, dstSwitch, dstPort, cost, latency, bandwidth);
        createOneWayIsl(dstSwitch, dstPort, srcSwitch, srcPort, cost, latency, bandwidth);
    }

    private void createOneWayIsl(Switch srcSwitch, int srcPort, Switch dstSwitch, int dstPort, int cost, long latency,
                                 int bandwidth) {
        islRepository.add(Isl.builder()
                .srcSwitch(srcSwitch)
                .srcPort(srcPort)
                .destSwitch(dstSwitch)
                .destPort(dstPort)
                .status(IslStatus.ACTIVE)
                .actualStatus(IslStatus.ACTIVE)
                .cost(cost)
                .availableBandwidth(bandwidth)
                .maxBandwidth(bandwidth)
                .latency(latency)
                .build());
    }

    private void createSwitchProperties(Switch sw, FlowEncapsulationType... encapsulation) {
        switchPropertiesRepository.add(SwitchProperties.builder()
                .switchObj(sw)
                .supportedTransitEncapsulation(Sets.newHashSet(encapsulation))
                .build());
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
}
