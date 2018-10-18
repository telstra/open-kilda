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

package org.openkilda.wfm.topology.nbworker.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.openkilda.model.Isl;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.Neo4jBasedTest;
import org.openkilda.wfm.error.IslNotFoundException;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.List;

public class LinkOperationsServiceTest extends Neo4jBasedTest {
    private static IslRepository islRepository;
    private static SwitchRepository switchRepository;
    private static LinkOperationsService linkOperationsService;

    private static final SwitchId TEST_SWITCH_A_ID = new SwitchId(1);
    private static final int TEST_SWITCH_A_PORT = 1;
    private static final SwitchId TEST_SWITCH_B_ID = new SwitchId(2);
    private static final int TEST_SWITCH_B_PORT = 1;

    private static int islCostWhenUnderMaintenance = 10000;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setUpOnce() {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        islRepository = repositoryFactory.createIslRepository();
        switchRepository = repositoryFactory.createSwitchRepository();

        linkOperationsService = new LinkOperationsService(persistenceManager.getRepositoryFactory(),
                persistenceManager.getTransactionManager(),
                islCostWhenUnderMaintenance);
    }

    @Test
    public void shouldUpdateLinkUnderMaintenanceFlagAndChangeCost() throws IslNotFoundException {
        createIsl();

        for (int i = 0; i < 2; i++) {
            List<Isl> link = linkOperationsService
                    .updateLinkUnderMaintenanceFlag(TEST_SWITCH_A_ID, TEST_SWITCH_A_PORT,
                            TEST_SWITCH_B_ID, TEST_SWITCH_B_PORT, true);
            assertEquals(2, link.size());
            assertEquals(islCostWhenUnderMaintenance, link.get(0).getCost());
            assertEquals(islCostWhenUnderMaintenance, link.get(1).getCost());
            assertTrue(link.get(0).isUnderMaintenance());
            assertTrue(link.get(1).isUnderMaintenance());
        }

        for (int i = 0; i < 2; i++) {
            List<Isl> link = linkOperationsService
                    .updateLinkUnderMaintenanceFlag(TEST_SWITCH_A_ID, TEST_SWITCH_A_PORT,
                            TEST_SWITCH_B_ID, TEST_SWITCH_B_PORT, false);
            assertEquals(2, link.size());
            assertEquals(0, link.get(0).getCost());
            assertEquals(0, link.get(1).getCost());
            assertFalse(link.get(0).isUnderMaintenance());
            assertFalse(link.get(1).isUnderMaintenance());
        }

    }

    private void createIsl() {
        Isl isl = new Isl();
        isl.setSrcSwitch(createSwitchIfNotExist(TEST_SWITCH_A_ID));
        isl.setSrcPort(TEST_SWITCH_A_PORT);
        isl.setDestSwitch(createSwitchIfNotExist(TEST_SWITCH_B_ID));
        isl.setDestPort(TEST_SWITCH_B_PORT);
        isl.setCost(0);

        islRepository.createOrUpdate(isl);

        isl = new Isl();
        isl.setSrcSwitch(createSwitchIfNotExist(TEST_SWITCH_B_ID));
        isl.setSrcPort(TEST_SWITCH_B_PORT);
        isl.setDestSwitch(createSwitchIfNotExist(TEST_SWITCH_A_ID));
        isl.setDestPort(TEST_SWITCH_A_PORT);
        isl.setCost(0);

        islRepository.createOrUpdate(isl);
    }

    private Switch createSwitchIfNotExist(SwitchId switchId) {
        return switchRepository.findById(switchId).orElseGet(() -> {
            Switch sw = Switch.builder().switchId(switchId).status(SwitchStatus.ACTIVE).build();
            switchRepository.createOrUpdate(sw);
            return sw;
        });
    }
}
