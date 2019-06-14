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

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeatures;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.SwitchFeaturesRepository;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public class Neo4jSwitchFeaturesRepository extends Neo4jGenericRepository<SwitchFeatures>
        implements SwitchFeaturesRepository {

    public Neo4jSwitchFeaturesRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    @Override
    protected Class<SwitchFeatures> getEntityType() {
        return SwitchFeatures.class;
    }

    @Override
    public Optional<SwitchFeatures> findBySwitch(Switch theSwitch) {
        Map<String, Object> parameters = ImmutableMap.of(
                "switch_id", theSwitch.getSwitchId()
                );

        String query = "MATCH  (sf:switch_features)<-[:has]-(sw:switch) "
                + "WHERE sw.name = $switch_id "
                + "RETURN sf, sw";
        Collection<SwitchFeatures> results = Lists.newArrayList(getSession().query(getEntityType(), query, parameters));

        if (results.size() > 1) {
            throw new PersistenceException(format("Found more that 1 SwitchFeatures entity by %s as switch name",
                    theSwitch.getSwitchId()));
        }
        return results.isEmpty() ? Optional.empty() : Optional.of(results.iterator().next());
    }

    @Override
    protected int getDepthCreateUpdateEntity() {
        return 1;
    }
}
