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

package org.openkilda.wfm.topology.isllatency.service;

import static org.junit.Assert.assertEquals;

import org.openkilda.model.Isl;
import org.openkilda.model.IslStatus;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.Neo4jBasedTest;
import org.openkilda.wfm.error.IllegalIslStateException;
import org.openkilda.wfm.error.IslNotFoundException;
import org.openkilda.wfm.error.SwitchNotFoundException;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class IslLatencyServiceTest extends Neo4jBasedTest {
    private static final SwitchId SWITCH_ID_1 = new SwitchId("00:00:00:00:00:00:00:01");
    private static final SwitchId SWITCH_ID_2 = new SwitchId("00:00:00:00:00:00:00:02");
    private static final int PORT_1 = 1;
    private static final int PORT_2 = 2;
    private static final int PORT_3 = 3;
    private static final int PORT_4 = 4;
    private static final int FORWARD_LATENCY = 123;
    private static final int REVERSE_LATENCY = 456;
    private static final int TWIN_LATENCY = 999;
    private static final SwitchId NON_EXISTENT_SWITCH_ID = new SwitchId(123);
    private static final int NON_EXISTENT_PORT = 555;


    private static SwitchRepository switchRepository;
    private static IslRepository islRepository;
    private static IslLatencyService islLatencyService;


    @BeforeClass
    public static void setUpOnce() {
        islLatencyService = new IslLatencyService(
                persistenceManager.getTransactionManager(), persistenceManager.getRepositoryFactory());
        switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        islRepository = persistenceManager.getRepositoryFactory().createIslRepository();
    }

    @Before
    public void setup() {
        Switch firstSwitch = createSwitch(SWITCH_ID_1);
        Switch secondSwitch = createSwitch(SWITCH_ID_2);

        // Forward and Teverse ISLs will be used in tests
        createIsl(firstSwitch, PORT_1, secondSwitch, PORT_2, FORWARD_LATENCY);
        createIsl(secondSwitch, PORT_2, firstSwitch, PORT_1, REVERSE_LATENCY);

        // Twins of Forward and Reverse ISLs. They they will be used to check that IslLatencyService changes only
        // Forward and Reverse ISLs, but not their twins.
        createIsl(firstSwitch, PORT_3, secondSwitch, PORT_4, TWIN_LATENCY);
        createIsl(secondSwitch, PORT_4, firstSwitch, PORT_3, TWIN_LATENCY);
    }

    @After
    public void cleanUp() {
        // force delete will delete all relations (including created ISLs)
        switchRepository.forceDelete(SWITCH_ID_1);
        switchRepository.forceDelete(SWITCH_ID_2);
    }

    @Test
    public void getIslTest() throws IslNotFoundException, IllegalIslStateException {
        assertEquals(FORWARD_LATENCY, islLatencyService.getIsl(SWITCH_ID_1, PORT_1).getLatency());
        assertEquals(REVERSE_LATENCY, islLatencyService.getIsl(SWITCH_ID_2, PORT_2).getLatency());
        assertEquals(FORWARD_LATENCY, islLatencyService.getIsl(SWITCH_ID_1, PORT_1, SWITCH_ID_2, PORT_2).getLatency());
        assertEquals(REVERSE_LATENCY, islLatencyService.getIsl(SWITCH_ID_2, PORT_2, SWITCH_ID_1, PORT_1).getLatency());
    }

    @Test(expected = IslNotFoundException.class)
    public void getIslByOneEndpointNonExistentTest() throws IslNotFoundException, IllegalIslStateException {
        islLatencyService.getIsl(NON_EXISTENT_SWITCH_ID, NON_EXISTENT_PORT);
    }

    @Test(expected = IslNotFoundException.class)
    public void getIslByTwoEndpointsNonExistentTest() throws IslNotFoundException {
        islLatencyService.getIsl(SWITCH_ID_1, PORT_1, NON_EXISTENT_SWITCH_ID, NON_EXISTENT_PORT);
    }

    @Test
    public void setIslLatencyBySourceEndpointTest() throws IslNotFoundException, IllegalIslStateException {
        islLatencyService.setIslLatencyBySourceEndpoint(SWITCH_ID_1, PORT_1, 1000);
        assertEquals(1000, islLatencyService.getIsl(SWITCH_ID_1, PORT_1).getLatency());
        assertTwinsIslsNotChanged();
    }

    @Test(expected = IslNotFoundException.class)
    public void setIslLatencyBySourceEndpointNonExsistentTest() throws IslNotFoundException, IllegalIslStateException {
        islLatencyService.setIslLatencyBySourceEndpoint(NON_EXISTENT_SWITCH_ID, NON_EXISTENT_PORT, 1000);
    }

    @Test
    public void setIslLatencyBySourceAndDestinationEndpointTest() throws IslNotFoundException, SwitchNotFoundException {
        islLatencyService.setIslLatencyBySourceAndDestinationEndpoint(SWITCH_ID_1, PORT_1, SWITCH_ID_2, PORT_2, 1000);
        assertEquals(1000, islLatencyService.getIsl(SWITCH_ID_1, PORT_1, SWITCH_ID_2, PORT_2).getLatency());
        assertTwinsIslsNotChanged();
    }

    @Test(expected = SwitchNotFoundException.class)
    public void setIslLatencyBySourceAndDestinationEndpointNonExistentSrcSwitchTest() throws SwitchNotFoundException {
        islLatencyService.setIslLatencyBySourceAndDestinationEndpoint(
                NON_EXISTENT_SWITCH_ID, NON_EXISTENT_PORT, SWITCH_ID_2, PORT_2, 1000);
    }

    @Test(expected = SwitchNotFoundException.class)
    public void setIslLatencyBySourceAndDestinationEndpointNonExistentDstSwitchTest() throws SwitchNotFoundException {
        islLatencyService.setIslLatencyBySourceAndDestinationEndpoint(
                SWITCH_ID_1, PORT_1, NON_EXISTENT_SWITCH_ID, NON_EXISTENT_PORT, 1000);
    }

    @Test
    public void copyLatencyFromReverseIslTest() throws IslNotFoundException {
        assertEquals(FORWARD_LATENCY, islLatencyService.getIsl(SWITCH_ID_1, PORT_1, SWITCH_ID_2, PORT_2).getLatency());
        islLatencyService.copyLatencyFromReverseIsl(SWITCH_ID_1, PORT_1, SWITCH_ID_2, PORT_2);
        assertEquals(REVERSE_LATENCY, islLatencyService.getIsl(SWITCH_ID_1, PORT_1, SWITCH_ID_2, PORT_2).getLatency());
        assertTwinsIslsNotChanged();
    }

    private void assertTwinsIslsNotChanged() throws IslNotFoundException {
        assertEquals(TWIN_LATENCY, islLatencyService.getIsl(SWITCH_ID_1, PORT_3, SWITCH_ID_2, PORT_4).getLatency());
        assertEquals(TWIN_LATENCY, islLatencyService.getIsl(SWITCH_ID_2, PORT_4, SWITCH_ID_1, PORT_3).getLatency());
    }

    private static Switch createSwitch(SwitchId switchId) {
        Switch sw = new Switch();
        sw.setSwitchId(switchId);
        switchRepository.createOrUpdate(sw);
        return sw;
    }

    private void createIsl(Switch srcSwitch, int srcPort, Switch dstSwitch, int dstPort, int latency) {
        Isl isl = Isl.builder()
                .srcSwitch(srcSwitch)
                .srcPort(srcPort)
                .destSwitch(dstSwitch)
                .destPort(dstPort)
                .actualStatus(IslStatus.ACTIVE)
                .latency(latency).build();
        islRepository.createOrUpdate(isl);
    }
}
