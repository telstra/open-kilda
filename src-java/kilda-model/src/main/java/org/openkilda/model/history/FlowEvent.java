/* Copyright 2019 Telstra Open Source
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

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.BeanSerializer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
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

/**
 * Represents information about the flow event.
 */
@DefaultSerializer(BeanSerializer.class)
@ToString
public class FlowEvent implements CompositeDataEntity<FlowEvent.FlowEventData> {
    public static final List<String> FLOW_STATUS_ACTION_PARTS =
            Lists.newArrayList("The flow status was set to ", "The flow status was reverted to ");
    public static final String FLOW_DELETED_ACTION = "Flow was deleted successfully";

    @Getter
    @Setter
    @Delegate
    @JsonIgnore
    private FlowEventData data;

    /**
     * No args constructor for deserialization purpose.
     */
    public FlowEvent() {
        data = new FlowEventDataImpl();
    }

    /**
     * Cloning constructor which performs deep copy of the entity.
     *
     * @param entityToClone the entity to copy entity data from.
     */
    public FlowEvent(@NonNull FlowEvent entityToClone) {
        data = FlowEventCloner.INSTANCE.deepCopy(entityToClone.getData());
    }

    @Builder
    public FlowEvent(String flowId, Instant timestamp, String actor, String action,
                     String taskId, String details) {
        this.data = FlowEventDataImpl.builder().flowId(flowId).timestamp(timestamp)
                .actor(actor).action(action).taskId(taskId).details(details).build();
    }

    public FlowEvent(@NonNull FlowEventData data) {
        this.data = data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FlowEvent that = (FlowEvent) o;
        return new EqualsBuilder()
                .append(getFlowId(), that.getFlowId())
                .append(getTimestamp(), that.getTimestamp())
                .append(getActor(), that.getActor())
                .append(getAction(), that.getAction())
                .append(getTaskId(), that.getTaskId())
                .append(getDetails(), that.getDetails())
                .isEquals();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getFlowId(), getTimestamp(), getActor(), getAction(), getTaskId(),
                getDetails());
    }

    /**
     * Defines persistable data of the FlowEvent.
     */
    public interface FlowEventData extends GenericFlowEventData<FlowEventDump, FlowEventAction> {
        String getFlowId();

        void setFlowId(String flowId);
    }

    /**
     * POJO implementation of FlowEventData.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static final class FlowEventDataImpl implements FlowEventData, Serializable {
        private static final long serialVersionUID = 1L;
        String flowId;
        Instant timestamp;
        String actor;
        String action;
        String taskId;
        String details;
        @Builder.Default
        List<FlowEventAction> eventActions = new ArrayList<>();
        @Builder.Default
        List<FlowEventDump> eventDumps = new ArrayList<>();
    }

    @Mapper(collectionMappingStrategy = CollectionMappingStrategy.TARGET_IMMUTABLE)
    public interface FlowEventCloner {
        FlowEventCloner INSTANCE = Mappers.getMapper(FlowEventCloner.class);

        @Mapping(target = "eventActions", ignore = true)
        @Mapping(target = "eventDumps", ignore = true)
        void copyWithoutRecordsAndDumps(FlowEventData source, @MappingTarget FlowEventData target);

        /**
         * Performs deep copy of entity data.
         */
        default FlowEventData deepCopy(FlowEventData source) {
            FlowEventDataImpl result = new FlowEventDataImpl();
            copyWithoutRecordsAndDumps(source, result);
            result.setEventActions(source.getEventActions().stream()
                    .map(FlowEventAction::new)
                    .collect(Collectors.toList()));
            result.setEventDumps(source.getEventDumps().stream()
                    .map(FlowEventDump::new)
                    .collect(Collectors.toList()));
            return result;
        }
    }
}
