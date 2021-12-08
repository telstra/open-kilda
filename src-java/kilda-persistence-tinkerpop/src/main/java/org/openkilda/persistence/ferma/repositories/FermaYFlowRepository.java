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

import org.openkilda.model.YFlow;
import org.openkilda.model.YFlow.YFlowData;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.ferma.FermaPersistentImplementation;
import org.openkilda.persistence.ferma.frames.KildaBaseVertexFrame;
import org.openkilda.persistence.ferma.frames.YFlowFrame;
import org.openkilda.persistence.ferma.frames.YSubFlowFrame;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.persistence.tx.TransactionManager;

import lombok.extern.slf4j.Slf4j;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Ferma implementation of {@link FlowRepository}.
 */
@Slf4j
public class FermaYFlowRepository extends FermaGenericRepository<YFlow, YFlowData, YFlowFrame>
        implements YFlowRepository {
    public FermaYFlowRepository(FermaPersistentImplementation implementation) {
        super(implementation);
    }

    @Override
    public Collection<YFlow> findAll() {
        return framedGraph().traverse(g -> g.V()
                        .hasLabel(YFlowFrame.FRAME_LABEL))
                .toListExplicit(YFlowFrame.class).stream()
                .map(YFlow::new)
                .collect(Collectors.toList());
    }

    @Override
    public boolean exists(String yFlowId) {
        try (GraphTraversal<?, ?> traversal = framedGraph().traverse(g -> g.V()
                        .hasLabel(YFlowFrame.FRAME_LABEL)
                        .has(YFlowFrame.YFLOW_ID_PROPERTY, yFlowId))
                .getRawTraversal()) {
            return traversal.hasNext();
        } catch (Exception e) {
            throw new PersistenceException("Failed to traverse", e);
        }
    }

    @Override
    public Optional<YFlow> findById(String yFlowId) {
        return YFlowFrame.load(framedGraph(), yFlowId).map(YFlow::new);
    }

    @Override
    public boolean isSubFlow(String flowId) {
        try (GraphTraversal<?, ?> traversal = framedGraph().traverse(g -> g.E()
                        .hasLabel(YSubFlowFrame.FRAME_LABEL)
                        .has(YSubFlowFrame.SUBFLOW_ID_PROPERTY, flowId))
                .getRawTraversal()) {
            return traversal.hasNext();
        } catch (Exception e) {
            throw new PersistenceException("Failed to traverse", e);
        }
    }

    @Override
    public Optional<YFlow> remove(String flowId) {
        TransactionManager transactionManager = getTransactionManager();
        if (transactionManager.isTxOpen()) {
            // This implementation removes dependant entities (paths, segments) in a separate transactions,
            // so the flow entity may require to be reloaded in a case of failed transaction.
            throw new IllegalStateException("This implementation of remove requires no outside transaction");
        }

        return transactionManager.doInTransaction(() ->
                findById(flowId)
                        .map(flow -> {
                            remove(flow);
                            return flow;
                        }));
    }

    @Override
    protected YFlowFrame doAdd(YFlowData data) {
        YFlowFrame frame = KildaBaseVertexFrame.addNewFramedVertex(framedGraph(), YFlowFrame.FRAME_LABEL,
                YFlowFrame.class);
        YFlow.YFlowCloner.INSTANCE.copyWithoutSubFlows(data, frame);
        frame.setSubFlows(data.getSubFlows());
        return frame;
    }

    @Override
    protected void doRemove(YFlowFrame frame) {
        frame.getSubFlows().forEach(flow -> {
            if (flow.getData() instanceof YSubFlowFrame) {
                ((YSubFlowFrame) flow.getData()).remove();
            }
        });
        frame.remove();
    }

    @Override
    protected YFlowData doDetach(YFlow entity, YFlowFrame frame) {
        return YFlow.YFlowCloner.INSTANCE.deepCopy(frame, entity);
    }
}
