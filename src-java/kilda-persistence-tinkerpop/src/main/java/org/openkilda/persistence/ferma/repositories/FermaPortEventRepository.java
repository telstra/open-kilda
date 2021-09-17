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

import org.openkilda.model.SwitchId;
import org.openkilda.model.history.PortEvent;
import org.openkilda.model.history.PortEvent.PortEventCloner;
import org.openkilda.model.history.PortEvent.PortEventData;
import org.openkilda.persistence.ferma.FermaPersistentImplementation;
import org.openkilda.persistence.ferma.frames.KildaBaseVertexFrame;
import org.openkilda.persistence.ferma.frames.PortEventFrame;
import org.openkilda.persistence.ferma.frames.converters.InstantLongConverter;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;
import org.openkilda.persistence.repositories.history.PortEventRepository;

import org.apache.tinkerpop.gremlin.process.traversal.Order;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Ferma (Tinkerpop) implementation of {@link PortEventRepository}.
 */
public class FermaPortEventRepository extends FermaGenericRepository<PortEvent, PortEventData, PortEventFrame>
        implements PortEventRepository {
    FermaPortEventRepository(FermaPersistentImplementation implementation) {
        super(implementation);
    }

    @Override
    public List<PortEvent> findBySwitchIdAndPortNumber(SwitchId switchId, int portNumber,
                                                       Instant timeFrom, Instant timeTo) {
        return framedGraph().traverse(g -> {
            GraphTraversal<Vertex, Vertex> traversal = g.V()
                    .hasLabel(PortEventFrame.FRAME_LABEL)
                    .has(PortEventFrame.SWITCH_ID_PROPERTY, SwitchIdConverter.INSTANCE.toGraphProperty(switchId))
                    .has(PortEventFrame.PORT_NUMBER_PROPERTY, portNumber);
            if (timeFrom != null) {
                traversal = traversal.has(PortEventFrame.TIME_PROPERTY,
                        P.gte(InstantLongConverter.INSTANCE.toGraphProperty(timeFrom)));
            }
            if (timeTo != null) {
                traversal = traversal.has(PortEventFrame.TIME_PROPERTY,
                        P.lte(InstantLongConverter.INSTANCE.toGraphProperty(timeTo)));
            }
            return traversal
                    .order().by(PortEventFrame.TIME_PROPERTY, Order.incr);
        }).toListExplicit(PortEventFrame.class).stream()
                .map(PortEvent::new)
                .collect(Collectors.toList());
    }

    @Override
    protected PortEventFrame doAdd(PortEventData data) {
        PortEventFrame frame = KildaBaseVertexFrame.addNewFramedVertex(framedGraph(),
                PortEventFrame.FRAME_LABEL, PortEventFrame.class);
        PortEventCloner.INSTANCE.copy(data, frame);
        frame.setRecordId(UUID.randomUUID());
        return frame;
    }

    @Override
    protected void doRemove(PortEventFrame frame) {
        frame.remove();
    }

    @Override
    protected PortEventData doDetach(PortEvent entity, PortEventFrame frame) {
        return PortEventCloner.INSTANCE.deepCopy(frame);
    }
}
