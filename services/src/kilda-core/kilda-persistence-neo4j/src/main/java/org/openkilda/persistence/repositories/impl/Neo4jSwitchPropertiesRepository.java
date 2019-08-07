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
import static org.openkilda.persistence.repositories.impl.Neo4jSwitchRepository.SWITCH_NAME_PROPERTY_NAME;

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;

import com.google.common.collect.Lists;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

import java.util.Collection;
import java.util.Optional;

public class Neo4jSwitchPropertiesRepository extends Neo4jGenericRepository<SwitchProperties>
        implements SwitchPropertiesRepository {
    private static final String SWITCH_FIELD = "switchObj";


    public Neo4jSwitchPropertiesRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    @Override
    protected Class<SwitchProperties> getEntityType() {
        return SwitchProperties.class;
    }

    @Override
    public Optional<SwitchProperties> findBySwitchId(SwitchId switchId) {
        if (switchId == null) {
            throw new IllegalArgumentException("Switch id should be not null for SwitchProperties");
        }
        Filter switchFilter = new Filter(SWITCH_NAME_PROPERTY_NAME, ComparisonOperator.EQUALS, switchId.toString());
        switchFilter.setNestedPath(new Filter.NestedPathSegment(SWITCH_FIELD, Switch.class));

        Collection<SwitchProperties> results = Lists.newArrayList(loadAll(switchFilter));

        if (results.size() > 1) {
            throw new PersistenceException(format("Found more that 1 SwitchProperties entity by %s as switch name",
                    switchId));
        }
        return results.isEmpty() ? Optional.empty() : Optional.of(results.iterator().next());
    }

    @Override
    protected int getDepthCreateUpdateEntity() {
        return 1;
    }
}
