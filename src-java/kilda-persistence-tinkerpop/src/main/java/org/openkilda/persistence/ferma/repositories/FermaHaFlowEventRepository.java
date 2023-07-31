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

import org.openkilda.model.history.HaFlowEvent;
import org.openkilda.model.history.HaFlowEvent.HaFlowEventCloner;
import org.openkilda.model.history.HaFlowEvent.HaFlowEventData;
import org.openkilda.persistence.ferma.FermaPersistentImplementation;
import org.openkilda.persistence.ferma.frames.HaFlowEventFrame;
import org.openkilda.persistence.ferma.frames.KildaBaseVertexFrame;
import org.openkilda.persistence.ferma.frames.converters.InstantLongConverter;
import org.openkilda.persistence.repositories.history.HaFlowEventRepository;

import org.apache.tinkerpop.gremlin.process.traversal.Order;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class FermaHaFlowEventRepository extends FermaGenericRepository<HaFlowEvent, HaFlowEventData, HaFlowEventFrame>
        implements HaFlowEventRepository {
    FermaHaFlowEventRepository(FermaPersistentImplementation implementation) {
        super(implementation);
    }

    @Override
    public List<HaFlowEvent> findByHaFlowIdAndTimeFrame(String haFlowId,
                                                        Instant timeFrom, Instant timeTo, int maxCount) {
        return framedGraph().traverse(g -> {
            GraphTraversal<Vertex, Vertex> traversal = g.V()
                    .hasLabel(HaFlowEventFrame.FRAME_LABEL)
                    .has(HaFlowEventFrame.HA_FLOW_ID_PROPERTY, haFlowId);
            if (timeFrom != null) {
                traversal = traversal.has(HaFlowEventFrame.TIMESTAMP_PROPERTY,
                        P.gte(InstantLongConverter.INSTANCE.toGraphProperty(timeFrom)));
            }
            if (timeTo != null) {
                traversal = traversal.has(HaFlowEventFrame.TIMESTAMP_PROPERTY,
                        P.lte(InstantLongConverter.INSTANCE.toGraphProperty(timeTo)));
            }
            return traversal
                    .order().by(HaFlowEventFrame.TIMESTAMP_PROPERTY, Order.desc)
                    .limit(maxCount);
        }).toListExplicit(HaFlowEventFrame.class).stream()
                .sorted(Comparator.comparing(HaFlowEventFrame::getTimestamp))
                .map(HaFlowEvent::new)
                .collect(Collectors.toList());
    }

    @Override
    protected HaFlowEventFrame doAdd(HaFlowEventData data) {
        HaFlowEventFrame frame = KildaBaseVertexFrame.addNewFramedVertex(framedGraph(), HaFlowEventFrame.FRAME_LABEL,
                HaFlowEventFrame.class);
        HaFlowEventCloner.INSTANCE.copyWithoutRecordsAndDumps(data, frame);
        return frame;
    }

    @Override
    protected void doRemove(HaFlowEventFrame frame) {
        frame.remove();
    }

    @Override
    protected HaFlowEventData doDetach(HaFlowEvent entity, HaFlowEventFrame frame) {
        return HaFlowEventCloner.INSTANCE.deepCopy(frame);
    }
}
