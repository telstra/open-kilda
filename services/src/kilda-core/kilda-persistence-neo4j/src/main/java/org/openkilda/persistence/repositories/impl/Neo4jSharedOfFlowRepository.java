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

import org.openkilda.model.PathId;
import org.openkilda.model.SharedOfFlow;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.SharedOfFlowRepository;

import com.google.common.collect.ImmutableMap;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.session.Session;

import java.time.Instant;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Neo4jSharedOfFlowRepository
        extends Neo4jGenericRepository<SharedOfFlow>  implements SharedOfFlowRepository {

    private static final String UNIQUE_INDEX_PROPERTY_NAME = "unique_index";

    public Neo4jSharedOfFlowRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    @Override
    public long countReferences(SharedOfFlow entity, List<PathId> ignorePath) {
        Session session = getSession();
        Long persistentId = session.resolveGraphIdFor(entity);
        if (persistentId == null) {
            throw new IllegalStateException(String.format("%s object is not persistent", entity));
        }

        Map<String, Object> parameters = ImmutableMap.of(
                "id", persistentId,
                "ignore", ignorePath);
        String query = "MATCH (of_flow:shadow_of_flow)<-[:uses]-(p:flow_path) "
                + "WHERE id(of_flow)=$id AND p.path_id not in $ignore "
                + "RETURN count(p) as references";
        return queryForLong(query, parameters, "references")
                .orElse(0L);
    }

    @Override
    public Optional<SharedOfFlow> findByUniqueIndex(SharedOfFlow entity) {
        Filter filter = new Filter(UNIQUE_INDEX_PROPERTY_NAME, ComparisonOperator.EQUALS, entity.getUniqueIndex());

        Collection<SharedOfFlow> persistentEntities = loadAll(filter);
        if (1 < persistentEntities.size()) {
            throw new PersistenceException(format(
                    "Found more that 1 %s entity (uniquie constraint is broken)", SharedOfFlow.class.getSimpleName()));
        }

        Iterator<SharedOfFlow> iterator = persistentEntities.iterator();
        if (iterator.hasNext()) {
            return Optional.of(iterator.next());
        }
        return Optional.empty();
    }

    @Override
    public void createOrUpdate(SharedOfFlow entity) {
        validate(entity);

        Instant now = Instant.now();

        if (entity.getTimeCreate() == null) {
            entity.setTimeCreate(now);
        }
        entity.setTimeModify(now);

        super.createOrUpdate(entity);
    }

    private void validate(SharedOfFlow entity) {
        if (entity.getSwitchObj() != null) {
            requireManagedEntity(entity.getSwitchObj());
        }
    }

    @Override
    protected Class<SharedOfFlow> getEntityType() {
        return SharedOfFlow.class;
    }
}
