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
    private static final int LATENCY = 123;
    private static final SwitchId NON_EXISTENT_SWITCH_ID = new SwitchId(123);
    private static final int NON_EXISTENT_PORT = 555;


    private static SwitchRepository switchRepository;
    private static IslRepository islRepository;
    private static IslLatencyService islLatencyService;


    @BeforeClass
    public static void setUpOnce() {
        islLatencyService = new IslLatencyService(
                persistenceManager.getTransactionManager(), persistenceManager.getRepositoryFactory(), 1, 1);
        switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        islRepository = persistenceManager.getRepositoryFactory().createIslRepository();
    }

    @Before
    public void setup() {
        Switch firstSwitch = createSwitch(SWITCH_ID_1);
        Switch secondSwitch = createSwitch(SWITCH_ID_2);

        createIsl(firstSwitch, PORT_1, secondSwitch, PORT_2, LATENCY);
    }

    @After
    public void cleanUp() {
        // force delete will delete all relations (including created ISL)
        switchRepository.forceDelete(SWITCH_ID_1);
        switchRepository.forceDelete(SWITCH_ID_2);
    }

    @Test(expected = SwitchNotFoundException.class)
    public void updateIslLatencyNonExistentSrcEndpointTest() throws IslNotFoundException, SwitchNotFoundException {
        islLatencyService.updateIslLatency(NON_EXISTENT_SWITCH_ID, PORT_1, SWITCH_ID_2, PORT_2, 0);
    }

    @Test(expected = SwitchNotFoundException.class)
    public void updateIslLatencyNonExistentDstEndpointTest() throws IslNotFoundException, SwitchNotFoundException {
        islLatencyService.updateIslLatency(SWITCH_ID_1, PORT_1, NON_EXISTENT_SWITCH_ID, PORT_2, 0);
    }

    @Test(expected = IslNotFoundException.class)
    public void updateIslLatencyNonExistentIslTest() throws IslNotFoundException, SwitchNotFoundException {
        islLatencyService.updateIslLatency(SWITCH_ID_1, NON_EXISTENT_PORT, SWITCH_ID_2, NON_EXISTENT_PORT, 0);
    }

    @Test
    public void updateIslLatencyTest() throws IslNotFoundException, SwitchNotFoundException {
        islLatencyService.updateIslLatency(SWITCH_ID_1, PORT_1, SWITCH_ID_2, PORT_2, 1000);
        long actualLatency = islRepository.findByEndpoints(SWITCH_ID_1, PORT_1, SWITCH_ID_2, PORT_2).get().getLatency();
        assertEquals(1000, actualLatency);
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
