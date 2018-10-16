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

package org.openkilda.persistence.spi;

import org.openkilda.config.provider.ConfigurationProvider;
import org.openkilda.persistence.Neo4jConfig;
import org.openkilda.persistence.Neo4jPersistenceManager;
import org.openkilda.persistence.PersistenceManager;

/**
 * Neo4J OGM implementation of the service provider for persistence manager(s).
 */
public class Neo4jPersistenceProvider implements PersistenceProvider {
    @Override
    public PersistenceManager createPersistenceManager(ConfigurationProvider configurationProvider) {
        Neo4jConfig config = configurationProvider.getConfiguration(Neo4jConfig.class);
        return new Neo4jPersistenceManager(config);
    }
}
