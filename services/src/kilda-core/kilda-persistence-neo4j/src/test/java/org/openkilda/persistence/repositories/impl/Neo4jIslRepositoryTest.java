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

import org.openkilda.model.Isl;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.Neo4jBasedTest;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class Neo4jIslRepositoryTest extends Neo4jBasedTest {
    static final SwitchId TEST_SWITCH_A_ID = new SwitchId(1);
    static final SwitchId TEST_SWITCH_B_ID = new SwitchId(2);

    static IslRepository islRepository;
    static SwitchRepository switchRepository;

    @BeforeClass
    public static void setUp() {
        islRepository = new Neo4jIslRepository(neo4jSessionFactory);
        switchRepository = new Neo4jSwitchRepository(neo4jSessionFactory);
    }

    @Test
    public void shouldCreateFindAndDeleteIsl() {
        Switch switchA = new Switch();
        switchA.setSwitchId(TEST_SWITCH_A_ID);

        Switch switchB = new Switch();
        switchB.setSwitchId(TEST_SWITCH_B_ID);

        Isl isl = new Isl();
        isl.setSrcSwitch(switchA);
        isl.setDestSwitch(switchB);

        islRepository.createOrUpdate(isl);

        assertEquals(1, islRepository.findAll().size());
        assertEquals(2, switchRepository.findAll().size());

        Switch foundSwitch = switchRepository.findBySwitchId(TEST_SWITCH_A_ID);
        assertEquals(switchA.getDescription(), foundSwitch.getDescription());

        islRepository.delete(isl);
        assertEquals(0, islRepository.findAll().size());
        assertEquals(2, switchRepository.findAll().size());

        switchRepository.delete(switchA);
        switchRepository.delete(switchB);
    }

    @Test
    public void shouldCreateAndFindIslByEndpoint() {
        Switch switchA = new Switch();
        switchA.setSwitchId(TEST_SWITCH_A_ID);

        Switch switchB = new Switch();
        switchB.setSwitchId(TEST_SWITCH_B_ID);

        Isl isl = new Isl();
        isl.setSrcSwitch(switchA);
        isl.setSrcPort(1);
        isl.setDestSwitch(switchB);
        isl.setDestPort(1);

        islRepository.createOrUpdate(isl);

        assertEquals(1, islRepository.findAll().size());
        assertEquals(2, switchRepository.findAll().size());

        List<Isl> foundIsls = new ArrayList<>();
        islRepository.findByEndpoint(TEST_SWITCH_A_ID, 1).forEach(foundIsls::add);

        assertEquals(switchB, foundIsls.get(0).getDestSwitch());

        islRepository.delete(isl);
        switchRepository.delete(switchA);
        switchRepository.delete(switchB);
    }

    @Test
    public void shouldCreateAndFindIslByEndpoints() {
        Switch switchA = new Switch();
        switchA.setSwitchId(TEST_SWITCH_A_ID);

        Switch switchB = new Switch();
        switchB.setSwitchId(TEST_SWITCH_B_ID);

        Isl isl = new Isl();
        isl.setSrcSwitch(switchA);
        isl.setSrcPort(1);
        isl.setDestSwitch(switchB);
        isl.setDestPort(1);

        islRepository.createOrUpdate(isl);

        assertEquals(1, islRepository.findAll().size());
        assertEquals(2, switchRepository.findAll().size());

        Isl foundIsl = islRepository.findByEndpoints(TEST_SWITCH_A_ID, 1, TEST_SWITCH_B_ID, 1);

        assertEquals(switchB, foundIsl.getDestSwitch());

        islRepository.delete(isl);
        switchRepository.delete(switchA);
        switchRepository.delete(switchB);
    }
}
