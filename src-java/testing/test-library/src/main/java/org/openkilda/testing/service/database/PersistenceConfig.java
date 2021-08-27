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

package org.openkilda.testing.service.database;

import org.openkilda.config.provider.ConfigurationProvider;
import org.openkilda.config.provider.PropertiesBasedConfigurationProvider;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.spi.PersistenceProvider;
import org.openkilda.persistence.tx.TransactionManager;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.Properties;

@Configuration
public class PersistenceConfig {
    @Bean
    public PersistenceManager persistenceManager(ConfigurationProvider configurationProvider) {
        return PersistenceProvider.loadAndMakeDefault(configurationProvider);
    }

    @Bean
    public ConfigurationProvider configurationProvider(
            @Value("${kilda.config.file:kilda.properties}") String fileLocation) throws IOException {
        Resource resource = new FileSystemResource(fileLocation);
        Properties props = PropertiesLoaderUtils.loadProperties(resource);
        return new PropertiesBasedConfigurationProvider(props);
    }

    @Bean
    public TransactionManager transactionManager(PersistenceManager persistenceManager) {
        return persistenceManager.getTransactionManager();
    }

    @Bean
    public RepositoryFactory repositoryFactory(PersistenceManager persistenceManager) {
        return persistenceManager.getRepositoryFactory();
    }
}
