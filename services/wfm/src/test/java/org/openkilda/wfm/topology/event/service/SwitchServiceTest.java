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

package org.openkilda.wfm.topology.event.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.Neo4jBasedTest;

import org.junit.BeforeClass;
import org.junit.Test;

public class SwitchServiceTest extends Neo4jBasedTest {
    private static SwitchRepository switchRepository;
    private static SwitchService switchService;

    @BeforeClass
    public static void setUpOnce() {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        switchRepository = repositoryFactory.createSwitchRepository();

        switchService = new SwitchService(txManager, repositoryFactory);
    }

    @Test
    public void shouldCreateAndThenUpdateSwitch() {
        SwitchId switchId = new SwitchId("ff:01");
        String description = "test";
        Switch sw = createSwitch(switchId, description, SwitchStatus.ACTIVE);
        switchService.createOrUpdateSwitch(sw);

        Switch foundSwitch = switchRepository.findById(switchId).get();

        assertNotNull(foundSwitch);
        assertEquals(switchId, foundSwitch.getSwitchId());
        assertEquals(description, foundSwitch.getDescription());

        description = "description";
        sw = createSwitch(switchId, description, SwitchStatus.ACTIVE);
        switchService.createOrUpdateSwitch(sw);

        foundSwitch = switchRepository.findById(switchId).get();
        assertEquals(description, foundSwitch.getDescription());
    }

    @Test
    public void shouldDeactivateAndActivateSwitch() {
        SwitchId switchId = new SwitchId("ff:01");
        String description = "test";
        Switch sw = createSwitch(switchId, description, SwitchStatus.ACTIVE);
        switchService.createOrUpdateSwitch(sw);

        switchService.deactivateSwitch(sw);
        Switch foundSwitch = switchRepository.findById(switchId).get();

        assertEquals(SwitchStatus.INACTIVE, foundSwitch.getStatus());
    }

    private Switch createSwitch(SwitchId switchId, String description, SwitchStatus switchStatus) {
        return Switch.builder().switchId(switchId)
                .address("address").hostname("hostname").description(description).status(switchStatus).build();
    }
}
