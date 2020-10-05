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

import org.openkilda.model.BfdSession;
import org.openkilda.model.BfdSession.BfdSessionData;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.ferma.FramedGraphFactory;
import org.openkilda.persistence.ferma.frames.BfdSessionFrame;
import org.openkilda.persistence.ferma.frames.KildaBaseVertexFrame;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;
import org.openkilda.persistence.repositories.BfdSessionRepository;
import org.openkilda.persistence.tx.TransactionManager;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Ferma (Tinkerpop) implementation of {@link BfdSessionRepository}.
 */
public class FermaBfdSessionRepository extends FermaGenericRepository<BfdSession, BfdSessionData, BfdSessionFrame>
        implements BfdSessionRepository {
    public FermaBfdSessionRepository(FramedGraphFactory<?> graphFactory, TransactionManager transactionManager) {
        super(graphFactory, transactionManager);
    }

    @Override
    public Collection<BfdSession> findAll() {
        return framedGraph().traverse(g -> g.V()
                .hasLabel(BfdSessionFrame.FRAME_LABEL))
                .toListExplicit(BfdSessionFrame.class).stream()
                .map(BfdSession::new)
                .collect(Collectors.toList());
    }

    @Override
    public boolean exists(SwitchId switchId, Integer port) {
        try (GraphTraversal<?, ?> traversal = framedGraph().traverse(g -> g.V()
                .hasLabel(BfdSessionFrame.FRAME_LABEL)
                .has(BfdSessionFrame.SWITCH_ID_PROPERTY, SwitchIdConverter.INSTANCE.toGraphProperty(switchId))
                .has(BfdSessionFrame.PORT_PROPERTY, port))
                .getRawTraversal()) {
            return traversal.hasNext();
        } catch (Exception e) {
            throw new PersistenceException("Failed to traverse", e);
        }
    }

    @Override
    public Optional<BfdSession> findBySwitchIdAndPort(SwitchId switchId, Integer port) {
        List<? extends BfdSessionFrame> bfdSessionFrames = framedGraph().traverse(g -> g.V()
                .hasLabel(BfdSessionFrame.FRAME_LABEL)
                .has(BfdSessionFrame.SWITCH_ID_PROPERTY, SwitchIdConverter.INSTANCE.toGraphProperty(switchId))
                .has(BfdSessionFrame.PORT_PROPERTY, port))
                .toListExplicit(BfdSessionFrame.class);
        return bfdSessionFrames.isEmpty() ? Optional.empty() : Optional.of(bfdSessionFrames.get(0))
                .map(BfdSession::new);
    }

    @Override
    protected BfdSessionFrame doAdd(BfdSessionData data) {
        BfdSessionFrame frame = KildaBaseVertexFrame.addNewFramedVertex(framedGraph(),
                BfdSessionFrame.FRAME_LABEL, BfdSessionFrame.class);
        BfdSession.BfdSessionCloner.INSTANCE.copy(data, frame);
        return frame;
    }

    @Override
    protected void doRemove(BfdSessionFrame frame) {
        frame.remove();
    }

    @Override
    protected BfdSessionData doDetach(BfdSession entity, BfdSessionFrame frame) {
        return BfdSession.BfdSessionCloner.INSTANCE.copy(frame);
    }
}
