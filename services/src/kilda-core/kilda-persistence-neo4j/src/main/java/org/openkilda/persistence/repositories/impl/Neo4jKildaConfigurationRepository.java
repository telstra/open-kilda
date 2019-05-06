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

import org.openkilda.model.KildaConfiguration;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.KildaConfigurationRepository;

import java.util.Collection;
import java.util.Optional;

public class Neo4jKildaConfigurationRepository extends Neo4jGenericRepository<KildaConfiguration>
        implements KildaConfigurationRepository {

    public Neo4jKildaConfigurationRepository(Neo4jSessionFactory sessionFactory,
                                             TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    @Override
    public KildaConfiguration find() {
        Collection<KildaConfiguration> kildaConfigurations = findAll();
        if (kildaConfigurations.size() > 1) {
            throw new PersistenceException("Found more than 1 kilda configuration.");
        }
        return kildaConfigurations.isEmpty() ? KildaConfiguration.DEFAULTS.toBuilder().build() :
                mergeConfigs(kildaConfigurations.iterator().next(), KildaConfiguration.DEFAULTS, true);
    }

    @Override
    public void createOrUpdate(KildaConfiguration kildaConfiguration) {
        transactionManager.doInTransaction(() -> super.createOrUpdate(mergeConfigs(kildaConfiguration, find(), false)));
    }

    private KildaConfiguration mergeConfigs(KildaConfiguration firstConfig, KildaConfiguration secondConfig,
                                            boolean copyToFirstConfig) {
        KildaConfiguration kildaConfiguration = copyToFirstConfig ? firstConfig : secondConfig;

        kildaConfiguration.setDefaultFlowEncapsulationType(Optional.ofNullable(
                firstConfig.getDefaultFlowEncapsulationType()).orElse(secondConfig.getDefaultFlowEncapsulationType()));

        return kildaConfiguration;
    }

    @Override
    protected Class<KildaConfiguration> getEntityType() {
        return KildaConfiguration.class;
    }
}
