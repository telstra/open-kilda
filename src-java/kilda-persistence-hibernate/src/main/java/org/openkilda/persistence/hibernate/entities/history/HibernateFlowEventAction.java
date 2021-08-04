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

import org.openkilda.model.history.FlowEventAction.FlowEventActionData;
import org.openkilda.persistence.hibernate.entities.EntityBase;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

@Getter
@Setter
@Entity(name = "FlowEventAction")
@Table(name = "flow_event_action")
public class HibernateFlowEventAction extends EntityBase implements FlowEventActionData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "action")
    private String action;

    @Column(name = "details")
    private String details;

    @Transient
    private String taskId;

    @ManyToOne
    @JoinColumn(name = "flow_event_id")
    private HibernateFlowEvent event;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "event_time")
    protected Instant eventTime;

    @Override
    public String getTaskId() {
        if (taskId != null) {
            return taskId;
        }
        if (event != null) {
            return event.getTaskId();
        }
        return null;
    }

    @Override
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    @Override
    public Instant getTimestamp() {
        return eventTime;
    }

    @Override
    public void setTimestamp(Instant value) {
        eventTime = value;
    }
}
