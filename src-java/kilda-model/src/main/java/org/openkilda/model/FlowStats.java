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

package org.openkilda.model;

import org.openkilda.model.FlowStats.FlowStatsData;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.BeanSerializer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
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
import java.util.Objects;

/**
 * Represents flow stats.
 */
@DefaultSerializer(BeanSerializer.class)
@ToString
public class FlowStats implements CompositeDataEntity<FlowStatsData> {

    public static FlowStats EMPTY = new FlowStats();

    @Getter
    @Setter
    @Delegate
    @JsonIgnore
    private FlowStatsData data;

    /**
     * No args constructor for deserialization purpose.
     */
    private FlowStats() {
        data = new FlowStatsDataImpl();
    }

    /**
     * Cloning constructor which performs deep copy of the entity.
     *
     * @param entityToClone the entity to copy entity data from.
     */
    public FlowStats(@NonNull FlowStats entityToClone) {
        data = FlowStatsCloner.INSTANCE.deepCopy(entityToClone.getData());
    }

    @Builder
    public FlowStats(Flow flowObj, Long forwardLatency, Long reverseLatency) {
        this.data = FlowStatsDataImpl.builder().flowObj(flowObj)
                .forwardLatency(forwardLatency).reverseLatency(reverseLatency).build();
    }

    public FlowStats(@NonNull FlowStats.FlowStatsData data) {
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
        FlowStats that = (FlowStats) o;
        return new EqualsBuilder()
                .append(getFlowId(), that.getFlowId())
                .append(getForwardLatency(), that.getForwardLatency())
                .append(getReverseLatency(), that.getReverseLatency())
                .isEquals();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getFlowId(), getForwardLatency(), getReverseLatency());
    }

    /**
     * Defines persistable data of the FlowStats.
     */
    public interface FlowStatsData {
        String getFlowId();

        Flow getFlowObj();

        void setFlowObj(Flow flowObj);

        Long getForwardLatency();

        void setForwardLatency(Long latency);

        Long getReverseLatency();

        void setReverseLatency(Long latency);

        Instant getTimeModify();

        void setTimeModify(Instant timeModify);
    }

    /**
     * POJO implementation of FlowStatsData.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static final class FlowStatsDataImpl implements FlowStatsData, Serializable {
        private static final long serialVersionUID = 1L;
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        Flow flowObj;
        Long forwardLatency;
        Long reverseLatency;

        Instant timeModify;

        @Override
        public String getFlowId() {
            return flowObj.getFlowId();
        }
    }

    /**
     * A cloner for FlowStats entity.
     */
    @Mapper(collectionMappingStrategy = CollectionMappingStrategy.TARGET_IMMUTABLE)
    public interface FlowStatsCloner {
        FlowStatsCloner INSTANCE = Mappers.getMapper(FlowStatsCloner.class);

        void copy(FlowStatsData source, @MappingTarget FlowStatsData target);

        @Mapping(target = "flowObj", ignore = true)
        void copyWithoutFlow(FlowStatsData source, @MappingTarget FlowStatsData target);

        /**
         * Performs deep copy of entity data.
         */
        default FlowStatsData deepCopy(FlowStatsData source) {
            FlowStatsData result = new FlowStatsDataImpl();
            result.setFlowObj(new Flow(source.getFlowObj()));
            copyWithoutFlow(source, result);
            return result;
        }
    }
}
