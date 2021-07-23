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

package org.openkilda.persistence.inmemory;

import org.openkilda.config.provider.ConfigurationProvider;
import org.openkilda.config.provider.PropertiesBasedConfigurationProvider;
import org.openkilda.persistence.PersistenceImplementationType;
import org.openkilda.persistence.PersistenceManager;

import com.google.common.collect.ImmutableMap;
import lombok.Getter;

import java.util.Properties;

public class InMemoryGraphPersistenceManager extends PersistenceManager {
    @Getter
    private final InMemoryGraphPersistenceImplementation inMemoryImplementation;

    /**
     * Create new {@link InMemoryGraphPersistenceManager}.
     */
    public static InMemoryGraphPersistenceManager newInstance() {
        Properties properties = new Properties();
        properties.put("persistence.implementation.default", PersistenceImplementationType.IN_MEMORY_GRAPH.name());
        PropertiesBasedConfigurationProvider configurationProvider = new PropertiesBasedConfigurationProvider(
                properties);
        InMemoryGraphPersistenceImplementation implementation = new InMemoryGraphPersistenceImplementation(
                configurationProvider);
        return new InMemoryGraphPersistenceManager(
                configurationProvider, PersistenceImplementationType.IN_MEMORY_GRAPH, implementation);
    }

    public InMemoryGraphPersistenceManager(
            ConfigurationProvider configurationProvider, PersistenceImplementationType type,
            InMemoryGraphPersistenceImplementation implementation) {
        super(configurationProvider, ImmutableMap.of(type, implementation));
        this.inMemoryImplementation = implementation;
    }
}
