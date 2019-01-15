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

    @BeforeClass
    public static void setUpOnce() {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        switchRepository = repositoryFactory.createSwitchRepository();

        switchOperationsService = new SwitchOperationsService(persistenceManager.getRepositoryFactory(),
                persistenceManager.getTransactionManager());
    }

    @Test
    public void shouldUpdateLinkUnderMaintenanceFlag() throws SwitchNotFoundException {
        createSwitchIfNotExist(TEST_SWITCH_ID);

        switchOperationsService.updateSwitchUnderMaintenanceFlag(TEST_SWITCH_ID, true);
        Switch sw = createSwitchIfNotExist(TEST_SWITCH_ID);
        assertTrue(sw.isUnderMaintenance());

        switchOperationsService.updateSwitchUnderMaintenanceFlag(TEST_SWITCH_ID, false);
        sw = createSwitchIfNotExist(TEST_SWITCH_ID);
        assertFalse(sw.isUnderMaintenance());
    }

    private Switch createSwitchIfNotExist(SwitchId switchId) {
        return switchRepository.findById(switchId).orElseGet(() -> {
            Switch sw = new Switch();
            sw.setSwitchId(switchId);
            sw.setStatus(SwitchStatus.ACTIVE);
            switchRepository.createOrUpdate(sw);
            return sw;
        });
    }
}
