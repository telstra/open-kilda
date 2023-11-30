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

package org.openkilda.persistence.ferma.frames;

import org.openkilda.model.history.HaFlowEvent.HaFlowEventData;
import org.openkilda.model.history.HaFlowEventAction;
import org.openkilda.model.history.HaFlowEventDump;
import org.openkilda.persistence.ferma.frames.converters.Convert;
import org.openkilda.persistence.ferma.frames.converters.InstantLongConverter;

import com.syncleus.ferma.annotations.Property;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public abstract class HaFlowEventFrame extends KildaBaseVertexFrame implements HaFlowEventData {

    public static final String FRAME_LABEL = "ha_flow_event";

    public static final String ACTION_PROPERTY = "action";
    public static final String ACTOR_PROPERTY = "actor";
    public static final String DETAILS_PROPERTY = "details";
    public static final String EVENT_ACTIONS_PROPERTY = "event_actions";
    public static final String EVENT_DUMPS_PROPERTY = "event_dumps";
    public static final String HA_FLOW_ID_PROPERTY = "ha_flow_id";
    public static final String TASK_ID_PROPERTY = "task_id";
    public static final String TIMESTAMP_PROPERTY = "timestamp";

    @Override
    @Property(TIMESTAMP_PROPERTY)
    @Convert(InstantLongConverter.class)
    public abstract Instant getTimestamp();

    @Override
    @Property(TIMESTAMP_PROPERTY)
    @Convert(InstantLongConverter.class)
    public abstract void setTimestamp(Instant timestamp);

    @Override
    @Property(ACTOR_PROPERTY)
    public abstract String getActor();

    @Override
    @Property(ACTOR_PROPERTY)
    public abstract void setActor(String actor);

    @Override
    @Property(ACTION_PROPERTY)
    public abstract String getAction();

    @Override
    @Property(ACTION_PROPERTY)
    public abstract void setAction(String action);

    @Override
    @Property(TASK_ID_PROPERTY)
    public abstract String getTaskId();

    @Override
    @Property(TASK_ID_PROPERTY)
    public abstract void setTaskId(String taskId);

    @Override
    @Property(DETAILS_PROPERTY)
    public abstract String getDetails();

    @Override
    @Property(DETAILS_PROPERTY)
    public abstract void setDetails(String details);

    @Override
    @Property(EVENT_ACTIONS_PROPERTY)
    public List<HaFlowEventAction> getEventActions() {
        return getGraph().traverse(g -> g.V()
                        .hasLabel(HaFlowEventActionFrame.FRAME_LABEL)
                        .has(HaFlowEventActionFrame.TASK_ID_PROPERTY, getTaskId()))
                .toListExplicit(HaFlowEventActionFrame.class).stream()
                .sorted(Comparator.comparing(HaFlowEventActionFrame::getTimestamp)
                        .thenComparing(x -> x.getId()))
                .map(HaFlowEventAction::new)
                .collect(Collectors.toList());
    }

    @Override
    @Property(EVENT_DUMPS_PROPERTY)
    public List<HaFlowEventDump> getEventDumps() {
        return getGraph().traverse(g -> g.V()
                        .hasLabel(HaFlowEventDumpFrame.FRAME_LABEL)
                        .has(HaFlowEventDumpFrame.TASK_ID_PROPERTY, getTaskId()))
                .toListExplicit(HaFlowEventDumpFrame.class).stream()
                .map(HaFlowEventDump::new)
                .collect(Collectors.toList());
    }

    @Override
    @Property(HA_FLOW_ID_PROPERTY)
    public abstract String getHaFlowId();

    @Override
    @Property(HA_FLOW_ID_PROPERTY)
    public abstract void setHaFlowId(String flowId);
}
