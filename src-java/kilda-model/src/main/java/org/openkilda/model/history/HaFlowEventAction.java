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
import org.openkilda.model.history.HaFlowEventAction.HaFlowEventActionData;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@DefaultSerializer
@ToString
public class HaFlowEventAction implements CompositeDataEntity<HaFlowEventActionData> {

    @Getter
    @Setter
    @Delegate
    @JsonIgnore
    private HaFlowEventActionData data;

    public HaFlowEventAction(@NonNull HaFlowEventAction entityToClone) {
        data = HaFlowEventActionCloner.INSTANCE.deepCopy(entityToClone.getData());
    }

    public HaFlowEventAction() {
        data = new HaFlowEventActionDataImpl();
    }

    public HaFlowEventAction(@NonNull HaFlowEventActionData data) {
        this.data = data;
    }

    public interface HaFlowEventActionData {
        Instant getTimestamp();

        void setTimestamp(Instant timestamp);

        String getAction();

        void setAction(String action);

        String getTaskId();

        void setTaskId(String taskId);

        String getDetails();

        void setDetails(String details);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        HaFlowEventAction that = (HaFlowEventAction) o;
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HaFlowEventActionDataImpl implements HaFlowEventActionData, Serializable {
        private static final long serialVersionUID = 1L;
        Instant timestamp;
        String action;
        String taskId;
        String details;
    }

    @Mapper
    public interface HaFlowEventActionCloner {
        HaFlowEventActionCloner INSTANCE = Mappers.getMapper(HaFlowEventActionCloner.class);

        void copy(HaFlowEventActionData source, @MappingTarget HaFlowEventActionData target);

        /**
         * This method is used when cloning a persistence object.
         * @param source an object to copy
         * @return a copy of the source object
         */
        default HaFlowEventActionData deepCopy(HaFlowEventActionData source) {
            HaFlowEventActionData result = new HaFlowEventActionDataImpl();
            copy(source, result);
            return result;
        }
    }
}
