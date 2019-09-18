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

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.openkilda.model.PortProperties.DISCOVERY_ENABLED_DEFAULT;

import org.openkilda.model.PortProperties;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.PortPropertiesRepository;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class Neo4jPortPropertiesRepository extends Neo4jGenericRepository<PortProperties>
        implements PortPropertiesRepository {

    private Neo4jSwitchRepository neo4jSwitchRepository;

    public Neo4jPortPropertiesRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
        neo4jSwitchRepository = new Neo4jSwitchRepository(sessionFactory, transactionManager);
    }

    @Override
    protected Class<PortProperties> getEntityType() {
        return PortProperties.class;
    }

    @Override
    public PortProperties getBySwitchIdAndPort(SwitchId switchId, int port) {
        if (switchId == null) {
            throw new PersistenceException("Switch id should not be null");
        }

        Switch sw = neo4jSwitchRepository.findById(switchId)
                .orElseThrow(() -> new PersistenceException(format("No switch found with id '%s'", switchId)));
        if (sw.getPortProperties() == null) {
            return getDefault(sw, port);
        }
        List<PortProperties> portProperties = sw.getPortProperties().stream()
                .filter(p -> p.getPort() == port)
                .collect(toList());
        if (portProperties.size() > 1) {
            throw new PersistenceException(
                    format("Found more than one PortProperties by switch id '%s' and port '%d'", switchId, port));
        }
        return portProperties.isEmpty() ? getDefault(sw, port) : portProperties.get(0);
    }

    @Override
    protected int getDepthCreateUpdateEntity() {
        return 1;
    }

    private PortProperties getDefault(Switch sw, int port) {
        return PortProperties.builder()
                .switchObj(sw)
                .port(port)
                .discoveryEnabled(DISCOVERY_ENABLED_DEFAULT)
                .build();
    }
}
