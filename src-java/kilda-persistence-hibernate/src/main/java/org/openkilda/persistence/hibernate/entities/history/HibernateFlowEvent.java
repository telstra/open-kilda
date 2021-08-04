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

package org.openkilda.persistence.hibernate.entities.history;

import org.openkilda.model.history.FlowEvent.FlowEventData;
import org.openkilda.model.history.FlowEventAction;
import org.openkilda.model.history.FlowEventDump;
import org.openkilda.persistence.hibernate.entities.EntityBase;
import org.openkilda.persistence.hibernate.entities.JsonPayloadBase;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Delegate;
import org.apache.commons.codec.digest.DigestUtils;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;

@Getter
@Setter
@Entity(name = "FlowEvent")
@Table(name = "flow_event")
public class HibernateFlowEvent extends EntityBase implements FlowEventData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flow_id")
    private String flowId;

    @Column(name = "task_id")
    private String taskId;

    @Column(name = "task_id_unique_key")
    private String taskIdUniqueKey;

    @Column(name = "action")
    private String action;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "event_time")
    protected Instant eventTime;

    @Delegate
    @Type(type = "json")
    @Column(name = "unstructured", columnDefinition = "json")
    private FlowEventUnstructured unstructured;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL)
    @OrderBy("event_time")
    private List<HibernateFlowEventAction> actions = new ArrayList<>();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<HibernateFlowEventDump> dumps = new ArrayList<>();

    public HibernateFlowEvent() {
        unstructured = new FlowEventUnstructured();
    }

    public void setTaskId(String value) {
        taskIdUniqueKey = String.format("%s:%x:sha256", DigestUtils.sha256Hex(value), value.length());
        taskId = value;
    }

    public void setTaskIdUniqueKey(String value) {
        // TaskIdUniqueKey is not accessible to write
    }

    @Override
    public Instant getTimestamp() {
        return eventTime;
    }

    @Override
    public void setTimestamp(Instant value) {
        eventTime = value;
    }

    @Override
    public List<FlowEventAction> getEventActions() {
        return actions.stream()
                .map(FlowEventAction::new)
                .collect(Collectors.toList());
    }

    @Override
    public List<FlowEventDump> getEventDumps() {
        return dumps.stream()
                .map(FlowEventDump::new)
                .collect(Collectors.toList());
    }

    public void addAction(HibernateFlowEventAction entry) {
        actions.add(entry);
        entry.setEvent(this);
    }

    public void addDump(HibernateFlowEventDump entry) {
        dumps.add(entry);
        entry.setEvent(this);
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class FlowEventUnstructured extends JsonPayloadBase {
        String actor;
        String details;
    }
}
