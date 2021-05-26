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
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowMirrorPoints.FlowMirrorPointsData;
import org.openkilda.model.GroupId;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.ferma.FramedGraphFactory;
import org.openkilda.persistence.ferma.frames.FlowMirrorPointsFrame;
import org.openkilda.persistence.ferma.frames.FlowPathFrame;
import org.openkilda.persistence.ferma.frames.KildaBaseVertexFrame;
import org.openkilda.persistence.ferma.frames.converters.GroupIdConverter;
import org.openkilda.persistence.ferma.frames.converters.PathIdConverter;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;
import org.openkilda.persistence.repositories.FlowMirrorPathRepository;
import org.openkilda.persistence.repositories.FlowMirrorPointsRepository;
import org.openkilda.persistence.tx.TransactionManager;

import lombok.extern.slf4j.Slf4j;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Ferma implementation of {@link FlowMirrorPointsRepository}.
 */
@Slf4j
public class FermaFlowMirrorPointsRepository
        extends FermaGenericRepository<FlowMirrorPoints, FlowMirrorPointsData, FlowMirrorPointsFrame>
        implements FlowMirrorPointsRepository {
    protected final FlowMirrorPathRepository flowMirrorPathRepository;

    public FermaFlowMirrorPointsRepository(FramedGraphFactory<?> graphFactory,
                                           FlowMirrorPathRepository flowMirrorPathRepository,
                                           TransactionManager transactionManager) {
        super(graphFactory, transactionManager);
        this.flowMirrorPathRepository = flowMirrorPathRepository;
    }

    @Override
    public Collection<FlowMirrorPoints> findAll() {
        return framedGraph().traverse(g -> g.V()
                .hasLabel(FlowMirrorPointsFrame.FRAME_LABEL))
                .toListExplicit(FlowMirrorPointsFrame.class).stream()
                .map(FlowMirrorPoints::new)
                .collect(Collectors.toList());
    }

    @Override
    public boolean exists(PathId pathId, SwitchId switchId) {
        try (GraphTraversal<?, ?> traversal = framedGraph().traverse(g -> g.V()
                .hasLabel(FlowMirrorPointsFrame.FRAME_LABEL)
                .has(FlowMirrorPointsFrame.FLOW_PATH_ID_PROPERTY,
                        PathIdConverter.INSTANCE.toGraphProperty(pathId))
                .has(FlowMirrorPointsFrame.MIRROR_SWITCH_ID_PROPERTY,
                        SwitchIdConverter.INSTANCE.toGraphProperty(switchId)))
                .getRawTraversal()) {
            return traversal.hasNext();
        } catch (Exception e) {
            throw new PersistenceException("Failed to traverse", e);
        }
    }

    @Override
    public Optional<FlowMirrorPoints> findByGroupId(GroupId groupId) {
        List<? extends FlowMirrorPointsFrame> flowMirrorPointsFrames = framedGraph().traverse(g -> g.V()
                .hasLabel(FlowMirrorPointsFrame.FRAME_LABEL)
                .has(FlowMirrorPointsFrame.MIRROR_GROUP_ID_PROPERTY,
                        GroupIdConverter.INSTANCE.toGraphProperty(groupId)))
                .toListExplicit(FlowMirrorPointsFrame.class);

        return flowMirrorPointsFrames.isEmpty() ? Optional.empty()
                : Optional.of(flowMirrorPointsFrames.get(0)).map(FlowMirrorPoints::new);
    }

    @Override
    public Optional<FlowMirrorPoints> findByPathIdAndSwitchId(PathId pathId, SwitchId switchId) {
        List<? extends FlowMirrorPointsFrame> flowMirrorPointsFrames = framedGraph().traverse(g -> g.V()
                .hasLabel(FlowMirrorPointsFrame.FRAME_LABEL)
                .has(FlowMirrorPointsFrame.FLOW_PATH_ID_PROPERTY,
                        PathIdConverter.INSTANCE.toGraphProperty(pathId))
                .has(FlowMirrorPointsFrame.MIRROR_SWITCH_ID_PROPERTY,
                        SwitchIdConverter.INSTANCE.toGraphProperty(switchId)))
                .toListExplicit(FlowMirrorPointsFrame.class);

        return flowMirrorPointsFrames.isEmpty() ? Optional.empty()
                : Optional.of(flowMirrorPointsFrames.get(0)).map(FlowMirrorPoints::new);
    }

    @Override
    protected FlowMirrorPointsFrame doAdd(FlowMirrorPointsData data) {
        FlowMirrorPointsFrame frame = KildaBaseVertexFrame.addNewFramedVertex(framedGraph(),
                FlowMirrorPointsFrame.FRAME_LABEL, FlowMirrorPointsFrame.class);
        FlowMirrorPoints.FlowMirrorPointsCloner.INSTANCE.copyWithoutPaths(data, frame);
        frame.addPaths(data.getMirrorPaths().stream()
                .peek(path -> {
                    if (!(path.getData() instanceof FlowPathFrame)) {
                        flowMirrorPathRepository.add(path);
                    }
                })
                .toArray(FlowMirrorPath[]::new));
        return frame;
    }

    @Override
    protected void doRemove(FlowMirrorPointsFrame frame) {
        frame.getMirrorPaths().forEach(path -> {
            if (path.getData() instanceof FlowPathFrame) {
                flowMirrorPathRepository.remove(path);
            }
        });
        frame.remove();
    }

    @Override
    protected FlowMirrorPointsData doDetach(FlowMirrorPoints entity, FlowMirrorPointsFrame frame) {
        return FlowMirrorPoints.FlowMirrorPointsCloner.INSTANCE.deepCopy(frame, entity.getFlowPath(), entity);
    }
}
