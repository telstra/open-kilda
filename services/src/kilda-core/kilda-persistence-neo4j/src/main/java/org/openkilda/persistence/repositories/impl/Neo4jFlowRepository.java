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
import static java.util.Collections.singleton;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.FetchStrategy;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.converters.FlowStatusConverter;
import org.openkilda.persistence.repositories.FlowRepository;

import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.session.Neo4jSession;
import org.neo4j.ogm.session.Session;
import org.neo4j.ogm.typeconversion.InstantStringConverter;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Neo4j OGM implementation of {@link FlowRepository}.
 */
@Slf4j
public class Neo4jFlowRepository extends Neo4jGenericRepository<Flow> implements FlowRepository {
    static final String FLOW_ID_PROPERTY_NAME = "flow_id";
    static final String GROUP_ID_PROPERTY_NAME = "group_id";
    static final String SRC_PORT_PROPERTY_NAME = "src_port";
    static final String DST_PORT_PROPERTY_NAME = "dst_port";
    static final String SRC_MULTI_TABLE_PROPERTY_NAME = "src_with_multi_table";
    static final String DST_MULTI_TABLE_PROPERTY_NAME = "dst_with_multi_table";
    static final String PERIODIC_PINGS_PROPERTY_NAME = "periodic_pings";
    static final String STATUS_PROPERTY_NAME = "status";

    private final FlowStatusConverter flowStatusConverter = new FlowStatusConverter();
    private final InstantStringConverter instantStringConverter = new InstantStringConverter();

    private final Neo4jFlowPathRepository flowPathRepository;

    public Neo4jFlowRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);

        flowPathRepository = new Neo4jFlowPathRepository(sessionFactory, transactionManager);
    }

    @Override
    public Collection<Flow> findAll() {
        return loadAll(EMPTY_FILTERS, FetchStrategy.DIRECT_RELATIONS);
    }

    @Override
    public long countFlows() {
        return getSession().countEntitiesOfType(getEntityType());
    }

    @Override
    public boolean exists(String flowId) {
        Filter flowIdFilter = new Filter(FLOW_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, flowId);

        return getSession().count(getEntityType(), singleton(flowIdFilter)) > 0;
    }

    @Override
    public Optional<Flow> findById(String flowId) {
        return findById(flowId, getDefaultFetchStrategy());
    }

    @Override
    public Optional<Flow> findById(String flowId, FetchStrategy fetchStrategy) {
        Filter flowIdFilter = new Filter(FLOW_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, flowId);

        Collection<Flow> flows = loadAll(flowIdFilter, fetchStrategy);
        if (flows.size() > 1) {
            throw new PersistenceException(format("Found more that 1 Flow entity by %s as flowId", flowId));
        } else if (flows.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(flows.iterator().next());
    }

    @Override
    public Collection<Flow> findByGroupId(String flowGroupId) {
        Filter groupIdFilter = new Filter(GROUP_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, flowGroupId);

        return loadAll(groupIdFilter);
    }

    @Override
    public Collection<String> findFlowsIdByGroupId(String flowGroupId) {
        Map<String, Object> flowParameters = ImmutableMap.of("flow_group_id", flowGroupId);

        return queryForStrings(
                "MATCH (f:flow {group_id: $flow_group_id}) RETURN f.flow_id as flow_id", flowParameters, "flow_id");
    }

    @Override
    public Collection<Flow> findWithPeriodicPingsEnabled() {
        Filter periodicPingsFilter = new Filter(PERIODIC_PINGS_PROPERTY_NAME, ComparisonOperator.EQUALS, true);

        return loadAll(periodicPingsFilter);
    }

    @Override
    public Collection<Flow> findByEndpoint(SwitchId switchId, int port) {
        Filter srcSwitchFilter = createSrcSwitchFilter(switchId);
        Filter srcPortFilter = new Filter(SRC_PORT_PROPERTY_NAME, ComparisonOperator.EQUALS, port);
        Filter dstSwitchFilter = createDstSwitchFilter(switchId);
        Filter dstPortFilter = new Filter(DST_PORT_PROPERTY_NAME, ComparisonOperator.EQUALS, port);

        return Stream.concat(
                loadAll(srcSwitchFilter.and(srcPortFilter)).stream(),
                loadAll(dstSwitchFilter.and(dstPortFilter)).stream())
                .collect(Collectors.toList());
    }

    @Override
    public Collection<Flow> findByEndpointWithMultiTableSupport(SwitchId switchId, int port) {
        Filter srcSwitchFilter = createSrcSwitchFilter(switchId);
        Filter srcPortFilter = new Filter(SRC_PORT_PROPERTY_NAME, ComparisonOperator.EQUALS, port);
        Filter srcMultiTableFilter = new Filter(SRC_MULTI_TABLE_PROPERTY_NAME, ComparisonOperator.IS_TRUE);
        Filter dstSwitchFilter = createDstSwitchFilter(switchId);
        Filter dstPortFilter = new Filter(DST_PORT_PROPERTY_NAME, ComparisonOperator.EQUALS, port);
        Filter dstMultiTableFilter = new Filter(DST_MULTI_TABLE_PROPERTY_NAME, ComparisonOperator.IS_TRUE);

        return Stream.concat(
                loadAll(srcSwitchFilter.and(srcPortFilter).and(srcMultiTableFilter)).stream(),
                loadAll(dstSwitchFilter.and(dstPortFilter).and(dstMultiTableFilter)).stream())
                .collect(Collectors.toList());
    }

    @Override
    public Collection<Flow> findByEndpointSwitch(SwitchId switchId) {
        Filter srcSwitchFilter = createSrcSwitchFilter(switchId);
        Filter dstSwitchFilter = createDstSwitchFilter(switchId);

        return Stream.concat(loadAll(srcSwitchFilter).stream(), loadAll(dstSwitchFilter).stream())
                .collect(Collectors.toList());
    }

    @Override
    public Collection<Flow> findDownFlows() {
        Filter flowStatusDown = new Filter(STATUS_PROPERTY_NAME, ComparisonOperator.EQUALS, FlowStatus.DOWN);
        Filter flowStatusDegraded = new Filter(STATUS_PROPERTY_NAME, ComparisonOperator.EQUALS, FlowStatus.DEGRADED);

        return loadAll(flowStatusDown.or(flowStatusDegraded));
    }

    @Override
    public void createOrUpdate(Flow flow) {
        validateFlow(flow);

        if (flow.getTimeCreate() == null) {
            flow.setTimeCreate(Instant.now());
        } else {
            flow.setTimeModify(Instant.now());
        }

        transactionManager.doInTransaction(() -> {
            Session session = getSession();
            // To avoid Neo4j deadlocks, we perform locking of switch nodes in the case of new flow, path or segments.
            boolean isNewFlow = session.resolveGraphIdFor(flow) == null;
            if (isNewFlow || hasUnmanagedEntity(flow)) {
                // No need to fetch current paths for a new flow.
                Collection<FlowPath> currentPaths = isNewFlow ? emptyList()
                        : flowPathRepository.findByFlowId(flow.getFlowId());

                flowPathRepository.lockInvolvedSwitches(Stream.concat(currentPaths.stream(), flow.getPaths().stream())
                        .toArray(FlowPath[]::new));

                if (!isNewFlow) {
                    deleteOrphanPaths(flow, currentPaths);
                }
            } else {
                deleteOrphanPaths(flow);
            }

            super.createOrUpdate(flow);
        });
    }

    /**
     * Validate the flow relations and flow path to be managed by Neo4j OGM.
     */
    private void validateFlow(Flow flow) {
        // The flow must reference a managed switches to avoid creation of duplicated ones.
        // Check for nulls as the entity may be read not completely.
        if (flow.getSrcSwitch() != null) {
            requireManagedEntity(flow.getSrcSwitch());
        }
        if (flow.getDestSwitch() != null) {
            requireManagedEntity(flow.getDestSwitch());
        }

        for (FlowPath path : flow.getPaths()) {
            flowPathRepository.validateFlowPath(path);
        }
    }

    private boolean hasUnmanagedEntity(Flow flow) {
        Session session = getSession();
        for (FlowPath path : flow.getPaths()) {
            if (session.resolveGraphIdFor(path) == null) {
                return true;
            }
            for (PathSegment segment : path.getSegments()) {
                if (session.resolveGraphIdFor(segment) == null) {
                    return true;
                }
            }
        }

        return false;
    }

    private void deleteOrphanPaths(Flow flow, Collection<FlowPath> currentPaths) {
        Session session = getSession();
        Set<Long> updatedFlowPaths = flow.getPaths().stream()
                .map(session::resolveGraphIdFor)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        FlowPath[] pathsToDelete = currentPaths.stream()
                .filter(path -> !updatedFlowPaths.contains(session.resolveGraphIdFor(path)))
                .toArray(FlowPath[]::new);
        if (pathsToDelete.length > 0) {
            flowPathRepository.lockInvolvedSwitches(pathsToDelete);

            for (FlowPath path : pathsToDelete) {
                flowPathRepository.delete(path);
            }
        }
    }

    private void deleteOrphanPaths(Flow flow) {
        Session session = getSession();
        Set<Long> currentPathIds = findPathEntityIdsByFlowId(flow.getFlowId());
        flow.getPaths().stream()
                .map(session::resolveGraphIdFor)
                .filter(Objects::nonNull)
                .forEach(currentPathIds::remove);

        if (!currentPathIds.isEmpty()) {
            FlowPath[] pathsToDelete = currentPathIds.stream()
                    .map(pathEntityId -> session.load(FlowPath.class, pathEntityId))
                    .filter(Objects::nonNull)
                    .toArray(FlowPath[]::new);

            if (pathsToDelete.length > 0) {
                flowPathRepository.lockInvolvedSwitches(pathsToDelete);

                for (FlowPath path : pathsToDelete) {
                    flowPathRepository.delete(path);
                }
            }
        }
    }

    private Set<Long> findPathEntityIdsByFlowId(String flowId) {
        Map<String, Object> parameters = ImmutableMap.of(
                "flow_id", flowId);

        Set<Long> pathEntityIds = new HashSet<>();
        queryForLongs("MATCH (flow {flow_id: $flow_id})-[:owns]-(fp:flow_path) RETURN id(fp) as id",
                parameters, "id").forEach(pathEntityIds::add);
        return pathEntityIds;
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
        return transactionManager.doInTransaction(() -> findById(flowId, FetchStrategy.NO_RELATIONS)
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
    public void updateStatus(String flowId, FlowStatus flowStatus) {
        Instant timestamp = Instant.now();
        Map<String, Object> parameters = ImmutableMap.of(
                "flow_id", flowId,
                "status", flowStatusConverter.toGraphProperty(flowStatus),
                "time_modify", instantStringConverter.toGraphProperty(timestamp));
        Session session = getSession();
        Optional<Long> updatedEntityId = queryForLong(
                "MATCH (f:flow {flow_id: $flow_id}) "
                        + "SET f.status=$status, f.time_modify=$time_modify "
                        + "RETURN id(f) as id", parameters, "id");
        if (!updatedEntityId.isPresent()) {
            throw new PersistenceException(format("Flow not found to be updated: %s", flowId));
        }

        Object updatedEntity = ((Neo4jSession) session).context().getNodeEntity(updatedEntityId.get());
        if (updatedEntity instanceof Flow) {
            Flow updatedFlow = (Flow) updatedEntity;
            updatedFlow.setStatus(flowStatus);
            updatedFlow.setTimeModify(timestamp);
        } else if (updatedEntity != null) {
            throw new PersistenceException(format("Expected a Flow entity, but found %s.", updatedEntity));
        }
    }

    @Override
    protected Class<Flow> getEntityType() {
        return Flow.class;
    }

    @Override
    protected FetchStrategy getDefaultFetchStrategy() {
        return FetchStrategy.ALL_RELATIONS;
    }

    @Override
    protected int getDepthLoadEntity(FetchStrategy fetchStrategy) {
        switch (fetchStrategy) {
            case ALL_RELATIONS:
                // depth 3 is needed to load switches in PathSegment entity.
                return 3;
            default:
                return super.getDepthLoadEntity(fetchStrategy);
        }
    }

    @Override
    protected int getDepthCreateUpdateEntity() {
        // depth 3 is needed to create/update relations to switches, flow paths,
        // path segments and switches of path segments.
        return 3;
    }
}
