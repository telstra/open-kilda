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
import static junit.framework.TestCase.assertTrue;
import static org.openkilda.model.FlowEncapsulationType.TRANSIT_VLAN;
import static org.openkilda.model.FlowEncapsulationType.VXLAN;
import static org.openkilda.model.PathComputationStrategy.COST;
import static org.openkilda.model.PathComputationStrategy.LATENCY;
import static org.openkilda.model.PathComputationStrategy.MAX_LATENCY;
import static org.openkilda.wfm.topology.nbworker.services.PathsService.MAX_PATH_COUNT;

import org.openkilda.config.provider.PropertiesBasedConfigurationProvider;
import org.openkilda.messaging.info.network.PathsInfoData;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.Isl;
import org.openkilda.model.IslStatus;
import org.openkilda.model.KildaConfiguration;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.SwitchStatus;
import org.openkilda.pce.PathComputerConfig;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
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

import java.util.List;
import java.util.Optional;

public class PathsServiceTest extends InMemoryGraphBasedTest {
    private static final int SWITCH_COUNT = MAX_PATH_COUNT + 50;
    private static final int VXLAN_SWITCH_COUNT = MAX_PATH_COUNT / 2;

    private static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    private static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    private static final SwitchId SWITCH_ID_3 = new SwitchId(3);
    public static final long BASE_LATENCY = 10000;
    public static final long MIN_LATENCY = BASE_LATENCY - SWITCH_COUNT;

    private static KildaConfigurationRepository kildaConfigurationRepository;
    private static SwitchRepository switchRepository;
    private static SwitchPropertiesRepository switchPropertiesRepository;
    private static IslRepository islRepository;
    private static PathsService pathsService;


    @BeforeClass
    public static void setUpOnce() {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        kildaConfigurationRepository = repositoryFactory.createKildaConfigurationRepository();
        switchRepository = repositoryFactory.createSwitchRepository();
        switchPropertiesRepository = repositoryFactory.createSwitchPropertiesRepository();
        islRepository = repositoryFactory.createIslRepository();
        kildaConfigurationRepository = repositoryFactory.createKildaConfigurationRepository();
        PathComputerConfig pathComputerConfig = new PropertiesBasedConfigurationProvider()
                .getConfiguration(PathComputerConfig.class);
        pathsService = new PathsService(repositoryFactory, pathComputerConfig);
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
        List<PathsInfoData> paths = pathsService.getPaths(SWITCH_ID_1, SWITCH_ID_2, TRANSIT_VLAN, COST, null, null);

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
        List<PathsInfoData> paths = pathsService.getPaths(SWITCH_ID_1, SWITCH_ID_2, TRANSIT_VLAN, LATENCY, 1L, 2L);
        assertTransitVlanAndLatencyPaths(paths);
    }

    @Test
    public void findNPathsByVxlanAndEnoughMaxLatencyZeroTier2Latency()
            throws SwitchNotFoundException, RecoverableException, UnroutableFlowException {
        long maxLatency = MIN_LATENCY * 2 + 1;
        List<PathsInfoData> paths = pathsService.getPaths(
                SWITCH_ID_1, SWITCH_ID_2, VXLAN, MAX_LATENCY, maxLatency, 0L);
        assertMaxLatencyPaths(paths, maxLatency, 1, VXLAN);
    }

    @Test
    public void findNPathsByTransitVlanAndTooSmallMaxLatency()
            throws SwitchNotFoundException, RecoverableException, UnroutableFlowException {
        List<PathsInfoData> paths = pathsService.getPaths(SWITCH_ID_1, SWITCH_ID_2, TRANSIT_VLAN, MAX_LATENCY, 1L, 0L);
        assertTrue(paths.isEmpty());
    }

    @Test
    public void findNPathsByTransitVlanAndTooSmallMaxLatencyAndTier2Latency()
            throws SwitchNotFoundException, RecoverableException, UnroutableFlowException {
        List<PathsInfoData> paths = pathsService.getPaths(SWITCH_ID_1, SWITCH_ID_2, TRANSIT_VLAN, MAX_LATENCY, 1L, 2L);
        assertTrue(paths.isEmpty());
    }

    @Test
    public void findNPathsByTransitVlanAndTooSmallMaxLatencyEnoughTier2Latency()
            throws SwitchNotFoundException, RecoverableException, UnroutableFlowException {
        long maxLatencyTier2 = MIN_LATENCY * 2 + 1;
        List<PathsInfoData> paths = pathsService.getPaths(SWITCH_ID_1, SWITCH_ID_2, TRANSIT_VLAN, MAX_LATENCY, 1L,
                maxLatencyTier2);
        assertMaxLatencyPaths(paths, maxLatencyTier2, 1, TRANSIT_VLAN);
    }

    @Test
    public void findNPathsByTransitVlanAndZeroMaxLatency()
            throws SwitchNotFoundException, RecoverableException, UnroutableFlowException {
        // as max latency param is 0 LATENCY starategy will be used instead. It means all 500 paths will be returned
        List<PathsInfoData> paths = pathsService.getPaths(SWITCH_ID_1, SWITCH_ID_2, TRANSIT_VLAN, MAX_LATENCY, 0L,
                null);
        assertTransitVlanAndLatencyPaths(paths);
    }

    @Test
    public void findNPathsByTransitVlanAndNullMaxLatency()
            throws SwitchNotFoundException, RecoverableException, UnroutableFlowException {
        // as max latency param is null LATENCY starategy will be used instead. It means all 500 paths will be returned
        List<PathsInfoData> paths = pathsService.getPaths(SWITCH_ID_1, SWITCH_ID_2, TRANSIT_VLAN, MAX_LATENCY, null,
                null);
        assertTransitVlanAndLatencyPaths(paths);
    }

    @Test
    public void findNPathsByTransitVlanAndLatency()
            throws SwitchNotFoundException, RecoverableException, UnroutableFlowException {
        List<PathsInfoData> paths = pathsService.getPaths(SWITCH_ID_1, SWITCH_ID_2, TRANSIT_VLAN, LATENCY, null, null);
        assertTransitVlanAndLatencyPaths(paths);
    }

    @Test
    public void findNPathsByVxlanAndCost()
            throws SwitchNotFoundException, RecoverableException, UnroutableFlowException {
        List<PathsInfoData> paths = pathsService.getPaths(SWITCH_ID_1, SWITCH_ID_2, VXLAN, COST, null, null);
        assertVxlanAndCostPathes(paths);
    }

    @Test(expected = IllegalArgumentException.class)
    public void findNPathsByVxlanSrcWithoutVxlanSupport()
            throws SwitchNotFoundException, RecoverableException, UnroutableFlowException {
        pathsService.getPaths(SWITCH_ID_3, SWITCH_ID_2, VXLAN, COST, null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void findNPathsByVxlanDstWithoutVxlanSupport()
            throws SwitchNotFoundException, RecoverableException, UnroutableFlowException {
        pathsService.getPaths(SWITCH_ID_2, SWITCH_ID_3, VXLAN, COST, null, null);
    }

    @Test
    public void findNPathsByTransitVlanAndDefaultStrategy()
            throws SwitchNotFoundException, RecoverableException, UnroutableFlowException {
        // set LATENCY as default path computations strategy
        kildaConfigurationRepository.find().ifPresent(config -> {
            config.setPathComputationStrategy(LATENCY);
        });
        // find N paths without strategy
        List<PathsInfoData> paths = pathsService.getPaths(SWITCH_ID_1, SWITCH_ID_2, TRANSIT_VLAN, null, null, null);
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
        List<PathsInfoData> paths = pathsService.getPaths(SWITCH_ID_1, SWITCH_ID_2, null, COST, null, null);
        assertVxlanAndCostPathes(paths);
    }

    private void assertMaxLatencyPaths(List<PathsInfoData> paths, long maxLatency, long expectedCount,
                                       FlowEncapsulationType encapsulationType) {
        assertEquals(expectedCount, paths.size());
        assertPathLength(paths);

        for (PathsInfoData path : paths) {
            assertTrue(path.getPath().getLatency() < maxLatency);
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
}
