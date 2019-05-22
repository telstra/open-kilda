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
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowRepository;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
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
    public Collection<String> findFlowsIdByGroupId(String flowGroupId) {
        Map<String, Object> flowParameters = ImmutableMap.of("flow_group_id", flowGroupId);

        return Lists.newArrayList(getSession().query(String.class,
                "MATCH (f:flow {group_id: $flow_group_id}) RETURN f.flow_id", flowParameters));
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
    public Collection<Flow> findDownFlows() {
        Filter flowStatusFilter = new Filter(STATUS_PROPERTY_NAME, ComparisonOperator.EQUALS, FlowStatus.DOWN);

        return loadAll(flowStatusFilter);
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
            Collection<FlowPath> currentPaths = flowPathRepository.findByFlowId(flow.getFlowId());
            flowPathRepository.lockInvolvedSwitches(Stream.concat(currentPaths.stream(), flow.getPaths().stream())
                    .toArray(FlowPath[]::new));

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

        for (FlowPath path : flow.getPaths()) {
            if (path.getFlow() != flow) {
                // it must reference the same object.
                throw new IllegalArgumentException(format("Flow path %s references different flow %s, but expect %s",
                        path, path.getFlow(), flow));
            }
            flowPathRepository.validateFlowPath(path);
        }
    }
}
