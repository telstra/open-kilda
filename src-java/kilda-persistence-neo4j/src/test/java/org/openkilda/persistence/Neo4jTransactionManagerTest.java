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

package org.openkilda.persistence;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.impl.Neo4jSwitchRepository;

import net.jodah.failsafe.RetryPolicy;
import org.junit.BeforeClass;
import org.junit.Test;

public class Neo4jTransactionManagerTest extends Neo4jBasedTest {
    static final SwitchId TEST_SWITCH_ID = new SwitchId(1);

    static SwitchRepository repository;

    @BeforeClass
    public static void setUp() {
        repository = new Neo4jSwitchRepository(neo4jSessionFactory, txManager);
    }

    @Test
    public void shouldCommitTx() {
        // given
        Switch origSwitch = Switch.builder().switchId(TEST_SWITCH_ID).build();

        // when
        txManager.doInTransaction(() -> repository.createOrUpdate(origSwitch));

        // then
        Switch foundSwitch = repository.findById(TEST_SWITCH_ID).get();
        assertEquals(origSwitch.getDescription(), foundSwitch.getDescription());

        repository.delete(foundSwitch);
        assertEquals(0, repository.findAll().size());
    }

    @Test
    public void shouldCommitExtendedTx() {
        // given
        Switch origSwitch = Switch.builder().switchId(TEST_SWITCH_ID).build();

        // when

        // Start the root Tx.
        txManager.doInTransaction(() -> {
            // Start the extended Tx.
            txManager.doInTransaction(() -> {
                repository.createOrUpdate(origSwitch);
            });
        });

        // then
        Switch foundSwitch = repository.findById(TEST_SWITCH_ID).get();
        assertEquals(origSwitch.getDescription(), foundSwitch.getDescription());

        repository.delete(foundSwitch);
        assertEquals(0, repository.findAll().size());
    }

    @Test
    public void shouldRollbackTx() {
        // given
        Switch origSwitch = Switch.builder().switchId(TEST_SWITCH_ID)
                .description("Some description").build();

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
        Switch origSwitch = Switch.builder().switchId(TEST_SWITCH_ID).build();

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
        Switch origSwitch = Switch.builder().switchId(TEST_SWITCH_ID).build();

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

    @Test(expected = TestCheckedException.class)
    public void shouldPassthroughAndCatchCheckedException() throws TestCheckedException {
        TransactionCallbackWithoutResult<TestCheckedException> callback = () -> {
            throw new TestCheckedException();
        };

        // when
        try {
            txManager.doInTransaction(callback);
        } catch (TestCheckedException ex) {
            throw ex;
        }

        fail();
    }

    @Test(expected = TestCheckedException.class)
    public void shouldPassthroughCheckedException() {
        // when
        txManager.doInTransaction(() -> {
            throw new TestCheckedException();
        });

        fail();
    }

    @Test(expected = TestCheckedException.class)
    public void shouldPassthroughCheckedExceptionWithoutResult() throws TestCheckedException {
        // when
        txManager.doInTransaction((TransactionCallbackWithoutResult<TestCheckedException>) () -> {
            throw new TestCheckedException();
        });

        fail();
    }

    @Test
    public void mustRepeatAccordingToRetryPolicy() throws Throwable {
        SwitchId switchAlphaId = TEST_SWITCH_ID;
        SwitchId switchBetaId = new SwitchId(TEST_SWITCH_ID.toLong() + 1);

        Switch switchAlpha = Switch.builder().switchId(switchAlphaId).build();
        Switch switchBeta = Switch.builder().switchId(switchBetaId).build();

        // The switch repository is empty at start
        assertEquals(0, repository.findAll().size());

        RetryPolicy retry = new RetryPolicy()
                .retryOn(NeedRepeatException.class)
                .withMaxRetries(2);
        txManager.doInTransaction(retry, new TransactionCallbackWithoutResult<Throwable>() {
            int attempt = 0;

            @Override
            public void doInTransaction() throws Exception {
                if (attempt++ == 0) {
                    repository.createOrUpdate(switchBeta);
                    throw new NeedRepeatException();
                }

                repository.createOrUpdate(switchAlpha);
            }
        });

        // switchBeta must be absent, because it must be rolled back after exception inside transaction
        assertFalse("There was no rollback between transaction execution attempts",
                    repository.findById(switchBetaId).isPresent());
        assertTrue("There was no repeat attempt", repository.findById(switchAlphaId).isPresent());

        repository.delete(switchAlpha);
    }

    private class TestCheckedException extends Exception {
    }

    private static class NeedRepeatException extends Exception {
    }
}
