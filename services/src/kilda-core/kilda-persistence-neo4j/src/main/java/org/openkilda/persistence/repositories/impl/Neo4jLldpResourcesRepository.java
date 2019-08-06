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

import org.openkilda.model.LldpResources;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.LldpResourcesRepository;

import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

import java.util.Collection;
import java.util.Optional;

/**
 * Neo4j OGM implementation of {@link LldpResourcesRepository}.
 */
public class Neo4jLldpResourcesRepository
        extends Neo4jGenericRepository<LldpResources> implements LldpResourcesRepository {
    private static final String FLOW_ID_PROPERTY_NAME = "flow_id";

    public Neo4jLldpResourcesRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    @Override
    public Optional<LldpResources> findByFlowId(String flowId) {
        Filter flowIdFilter = new Filter(FLOW_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, flowId);
        Collection<LldpResources> lldpResources = loadAll(flowIdFilter);

        if (lldpResources.size() > 1) {
            throw new PersistenceException(format("Found more that 1 LLDP Resources node by flow id '%s'", flowId));
        }

        return  lldpResources.isEmpty() ? Optional.empty() : Optional.of(lldpResources.iterator().next());
    }

    @Override
    public Collection<LldpResources> findByFlowIds(Collection<String> flowIds) {
        Filter flowIdsFilter = new Filter(FLOW_ID_PROPERTY_NAME, ComparisonOperator.IN, flowIds);
        return loadAll(flowIdsFilter);
    }

    @Override
    protected Class<LldpResources> getEntityType() {
        return LldpResources.class;
    }
}
