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

import static java.lang.String.format;
import static java.util.Collections.singleton;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPair;
import org.openkilda.model.FlowPair.FlowPairBuilder;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.converters.FlowStatusConverter;
import org.openkilda.persistence.repositories.FlowRepository;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Neo4J OGM implementation of {@link FlowRepository}.
 */
public class Neo4jFlowRepository extends Neo4jGenericRepository<Flow> implements FlowRepository {
    private static final String FLOW_ID_PROPERTY_NAME = "flowid";
    private static final String FLOW_GROUP_ID_PROPERTY_NAME = "group_id";
    private static final String COOKIE_PROPERTY_NAME = "cookie";
    private static final String PERIODIC_PINGS_PROPERTY_NAME = "periodic_pings";

    private final FlowStatusConverter flowStatusConverter = new FlowStatusConverter();

    public Neo4jFlowRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    @Override
    public boolean exists(String flowId) {
        Filter flowIdFilter = new Filter(FLOW_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, flowId);

        return getSession().count(getEntityType(), singleton(flowIdFilter)) > 0;
    }

    @Override
    public Collection<Flow> findById(String flowId) {
        Filter flowIdFilter = new Filter(FLOW_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, flowId);

        return getSession().loadAll(getEntityType(), flowIdFilter, DEPTH_LOAD_ENTITY);
    }

    @Override
    public Collection<Flow> findByGroupId(String flowGroupId) {
        Filter flowIdFilter = new Filter(FLOW_GROUP_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, flowGroupId);

        return getSession().loadAll(getEntityType(), flowIdFilter, DEPTH_LOAD_ENTITY);
    }

    @Override
    public Optional<Flow> findByIdAndCookie(String flowId, long cookie) {
        Filter flowIdFilter = new Filter(FLOW_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, flowId);
        Filter cookieFilter = new Filter(COOKIE_PROPERTY_NAME, ComparisonOperator.EQUALS, cookie);

        Collection<Flow> flows =
                getSession().loadAll(getEntityType(), flowIdFilter.and(cookieFilter), DEPTH_LOAD_ENTITY);
        return flows.isEmpty() ? Optional.empty() : Optional.of(flows.iterator().next());
    }

    @Override
    public Optional<FlowPair> findFlowPairById(String flowId) {
        Collection<FlowPair> flowPairs = buildFlowPairs(findById(flowId));
        if (flowPairs.size() > 1) {
            throw new PersistenceException(format("Found more that 1 FlowPair entity by %s as flowId", flowId));
        }
        return flowPairs.isEmpty() ? Optional.empty() : Optional.of(flowPairs.iterator().next());
    }

    @Override
    public Collection<FlowPair> findFlowPairsByGroupId(String flowGroupId) {
        return buildFlowPairs(findByGroupId(flowGroupId));
    }

    @Override
    public Collection<FlowPair> findAllFlowPairs() {
        return buildFlowPairs(findAll());
    }

    @Override
    public Collection<FlowPair> findFlowPairsWithPeriodicPingsEnabled() {
        Filter periodicPingsFilter = new Filter(PERIODIC_PINGS_PROPERTY_NAME, ComparisonOperator.EQUALS, true);

        Collection<Flow> flows = getSession().loadAll(getEntityType(), periodicPingsFilter, DEPTH_LOAD_ENTITY);
        return buildFlowPairs(flows);
    }


    private Collection<FlowPair> buildFlowPairs(Iterable<Flow> flows) {
        Map<String, FlowPair.FlowPairBuilder> flowPairsMap = new HashMap<>();

        flows.forEach(flow -> {
            FlowPair.FlowPairBuilder builder = flowPairsMap.computeIfAbsent(flow.getFlowId(), k -> FlowPair.builder());
            if (flow.isForward()) {
                builder.forward(flow);
            } else {
                builder.reverse(flow);
            }
        });

        return flowPairsMap.values().stream().map(FlowPairBuilder::build).collect(Collectors.toList());
    }

    @Override
    public Collection<Flow> findFlowIdsByEndpoint(SwitchId switchId, int port) {
        Map<String, Object> parameters = ImmutableMap.of(
                "switch_id", switchId.toString(),
                "port", port);

        Set<Flow> flows = new HashSet<>();
        getSession().query(Flow.class, "MATCH (src:switch)-[f:flow]->(dst:switch) "
                + "WHERE src.name=$switch_id AND f.src_port=$port "
                + " OR dst.name=$switch_id AND f.dst_port=$port "
                + "RETURN src,f, dst", parameters).forEach(flows::add);
        return flows;
    }

    @Override
    public Collection<String> findActiveFlowIdsWithPortInPath(SwitchId switchId, int port) {
        Map<String, Object> parameters = ImmutableMap.of(
                "switch_id", switchId.toString(),
                "port", port,
                "flow_status", flowStatusConverter.toGraphProperty(FlowStatus.UP));

        Set<String> flowIds = new HashSet<>();
        // Treat empty status as UP to support old storage schema.
        getSession().query(String.class, "MATCH (src:switch)-[f:flow]->(dst:switch) "
                + "WHERE (src.name=$switch_id AND f.src_port=$port "
                + " OR dst.name=$switch_id AND f.dst_port=$port) "
                + " AND (f.status=$flow_status OR f.status IS NULL)"
                + "RETURN f.flowid", parameters).forEach(flowIds::add);

        getSession().query(String.class, "MATCH (src:switch)-[fs:flow_segment]->(dst:switch) "
                + "WHERE (src.name=$switch_id AND fs.src_port=$port "
                + " OR dst.name=$switch_id AND fs.dst_port=$port) "
                + "WITH fs "
                + "MATCH ()-[f:flow]->() "
                + "WHERE fs.flowid = f.flowid AND (f.status=$flow_status OR f.status IS NULL)"
                + "RETURN f.flowid", parameters).forEach(flowIds::add);
        return flowIds;
    }

    @Override
    public Collection<String> findDownFlowIds() {
        Map<String, Object> parameters = ImmutableMap.of(
                "flow_status", flowStatusConverter.toGraphProperty(FlowStatus.DOWN));

        Set<String> flowIds = new HashSet<>();
        getSession().query(String.class,
                "MATCH ()-[f:flow{status: {flow_status}}]->() RETURN f.flowid", parameters).forEach(flowIds::add);
        return flowIds;
    }

    @Override
    public Collection<Flow> findBySrcSwitchId(SwitchId switchId) {
        Filter srcSwitchFilter = createSrcSwitchFilter(switchId);
        return getSession().loadAll(getEntityType(), srcSwitchFilter, DEPTH_LOAD_ENTITY);
    }

    @Override
    public Collection<Flow> findByDstSwitchId(SwitchId switchId) {
        Filter dstSwitchFilter = createDstSwitchFilter(switchId);
        return getSession().loadAll(getEntityType(), dstSwitchFilter, DEPTH_LOAD_ENTITY);
    }

    @Override
    public void createOrUpdate(Flow flow) {
        transactionManager.doInTransaction(() -> {
            lockSwitches(requireManagedEntity(flow.getSrcSwitch()), requireManagedEntity(flow.getDestSwitch()));
            super.createOrUpdate(flow);
        });
    }

    @Override
    public void createOrUpdate(FlowPair flowPair) {
        transactionManager.doInTransaction(() -> {
            createOrUpdate(flowPair.getForward());
            createOrUpdate(flowPair.getReverse());
        });
    }

    @Override
    public Optional<String> getOrCreateFlowGroupId(String flowId) {
        return transactionManager.doInTransaction(() -> findFlowPairById(flowId)
                .map(diverseFlow -> {
                    if (diverseFlow.getForward().getGroupId() == null) {
                        String groupId = UUID.randomUUID().toString();

                        diverseFlow.getForward().setGroupId(groupId);
                        diverseFlow.getReverse().setGroupId(groupId);
                        createOrUpdate(diverseFlow);
                    }
                    return diverseFlow.getForward().getGroupId();
                }));
    }

    @Override
    public void delete(FlowPair flowPair) {
        transactionManager.doInTransaction(() -> {
            delete(flowPair.getForward());
            delete(flowPair.getReverse());
        });
    }

    @Override
    public Collection<FlowPair> findAllFlowPairsWithSegment(SwitchId srcSwitchId, int srcPort,
                                                            SwitchId dstSwitchId, int dstPort) {
        Map<String, Object> parameters = ImmutableMap.of(
                "src_switch", srcSwitchId,
                "src_port", srcPort,
                "dst_switch", dstSwitchId,
                "dst_port", dstPort);

        Iterable<Flow> flows = getSession().query(Flow.class, "MATCH (:switch)-[fc:flow_segment]->(:switch) "
                + "WHERE fc.src_switch=$src_switch "
                + "AND fc.src_port=$src_port  "
                + "AND fc.dst_switch=$dst_switch "
                + "AND fc.dst_port=$dst_port  "
                + "MATCH (src:switch)-[f:flow]->(dst:switch) "
                + "WHERE fc.flowid=f.flowid  "
                + "RETURN src, f, dst", parameters);

        return buildFlowPairs(flows);
    }

    @Override
    public Set<String> findFlowIdsBySwitch(SwitchId switchId) {
        Map<String, Object> parameters = ImmutableMap.of(
                "switch_id", switchId);

        return Sets.newHashSet(getSession().query(String.class, "MATCH (:switch)-[fc:flow_segment]->(:switch) "
                + "WHERE fc.src_switch=$switch_id "
                + "OR fc.dst_switch=$switch_id "
                + "RETURN fc.flowid ", parameters));
    }

    @Override
    Class<Flow> getEntityType() {
        return Flow.class;
    }
}
