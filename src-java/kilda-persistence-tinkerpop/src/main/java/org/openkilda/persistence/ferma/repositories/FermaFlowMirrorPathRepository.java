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

import org.openkilda.model.FlowMirror;
import org.openkilda.model.FlowMirrorPath;
import org.openkilda.model.FlowMirrorPath.FlowMirrorPathData;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.ferma.FermaPersistentImplementation;
import org.openkilda.persistence.ferma.frames.FlowMirrorPathFrame;
import org.openkilda.persistence.ferma.frames.KildaBaseVertexFrame;
import org.openkilda.persistence.ferma.frames.PathSegmentFrame;
import org.openkilda.persistence.ferma.frames.converters.PathIdConverter;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;
import org.openkilda.persistence.repositories.FlowMirrorPathRepository;
import org.openkilda.persistence.tx.TransactionManager;

import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ferma (Tinkerpop) implementation of {@link FlowMirrorPathRepository}.
 */
public class FermaFlowMirrorPathRepository
        extends FermaGenericRepository<FlowMirrorPath, FlowMirrorPathData, FlowMirrorPathFrame>
        implements FlowMirrorPathRepository {
    public FermaFlowMirrorPathRepository(FermaPersistentImplementation implementation) {
        super(implementation);
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
    public Map<PathId, FlowMirrorPath> findByIds(Set<PathId> pathIds) {
        Set<String> graphPathIds = getPathIds(pathIds);
        List<? extends FlowMirrorPathFrame> pathFrames = framedGraph().traverse(g -> g.V()
                        .hasLabel(FlowMirrorPathFrame.FRAME_LABEL)
                        .has(FlowMirrorPathFrame.PATH_ID_PROPERTY, P.within(graphPathIds)))
                .toListExplicit(FlowMirrorPathFrame.class);
        return pathFrames.stream()
                .map(FlowMirrorPath::new)
                .collect(Collectors.toMap(FlowMirrorPath::getMirrorPathId, Function.identity()));
    }

    @Override
    public Map<PathId, FlowMirror> findFlowsMirrorsByPathIds(Set<PathId> pathIds) {
        Set<String> graphPathIds = getPathIds(pathIds);
        List<? extends FlowMirrorPathFrame> flowPathFrames = framedGraph().traverse(g -> g.V()
                        .hasLabel(FlowMirrorPathFrame.FRAME_LABEL)
                        .has(FlowMirrorPathFrame.PATH_ID_PROPERTY, P.within(graphPathIds)))
                .toListExplicit(FlowMirrorPathFrame.class);
        return flowPathFrames.stream()
                .map(FlowMirrorPath::new)
                .collect(Collectors.toMap(FlowMirrorPath::getMirrorPathId, FlowMirrorPath::getFlowMirror));
    }

    @Override
    public Map<PathId, FlowMirrorPoints> findFlowsMirrorPointsByPathIds(Set<PathId> pathIds) {
        Set<String> graphPathIds = getPathIds(pathIds);
        List<? extends FlowMirrorPathFrame> flowPathFrames = framedGraph().traverse(g -> g.V()
                        .hasLabel(FlowMirrorPathFrame.FRAME_LABEL)
                        .has(FlowMirrorPathFrame.PATH_ID_PROPERTY, P.within(graphPathIds)))
                .toListExplicit(FlowMirrorPathFrame.class);
        return flowPathFrames.stream()
                .map(FlowMirrorPath::new)
                .collect(Collectors.toMap(
                        FlowMirrorPath::getMirrorPathId, path -> path.getFlowMirror().getFlowMirrorPoints()));
    }

    @Override
    public void updateStatus(PathId pathId, FlowPathStatus pathStatus) {
        getTransactionManager().doInTransaction(() ->
                framedGraph().traverse(g -> g.V()
                        .hasLabel(FlowMirrorPathFrame.FRAME_LABEL)
                        .has(FlowMirrorPathFrame.PATH_ID_PROPERTY, PathIdConverter.INSTANCE.toGraphProperty(pathId)))
                        .toListExplicit(FlowMirrorPathFrame.class)
                        .forEach(pathFrame -> pathFrame.setStatus(pathStatus)));
    }

    @Override
    public Collection<FlowMirrorPath> findByEndpointSwitch(SwitchId switchId) {
        Map<PathId, FlowMirrorPath> result = new HashMap<>();
        String swId = SwitchIdConverter.INSTANCE.toGraphProperty(switchId);
        framedGraph().traverse(g -> g.V()
                        .hasLabel(FlowMirrorPathFrame.FRAME_LABEL)
                        .has(FlowMirrorPathFrame.MIRROR_SWITCH_ID_PROPERTY, swId))
                .frameExplicit(FlowMirrorPathFrame.class)
                .forEachRemaining(frame -> result.put(frame.getMirrorPathId(), new FlowMirrorPath(frame)));
        framedGraph().traverse(g -> g.V()
                        .hasLabel(FlowMirrorPathFrame.FRAME_LABEL)
                        .has(FlowMirrorPathFrame.EGRESS_SWITCH_ID_PROPERTY, swId))
                .frameExplicit(FlowMirrorPathFrame.class)
                .forEachRemaining(frame -> result.put(frame.getMirrorPathId(), new FlowMirrorPath(frame)));

        return result.values();
    }

    @Override
    public Collection<FlowMirrorPath> findBySegmentSwitch(SwitchId switchId) {
        Map<PathId, FlowMirrorPath> result = new HashMap<>();
        String swId = SwitchIdConverter.INSTANCE.toGraphProperty(switchId);
        framedGraph().traverse(g -> g.V()
                        .hasLabel(PathSegmentFrame.FRAME_LABEL)
                        .has(PathSegmentFrame.SRC_SWITCH_ID_PROPERTY, swId)
                        .in(FlowMirrorPathFrame.OWNS_SEGMENTS_EDGE)
                        .hasLabel(FlowMirrorPathFrame.FRAME_LABEL))
                .frameExplicit(FlowMirrorPathFrame.class)
                .forEachRemaining(frame -> result.put(frame.getMirrorPathId(), new FlowMirrorPath(frame)));
        framedGraph().traverse(g -> g.V()
                        .hasLabel(PathSegmentFrame.FRAME_LABEL)
                        .has(PathSegmentFrame.DST_SWITCH_ID_PROPERTY, swId)
                        .in(FlowMirrorPathFrame.OWNS_SEGMENTS_EDGE)
                        .hasLabel(FlowMirrorPathFrame.FRAME_LABEL))
                .frameExplicit(FlowMirrorPathFrame.class)
                .forEachRemaining(frame -> result.put(frame.getMirrorPathId(), new FlowMirrorPath(frame)));
        return result.values();
    }

    @Override
    public Optional<FlowMirrorPath> remove(PathId pathId) {
        TransactionManager transactionManager = getTransactionManager();
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
        return FlowMirrorPath.FlowMirrorPathCloner.INSTANCE.deepCopy(frame, entity.getFlowMirror());
    }

    private static Set<String> getPathIds(Set<PathId> pathIds) {
        return pathIds.stream()
                .map(PathIdConverter.INSTANCE::toGraphProperty)
                .collect(Collectors.toSet());
    }
}
