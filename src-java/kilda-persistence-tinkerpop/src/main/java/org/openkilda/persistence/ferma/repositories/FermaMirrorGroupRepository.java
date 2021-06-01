/* Copyright 2021 Telstra Open Source
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

import org.openkilda.model.GroupId;
import org.openkilda.model.MirrorGroup;
import org.openkilda.model.MirrorGroup.MirrorGroupData;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.ferma.FramedGraphFactory;
import org.openkilda.persistence.ferma.frames.FlowMeterFrame;
import org.openkilda.persistence.ferma.frames.KildaBaseVertexFrame;
import org.openkilda.persistence.ferma.frames.MirrorGroupFrame;
import org.openkilda.persistence.ferma.frames.converters.GroupIdConverter;
import org.openkilda.persistence.ferma.frames.converters.PathIdConverter;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;
import org.openkilda.persistence.repositories.MirrorGroupRepository;
import org.openkilda.persistence.tx.TransactionManager;

import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Ferma (Tinkerpop) implementation of {@link MirrorGroupRepository}.
 */
public class FermaMirrorGroupRepository extends FermaGenericRepository<MirrorGroup, MirrorGroupData, MirrorGroupFrame>
        implements MirrorGroupRepository {

    public FermaMirrorGroupRepository(FramedGraphFactory<?> graphFactory, TransactionManager transactionManager) {
        super(graphFactory, transactionManager);
    }

    @Override
    public Collection<MirrorGroup> findAll() {
        return framedGraph().traverse(g -> g.V()
                .hasLabel(MirrorGroupFrame.FRAME_LABEL))
                .toListExplicit(MirrorGroupFrame.class).stream()
                .map(MirrorGroup::new)
                .collect(Collectors.toList());
    }

    @Override
    public Collection<MirrorGroup> findByPathId(PathId pathId) {
        return framedGraph().traverse(g -> g.V()
                .hasLabel(MirrorGroupFrame.FRAME_LABEL)
                .has(MirrorGroupFrame.PATH_ID_PROPERTY, PathIdConverter.INSTANCE.toGraphProperty(pathId)))
                .toListExplicit(MirrorGroupFrame.class).stream()
                .map(MirrorGroup::new)
                .collect(Collectors.toList());
    }

    @Override
    public Collection<MirrorGroup> findBySwitchId(SwitchId switchId) {
        return framedGraph().traverse(g -> g.V()
                .hasLabel(MirrorGroupFrame.FRAME_LABEL)
                .has(MirrorGroupFrame.SWITCH_ID_PROPERTY, SwitchIdConverter.INSTANCE.toGraphProperty(switchId)))
                .toListExplicit(MirrorGroupFrame.class).stream()
                .map(MirrorGroup::new)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<MirrorGroup> findByGroupId(GroupId groupId) {
        List<? extends MirrorGroupFrame> mirrorGroupFrames = framedGraph().traverse(g -> g.V()
                .hasLabel(MirrorGroupFrame.FRAME_LABEL)
                .has(MirrorGroupFrame.GROUP_ID_PROPERTY, GroupIdConverter.INSTANCE.toGraphProperty(groupId)))
                .toListExplicit(MirrorGroupFrame.class);

        return mirrorGroupFrames.isEmpty() ? Optional.empty()
                : Optional.of(mirrorGroupFrames.get(0)).map(MirrorGroup::new);
    }

    @Override
    public Optional<MirrorGroup> findByPathIdAndSwitchId(PathId pathId, SwitchId switchId) {
        return framedGraph().traverse(g -> g.V()
                .hasLabel(MirrorGroupFrame.FRAME_LABEL)
                .has(MirrorGroupFrame.PATH_ID_PROPERTY, PathIdConverter.INSTANCE.toGraphProperty(pathId))
                .has(MirrorGroupFrame.SWITCH_ID_PROPERTY, SwitchIdConverter.INSTANCE.toGraphProperty(switchId)))
                .toListExplicit(MirrorGroupFrame.class).stream()
                .map(MirrorGroup::new).findAny();
    }

    @Override
    public boolean exists(SwitchId switchId, GroupId groupId) {
        String switchIdAsStr = SwitchIdConverter.INSTANCE.toGraphProperty(switchId);
        Long groupIdAsLong = GroupIdConverter.INSTANCE.toGraphProperty(groupId);

        try (GraphTraversal<?, ?> traversal = framedGraph().traverse(g -> g.V()
                .hasLabel(FlowMeterFrame.FRAME_LABEL)
                .has(MirrorGroupFrame.GROUP_ID_PROPERTY, groupIdAsLong)
                .has(MirrorGroupFrame.SWITCH_ID_PROPERTY, switchIdAsStr))
                .getRawTraversal()) {
            return traversal.hasNext();
        } catch (Exception e) {
            throw new PersistenceException("Failed to traverse", e);
        }
    }

    @Override
    public Optional<GroupId> findFirstUnassignedGroupId(SwitchId switchId, GroupId lowestGroupId,
                                                        GroupId highestGroupId) {
        String switchIdAsStr = SwitchIdConverter.INSTANCE.toGraphProperty(switchId);
        Long lowestGroupIdAsLong = GroupIdConverter.INSTANCE.toGraphProperty(lowestGroupId);
        Long highestGroupIdAsLong = GroupIdConverter.INSTANCE.toGraphProperty(highestGroupId);

        try (GraphTraversal<?, ?> traversal = framedGraph().traverse(g -> g.V()
                .hasLabel(MirrorGroupFrame.FRAME_LABEL)
                .has(MirrorGroupFrame.SWITCH_ID_PROPERTY, switchIdAsStr)
                .has(MirrorGroupFrame.GROUP_ID_PROPERTY, P.gte(lowestGroupIdAsLong))
                .has(MirrorGroupFrame.GROUP_ID_PROPERTY, P.lt(highestGroupIdAsLong))
                .values(MirrorGroupFrame.GROUP_ID_PROPERTY)
                .order().math("_ + 1").as("a")
                .where(__.not(__.V().hasLabel(MirrorGroupFrame.FRAME_LABEL)
                        .has(MirrorGroupFrame.SWITCH_ID_PROPERTY, switchIdAsStr)
                        .values(MirrorGroupFrame.GROUP_ID_PROPERTY)
                        .where(P.eq("a"))))
                .select("a")
                .limit(1))
                .getRawTraversal()) {
            if (traversal.hasNext()) {
                return traversal.tryNext()
                        .map(l -> ((Double) l).longValue())
                        .map(GroupIdConverter.INSTANCE::toEntityAttribute);
            }
        } catch (Exception e) {
            throw new PersistenceException("Failed to traverse", e);
        }

        try (GraphTraversal<?, ?> traversal = framedGraph().traverse(g -> g.V()
                .hasLabel(MirrorGroupFrame.FRAME_LABEL)
                .has(MirrorGroupFrame.SWITCH_ID_PROPERTY, switchIdAsStr)
                .has(MirrorGroupFrame.GROUP_ID_PROPERTY, lowestGroupIdAsLong))
                .getRawTraversal()) {
            if (!traversal.hasNext()) {
                return Optional.of(lowestGroupId);
            }
        } catch (Exception e) {
            throw new PersistenceException("Failed to traverse", e);
        }

        return Optional.empty();
    }

    @Override
    protected MirrorGroupFrame doAdd(MirrorGroupData data) {
        MirrorGroupFrame frame = KildaBaseVertexFrame.addNewFramedVertex(framedGraph(),
                MirrorGroupFrame.FRAME_LABEL, MirrorGroupFrame.class);
        MirrorGroup.MirrorGroupCloner.INSTANCE.copy(data, frame);
        return frame;
    }

    @Override
    protected void doRemove(MirrorGroupFrame frame) {
        frame.remove();
    }

    @Override
    protected MirrorGroupData doDetach(MirrorGroup entity, MirrorGroupFrame frame) {
        return MirrorGroup.MirrorGroupCloner.INSTANCE.deepCopy(frame);
    }
}
