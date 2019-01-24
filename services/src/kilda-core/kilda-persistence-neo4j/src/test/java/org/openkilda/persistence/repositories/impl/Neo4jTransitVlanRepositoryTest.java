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

import org.openkilda.model.PathId;
import org.openkilda.model.TransitVlan;
import org.openkilda.persistence.Neo4jBasedTest;
import org.openkilda.persistence.repositories.TransitVlanRepository;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collection;

public class Neo4jTransitVlanRepositoryTest extends Neo4jBasedTest {
    static final String TEST_FLOW_ID = "test_flow";

    static TransitVlanRepository transitVlanRepository;

    @BeforeClass
    public static void setUp() {
        transitVlanRepository = new Neo4jTransitVlanRepository(neo4jSessionFactory, txManager);
    }

    @Test
    public void shouldCreateTransitVlan() {
        TransitVlan vlan = TransitVlan.builder()
                .vlan(1)
                .pathId(new PathId(TEST_FLOW_ID + "_path"))
                .flowId(TEST_FLOW_ID)
                .build();
        transitVlanRepository.createOrUpdate(vlan);

        Collection<TransitVlan> allVlans = transitVlanRepository.findAll();
        TransitVlan foundVlan = allVlans.iterator().next();

        assertEquals(vlan.getVlan(), foundVlan.getVlan());
        assertEquals(TEST_FLOW_ID, foundVlan.getFlowId());
    }

    @Test
    public void shouldDeleteFlowMeter() {
        TransitVlan vlan = TransitVlan.builder()
                .vlan(1)
                .pathId(new PathId(TEST_FLOW_ID + "_path"))
                .flowId(TEST_FLOW_ID)
                .build();
        transitVlanRepository.createOrUpdate(vlan);

        transitVlanRepository.delete(vlan);

        assertEquals(0, transitVlanRepository.findAll().size());
    }

    @Test
    public void shouldDeleteFoundFlowMeter() {
        TransitVlan vlan = TransitVlan.builder()
                .vlan(1)
                .pathId(new PathId(TEST_FLOW_ID + "_path"))
                .flowId(TEST_FLOW_ID)
                .build();
        transitVlanRepository.createOrUpdate(vlan);

        Collection<TransitVlan> allVlans = transitVlanRepository.findAll();
        TransitVlan foundVlan = allVlans.iterator().next();
        transitVlanRepository.delete(foundVlan);

        assertEquals(0, transitVlanRepository.findAll().size());
    }
}
