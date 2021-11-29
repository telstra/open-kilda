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

package org.openkilda.model;

import org.openkilda.model.YSubFlow.YSubFlowData;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.BeanSerializer;
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
public class YSubFlow implements CompositeDataEntity<YSubFlowData> {
    @Getter
    @Setter
    @Delegate
    @JsonIgnore
    private YSubFlowData data;

    /**
     * No args constructor for deserialization purpose.
     */
    private YSubFlow() {
        data = new YSubFlowDataImpl();
    }

    /**
     * Cloning constructor which performs deep copy of the entity.
     *
     * @param entityToClone the sub-flow entity to copy entity data from.
     */
    public YSubFlow(@NonNull YSubFlow entityToClone, YFlow yFlow) {
        data = YSubFlowCloner.INSTANCE.deepCopy(entityToClone.getData(), yFlow);
    }

    public YSubFlow(@NonNull YSubFlow.YSubFlowData data) {
        this.data = data;
    }


    @Builder
    public YSubFlow(@NonNull YFlow yFlow, @NonNull Flow flow, int sharedEndpointVlan, int sharedEndpointInnerVlan,
                    SwitchId endpointSwitchId, int endpointPort, int endpointVlan, int endpointInnerVlan) {
        YSubFlowDataImpl.YSubFlowDataImplBuilder builder = YSubFlowDataImpl.builder()
                .yFlow(yFlow).flow(flow).sharedEndpointVlan(sharedEndpointVlan)
                .sharedEndpointInnerVlan(sharedEndpointInnerVlan)
                .endpointSwitchId(endpointSwitchId).endpointPort(endpointPort).endpointVlan(endpointVlan)
                .endpointInnerVlan(endpointInnerVlan);
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
        YSubFlow that = (YSubFlow) o;
        return new EqualsBuilder()
                .append(getYFlowId(), that.getYFlowId())
                .append(getSubFlowId(), that.getSubFlowId())
                .append(getSharedEndpointVlan(), that.getSharedEndpointVlan())
                .append(getSharedEndpointInnerVlan(), that.getSharedEndpointInnerVlan())
                .append(getEndpointSwitchId(), that.getEndpointSwitchId())
                .append(getEndpointPort(), that.getEndpointPort())
                .append(getEndpointVlan(), that.getEndpointVlan())
                .append(getEndpointInnerVlan(), that.getEndpointInnerVlan())
                .append(getTimeCreate(), that.getTimeCreate())
                .append(getTimeModify(), that.getTimeModify())
                .isEquals();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getYFlowId(), getSubFlowId(), getSharedEndpointVlan(), getSharedEndpointInnerVlan(),
                getEndpointSwitchId(), getEndpointPort(), getEndpointVlan(), getEndpointInnerVlan(),
                getTimeCreate(), getTimeModify());
    }

    /**
     * Defines persistable data of the sub-flow.
     */
    public interface YSubFlowData {
        String getYFlowId();

        YFlow getYFlow();

        void setYFlow(YFlow yFlow);

        String getSubFlowId();

        Flow getFlow();

        void setFlow(Flow flow);

        int getSharedEndpointVlan();

        void setSharedEndpointVlan(int sharedEndpointVlan);

        int getSharedEndpointInnerVlan();

        void setSharedEndpointInnerVlan(int sharedEndpointInnerVlan);

        SwitchId getEndpointSwitchId();

        void setEndpointSwitchId(SwitchId endpointSwitchId);

        int getEndpointPort();

        void setEndpointPort(int endpointPort);

        int getEndpointVlan();

        void setEndpointVlan(int endpointVlan);

        int getEndpointInnerVlan();

        void setEndpointInnerVlan(int endpointInnerVlan);

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
    static final class YSubFlowDataImpl implements YSubFlowData, Serializable {
        private static final long serialVersionUID = 1L;
        @NonNull YFlow yFlow;
        @NonNull Flow flow;
        int sharedEndpointVlan;
        int sharedEndpointInnerVlan;

        SwitchId endpointSwitchId;
        int endpointPort;
        int endpointVlan;
        int endpointInnerVlan;

        Instant timeCreate;
        Instant timeModify;

        @Override
        public String getYFlowId() {
            return yFlow.getYFlowId();
        }

        @Override
        public String getSubFlowId() {
            return flow.getFlowId();
        }
    }

    /**
     * A cloner for sub-flow entity.
     */
    @Mapper
    public interface YSubFlowCloner {
        YSubFlowCloner INSTANCE = Mappers.getMapper(YSubFlowCloner.class);

        @Mapping(target = "YFlow", ignore = true)
        @Mapping(target = "flow", ignore = true)
        void copyWithoutFlows(YSubFlowData source, @MappingTarget YSubFlowData target);

        /**
         * Performs deep copy of entity data.
         */
        default YSubFlowData deepCopy(YSubFlowData source, YFlow targetFlow) {
            YSubFlowDataImpl result = new YSubFlowDataImpl();
            copyWithoutFlows(source, result);
            result.setYFlow(targetFlow);
            result.setFlow(new Flow(source.getFlow()));
            return result;
        }
    }
}
