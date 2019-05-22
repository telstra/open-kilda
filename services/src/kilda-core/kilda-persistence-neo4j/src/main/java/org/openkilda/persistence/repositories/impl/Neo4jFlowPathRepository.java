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
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.converters.FlowPathStatusConverter;
import org.openkilda.persistence.converters.SwitchIdConverter;
import org.openkilda.persistence.repositories.FlowPathRepository;

import com.google.common.collect.ImmutableMap;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.cypher.function.FilterFunction;
import org.neo4j.ogm.session.Session;

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
    private final FlowPathStatusConverter statusConverter = new FlowPathStatusConverter();

    public Neo4jFlowPathRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    @Override
    public Optional<FlowPath> findById(PathId pathId) {
        Filter pathIdFilter = new Filter(PATH_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, pathId);

        Collection<FlowPath> flowPaths = loadAll(pathIdFilter);
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
        Filter flowIdFilter = new Filter(FLOW_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, flowId);
        flowIdFilter.setNestedPath(new Filter.NestedPathSegment(FLOW_PROPERTY_NAME, Flow.class));

        return loadAll(flowIdFilter);
    }

    @Override
    public Collection<FlowPath> findByFlowGroupId(String flowGroupId) {
        Map<String, Object> flowParameters = ImmutableMap.of("flow_group_id", flowGroupId);

        Set<String> pathIds = new HashSet<>();
        getSession().query(String.class, "MATCH (f:flow {group_id: $flow_group_id})-[:owns]-(fp:flow_path) "
                + "RETURN fp.path_id", flowParameters).forEach(pathIds::add);

        if (pathIds.isEmpty()) {
            return emptyList();
        }

        Filter pathIdsFilter = new Filter(PATH_ID_PROPERTY_NAME, new InOperatorWithNoConverterComparison(pathIds));

        return loadAll(pathIdsFilter);
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
        getSession().query(String.class,
                "MATCH (ps_src:switch)-[:source]-(ps:path_segment)-[:destination]-(ps_dst:switch) "
                        + "WHERE ps_src.name = $switch_id OR ps_dst.name = $switch_id "
                        + "MATCH (fp:flow_path)-[:owns]-(ps)"
                        + "RETURN fp.path_id", parameters).forEach(pathIds::add);

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

        Set<String> pathsId = new HashSet<>();
        getSession().query(String.class,
                "MATCH (src:switch)-[:source]-(ps:path_segment)-[:destination]-(dst:switch) "
                        + "WHERE src.name = $src_switch AND ps.src_port = $src_port  "
                        + "AND dst.name = $dst_switch AND ps.dst_port = $dst_port  "
                        + "MATCH (fp:flow_path)-[:owns]-(ps) "
                        + "RETURN fp.path_id", parameters).forEach(pathsId::add);

        if (pathsId.isEmpty()) {
            return emptyList();
        }

        Filter pathIdsFilter = new Filter(PATH_ID_PROPERTY_NAME, new InOperatorWithNoConverterComparison(pathsId));
        pathIdsFilter.setPropertyConverter(null);

        return loadAll(pathIdsFilter);
    }

    @Override
    public Collection<FlowPath> findBySegmentDestSwitch(SwitchId switchId) {
        Map<String, Object> parameters = ImmutableMap.of(
                "switch_id", switchIdConverter.toGraphProperty(switchId));

        Set<String> pathIds = new HashSet<>();
        getSession().query(String.class,
                "MATCH (fp:flow_path)-[:owns]-(:path_segment)-[:destination]-(ps_dst:switch) "
                        + "WHERE ps_dst.name = $switch_id "
                        + "RETURN fp.path_id", parameters).forEach(pathIds::add);

        if (pathIds.isEmpty()) {
            return emptyList();
        }

        Filter pathIdsFilter = new Filter(PATH_ID_PROPERTY_NAME, new InOperatorWithNoConverterComparison(pathIds));
        pathIdsFilter.setPropertyConverter(null);

        return loadAll(pathIdsFilter);
    }

    @Override
    public Collection<FlowPath> findAffectedPaths(SwitchId switchId, int port) {
        Map<String, Object> parameters = ImmutableMap.of(
                "switch_id", switchIdConverter.toGraphProperty(switchId),
                "port", port);

        Set<String> pathsId = new HashSet<>();
        getSession().query(String.class,
                "MATCH (src:switch)-[:source]-(ps:path_segment)-[:destination]-(dst:switch) "
                        + "WHERE ((src.name = $switch_id AND ps.src_port = $port) "
                        + "OR (dst.name = $switch_id AND ps.dst_port = $port)) "
                        + "MATCH (fp:flow_path)-[:owns]-(ps) "
                        + "RETURN fp.path_id", parameters).forEach(pathsId::add);

        if (pathsId.isEmpty()) {
            return emptyList();
        }

        Filter pathIdsFilter = new Filter(PATH_ID_PROPERTY_NAME, new InOperatorWithNoConverterComparison(pathsId));
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
            Collection<PathSegment> currentSegments = findPathSegmentsByPathId(flowPath.getPathId());
            lockSwitches(getInvolvedSwitches(flowPath, currentSegments));

            super.createOrUpdate(flowPath);
        });
    }

    @Override
    public void delete(FlowPath flowPath) {
        transactionManager.doInTransaction(() -> {
            Collection<PathSegment> currentSegments = findPathSegmentsByPathId(flowPath.getPathId());
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
                .map(path -> getInvolvedSwitches(path, findPathSegmentsByPathId(path.getPathId())))
                .flatMap(Arrays::stream)
                .toArray(Switch[]::new));
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

        return Optional.ofNullable(getSession().queryForObject(Long.class, query, parameters))
                .orElse(0L);
    }

    private Collection<PathSegment> findPathSegmentsByPathId(PathId pathId) {
        Filter pathIdFilter = new Filter(PATH_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, pathId);
        pathIdFilter.setNestedPath(new Filter.NestedPathSegment("path", FlowPath.class));

        return getSession().loadAll(PathSegment.class, pathIdFilter, getDepthLoadEntity());
    }

    private Switch[] getInvolvedSwitches(FlowPath flowPath, Collection<PathSegment> additionalSegments) {
        return Stream.concat(
                Stream.of(flowPath.getSrcSwitch(), flowPath.getDestSwitch()),
                Stream.concat(
                        flowPath.getSegments().stream()
                                .flatMap(segment -> Stream.of(segment.getSrcSwitch(), segment.getDestSwitch())),
                        additionalSegments.stream()
                                .flatMap(segment -> Stream.of(segment.getSrcSwitch(), segment.getDestSwitch()))
                ))
                .filter(Objects::nonNull)
                .toArray(Switch[]::new);
    }

    @Override
    protected Class<FlowPath> getEntityType() {
        return FlowPath.class;
    }

    @Override
    protected int getDepthLoadEntity() {
        // depth 2 is needed to load switches in PathSegment entity.
        return 2;
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
        requireManagedEntity(flowPath.getSrcSwitch());
        requireManagedEntity(flowPath.getDestSwitch());

        flowPath.getSegments().forEach(pathSegment -> {
            requireManagedEntity(pathSegment.getSrcSwitch());
            requireManagedEntity(pathSegment.getDestSwitch());

            // A segment must reference the same flow path.
            if (pathSegment.getPath() != flowPath) {
                throw new IllegalArgumentException(
                        format("Segment %s references different flow path %s, but expect %s",
                        pathSegment, pathSegment.getPath(), flowPath));
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
