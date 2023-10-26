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

package org.openkilda.wfm.topology.nbworker.services;

import org.openkilda.messaging.model.system.KildaConfigurationDto;
import org.openkilda.model.KildaConfiguration;
import org.openkilda.model.KildaConfiguration.KildaConfigurationCloner;
import org.openkilda.persistence.repositories.KildaConfigurationRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.wfm.share.mappers.KildaConfigurationMapper;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class KildaConfigurationService {

    private final KildaConfigurationRepository kildaConfigurationRepository;
    private final TransactionManager transactionManager;

    public KildaConfigurationService(RepositoryFactory repositoryFactory, TransactionManager transactionManager) {
        this.kildaConfigurationRepository = repositoryFactory.createKildaConfigurationRepository();
        this.transactionManager = transactionManager;
    }

    /**
     * Get kilda configuration.
     *
     * @return kilda configuration.
     */
    public KildaConfigurationDto getKildaConfiguration() {
        return KildaConfigurationMapper.INSTANCE.map(kildaConfigurationRepository.getOrDefault());
    }

    /**
     * Update kilda configuration.
     *
     * @param kildaConfigurationDto kilda configuration.
     * @return updated kilda configuration.
     */
    public KildaConfigurationDto updateKildaConfiguration(KildaConfigurationDto kildaConfigurationDto) {
        log.info("Process kilda config update - config: {}", kildaConfigurationDto);
        if (Boolean.FALSE.equals(kildaConfigurationDto.getUseMultiTable())) {
            throw new IllegalArgumentException("Single table mode was deprecated. OpenKilda doesn't support it "
                    + "anymore. Configuration property `use_multi_table` can't be 'false'.");
        }

        KildaConfiguration kildaConfiguration = KildaConfigurationMapper.INSTANCE.map(kildaConfigurationDto);
        KildaConfiguration updatedConfiguration = transactionManager.doInTransaction(() -> {
            Optional<KildaConfiguration> currentKildaConfiguration = kildaConfigurationRepository.find();
            if (currentKildaConfiguration.isPresent()) {
                KildaConfigurationCloner.INSTANCE.copyNonNull(kildaConfiguration,
                        currentKildaConfiguration.get());
                return currentKildaConfiguration.get();
            } else {
                kildaConfigurationRepository.add(kildaConfiguration);
                return kildaConfiguration;
            }
        });
        return KildaConfigurationMapper.INSTANCE.map(updatedConfiguration);
    }
}
