/* Copyright 2022 Telstra Open Source
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

import org.openkilda.model.FlowMirror;
import org.openkilda.model.FlowMirror.FlowMirrorData;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.ferma.FermaPersistentImplementation;
import org.openkilda.persistence.ferma.frames.FlowMirrorFrame;
import org.openkilda.persistence.ferma.frames.KildaBaseVertexFrame;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;
import org.openkilda.persistence.repositories.FlowMirrorPathRepository;
import org.openkilda.persistence.repositories.FlowMirrorRepository;
import org.openkilda.persistence.tx.TransactionManager;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Ferma implementation of {@link FlowMirrorRepository}.
 */
@Slf4j
public class FermaFlowMirrorRepository extends FermaGenericRepository<FlowMirror, FlowMirrorData, FlowMirrorFrame>
        implements FlowMirrorRepository {
    protected final FlowMirrorPathRepository flowMirrorPathRepository;

    public FermaFlowMirrorRepository(
            FermaPersistentImplementation implementation, FlowMirrorPathRepository flowMirrorPathRepository) {
        super(implementation);
        this.flowMirrorPathRepository = flowMirrorPathRepository;
    }

    @Override
    public Collection<FlowMirror> findAll() {
        return framedGraph().traverse(g -> g.V()
                .hasLabel(FlowMirrorFrame.FRAME_LABEL))
                .toListExplicit(FlowMirrorFrame.class).stream()
                .map(FlowMirror::new)
                .collect(Collectors.toList());
    }

    @Override
    public boolean exists(String flowMirrorId) {
        try (GraphTraversal<?, ?> traversal = framedGraph().traverse(g -> g.V()
                        .hasLabel(FlowMirrorFrame.FRAME_LABEL)
                        .has(FlowMirrorFrame.FLOW_MIRROR_ID_PROPERTY, flowMirrorId))
                .getRawTraversal()) {
            return traversal.hasNext();
        } catch (Exception e) {
            throw new PersistenceException("Failed to traverse", e);
        }
    }

    @Override
    public Optional<FlowMirror> findById(String flowMirrorId) {
        List<? extends FlowMirrorFrame> flowMirrorFrames = framedGraph().traverse(g -> g.V()
                        .hasLabel(FlowMirrorFrame.FRAME_LABEL)
                        .has(FlowMirrorFrame.FLOW_MIRROR_ID_PROPERTY, flowMirrorId))
                .toListExplicit(FlowMirrorFrame.class);
        return flowMirrorFrames.isEmpty() ? Optional.empty() : Optional.of(flowMirrorFrames.get(0))
                .map(FlowMirror::new);
    }

    @Override
    public Optional<FlowMirror> findByEgressEndpoint(SwitchId switchId, int port, int outerVlan, int innerVlan) {
        List<? extends FlowMirrorFrame> flowMirrorFrames = framedGraph().traverse(g -> g.V()
                        .hasLabel(FlowMirrorFrame.FRAME_LABEL)
                        .has(FlowMirrorFrame.EGRESS_SWITCH_ID_PROPERTY,
                                SwitchIdConverter.INSTANCE.toGraphProperty(switchId))
                        .has(FlowMirrorFrame.EGRESS_PORT_PROPERTY, port)
                        .has(FlowMirrorFrame.EGRESS_OUTER_VLAN_PROPERTY, outerVlan)
                        .has(FlowMirrorFrame.EGRESS_INNER_VLAN_PROPERTY, innerVlan))
                .toListExplicit(FlowMirrorFrame.class);
        return flowMirrorFrames.isEmpty() ? Optional.empty()
                : Optional.of(flowMirrorFrames.get(0)).map(FlowMirror::new);
    }

    @Override
    public Collection<FlowMirror> findByEgressSwitchIdAndPort(SwitchId switchId, int port) {
        return framedGraph().traverse(g -> g.V()
                        .hasLabel(FlowMirrorFrame.FRAME_LABEL)
                        .has(FlowMirrorFrame.EGRESS_SWITCH_ID_PROPERTY,
                                SwitchIdConverter.INSTANCE.toGraphProperty(switchId))
                        .has(FlowMirrorFrame.EGRESS_PORT_PROPERTY, port))
                .toListExplicit(FlowMirrorFrame.class).stream()
                .map(FlowMirror::new)
                .collect(Collectors.toList());
    }

    @Override
    public void updateStatus(@NonNull String flowMirrorId, @NonNull FlowPathStatus status) {
        getTransactionManager().doInTransaction(() ->
                framedGraph().traverse(g -> g.V()
                                .hasLabel(FlowMirrorFrame.FRAME_LABEL)
                                .has(FlowMirrorFrame.FLOW_MIRROR_ID_PROPERTY, flowMirrorId))
                        .toListExplicit(FlowMirrorFrame.class)
                        .forEach(flowMirrorFrame -> flowMirrorFrame.setStatus(status)));
    }

    @Override
    public Optional<FlowMirror> remove(String flowMirrorId) {
        TransactionManager transactionManager = getTransactionManager();
        if (transactionManager.isTxOpen()) {
            // This implementation removes dependant entities (paths, segments) in a separate transactions,
            // so the flow entity may require to be reloaded in a case of failed transaction.
            throw new IllegalStateException("This implementation of remove requires no outside transaction");
        }

        return transactionManager.doInTransaction(() ->
                findById(flowMirrorId)
                        .map(flowMirror -> {
                            remove(flowMirror);
                            return flowMirror;
                        }));
    }

    @Override
    protected FlowMirrorFrame doAdd(FlowMirrorData data) {
        FlowMirrorFrame frame = KildaBaseVertexFrame.addNewFramedVertex(framedGraph(),
                FlowMirrorFrame.FRAME_LABEL, FlowMirrorFrame.class);
        FlowMirror.FlowMirrorCloner.INSTANCE.copyWithoutPaths(data, frame);
        return frame;
    }

    @Override
    protected void doRemove(FlowMirrorFrame frame) {
        frame.remove();
    }

    @Override
    protected FlowMirrorData doDetach(FlowMirror entity, FlowMirrorFrame frame) {
        return FlowMirror.FlowMirrorCloner.INSTANCE.deepCopy(frame, entity);
    }
}
