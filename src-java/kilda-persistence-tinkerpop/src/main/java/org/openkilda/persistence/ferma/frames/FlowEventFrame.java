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

package org.openkilda.persistence.ferma.frames;

import org.openkilda.model.history.FlowEvent.FlowEventData;
import org.openkilda.model.history.FlowEventAction;
import org.openkilda.model.history.FlowEventDump;
import org.openkilda.persistence.ferma.frames.converters.Convert;
import org.openkilda.persistence.ferma.frames.converters.InstantLongConverter;

import com.syncleus.ferma.annotations.Property;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public abstract class FlowEventFrame extends KildaBaseVertexFrame implements FlowEventData {
    public static final String FRAME_LABEL = "flow_event";
    public static final String TASK_ID_PROPERTY = "task_id";
    public static final String FLOW_ID_PROPERTY = "flow_id";
    public static final String TIMESTAMP_PROPERTY = "timestamp";

    @Override
    @Property(FLOW_ID_PROPERTY)
    public abstract String getFlowId();

    @Override
    @Property(FLOW_ID_PROPERTY)
    public abstract void setFlowId(String flowId);

    @Override
    @Property(TIMESTAMP_PROPERTY)
    @Convert(InstantLongConverter.class)
    public abstract Instant getTimestamp();

    @Override
    @Property(TIMESTAMP_PROPERTY)
    @Convert(InstantLongConverter.class)
    public abstract void setTimestamp(Instant timestamp);

    @Override
    @Property("actor")
    public abstract String getActor();

    @Override
    @Property("actor")
    public abstract void setActor(String actor);

    @Override
    @Property("action")
    public abstract String getAction();

    @Override
    @Property("action")
    public abstract void setAction(String action);

    @Override
    @Property(TASK_ID_PROPERTY)
    public abstract String getTaskId();

    @Override
    @Property(TASK_ID_PROPERTY)
    public abstract void setTaskId(String taskId);

    @Override
    @Property("details")
    public abstract String getDetails();

    @Override
    @Property("details")
    public abstract void setDetails(String details);

    @Override
    public List<FlowEventAction> getEventActions() {
        return getGraph().traverse(g -> g.V()
                .hasLabel(FlowEventActionFrame.FRAME_LABEL)
                .has(FlowEventActionFrame.TASK_ID_PROPERTY, getTaskId()))
                .toListExplicit(FlowEventActionFrame.class).stream()
                .sorted(Comparator.comparing(FlowEventActionFrame::getTimestamp))
                .map(FlowEventAction::new)
                .collect(Collectors.toList());
    }

    @Override
    public List<FlowEventDump> getEventDumps() {
        return getGraph().traverse(g -> g.V()
                .hasLabel(FlowEventDumpFrame.FRAME_LABEL)
                .has(FlowEventDumpFrame.TASK_ID_PROPERTY, getTaskId()))
                .toListExplicit(FlowEventDumpFrame.class).stream()
                .map(FlowEventDump::new)
                .collect(Collectors.toList());
    }
}
