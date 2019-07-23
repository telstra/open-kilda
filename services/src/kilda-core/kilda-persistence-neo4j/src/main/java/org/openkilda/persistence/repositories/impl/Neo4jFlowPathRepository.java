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
import static org.openkilda.persistence.repositories.impl.Neo4jFlowRepository.FLOW_ID_PROPERTY_NAME;

import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.FetchStrategy;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.converters.FlowPathStatusConverter;
import org.openkilda.persistence.converters.PathIdConverter;
import org.openkilda.persistence.converters.SwitchIdConverter;
import org.openkilda.persistence.repositories.FlowPathRepository;

import com.google.common.collect.ImmutableMap;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.cypher.function.FilterFunction;
import org.neo4j.ogm.session.Neo4jSession;
import org.neo4j.ogm.session.Session;
import org.neo4j.ogm.typeconversion.InstantStringConverter;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Neo4j OGM implementation of {@link FlowPathRepository}.
 */
public class Neo4jFlowPathRepository extends Neo4jGenericRepository<FlowPath> implements FlowPathRepository {
    static final String PATH_ID_PROPERTY_NAME = "path_id";
    static final String FLOW_PROPERTY_NAME = "flow";
    static final String COOKIE_PROPERTY_NAME = "cookie";

    private final SwitchIdConverter switchIdConverter = new SwitchIdConverter();
    private final PathIdConverter pathIdConverter = new PathIdConverter();
    private final FlowPathStatusConverter statusConverter = new FlowPathStatusConverter();
    private final InstantStringConverter instantStringConverter = new InstantStringConverter();

    public Neo4jFlowPathRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    @Override
    public Optional<FlowPath> findById(PathId pathId) {
        return findById(pathId, getDefaultFetchStrategy());
    }

    @Override
    public Optional<FlowPath> findById(PathId pathId, FetchStrategy fetchStrategy) {
        Filter pathIdFilter = new Filter(PATH_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, pathId);

        Collection<FlowPath> flowPaths = loadAll(pathIdFilter, fetchStrategy);
        if (flowPaths.size() > 1) {
            throw new PersistenceException(format("Found more that 1 FlowPath entity by (%s)", pathId));
        } else if (flowPaths.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(flowPaths.iterator().next());
    }

    @Override
    public Optional<FlowPath> findByFlowIdAndCookie(String flowId, Cookie cookie) {
        Filter flowIdFilter = new Filter(FLOW_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, flowId);
        flowIdFilter.setNestedPath(new Filter.NestedPathSegment(FLOW_PROPERTY_NAME, Flow.class));
        Filter cookieFilter = new Filter(COOKIE_PROPERTY_NAME, ComparisonOperator.EQUALS, cookie);

        Collection<FlowPath> flowPaths = loadAll(flowIdFilter.and(cookieFilter));
        if (flowPaths.size() > 1) {
            throw new PersistenceException(format("Found more that 1 FlowPath entity by (%s, %s)", flowId, cookie));
        } else if (flowPaths.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(flowPaths.iterator().next());
    }

    @Override
    public Collection<FlowPath> findByFlowId(String flowId) {
        return findByFlowId(flowId, getDefaultFetchStrategy());
    }

    @Override
    public Collection<FlowPath> findByFlowId(String flowId, FetchStrategy fetchStrategy) {
        Filter flowIdFilter = new Filter(FLOW_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, flowId);
        flowIdFilter.setNestedPath(new Filter.NestedPathSegment(FLOW_PROPERTY_NAME, Flow.class));

        return loadAll(flowIdFilter, fetchStrategy);
    }

    @Override
    public Collection<FlowPath> findByFlowGroupId(String flowGroupId) {
        Map<String, Object> flowParameters = ImmutableMap.of("flow_group_id", flowGroupId);

        Set<String> pathIds = new HashSet<>();
        queryForStrings("MATCH (f:flow {group_id: $flow_group_id})-[:owns]-(fp:flow_path) "
                + "RETURN fp.path_id as path_id", flowParameters, "path_id").forEach(pathIds::add);

        if (pathIds.isEmpty()) {
            return emptyList();
        }

        Filter pathIdsFilter = new Filter(PATH_ID_PROPERTY_NAME, new InOperatorWithNoConverterComparison(pathIds));

        return loadAll(pathIdsFilter);
    }

    @Override
    public Collection<PathId> findPathIdsByFlowGroupId(String flowGroupId) {
        Map<String, Object> flowParameters = ImmutableMap.of("flow_group_id", flowGroupId);

        Set<PathId> pathIds = new HashSet<>();
        queryForStrings("MATCH (f:flow {group_id: $flow_group_id})-[:owns]-(fp:flow_path) "
                + "RETURN fp.path_id as path_id", flowParameters, "path_id")
                .forEach(pathId -> pathIds.add(pathIdConverter.toEntityAttribute(pathId)));

        return pathIds;
    }

    @Override
    public Collection<FlowPath> findBySrcSwitch(SwitchId switchId) {
        Filter srcSwitchFilter = createSrcSwitchFilter(switchId);

        return filterSrcProtectedPathEndpoint(loadAll(srcSwitchFilter).stream(), switchId)
                .collect(Collectors.toList());
    }

    @Override
    public Collection<FlowPath> findByEndpointSwitch(SwitchId switchId) {
        Filter srcSwitchFilter = createSrcSwitchFilter(switchId);
        Filter dstSwitchFilter = createDstSwitchFilter(switchId);

        return filterSrcProtectedPathEndpoint(
                Stream.concat(loadAll(srcSwitchFilter).stream(), loadAll(dstSwitchFilter).stream()), switchId)
                .collect(Collectors.toList());
    }

    private Stream<FlowPath> filterSrcProtectedPathEndpoint(Stream<FlowPath> pathStream, SwitchId switchId) {
        return pathStream.filter(
                path -> !(path.isProtected() && switchId.equals(path.getSrcSwitch().getSwitchId())));
    }

    @Override
    public Collection<FlowPath> findBySegmentSwitch(SwitchId switchId) {
        Map<String, Object> parameters = ImmutableMap.of(
                "switch_id", switchIdConverter.toGraphProperty(switchId));

        Set<String> pathIds = new HashSet<>();
        queryForStrings("MATCH (ps_src:switch)-[:source]-(ps:path_segment)-[:destination]-(ps_dst:switch) "
                + "WHERE ps_src.name = $switch_id OR ps_dst.name = $switch_id "
                + "MATCH (fp:flow_path)-[:owns]-(ps)"
                + "RETURN fp.path_id as path_id", parameters, "path_id").forEach(pathIds::add);

        if (pathIds.isEmpty()) {
            return emptyList();
        }

        Filter pathIdsFilter = new Filter(PATH_ID_PROPERTY_NAME, new InOperatorWithNoConverterComparison(pathIds));

        return loadAll(pathIdsFilter);
    }

    @Override
    public Collection<FlowPath> findWithPathSegment(SwitchId srcSwitchId, int srcPort,
                                                    SwitchId dstSwitchId, int dstPort) {
        Map<String, Object> parameters = ImmutableMap.of(
                "src_switch", switchIdConverter.toGraphProperty(srcSwitchId),
                "src_port", srcPort,
                "dst_switch", switchIdConverter.toGraphProperty(dstSwitchId),
                "dst_port", dstPort);

        Set<String> pathIds = new HashSet<>();
        queryForStrings("MATCH (src:switch)-[:source]-(ps:path_segment)-[:destination]-(dst:switch) "
                + "WHERE src.name = $src_switch AND ps.src_port = $src_port  "
                + "AND dst.name = $dst_switch AND ps.dst_port = $dst_port  "
                + "MATCH (fp:flow_path)-[:owns]-(ps) "
                + "RETURN fp.path_id as path_id", parameters, "path_id").forEach(pathIds::add);

        if (pathIds.isEmpty()) {
            return emptyList();
        }

        Filter pathIdsFilter = new Filter(PATH_ID_PROPERTY_NAME, new InOperatorWithNoConverterComparison(pathIds));
        pathIdsFilter.setPropertyConverter(null);

        return loadAll(pathIdsFilter);
    }

    @Override
    public Collection<FlowPath> findBySegmentDestSwitch(SwitchId switchId) {
        Map<String, Object> parameters = ImmutableMap.of(
                "switch_id", switchIdConverter.toGraphProperty(switchId));

        Set<String> pathIds = new HashSet<>();
        queryForStrings("MATCH (fp:flow_path)-[:owns]-(:path_segment)-[:destination]-(ps_dst:switch) "
                + "WHERE ps_dst.name = $switch_id "
                + "RETURN fp.path_id as path_id", parameters, "path_id").forEach(pathIds::add);

        if (pathIds.isEmpty()) {
            return emptyList();
        }

        Filter pathIdsFilter = new Filter(PATH_ID_PROPERTY_NAME, new InOperatorWithNoConverterComparison(pathIds));
        pathIdsFilter.setPropertyConverter(null);

        return loadAll(pathIdsFilter);
    }

    @Override
    public Collection<FlowPath> findBySegmentEndpoint(SwitchId switchId, int port) {
        Map<String, Object> parameters = ImmutableMap.of(
                "switch_id", switchIdConverter.toGraphProperty(switchId),
                "port", port);

        Set<String> pathIds = new HashSet<>();
        queryForStrings("MATCH (src:switch)-[:source]-(ps:path_segment)-[:destination]-(dst:switch) "
                + "WHERE ((src.name = $switch_id AND ps.src_port = $port) "
                + "OR (dst.name = $switch_id AND ps.dst_port = $port)) "
                + "MATCH (fp:flow_path)-[:owns]-(ps) "
                + "RETURN fp.path_id as path_id", parameters, "path_id").forEach(pathIds::add);

        if (pathIds.isEmpty()) {
            return emptyList();
        }

        Filter pathIdsFilter = new Filter(PATH_ID_PROPERTY_NAME, new InOperatorWithNoConverterComparison(pathIds));
        pathIdsFilter.setPropertyConverter(null);

        return loadAll(pathIdsFilter);
    }

    @Override
    public void createOrUpdate(FlowPath flowPath) {
        // The flow path must reference a managed flow to avoid creation of duplicated flow.
        requireManagedEntity(flowPath.getFlow());
        validateFlowPath(flowPath);

        if (flowPath.getTimeCreate() == null) {
            flowPath.setTimeCreate(Instant.now());
        } else {
            flowPath.setTimeModify(Instant.now());
        }

        transactionManager.doInTransaction(() -> {
            Session session = getSession();
            // To avoid Neo4j deadlocks, we perform locking of switch nodes in the case of new flow, path or segments.
            boolean isNewPath = session.resolveGraphIdFor(flowPath) == null;
            if (isNewPath || hasUnmanagedEntity(flowPath)) {
                lockInvolvedSwitches(flowPath);
            }

            if (!isNewPath) {
                deleteOrphanSegments(flowPath);
            }

            super.createOrUpdate(flowPath);
        });
    }

    private boolean hasUnmanagedEntity(FlowPath path) {
        Session session = getSession();
        for (PathSegment segment : path.getSegments()) {
            if (session.resolveGraphIdFor(segment) == null) {
                return true;
            }
        }

        return false;
    }

    private void deleteOrphanSegments(FlowPath flowPath) {
        Session session = getSession();
        Set<Long> currentSegmentIds = findSegmentEntityIdsByPathId(flowPath.getPathId());
        flowPath.getSegments().stream()
                .map(session::resolveGraphIdFor)
                .filter(Objects::nonNull)
                .forEach(currentSegmentIds::remove);

        if (!currentSegmentIds.isEmpty()) {
            lockInvolvedSwitches(flowPath);

            currentSegmentIds.forEach(this::deleteSegmentByEntityId);
        }
    }

    private Set<Long> findSegmentEntityIdsByPathId(PathId pathId) {
        Map<String, Object> parameters = ImmutableMap.of(
                "path_id", pathIdConverter.toGraphProperty(pathId));

        Set<Long> segmentEntityIds = new HashSet<>();
        queryForLongs("MATCH (flow_path {path_id: $path_id})-[:owns]-(ps:path_segment) RETURN id(ps) as id",
                parameters, "id").forEach(segmentEntityIds::add);
        return segmentEntityIds;
    }

    private void deleteSegmentByEntityId(Long segmentEntityId) {
        Map<String, Object> parameters = ImmutableMap.of(
                "segment_id", segmentEntityId);

        Session session = getSession();
        session.query("MATCH (ps:path_segment) WHERE id(ps) = $segment_id "
                + "DETACH DELETE ps", parameters);

        ((Neo4jSession) session).context().detachNodeEntity(segmentEntityId);
    }

    @Override
    public void delete(FlowPath flowPath) {
        transactionManager.doInTransaction(() -> {
            lockInvolvedSwitches(flowPath);

            deleteSegmentsByPathId(flowPath.getPathId());

            super.delete(flowPath);
        });
    }

    private void deleteSegmentsByPathId(PathId pathId) {
        Map<String, Object> parameters = ImmutableMap.of(
                "path_id", pathIdConverter.toGraphProperty(pathId));

        Session session = getSession();
        queryForLongs("MATCH (flow_path {path_id: $path_id})-[:owns]-(ps:path_segment) "
                        + "DETACH DELETE ps "
                        + "RETURN id(ps) as id", parameters, "id")
                .forEach(deletedEntityId -> ((Neo4jSession) session).context().detachNodeEntity(deletedEntityId));
    }

    @Override
    public void lockInvolvedSwitches(FlowPath... flowPaths) {
        lockSwitches(Arrays.stream(flowPaths)
                .filter(Objects::nonNull)
                .flatMap(path -> {
                    Set<SwitchId> switchesToLock = findSegmentSwitchesByPathId(path.getPathId());
                    switchesToLock.add(path.getSrcSwitch().getSwitchId());
                    switchesToLock.add(path.getDestSwitch().getSwitchId());
                    path.getSegments().forEach(segment -> {
                        switchesToLock.add(segment.getSrcSwitch().getSwitchId());
                        switchesToLock.add(segment.getDestSwitch().getSwitchId());
                    });
                    return switchesToLock.stream();
                }));
    }

    private Set<SwitchId> findSegmentSwitchesByPathId(PathId pathId) {
        Map<String, Object> parameters = ImmutableMap.of(
                "path_id", pathIdConverter.toGraphProperty(pathId));

        Set<SwitchId> switchIds = new HashSet<>();
        queryForStrings("MATCH (fp:flow_path {path_id: $path_id})-[:owns]-(:path_segment)-[]-(sw:switch) "
                + "RETURN sw.name as name", parameters, "name")
                .forEach(switchId -> switchIds.add(new SwitchId(switchId)));
        return switchIds;
    }

    @Override
    public void updateStatus(PathId pathId, FlowPathStatus pathStatus) {
        Instant timestamp = Instant.now();
        Map<String, Object> parameters = ImmutableMap.of(
                "path_id", pathIdConverter.toGraphProperty(pathId),
                "status", statusConverter.toGraphProperty(pathStatus),
                "time_modify", instantStringConverter.toGraphProperty(timestamp));
        Session session = getSession();
        Optional<Long> updatedEntityId = queryForLong(
                "MATCH (fp:flow_path {path_id: $path_id}) "
                        + "SET fp.status=$status, fp.time_modify=$time_modify "
                        + "RETURN id(fp) as id", parameters, "id");
        if (!updatedEntityId.isPresent()) {
            throw new PersistenceException(format("Path not found to be updated: %s", pathId));
        }

        Object updatedEntity = ((Neo4jSession) session).context().getNodeEntity(updatedEntityId.get());
        if (updatedEntity instanceof FlowPath) {
            FlowPath updatedPath = (FlowPath) updatedEntity;
            updatedPath.setStatus(pathStatus);
            updatedPath.setTimeModify(timestamp);
        } else if (updatedEntity != null) {
            throw new PersistenceException(format("Expected a FlowPath entity, but found %s.", updatedEntity));
        }
    }

    @Override
    public long getUsedBandwidthBetweenEndpoints(SwitchId srcSwitchId, int srcPort, SwitchId dstSwitchId, int dstPort) {
        Map<String, Object> parameters = ImmutableMap.of(
                "src_switch", switchIdConverter.toGraphProperty(srcSwitchId),
                "src_port", srcPort,
                "dst_switch", switchIdConverter.toGraphProperty(dstSwitchId),
                "dst_port", dstPort);

        String query = "MATCH (src:switch {name: $src_switch}), (dst:switch {name: $dst_switch}) "
                + "MATCH (src)-[:source]-(ps:path_segment { "
                + " src_port: $src_port, "
                + " dst_port: $dst_port "
                + "})-[:destination]-(dst) "
                + "MATCH (fp:flow_path { ignore_bandwidth: false })-[:owns]-(ps) "
                + "WITH sum(fp.bandwidth) AS used_bandwidth RETURN used_bandwidth";

        return queryForLong(query, parameters, "used_bandwidth").orElse(0L);
    }

    @Override
    protected Class<FlowPath> getEntityType() {
        return FlowPath.class;
    }

    @Override
    protected FetchStrategy getDefaultFetchStrategy() {
        return FetchStrategy.ALL_RELATIONS;
    }

    @Override
    protected int getDepthLoadEntity(FetchStrategy fetchStrategy) {
        switch (fetchStrategy) {
            case ALL_RELATIONS:
                // depth 2 is needed to load switches in PathSegment entity.
                return 2;
            default:
                return super.getDepthLoadEntity(fetchStrategy);
        }
    }

    @Override
    protected int getDepthCreateUpdateEntity() {
        // depth 2 is needed to create/update relations to switches, path segments and switches of path segments.
        return 2;
    }

    /**
     * Validate the path relations and path segments to be managed by Neo4j OGM and reference the flow path.
     */
    void validateFlowPath(FlowPath flowPath) {
        // Check for nulls as the entity may be read not completely.
        if (flowPath.getSrcSwitch() != null) {
            requireManagedEntity(flowPath.getSrcSwitch());
        }
        if (flowPath.getDestSwitch() != null) {
            requireManagedEntity(flowPath.getDestSwitch());
        }

        flowPath.getSegments().forEach(pathSegment -> {
            if (pathSegment.getSrcSwitch() != null) {
                requireManagedEntity(pathSegment.getSrcSwitch());
            }
            if (pathSegment.getDestSwitch() != null) {
                requireManagedEntity(pathSegment.getDestSwitch());
            }
        });
    }

    private static class InOperatorWithNoConverterComparison implements FilterFunction<Object> {

        private final Object value;
        private Filter filter;

        public InOperatorWithNoConverterComparison(Object value) {
            this.value = value;
        }

        @Override
        public Filter getFilter() {
            return filter;
        }

        @Override
        public void setFilter(Filter filter) {
            this.filter = filter;
        }

        @Override
        public Object getValue() {
            return value;
        }

        @Override
        public String expression(String nodeIdentifier) {
            return String.format("%s.`%s` IN { `%s` } ", nodeIdentifier, filter.getPropertyName(),
                    filter.uniqueParameterName());
        }

        @Override
        public Map<String, Object> parameters() {
            Map<String, Object> map = new HashMap<>();
            map.put(filter.uniqueParameterName(), value);
            return map;
        }
    }
}
