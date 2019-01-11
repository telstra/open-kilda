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

import org.openkilda.model.FlowSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowSegmentRepository;

import com.google.common.collect.ImmutableMap;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Neo4J OGM implementation of {@link FlowSegmentRepository}.
 */
public class Neo4jFlowSegmentRepository extends Neo4jGenericRepository<FlowSegment> implements FlowSegmentRepository {
    private static final String FLOW_ID_PROPERTY_NAME = "flowid";
    private static final String COOKIE_PROPERTY_NAME = "cookie";

    public Neo4jFlowSegmentRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    @Override
    public Collection<FlowSegment> findByFlowIdAndCookie(String flowId, long flowCookie) {
        Filter flowIdFilter = new Filter(FLOW_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, flowId);
        Filter cookieFilter = new Filter(COOKIE_PROPERTY_NAME, ComparisonOperator.EQUALS, flowCookie);
        return getSession().loadAll(getEntityType(), flowIdFilter.and(cookieFilter), DEPTH_LOAD_ENTITY);
    }

    @Override
    public Collection<FlowSegment> findByDestSwitchId(SwitchId switchId) {
        Filter destSwitchIdFilter = createDstSwitchFilter(switchId);
        return getSession().loadAll(getEntityType(), destSwitchIdFilter, DEPTH_LOAD_ENTITY);
    }

    @Override
    public Collection<FlowSegment> findBySrcSwitchId(SwitchId switchId) {
        Filter srcSwitchFilter = createSrcSwitchFilter(switchId);
        return getSession().loadAll(getEntityType(), srcSwitchFilter, DEPTH_LOAD_ENTITY);
    }

    @Override
    public void createOrUpdate(FlowSegment segment) {
        transactionManager.doInTransaction(() -> {
            lockSwitches(requireManagedEntity(segment.getSrcSwitch()), requireManagedEntity(segment.getDestSwitch()));

            super.createOrUpdate(segment);
        });
    }

    @Override
    public long getUsedBandwidthBetweenEndpoints(SwitchId srcSwitchId, int srcPort, SwitchId dstSwitchId, int dstPort) {
        Map<String, Object> parameters = ImmutableMap.of(
                "src_switch", srcSwitchId.toString(),
                "src_port", srcPort,
                "dst_switch", dstSwitchId.toString(),
                "dst_port", dstPort);

        String query = "MATCH (src:switch {name: $src_switch}), (dst:switch {name: $dst_switch}) "
                + "WITH src,dst "
                + "OPTIONAL MATCH (src) - [fs:flow_segment { "
                + " src_port: $src_port, "
                + " dst_port: $dst_port, "
                + " ignore_bandwidth: false "
                + "}] -> (dst) "
                + "WITH sum(fs.bandwidth) AS used_bandwidth RETURN used_bandwidth";

        return Optional.ofNullable(getSession().queryForObject(Long.class, query, parameters))
                .orElse(0L);
    }

    @Override
    Class<FlowSegment> getEntityType() {
        return FlowSegment.class;
    }
}
