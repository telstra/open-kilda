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

package org.openkilda.model;

import org.openkilda.model.HaSubFlowEdge.HaSubFlowEdgeData;

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
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents a sub-flow of Y-flow.
 */
@DefaultSerializer(BeanSerializer.class)
@ToString
public class HaSubFlowEdge implements CompositeDataEntity<HaSubFlowEdgeData> {
    @Getter
    @Setter
    @Delegate
    @JsonIgnore
    private HaSubFlowEdgeData data;

    /**
     * No args constructor for deserialization purpose.
     */
    private HaSubFlowEdge() {
        data = new HaSubFlowEdgeImpl();
    }

    /**
     * Cloning constructor which performs deep copy of the entity.
     *
     * @param entityToClone the sub-flow entity to copy entity data from.
     */
    public HaSubFlowEdge(@NonNull HaSubFlowEdge entityToClone, HaFlowPath haFlowPath, HaSubFlow haSubFlow) {
        data = HaSubFlowEdgeCloner.INSTANCE.deepCopy(entityToClone.getData(), haFlowPath, haSubFlow);
    }

    public HaSubFlowEdge(@NonNull HaSubFlowEdge.HaSubFlowEdgeData data) {
        this.data = data;
    }


    @Builder
    public HaSubFlowEdge(@NonNull String haFlowId, @NonNull HaSubFlow haSubFlow, MeterId meterId) {
        HaSubFlowEdgeImpl.HaSubFlowEdgeImplBuilder builder = HaSubFlowEdgeImpl.builder()
                .haFlowId(haFlowId).haSubFlow(haSubFlow).meterId(meterId);
        this.data = builder.build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        HaSubFlowEdge that = (HaSubFlowEdge) o;
        return new EqualsBuilder()
                .append(getHaFlowId(), that.getHaFlowId())
                .append(getHaSubFlowId(), that.getHaSubFlowId())
                .append(getHaFlowPathId(), that.getHaFlowPathId())
                .append(getMeterId(), that.getMeterId())
                .append(getTimeCreate(), that.getTimeCreate())
                .append(getTimeModify(), that.getTimeModify())
                .isEquals();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getHaFlowId(), getHaSubFlowId(), getHaFlowPathId(), getMeterId(), getTimeCreate(),
                getTimeModify());
    }

    /**
     * Defines persistable data of the sub-flow.
     */
    public interface HaSubFlowEdgeData {
        String getHaFlowId();

        String getHaSubFlowId();

        HaSubFlow getHaSubFlow();

        void setHaSubFlow(HaSubFlow haSubFlow);

        SwitchId getSubFlowEndpointSwitchId();

        PathId getHaFlowPathId();

        HaFlowPath getHaFlowPath();

        void setHaFlowPath(HaFlowPath flow);

        MeterId getMeterId();

        void setMeterId(MeterId meterId);

        Instant getTimeCreate();

        void setTimeCreate(Instant timeCreate);

        Instant getTimeModify();

        void setTimeModify(Instant timeModify);
    }

    /**
     * POJO implementation of YSubFlowData.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static final class HaSubFlowEdgeImpl implements HaSubFlowEdgeData, Serializable {
        private static final long serialVersionUID = 1L;
        @NonNull String haFlowId;
        @NonNull HaSubFlow haSubFlow;
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        HaFlowPath haFlowPath;

        MeterId meterId;

        Instant timeCreate;
        Instant timeModify;

        @Override
        public String getHaSubFlowId() {
            return haSubFlow.getHaSubFlowId();
        }

        @Override
        public PathId getHaFlowPathId() {
            return haFlowPath == null ? null : haFlowPath.getHaPathId();
        }

        @Override
        public SwitchId getSubFlowEndpointSwitchId() {
            return haSubFlow.getEndpointSwitchId();
        }
    }

    /**
     * A cloner for sub-flow entity.
     */
    @Mapper
    public interface HaSubFlowEdgeCloner {
        HaSubFlowEdgeCloner INSTANCE = Mappers.getMapper(HaSubFlowEdgeCloner.class);

        @Mapping(target = "haSubFlow", ignore = true)
        @Mapping(target = "haFlowPath", ignore = true)
        void copyWithoutFlows(HaSubFlowEdgeData source, @MappingTarget HaSubFlowEdgeData target);

        /**
         * Performs deep copy of entity data.
         */
        default HaSubFlowEdgeData deepCopy(HaSubFlowEdgeData source, HaFlowPath targetPath, HaSubFlow targetHaSubFlow) {
            HaSubFlowEdgeImpl result = new HaSubFlowEdgeImpl();
            copyWithoutFlows(source, result);
            result.setHaFlowPath(targetPath);
            result.setHaSubFlow(targetHaSubFlow);
            return result;
        }
    }
}
