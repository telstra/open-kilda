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

package org.openkilda.persistence.hibernate.entities.history;

import org.openkilda.model.history.HaFlowEvent.HaFlowEventData;
import org.openkilda.model.history.HaFlowEventAction;
import org.openkilda.model.history.HaFlowEventDump;
import org.openkilda.persistence.hibernate.entities.EntityBase;
import org.openkilda.persistence.hibernate.utils.UniqueKeyUtil;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@Entity(name = "HaFlowEvent")
@Table(name = "ha_flow_event")
public class HibernateHaFlowEvent extends EntityBase implements HaFlowEventData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ha_flow_id")
    private String haFlowId;

    @Column(name = "timestamp")
    private Instant timestamp;

    @Column(name = "actor")
    private String actor;

    @Column(name = "action")
    private String action;

    @Column(name = "task_id")
    private String taskId;

    @Column(name = "task_id_unique_key")
    private String taskIdUniqueKey;

    @Column(name = "details")
    private String details;

    @OneToMany(mappedBy = "haFlowEvent", cascade = CascadeType.ALL)
    @OrderBy("timestamp")
    @LazyCollection(LazyCollectionOption.FALSE)
    private List<HibernateHaFlowEventAction> eventActions = new ArrayList<>();

    @OneToMany(mappedBy = "haFlowEvent", cascade = CascadeType.ALL)
    @OrderBy("id")
    @LazyCollection(LazyCollectionOption.FALSE)
    private List<HibernateHaFlowEventDump> eventDumps = new ArrayList<>();

    @Override
    public List<HaFlowEventAction> getEventActions() {
        return eventActions.stream()
                .map(HaFlowEventAction::new)
                .collect(Collectors.toList());
    }

    public void linkAction(HibernateHaFlowEventAction action) {
        action.setHaFlowEvent(this);
        eventActions.add(action);
    }

    @Override
    public List<HaFlowEventDump> getEventDumps() {
        return eventDumps.stream()
                .map(HaFlowEventDump::new)
                .collect(Collectors.toList());
    }

    public void linkDump(HibernateHaFlowEventDump dump) {
        dump.setHaFlowEvent(this);
        eventDumps.add(dump);
    }

    public void setTaskId(String value) {
        taskIdUniqueKey = UniqueKeyUtil.makeTaskIdUniqueKey(value);
        taskId = value;
    }

    public void setTaskIdUniqueKey(String value) {
        // TaskIdUniqueKey is not accessible to write
    }
}
