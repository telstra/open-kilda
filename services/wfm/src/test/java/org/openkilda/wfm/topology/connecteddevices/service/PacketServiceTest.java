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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.openkilda.messaging.info.event.LldpInfoData;
import org.openkilda.model.ConnectedDevice;
import org.openkilda.model.FlowCookie;
import org.openkilda.persistence.repositories.ConnectedDeviceRepository;
import org.openkilda.persistence.repositories.FlowCookieRepository;
import org.openkilda.wfm.Neo4jBasedTest;

import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

public class PacketServiceTest extends Neo4jBasedTest {
    public static final int COOKIE = 1;
    public static final String MAC_ADDRESS = "00:00:00:00:00:01";
    public static final String CHASSIS_ID = "00:00:00:00:00:02";
    public static final String PORT_ID = "00:00:00:00:00:03";
    public static final String POST = "some_post";
    public static final String SYSTEM_NAME = "ubuntu";
    public static final String SYSTEM_DESCRIPTION = "ubuntu 18.04";
    public static final String CAPABILITIES = "cap";
    public static final String MANAGEMENT_ADDRESS = "127.0.0.1";
    public static final String FLOW_ID = "flow1";
    public static final int TTL = 120;

    private ConnectedDeviceRepository connectedDeviceRepository;
    private FlowCookieRepository flowCookieRepository;
    private PacketService packetService;

    @Before
    public void setUp() {
        connectedDeviceRepository = persistenceManager.getRepositoryFactory().createConnectedDeviceRepository();
        flowCookieRepository = persistenceManager.getRepositoryFactory().createFlowCookieRepository();
        packetService = new PacketService(persistenceManager);
    }

    @Test
    public void testHandleLldpDataSameTimeOnCreate() {
        flowCookieRepository.createOrUpdate(new FlowCookie(FLOW_ID, COOKIE));
        LldpInfoData data = createLldpInfoData(COOKIE);
        packetService.handleLldpData(data);
        Collection<ConnectedDevice> devices = connectedDeviceRepository.findAll();
        assertEquals(1, devices.size());
        assertEquals(devices.iterator().next().getTimeFirstSeen(), devices.iterator().next().getTimeLastSeen());
    }

    @Test
    public void testHandleLldpDataDifferentTimeOnUpdate() throws InterruptedException {
        flowCookieRepository.createOrUpdate(new FlowCookie(FLOW_ID, COOKIE));
        LldpInfoData data = createLldpInfoData(COOKIE);
        // create
        packetService.handleLldpData(data);

        Thread.sleep(10);
        // update
        packetService.handleLldpData(data);

        Collection<ConnectedDevice> devices = connectedDeviceRepository.findAll();
        assertEquals(1, devices.size());
        assertNotEquals(devices.iterator().next().getTimeFirstSeen(), devices.iterator().next().getTimeLastSeen());
    }

    private LldpInfoData createLldpInfoData(long cookie) {
        return new LldpInfoData(cookie, MAC_ADDRESS, CHASSIS_ID, PORT_ID, TTL, POST, SYSTEM_NAME, SYSTEM_DESCRIPTION,
                CAPABILITIES, MANAGEMENT_ADDRESS);
    }
}
