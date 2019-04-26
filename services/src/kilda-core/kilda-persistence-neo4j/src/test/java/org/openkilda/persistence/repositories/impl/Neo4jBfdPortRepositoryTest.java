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

import org.openkilda.model.BfdPort;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.ConstraintViolationException;
import org.openkilda.persistence.Neo4jBasedTest;
import org.openkilda.persistence.repositories.BfdPortRepository;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Optional;

public class Neo4jBfdPortRepositoryTest extends Neo4jBasedTest {
    static final SwitchId TEST_SWITCH_ID = new SwitchId(1);
    static final Integer TEST_PORT = 100;
    static final int TEST_DISCRIMINATOR = 10001;

    static BfdPortRepository repository;

    @BeforeClass
    public static void setUp() {
        repository = new Neo4JBfdPortRepository(neo4jSessionFactory, txManager);
    }

    @Test
    public void shouldCreateBfdPort() {
        BfdPort bfdPort = new BfdPort();
        bfdPort.setSwitchId(TEST_SWITCH_ID);
        bfdPort.setPort(TEST_PORT);
        bfdPort.setDiscriminator(TEST_DISCRIMINATOR);
        repository.createOrUpdate(bfdPort);

        assertEquals(1, repository.findAll().size());
    }

    @Test
    public void shouldFindBySwitchIdAndPort() {
        BfdPort bfdPort = new BfdPort();
        bfdPort.setSwitchId(TEST_SWITCH_ID);
        bfdPort.setPort(TEST_PORT);
        bfdPort.setDiscriminator(TEST_DISCRIMINATOR);
        repository.createOrUpdate(bfdPort);

        BfdPort foundPort = repository.findBySwitchIdAndPort(TEST_SWITCH_ID, TEST_PORT).get();
        assertEquals(bfdPort.getDiscriminator(), foundPort.getDiscriminator());
    }

    @Test
    public void shouldDeleteBfdPort() {
        BfdPort bfdPort = new BfdPort();
        bfdPort.setSwitchId(TEST_SWITCH_ID);
        bfdPort.setPort(TEST_PORT);
        bfdPort.setDiscriminator(TEST_DISCRIMINATOR);
        repository.createOrUpdate(bfdPort);

        assertEquals(1, repository.findAll().size());

        repository.delete(bfdPort);

        assertEquals(0, repository.findAll().size());
    }

    @Test(expected = ConstraintViolationException.class)
    public void discriminatorConflict() {
        BfdPort bfdPort = new BfdPort();
        bfdPort.setSwitchId(TEST_SWITCH_ID);
        bfdPort.setPort(TEST_PORT);
        bfdPort.setDiscriminator(TEST_DISCRIMINATOR);
        repository.createOrUpdate(bfdPort);

        BfdPort bfdPort2 = new BfdPort();
        bfdPort2.setSwitchId(TEST_SWITCH_ID);
        bfdPort2.setPort(TEST_PORT + 1);
        bfdPort2.setDiscriminator(TEST_DISCRIMINATOR);
        repository.createOrUpdate(bfdPort2);
    }

    private int getDiscriminator(SwitchId switchId, int port, int randomDiscriminator)
            throws ConstraintViolationException {

        Optional<BfdPort> foundPort = repository.findBySwitchIdAndPort(switchId, port);
        if (foundPort.isPresent()) {
            return foundPort.get().getDiscriminator();
        }

        BfdPort bfdPort = new BfdPort();
        bfdPort.setSwitchId(switchId);
        bfdPort.setPort(port);
        bfdPort.setDiscriminator(randomDiscriminator);
        repository.createOrUpdate(bfdPort);
        return bfdPort.getDiscriminator();
    }

    @Test(expected = ConstraintViolationException.class)
    public void createUseCaseTest() {

        assertEquals(TEST_DISCRIMINATOR, getDiscriminator(TEST_SWITCH_ID, TEST_PORT, TEST_DISCRIMINATOR));

        assertEquals(TEST_DISCRIMINATOR, getDiscriminator(TEST_SWITCH_ID, TEST_PORT, TEST_DISCRIMINATOR));

        assertEquals(1, repository.findAll().size());

        assertEquals(TEST_DISCRIMINATOR, getDiscriminator(TEST_SWITCH_ID, TEST_PORT + 1, TEST_DISCRIMINATOR));
    }

    private void freeDiscriminator(SwitchId switchId, int port) {
        repository.findBySwitchIdAndPort(switchId, port).ifPresent(bfdPort -> repository.delete(bfdPort));
    }

    @Test
    public void deleteUseCaseTest() {

        getDiscriminator(TEST_SWITCH_ID, TEST_PORT, TEST_DISCRIMINATOR);

        assertEquals(1, repository.findAll().size());

        freeDiscriminator(TEST_SWITCH_ID, TEST_PORT);

        assertEquals(0, repository.findAll().size());
    }
}
