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
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.history.HaFlowEventDump.HaFlowEventDumpData;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Delegate;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

import java.time.Instant;

public class HaFlowEventDump implements CompositeDataEntity<HaFlowEventDumpData> {

    @Getter
    @Setter
    @Delegate
    @JsonIgnore
    private HaFlowEventDumpData data;

    public HaFlowEventDump() {
        this.data = new HaFlowEventDumpDataImpl();
    }

    public HaFlowEventDump(HaFlowEventDumpData data) {
        this.data = data;
    }

    public HaFlowEventDump(HaFlowEventDump entityToClone) {
        data = HaFlowEventDumpCloner.INSTANCE.deepCopy(entityToClone.getData());
    }

    public interface HaFlowEventDumpData {
        String getTaskId();

        void setTaskId(String taskId);

        DumpType getDumpType();

        void setDumpType(DumpType type);

        String getHaFlowId();

        void setHaFlowId(String haFlowId);

        SwitchId getSharedSwitchId();

        void setSharedSwitchId(SwitchId switchId);

        int getSharedPort();

        void setSharedPort(int port);

        int getSharedOuterVlan();

        void setSharedOuterVlan(int outerVlan);

        int getSharedInnerVlan();

        void setSharedInnerVlan(int innerVlan);

        long getMaximumBandwidth();

        void setMaximumBandwidth(long maximumBandwidth);

        PathComputationStrategy getPathComputationStrategy();

        void setPathComputationStrategy(PathComputationStrategy pathComputationStrategy);

        FlowEncapsulationType getEncapsulationType();

        void setEncapsulationType(FlowEncapsulationType encapsulationType);

        Long getMaxLatency();

        void setMaxLatency(Long maxLatency);

        Long getMaxLatencyTier2();

        void setMaxLatencyTier2(Long maxLatencyTier2);

        boolean isIgnoreBandwidth();

        void setIgnoreBandwidth(boolean ignoreBandwidth);

        boolean isPeriodicPings();

        void setPeriodicPings(boolean periodicPings);

        boolean isPinned();

        void setPinned(boolean pinned);

        Integer getPriority();

        void setPriority(Integer priority);

        boolean isStrictBandwidth();

        void setStrictBandwidth(boolean strictBandwidth);

        String getDescription();

        void setDescription(String description);

        boolean isAllocateProtectedPath();

        void setAllocateProtectedPath(boolean allocateProtectedPath);

        String getDiverseGroupId();

        void setDiverseGroupId(String diverseGroupId);

        String getAffinityGroupId();

        void setAffinityGroupId(String affinityGroupId);

        PathId getForwardPathId();

        void setForwardPathId(PathId forwardPathId);

        PathId getReversePathId();

        void setReversePathId(PathId reversePathId);

        PathId getProtectedForwardPathId();

        void setProtectedForwardPathId(PathId protectedForwardPathId);

        PathId getProtectedReversePathId();

        void setProtectedReversePathId(PathId protectedReversePathId);

        String getPaths();

        void setPaths(String paths);

        String getHaSubFlows();

        void setHaSubFlows(String haSubFlows);

        FlowStatus getStatus();

        void setStatus(FlowStatus status);

        Instant getFlowTimeCreate();

        void setFlowTimeCreate(Instant timeCreate);

        Instant getFlowTimeModify();

        void setFlowTimeModify(Instant timeModify);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HaFlowEventDumpDataImpl implements HaFlowEventDumpData {
        String taskId;
        DumpType dumpType;
        String haFlowId;
        SwitchId sharedSwitchId;
        int sharedPort;
        int sharedOuterVlan;
        int sharedInnerVlan;
        long maximumBandwidth;
        PathComputationStrategy pathComputationStrategy;
        FlowEncapsulationType encapsulationType;
        Long maxLatency;
        Long maxLatencyTier2;
        boolean ignoreBandwidth;
        boolean periodicPings;
        boolean pinned;
        Integer priority;
        boolean strictBandwidth;
        String description;
        boolean allocateProtectedPath;
        String diverseGroupId;
        String affinityGroupId;
        PathId forwardPathId;
        PathId reversePathId;
        PathId protectedForwardPathId;
        PathId protectedReversePathId;
        String paths;
        String haSubFlows;
        FlowStatus status;
        Instant flowTimeCreate;
        Instant flowTimeModify;
    }

    @Mapper
    public interface HaFlowEventDumpCloner {
        HaFlowEventDumpCloner INSTANCE = Mappers.getMapper(HaFlowEventDumpCloner.class);

        void copy(HaFlowEventDumpData source, @MappingTarget HaFlowEventDumpData target);

        /**
         * Performs deep copy of entity data.
         */
        default HaFlowEventDumpData deepCopy(HaFlowEventDumpData source) {
            HaFlowEventDumpData result = new HaFlowEventDumpDataImpl();
            copy(source, result);
            return result;
        }
    }
}
