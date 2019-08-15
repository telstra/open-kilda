/* Copyright 2019 Telstra Open Source
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
import static org.junit.Assert.assertTrue;

import org.openkilda.model.LldpResources;
import org.openkilda.model.MeterId;
import org.openkilda.persistence.Neo4jBasedTest;

import com.google.common.collect.Lists;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class Neo4jLldpResourcesRepositoryTest extends Neo4jBasedTest {
    private static final String FLOW_ID_1 = "first_flow";
    private static final String FLOW_ID_2 = "second_flow";
    private static final String FLOW_ID_3 = "third_flow";
    private static final MeterId METER_ID_1 = new MeterId(32);
    private static final MeterId METER_ID_2 = new MeterId(33);
    private static final MeterId METER_ID_3 = new MeterId(34);

    private static Neo4jLldpResourcesRepository repository;

    @BeforeClass
    public static void setUp() {
        repository = new Neo4jLldpResourcesRepository(neo4jSessionFactory, txManager);
    }

    @Test
    public void createLldpResourcesTest() {
        runCreateLldpResourcesTest(FLOW_ID_1, METER_ID_1, METER_ID_2);
        runCreateLldpResourcesTest(FLOW_ID_2, null, METER_ID_3);
        runCreateLldpResourcesTest(FLOW_ID_3, METER_ID_1, null);
        runCreateLldpResourcesTest(FLOW_ID_1, null, null);
    }

    private void runCreateLldpResourcesTest(String flowId, MeterId srcMeterId, MeterId dstMeterId) {
        LldpResources lldpResources = new LldpResources(flowId, srcMeterId, dstMeterId);
        repository.createOrUpdate(lldpResources);
        Collection<LldpResources> allResources = repository.findAll();
        assertEquals(1, allResources.size());

        LldpResources resources = allResources.iterator().next();

        assertLldpResources(resources, flowId, srcMeterId, dstMeterId);
        repository.delete(lldpResources);
        assertEquals(0, repository.findAll().size());
    }

    @Test
    public void findByFlowIdTest() {
        repository.createOrUpdate(new LldpResources(FLOW_ID_1, METER_ID_1, METER_ID_2));
        repository.createOrUpdate(new LldpResources(FLOW_ID_2, METER_ID_2, METER_ID_3));

        assertEquals(2, repository.findAll().size());

        Optional<LldpResources> resources = repository.findByFlowId(FLOW_ID_1);

        assertTrue(resources.isPresent());
        assertLldpResources(resources.get(), FLOW_ID_1, METER_ID_1, METER_ID_2);
    }

    @Test
    public void findByFlowIdsTest() {
        repository.createOrUpdate(new LldpResources(FLOW_ID_1, METER_ID_1, METER_ID_2));
        repository.createOrUpdate(new LldpResources(FLOW_ID_2, METER_ID_2, METER_ID_3));
        repository.createOrUpdate(new LldpResources(FLOW_ID_3, METER_ID_3, METER_ID_1));

        assertEquals(3, repository.findAll().size());

        List<LldpResources> resources =
                new ArrayList<>(repository.findByFlowIds(Lists.newArrayList(FLOW_ID_1, FLOW_ID_3)));

        assertEquals(2, resources.size());

        resources.sort(Comparator.comparing(LldpResources::getFlowId));

        assertLldpResources(resources.get(0), FLOW_ID_1, METER_ID_1, METER_ID_2);
        assertLldpResources(resources.get(1), FLOW_ID_3, METER_ID_3, METER_ID_1);
    }

    @Test
    public void deleteLldpResourcesTest() {
        LldpResources lldpResources = new LldpResources(FLOW_ID_1, METER_ID_1, METER_ID_2);
        repository.createOrUpdate(lldpResources);
        assertEquals(1, repository.findAll().size());

        repository.delete(lldpResources);
        assertEquals(0, repository.findAll().size());
    }

    private void assertLldpResources(LldpResources resources, String flowId, MeterId srcMeterId, MeterId dstMeterId) {
        assertEquals(flowId, resources.getFlowId());
        assertEquals(srcMeterId, resources.getSrcMeterId());
        assertEquals(dstMeterId, resources.getDstMeterId());
    }
}
