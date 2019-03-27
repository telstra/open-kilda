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

import org.openkilda.model.Cookie;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowPathRepository;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.session.Session;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
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
    static final String FLOW_ID_PROPERTY_NAME = "flowid";
    static final String COOKIE_PROPERTY_NAME = "cookie";

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

        return Optional.of(flowPaths.iterator().next())
                .map(this::completeWithSegments);
    }

    @Override
    public Optional<FlowPath> findByFlowIdAndCookie(String flowId, Cookie cookie) {
        Filter flowIdFilter = new Filter(FLOW_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, flowId);
        Filter cookieFilter = new Filter(COOKIE_PROPERTY_NAME, ComparisonOperator.EQUALS, cookie);

        Collection<FlowPath> flowPaths = loadAll(flowIdFilter.and(cookieFilter));
        if (flowPaths.size() > 1) {
            throw new PersistenceException(format("Found more that 1 FlowPath entity by (%s, %s)", flowId, cookie));
        } else if (flowPaths.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(flowPaths.iterator().next())
                .map(this::completeWithSegments);
    }

    @Override
    public Collection<FlowPath> findAll() {
        Collection<FlowPath> paths = super.findAll();
        //TODO: this is slow and requires optimization
        paths.forEach(this::completeWithSegments);
        return paths;
    }

    @Override
    public Collection<FlowPath> findByFlowId(String flowId) {
        Filter flowIdFilter = new Filter(FLOW_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, flowId);

        Collection<FlowPath> paths = loadAll(flowIdFilter);
        //TODO: this is slow and requires optimization
        paths.forEach(this::completeWithSegments);
        return paths;
    }

    @Override
    public Collection<FlowPath> findByFlowGroupId(String flowGroupId) {
        Map<String, Object> parameters = ImmutableMap.of("flow_group_id", flowGroupId);
        String query = "MATCH (:switch)-[f:flow {group_id: $flow_group_id}]->(:switch) "
                + "MATCH (src:switch)-[fp:flow_path]->(dst:switch) "
                + "WHERE fp.flow_id = f.flowid "
                + "RETURN src, fp, dst";

        Collection<FlowPath> paths = Lists.newArrayList(getSession().query(FlowPath.class, query, parameters));
        //TODO: this is slow and requires optimization
        paths.forEach(this::completeWithSegments);
        return paths;
    }

    @Override
    public Collection<FlowPath> findBySrcSwitch(SwitchId switchId) {
        Map<String, Object> parameters = ImmutableMap.of("switch_id", switchId.toString());
        String query = "MATCH (src:switch)-[fp:flow_path]->(dst:switch) "
                + "WHERE src.name = $switch_id "
                + "RETURN src, fp, dst";

        Collection<FlowPath> paths = Lists.newArrayList(getSession().query(FlowPath.class, query, parameters));
        //TODO: this is slow and requires optimization
        paths.forEach(this::completeWithSegments);
        return paths;
    }

    @Override
    public Collection<FlowPath> findByEndpointSwitch(SwitchId switchId) {
        Map<String, Object> parameters = ImmutableMap.of("switch_id", switchId.toString());
        String query = "MATCH (src:switch)-[fp:flow_path]->(dst:switch) "
                + "WHERE src.name = $switch_id or dst.name = $switch_id "
                + "RETURN src, fp, dst";

        Collection<FlowPath> paths = Lists.newArrayList(getSession().query(FlowPath.class, query, parameters));
        //TODO: this is slow and requires optimization
        paths.forEach(this::completeWithSegments);
        return paths;
    }

    @Override
    public Collection<FlowPath> findBySegmentSwitch(SwitchId switchId) {
        Map<String, Object> parameters = ImmutableMap.of("switch_id", switchId.toString());
        String query = "MATCH (ps_src:switch)-[ps:path_segment]->(ps_dst:switch) "
                + "WHERE ps_src.name = $switch_id OR ps_dst.name = $switch_id "
                + "MATCH (src:switch)-[fp:flow_path]->(dst:switch) "
                + "WHERE ps.path_id = fp.path_id "
                + "RETURN src, fp, dst";

        Collection<FlowPath> paths = Lists.newArrayList(getSession().query(FlowPath.class, query, parameters));
        //TODO: this is slow and requires optimization
        paths.forEach(this::completeWithSegments);
        return paths;
    }

    @Override
    public Collection<FlowPath> findBySegmentDestSwitch(SwitchId switchId) {
        Map<String, Object> parameters = ImmutableMap.of("switch_id", switchId.toString());
        String query = "MATCH (:switch)-[ps:path_segment]->(:switch {name: $switch_id}) "
                + "MATCH (src:switch)-[fp:flow_path]->(dst:switch) "
                + "WHERE ps.path_id = fp.path_id "
                + "RETURN src, fp, dst";

        Collection<FlowPath> paths = Lists.newArrayList(getSession().query(FlowPath.class, query, parameters));
        //TODO: this is slow and requires optimization
        paths.forEach(this::completeWithSegments);
        return paths;
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
                "src_switch", srcSwitchId.toString(),
                "src_port", srcPort,
                "dst_switch", dstSwitchId.toString(),
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

    private FlowPath completeWithSegments(FlowPath flowPath) {
        flowPath.setSegments(findPathSegmentsByPathId(flowPath.getPathId()));
        return flowPath;
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

    @Override
    protected Class<FlowPath> getEntityType() {
        return FlowPath.class;
    }
}
