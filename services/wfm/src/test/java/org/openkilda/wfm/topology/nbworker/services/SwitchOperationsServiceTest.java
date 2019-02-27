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

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.Neo4jBasedTest;
import org.openkilda.wfm.error.SwitchNotFoundException;

import org.junit.BeforeClass;
import org.junit.Test;

public class SwitchOperationsServiceTest extends Neo4jBasedTest {
    private static SwitchRepository switchRepository;
    private static SwitchOperationsService switchOperationsService;

    private static final SwitchId TEST_SWITCH_ID = new SwitchId(1);

    private static int islCostWhenUnderMaintenance = 10000;

    @BeforeClass
    public static void setUpOnce() {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        switchRepository = repositoryFactory.createSwitchRepository();

        switchOperationsService = new SwitchOperationsService(persistenceManager.getRepositoryFactory(),
                persistenceManager.getTransactionManager(), islCostWhenUnderMaintenance);
    }

    @Test
    public void shouldUpdateLinkUnderMaintenanceFlag() throws SwitchNotFoundException {
        Switch sw = Switch.builder().switchId(TEST_SWITCH_ID).status(SwitchStatus.ACTIVE).build();
        switchRepository.createOrUpdate(sw);

        switchOperationsService.updateSwitchUnderMaintenanceFlag(TEST_SWITCH_ID, true);
        sw = switchRepository.findById(TEST_SWITCH_ID).get();
        assertTrue(sw.isUnderMaintenance());

        switchOperationsService.updateSwitchUnderMaintenanceFlag(TEST_SWITCH_ID, false);
        sw = switchRepository.findById(TEST_SWITCH_ID).get();
        assertFalse(sw.isUnderMaintenance());
    }
}
