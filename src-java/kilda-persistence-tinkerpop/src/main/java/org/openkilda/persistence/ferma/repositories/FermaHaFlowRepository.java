/* Copyright 2023 Telstra Open Source
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

package org.openkilda.persistence.ferma.repositories;

import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlow.HaFlowData;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.ferma.FermaPersistentImplementation;
import org.openkilda.persistence.ferma.frames.FlowFrame;
import org.openkilda.persistence.ferma.frames.HaFlowFrame;
import org.openkilda.persistence.ferma.frames.HaFlowPathFrame;
import org.openkilda.persistence.ferma.frames.HaSubFlowFrame;
import org.openkilda.persistence.ferma.frames.KildaBaseVertexFrame;
import org.openkilda.persistence.ferma.frames.converters.FlowStatusConverter;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;
import org.openkilda.persistence.repositories.HaFlowPathRepository;
import org.openkilda.persistence.repositories.HaFlowRepository;
import org.openkilda.persistence.repositories.HaSubFlowRepository;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.persistence.tx.TransactionRequired;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Ferma implementation of {@link HaFlowRepository}.
 */
@Slf4j
public class FermaHaFlowRepository extends FermaGenericRepository<HaFlow, HaFlowData, HaFlowFrame>
        implements HaFlowRepository {
    protected final HaFlowPathRepository haFlowPathRepository;
    protected final HaSubFlowRepository haSubFlowRepository;

    public FermaHaFlowRepository(
            FermaPersistentImplementation implementation, HaFlowPathRepository haFlowPathRepository,
            HaSubFlowRepository haSubFlowRepository) {
        super(implementation);
        this.haFlowPathRepository = haFlowPathRepository;
        this.haSubFlowRepository = haSubFlowRepository;
    }

    @Override
    public Collection<HaFlow> findAll() {
        return framedGraph().traverse(g -> g.V()
                .hasLabel(HaFlowFrame.FRAME_LABEL))
                .toListExplicit(HaFlowFrame.class).stream()
                .map(HaFlow::new)
                .collect(Collectors.toList());
    }

    @Override
    public boolean exists(String haFlowId) {
        try (GraphTraversal<?, ?> traversal = framedGraph().traverse(g -> g.V()
                .hasLabel(HaFlowFrame.FRAME_LABEL)
                .has(HaFlowFrame.HA_FLOW_ID_PROPERTY, haFlowId))
                .getRawTraversal()) {
            return traversal.hasNext();
        } catch (Exception e) {
            throw new PersistenceException("Failed to traverse", e);
        }
    }

    @Override
    public Collection<HaFlow> findWithPeriodicPingsEnabled() {
        return framedGraph().traverse(g -> g.V()
                        .hasLabel(HaFlowFrame.FRAME_LABEL)
                        .has(HaFlowFrame.PERIODIC_PINGS_PROPERTY, true))
                .toListExplicit(HaFlowFrame.class).stream()
                .map(HaFlow::new)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<HaFlow> findById(String haFlowId) {
        return HaFlowFrame.load(framedGraph(), haFlowId).map(HaFlow::new);
    }

    @Override
    public Collection<HaFlow> findByEndpoint(SwitchId switchId, int port, int vlan, int innerVLan) {
        Map<String, HaFlow> result = new HashMap<>();
        framedGraph().traverse(g -> g.V()
                        .hasLabel(HaFlowFrame.FRAME_LABEL)
                        .has(HaFlowFrame.SHARED_ENDPOINT_SWITCH_ID_PROPERTY,
                                SwitchIdConverter.INSTANCE.toGraphProperty(switchId))
                        .has(HaFlowFrame.SHARED_ENDPOINT_PORT_PROPERTY, port)
                        .has(HaFlowFrame.SHARED_ENDPOINT_VLAN_PROPERTY, vlan)
                        .has(HaFlowFrame.SHARED_ENDPOINT_INNER_VLAN_PROPERTY, innerVLan))
                .frameExplicit(HaFlowFrame.class)
                .forEachRemaining(frame -> result.put(frame.getHaFlowId(), new HaFlow(frame)));
        framedGraph().traverse(g -> g.V()
                        .hasLabel(HaSubFlowFrame.FRAME_LABEL)
                        .has(HaSubFlowFrame.ENDPOINT_SWITCH_ID_PROPERTY,
                                SwitchIdConverter.INSTANCE.toGraphProperty(switchId))
                        .has(HaSubFlowFrame.ENDPOINT_PORT_PROPERTY, port)
                        .has(HaSubFlowFrame.ENDPOINT_VLAN_PROPERTY, vlan)
                        .has(HaSubFlowFrame.ENDPOINT_INNER_VLAN_PROPERTY, innerVLan)
                        .in(HaFlowFrame.OWNS_SUB_FLOW_EDGE))
                .frameExplicit(HaFlowFrame.class)
                .forEachRemaining(frame -> result.put(frame.getHaFlowId(), new HaFlow(frame)));
        return result.values();
    }

    @Override
    public Collection<HaFlow> findInactive() {
        String downStatus = FlowStatusConverter.INSTANCE.toGraphProperty(FlowStatus.DOWN);
        String degradedStatus = FlowStatusConverter.INSTANCE.toGraphProperty(FlowStatus.DEGRADED);

        return framedGraph().traverse(g -> g.V()
                        .hasLabel(HaFlowFrame.FRAME_LABEL)
                        .has(HaFlowFrame.STATUS_PROPERTY, P.within(downStatus, degradedStatus)))
                .toListExplicit(HaFlowFrame.class).stream()
                .map(HaFlow::new)
                .collect(Collectors.toList());
    }

    @Override
    public Collection<String> findHaFlowIdsByDiverseGroupId(String diverseGroupId) {
        return findFlowsIdByGroupId(HaFlowFrame.DIVERSE_GROUP_ID_PROPERTY, diverseGroupId);
    }

    @Override
    public Collection<String> findHaFlowIdsByAffinityGroupId(String affinityGroupId) {
        return findFlowsIdByGroupId(HaFlowFrame.AFFINITY_GROUP_ID_PROPERTY, affinityGroupId);
    }

    private Collection<String> findFlowsIdByGroupId(String groupIdProperty, String groupId) {
        if (groupId == null) {
            return new ArrayList<>();
        }
        return framedGraph().traverse(g -> g.V()
                        .hasLabel(HaFlowFrame.FRAME_LABEL)
                        .has(groupIdProperty, groupId)
                        .values(HaFlowFrame.HA_FLOW_ID_PROPERTY))
                .getRawTraversal().toStream()
                .map(String.class::cast)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<String> getDiverseHaFlowGroupId(String haFlowId) {
        return getTransactionManager().doInTransaction(() -> findById(haFlowId)
                .map(HaFlow::getDiverseGroupId));
    }

    @Override
    public Optional<String> getOrCreateDiverseHaFlowGroupId(String haFlowId) {
        return getTransactionManager().doInTransaction(() -> findById(haFlowId)
                .map(haFlow -> {
                    String groupId = haFlow.getDiverseGroupId();
                    if (groupId == null) {
                        groupId = UUID.randomUUID().toString();
                        haFlow.setDiverseGroupId(groupId);
                    }
                    return groupId;
                }));
    }

    @Override
    public void updateStatus(String haFlowId, FlowStatus flowStatus) {
        getTransactionManager().doInTransaction(() ->
                framedGraph().traverse(g -> g.V()
                                .hasLabel(HaFlowFrame.FRAME_LABEL)
                                .has(HaFlowFrame.HA_FLOW_ID_PROPERTY, haFlowId))
                        .toListExplicit(FlowFrame.class)
                        .forEach(haFlowFrame -> {
                            haFlowFrame.setStatus(flowStatus);
                        }));
    }

    @Override
    public void updateStatus(String haFlowId, FlowStatus flowStatus, String flowStatusInfo) {
        getTransactionManager().doInTransaction(() ->
                framedGraph().traverse(g -> g.V()
                                .hasLabel(HaFlowFrame.FRAME_LABEL)
                                .has(HaFlowFrame.HA_FLOW_ID_PROPERTY, haFlowId))
                        .toListExplicit(FlowFrame.class)
                        .forEach(haFlowFrame -> {
                            haFlowFrame.setStatus(flowStatus);
                            haFlowFrame.setStatusInfo(flowStatusInfo);
                        }));
    }

    /**
     * HA-flow in "IN_PROGRESS" status can be switched to other status only inside flow CRUD handlers topology.
     * All other components must use this method, which guarantee safety such HA-flows status.
     */
    @Override
    @TransactionRequired
    public void updateStatusSafe(HaFlow haFlow, FlowStatus status, String statusInfo) {
        if (haFlow.getStatus() != FlowStatus.IN_PROGRESS) {
            haFlow.setStatus(status);
            haFlow.setStatusInfo(statusInfo);
        }
    }

    @Override
    public void updateAffinityFlowGroupId(@NonNull String haFlowId, String affinityGroupId) {
        getTransactionManager().doInTransaction(() ->
                framedGraph().traverse(g -> g.V()
                                .hasLabel(HaFlowFrame.FRAME_LABEL)
                                .has(HaFlowFrame.HA_FLOW_ID_PROPERTY, haFlowId))
                        .toListExplicit(HaFlowFrame.class)
                        .forEach(haFlowFrame -> {
                            haFlowFrame.setAffinityGroupId(affinityGroupId);
                        }));
    }

    @Override
    public Optional<HaFlow> remove(String haFlowId) {
        TransactionManager transactionManager = getTransactionManager();
        if (transactionManager.isTxOpen()) {
            // This implementation removes dependant entities (paths, segments, haSubFlows) in a separate transaction,
            // so the ha-flow entity may require to be reloaded in a case of failed transaction.
            throw new IllegalStateException("This implementation of remove requires no outside transaction");
        }

        return transactionManager.doInTransaction(() ->
                findById(haFlowId)
                        .map(haFlow -> {
                            remove(haFlow);
                            return haFlow;
                        }));
    }

    @Override
    protected HaFlowFrame doAdd(HaFlowData data) {
        HaFlowFrame frame = KildaBaseVertexFrame.addNewFramedVertex(
                framedGraph(), HaFlowFrame.FRAME_LABEL, HaFlowFrame.class);
        HaFlow.HaFlowCloner.INSTANCE.copyWithoutSubFlowsAndPaths(data, frame);
        frame.setHaSubFlows(data.getHaSubFlows().stream()
                .peek(subFlow -> {
                    if (!(subFlow.getData() instanceof HaSubFlowFrame)) {
                        haSubFlowRepository.add(subFlow);
                    }
                }).collect(Collectors.toSet()));
        frame.addPaths(data.getPaths().stream()
                .peek(path -> {
                    if (!(path.getData() instanceof HaFlowPathFrame)) {
                        haFlowPathRepository.add(path);
                    }
                })
                .toArray(HaFlowPath[]::new));
        return frame;
    }

    @Override
    protected void doRemove(HaFlowFrame frame) {
        frame.getPaths().forEach(path -> {
            if (path.getData() instanceof HaFlowPathFrame) {
                haFlowPathRepository.remove(path);
            }
        });
        frame.getHaSubFlows().forEach(subFlow -> {
            if (subFlow.getData() instanceof HaSubFlowFrame) {
                haSubFlowRepository.remove(subFlow);
            }
        });
        frame.remove();
    }

    @Override
    protected HaFlowData doDetach(HaFlow entity, HaFlowFrame frame) {
        return HaFlow.HaFlowCloner.INSTANCE.deepCopy(frame, entity);
    }
}
