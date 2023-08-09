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

package org.openkilda.model.history;

import org.openkilda.model.CompositeDataEntity;
import org.openkilda.model.history.HaFlowEvent.HaFlowEventData;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.BeanSerializer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Delegate;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@DefaultSerializer(BeanSerializer.class)
@ToString
public class HaFlowEvent implements CompositeDataEntity<HaFlowEventData> {

    @Getter
    @Setter
    @Delegate
    @JsonIgnore
    private HaFlowEventData data;

    public interface HaFlowEventData extends GenericFlowEventData<HaFlowEventDump, HaFlowEventAction> {
        String getHaFlowId();

        void setHaFlowId(String flowId);
    }

    public HaFlowEvent() {
        this.data = new HaFlowEventDataImpl();
    }

    @Builder
    public HaFlowEvent(String haFlowId, Instant timestamp, String actor, String action,
                       String taskId, String details) {
        this.data = HaFlowEventDataImpl.builder()
                .haFlowId(haFlowId)
                .timestamp(timestamp)
                .actor(actor)
                .action(action)
                .taskId(taskId)
                .details(details)
                .build();
    }

    public HaFlowEvent(HaFlowEvent entityToClone) {
        data = HaFlowEventCloner.INSTANCE.deepCopy(entityToClone.getData());
    }

    public HaFlowEvent(HaFlowEventData data) {
        this.data = data;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static final class HaFlowEventDataImpl implements HaFlowEventData, Serializable {
        private static final long serialVersionUID = 1L;
        String haFlowId;
        Instant timestamp;
        String actor;
        String action;
        String taskId;
        String details;
        @Builder.Default
        List<HaFlowEventAction> eventActions = new ArrayList<>();
        @Builder.Default
        List<HaFlowEventDump> eventDumps = new ArrayList<>();
    }

    @Mapper(collectionMappingStrategy = CollectionMappingStrategy.TARGET_IMMUTABLE)
    public interface HaFlowEventCloner {
        HaFlowEventCloner INSTANCE = Mappers.getMapper(HaFlowEventCloner.class);

        @Mapping(target = "eventActions", ignore = true)
        @Mapping(target = "eventDumps", ignore = true)
        void copyWithoutRecordsAndDumps(HaFlowEventData source, @MappingTarget HaFlowEventData target);

        /**
         * Deep copy of the entity.
         * @param source source
         * @return target
         */
        default HaFlowEventData deepCopy(HaFlowEventData source) {
            HaFlowEventDataImpl result = new HaFlowEventDataImpl();
            copyWithoutRecordsAndDumps(source, result);
            if (source.getEventActions() != null) {
                result.setEventActions(source.getEventActions().stream()
                        .map(HaFlowEventAction::new)
                        .collect(Collectors.toList()));
            }
            if (source.getEventDumps() != null) {
                result.setEventDumps(source.getEventDumps().stream()
                        .map(HaFlowEventDump::new)
                        .collect(Collectors.toList()));
            }
            return result;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        HaFlowEvent that = (HaFlowEvent) o;
        return new EqualsBuilder()
                .append(getHaFlowId(), that.getHaFlowId())
                .append(getTimestamp(), that.getTimestamp())
                .append(getActor(), that.getActor())
                .append(getAction(), that.getAction())
                .append(getTaskId(), that.getTaskId())
                .append(getDetails(), that.getDetails())
                .isEquals();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getHaFlowId(), getTimestamp(), getActor(), getAction(), getTaskId(),
                getDetails());
    }
}
