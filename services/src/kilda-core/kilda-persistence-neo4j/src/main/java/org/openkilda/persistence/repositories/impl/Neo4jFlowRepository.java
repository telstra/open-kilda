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
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.converters.FlowStatusConverter;
import org.openkilda.persistence.converters.SwitchIdConverter;
import org.openkilda.persistence.repositories.FlowRepository;

import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.session.Session;

import java.util.Arrays;
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
    static final String PERIODIC_PINGS_PROPERTY_NAME = "periodic_pings";
    static final String STATUS_PROPERTY_NAME = "status";

    private final FlowStatusConverter flowStatusConverter = new FlowStatusConverter();
    private final SwitchIdConverter switchIdConverter = new SwitchIdConverter();

    private final Neo4jFlowPathRepository flowPathRepository;

    public Neo4jFlowRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);

        flowPathRepository = new Neo4jFlowPathRepository(sessionFactory, transactionManager);
    }

    @Override
    public Collection<Flow> findAll() {
        return getSession().loadAll(getEntityType(), 1);
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
        Filter flowIdFilter = new Filter(FLOW_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, flowId);

        Collection<Flow> flows = loadAll(flowIdFilter);
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
    public Collection<Flow> findByEndpointSwitch(SwitchId switchId) {
        Filter srcSwitchFilter = createSrcSwitchFilter(switchId);
        Filter dstSwitchFilter = createDstSwitchFilter(switchId);

        return Stream.concat(loadAll(srcSwitchFilter).stream(), loadAll(dstSwitchFilter).stream())
                .collect(Collectors.toList());
    }

    @Override
    public Collection<Flow> findActiveFlowsWithPortInPath(SwitchId switchId, int port) {
        Map<String, Object> parameters = ImmutableMap.of(
                "switch_id", switchIdConverter.toGraphProperty(switchId),
                "port", port,
                "flow_status", flowStatusConverter.toGraphProperty(FlowStatus.UP));

        Set<String> flowIds = new HashSet<>();
        getSession().query(String.class,
                "MATCH (src:switch)-[:source]-(f:flow)-[:destination]-(dst:switch) "
                        + "WHERE (src.name=$switch_id AND f.src_port=$port "
                        + " OR dst.name=$switch_id AND f.dst_port=$port) "
                        + " AND (f.status=$flow_status OR f.status IS NULL) "
                        + "RETURN f.flow_id "
                        + "UNION ALL "
                        + "MATCH (src:switch)-[:source]-(ps:path_segment)-[:destination]-(dst:switch) "
                        + "WHERE src.name=$switch_id AND ps.src_port=$port "
                        + " OR dst.name=$switch_id AND ps.dst_port=$port "
                        + "MATCH (f:flow)-[:owns]-(:flow_path)-[:owns]-(ps) "
                        + "WHERE f.status=$flow_status OR f.status IS NULL "
                        + "RETURN f.flow_id", parameters).forEach(flowIds::add);

        if (flowIds.isEmpty()) {
            return emptyList();
        }

        Filter flowIdsFilter = new Filter(FLOW_ID_PROPERTY_NAME, ComparisonOperator.IN, flowIds);

        return loadAll(flowIdsFilter);
    }

    @Override
    public Collection<Flow> findDownFlows() {
        Filter flowStatusFilter = new Filter(STATUS_PROPERTY_NAME, ComparisonOperator.EQUALS, FlowStatus.DOWN);

        return loadAll(flowStatusFilter);
    }

    @Override
    public void createOrUpdate(Flow flow) {
        validateFlow(flow);

        transactionManager.doInTransaction(() -> {
            Collection<FlowPath> currentPaths = flowPathRepository.findByFlowId(flow.getFlowId());
            flowPathRepository.lockInvolvedSwitches(Stream.concat(currentPaths.stream(), flow.getPaths().stream())
                    .toArray(FlowPath[]::new));

            updatePaths(currentPaths, Arrays.asList(flow.getForwardPath(), flow.getReversePath()));

            super.createOrUpdate(flow);
        });
    }

    private void updatePaths(Collection<FlowPath> currentPaths, Collection<FlowPath> newPaths) {
        Session session = getSession();

        Set<Long> updatedEntities = newPaths.stream()
                .map(session::resolveGraphIdFor)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        currentPaths.forEach(path -> {
            if (!updatedEntities.contains(session.resolveGraphIdFor(path))) {
                flowPathRepository.delete(path);
            }
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
        getSession().query(String.class,
                "MATCH (src:switch)-[:source]-(ps:path_segment)-[:destination]-(dst:switch) "
                        + "WHERE src.name=$src_switch AND ps.src_port=$src_port  "
                        + "AND dst.name=$dst_switch AND ps.dst_port=$dst_port  "
                        + "MATCH (f:flow)-[:owns]-(:flow_path)-[:owns]-(ps) "
                        + "RETURN f.flow_id", parameters).forEach(flowIds::add);

        if (flowIds.isEmpty()) {
            return emptyList();
        }

        Filter flowIdsFilter = new Filter(FLOW_ID_PROPERTY_NAME, ComparisonOperator.IN, flowIds);

        return loadAll(flowIdsFilter);
    }

    @Override
    public Set<String> findFlowIdsWithSwitchInPath(SwitchId switchId) {
        Map<String, Object> parameters = ImmutableMap.of(
                "switch_id", switchIdConverter.toGraphProperty(switchId));

        Set<String> flowIds = new HashSet<>();
        getSession().query(String.class,
                "MATCH (src:switch)-[:source]-(f:flow)-[:destination]-(dst:switch) "
                        + "WHERE src.name=$switch_id OR dst.name=$switch_id "
                        + "RETURN f.flow_id "
                        + "UNION ALL "
                        + "MATCH (src:switch)-[:source]-(ps:path_segment)-[:destination]-(dst:switch) "
                        + "WHERE src.name=$switch_id OR dst.name=$switch_id "
                        + "MATCH (f:flow)-[:owns]-(:flow_path)-[:owns]-(ps) "
                        + "RETURN f.flow_id", parameters).forEach(flowIds::add);
        return flowIds;
    }

    @Override
    protected Class<Flow> getEntityType() {
        return Flow.class;
    }

    @Override
    protected int getDepthLoadEntity() {
        // depth 3 is needed to load switches in PathSegment entity.
        return 3;
    }

    @Override
    protected int getDepthCreateUpdateEntity() {
        // depth 3 is needed to create/update relations to switches, flow paths,
        // path segments and switches of path segments.
        return 3;
    }

    /**
     * Validate the flow relations and flow path to be managed by Neo4j OGM and reference the same flow.
     */
    private void validateFlow(Flow flow) {
        requireManagedEntity(flow.getSrcSwitch());
        requireManagedEntity(flow.getDestSwitch());

        // it must reference the same object.
        FlowPath forwardPath = flow.getForwardPath();
        if (forwardPath != null) {
            if (forwardPath.getFlow() != flow) {
                throw new IllegalArgumentException(format("Flow path %s references different flow, but expect %s",
                        forwardPath, flow));
            }
            flowPathRepository.validateFlowPath(forwardPath);
        }

        FlowPath reversePath = flow.getReversePath();
        if (reversePath != null) {
            if (reversePath.getFlow() != flow) {
                throw new IllegalArgumentException(format("Flow path %s references different flow, but expect %s",
                        reversePath, flow));
            }
            flowPathRepository.validateFlowPath(reversePath);
        }
    }
}
