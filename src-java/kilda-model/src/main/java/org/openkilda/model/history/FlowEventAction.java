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
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Delegate;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents information about the flow history.
 * The history log always related to particular Flow Event and represents inside Kilda actions caused by the Flow Event.
 */
@DefaultSerializer(BeanSerializer.class)
@ToString
public class FlowEventAction implements CompositeDataEntity<FlowEventAction.FlowEventActionData> {
    @Getter
    @Setter
    @Delegate
    @JsonIgnore
    private FlowEventActionData data;

    /**
     * No args constructor for deserialization purpose.
     */
    public FlowEventAction() {
        data = new FlowEventActionDataImpl();
    }

    /**
     * Cloning constructor which performs deep copy of the entity.
     *
     * @param entityToClone the entity to copy entity data from.
     */
    public FlowEventAction(@NonNull FlowEventAction entityToClone) {
        data = FlowEventActionCloner.INSTANCE.deepCopy(entityToClone.getData());
    }

    public FlowEventAction(@NonNull FlowEventAction.FlowEventActionData data) {
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
        FlowEventAction that = (FlowEventAction) o;
        return new EqualsBuilder()
                .append(getTimestamp(), that.getTimestamp())
                .append(getAction(), that.getAction())
                .append(getTaskId(), that.getTaskId())
                .append(getDetails(), that.getDetails())
                .isEquals();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTimestamp(), getAction(), getTaskId(), getDetails());
    }

    /**
     * Defines persistable data of the FlowHistory.
     */
    public interface FlowEventActionData {
        Instant getTimestamp();

        void setTimestamp(Instant timestamp);

        String getAction();

        void setAction(String action);

        String getTaskId();

        void setTaskId(String taskId);

        String getDetails();

        void setDetails(String details);
    }

    /**
     * POJO implementation of FlowHistoryData.
     */
    @Data
    @NoArgsConstructor
    static final class FlowEventActionDataImpl implements FlowEventActionData, Serializable {
        private static final long serialVersionUID = 1L;
        Instant timestamp;
        String action;
        String taskId;
        String details;
    }

    @Mapper
    public interface FlowEventActionCloner {
        FlowEventActionCloner INSTANCE = Mappers.getMapper(FlowEventActionCloner.class);

        void copy(FlowEventActionData source, @MappingTarget FlowEventActionData target);

        /**
         * Performs deep copy of entity data.
         */
        default FlowEventActionData deepCopy(FlowEventActionData source) {
            FlowEventActionData result = new FlowEventActionDataImpl();
            copy(source, result);
            return result;
        }
    }
}
