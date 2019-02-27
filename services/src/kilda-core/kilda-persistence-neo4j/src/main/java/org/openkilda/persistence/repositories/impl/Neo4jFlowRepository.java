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
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.converters.FlowStatusConverter;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

import java.util.Collection;
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
    private static final String PERIODIC_PINGS_PROPERTY_NAME = "periodic_pings";

    private final FlowStatusConverter flowStatusConverter = new FlowStatusConverter();

    private final FlowPathRepository flowPathRepository;

    public Neo4jFlowRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);

        flowPathRepository = new Neo4jFlowPathRepository(sessionFactory, transactionManager);
    }

    @Override
    public Collection<Flow> findAll() {
        return super.findAll().stream()
                .map(flow -> {
                    flow.setForwardPath(findFlowPathByPathId(flow.getForwardPathId()));
                    flow.setReversePath(findFlowPathByPathId(flow.getReversePathId()));
                    return flow;
                }).collect(Collectors.toList());
    }

    @Override
    public boolean exists(String flowId) {
        Filter flowIdFilter = new Filter(FLOW_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, flowId);

        return getSession().count(getEntityType(), singleton(flowIdFilter)) > 0;
    }

    @Override
    public Optional<Flow> findById(String flowId) {
        Filter flowIdFilter = new Filter(FLOW_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, flowId);

        Collection<Flow> flows = loadAll(flowIdFilter);
        if (flows.size() > 1) {
            throw new PersistenceException(format("Found more that 1 Flow entity by %s as flowId", flowId));
        } else if (flows.isEmpty()) {
            return Optional.empty();
        }

        Flow flow = flows.iterator().next();
        flow.setForwardPath(findFlowPathByPathId(flow.getForwardPathId()));
        flow.setReversePath(findFlowPathByPathId(flow.getReversePathId()));
        return Optional.of(flow);
    }

    @Override
    public Collection<Flow> findByGroupId(String flowGroupId) {
        Filter groupIdFilter = new Filter(FLOW_GROUP_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, flowGroupId);

        return loadAll(groupIdFilter).stream()
                .map(flow -> {
                    flow.setForwardPath(findFlowPathByPathId(flow.getForwardPathId()));
                    flow.setReversePath(findFlowPathByPathId(flow.getReversePathId()));
                    return flow;
                }).collect(Collectors.toList());
    }

    @Override
    public Collection<Flow> findWithPeriodicPingsEnabled() {
        Filter periodicPingsFilter = new Filter(PERIODIC_PINGS_PROPERTY_NAME, ComparisonOperator.EQUALS, true);

        return loadAll(periodicPingsFilter).stream()
                .map(flow -> {
                    flow.setForwardPath(findFlowPathByPathId(flow.getForwardPathId()));
                    flow.setReversePath(findFlowPathByPathId(flow.getReversePathId()));
                    return flow;
                }).collect(Collectors.toList());
    }


    @Override
    public Collection<Flow> findByEndpoint(SwitchId switchId, int port) {
        Map<String, Object> parameters = ImmutableMap.of(
                "switch_id", switchId.toString(),
                "port", port);

        Set<Flow> flows = new HashSet<>();
        getSession().query(Flow.class, "MATCH (src:switch)-[f:flow]->(dst:switch) "
                + "WHERE src.name=$switch_id AND f.src_port=$port "
                + " OR dst.name=$switch_id AND f.dst_port=$port "
                + "RETURN src,f,dst", parameters)
                .forEach(flow -> {
                    flow.setForwardPath(findFlowPathByPathId(flow.getForwardPathId()));
                    flow.setReversePath(findFlowPathByPathId(flow.getReversePathId()));
                    flows.add(flow);
                });
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

        getSession().query(String.class, "MATCH (src:switch)-[ps:path_segment]->(dst:switch) "
                + "WHERE (src.name=$switch_id AND ps.src_port=$port "
                + " OR dst.name=$switch_id AND ps.dst_port=$port) "
                + "MATCH ()-[fp:flow_path { path_id: ps.path_id }]->() "
                + "MATCH ()-[f:flow]->() "
                + "WHERE fp.flow_id = f.flowid AND (f.status=$flow_status OR f.status IS NULL)"
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
    public void createOrUpdate(Flow flow) {
        transactionManager.doInTransaction(() -> {
            lockSwitches(requireManagedEntity(flow.getSrcSwitch()), requireManagedEntity(flow.getDestSwitch()));

            flowPathRepository.createOrUpdate(flow.getForwardPath());
            flowPathRepository.createOrUpdate(flow.getReversePath());

            super.createOrUpdate(flow);
        });
    }

    @Override
    public void delete(Flow flow) {
        transactionManager.doInTransaction(() -> {
            lockSwitches(requireManagedEntity(flow.getSrcSwitch()), requireManagedEntity(flow.getDestSwitch()));

            flowPathRepository.findByFlowId(flow.getFlowId())
                    .forEach(flowPathRepository::delete);

            super.delete(flow);
        });
    }

    @Override
    public Optional<String> getOrCreateFlowGroupId(String flowId) {
        return transactionManager.doInTransaction(() -> findById(flowId)
                .map(diverseFlow -> {
                    if (diverseFlow.getGroupId() == null) {
                        String groupId = UUID.randomUUID().toString();

                        diverseFlow.setGroupId(groupId);
                        createOrUpdate(diverseFlow);
                    }
                    return diverseFlow.getGroupId();
                }));
    }

    @Override
    public Collection<Flow> findWithPathSegment(SwitchId srcSwitchId, int srcPort,
                                                SwitchId dstSwitchId, int dstPort) {
        Map<String, Object> parameters = ImmutableMap.of(
                "src_switch", srcSwitchId,
                "src_port", srcPort,
                "dst_switch", dstSwitchId,
                "dst_port", dstPort);

        Set<Flow> flows = new HashSet<>();
        getSession().query(Flow.class, "MATCH (src:switch)-[ps:path_segment]->(dst:switch) "
                + "WHERE src.name=$src_switch AND ps.src_port=$src_port  "
                + "AND dst.name=$dst_switch AND ps.dst_port=$dst_port  "
                + "MATCH ()-[fp:flow_path { path_id: ps.path_id }]->() "
                + "MATCH (f_src:switch)-[f:flow]->(f_dst:switch) "
                + "WHERE fp.flow_id=f.flowid  "
                + "RETURN f_src, f, f_dst", parameters)
                .forEach(flow -> {
                    flow.setForwardPath(findFlowPathByPathId(flow.getForwardPathId()));
                    flow.setReversePath(findFlowPathByPathId(flow.getReversePathId()));
                    flows.add(flow);
                });
        return flows;
    }

    @Override
    public Set<String> findFlowIdsBySwitch(SwitchId switchId) {
        Map<String, Object> parameters = ImmutableMap.of(
                "switch_id", switchId);

        return Sets.newHashSet(getSession().query(String.class, "MATCH (src:switch)-[ps:path_segment]->(dst:switch) "
                + "WHERE src.name=$switch_id "
                + "OR dst.name=$switch_id "
                + "MATCH ()-[fp:flow_path { path_id: ps.path_id }]->() "
                + "RETURN fp.flow_id", parameters));
    }

    private FlowPath findFlowPathByPathId(PathId pathId) {
        return flowPathRepository.findById(pathId).orElse(null);
    }

    @Override
    Class<Flow> getEntityType() {
        return Flow.class;
    }
}
