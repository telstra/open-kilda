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

package org.openkilda.persistence.neo4j;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.openkilda.model.Switch;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TestConfigurationProvider;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.impl.SwitchRepositoryImpl;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.neo4j.ogm.testutil.TestServer;

import java.io.IOException;

public class Neo4jTransactionManagerTest {
    static final String TEST_SWITCH_NAME = "TxTestSwitch";

    static TestServer testServer;
    static Neo4jTransactionManager txManager;
    static SwitchRepository repository;

    @BeforeClass
    public static void setUp() throws IOException {
        testServer = new TestServer(true, true, 5, 7687);

        Neo4jConfig neo4jConfig = new TestConfigurationProvider().getConfiguration(Neo4jConfig.class);
        txManager = new Neo4jTransactionManager(neo4jConfig);

        repository = new SwitchRepositoryImpl(txManager);
    }

    @AfterClass
    public static void tearDown() {
        testServer.shutdown();
    }

    @Test
    public void shouldCommitTx() {
        // given
        Switch origSwitch = new Switch();
        origSwitch.setName(TEST_SWITCH_NAME);
        origSwitch.setDescription("Some description");

        // when
        txManager.begin();
        try {
            repository.createOrUpdate(origSwitch);

            txManager.commit();
        } catch (Exception ex) {
            txManager.rollback();
            throw ex;
        }

        // then
        Switch foundSwitch = repository.findByName(TEST_SWITCH_NAME);
        assertEquals(origSwitch.getDescription(), foundSwitch.getDescription());

        repository.delete(foundSwitch);
        assertEquals(0, repository.findAll().size());
    }

    @Test
    public void shouldCommitExtendedTx() {
        // given
        Switch origSwitch = new Switch();
        origSwitch.setName(TEST_SWITCH_NAME);
        origSwitch.setDescription("Some description");

        // when

        // Start the root Tx.
        txManager.begin();
        // Start the extended Tx.
        txManager.begin();

        repository.createOrUpdate(origSwitch);

        // Commit the extended Tx.
        txManager.commit();
        // Commit the root Tx.
        txManager.commit();

        // then
        Switch foundSwitch = repository.findByName(TEST_SWITCH_NAME);
        assertEquals(origSwitch.getDescription(), foundSwitch.getDescription());

        repository.delete(foundSwitch);
        assertEquals(0, repository.findAll().size());
    }

    @Test
    public void shouldRollbackTx() {
        // given
        Switch origSwitch = new Switch();
        origSwitch.setName(TEST_SWITCH_NAME);
        origSwitch.setDescription("Some description");

        // when
        txManager.begin();

        repository.createOrUpdate(origSwitch);

        txManager.rollback();

        // then
        assertEquals(0, repository.findAll().size());
    }

    @Test(expected = PersistenceException.class)
    public void shouldRollbackExtendedTx() {
        // given
        Switch origSwitch = new Switch();
        origSwitch.setName(TEST_SWITCH_NAME);
        origSwitch.setDescription("Some description");

        // when

        // Start the root Tx.
        txManager.begin();
        // Start the extended Tx.
        txManager.begin();

        repository.createOrUpdate(origSwitch);

        // Rollback the extended Tx.
        txManager.rollback();

        try {
            // Trying to commit the root Tx.
            txManager.commit();
            // And shouldn't be allowed.
            fail();
        } catch (Exception ex) {
            txManager.rollback();
            throw ex;
        }
    }

    @Test
    public void shouldRollbackExtendedAndRootTx() {
        // given
        Switch origSwitch = new Switch();
        origSwitch.setName(TEST_SWITCH_NAME);
        origSwitch.setDescription("Some description");

        // when

        // Start the root Tx.
        txManager.begin();
        // Start the extended Tx.
        txManager.begin();

        repository.createOrUpdate(origSwitch);

        // Rollback the extended Tx.
        txManager.rollback();

        // Rollback the root Tx.
        txManager.rollback();

        // then
        assertEquals(0, repository.findAll().size());
    }
}
