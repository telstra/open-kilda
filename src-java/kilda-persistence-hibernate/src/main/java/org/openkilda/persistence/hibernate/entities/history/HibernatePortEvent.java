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

import org.openkilda.model.SwitchId;
import org.openkilda.model.history.PortEvent.PortEventData;
import org.openkilda.persistence.hibernate.converters.SwitchIdConverter;
import org.openkilda.persistence.hibernate.entities.EntityBase;
import org.openkilda.persistence.hibernate.entities.JsonPayloadBase;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Delegate;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

@Getter
@Setter
@Entity(name = "PortEvent")
@Table(name = "port_event")
public class HibernatePortEvent extends EntityBase implements PortEventData {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
            name = "UUID",
            strategy = "org.hibernate.id.UUIDGenerator"
    )
    @Type(type = "uuid-char")
    @Column(name = "id", columnDefinition = "string(36)")
    private UUID recordId;

    @Convert(converter = SwitchIdConverter.class)
    @Column(name = "switch_id")
    private SwitchId switchId;

    @Column(name = "port_number")
    private int portNumber;

    @Column(name = "event")
    String event;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "event_time")
    protected Instant eventTime;

    @Delegate
    @Type(type = "json")
    @Column(name = "unstructured", columnDefinition = "json")
    private PortEventUnstructured unstructured;

    public HibernatePortEvent() {
        unstructured = new PortEventUnstructured();
    }

    @Override
    public Instant getTime() {
        return eventTime;
    }

    @Override
    public void setTime(Instant value) {
        eventTime = value;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PortEventUnstructured extends JsonPayloadBase {
        int upEventsCount;
        int downEventsCount;
    }
}
