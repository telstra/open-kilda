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

import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlow.HaFlowData;
import org.openkilda.model.HaFlowPath;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.ferma.FermaPersistentImplementation;
import org.openkilda.persistence.ferma.frames.HaFlowFrame;
import org.openkilda.persistence.ferma.frames.HaFlowPathFrame;
import org.openkilda.persistence.ferma.frames.HaSubFlowFrame;
import org.openkilda.persistence.ferma.frames.KildaBaseVertexFrame;
import org.openkilda.persistence.repositories.HaFlowPathRepository;
import org.openkilda.persistence.repositories.HaFlowRepository;
import org.openkilda.persistence.repositories.HaSubFlowRepository;
import org.openkilda.persistence.tx.TransactionManager;

import lombok.extern.slf4j.Slf4j;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;

import java.util.Collection;
import java.util.Optional;
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
    public Optional<HaFlow> findById(String haFlowId) {
        return HaFlowFrame.load(framedGraph(), haFlowId).map(HaFlow::new);
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
        frame.setSubFlows(data.getSubFlows().stream()
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
        frame.getSubFlows().forEach(subFlow -> {
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
