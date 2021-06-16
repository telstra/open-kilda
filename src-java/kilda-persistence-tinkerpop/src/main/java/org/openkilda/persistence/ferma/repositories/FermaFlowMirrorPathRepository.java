/* Copyright 2020 Telstra Open Source
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

import org.openkilda.model.FlowMirrorPath;
import org.openkilda.model.FlowMirrorPath.FlowMirrorPathData;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.ferma.FramedGraphFactory;
import org.openkilda.persistence.ferma.frames.FlowMirrorPathFrame;
import org.openkilda.persistence.ferma.frames.KildaBaseVertexFrame;
import org.openkilda.persistence.ferma.frames.PathSegmentFrame;
import org.openkilda.persistence.ferma.frames.converters.PathIdConverter;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;
import org.openkilda.persistence.repositories.FlowMirrorPathRepository;
import org.openkilda.persistence.tx.TransactionManager;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Ferma (Tinkerpop) implementation of {@link FlowMirrorPathRepository}.
 */
public class FermaFlowMirrorPathRepository
        extends FermaGenericRepository<FlowMirrorPath, FlowMirrorPathData, FlowMirrorPathFrame>
        implements FlowMirrorPathRepository {
    public FermaFlowMirrorPathRepository(FramedGraphFactory<?> graphFactory, TransactionManager transactionManager) {
        super(graphFactory, transactionManager);
    }

    @Override
    public boolean exists(PathId mirrorPathId) {
        try (GraphTraversal<?, ?> traversal = framedGraph().traverse(g -> g.V()
                .hasLabel(FlowMirrorPathFrame.FRAME_LABEL)
                .has(FlowMirrorPathFrame.PATH_ID_PROPERTY, PathIdConverter.INSTANCE.toGraphProperty(mirrorPathId)))
                .getRawTraversal()) {
            return traversal.hasNext();
        } catch (Exception e) {
            throw new PersistenceException("Failed to traverse", e);
        }
    }

    @Override
    public Collection<FlowMirrorPath> findAll() {
        return framedGraph().traverse(g -> g.V()
                .hasLabel(FlowMirrorPathFrame.FRAME_LABEL))
                .toListExplicit(FlowMirrorPathFrame.class).stream()
                .map(FlowMirrorPath::new)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<FlowMirrorPath> findById(PathId pathId) {
        List<? extends FlowMirrorPathFrame> flowPathFrames = framedGraph().traverse(g -> g.V()
                .hasLabel(FlowMirrorPathFrame.FRAME_LABEL)
                .has(FlowMirrorPathFrame.PATH_ID_PROPERTY, PathIdConverter.INSTANCE.toGraphProperty(pathId)))
                .toListExplicit(FlowMirrorPathFrame.class);
        return flowPathFrames.isEmpty() ? Optional.empty() : Optional.of(flowPathFrames.get(0))
                .map(FlowMirrorPath::new);
    }

    @Override
    public Optional<FlowMirrorPath> findByEgressEndpoint(SwitchId switchId, int port, int outerVlan, int innerVlan) {
        List<? extends FlowMirrorPathFrame> flowMirrorPathFrames = framedGraph().traverse(g -> g.V()
                .hasLabel(FlowMirrorPathFrame.FRAME_LABEL)
                .has(FlowMirrorPathFrame.EGRESS_SWITCH_ID_PROPERTY,
                        SwitchIdConverter.INSTANCE.toGraphProperty(switchId))
                .has(FlowMirrorPathFrame.EGRESS_PORT_PROPERTY, port)
                .has(FlowMirrorPathFrame.EGRESS_OUTER_VLAN_PROPERTY, outerVlan)
                .has(FlowMirrorPathFrame.EGRESS_INNER_VLAN_PROPERTY, innerVlan))
                .toListExplicit(FlowMirrorPathFrame.class);
        return flowMirrorPathFrames.isEmpty() ? Optional.empty()
                : Optional.of(flowMirrorPathFrames.get(0)).map(FlowMirrorPath::new);
    }

    @Override
    public Collection<FlowMirrorPath> findByEgressSwitchIdAndPort(SwitchId switchId, int port) {
        return framedGraph().traverse(g -> g.V()
                .hasLabel(FlowMirrorPathFrame.FRAME_LABEL)
                .has(FlowMirrorPathFrame.EGRESS_SWITCH_ID_PROPERTY,
                        SwitchIdConverter.INSTANCE.toGraphProperty(switchId))
                .has(FlowMirrorPathFrame.EGRESS_PORT_PROPERTY, port))
                .toListExplicit(FlowMirrorPathFrame.class).stream()
                .map(FlowMirrorPath::new)
                .collect(Collectors.toList());
    }

    @Override
    public void updateStatus(PathId pathId, FlowPathStatus pathStatus) {
        transactionManager.doInTransaction(() ->
                framedGraph().traverse(g -> g.V()
                        .hasLabel(FlowMirrorPathFrame.FRAME_LABEL)
                        .has(FlowMirrorPathFrame.PATH_ID_PROPERTY, PathIdConverter.INSTANCE.toGraphProperty(pathId)))
                        .toListExplicit(FlowMirrorPathFrame.class)
                        .forEach(pathFrame -> pathFrame.setStatus(pathStatus)));
    }

    @Override
    public Optional<FlowMirrorPath> remove(PathId pathId) {
        if (transactionManager.isTxOpen()) {
            // This implementation removes dependant entities (segments) in a separate transactions,
            // so the path entity may require to be reloaded in a case of failed transaction.
            throw new IllegalStateException("This implementation of remove requires no outside transaction");
        }

        return transactionManager.doInTransaction(() ->
                findById(pathId)
                        .map(path -> {
                            remove(path);
                            return path;
                        }));
    }

    @Override
    protected FlowMirrorPathFrame doAdd(FlowMirrorPathData data) {
        FlowMirrorPathFrame frame = KildaBaseVertexFrame.addNewFramedVertex(framedGraph(),
                FlowMirrorPathFrame.FRAME_LABEL, FlowMirrorPathFrame.class);
        FlowMirrorPath.FlowMirrorPathCloner.INSTANCE.copy(data, frame);
        return frame;
    }

    @Override
    protected void doRemove(FlowMirrorPathFrame frame) {
        frame.getSegments().forEach(pathSegment -> {
            if (pathSegment.getData() instanceof PathSegmentFrame) {
                // No need to call the PathSegment repository, as segments already detached along with the path.
                ((PathSegmentFrame) pathSegment.getData()).remove();
            }
        });
        frame.remove();
    }

    @Override
    protected FlowMirrorPathData doDetach(FlowMirrorPath entity, FlowMirrorPathFrame frame) {
        return FlowMirrorPath.FlowMirrorPathCloner.INSTANCE.deepCopy(frame, entity.getFlowMirrorPoints());
    }
}
