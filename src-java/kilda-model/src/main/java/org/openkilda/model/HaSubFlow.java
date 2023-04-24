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

import org.openkilda.model.HaSubFlow.HaSubFlowData;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.BeanSerializer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
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
 * Represents a sub-flow of HA-flow.
 */
@DefaultSerializer(BeanSerializer.class)
@ToString
public class HaSubFlow implements CompositeDataEntity<HaSubFlowData> {
    @Getter
    @Setter
    @Delegate
    @JsonIgnore
    private HaSubFlowData data;

    /**
     * No args constructor for deserialization purpose.
     */
    private HaSubFlow() {
        data = new HaSubFlowDataImpl();
    }

    /**
     * Cloning constructor which performs deep copy of the entity.
     *
     * @param entityToClone the sub-flow entity to copy entity data from.
     */
    public HaSubFlow(@NonNull HaSubFlow entityToClone, HaFlow haFlow) {
        data = HaSubFlowCloner.INSTANCE.deepCopy(entityToClone.getData(), haFlow);
    }

    public HaSubFlow(@NonNull HaSubFlow.HaSubFlowData data) {
        this.data = data;
    }


    @Builder
    public HaSubFlow(@NonNull String haSubFlowId, FlowStatus status, @NonNull Switch endpointSwitch,
                     int endpointPort, int endpointVlan, int endpointInnerVlan, String description) {
        HaSubFlowDataImpl.HaSubFlowDataImplBuilder builder = HaSubFlowDataImpl.builder().haSubFlowId(haSubFlowId)
                .status(status).endpointSwitch(endpointSwitch).endpointPort(endpointPort)
                .endpointVlan(endpointVlan).endpointInnerVlan(endpointInnerVlan).description(description);
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
        HaSubFlow that = (HaSubFlow) o;
        return new EqualsBuilder()
                .append(getHaFlowId(), that.getHaFlowId())
                .append(getHaSubFlowId(), that.getHaSubFlowId())
                .append(getStatus(), that.getStatus())
                .append(getEndpointSwitchId(), that.getEndpointSwitchId())
                .append(getEndpointPort(), that.getEndpointPort())
                .append(getEndpointVlan(), that.getEndpointVlan())
                .append(getEndpointInnerVlan(), that.getEndpointInnerVlan())
                .append(getDescription(), that.getDescription())
                .append(getTimeCreate(), that.getTimeCreate())
                .append(getTimeModify(), that.getTimeModify())
                .isEquals();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getHaFlowId(), getHaSubFlowId(), getStatus(), getEndpointSwitchId(), getEndpointPort(),
                getEndpointVlan(), getEndpointInnerVlan(), getDescription(), getTimeCreate(), getTimeModify());
    }

    /**
     * Defines persistable data of the sub-flow.
     */
    public interface HaSubFlowData {
        String getHaFlowId();

        HaFlow getHaFlow();

        String getHaSubFlowId();

        void setHaSubFlowId(String subFlowId);

        FlowStatus getStatus();

        void setStatus(FlowStatus status);

        SwitchId getEndpointSwitchId();

        Switch getEndpointSwitch();

        void setEndpointSwitch(Switch endpointSwitch);

        int getEndpointPort();

        void setEndpointPort(int endpointPort);

        int getEndpointVlan();

        void setEndpointVlan(int endpointVlan);

        int getEndpointInnerVlan();

        void setEndpointInnerVlan(int endpointInnerVlan);

        String getDescription();

        void setDescription(String description);

        Instant getTimeCreate();

        void setTimeCreate(Instant timeCreate);

        Instant getTimeModify();

        void setTimeModify(Instant timeModify);
    }

    /**
     * POJO implementation of HaSubFlowData.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static final class HaSubFlowDataImpl implements HaSubFlowData, Serializable {
        private static final long serialVersionUID = 1L;
        @Setter(AccessLevel.NONE)
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        HaFlow haFlow;
        @NonNull String haSubFlowId;
        FlowStatus status;

        @NonNull Switch endpointSwitch;
        int endpointPort;
        int endpointVlan;
        int endpointInnerVlan;
        String description;

        Instant timeCreate;
        Instant timeModify;

        @Override
        public String getHaFlowId() {
            return haFlow != null ? haFlow.getHaFlowId() : null;
        }

        @Override
        public SwitchId getEndpointSwitchId() {
            return endpointSwitch.getSwitchId();
        }
    }

    /**
     * A cloner for ha-ub-flow entity.
     */
    @Mapper
    public interface HaSubFlowCloner {
        HaSubFlowCloner INSTANCE = Mappers.getMapper(HaSubFlowCloner.class);

        void copy(HaSubFlowData source, @MappingTarget HaSubFlowData target);

        @Mapping(target = "haFlow", ignore = true)
        @Mapping(target = "endpointSwitch", ignore = true)
        void copyWithoutHaFlowAndSwitch(HaSubFlowData source, @MappingTarget HaSubFlowData target);

        /**
         * Performs deep copy of entity data.
         */
        default HaSubFlowData deepCopy(HaSubFlowData source, HaFlow targetHaFlow) {
            HaSubFlowDataImpl result = new HaSubFlowDataImpl();
            copyWithoutHaFlowAndSwitch(source, result);
            result.setEndpointSwitch(new Switch(source.getEndpointSwitch()));
            result.haFlow = targetHaFlow;
            return result;
        }
    }
}
