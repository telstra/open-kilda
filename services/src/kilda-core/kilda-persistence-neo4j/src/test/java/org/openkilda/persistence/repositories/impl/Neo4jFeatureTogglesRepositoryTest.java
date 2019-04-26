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

import org.openkilda.model.FeatureToggles;
import org.openkilda.persistence.Neo4jBasedTest;
import org.openkilda.persistence.repositories.FeatureTogglesRepository;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collection;

public class Neo4jFeatureTogglesRepositoryTest extends Neo4jBasedTest {
    private static FeatureTogglesRepository featureTogglesRepository;

    @BeforeClass
    public static void setUp() {
        featureTogglesRepository = new Neo4jFeatureTogglesRepository(neo4jSessionFactory, txManager);
    }

    @Test
    public void shouldCreateAndUpdateFeatureToggles() {
        FeatureToggles featureTogglesA = FeatureToggles.builder()
                .flowsRerouteOnIslDiscoveryEnabled(false)
                .createFlowEnabled(false)
                .updateFlowEnabled(false)
                .deleteFlowEnabled(false)
                .pushFlowEnabled(false)
                .unpushFlowEnabled(false)
                .useBfdForIslIntegrityCheck(false)
                .build();

        featureTogglesRepository.createOrUpdate(featureTogglesA);

        FeatureToggles foundFeatureToggles = featureTogglesRepository.find().get();
        assertEquals(featureTogglesA, foundFeatureToggles);

        FeatureToggles featureTogglesB = FeatureToggles.builder()
                .updateFlowEnabled(true)
                .pushFlowEnabled(true)
                .build();

        featureTogglesRepository.createOrUpdate(featureTogglesB);

        Collection<FeatureToggles> featureTogglesCollection = featureTogglesRepository.findAll();
        assertEquals(1, featureTogglesCollection.size());

        FeatureToggles featureTogglesC = FeatureToggles.builder()
                .flowsRerouteOnIslDiscoveryEnabled(false)
                .createFlowEnabled(false)
                .updateFlowEnabled(true)
                .deleteFlowEnabled(false)
                .pushFlowEnabled(true)
                .unpushFlowEnabled(false)
                .useBfdForIslIntegrityCheck(false)
                .build();

        assertEquals(featureTogglesC, featureTogglesCollection.iterator().next());
    }
}
