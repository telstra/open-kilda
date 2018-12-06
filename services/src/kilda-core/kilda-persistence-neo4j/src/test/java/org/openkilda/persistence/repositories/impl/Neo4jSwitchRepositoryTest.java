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

package org.openkilda.persistence.repositories.impl;

import static org.junit.Assert.assertEquals;

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.Neo4jBasedTest;
import org.openkilda.persistence.repositories.SwitchRepository;

import org.junit.BeforeClass;
import org.junit.Test;

public class Neo4jSwitchRepositoryTest extends Neo4jBasedTest {
    static final SwitchId TEST_SWITCH_ID = new SwitchId(1);

    static SwitchRepository repository;

    @BeforeClass
    public static void setUp() {
        repository = new Neo4jSwitchRepository(neo4jSessionFactory, txManager);
    }

    @Test
    public void shouldCreateSwitch() {
        Switch origSwitch = new Switch();
        origSwitch.setSwitchId(TEST_SWITCH_ID);
        origSwitch.setDescription("Some description");

        repository.createOrUpdate(origSwitch);

        assertEquals(1, repository.findAll().size());
    }

    @Test
    public void shouldFindSwitchById() {
        Switch origSwitch = new Switch();
        origSwitch.setSwitchId(TEST_SWITCH_ID);
        origSwitch.setDescription("Some description");

        repository.createOrUpdate(origSwitch);

        Switch foundSwitch = repository.findById(TEST_SWITCH_ID).get();
        assertEquals(origSwitch.getDescription(), foundSwitch.getDescription());
    }

    @Test
    public void shouldDeleteSwitch() {
        Switch origSwitch = new Switch();
        origSwitch.setSwitchId(TEST_SWITCH_ID);
        origSwitch.setDescription("Some description");

        repository.createOrUpdate(origSwitch);
        repository.delete(origSwitch);

        assertEquals(0, repository.findAll().size());
    }
}
