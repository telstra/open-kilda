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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.openkilda.model.KildaFeatureToggles;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;

import org.junit.Before;
import org.junit.Test;

public class FermaKildaFeatureTogglesRepositoryTest extends InMemoryGraphBasedTest {
    KildaFeatureTogglesRepository featureTogglesRepository;

    @Before
    public void setUp() {
        featureTogglesRepository = repositoryFactory.createFeatureTogglesRepository();
    }

    @Test
    public void shouldCreateAndUpdateFeatureToggles() {
        KildaFeatureToggles featureTogglesA = KildaFeatureToggles.builder()
                .flowsRerouteOnIslDiscoveryEnabled(false)
                .createFlowEnabled(false)
                .updateFlowEnabled(false)
                .deleteFlowEnabled(false)
                .useBfdForIslIntegrityCheck(false)
                .floodlightRoutePeriodicSync(false)
                .flowsRerouteUsingDefaultEncapType(false)
                .build();
        featureTogglesRepository.add(featureTogglesA);

        KildaFeatureToggles foundFeatureToggles = featureTogglesRepository.find().get();
        assertEquals(featureTogglesA, foundFeatureToggles);

        foundFeatureToggles.setUpdateFlowEnabled(true);

        KildaFeatureToggles updatedFeatureToggles = featureTogglesRepository.find().get();
        assertTrue(updatedFeatureToggles.getUpdateFlowEnabled());
    }
}
