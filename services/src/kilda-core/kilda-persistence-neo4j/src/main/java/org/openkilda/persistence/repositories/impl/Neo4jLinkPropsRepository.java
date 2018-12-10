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

package org.openkilda.persistence.repositories.impl;

import org.openkilda.model.LinkProps;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.LinkPropsRepository;

import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.cypher.Filters;

import java.util.Collection;

public class Neo4jLinkPropsRepository extends Neo4jGenericRepository<LinkProps> implements LinkPropsRepository {
    private static final String SRC_SWITCH_PROPERTY_NAME = "src_switch";
    private static final String SRC_PORT_PROPERTY_NAME = "src_port";
    private static final String DST_SWITCH_PROPERTY_NAME = "dst_switch";
    private static final String DST_PORT_PROPERTY_NAME = "dst_port";

    public Neo4jLinkPropsRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    @Override
    Class<LinkProps> getEntityType() {
        return LinkProps.class;
    }

    @Override
    public Collection<LinkProps> findByEndpoints(SwitchId srcSwitch, Integer srcPort,
                                                 SwitchId dstSwitch, Integer dstPort) {
        Filters filters = new Filters();
        if (srcSwitch != null) {
            filters.and(new Filter(SRC_SWITCH_PROPERTY_NAME, ComparisonOperator.EQUALS, srcSwitch));
        }
        if (srcPort != null) {
            filters.and(new Filter(SRC_PORT_PROPERTY_NAME, ComparisonOperator.EQUALS, srcPort));
        }
        if (dstSwitch != null) {
            filters.and(new Filter(DST_SWITCH_PROPERTY_NAME, ComparisonOperator.EQUALS, dstSwitch));
        }
        if (dstPort != null) {
            filters.and(new Filter(DST_PORT_PROPERTY_NAME, ComparisonOperator.EQUALS, dstPort));
        }

        return getSession().loadAll(getEntityType(), filters, DEPTH_LOAD_ENTITY);
    }
}
