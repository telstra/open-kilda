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

package org.openkilda.wfm.topology.flow.service;

import org.openkilda.model.FeatureToggles;
import org.openkilda.persistence.repositories.FeatureTogglesRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.error.FeatureTogglesNotEnabledException;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class FeatureTogglesService {
    private FeatureTogglesRepository featureTogglesRepository;

    public FeatureTogglesService(RepositoryFactory repositoryFactory) {
        this.featureTogglesRepository = repositoryFactory.createFeatureTogglesRepository();
    }

    /**
     * If feature toggle is not enabled throws FeatureTogglesNotEnabledException.
     */
    public void checkFeatureToggleEnabled(FeatureToggle featureToggle) throws FeatureTogglesNotEnabledException {
        Optional<FeatureToggles> foundFeatureToggles = featureTogglesRepository.find();

        if (foundFeatureToggles.isPresent()) {
            FeatureToggles featureToggles = foundFeatureToggles.get();
            switch (featureToggle) {

                case CREATE_FLOW:
                    if (featureToggles.getCreateFlowEnabled() != null && featureToggles.getCreateFlowEnabled()) {
                        return;
                    }
                    break;

                case UPDATE_FLOW:
                    if (featureToggles.getUpdateFlowEnabled() != null && featureToggles.getUpdateFlowEnabled()) {
                        return;
                    }
                    break;

                case DELETE_FLOW:
                    if (featureToggles.getDeleteFlowEnabled() != null && featureToggles.getDeleteFlowEnabled()) {
                        return;
                    }
                    break;

                case PUSH_FLOW:
                    if (featureToggles.getPushFlowEnabled() != null && featureToggles.getPushFlowEnabled()) {
                        return;
                    }
                    break;

                case UNPUSH_FLOW:
                    if (featureToggles.getUnpushFlowEnabled() != null && featureToggles.getUnpushFlowEnabled()) {
                        return;
                    }
                    break;

                default:
            }
        }

        log.info("Feature toggle '{}' is disabled", featureToggle.toString());
        throw new FeatureTogglesNotEnabledException(featureToggle.toString());
    }
}
