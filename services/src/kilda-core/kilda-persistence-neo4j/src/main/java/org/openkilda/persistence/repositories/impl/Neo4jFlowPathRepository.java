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
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.session.Session;

import java.util.ArrayList;
import java.util.Collection;
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

    private List<PathSegment> findPathSegmentsByPathId(PathId pathId) {
        Filter pathIdFilter = new Filter(PATH_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, pathId);

        return new ArrayList<>(getSession().loadAll(PathSegment.class, pathIdFilter, getDepthLoadEntity()));
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
    public Collection<PathSegment> findPathSegmentsByDestSwitchId(SwitchId switchId) {
        Filter destSwitchIdFilter = createDstSwitchFilter(switchId);

        return getSession().loadAll(PathSegment.class, destSwitchIdFilter, getDepthLoadEntity());
    }

    @Override
    public Collection<PathSegment> findAllPathSegments() {
        return getSession().loadAll(PathSegment.class, getDepthLoadEntity());
    }

    @Override
    public void createOrUpdate(FlowPath entity) {
        transactionManager.doInTransaction(() -> {
            createOrUpdate(entity.getPathId(), entity.getSegments());

            super.createOrUpdate(entity);
        });
    }

    private void createOrUpdate(PathId pathId, Collection<PathSegment> segments) {
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

        segments.forEach(segment -> {
            session.save(segment, getDepthCreateUpdateEntity());
        });
    }

    @Override
    public void createOrUpdate(PathSegment segment) {
        transactionManager.doInTransaction(() -> {
            lockSwitches(requireManagedEntity(segment.getSrcSwitch()), requireManagedEntity(segment.getDestSwitch()));

            getSession().save(segment, getDepthCreateUpdateEntity());
        });
    }

    @Override
    public void delete(PathSegment segment) {
        Session session = getSession();
        if (session.resolveGraphIdFor(segment) == null) {
            throw new PersistenceException("Required GraphId wasn't set: " + segment.toString());
        }
        session.delete(segment);
    }

    @Override
    public long getUsedBandwidthBetweenEndpoints(SwitchId srcSwitchId, int srcPort, SwitchId dstSwitchId, int dstPort) {
        Map<String, Object> parameters = ImmutableMap.of(
                "src_switch", srcSwitchId.toString(),
                "src_port", srcPort,
                "dst_switch", dstSwitchId.toString(),
                "dst_port", dstPort);

        String query = "MATCH (src:switch {name: $src_switch}), (dst:switch {name: $dst_switch}) "
                + "WITH src, dst "
                + "MATCH (src) - [ps:path_segment { "
                + " src_port: $src_port, "
                + " dst_port: $dst_port "
                + "}] -> (dst) "
                + "MATCH () - [fp:flow_path { path_id: ps.path_id, ignore_bandwidth: false }] - () "
                + "WITH sum(fp.bandwidth) AS used_bandwidth RETURN used_bandwidth";

        return Optional.ofNullable(getSession().queryForObject(Long.class, query, parameters))
                .orElse(0L);
    }

    @Override
    Class<FlowPath> getEntityType() {
        return FlowPath.class;
    }
}
