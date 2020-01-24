/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.connecteddevices.service;

import static com.google.common.collect.Lists.newArrayList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.openkilda.model.ConnectedDeviceType.LLDP;

import org.openkilda.messaging.info.event.SwitchLldpInfoData;
import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowCookie;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchConnectedDevice;
import org.openkilda.model.SwitchId;
import org.openkilda.model.TransitVlan;
import org.openkilda.persistence.repositories.FlowCookieRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.SwitchConnectedDeviceRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.wfm.Neo4jBasedTest;
import org.openkilda.wfm.topology.connecteddevices.service.PacketService.FlowRelatedData;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RunWith(JUnitParamsRunner.class)
public class PacketServiceTest extends Neo4jBasedTest {
    public static final long COOKIE = Cookie.LLDP_INPUT_PRE_DROP_COOKIE;
    public static final String MAC_ADDRESS_1 = "00:00:00:00:00:01";
    public static final String MAC_ADDRESS_2 = "00:00:00:00:00:02";
    public static final String CHASSIS_ID_1 = "00:00:00:00:00:03";
    public static final String CHASSIS_ID_2 = "00:00:00:00:00:04";
    public static final String PORT_ID_1 = "00:00:00:00:00:05";
    public static final String PORT_ID_2 = "00:00:00:00:00:06";
    public static final String PORT_DESCRIPTION_1 = "some_port_1";
    public static final String PORT_DESCRIPTION_2 = "some_port_2";
    public static final String SYSTEM_NAME_1 = "ubuntu_1";
    public static final String SYSTEM_NAME_2 = "ubuntu_2";
    public static final String SYSTEM_DESCRIPTION_1 = "ubuntu 18.04";
    public static final String SYSTEM_DESCRIPTION_2 = "ubuntu 19.05";
    public static final String CAPABILITIES_1 = "cap_1";
    public static final String CAPABILITIES_2 = "cap_2";
    public static final String MANAGEMENT_ADDRESS_1 = "127.0.0.1";
    public static final String MANAGEMENT_ADDRESS_2 = "192.168.1.1";
    public static final String FLOW_ID = "flow1";
    public static final String PATH_ID = "path1";
    public static final SwitchId SWITCH_ID_1 = new SwitchId("01");
    public static final SwitchId SWITCH_ID_2 = new SwitchId("02");
    public static final int PORT_NUMBER_1 = 1;
    public static final int PORT_NUMBER_2 = 2;
    public static final int VLAN_0 = 0;
    public static final int VLAN_1 = 1;
    public static final int VLAN_2 = 2;
    public static final int VLAN_3 = 3;
    public static final int TTL_1 = 120;
    public static final int TTL_2 = 240;

    private static SwitchConnectedDeviceRepository switchConnectedDeviceRepository;
    private static SwitchRepository switchRepository;
    private static FlowCookieRepository flowCookieRepository;
    private static FlowRepository flowRepository;
    private static TransitVlanRepository transitVlanRepository;
    private static PacketService packetService;

    @BeforeClass
    public static void setUpOnce() {
        switchConnectedDeviceRepository = persistenceManager.getRepositoryFactory()
                .createSwitchConnectedDeviceRepository();
        switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        flowCookieRepository = persistenceManager.getRepositoryFactory().createFlowCookieRepository();
        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        transitVlanRepository = persistenceManager.getRepositoryFactory().createTransitVlanRepository();
        packetService = new PacketService(persistenceManager);
    }

    @Before
    public void setUp() {
        switchRepository.createOrUpdate(Switch.builder().switchId(SWITCH_ID_1).build());
        switchRepository.createOrUpdate(Switch.builder().switchId(SWITCH_ID_2).build());
    }

    @Test
    public void testHandleLldpDataSameTimeOnCreate() {
        flowCookieRepository.createOrUpdate(new FlowCookie(FLOW_ID, COOKIE));
        SwitchLldpInfoData data = createSwitchLldpInfoData();
        packetService.handleSwitchLldpData(data);
        Collection<SwitchConnectedDevice> devices = switchConnectedDeviceRepository.findAll();
        assertEquals(1, devices.size());
        assertEquals(devices.iterator().next().getTimeFirstSeen(), devices.iterator().next().getTimeLastSeen());
    }

    @Test
    public void testHandleLldpDataDifferentTimeOnUpdate() throws InterruptedException {
        flowCookieRepository.createOrUpdate(new FlowCookie(FLOW_ID, COOKIE));
        // create
        packetService.handleSwitchLldpData(createSwitchLldpInfoData());

        Thread.sleep(10);
        // update
        packetService.handleSwitchLldpData(createSwitchLldpInfoData());

        Collection<SwitchConnectedDevice> devices = switchConnectedDeviceRepository.findAll();
        assertEquals(1, devices.size());
        assertNotEquals(devices.iterator().next().getTimeFirstSeen(), devices.iterator().next().getTimeLastSeen());
    }

    @Test
    public void testHandleSwitchLldpDataNonExistentSwitch() {
        SwitchLldpInfoData data = createSwitchLldpInfoData();
        data.setSwitchId(new SwitchId("12345"));
        packetService.handleSwitchLldpData(data);
        assertTrue(switchConnectedDeviceRepository.findAll().isEmpty());
    }

    @Test
    public void testHandleSwitchLldpDataDifferentSwitchId() {
        SwitchLldpInfoData updatedData = createSwitchLldpInfoData();
        updatedData.setSwitchId(SWITCH_ID_2);
        runHandleSwitchLldpWithAddedDevice(updatedData);
    }

    @Test
    public void testHandleSwitchLldpDataDifferentPortNumber() {
        SwitchLldpInfoData updatedData = createSwitchLldpInfoData();
        updatedData.setPortNumber(PORT_NUMBER_2);
        runHandleSwitchLldpWithAddedDevice(updatedData);
    }

    @Test
    public void testHandleSwitchLldpDataDifferentVlan() {
        SwitchLldpInfoData updatedData = createSwitchLldpInfoData();
        updatedData.setVlans(Collections.singletonList(VLAN_2));
        runHandleSwitchLldpWithAddedDevice(updatedData);
    }

    @Test
    public void testHandleSwitchLldpDataDifferentMac() {
        SwitchLldpInfoData updatedData = createSwitchLldpInfoData();
        updatedData.setMacAddress(MAC_ADDRESS_2);
        runHandleSwitchLldpWithAddedDevice(updatedData);
    }

    @Test
    public void testHandleSwitchLldpDataDifferentPortId() {
        SwitchLldpInfoData updatedData = createSwitchLldpInfoData();
        updatedData.setPortId(PORT_ID_2);
        runHandleSwitchLldpWithAddedDevice(updatedData);
    }

    @Test
    public void testHandleSwitchLldpDataDifferentChassisId() {
        SwitchLldpInfoData updatedData = createSwitchLldpInfoData();
        updatedData.setChassisId(CHASSIS_ID_2);
        runHandleSwitchLldpWithAddedDevice(updatedData);
    }

    @Test
    public void testHandleSwitchLldpDataUpdateDifferentPortDescription() throws InterruptedException {
        SwitchLldpInfoData updatedData = createSwitchLldpInfoData();
        updatedData.setPortDescription(PORT_DESCRIPTION_2);
        runHandleSwitchLldpWithUpdatedDevice(updatedData);
    }

    @Test
    public void testHandleSwitchLldpDataUpdateDifferentManagementAddress() throws InterruptedException {
        SwitchLldpInfoData updatedData = createSwitchLldpInfoData();
        updatedData.setManagementAddress(MANAGEMENT_ADDRESS_2);
        runHandleSwitchLldpWithUpdatedDevice(updatedData);
    }

    @Test
    public void testHandleSwitchLldpDataUpdateDifferentSystemName() throws InterruptedException {
        SwitchLldpInfoData updatedData = createSwitchLldpInfoData();
        updatedData.setSystemName(SYSTEM_NAME_2);
        runHandleSwitchLldpWithUpdatedDevice(updatedData);
    }

    @Test
    public void testHandleSwitchLldpDataUpdateDifferentSystemDescription() throws InterruptedException {
        SwitchLldpInfoData updatedData = createSwitchLldpInfoData();
        updatedData.setSystemDescription(SYSTEM_DESCRIPTION_2);
        runHandleSwitchLldpWithUpdatedDevice(updatedData);
    }

    @Test
    public void testHandleSwitchLldpDataUpdateDifferentSystemCapabilities() throws InterruptedException {
        SwitchLldpInfoData updatedData = createSwitchLldpInfoData();
        updatedData.setSystemCapabilities(CAPABILITIES_2);
        runHandleSwitchLldpWithUpdatedDevice(updatedData);
    }

    @Test
    public void testHandleSwitchLldpDataUpdateDifferentTtl() throws InterruptedException {
        SwitchLldpInfoData updatedData = createSwitchLldpInfoData();
        updatedData.setTtl(TTL_2);
        runHandleSwitchLldpWithUpdatedDevice(updatedData);
    }

    private Object[][] getOneSwitchOnePortFlowParameters() {
        return new Object[][] {
                // inVlan, srcVlan, dstVlan, vlansInPacket, sourceSwitch
                {VLAN_0, VLAN_0, VLAN_2, newArrayList(VLAN_2), true},
                {VLAN_1, VLAN_0, VLAN_2, newArrayList(VLAN_2, VLAN_1), true},
                {VLAN_1, VLAN_1, VLAN_2, newArrayList(VLAN_2), true},
                {VLAN_0, VLAN_2, VLAN_0, newArrayList(VLAN_2), false},
                {VLAN_1, VLAN_2, VLAN_0, newArrayList(VLAN_2, VLAN_1), false},
                {VLAN_1, VLAN_2, VLAN_1, newArrayList(VLAN_2), false}
        };
    }

    @Test
    @Parameters(method = "getOneSwitchOnePortFlowParameters")
    public void findFlowRelatedDataForOneSwitchOnePortFlowTest(
            int inVlan, int srcVlan, int dstVlan, List<Integer> vlansInPacket, boolean source) {
        createFlow(FLOW_ID, srcVlan, dstVlan, null, true, true);
        SwitchLldpInfoData data = createSwitchLldpInfoData(SWITCH_ID_1, vlansInPacket, PORT_NUMBER_1);
        FlowRelatedData flowRelatedData = packetService.findFlowRelatedDataForOneSwitchFlow(data);
        assertEquals(FLOW_ID, flowRelatedData.getFlowId());
        assertEquals(inVlan, flowRelatedData.getOriginalVlan());
        assertEquals(source, flowRelatedData.getSource());
    }

    private Object[][] getOneSwitchFlowParameters() {
        return new Object[][] {
                // inVlan, srcVlan, dstVlan, vlansInPacket, sourceSwitch
                {VLAN_0, VLAN_0, VLAN_0, newArrayList(), true},
                {VLAN_1, VLAN_0, VLAN_0, newArrayList(VLAN_1), true},
                {VLAN_0, VLAN_0, VLAN_2, newArrayList(VLAN_2), true},
                {VLAN_1, VLAN_0, VLAN_2, newArrayList(VLAN_2, VLAN_1), true},
                {VLAN_1, VLAN_1, VLAN_2, newArrayList(VLAN_2), true},
                {VLAN_0, VLAN_0, VLAN_0, newArrayList(), false},
                {VLAN_1, VLAN_0, VLAN_0, newArrayList(VLAN_1), false},
                {VLAN_0, VLAN_2, VLAN_0, newArrayList(VLAN_2), false},
                {VLAN_1, VLAN_2, VLAN_0, newArrayList(VLAN_2, VLAN_1), false},
                {VLAN_1, VLAN_2, VLAN_1, newArrayList(VLAN_2), false}
        };
    }

    @Test
    @Parameters(method = "getOneSwitchFlowParameters")
    public void findFlowRelatedDataForOneSwitchFlowTest(
            int inVlan, int srcVlan, int dstVlan, List<Integer> vlansInPacket, boolean source) {
        createFlow(FLOW_ID, srcVlan, dstVlan, null, true, false);
        SwitchLldpInfoData data = createSwitchLldpInfoData(
                SWITCH_ID_1, vlansInPacket, source ? PORT_NUMBER_1 : PORT_NUMBER_2);
        FlowRelatedData flowRelatedData = packetService.findFlowRelatedDataForOneSwitchFlow(data);
        assertEquals(FLOW_ID, flowRelatedData.getFlowId());
        assertEquals(inVlan, flowRelatedData.getOriginalVlan());
        assertEquals(source, flowRelatedData.getSource());
    }

    private Object[][] getVlanFlowParameters() {
        return new Object[][] {
                // inVlan, srcVlan, dstVlan, transitVlan, vlansInPacket, sourceSwitch
                {VLAN_0, VLAN_0, VLAN_3, VLAN_2, newArrayList(VLAN_2), true},
                {VLAN_1, VLAN_0, VLAN_3, VLAN_2, newArrayList(VLAN_2, VLAN_1), true},
                {VLAN_1, VLAN_1, VLAN_3, VLAN_2, newArrayList(VLAN_2), true},
                {VLAN_0, VLAN_3, VLAN_0, VLAN_2, newArrayList(VLAN_2), false},
                {VLAN_1, VLAN_3, VLAN_0, VLAN_2, newArrayList(VLAN_2, VLAN_1), false},
                {VLAN_1, VLAN_3, VLAN_1, VLAN_2, newArrayList(VLAN_2), false}
        };
    }

    @Test
    @Parameters(method = "getVlanFlowParameters")
    public void findFlowRelatedDataForVlanFlowTest(
            int inVlan, int srcVlan, int dstVlan, int transitVlan, List<Integer> vlansInPacket, boolean source) {
        createFlow(FLOW_ID, srcVlan, dstVlan, transitVlan, false, false);
        SwitchLldpInfoData data = createSwitchLldpInfoData(
                source ? SWITCH_ID_1 : SWITCH_ID_2, vlansInPacket, source ? PORT_NUMBER_1 : PORT_NUMBER_2);
        FlowRelatedData flowRelatedData = packetService.findFlowRelatedDataForVlanFlow(data);
        assertEquals(FLOW_ID, flowRelatedData.getFlowId());
        assertEquals(inVlan, flowRelatedData.getOriginalVlan());
        assertEquals(source, flowRelatedData.getSource());
    }

    private Object[][] getInOutVlanCombinationForVxlanParameters() {
        return new Object[][] {
                // inVlan, srcVlan, dstVlan, vlansInPacket, sourceSwitch
                {VLAN_0, VLAN_0, VLAN_0, newArrayList(), true},
                {VLAN_0, VLAN_0, VLAN_2, newArrayList(), true},
                {VLAN_1, VLAN_0, VLAN_2, newArrayList(VLAN_1), true},
                {VLAN_1, VLAN_1, VLAN_2, newArrayList(VLAN_1), true},
                {VLAN_0, VLAN_0, VLAN_0, newArrayList(), false},
                {VLAN_0, VLAN_2, VLAN_0, newArrayList(), false},
                {VLAN_1, VLAN_2, VLAN_0, newArrayList(VLAN_1), false},
                {VLAN_1, VLAN_2, VLAN_1, newArrayList(VLAN_1), false}
        };
    }

    @Test
    @Parameters(method = "getInOutVlanCombinationForVxlanParameters")
    public void findFlowRelatedDataForVxlanFlowTest(
            int inVlan, int srcVlan, int dstVlan, List<Integer> vlansInPacket, boolean source) {
        createFlow(FLOW_ID, srcVlan, dstVlan, null, false, false);
        SwitchLldpInfoData data = createSwitchLldpInfoData(
                source ? SWITCH_ID_1 : SWITCH_ID_2, vlansInPacket, source ? PORT_NUMBER_1 : PORT_NUMBER_2);
        FlowRelatedData flowRelatedData = packetService.findFlowRelatedDataForVxlanFlow(data);
        assertEquals(FLOW_ID, flowRelatedData.getFlowId());
        assertEquals(inVlan, flowRelatedData.getOriginalVlan());
        assertEquals(source, flowRelatedData.getSource());
    }

    private void runHandleSwitchLldpWithAddedDevice(SwitchLldpInfoData updatedData) {
        SwitchLldpInfoData data = createSwitchLldpInfoData();
        packetService.handleSwitchLldpData(data);
        assertEquals(1, switchConnectedDeviceRepository.findAll().size());
        assertSwitchConnectedDeviceExistInDatabase(data);

        // we must add second device
        packetService.handleSwitchLldpData(updatedData);
        assertEquals(2, switchConnectedDeviceRepository.findAll().size());
        assertSwitchConnectedDeviceExistInDatabase(data);
        assertSwitchConnectedDeviceExistInDatabase(updatedData);
    }

    private void runHandleSwitchLldpWithUpdatedDevice(SwitchLldpInfoData updatedData) throws InterruptedException {
        SwitchLldpInfoData data = createSwitchLldpInfoData();
        packetService.handleSwitchLldpData(data);
        Collection<SwitchConnectedDevice> oldDevices = switchConnectedDeviceRepository.findAll();
        assertEquals(1, oldDevices.size());
        assertSwitchLldpInfoDataEqualsSwitchConnectedDevice(data, oldDevices.iterator().next());

        // Need to have a different timestamp in 'data' and 'updatedData' messages.
        // More info https://github.com/telstra/open-kilda/issues/3064
        updatedData.setTimestamp(data.getTimestamp() + 1000);

        // we must update old device
        packetService.handleSwitchLldpData(updatedData);
        Collection<SwitchConnectedDevice> newDevices = switchConnectedDeviceRepository.findAll();
        assertEquals(1, newDevices.size());
        assertSwitchLldpInfoDataEqualsSwitchConnectedDevice(updatedData, newDevices.iterator().next());

        // time must be updated
        assertNotEquals(oldDevices.iterator().next().getTimeLastSeen(), newDevices.iterator().next().getTimeLastSeen());
    }

    private void assertSwitchConnectedDeviceExistInDatabase(SwitchLldpInfoData data) {
        Optional<SwitchConnectedDevice> switchConnectedDevice = switchConnectedDeviceRepository
                .findByUniqueFieldCombination(data.getSwitchId(), data.getPortNumber(), data.getVlans().get(0),
                        data.getMacAddress(), LLDP, data.getChassisId(), data.getPortId());
        assertTrue(switchConnectedDevice.isPresent());
        assertSwitchLldpInfoDataEqualsSwitchConnectedDevice(data, switchConnectedDevice.get());
    }

    private void assertSwitchLldpInfoDataEqualsSwitchConnectedDevice(
            SwitchLldpInfoData data, SwitchConnectedDevice device) {
        assertEquals(data.getSwitchId(), device.getSwitchObj().getSwitchId());
        assertEquals(data.getPortNumber(), device.getPortNumber());
        assertEquals(data.getVlans().get(0).intValue(), device.getVlan());
        assertEquals(data.getMacAddress(), device.getMacAddress());
        assertEquals(data.getChassisId(), device.getChassisId());
        assertEquals(data.getPortId(), device.getPortId());
        assertEquals(data.getPortDescription(), device.getPortDescription());
        assertEquals(data.getManagementAddress(), device.getManagementAddress());
        assertEquals(data.getSystemCapabilities(), device.getSystemCapabilities());
        assertEquals(data.getSystemName(), device.getSystemName());
        assertEquals(data.getSystemDescription(), device.getSystemDescription());
        assertEquals(data.getTtl(), device.getTtl());
    }

    private void createFlow(
            String flowId, int srcVlan, int dstVlan, Integer transitVlan, boolean oneSwitchFlow, boolean onePort) {
        if (transitVlan != null) {
            transitVlanRepository.createOrUpdate(new TransitVlan(flowId, new PathId(PATH_ID), transitVlan));
        }
        Switch srcSwitch = switchRepository.findById(SWITCH_ID_1).get();
        Switch dstSwitch = oneSwitchFlow ? srcSwitch : switchRepository.findById(SWITCH_ID_2).get();
        Flow flow = Flow.builder()
                .flowId(flowId)
                .srcSwitch(srcSwitch)
                .srcVlan(srcVlan)
                .srcPort(PORT_NUMBER_1)
                .destSwitch(dstSwitch)
                .destVlan(dstVlan)
                .destPort(onePort ? PORT_NUMBER_1 : PORT_NUMBER_2)
                .build();
        flowRepository.createOrUpdate(flow);
    }

    private SwitchLldpInfoData createSwitchLldpInfoData() {
        return createSwitchLldpInfoData(SWITCH_ID_1, newArrayList(VLAN_1), PORT_NUMBER_1);
    }

    private SwitchLldpInfoData createSwitchLldpInfoData(SwitchId switchId, List<Integer> vlans, int portNumber) {
        return new SwitchLldpInfoData(switchId, portNumber, vlans, COOKIE, MAC_ADDRESS_1,
                CHASSIS_ID_1, PORT_ID_1, TTL_1, PORT_DESCRIPTION_1, SYSTEM_NAME_1, SYSTEM_DESCRIPTION_1, CAPABILITIES_1,
                MANAGEMENT_ADDRESS_1);
    }
}
