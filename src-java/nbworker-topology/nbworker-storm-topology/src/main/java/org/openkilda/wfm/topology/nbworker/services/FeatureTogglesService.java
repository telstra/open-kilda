/* Copyright 2021 Telstra Open Source
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

import org.openkilda.model.KildaFeatureToggles;
import org.openkilda.model.KildaFeatureToggles.FeatureTogglesCloner;
import org.openkilda.model.Switch;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.tx.TransactionManager;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Optional;

@Slf4j
public class FeatureTogglesService {
    private final IFeatureTogglesCarrier carrier;

    private KildaFeatureTogglesRepository featureTogglesRepository;
    private SwitchRepository switchRepository;
    private TransactionManager transactionManager;

    public FeatureTogglesService(IFeatureTogglesCarrier carrier, RepositoryFactory repositoryFactory,
                                 TransactionManager transactionManager) {
        this.carrier = carrier;
        this.featureTogglesRepository = repositoryFactory.createFeatureTogglesRepository();
        this.switchRepository = repositoryFactory.createSwitchRepository();
        this.transactionManager = transactionManager;
    }

    /**
     * Get feature toggles.
     * @return feature toggles.
     */
    public KildaFeatureToggles getFeatureToggles() {
        return featureTogglesRepository.getOrDefault();
    }

    /**
     * Create or update feature toggles.
     * @param featureToggles feature toggles.
     * @return updated feature toggles.
     */
    public KildaFeatureToggles createOrUpdateFeatureToggles(KildaFeatureToggles featureToggles) {
        log.info("Process feature-toggles update - toggles:{}", featureToggles);
        KildaFeatureToggles before = featureTogglesRepository.getOrDefault();

        KildaFeatureToggles after = transactionManager.doInTransaction(() -> {
            Optional<KildaFeatureToggles> foundCurrent = featureTogglesRepository.find();
            KildaFeatureToggles current;
            if (foundCurrent.isPresent()) {
                current = foundCurrent.get();
                FeatureTogglesCloner.INSTANCE.copyNonNull(featureToggles, current);
            } else {
                current = featureToggles;
                featureTogglesRepository.add(current);
            }
            featureTogglesRepository.detach(current);
            FeatureTogglesCloner.INSTANCE.replaceNullProperties(KildaFeatureToggles.DEFAULTS, current);
            return current;
        });

        if (!before.equals(after)) {
            log.info("Emit feature-toggles update notification - toggles:{}", after);
            carrier.featureTogglesUpdateNotification(after);

            if (before.getServer42FlowRtt() != after.getServer42FlowRtt()
                    || before.getServer42IslRtt() != after.getServer42IslRtt()) {
                Collection<Switch> switches = switchRepository.findActive();

                for (Switch sw : switches) {
                    log.info("Emit switch {} sync command", sw.getSwitchId());
                    carrier.requestSwitchSync(sw.getSwitchId());
                }
            }
        }
        return after;
    }
}
