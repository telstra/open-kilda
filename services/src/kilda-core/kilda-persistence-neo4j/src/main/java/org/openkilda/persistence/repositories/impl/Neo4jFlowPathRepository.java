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

        FlowPath flowPath = flowPaths.iterator().next();
        flowPath.setSegments(findPathSegmentsByPathId(flowPath.getPathId()));
        return Optional.of(flowPath);
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

        FlowPath flowPath = flowPaths.iterator().next();
        flowPath.setSegments(findPathSegmentsByPathId(flowPath.getPathId()));
        return Optional.of(flowPath);
    }

    @Override
    public Collection<FlowPath> findAll() {
        Collection<FlowPath> paths = super.findAll();
        //TODO: this is slow and requires optimization
        paths.forEach(path -> path.setSegments(findPathSegmentsByPathId(path.getPathId())));
        return paths;
    }

    @Override
    public Collection<FlowPath> findByFlowId(String flowId) {
        Filter flowIdFilter = new Filter(FLOW_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, flowId);

        Collection<FlowPath> paths = loadAll(flowIdFilter);
        //TODO: this is slow and requires optimization
        paths.forEach(path -> path.setSegments(findPathSegmentsByPathId(path.getPathId())));
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
        paths.forEach(path -> path.setSegments(findPathSegmentsByPathId(path.getPathId())));
        return paths;
    }
    
    private List<PathSegment> findPathSegmentsByPathId(PathId pathId) {
        Filter pathIdFilter = new Filter(PATH_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, pathId);

        return getSession().loadAll(PathSegment.class, pathIdFilter, getDepthLoadEntity()).stream()
                .sorted(Comparator.comparingInt(PathSegment::getSeqId))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<FlowPath> findBySrcSwitchId(SwitchId switchId) {
        Filter srcSwitchFilter = createSrcSwitchFilter(switchId);

        Collection<FlowPath> paths = loadAll(srcSwitchFilter);
        //TODO: this is slow and requires optimization
        paths.forEach(path -> path.setSegments(findPathSegmentsByPathId(path.getPathId())));
        return paths;
    }

    @Override
    public Collection<PathSegment> findPathSegmentsBySrcSwitchId(SwitchId switchId) {
        Filter srcSwitchIdFilter = createSrcSwitchFilter(switchId);

        return getSession().loadAll(PathSegment.class, srcSwitchIdFilter, getDepthLoadEntity());
    }

    @Override
    public Collection<FlowPath> findBySegmentSrcSwitchId(SwitchId switchId) {
        //TODO: this is slow and requires optimization
        return findPathSegmentsBySrcSwitchId(switchId).stream()
                .map(PathSegment::getPathId)
                .map(this::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    @Override
    public Collection<PathSegment> findPathSegmentsByDestSwitchId(SwitchId switchId) {
        Filter destSwitchIdFilter = createDstSwitchFilter(switchId);

        return getSession().loadAll(PathSegment.class, destSwitchIdFilter, getDepthLoadEntity());
    }

    @Override
    public Collection<FlowPath> findBySegmentDestSwitchId(SwitchId switchId) {
        //TODO: this is slow and requires optimization
        return findPathSegmentsByDestSwitchId(switchId).stream()
                .map(PathSegment::getPathId)
                .map(this::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    @Override
    public void createOrUpdate(FlowPath flowPath) {
        transactionManager.doInTransaction(() -> {
            createOrUpdate(flowPath.getPathId(), flowPath.getSegments());

            super.createOrUpdate(flowPath);
        });
    }

    private void createOrUpdate(PathId pathId, List<PathSegment> segments) {
        List<PathSegment> currentSegments = findPathSegmentsByPathId(pathId);
        Switch[] switches = Stream.concat(currentSegments.stream(), segments.stream())
                .flatMap(segment -> Stream.of(segment.getSrcSwitch(), segment.getDestSwitch()))
                .map(this::requireManagedEntity)
                .toArray(Switch[]::new);
        lockSwitches(switches);

        Session session = getSession();
        Set<Long> updatedEntities = segments.stream()
                .map(session::resolveGraphIdFor)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        currentSegments.forEach(segment -> {
            if (!updatedEntities.contains(session.resolveGraphIdFor(segment))) {
                session.delete(segment);
            }
        });

        for (int idx = 0; idx < segments.size(); idx++) {
            PathSegment segment = segments.get(idx);
            segment.setSeqId(idx);
            session.save(segment, getDepthCreateUpdateEntity());
        }
    }

    @Override
    public void delete(FlowPath flowPath) {
        transactionManager.doInTransaction(() -> {
            Session session = getSession();
            List<PathSegment> currentSegments = findPathSegmentsByPathId(flowPath.getPathId());
            Switch[] switches = Stream.concat(currentSegments.stream(), flowPath.getSegments().stream())
                    .flatMap(segment -> Stream.of(segment.getSrcSwitch(), segment.getDestSwitch()))
                    .map(this::requireManagedEntity)
                    .toArray(Switch[]::new);
            lockSwitches(switches);

            //TODO: this is slow and requires optimization
            findPathSegmentsByPathId(flowPath.getPathId())
                    .forEach(session::delete);

            super.delete(flowPath);
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
                + "MATCH (src)-[ps:path_segment { "
                + " src_port: $src_port, "
                + " dst_port: $dst_port "
                + "}]->(dst) "
                + "MATCH ()-[fp:flow_path { path_id: ps.path_id, ignore_bandwidth: false }]->() "
                + "WITH sum(fp.bandwidth) AS used_bandwidth RETURN used_bandwidth";

        return Optional.ofNullable(getSession().queryForObject(Long.class, query, parameters))
                .orElse(0L);
    }

    @Override
    Class<FlowPath> getEntityType() {
        return FlowPath.class;
    }
}
