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

import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.HaFlowPath.HaFlowPathData;
import org.openkilda.model.PathId;
import org.openkilda.persistence.ferma.FermaPersistentImplementation;
import org.openkilda.persistence.ferma.frames.FlowPathFrame;
import org.openkilda.persistence.ferma.frames.HaFlowFrame;
import org.openkilda.persistence.ferma.frames.HaFlowPathFrame;
import org.openkilda.persistence.ferma.frames.KildaBaseVertexFrame;
import org.openkilda.persistence.ferma.frames.converters.PathIdConverter;
import org.openkilda.persistence.repositories.HaFlowPathRepository;
import org.openkilda.persistence.tx.TransactionManager;

import lombok.extern.slf4j.Slf4j;
import org.apache.tinkerpop.gremlin.process.traversal.P;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ferma implementation of {@link HaFlowPathRepository}.
 */
@Slf4j
public class FermaHaFlowPathRepository extends FermaGenericRepository<HaFlowPath, HaFlowPathData, HaFlowPathFrame>
        implements HaFlowPathRepository {
    public FermaHaFlowPathRepository(FermaPersistentImplementation implementation) {
        super(implementation);
    }

    @Override
    public Collection<HaFlowPath> findAll() {
        return framedGraph().traverse(g -> g.V()
                        .hasLabel(HaFlowPathFrame.FRAME_LABEL))
                .toListExplicit(HaFlowPathFrame.class).stream()
                .map(HaFlowPath::new)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<HaFlowPath> findById(PathId haFlowPathId) {
        return HaFlowPathFrame.load(framedGraph(), haFlowPathId).map(HaFlowPath::new);
    }

    @Override
    public Collection<HaFlowPath> findByHaFlowId(String haFlowId) {
        return framedGraph().traverse(g -> g.V()
                        .hasLabel(HaFlowPathFrame.FRAME_LABEL)
                        .has(HaFlowPathFrame.HA_FLOW_ID_PROPERTY, haFlowId))
                .toListExplicit(HaFlowPathFrame.class).stream()
                .map(HaFlowPath::new)
                .collect(Collectors.toList());
    }

    @Override
    public Collection<PathId> findPathIdsByDiverseGroupId(String diverseGroupId) {
        return findPathIdsByFlowGroupId(HaFlowFrame.DIVERSE_GROUP_ID_PROPERTY, diverseGroupId);
    }

    @Override
    public Collection<PathId> findPathIdsByAffinityGroupId(String affinityGroupId) {
        return findPathIdsByFlowGroupId(HaFlowFrame.AFFINITY_GROUP_ID_PROPERTY, affinityGroupId);
    }

    private Collection<PathId> findPathIdsByFlowGroupId(String groupIdProperty, String groupId) {
        return framedGraph().traverse(g -> g.V()
                        .hasLabel(HaFlowFrame.FRAME_LABEL)
                        .has(groupIdProperty, groupId)
                        .out(HaFlowFrame.OWNS_PATHS_EDGE)
                        .hasLabel(HaFlowPathFrame.FRAME_LABEL)
                        .values(HaFlowPathFrame.HA_PATH_ID_PROPERTY))
                .getRawTraversal().toStream()
                .map(pathId -> PathIdConverter.INSTANCE.toEntityAttribute((String) pathId))
                .collect(Collectors.toList());
    }

    @Override
    public Map<PathId, HaFlow> findHaFlowsByPathIds(Set<PathId> pathIds) {
        Set<String> graphPathIds = pathIds.stream()
                .map(PathIdConverter.INSTANCE::toGraphProperty)
                .collect(Collectors.toSet());
        List<? extends HaFlowPathFrame> flowPathFrames = framedGraph().traverse(g -> g.V()
                        .hasLabel(HaFlowPathFrame.FRAME_LABEL)
                        .has(HaFlowPathFrame.HA_PATH_ID_PROPERTY, P.within(graphPathIds)))
                .toListExplicit(HaFlowPathFrame.class);
        return flowPathFrames.stream()
                .map(HaFlowPath::new)
                .filter(path -> path.getHaFlow() != null)
                .collect(Collectors.toMap(HaFlowPath::getHaPathId, HaFlowPath::getHaFlow));
    }

    @Override
    public Map<PathId, HaFlowPath> findByIds(Set<PathId> pathIds) {
        Set<String> graphPathIds = pathIds.stream()
                .map(PathIdConverter.INSTANCE::toGraphProperty)
                .collect(Collectors.toSet());
        List<? extends HaFlowPathFrame> haFlowPathFrames = framedGraph().traverse(g -> g.V()
                        .hasLabel(HaFlowPathFrame.FRAME_LABEL)
                        .has(HaFlowPathFrame.HA_PATH_ID_PROPERTY, P.within(graphPathIds)))
                .toListExplicit(HaFlowPathFrame.class);
        return haFlowPathFrames.stream()
                .map(HaFlowPath::new)
                .collect(Collectors.toMap(HaFlowPath::getHaPathId, Function.identity()));
    }

    @Override
    public void updateStatus(PathId pathId, FlowPathStatus pathStatus) {
        getTransactionManager().doInTransaction(() ->
                framedGraph().traverse(g -> g.V()
                                .hasLabel(HaFlowPathFrame.FRAME_LABEL)
                                .has(HaFlowPathFrame.HA_PATH_ID_PROPERTY,
                                        PathIdConverter.INSTANCE.toGraphProperty(pathId)))
                        .toListExplicit(HaFlowPathFrame.class)
                        .forEach(pathFrame -> pathFrame.setStatus(pathStatus)));
    }

    @Override
    public Optional<HaFlowPath> remove(PathId haFlowPathId) {
        TransactionManager transactionManager = getTransactionManager();
        if (transactionManager.isTxOpen()) {
            // This implementation removes dependant entities (segments, haSubFlowEdges) in a separate transaction,
            // so the path entity may require to be reloaded in a case of failed transaction.
            throw new IllegalStateException("This implementation of remove requires no outside transaction");
        }

        return transactionManager.doInTransaction(() ->
                findById(haFlowPathId)
                        .map(path -> {
                            remove(path);
                            return path;
                        }));
    }

    @Override
    protected HaFlowPathFrame doAdd(HaFlowPathData data) {
        HaFlowPathFrame frame = KildaBaseVertexFrame.addNewFramedVertex(framedGraph(), HaFlowPathFrame.FRAME_LABEL,
                HaFlowPathFrame.class);
        HaFlowPath.HaFlowPathCloner.INSTANCE.copyWithoutHaSubFlows(data, frame);
        frame.setHaSubFlows(data.getHaSubFlows());
        return frame;
    }

    @Override
    protected void doRemove(HaFlowPathFrame frame) {
        frame.getSubPaths().forEach(subPath -> {
            if (subPath.getData() instanceof FlowPathFrame) {
                // No need to call the FlowPath repository, as sub paths already detached along with the path.
                ((FlowPathFrame) subPath.getData()).remove();
            }
        });
        frame.remove();
    }

    @Override
    protected HaFlowPathData doDetach(HaFlowPath entity, HaFlowPathFrame frame) {
        return HaFlowPath.HaFlowPathCloner.INSTANCE.deepCopy(frame, entity.getHaFlow());
    }
}
