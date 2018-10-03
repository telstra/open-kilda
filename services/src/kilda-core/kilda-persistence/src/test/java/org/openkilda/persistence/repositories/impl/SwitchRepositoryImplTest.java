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
import org.openkilda.persistence.TestConfigurationProvider;
import org.openkilda.persistence.neo4j.Neo4jConfig;
import org.openkilda.persistence.neo4j.Neo4jTransactionManager;
import org.openkilda.persistence.repositories.SwitchRepository;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.neo4j.ogm.testutil.TestServer;

import java.io.IOException;

public class SwitchRepositoryImplTest {
    static final SwitchId TEST_SWITCH_ID = new SwitchId(1);

    static TestServer testServer;
    static SwitchRepository repository;

    @BeforeClass
    public static void setUp() throws IOException {
        testServer = new TestServer(true, true, 5, 7687);

        Neo4jConfig neo4jConfig = new TestConfigurationProvider().getConfiguration(Neo4jConfig.class);
        Neo4jTransactionManager txManager = new Neo4jTransactionManager(neo4jConfig);

        repository = new SwitchRepositoryImpl(txManager);
    }

    @AfterClass
    public static void tearDown() {
        testServer.shutdown();
    }

    @Test
    public void shouldCreateAndFindSwitch() {
        Switch origSwitch = new Switch();
        origSwitch.setSwitchId(TEST_SWITCH_ID);
        origSwitch.setDescription("Some description");

        repository.createOrUpdate(origSwitch);

        assertEquals(1, repository.findAll().size());

        Switch foundSwitch = repository.findBySwitchId(TEST_SWITCH_ID);
        assertEquals(origSwitch.getDescription(), foundSwitch.getDescription());

        repository.delete(foundSwitch);
        assertEquals(0, repository.findAll().size());
    }
}
