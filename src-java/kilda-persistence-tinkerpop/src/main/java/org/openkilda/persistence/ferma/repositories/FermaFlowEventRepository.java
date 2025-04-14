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

import org.openkilda.model.history.FlowEvent;
import org.openkilda.model.history.FlowEvent.FlowEventData;
import org.openkilda.model.history.FlowEventAction;
import org.openkilda.model.history.FlowStatusView;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.ferma.FermaPersistentImplementation;
import org.openkilda.persistence.ferma.frames.FlowEventFrame;
import org.openkilda.persistence.ferma.frames.KildaBaseVertexFrame;
import org.openkilda.persistence.ferma.frames.converters.InstantLongConverter;
import org.openkilda.persistence.repositories.history.FlowEventRepository;

import org.apache.tinkerpop.gremlin.process.traversal.Order;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Ferma (Tinkerpop) implementation of {@link FlowEventRepository}.
 */
public class FermaFlowEventRepository extends FermaGenericRepository<FlowEvent, FlowEventData, FlowEventFrame>
        implements FlowEventRepository {
    public FermaFlowEventRepository(FermaPersistentImplementation implementation) {
        super(implementation);
    }

    @Override
    public boolean existsByTaskId(String taskId) {
        try (GraphTraversal<?, ?> traversal = framedGraph().traverse(g -> g.V()
                .hasLabel(FlowEventFrame.FRAME_LABEL)
                .has(FlowEventFrame.TASK_ID_PROPERTY, taskId))
                .getRawTraversal()) {
            return traversal.hasNext();
        } catch (Exception e) {
            throw new PersistenceException("Failed to traverse", e);
        }
    }

    @Override
    public List<FlowEvent> findByFlowIdAndTimeFrame(String flowId, Instant timeFrom, Instant timeTo, int maxCount) {
        return framedGraph().traverse(g -> {
            GraphTraversal<Vertex, Vertex> traversal = g.V()
                    .hasLabel(FlowEventFrame.FRAME_LABEL)
                    .has(FlowEventFrame.FLOW_ID_PROPERTY, flowId);
            if (timeFrom != null) {
                traversal = traversal.has(FlowEventFrame.TIMESTAMP_PROPERTY,
                        P.gte(InstantLongConverter.INSTANCE.toGraphProperty(timeFrom)));
            }
            if (timeTo != null) {
                traversal = traversal.has(FlowEventFrame.TIMESTAMP_PROPERTY,
                        P.lte(InstantLongConverter.INSTANCE.toGraphProperty(timeTo)));
            }
            return traversal
                    .order().by(FlowEventFrame.TIMESTAMP_PROPERTY, Order.desc)
                    .limit(maxCount);
        }).toListExplicit(FlowEventFrame.class).stream()
                .sorted(Comparator.comparing(FlowEventFrame::getTimestamp))
                .map(FlowEvent::new)
                .collect(Collectors.toList());
    }

    @Override
    public List<FlowStatusView> findFlowStatusesByFlowIdAndTimeFrame(String flowId, Instant timeFrom,
                                                                     Instant timeTo, int maxCount) {
        List<FlowStatusView> statuses = new ArrayList<>();
        findByFlowIdAndTimeFrame(flowId, timeFrom, timeTo, maxCount).forEach(flowEvent -> {
            for (FlowEventAction flowEventAction : flowEvent.getEventActions()) {
                String action = flowEventAction.getAction();
                if (action.equals(FlowEvent.FLOW_DELETED_ACTION)) {
                    statuses.add(new FlowStatusView(flowEventAction.getTimestamp(), "DELETED"));
                }
                for (String actionPart : FlowEvent.FLOW_STATUS_ACTION_PARTS) {
                    if (action.contains(actionPart)) {
                        statuses.add(new FlowStatusView(
                                flowEventAction.getTimestamp(), action.replace(actionPart, "")));
                    }
                }
            }
        });

        statuses.sort(Comparator.comparing(FlowStatusView::getTimestamp));
        return statuses;
    }

    @Override
    protected FlowEventFrame doAdd(FlowEventData data) {
        FlowEventFrame frame = KildaBaseVertexFrame.addNewFramedVertex(framedGraph(), FlowEventFrame.FRAME_LABEL,
                FlowEventFrame.class);
        FlowEvent.FlowEventCloner.INSTANCE.copyWithoutRecordsAndDumps(data, frame);
        return frame;
    }

    @Override
    protected void doRemove(FlowEventFrame frame) {
        frame.remove();
    }

    @Override
    protected FlowEventData doDetach(FlowEvent entity, FlowEventFrame frame) {
        return FlowEvent.FlowEventCloner.INSTANCE.deepCopy(frame);
    }
}
