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
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

import org.openkilda.model.Cookie;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.converters.CookieConverter;
import org.openkilda.persistence.converters.PathIdConverter;
import org.openkilda.persistence.converters.SwitchIdConverter;
import org.openkilda.persistence.repositories.FlowPathRepository;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.StringUtils;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Neo4J OGM implementation of {@link FlowPathRepository}.
 */
public class Neo4jFlowPathRepository extends Neo4jGenericRepository<FlowPath> implements FlowPathRepository {
    static final String PATH_ID_PROPERTY_NAME = "path_id";

    private final CookieConverter cookieConverter = new CookieConverter();
    private final PathIdConverter pathIdConverter = new PathIdConverter();
    private final SwitchIdConverter switchIdConverter = new SwitchIdConverter();

    public Neo4jFlowPathRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    @Override
    public Collection<FlowPath> findAll() {
        return loadFlowPathsWithSegments("", emptyMap());
    }

    @Override
    public Optional<FlowPath> findById(PathId pathId) {
        Map<String, Object> parameters = ImmutableMap.of(
                "path_id", pathIdConverter.toGraphProperty(pathId));

        Collection<FlowPath> flowPaths = loadFlowPathsWithSegments("flow_path.path_id = $path_id", parameters);
        if (flowPaths.size() > 1) {
            throw new PersistenceException(format("Found more that 1 FlowPath entity by (%s)", pathId));
        } else if (flowPaths.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(flowPaths.iterator().next());
    }

    @Override
    public Optional<FlowPath> findByFlowIdAndCookie(String flowId, Cookie cookie) {
        Map<String, Object> parameters = ImmutableMap.of(
                "flow_id", flowId,
                "cookie", cookieConverter.toGraphProperty(cookie));

        Collection<FlowPath> flowPaths = loadFlowPathsWithSegments("flow_path.flow_id = $flow_id "
                + "AND flow_path.cookie = $cookie", parameters);
        if (flowPaths.size() > 1) {
            throw new PersistenceException(format("Found more that 1 FlowPath entity by (%s, %s)", flowId, cookie));
        } else if (flowPaths.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(flowPaths.iterator().next());
    }

    @Override
    public Collection<FlowPath> findByFlowId(String flowId) {
        Map<String, Object> parameters = ImmutableMap.of(
                "flow_id", flowId);

        return loadFlowPathsWithSegments("flow_path.flow_id = $flow_id", parameters);
    }

    @Override
    public Collection<FlowPath> findByFlowGroupId(String flowGroupId) {
        Map<String, Object> flowParameters = ImmutableMap.of("flow_group_id", flowGroupId);

        Set<String> flowIds = new HashSet<>();
        getSession().query(String.class, "MATCH ()-[f:flow {group_id: $flow_group_id}]->() "
                + "RETURN f.flowid", flowParameters).forEach(flowIds::add);

        if (flowIds.isEmpty()) {
            return emptyList();
        }

        Map<String, Object> flowPathParameters = ImmutableMap.of(
                "flow_ids", flowIds);

        return loadFlowPathsWithSegments("flow_path.flow_id IN $flow_ids", flowPathParameters);
    }

    @Override
    public Collection<FlowPath> findBySrcSwitch(SwitchId switchId) {
        Map<String, Object> parameters = ImmutableMap.of(
                "switch_id", switchIdConverter.toGraphProperty(switchId));

        return loadFlowPathsWithSegments("src.name = $switch_id", parameters);
    }

    @Override
    public Collection<FlowPath> findByEndpointSwitch(SwitchId switchId) {
        Map<String, Object> parameters = ImmutableMap.of(
                "switch_id", switchIdConverter.toGraphProperty(switchId));

        return loadFlowPathsWithSegments("src.name = $switch_id OR dst.name = $switch_id", parameters);
    }

    @Override
    public Collection<FlowPath> findBySegmentSwitch(SwitchId switchId) {
        Map<String, Object> parameters = ImmutableMap.of(
                "switch_id", switchIdConverter.toGraphProperty(switchId));

        Set<String> pathIds = new HashSet<>();
        getSession().query(String.class, "MATCH (ps_src:switch)-[ps:path_segment]->(ps_dst:switch) "
                + "WHERE ps_src.name = $switch_id OR ps_dst.name = $switch_id "
                + "RETURN ps.path_id", parameters).forEach(pathIds::add);

        if (pathIds.isEmpty()) {
            return emptyList();
        }

        Map<String, Object> flowPathParameters = ImmutableMap.of(
                "path_ids", pathIds);

        return loadFlowPathsWithSegments("flow_path.path_id IN $path_ids", flowPathParameters);
    }

    @Override
    public Collection<FlowPath> findBySegmentDestSwitch(SwitchId switchId) {
        Map<String, Object> parameters = ImmutableMap.of("switch_id", switchIdConverter.toGraphProperty(switchId));

        Set<String> pathIds = new HashSet<>();
        getSession().query(String.class, "MATCH (:switch)-[ps:path_segment]->(ps_dst:switch) "
                + "WHERE ps_dst.name = $switch_id "
                + "RETURN ps.path_id", parameters).forEach(pathIds::add);

        if (pathIds.isEmpty()) {
            return emptyList();
        }

        Map<String, Object> flowPathParameters = ImmutableMap.of(
                "path_ids", pathIds);

        return loadFlowPathsWithSegments("flow_path.path_id IN $path_ids", flowPathParameters);
    }

    @Override
    public void createOrUpdate(FlowPath flowPath) {
        transactionManager.doInTransaction(() -> {
            requireManagedEntity(flowPath.getSrcSwitch());
            requireManagedEntity(flowPath.getDestSwitch());

            List<PathSegment> currentSegments = findPathSegmentsByPathId(flowPath.getPathId());
            lockSwitches(getInvolvedSwitches(flowPath, currentSegments));

            createOrUpdateSegments(currentSegments, flowPath.getSegments());

            super.createOrUpdate(flowPath);
        });
    }

    private void createOrUpdateSegments(List<PathSegment> currentSegments, List<PathSegment> newSegments) {
        Session session = getSession();

        Set<Long> updatedEntities = newSegments.stream()
                .map(session::resolveGraphIdFor)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        currentSegments.forEach(segment -> {
            if (!updatedEntities.contains(session.resolveGraphIdFor(segment))) {
                session.delete(segment);
            }
        });

        for (int idx = 0; idx < newSegments.size(); idx++) {
            PathSegment segment = newSegments.get(idx);
            requireManagedEntity(segment.getSrcSwitch());
            requireManagedEntity(segment.getDestSwitch());

            segment.setSeqId(idx);
            session.save(segment, getDepthCreateUpdateEntity());
        }
    }

    @Override
    public void delete(FlowPath flowPath) {
        transactionManager.doInTransaction(() -> {
            List<PathSegment> currentSegments = findPathSegmentsByPathId(flowPath.getPathId());
            lockSwitches(getInvolvedSwitches(flowPath, currentSegments));

            Session session = getSession();
            //TODO: this is slow and requires optimization
            currentSegments.forEach(session::delete);

            super.delete(flowPath);
        });
    }

    @Override
    public void lockInvolvedSwitches(FlowPath... flowPaths) {
        lockSwitches(Arrays.stream(flowPaths)
                .filter(Objects::nonNull)
                .flatMap(path -> getInvolvedSwitches(path, findPathSegmentsByPathId(path.getPathId()))));
    }

    @Override
    public long getUsedBandwidthBetweenEndpoints(SwitchId srcSwitchId, int srcPort, SwitchId dstSwitchId, int dstPort) {
        Map<String, Object> parameters = ImmutableMap.of(
                "src_switch", switchIdConverter.toGraphProperty(srcSwitchId),
                "src_port", srcPort,
                "dst_switch", switchIdConverter.toGraphProperty(dstSwitchId),
                "dst_port", dstPort);

        String query = "MATCH (src:switch {name: $src_switch}), (dst:switch {name: $dst_switch}) "
                + "MATCH (src)-[ps:path_segment { "
                + " src_port: $src_port, "
                + " dst_port: $dst_port "
                + "}]->(dst) "
                + "MATCH ()-[fp:flow_path { path_id: ps.path_id, ignore_bandwidth: false }]->() "
                + "WITH sum(fp.bandwidth) AS used_bandwidth RETURN used_bandwidth";

        return Optional.ofNullable(getSession().queryForObject(Long.class, query, parameters))
                .orElse(0L);
    }

    private List<PathSegment> findPathSegmentsByPathId(PathId pathId) {
        Filter pathIdFilter = new Filter(PATH_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, pathId);

        return getSession().loadAll(PathSegment.class, pathIdFilter, getDepthLoadEntity()).stream()
                .sorted(Comparator.comparingInt(PathSegment::getSeqId))
                .collect(Collectors.toList());
    }

    private Stream<Switch> getInvolvedSwitches(FlowPath flowPath, Collection<PathSegment> additionalSegments) {
        return Stream.concat(
                Stream.of(flowPath.getSrcSwitch(), flowPath.getDestSwitch()),
                Stream.concat(
                        flowPath.getSegments().stream()
                                .flatMap(segment -> Stream.of(segment.getSrcSwitch(), segment.getDestSwitch())),
                        additionalSegments.stream()
                                .flatMap(segment -> Stream.of(segment.getSrcSwitch(), segment.getDestSwitch()))
                ));
    }

    @SuppressWarnings("unchecked")
    private Collection<FlowPath> loadFlowPathsWithSegments(String flowPathFilter, Map<String, Object> parameters) {
        Result queryResult = getSession().query(
                "MATCH (src:switch)-[flow_path:flow_path]->(dst:switch) "
                        + (StringUtils.isNotBlank(flowPathFilter) ? "WHERE " + flowPathFilter : "")
                        + " OPTIONAL MATCH (segment_src:switch)-[path_segment:path_segment]->(segment_dst:switch) "
                        + "WHERE path_segment.path_id = flow_path.path_id "
                        + "RETURN src, flow_path, dst, collect(segment_src), collect(segment_dst), "
                        + " collect(path_segment) as path_segments", parameters);

        Set<FlowPath> result = new HashSet<>();
        for (Map<String, Object> record : queryResult.queryResults()) {
            FlowPath flowPath = (FlowPath) record.get("flow_path");
            if (record.get("path_segments") instanceof List) {
                List<PathSegment> pathSegments = ((List<PathSegment>) record.get("path_segments")).stream()
                        .sorted(Comparator.comparingInt(PathSegment::getSeqId))
                        .collect(Collectors.toList());
                flowPath.setSegments(pathSegments);
            } else {
                flowPath.setSegments(emptyList());
            }

            result.add(flowPath);
        }
        return result;
    }

    @Override
    protected Class<FlowPath> getEntityType() {
        return FlowPath.class;
    }
}
