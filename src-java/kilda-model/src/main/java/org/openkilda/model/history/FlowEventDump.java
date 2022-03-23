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

package org.openkilda.model.history;

import org.openkilda.model.CompositeDataEntity;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;

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
import java.util.Objects;

/**
 * Represents information about the flow state.
 */
@DefaultSerializer(BeanSerializer.class)
@ToString
public class FlowEventDump implements CompositeDataEntity<FlowEventDump.FlowEventDumpData> {
    @Getter
    @Setter
    @Delegate
    @JsonIgnore
    private FlowEventDumpData data;

    /**
     * No args constructor for deserialization purpose.
     */
    public FlowEventDump() {
        data = new FlowEventDumpDataImpl();
    }

    /**
     * Cloning constructor which performs deep copy of the entity.
     *
     * @param entityToClone the entity to copy entity data from.
     */
    public FlowEventDump(@NonNull FlowEventDump entityToClone) {
        data = FlowEventDumpCloner.INSTANCE.deepCopy(entityToClone.getData());
    }

    public FlowEventDump(@NonNull FlowEventDump.FlowEventDumpData data) {
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
        FlowEventDump that = (FlowEventDump) o;
        return new EqualsBuilder()
                .append(getTaskId(), that.getTaskId())
                .append(getFlowId(), that.getFlowId())
                .append(getType(), that.getType())
                .append(getBandwidth(), that.getBandwidth())
                .append(isIgnoreBandwidth(), that.isIgnoreBandwidth())
                .append(getForwardCookie(), that.getForwardCookie())
                .append(getReverseCookie(), that.getReverseCookie())
                .append(getSourceSwitch(), that.getSourceSwitch())
                .append(getDestinationSwitch(), that.getDestinationSwitch())
                .append(getSourcePort(), that.getSourcePort())
                .append(getDestinationPort(), that.getDestinationPort())
                .append(getSourceVlan(), that.getSourceVlan())
                .append(getDestinationVlan(), that.getDestinationVlan())
                .append(getSourceInnerVlan(), that.getSourceInnerVlan())
                .append(getDestinationInnerVlan(), that.getDestinationInnerVlan())
                .append(getForwardMeterId(), that.getForwardMeterId())
                .append(getReverseMeterId(), that.getReverseMeterId())
                .append(getDiverseGroupId(), that.getDiverseGroupId())
                .append(getAffinityGroupId(), that.getAffinityGroupId())
                .append(getForwardPath(), that.getForwardPath())
                .append(getReversePath(), that.getReversePath())
                .append(getForwardStatus(), that.getForwardStatus())
                .append(getReverseStatus(), that.getReverseStatus())
                .append(isAllocateProtectedPath(), that.isAllocateProtectedPath())
                .append(isPinned(), that.isPinned())
                .append(isPeriodicPings(), that.isPeriodicPings())
                .append(getEncapsulationType(), that.getEncapsulationType())
                .append(getPathComputationStrategy(), that.getPathComputationStrategy())
                .append(getMaxLatency(), that.getMaxLatency())
                .append(getLoopSwitchId(), that.getLoopSwitchId())
                .isEquals();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTaskId(), getFlowId(), getType(), getBandwidth(), isIgnoreBandwidth(),
                getForwardCookie(), getReverseCookie(), getSourceSwitch(), getDestinationSwitch(),
                getSourcePort(), getDestinationPort(), getSourceVlan(), getDestinationVlan(),
                getSourceInnerVlan(), getDestinationInnerVlan(), getForwardMeterId(), getReverseMeterId(),
                getDiverseGroupId(), getAffinityGroupId(), getForwardPath(), getReversePath(), getForwardStatus(),
                getReverseStatus(), isAllocateProtectedPath(), isPinned(), isPeriodicPings(), getEncapsulationType(),
                getPathComputationStrategy(), getMaxLatency(), getLoopSwitchId());
    }

    /**
     * Defines persistable data of the FlowDump.
     */
    public interface FlowEventDumpData {
        String getTaskId();

        void setTaskId(String taskId);

        String getFlowId();

        void setFlowId(String flowId);

        String getType();

        void setType(String type);

        long getBandwidth();

        void setBandwidth(long bandwidth);

        boolean isIgnoreBandwidth();

        void setIgnoreBandwidth(boolean ignoreBandwidth);

        FlowSegmentCookie getForwardCookie();

        void setForwardCookie(FlowSegmentCookie forwardCookie);

        FlowSegmentCookie getReverseCookie();

        void setReverseCookie(FlowSegmentCookie reverseCookie);

        SwitchId getSourceSwitch();

        void setSourceSwitch(SwitchId sourceSwitch);

        SwitchId getDestinationSwitch();

        void setDestinationSwitch(SwitchId destinationSwitch);

        int getSourcePort();

        void setSourcePort(int sourcePort);

        int getDestinationPort();

        void setDestinationPort(int destinationPort);

        int getSourceVlan();

        void setSourceVlan(int sourceVlan);

        int getDestinationVlan();

        void setDestinationVlan(int destinationVlan);

        Integer getSourceInnerVlan();

        void setSourceInnerVlan(Integer sourceInnerVlan);

        Integer getDestinationInnerVlan();

        void setDestinationInnerVlan(Integer destinationInnerVlan);

        MeterId getForwardMeterId();

        void setForwardMeterId(MeterId forwardMeterId);

        MeterId getReverseMeterId();

        void setReverseMeterId(MeterId reverseMeterId);

        String getDiverseGroupId();

        void setDiverseGroupId(String diverseGroupId);

        String getAffinityGroupId();

        void setAffinityGroupId(String affinityGroupId);

        String getForwardPath();

        void setForwardPath(String forwardPath);

        String getReversePath();

        void setReversePath(String reversePath);

        FlowPathStatus getForwardStatus();

        void setForwardStatus(FlowPathStatus forwardStatus);

        FlowPathStatus getReverseStatus();

        void setReverseStatus(FlowPathStatus reverseStatus);

        Boolean isAllocateProtectedPath();

        void setAllocateProtectedPath(Boolean allocateProtectedPath);

        Boolean isPinned();

        void setPinned(Boolean pinned);

        Boolean isPeriodicPings();

        void setPeriodicPings(Boolean periodicPings);

        FlowEncapsulationType getEncapsulationType();

        void setEncapsulationType(FlowEncapsulationType encapsulationType);

        PathComputationStrategy getPathComputationStrategy();

        void setPathComputationStrategy(PathComputationStrategy pathComputationStrategy);

        Long getMaxLatency();

        void setMaxLatency(Long maxLatency);

        SwitchId getLoopSwitchId();

        void setLoopSwitchId(SwitchId switchId);
    }

    /**
     * POJO implementation of FlowDumpData.
     */
    @Data
    @NoArgsConstructor
    static final class FlowEventDumpDataImpl implements FlowEventDumpData, Serializable {
        private static final long serialVersionUID = 1L;
        String taskId;
        String flowId;
        String type;
        long bandwidth;
        boolean ignoreBandwidth;
        FlowSegmentCookie forwardCookie;
        FlowSegmentCookie reverseCookie;
        SwitchId sourceSwitch;
        SwitchId destinationSwitch;
        int sourcePort;
        int destinationPort;
        int sourceVlan;
        int destinationVlan;
        Integer sourceInnerVlan;
        Integer destinationInnerVlan;
        MeterId forwardMeterId;
        MeterId reverseMeterId;
        String diverseGroupId;
        String affinityGroupId;
        String forwardPath;
        String reversePath;
        FlowPathStatus forwardStatus;
        FlowPathStatus reverseStatus;
        Boolean allocateProtectedPath;
        Boolean pinned;
        Boolean periodicPings;
        FlowEncapsulationType encapsulationType;
        PathComputationStrategy pathComputationStrategy;
        Long maxLatency;
        SwitchId loopSwitchId;

        @Override
        public Boolean isAllocateProtectedPath() {
            return getAllocateProtectedPath();
        }

        @Override
        public Boolean isPinned() {
            return getPinned();
        }

        @Override
        public Boolean isPeriodicPings() {
            return getPeriodicPings();
        }
    }

    @Mapper
    public interface FlowEventDumpCloner {
        FlowEventDumpCloner INSTANCE = Mappers.getMapper(FlowEventDumpCloner.class);

        void copy(FlowEventDumpData source, @MappingTarget FlowEventDumpData target);

        /**
         * Performs deep copy of entity data.
         */
        default FlowEventDumpData deepCopy(FlowEventDumpData source) {
            FlowEventDumpData result = new FlowEventDumpDataImpl();
            copy(source, result);
            return result;
        }
    }
}
