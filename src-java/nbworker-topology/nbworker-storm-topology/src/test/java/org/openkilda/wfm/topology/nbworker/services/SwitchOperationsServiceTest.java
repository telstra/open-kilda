/* Copyright 2021 Telstra Open Source
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
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;

import org.openkilda.messaging.model.SwitchLocation;
import org.openkilda.messaging.model.SwitchPatch;
import org.openkilda.messaging.model.SwitchPropertiesDto;
import org.openkilda.messaging.model.SwitchPropertiesDto.RttState;
import org.openkilda.model.DetectConnectedDevices;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowMirrorPath;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.GroupId;
import org.openkilda.model.LagLogicalPort;
import org.openkilda.model.MacAddress;
import org.openkilda.model.MirrorDirection;
import org.openkilda.model.MirrorGroup;
import org.openkilda.model.MirrorGroupType;
import org.openkilda.model.PathId;
import org.openkilda.model.PortProperties;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.FlowMirrorPathRepository;
import org.openkilda.persistence.repositories.FlowMirrorPointsRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.LagLogicalPortRepository;
import org.openkilda.persistence.repositories.MirrorGroupRepository;
import org.openkilda.persistence.repositories.PortPropertiesRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.error.IllegalSwitchPropertiesException;
import org.openkilda.wfm.error.SwitchNotFoundException;
import org.openkilda.wfm.share.model.Endpoint;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

public class SwitchOperationsServiceTest extends InMemoryGraphBasedTest {
    public static final Set<org.openkilda.messaging.payload.flow.FlowEncapsulationType> SUPPORTED_TRANSIT_ENCAPSULATION
            = Collections.singleton(org.openkilda.messaging.payload.flow.FlowEncapsulationType.TRANSIT_VLAN);
    private static SwitchRepository switchRepository;
    private static SwitchPropertiesRepository switchPropertiesRepository;
    private static PortPropertiesRepository portPropertiesRepository;
    private static FlowRepository flowRepository;
    private static SwitchOperationsService switchOperationsService;
    private static MirrorGroupRepository mirrorGroupRepository;
    private static FlowMirrorPointsRepository flowMirrorPointsRepository;
    private static FlowMirrorPathRepository flowMirrorPathRepository;
    private static LagLogicalPortRepository lagLogicalPortRepository;

    private static final String TEST_FLOW_ID_1 = "test_flow_1";
    private static final int TEST_FLOW_SRC_PORT = 13;
    private static final SwitchId TEST_SWITCH_ID = new SwitchId(1);
    private static final SwitchId TEST_SWITCH_ID_2 = new SwitchId(2);
    private static final Integer SERVER_42_PORT_1 = 1;
    private static final Integer SERVER_42_PORT_2 = 2;
    private static final Integer SERVER_42_VLAN_1 = 3;
    private static final Integer SERVER_42_VLAN_2 = 4;
    private static final int LAG_LOGICAL_PORT = 5;
    private static final int PHYSICAL_PORT_1 = 6;
    private static final int PHYSICAL_PORT_2 = 7;
    private static final MacAddress SERVER_42_MAC_ADDRESS_1 = new MacAddress("42:42:42:42:42:42");
    private static final MacAddress SERVER_42_MAC_ADDRESS_2 = new MacAddress("45:45:45:45:45:45");

    @BeforeClass
    public static void setUpOnce() {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        switchRepository = repositoryFactory.createSwitchRepository();
        switchPropertiesRepository = repositoryFactory.createSwitchPropertiesRepository();
        portPropertiesRepository = repositoryFactory.createPortPropertiesRepository();
        flowRepository = repositoryFactory.createFlowRepository();
        mirrorGroupRepository = repositoryFactory.createMirrorGroupRepository();
        flowMirrorPointsRepository = repositoryFactory.createFlowMirrorPointsRepository();
        flowMirrorPathRepository = repositoryFactory.createFlowMirrorPathRepository();
        lagLogicalPortRepository = repositoryFactory.createLagLogicalPortRepository();

        SwitchOperationsServiceCarrier carrier = new SwitchOperationsServiceCarrier() {
            @Override
            public void requestSwitchSync(SwitchId switchId) {
            }

            @Override
            public void enableServer42FlowRttOnSwitch(SwitchId switchId) {
            }

            @Override
            public void disableServer42FlowRttOnSwitch(SwitchId switchId) {
            }

            @Override
            public void enableServer42IslRttOnSwitch(SwitchId switchId) {
            }

            @Override
            public void disableServer42IslRttOnSwitch(SwitchId switchId) {
            }
        };
        ILinkOperationsServiceCarrier linkCarrier = new ILinkOperationsServiceCarrier() {
            @Override
            public void islBfdPropertiesChanged(Endpoint source, Endpoint destination) {
            }
        };
        switchOperationsService = new SwitchOperationsService(persistenceManager.getRepositoryFactory(),
                persistenceManager.getTransactionManager(), carrier, linkCarrier);
    }

    @Test
    public void shouldUpdateLinkUnderMaintenanceFlag() throws SwitchNotFoundException {
        createSwitch(TEST_SWITCH_ID);

        switchOperationsService.updateSwitchUnderMaintenanceFlag(TEST_SWITCH_ID, true);
        Switch sw = switchRepository.findById(TEST_SWITCH_ID).get();
        assertTrue(sw.isUnderMaintenance());

        switchOperationsService.updateSwitchUnderMaintenanceFlag(TEST_SWITCH_ID, false);
        sw = switchRepository.findById(TEST_SWITCH_ID).get();
        assertFalse(sw.isUnderMaintenance());
    }

    @Test
    public void shouldDeletePortPropertiesWhenDeletingSwitch() throws SwitchNotFoundException {
        Switch sw = createSwitch(TEST_SWITCH_ID);
        PortProperties portProperties = PortProperties.builder().switchObj(sw).port(7).discoveryEnabled(false).build();
        portPropertiesRepository.add(portProperties);

        switchOperationsService.deleteSwitch(TEST_SWITCH_ID, false);
        assertFalse(switchRepository.findById(TEST_SWITCH_ID).isPresent());
        assertTrue(portPropertiesRepository.findAll().isEmpty());
    }

    @Test(expected = IllegalSwitchPropertiesException.class)
    public void shouldValidateSupportedEncapsulationTypeWhenUpdatingSwitchProperties() {
        Switch sw = createSwitch(TEST_SWITCH_ID);
        createSwitchProperties(sw, Collections.singleton(FlowEncapsulationType.TRANSIT_VLAN), false, false, false);

        switchOperationsService.updateSwitchProperties(TEST_SWITCH_ID, new SwitchPropertiesDto());
    }

    @Test(expected = IllegalSwitchPropertiesException.class)
    public void shouldValidateMultiTableFlagWhenUpdatingSwitchProperties() {
        Switch sw = createSwitch(TEST_SWITCH_ID);
        createSwitchProperties(sw, Collections.singleton(FlowEncapsulationType.TRANSIT_VLAN), true, true, false);

        // user can't disable multiTable without disabling LLDP
        SwitchPropertiesDto update = new SwitchPropertiesDto();
        update.setSupportedTransitEncapsulation(
                Collections.singleton(org.openkilda.messaging.payload.flow.FlowEncapsulationType.TRANSIT_VLAN));
        update.setMultiTable(false);
        update.setSwitchLldp(true);

        switchOperationsService.updateSwitchProperties(TEST_SWITCH_ID, update);
    }

    @Test(expected = IllegalSwitchPropertiesException.class)
    public void shouldValidateFlowWithLldpFlagWhenUpdatingSwitchProperties() {
        Switch firstSwitch = createSwitch(TEST_SWITCH_ID);
        Switch secondSwitch = createSwitch(TEST_SWITCH_ID_2);

        Flow flow = Flow.builder()
                .flowId(TEST_FLOW_ID_1)
                .srcSwitch(firstSwitch)
                .destSwitch(secondSwitch)
                .detectConnectedDevices(new DetectConnectedDevices(
                        true, false, true, false, false, false, false, false))
                .build();
        flowRepository.add(flow);

        createSwitchProperties(
                firstSwitch, Collections.singleton(FlowEncapsulationType.TRANSIT_VLAN), true, false, false);

        // user can't disable multiTable if some flows has enabled detect connected devices via LLDP
        SwitchPropertiesDto update = new SwitchPropertiesDto();
        update.setSupportedTransitEncapsulation(
                Collections.singleton(org.openkilda.messaging.payload.flow.FlowEncapsulationType.TRANSIT_VLAN));
        update.setMultiTable(false);
        update.setSwitchLldp(false);

        switchOperationsService.updateSwitchProperties(TEST_SWITCH_ID, update);
    }

    @Test(expected = IllegalSwitchPropertiesException.class)
    public void shouldValidateMultiTableFlagWhenUpdatingSwitchPropertiesWithArp() {
        Switch sw = createSwitch(TEST_SWITCH_ID);
        createSwitchProperties(sw, Collections.singleton(FlowEncapsulationType.TRANSIT_VLAN), true, false, true);

        // user can't disable multiTable without disabling ARP
        SwitchPropertiesDto update = new SwitchPropertiesDto();
        update.setSupportedTransitEncapsulation(
                Collections.singleton(org.openkilda.messaging.payload.flow.FlowEncapsulationType.TRANSIT_VLAN));
        update.setMultiTable(false);
        update.setSwitchArp(true);

        switchOperationsService.updateSwitchProperties(TEST_SWITCH_ID, update);
    }

    @Test(expected = IllegalSwitchPropertiesException.class)
    public void shouldValidateFlowWithArpFlagWhenUpdatingSwitchProperties() {
        Switch firstSwitch = createSwitch(TEST_SWITCH_ID);
        Switch secondSwitch = createSwitch(TEST_SWITCH_ID_2);

        Flow flow = Flow.builder()
                .flowId(TEST_FLOW_ID_1)
                .srcSwitch(firstSwitch)
                .destSwitch(secondSwitch)
                .detectConnectedDevices(
                        new DetectConnectedDevices(false, true, false, true, false, false, false, false))
                .build();
        flowRepository.add(flow);

        createSwitchProperties(firstSwitch,
                Collections.singleton(FlowEncapsulationType.TRANSIT_VLAN), true, false, false);

        // user can't disable multiTable if some flows has enabled detect connected devices via ARP
        SwitchPropertiesDto update = new SwitchPropertiesDto();
        update.setSupportedTransitEncapsulation(
                Collections.singleton(org.openkilda.messaging.payload.flow.FlowEncapsulationType.TRANSIT_VLAN));
        update.setMultiTable(false);
        update.setSwitchArp(false);

        switchOperationsService.updateSwitchProperties(TEST_SWITCH_ID, update);
    }

    @Test
    public void shouldDisableSwitchLldpForFlow() {
        Switch firstSwitch = createSwitch(TEST_SWITCH_ID);
        Switch secondSwitch = createSwitch(TEST_SWITCH_ID_2);

        Flow flow = Flow.builder()
                .flowId(TEST_FLOW_ID_1)
                .srcSwitch(firstSwitch)
                .destSwitch(secondSwitch)
                .detectConnectedDevices(
                        new DetectConnectedDevices(false, false, false, false, true, true, true, true))
                .build();
        flowRepository.add(flow);

        createSwitchProperties(firstSwitch,
                Collections.singleton(FlowEncapsulationType.TRANSIT_VLAN), true, true, true);
        createSwitchProperties(secondSwitch,
                Collections.singleton(FlowEncapsulationType.TRANSIT_VLAN), true, true, true);

        SwitchPropertiesDto firstUpdate = new SwitchPropertiesDto();
        firstUpdate.setSupportedTransitEncapsulation(SUPPORTED_TRANSIT_ENCAPSULATION);
        firstUpdate.setMultiTable(true);
        firstUpdate.setSwitchLldp(false);
        firstUpdate.setSwitchArp(false);

        switchOperationsService.updateSwitchProperties(TEST_SWITCH_ID, firstUpdate);

        Optional<Flow> foundFlow = flowRepository.findById(TEST_FLOW_ID_1);
        assertTrue(foundFlow.isPresent());
        assertFalse(foundFlow.get().getDetectConnectedDevices().isSrcSwitchLldp());
        assertFalse(foundFlow.get().getDetectConnectedDevices().isSrcSwitchArp());
        assertTrue(foundFlow.get().getDetectConnectedDevices().isDstSwitchLldp());
        assertTrue(foundFlow.get().getDetectConnectedDevices().isDstSwitchArp());

        SwitchPropertiesDto secondUpdate = new SwitchPropertiesDto();
        secondUpdate.setSupportedTransitEncapsulation(SUPPORTED_TRANSIT_ENCAPSULATION);
        secondUpdate.setMultiTable(true);
        secondUpdate.setSwitchLldp(false);
        secondUpdate.setSwitchArp(false);

        switchOperationsService.updateSwitchProperties(TEST_SWITCH_ID_2, secondUpdate);

        foundFlow = flowRepository.findById(TEST_FLOW_ID_1);
        assertTrue(foundFlow.isPresent());
        assertFalse(foundFlow.get().getDetectConnectedDevices().isSrcSwitchLldp());
        assertFalse(foundFlow.get().getDetectConnectedDevices().isSrcSwitchArp());
        assertFalse(foundFlow.get().getDetectConnectedDevices().isDstSwitchLldp());
        assertFalse(foundFlow.get().getDetectConnectedDevices().isDstSwitchArp());
    }

    @Test
    public void shouldEnableSwitchLldpForFlow() {
        Switch firstSwitch = createSwitch(TEST_SWITCH_ID);
        Switch secondSwitch = createSwitch(TEST_SWITCH_ID_2);

        Flow flow = Flow.builder()
                .flowId(TEST_FLOW_ID_1)
                .srcSwitch(firstSwitch)
                .destSwitch(secondSwitch)
                .detectConnectedDevices(
                        new DetectConnectedDevices(false, false, false, false, false, false, false, false))
                .build();
        flowRepository.add(flow);

        createSwitchProperties(firstSwitch,
                Collections.singleton(FlowEncapsulationType.TRANSIT_VLAN), true, false, false);
        createSwitchProperties(secondSwitch,
                Collections.singleton(FlowEncapsulationType.TRANSIT_VLAN), true, false, false);

        SwitchPropertiesDto firstUpdate = new SwitchPropertiesDto();
        firstUpdate.setSupportedTransitEncapsulation(SUPPORTED_TRANSIT_ENCAPSULATION);
        firstUpdate.setMultiTable(true);
        firstUpdate.setSwitchLldp(true);
        firstUpdate.setSwitchArp(true);

        switchOperationsService.updateSwitchProperties(TEST_SWITCH_ID, firstUpdate);

        Optional<Flow> foundFlow = flowRepository.findById(TEST_FLOW_ID_1);
        assertTrue(foundFlow.isPresent());
        assertTrue(foundFlow.get().getDetectConnectedDevices().isSrcSwitchLldp());
        assertTrue(foundFlow.get().getDetectConnectedDevices().isSrcSwitchArp());
        assertFalse(foundFlow.get().getDetectConnectedDevices().isDstSwitchLldp());
        assertFalse(foundFlow.get().getDetectConnectedDevices().isDstSwitchArp());

        SwitchPropertiesDto secondUpdate = new SwitchPropertiesDto();
        secondUpdate.setSupportedTransitEncapsulation(SUPPORTED_TRANSIT_ENCAPSULATION);
        secondUpdate.setMultiTable(true);
        secondUpdate.setSwitchLldp(true);
        secondUpdate.setSwitchArp(true);

        switchOperationsService.updateSwitchProperties(TEST_SWITCH_ID_2, secondUpdate);

        foundFlow = flowRepository.findById(TEST_FLOW_ID_1);
        assertTrue(foundFlow.isPresent());
        assertTrue(foundFlow.get().getDetectConnectedDevices().isSrcSwitchLldp());
        assertTrue(foundFlow.get().getDetectConnectedDevices().isSrcSwitchArp());
        assertTrue(foundFlow.get().getDetectConnectedDevices().isDstSwitchLldp());
        assertTrue(foundFlow.get().getDetectConnectedDevices().isDstSwitchArp());
    }

    @Test
    public void shouldUpdateServer42FlowRttSwitchProperties() {
        Switch sw = createSwitch(TEST_SWITCH_ID);
        createServer42SwitchProperties(sw, false, SERVER_42_PORT_1, SERVER_42_VLAN_1, SERVER_42_MAC_ADDRESS_1);

        SwitchPropertiesDto update = new SwitchPropertiesDto();
        update.setSupportedTransitEncapsulation(
                Collections.singleton(org.openkilda.messaging.payload.flow.FlowEncapsulationType.TRANSIT_VLAN));
        update.setMultiTable(true);
        update.setServer42FlowRtt(true);
        update.setServer42Port(SERVER_42_PORT_2);
        update.setServer42Vlan(SERVER_42_VLAN_2);
        update.setServer42MacAddress(SERVER_42_MAC_ADDRESS_2);

        switchOperationsService.updateSwitchProperties(TEST_SWITCH_ID, update);
        Optional<SwitchProperties> updated = switchPropertiesRepository.findBySwitchId(TEST_SWITCH_ID);

        assertTrue(updated.isPresent());
        assertTrue(updated.get().isServer42FlowRtt());
        assertEquals(SERVER_42_PORT_2, updated.get().getServer42Port());
        assertEquals(SERVER_42_VLAN_2, updated.get().getServer42Vlan());
        assertEquals(SERVER_42_MAC_ADDRESS_2, updated.get().getServer42MacAddress());
    }

    @Test(expected = IllegalSwitchPropertiesException.class)
    public void shouldValidateServer42VlanWhenEnableFlowRttInSwitchProperties() {
        // user can't enable server42FlowRtt and do not specify server42Vlan
        SwitchPropertiesDto properties = new SwitchPropertiesDto();
        properties.setServer42FlowRtt(true);
        properties.setServer42Port(SERVER_42_PORT_2);
        properties.setServer42Vlan(null);
        properties.setServer42MacAddress(SERVER_42_MAC_ADDRESS_2);
        runInvalidServer42PropsTest(properties);
    }

    @Test(expected = IllegalSwitchPropertiesException.class)
    public void shouldValidateServer42PortWhenEnableFlowRttInSwitchProperties() {
        // user can't enable server42FlowRtt and do not specify server42Port
        SwitchPropertiesDto properties = new SwitchPropertiesDto();
        properties.setServer42FlowRtt(true);
        properties.setServer42Port(null);
        properties.setServer42Vlan(SERVER_42_VLAN_2);
        properties.setServer42MacAddress(SERVER_42_MAC_ADDRESS_2);
        runInvalidServer42PropsTest(properties);
    }

    @Test(expected = IllegalSwitchPropertiesException.class)
    public void shouldValidateServer42MacAddressWhenEnableFlowRttInSwitchProperties() {
        // user can't enable server42FlowRtt and do not specify server42MacAddress
        SwitchPropertiesDto properties = new SwitchPropertiesDto();
        properties.setServer42FlowRtt(true);
        properties.setServer42Port(SERVER_42_PORT_2);
        properties.setServer42Vlan(SERVER_42_VLAN_2);
        properties.setServer42MacAddress(null);
        runInvalidServer42PropsTest(properties);
    }

    @Test(expected = IllegalSwitchPropertiesException.class)
    public void shouldValidateFlowMirrorPointsWhenUpdatingSwitchLldpProperties() {
        Switch mirrorSwitch = createSwitch(TEST_SWITCH_ID);

        MirrorGroup mirrorGroup = MirrorGroup.builder()
                .switchId(TEST_SWITCH_ID)
                .groupId(new GroupId(12L))
                .pathId(new PathId("test_path_id"))
                .flowId(TEST_FLOW_ID_1)
                .mirrorGroupType(MirrorGroupType.TRAFFIC_INTEGRITY)
                .mirrorDirection(MirrorDirection.INGRESS)
                .build();
        mirrorGroupRepository.add(mirrorGroup);

        FlowMirrorPoints flowMirrorPoints = FlowMirrorPoints.builder()
                .mirrorGroup(mirrorGroup)
                .mirrorSwitch(mirrorSwitch)
                .build();
        flowMirrorPointsRepository.add(flowMirrorPoints);

        createSwitchProperties(
                mirrorSwitch, Collections.singleton(FlowEncapsulationType.TRANSIT_VLAN), true, false, false);

        SwitchPropertiesDto update = new SwitchPropertiesDto();
        update.setSupportedTransitEncapsulation(
                Collections.singleton(org.openkilda.messaging.payload.flow.FlowEncapsulationType.TRANSIT_VLAN));
        update.setMultiTable(true);
        update.setSwitchLldp(true);

        switchOperationsService.updateSwitchProperties(TEST_SWITCH_ID, update);
    }

    @Test(expected = IllegalSwitchPropertiesException.class)
    public void shouldValidateFlowMirrorPointsWhenUpdatingSwitchArpProperties() {
        Switch mirrorSwitch = createSwitch(TEST_SWITCH_ID);

        MirrorGroup mirrorGroup = MirrorGroup.builder()
                .switchId(TEST_SWITCH_ID)
                .groupId(new GroupId(12L))
                .pathId(new PathId("test_path_id"))
                .flowId(TEST_FLOW_ID_1)
                .mirrorGroupType(MirrorGroupType.TRAFFIC_INTEGRITY)
                .mirrorDirection(MirrorDirection.INGRESS)
                .build();
        mirrorGroupRepository.add(mirrorGroup);

        FlowMirrorPoints flowMirrorPoints = FlowMirrorPoints.builder()
                .mirrorGroup(mirrorGroup)
                .mirrorSwitch(mirrorSwitch)
                .build();
        flowMirrorPointsRepository.add(flowMirrorPoints);

        createSwitchProperties(
                mirrorSwitch, Collections.singleton(FlowEncapsulationType.TRANSIT_VLAN), true, false, false);

        SwitchPropertiesDto update = new SwitchPropertiesDto();
        update.setSupportedTransitEncapsulation(
                Collections.singleton(org.openkilda.messaging.payload.flow.FlowEncapsulationType.TRANSIT_VLAN));
        update.setMultiTable(true);
        update.setSwitchArp(true);

        switchOperationsService.updateSwitchProperties(TEST_SWITCH_ID, update);
    }

    @Test(expected = IllegalSwitchPropertiesException.class)
    public void shouldValidateFlowWhenUpdatingServer42PortSwitchProperties() {
        Switch firstSwitch = createSwitch(TEST_SWITCH_ID);
        Switch secondSwitch = createSwitch(TEST_SWITCH_ID_2);

        Flow flow = Flow.builder()
                .flowId(TEST_FLOW_ID_1)
                .srcSwitch(firstSwitch)
                .srcPort(TEST_FLOW_SRC_PORT)
                .destSwitch(secondSwitch)
                .detectConnectedDevices(
                        new DetectConnectedDevices(false, true, false, true, false, false, false, false))
                .build();
        flowRepository.add(flow);

        createSwitchProperties(firstSwitch,
                Collections.singleton(FlowEncapsulationType.TRANSIT_VLAN), true, false, false);

        SwitchPropertiesDto update = new SwitchPropertiesDto();
        update.setSupportedTransitEncapsulation(
                Collections.singleton(org.openkilda.messaging.payload.flow.FlowEncapsulationType.TRANSIT_VLAN));
        update.setServer42Port(TEST_FLOW_SRC_PORT);

        switchOperationsService.updateSwitchProperties(TEST_SWITCH_ID, update);
    }

    @Test(expected = IllegalSwitchPropertiesException.class)
    public void shouldValidateFlowMirrorPathWhenUpdatingServer42PortSwitchProperties() {
        Switch firstSwitch = createSwitch(TEST_SWITCH_ID);
        Switch secondSwitch = createSwitch(TEST_SWITCH_ID_2);

        FlowMirrorPath flowMirrorPath = FlowMirrorPath.builder()
                .pathId(new PathId("test_path_id"))
                .mirrorSwitch(secondSwitch)
                .egressSwitch(firstSwitch)
                .egressPort(TEST_FLOW_SRC_PORT)
                .build();
        flowMirrorPathRepository.add(flowMirrorPath);

        createSwitchProperties(firstSwitch,
                Collections.singleton(FlowEncapsulationType.TRANSIT_VLAN), true, false, false);

        SwitchPropertiesDto update = new SwitchPropertiesDto();
        update.setSupportedTransitEncapsulation(
                Collections.singleton(org.openkilda.messaging.payload.flow.FlowEncapsulationType.TRANSIT_VLAN));
        update.setServer42Port(TEST_FLOW_SRC_PORT);

        switchOperationsService.updateSwitchProperties(TEST_SWITCH_ID, update);
    }

    @Test
    public void shouldUpdateServer42IslRttSwitchProperties() {
        Switch sw = createSwitch(TEST_SWITCH_ID);
        SwitchProperties switchProperties = SwitchProperties.builder()
                .switchObj(sw)
                .supportedTransitEncapsulation(Collections.singleton(FlowEncapsulationType.TRANSIT_VLAN))
                .multiTable(false)
                .server42IslRtt(SwitchProperties.RttState.DISABLED)
                .server42Port(SERVER_42_PORT_1).server42Vlan(SERVER_42_VLAN_1)
                .server42MacAddress(SERVER_42_MAC_ADDRESS_1)
                .build();
        switchPropertiesRepository.add(switchProperties);

        SwitchPropertiesDto update = new SwitchPropertiesDto();
        update.setSupportedTransitEncapsulation(
                Collections.singleton(org.openkilda.messaging.payload.flow.FlowEncapsulationType.TRANSIT_VLAN));
        update.setServer42IslRtt(RttState.ENABLED);
        update.setServer42Port(SERVER_42_PORT_2);
        update.setServer42Vlan(SERVER_42_VLAN_2);
        update.setServer42MacAddress(SERVER_42_MAC_ADDRESS_2);

        switchOperationsService.updateSwitchProperties(TEST_SWITCH_ID, update);
        Optional<SwitchProperties> updated = switchPropertiesRepository.findBySwitchId(TEST_SWITCH_ID);

        assertTrue(updated.isPresent());
        assertEquals(SwitchProperties.RttState.ENABLED, updated.get().getServer42IslRtt());
        assertEquals(SERVER_42_PORT_2, updated.get().getServer42Port());
        assertEquals(SERVER_42_VLAN_2, updated.get().getServer42Vlan());
        assertEquals(SERVER_42_MAC_ADDRESS_2, updated.get().getServer42MacAddress());
    }

    @Test
    public void shouldUpdateServer42IslRttSwitchPropertiesToAuto() {
        Switch sw = createSwitch(TEST_SWITCH_ID);
        SwitchProperties switchProperties = SwitchProperties.builder()
                .switchObj(sw)
                .supportedTransitEncapsulation(Collections.singleton(FlowEncapsulationType.TRANSIT_VLAN))
                .multiTable(false)
                .server42IslRtt(SwitchProperties.RttState.DISABLED)
                .server42Port(SERVER_42_PORT_1).server42Vlan(SERVER_42_VLAN_1)
                .server42MacAddress(SERVER_42_MAC_ADDRESS_1)
                .build();
        switchPropertiesRepository.add(switchProperties);

        SwitchPropertiesDto update = new SwitchPropertiesDto();
        update.setSupportedTransitEncapsulation(
                Collections.singleton(org.openkilda.messaging.payload.flow.FlowEncapsulationType.TRANSIT_VLAN));
        update.setServer42IslRtt(RttState.AUTO);
        update.setServer42Port(SERVER_42_PORT_2);
        update.setServer42Vlan(SERVER_42_VLAN_2);
        update.setServer42MacAddress(SERVER_42_MAC_ADDRESS_2);

        switchOperationsService.updateSwitchProperties(TEST_SWITCH_ID, update);
        Optional<SwitchProperties> updated = switchPropertiesRepository.findBySwitchId(TEST_SWITCH_ID);

        assertTrue(updated.isPresent());
        assertEquals(SwitchProperties.RttState.AUTO, updated.get().getServer42IslRtt());
        assertEquals(SERVER_42_PORT_2, updated.get().getServer42Port());
        assertEquals(SERVER_42_VLAN_2, updated.get().getServer42Vlan());
        assertEquals(SERVER_42_MAC_ADDRESS_2, updated.get().getServer42MacAddress());
    }

    @Test(expected = IllegalSwitchPropertiesException.class)
    public void shouldValidateServer42VlanWhenEnableIslRttInSwitchProperties() {
        // user can't enable server42FlowRtt and do not specify server42Vlan
        SwitchPropertiesDto properties = new SwitchPropertiesDto();
        properties.setServer42IslRtt(SwitchPropertiesDto.RttState.ENABLED);
        properties.setServer42Port(SERVER_42_PORT_2);
        properties.setServer42Vlan(null);
        properties.setServer42MacAddress(SERVER_42_MAC_ADDRESS_2);
        runInvalidServer42PropsTest(properties);
    }

    @Test(expected = IllegalSwitchPropertiesException.class)
    public void shouldValidateServer42PortWhenEnableIslRttInSwitchProperties() {
        // user can't enable server42FlowRtt and do not specify server42Port
        SwitchPropertiesDto properties = new SwitchPropertiesDto();
        properties.setServer42IslRtt(SwitchPropertiesDto.RttState.ENABLED);
        properties.setServer42Port(null);
        properties.setServer42Vlan(SERVER_42_VLAN_2);
        properties.setServer42MacAddress(SERVER_42_MAC_ADDRESS_2);
        runInvalidServer42PropsTest(properties);
    }

    @Test(expected = IllegalSwitchPropertiesException.class)
    public void shouldValidateServer42MacAddressWhenEnableIslRttInSwitchProperties() {
        // user can't enable server42FlowRtt and do not specify server42MacAddress
        SwitchPropertiesDto properties = new SwitchPropertiesDto();
        properties.setServer42IslRtt(SwitchPropertiesDto.RttState.ENABLED);
        properties.setServer42Port(SERVER_42_PORT_2);
        properties.setServer42Vlan(SERVER_42_VLAN_2);
        properties.setServer42MacAddress(null);
        runInvalidServer42PropsTest(properties);
    }

    @Test
    public void shouldPatchSwitch() throws SwitchNotFoundException {
        createSwitch(TEST_SWITCH_ID);

        SwitchPatch switchPatch =
                new SwitchPatch("pop", new SwitchLocation(48.860611, 2.337633, "street", "city", "country"));
        switchOperationsService.patchSwitch(TEST_SWITCH_ID, switchPatch);

        Switch updatedSwitch = switchRepository.findById(TEST_SWITCH_ID).get();
        assertEquals(switchPatch.getPop(), updatedSwitch.getPop());
        assertEquals(switchPatch.getLocation().getLatitude(), updatedSwitch.getLatitude());
        assertEquals(switchPatch.getLocation().getLongitude(), updatedSwitch.getLongitude());
        assertEquals(switchPatch.getLocation().getStreet(), updatedSwitch.getStreet());
        assertEquals(switchPatch.getLocation().getCity(), updatedSwitch.getCity());
        assertEquals(switchPatch.getLocation().getCountry(), updatedSwitch.getCountry());
    }

    @Test
    public void shouldSetNullPopWhenPopIsEmptyString() throws SwitchNotFoundException {
        createSwitch(TEST_SWITCH_ID);

        SwitchPatch switchPatch = new SwitchPatch("", null);
        switchOperationsService.patchSwitch(TEST_SWITCH_ID, switchPatch);

        Switch updatedSwitch = switchRepository.findById(TEST_SWITCH_ID).get();
        assertNull(updatedSwitch.getPop());
    }

    @Test
    public void shouldReturnLagPorts() throws SwitchNotFoundException {
        createSwitch(TEST_SWITCH_ID);

        LagLogicalPort lagLogicalPort = new LagLogicalPort(TEST_SWITCH_ID, LAG_LOGICAL_PORT,
                Lists.newArrayList(PHYSICAL_PORT_1, PHYSICAL_PORT_2), true);
        lagLogicalPortRepository.add(lagLogicalPort);

        Collection<LagLogicalPort> ports = switchOperationsService.getSwitchLagPorts(TEST_SWITCH_ID);

        assertEquals(1, ports.size());
        assertEquals(LAG_LOGICAL_PORT, ports.iterator().next().getLogicalPortNumber());
        assertEquals(PHYSICAL_PORT_1, ports.iterator().next().getPhysicalPorts().get(0).getPortNumber());
        assertEquals(PHYSICAL_PORT_2, ports.iterator().next().getPhysicalPorts().get(1).getPortNumber());
        assertTrue(ports.iterator().next().isLacpReply());
    }

    @Test(expected = SwitchNotFoundException.class)
    public void shouldThrowExceptionDuringGettingLagPorts() throws SwitchNotFoundException {
        switchOperationsService.getSwitchLagPorts(TEST_SWITCH_ID);
    }

    private void runInvalidServer42PropsTest(SwitchPropertiesDto invalidProperties) {
        invalidProperties.setMultiTable(true);
        invalidProperties.setSupportedTransitEncapsulation(Collections.singleton(
                org.openkilda.messaging.payload.flow.FlowEncapsulationType.TRANSIT_VLAN));

        Switch sw = createSwitch(TEST_SWITCH_ID);
        createServer42SwitchProperties(sw, false, SERVER_42_PORT_1, SERVER_42_VLAN_1, SERVER_42_MAC_ADDRESS_1);

        switchOperationsService.updateSwitchProperties(TEST_SWITCH_ID, invalidProperties);
    }

    private void createServer42SwitchProperties(
            Switch sw, boolean sever42FlowRtt, Integer port, Integer vlan, MacAddress macAddress) {
        SwitchProperties switchProperties = SwitchProperties.builder()
                .switchObj(sw)
                .supportedTransitEncapsulation(Collections.singleton(FlowEncapsulationType.TRANSIT_VLAN))
                .multiTable(true)
                .server42FlowRtt(sever42FlowRtt)
                .server42Port(port)
                .server42Vlan(vlan)
                .server42MacAddress(macAddress)
                .build();
        switchPropertiesRepository.add(switchProperties);
    }

    private void createSwitchProperties(Switch sw, Set<FlowEncapsulationType> transitEncapsulation, boolean multiTable,
                                        boolean switchLldp, boolean switchArp) {
        SwitchProperties switchProperties = SwitchProperties.builder()
                .switchObj(sw)
                .supportedTransitEncapsulation(transitEncapsulation)
                .multiTable(multiTable)
                .switchLldp(switchLldp)
                .switchArp(switchArp)
                .build();
        switchPropertiesRepository.add(switchProperties);
    }

    private Switch createSwitch(SwitchId switchId) {
        Switch sw = Switch.builder()
                .switchId(switchId)
                .status(SwitchStatus.ACTIVE)
                .features(Sets.newHashSet(SwitchFeature.MULTI_TABLE))
                .build();
        switchRepository.add(sw);
        return sw;
    }
}
