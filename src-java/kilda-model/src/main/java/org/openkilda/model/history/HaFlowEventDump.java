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
import org.openkilda.model.history.HaFlowEventDump.HaFlowEventDumpData;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.Delegate;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        DumpType getDumpType();

        void setDumpType(DumpType dumpType);

        String getTaskId();

        void setTaskId(String taskId);

        String getHaFlowId();

        void setHaFlowId(String haFlowId);

        String getAffinityGroupId();

        void setAffinityGroupId(String affinityGroupId);

        Boolean getAllocateProtectedPath();

        void setAllocateProtectedPath(Boolean allocateProtectedPath);

        String getDescription();

        void setDescription(String description);

        String getDiverseGroupId();

        void setDiverseGroupId(String diverseGroupId);

        FlowEncapsulationType getEncapsulationType();

        void setEncapsulationType(FlowEncapsulationType encapsulationType);

        String getFlowTimeCreate();

        void setFlowTimeCreate(String flowTimeCreate);

        String getFlowTimeModify();

        void setFlowTimeModify(String flowTimeModify);

        HaSubFlowDumpWrapper getHaSubFlows();

        void setHaSubFlows(HaSubFlowDumpWrapper haSubFlows);

        Boolean getIgnoreBandwidth();

        void setIgnoreBandwidth(Boolean ignoreBandwidth);

        Long getMaxLatency();

        void setMaxLatency(Long maxLatency);

        Long getMaxLatencyTier2();

        void setMaxLatencyTier2(Long maxLatencyTier2);

        Long getMaximumBandwidth();

        void setMaximumBandwidth(Long maximumBandwidth);

        PathComputationStrategy getPathComputationStrategy();

        void setPathComputationStrategy(PathComputationStrategy pathComputationStrategy);

        Boolean getPeriodicPings();

        void setPeriodicPings(Boolean periodicPings);

        Boolean getPinned();

        void setPinned(Boolean pinned);

        Integer getPriority();

        void setPriority(Integer priority);

        Integer getSharedInnerVlan();

        void setSharedInnerVlan(Integer sharedInnerVlan);

        Integer getSharedOuterVlan();

        void setSharedOuterVlan(Integer sharedOuterVlan);

        Integer getSharedPort();

        void setSharedPort(Integer sharedPort);

        String getSharedSwitchId();

        void setSharedSwitchId(String sharedSwitchId);

        FlowStatus getStatus();

        void setStatus(FlowStatus status);

        String getStatusInfo();

        void setStatusInfo(String statusInfo);

        Boolean getStrictBandwidth();

        void setStrictBandwidth(Boolean strictBandwidth);

        HaFlowPathDump getForwardPath();

        void setForwardPath(HaFlowPathDump forwardPath);

        HaFlowPathDump getReversePath();

        void setReversePath(HaFlowPathDump reversePath);

        HaFlowPathDump getProtectedForwardPath();

        void setProtectedForwardPath(HaFlowPathDump protectedForwardPath);

        HaFlowPathDump getProtectedReversePath();

        void setProtectedReversePath(HaFlowPathDump protectedReversePath);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HaFlowEventDumpDataImpl implements HaFlowEventDumpData {
        DumpType dumpType;
        String taskId;

        String haFlowId;

        String affinityGroupId;
        Boolean allocateProtectedPath;
        String description;
        String diverseGroupId;
        FlowEncapsulationType encapsulationType;
        String flowTimeCreate;
        String flowTimeModify;
        HaSubFlowDumpWrapper haSubFlows;
        Boolean ignoreBandwidth;
        Long maxLatency;
        Long maxLatencyTier2;
        Long maximumBandwidth;
        PathComputationStrategy pathComputationStrategy;
        Boolean periodicPings;
        Boolean pinned;
        Integer priority;
        Integer sharedInnerVlan;
        Integer sharedOuterVlan;
        Integer sharedPort;
        String sharedSwitchId;
        FlowStatus status;
        String statusInfo;
        Boolean strictBandwidth;

        HaFlowPathDump forwardPath;
        HaFlowPathDump reversePath;
        HaFlowPathDump protectedForwardPath;
        HaFlowPathDump protectedReversePath;
    }

    @Value
    @Builder
    public static class HaSubFlowDumpWrapper implements Serializable {
        List<HaSubFlowDump> haSubFlowDumpList;

        public List<HaSubFlowDump> getHaSubFlowDumpList() {
            return new ArrayList<>(haSubFlowDumpList);
        }

        /**
         * Creates an empty object with initialized collections.
         * @return an empty HaSubFlowDumpWrapper
         */
        public static HaSubFlowDumpWrapper empty() {
            return HaSubFlowDumpWrapper.builder()
                    .haSubFlowDumpList(Collections.emptyList())
                    .build();
        }
    }

    @Value
    @Builder
    public static class HaSubFlowDump implements Serializable {
        String haFlowId;
        String haSubFlowId;
        FlowStatus status;
        String endpointSwitchId;
        Integer endpointPort;
        Integer endpointVlan;
        Integer endpointInnerVlan;
        String description;
        String timeCreate;
        String timeModify;
    }

    @Data
    @Builder
    public static class HaFlowPathDump implements Serializable {
        String haPathId;
        String yPointSwitchId;
        String cookie;
        String yPointMeterId;
        String sharedPointMeterId;
        String yPointGroupId;
        Long bandwidth;
        Boolean ignoreBandwidth;
        String timeCreate;
        String timeModify;
        String status;
        String sharedSwitchId;
        List<List<PathNodePayload>> paths;

        List<HaSubFlowDump> haSubFlows;
    }
    
    @Value
    @Builder
    public static class PathNodePayload {
        String switchId;
        Integer inputPort;
        Integer outputPort;
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
