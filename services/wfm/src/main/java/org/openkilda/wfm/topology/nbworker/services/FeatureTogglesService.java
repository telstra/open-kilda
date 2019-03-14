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

package org.openkilda.wfm.topology.nbworker.services;

import org.openkilda.model.FeatureToggles;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FeatureTogglesRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.error.FeatureTogglesNotFoundException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FeatureTogglesService {
    private final IFeatureTogglesCarrier carrier;

    private FeatureTogglesRepository featureTogglesRepository;
    private TransactionManager transactionManager;

    public FeatureTogglesService(IFeatureTogglesCarrier carrier, RepositoryFactory repositoryFactory,
                                 TransactionManager transactionManager) {
        this.carrier = carrier;
        this.featureTogglesRepository = repositoryFactory.createFeatureTogglesRepository();
        this.transactionManager = transactionManager;
    }

    /**
     * Get feature toggles.
     * @return feature toggles.
     */
    public FeatureToggles getFeatureToggles() throws FeatureTogglesNotFoundException {
        return featureTogglesRepository.find().orElseThrow(FeatureTogglesNotFoundException::new);
    }

    /**
     * Create or update feature toggles.
     * @param featureToggles feature toggles.
     * @return updated feature toggles.
     */
    public FeatureToggles createOrUpdateFeatureToggles(FeatureToggles featureToggles) {
        FeatureToggles before = featureTogglesRepository.find().orElse(FeatureToggles.DEFAULTS);
        FeatureToggles after = transactionManager.doInTransaction(() -> {
            featureTogglesRepository.createOrUpdate(featureToggles);
            return featureTogglesRepository.find().get();
        });

        if (!before.equals(after)) {
            carrier.featureTogglesUpdateNotification(after);
        }
        return after;
    }
}
