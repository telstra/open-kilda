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

import org.openkilda.model.FeatureToggles;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FeatureTogglesRepository;

import java.util.Collection;
import java.util.Optional;

public class Neo4jFeatureTogglesRepository extends Neo4jGenericRepository<FeatureToggles>
        implements FeatureTogglesRepository {

    public Neo4jFeatureTogglesRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    @Override
    public Optional<FeatureToggles> find() {
        Collection<FeatureToggles> featureToggles = findAll();
        if (featureToggles.size() > 1) {
            throw new PersistenceException("Found more than 1 feature_toggles config.");
        }

        return featureToggles.isEmpty() ? Optional.empty() : Optional.of(featureToggles.iterator().next());
    }

    @Override
    public void createOrUpdate(FeatureToggles featureToggles) {
        transactionManager.doInTransaction(() -> {
            Optional<FeatureToggles> foundFeatureToggles = find();

            if (!foundFeatureToggles.isPresent()) {
                super.createOrUpdate(featureToggles);
            } else {
                FeatureToggles updatedFeatureToggles = foundFeatureToggles.get();
                if (featureToggles.getFlowsRerouteOnIslDiscoveryEnabled() != null) {
                    updatedFeatureToggles
                            .setFlowsRerouteOnIslDiscoveryEnabled(
                                    featureToggles.getFlowsRerouteOnIslDiscoveryEnabled());
                }
                if (featureToggles.getCreateFlowEnabled() != null) {
                    updatedFeatureToggles.setCreateFlowEnabled(featureToggles.getCreateFlowEnabled());
                }
                if (featureToggles.getUpdateFlowEnabled() != null) {
                    updatedFeatureToggles.setUpdateFlowEnabled(featureToggles.getUpdateFlowEnabled());
                }
                if (featureToggles.getDeleteFlowEnabled() != null) {
                    updatedFeatureToggles.setDeleteFlowEnabled(featureToggles.getDeleteFlowEnabled());
                }
                if (featureToggles.getPushFlowEnabled() != null) {
                    updatedFeatureToggles.setPushFlowEnabled(featureToggles.getPushFlowEnabled());
                }
                if (featureToggles.getUnpushFlowEnabled() != null) {
                    updatedFeatureToggles.setUnpushFlowEnabled(featureToggles.getUnpushFlowEnabled());
                }
                if (featureToggles.getUseBfdForIslIntegrityCheck() != null) {
                    updatedFeatureToggles.setUseBfdForIslIntegrityCheck(featureToggles.getUseBfdForIslIntegrityCheck());
                }

                super.createOrUpdate(updatedFeatureToggles);
            }
        });
    }

    @Override
    Class<FeatureToggles> getEntityType() {
        return FeatureToggles.class;
    }
}
