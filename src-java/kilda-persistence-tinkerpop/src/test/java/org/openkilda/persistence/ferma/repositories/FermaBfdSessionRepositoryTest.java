/* Copyright 2020 Telstra Open Source
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

package org.openkilda.persistence.ferma.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.openkilda.model.BfdSession;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.exceptions.ConstraintViolationException;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.BfdSessionRepository;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Optional;

public class FermaBfdSessionRepositoryTest extends InMemoryGraphBasedTest {
    static final SwitchId TEST_SWITCH_ID = new SwitchId(1);
    static final Integer PHYSICAL_PORT = 100;
    static final Integer TEST_PORT = 300;
    static final int TEST_DISCRIMINATOR = 10001;

    BfdSessionRepository repository;

    @BeforeEach
    public void setUp() {
        repository = repositoryFactory.createBfdSessionRepository();
    }

    @Test
    public void shouldCreateBfdPort() {
        createBfdSession();

        assertEquals(1, repository.findAll().size());
    }

    @Test
    public void shouldFindBySwitchIdAndPort() {
        BfdSession bfdSession = createBfdSession();

        BfdSession foundPort = repository.findBySwitchIdAndPort(TEST_SWITCH_ID, TEST_PORT).get();
        assertEquals(bfdSession.getDiscriminator(), foundPort.getDiscriminator());
    }

    @Test
    public void shouldDeleteBfdPort() {
        BfdSession bfdSession = createBfdSession();

        assertEquals(1, repository.findAll().size());

        transactionManager.doInTransaction(() ->
                repository.remove(bfdSession));

        assertEquals(0, repository.findAll().size());
    }

    @Disabled("Need to fix: in-memory persistence doesn't impose constraints")
    @Test
    public void discriminatorConflict() {
        Assertions.assertThrows(ConstraintViolationException.class, () -> {
            createBfdSession();
            BfdSession bfdSession2 = createBfdSession();
            bfdSession2.setPort(TEST_PORT + 1);
        });
    }

    private int getDiscriminator(SwitchId switchId, int port, int randomDiscriminator)
            throws ConstraintViolationException {

        Optional<BfdSession> foundPort = repository.findBySwitchIdAndPort(switchId, port);
        if (foundPort.isPresent()) {
            return foundPort.get().getDiscriminator();
        }

        BfdSession bfdSession = createBfdSession();
        bfdSession.setDiscriminator(randomDiscriminator);
        return bfdSession.getDiscriminator();
    }

    @Disabled("Need to fix: in-memory persistence doesn't impose constraints")
    @Test
    public void createUseCaseTest() {
        assertThrows(ConstraintViolationException.class, () -> {

            assertEquals(TEST_DISCRIMINATOR, getDiscriminator(TEST_SWITCH_ID, TEST_PORT, TEST_DISCRIMINATOR));

            assertEquals(TEST_DISCRIMINATOR, getDiscriminator(TEST_SWITCH_ID, TEST_PORT, TEST_DISCRIMINATOR));

            assertEquals(1, repository.findAll().size());

            assertEquals(TEST_DISCRIMINATOR, getDiscriminator(TEST_SWITCH_ID, TEST_PORT + 1, TEST_DISCRIMINATOR));
        });
    }

    private void freeDiscriminator(SwitchId switchId, int port) {
        transactionManager.doInTransaction(() ->
                repository.findBySwitchIdAndPort(switchId, port).ifPresent(bfdPort -> repository.remove(bfdPort)));
    }

    @Test
    public void deleteUseCaseTest() {

        getDiscriminator(TEST_SWITCH_ID, TEST_PORT, TEST_DISCRIMINATOR);

        assertEquals(1, repository.findAll().size());

        freeDiscriminator(TEST_SWITCH_ID, TEST_PORT);

        assertEquals(0, repository.findAll().size());
    }

    private BfdSession createBfdSession() {
        BfdSession bfdSession = BfdSession.builder()
                .switchId(TEST_SWITCH_ID).port(TEST_PORT).physicalPort(PHYSICAL_PORT).discriminator(TEST_DISCRIMINATOR)
                .build();
        repository.add(bfdSession);
        return bfdSession;
    }
}
