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
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.converters.FlowStatusConverter;
import org.openkilda.persistence.converters.SwitchIdConverter;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.model.Result;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
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

    private final FlowStatusConverter flowStatusConverter = new FlowStatusConverter();
    private final SwitchIdConverter switchIdConverter = new SwitchIdConverter();

    private final FlowPathRepository flowPathRepository;

    public Neo4jFlowRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);

        flowPathRepository = new Neo4jFlowPathRepository(sessionFactory, transactionManager);
    }

    @Override
    public long countFlows() {
        return getSession().countEntitiesOfType(getEntityType());
    }

    @Override
    public Collection<Flow> findAll() {
        return loadFlowsWithPaths("", emptyMap());
    }

    @Override
    public boolean exists(String flowId) {
        Filter flowIdFilter = new Filter(FLOW_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, flowId);

        return getSession().count(getEntityType(), singleton(flowIdFilter)) > 0;
    }

    @Override
    public Optional<Flow> findById(String flowId) {
        Map<String, Object> parameters = ImmutableMap.of(
                "flow_id", flowId);

        Collection<Flow> flows = loadFlowsWithPathsAndSegments("flow.flowid = $flow_id", parameters);
        if (flows.size() > 1) {
            throw new PersistenceException(format("Found more that 1 Flow entity by %s as flowId", flowId));
        } else if (flows.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(flows.iterator().next());
    }

    @Override
    public Collection<Flow> findByGroupId(String flowGroupId) {
        Map<String, Object> parameters = ImmutableMap.of(
                "group_id", flowGroupId);

        return loadFlowsWithPathsAndSegments("flow.group_id = $group_id", parameters);
    }

    @Override
    public Collection<Flow> findWithPeriodicPingsEnabled() {
        return loadFlowsWithPathsAndSegments("flow.periodic_pings = true", emptyMap());
    }

    @Override
    public Collection<Flow> findByEndpoint(SwitchId switchId, int port) {
        Map<String, Object> parameters = ImmutableMap.of(
                "switch_id", switchIdConverter.toGraphProperty(switchId),
                "port", port);

        return loadFlowsWithPathsAndSegments("src.name=$switch_id AND flow.src_port=$port "
                + " OR dst.name=$switch_id AND flow.dst_port=$port", parameters);
    }

    @Override
    public Collection<Flow> findByEndpointSwitch(SwitchId switchId) {
        Map<String, Object> parameters = ImmutableMap.of(
                "switch_id", switchIdConverter.toGraphProperty(switchId));

        return loadFlowsWithPathsAndSegments("src.name=$switch_id OR dst.name=$switch_id", parameters);
    }

    @Override
    public Collection<Flow> findActiveFlowsWithPortInPath(SwitchId switchId, int port) {
        Map<String, Object> parameters = ImmutableMap.of(
                "switch_id", switchIdConverter.toGraphProperty(switchId),
                "port", port,
                "flow_status", flowStatusConverter.toGraphProperty(FlowStatus.UP));

        return Sets.newHashSet(getSession().query(getEntityType(),
                "MATCH (src:switch)-[f:flow]->(dst:switch) "
                        + "WHERE (src.name=$switch_id AND f.src_port=$port "
                        + " OR dst.name=$switch_id AND f.dst_port=$port) "
                        + " AND (f.status=$flow_status OR f.status IS NULL) "
                        + "RETURN src, f, dst "
                        + "UNION ALL "
                        + "MATCH (src:switch)-[ps:path_segment]->(dst:switch) "
                        + "WHERE (src.name=$switch_id AND ps.src_port=$port "
                        + " OR dst.name=$switch_id AND ps.dst_port=$port) "
                        + "MATCH ()-[fp:flow_path { path_id: ps.path_id }]->() "
                        + "MATCH (f_src:switch)-[f:flow]->(f_dst:switch) "
                        + "WHERE fp.flow_id = f.flowid AND (f.status=$flow_status OR f.status IS NULL)"
                        + "RETURN f_src as src, f, f_dst as dst", parameters));
    }

    @Override
    public Collection<Flow> findDownFlows() {
        Map<String, Object> parameters = ImmutableMap.of(
                "flow_status", flowStatusConverter.toGraphProperty(FlowStatus.DOWN));

        return loadFlowsWithPathsAndSegments("flow.status=$flow_status", parameters);
    }

    @Override
    public void createOrUpdate(Flow flow) {
        transactionManager.doInTransaction(() -> {
            requireManagedEntity(flow.getSrcSwitch());
            requireManagedEntity(flow.getDestSwitch());

            flowPathRepository.lockInvolvedSwitches(flow.getForwardPath(), flow.getReversePath());

            if (flow.getForwardPath() != null) {
                flowPathRepository.createOrUpdate(flow.getForwardPath());
            }
            if (flow.getReversePath() != null) {
                flowPathRepository.createOrUpdate(flow.getReversePath());
            }

            super.createOrUpdate(flow);
        });
    }

    @Override
    public void delete(Flow flow) {
        transactionManager.doInTransaction(() -> {
            Collection<FlowPath> flowPaths = flowPathRepository.findByFlowId(flow.getFlowId());
            flowPathRepository.lockInvolvedSwitches(flowPaths.toArray(new FlowPath[0]));

            flowPaths.forEach(flowPathRepository::delete);

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
                        super.createOrUpdate(diverseFlow);
                    }
                    return diverseFlow.getGroupId();
                }));
    }

    @Override
    public Collection<Flow> findWithPathSegment(SwitchId srcSwitchId, int srcPort,
                                                SwitchId dstSwitchId, int dstPort) {
        Map<String, Object> parameters = ImmutableMap.of(
                "src_switch", switchIdConverter.toGraphProperty(srcSwitchId),
                "src_port", srcPort,
                "dst_switch", switchIdConverter.toGraphProperty(dstSwitchId),
                "dst_port", dstPort);

        Set<String> flowIds = new HashSet<>();
        getSession().query(String.class, "MATCH (src:switch)-[ps:path_segment]->(dst:switch) "
                + "WHERE src.name=$src_switch AND ps.src_port=$src_port  "
                + "AND dst.name=$dst_switch AND ps.dst_port=$dst_port  "
                + "MATCH ()-[fp:flow_path { path_id: ps.path_id }]->() "
                + "RETURN fp.flow_id", parameters).forEach(flowIds::add);

        if (flowIds.isEmpty()) {
            return emptyList();
        }

        String flowFilter = flowIds.stream()
                .map(flow -> "flow.flowid=\"" + flow + "\"")
                .collect(Collectors.joining(" OR "));

        return loadFlowsWithPathsAndSegments(flowFilter, emptyMap());
    }

    @Override
    public Set<String> findFlowIdsWithSwitchInPath(SwitchId switchId) {
        Map<String, Object> parameters = ImmutableMap.of(
                "switch_id", switchIdConverter.toGraphProperty(switchId));

        Set<String> flowIds = new HashSet<>();
        getSession().query(String.class, "MATCH (src:switch)-[f:flow]->(dst:switch) "
                + "WHERE (src.name=$switch_id OR dst.name=$switch_id) "
                + "RETURN f.flowid", parameters).forEach(flowIds::add);

        getSession().query(String.class, "MATCH (src:switch)-[ps:path_segment]->(dst:switch) "
                + "WHERE (src.name=$switch_id OR dst.name=$switch_id) "
                + "MATCH ()-[fp:flow_path { path_id: ps.path_id }]->() "
                + "RETURN fp.flow_id", parameters).forEach(flowIds::add);
        return flowIds;
    }

    @SuppressWarnings("unchecked")
    private Collection<Flow> loadFlowsWithPaths(String flowFilter, Map<String, Object> parameters) {
        Result queryResult = getSession().query(
                "MATCH (src:switch)-[flow:flow]->(dst:switch) "
                        + (StringUtils.isNotBlank(flowFilter) ? "WHERE " + flowFilter : "")
                        + " WITH src, flow, dst "
                        + "OPTIONAL MATCH (src)-[flow_path:flow_path {flow_id: flow.flowid}]-(dst) "
                        + " WITH src, flow, dst, collect(flow_path) as flow_paths "
                        + "RETURN src, flow, dst, flow_paths", parameters);

        Set<Flow> result = new HashSet<>();
        for (Map<String, Object> record : queryResult.queryResults()) {
            Flow flow = (Flow) record.get("flow");

            if (record.get("flow_paths") instanceof List) {
                Map<PathId, FlowPath> flowPaths = ((List<FlowPath>) record.get("flow_paths")).stream()
                        .peek(path -> path.setSegments(emptyList()))
                        .collect(Collectors.toMap(FlowPath::getPathId, path -> path));

                flow.setForwardPath(flowPaths.get(flow.getForwardPathId()));
                flow.setReversePath(flowPaths.get(flow.getReversePathId()));
            }

            result.add(flow);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Collection<Flow> loadFlowsWithPathsAndSegments(String flowFilter, Map<String, Object> parameters) {
        Result queryResult = getSession().query(
                "MATCH (src:switch)-[flow:flow]->(dst:switch) "
                        + (StringUtils.isNotBlank(flowFilter) ? "WHERE " + flowFilter : "")
                        + " WITH src, flow, dst "
                        + "OPTIONAL MATCH (src)-[flow_path:flow_path {flow_id: flow.flowid}]-(dst) "
                        + " WITH src, flow, dst, collect(flow_path) as flow_paths, "
                        + " collect(flow_path.path_id) as flow_path_ids "
                        + "OPTIONAL MATCH (segment_src:switch)-[path_segment:path_segment]->(segment_dst:switch) "
                        + "WHERE path_segment.path_id in flow_path_ids "
                        + "RETURN src, flow, dst, flow_paths, "
                        + "collect(segment_src), collect(segment_dst), "
                        + "collect(path_segment) as path_segments", parameters);

        Set<Flow> result = new HashSet<>();
        for (Map<String, Object> record : queryResult.queryResults()) {
            Flow flow = (Flow) record.get("flow");

            if (record.get("flow_paths") instanceof List) {
                Map<PathId, FlowPath> flowPaths = ((List<FlowPath>) record.get("flow_paths")).stream()
                        .peek(path -> path.setSegments(emptyList()))
                        .collect(Collectors.toMap(FlowPath::getPathId, path -> path));

                if (record.get("path_segments") instanceof List) {
                    Map<PathId, List<PathSegment>> pathSegments =
                            ((List<PathSegment>) record.get("path_segments")).stream()
                                    .collect(Collectors.groupingBy(PathSegment::getPathId));
                    pathSegments.forEach((k, v) ->
                            flowPaths.get(k).setSegments(pathSegments.get(k).stream()
                                    .sorted(Comparator.comparingInt(PathSegment::getSeqId))
                                    .collect(Collectors.toList())));
                }

                flow.setForwardPath(flowPaths.get(flow.getForwardPathId()));
                flow.setReversePath(flowPaths.get(flow.getReversePathId()));
            }

            result.add(flow);
        }
        return result;
    }

    @Override
    protected Class<Flow> getEntityType() {
        return Flow.class;
    }
}
