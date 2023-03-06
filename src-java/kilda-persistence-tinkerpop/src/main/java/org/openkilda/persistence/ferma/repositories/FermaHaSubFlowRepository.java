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

import org.openkilda.model.HaSubFlow;
import org.openkilda.model.HaSubFlow.HaSubFlowData;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.ferma.FermaPersistentImplementation;
import org.openkilda.persistence.ferma.frames.HaSubFlowFrame;
import org.openkilda.persistence.ferma.frames.KildaBaseVertexFrame;
import org.openkilda.persistence.repositories.HaSubFlowRepository;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Ferma (Tinkerpop) implementation of {@link HaSubFlowRepository}.
 */
public class FermaHaSubFlowRepository extends FermaGenericRepository<HaSubFlow, HaSubFlowData, HaSubFlowFrame>
        implements HaSubFlowRepository {
    public FermaHaSubFlowRepository(FermaPersistentImplementation implementation) {
        super(implementation);
    }

    @Override
    public Collection<HaSubFlow> findAll() {
        return framedGraph().traverse(g -> g.V()
                .hasLabel(HaSubFlowFrame.FRAME_LABEL))
                .toListExplicit(HaSubFlowFrame.class).stream()
                .map(HaSubFlow::new)
                .collect(Collectors.toList());
    }

    @Override
    public boolean exists(String haSubFlowId) {
        try (GraphTraversal<?, ?> traversal = framedGraph().traverse(g -> g.V()
                        .hasLabel(HaSubFlowFrame.FRAME_LABEL)
                        .has(HaSubFlowFrame.HA_SUBFLOW_ID_PROPERTY, haSubFlowId))
                .getRawTraversal()) {
            return traversal.hasNext();
        } catch (Exception e) {
            throw new PersistenceException("Failed to traverse", e);
        }
    }

    @Override
    public Optional<HaSubFlow> findById(String haSubFlowId) {
        return HaSubFlowFrame.load(framedGraph(), haSubFlowId).map(HaSubFlow::new);
    }

    @Override
    protected HaSubFlowFrame doAdd(HaSubFlowData data) {
        HaSubFlowFrame frame = KildaBaseVertexFrame.addNewFramedVertex(framedGraph(), HaSubFlowFrame.FRAME_LABEL,
                HaSubFlowFrame.class);
        HaSubFlow.HaSubFlowCloner.INSTANCE.copy(data, frame);
        return frame;
    }

    @Override
    protected void doRemove(HaSubFlowFrame frame) {
        frame.remove();
    }

    @Override
    protected HaSubFlowData doDetach(HaSubFlow entity, HaSubFlowFrame frame) {
        return HaSubFlow.HaSubFlowCloner.INSTANCE.deepCopy(frame, entity.getHaFlow());
    }
}
