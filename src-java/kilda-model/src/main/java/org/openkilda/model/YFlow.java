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

import org.openkilda.model.YFlow.YFlowData;

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
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a Y-flow.
 */
@DefaultSerializer(BeanSerializer.class)
@ToString
public class YFlow implements CompositeDataEntity<YFlowData> {
    @Getter
    @Setter
    @Delegate
    @JsonIgnore
    private YFlowData data;

    /**
     * No args constructor for deserialization purpose.
     */
    private YFlow() {
        data = new YFlowDataImpl();
    }

    /**
     * Cloning constructor which performs deep copy of the entity.
     *
     * @param entityToClone the Y-flow entity to copy entity data from.
     */
    public YFlow(@NonNull YFlow entityToClone) {
        data = YFlowCloner.INSTANCE.deepCopy(entityToClone.getData(), this);
    }

    @Builder
    public YFlow(@NonNull String yFlowId, @NonNull SharedEndpoint sharedEndpoint,
                 boolean allocateProtectedPath, long maximumBandwidth, boolean ignoreBandwidth,
                 boolean strictBandwidth, String description, boolean periodicPings,
                 FlowEncapsulationType encapsulationType, FlowStatus status,
                 Long maxLatency, Long maxLatencyTier2, Integer priority, boolean pinned,
                 PathComputationStrategy pathComputationStrategy, SwitchId yPoint, SwitchId protectedPathYPoint,
                 MeterId meterId, MeterId protectedPathMeterId, MeterId sharedEndpointMeterId) {
        YFlowDataImpl.YFlowDataImplBuilder builder = YFlowDataImpl.builder()
                .yFlowId(yFlowId).sharedEndpoint(sharedEndpoint).allocateProtectedPath(allocateProtectedPath)
                .maximumBandwidth(maximumBandwidth).ignoreBandwidth(ignoreBandwidth).strictBandwidth(strictBandwidth)
                .description(description).periodicPings(periodicPings).encapsulationType(encapsulationType)
                .status(status).maxLatency(maxLatency).maxLatencyTier2(maxLatencyTier2)
                .priority(priority).pinned(pinned).pathComputationStrategy(pathComputationStrategy)
                .yPoint(yPoint).protectedPathYPoint(protectedPathYPoint)
                .meterId(meterId).protectedPathMeterId(protectedPathMeterId)
                .sharedEndpointMeterId(sharedEndpointMeterId);
        data = builder.build();

        // The reference is used to link sub-flows back to the y-flow. See {@link #setSegments(List)}.
        ((YFlowDataImpl) data).yFlow = this;
    }

    public YFlow(@NonNull YFlow.YFlowData data) {
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
        YFlow that = (YFlow) o;
        return new EqualsBuilder()
                .append(getYFlowId(), that.getYFlowId())
                .append(getSharedEndpoint(), that.getSharedEndpoint())
                .append(isAllocateProtectedPath(), that.isAllocateProtectedPath())
                .append(getMaximumBandwidth(), that.getMaximumBandwidth())
                .append(isIgnoreBandwidth(), that.isIgnoreBandwidth())
                .append(isStrictBandwidth(), that.isStrictBandwidth())
                .append(getDescription(), that.getDescription())
                .append(isPeriodicPings(), that.isPeriodicPings())
                .append(getEncapsulationType(), that.getEncapsulationType())
                .append(getStatus(), that.getStatus())
                .append(getMaxLatency(), that.getMaxLatency())
                .append(getMaxLatencyTier2(), that.getMaxLatencyTier2())
                .append(getPriority(), that.getPriority())
                .append(isPinned(), that.isPinned())
                .append(getPathComputationStrategy(), that.getPathComputationStrategy())
                .append(getYPoint(), that.getYPoint())
                .append(getProtectedPathYPoint(), that.getProtectedPathYPoint())
                .append(getMeterId(), that.getMeterId())
                .append(getProtectedPathMeterId(), that.getProtectedPathMeterId())
                .append(getSharedEndpointMeterId(), that.getSharedEndpointMeterId())
                .append(getTimeCreate(), that.getTimeCreate())
                .append(getTimeModify(), that.getTimeModify())
                .append(new HashSet<>(getSubFlows()), new HashSet<>(that.getSubFlows()))
                .isEquals();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getYFlowId(), getSharedEndpoint(), isAllocateProtectedPath(), getMaximumBandwidth(),
                isIgnoreBandwidth(), isStrictBandwidth(), getDescription(), isPeriodicPings(),
                getEncapsulationType(), getStatus(), getMaxLatency(), getMaxLatencyTier2(),
                getPriority(), isPinned(), getPathComputationStrategy(), getYPoint(), getProtectedPathYPoint(),
                getMeterId(), getProtectedPathMeterId(), getSharedEndpointMeterId(),
                getTimeCreate(), getTimeModify(), getSubFlows());
    }

    /**
     * Defines persistable data of the Y-flow.
     */
    public interface YFlowData {
        String getYFlowId();

        void setYFlowId(String yFlowId);

        SharedEndpoint getSharedEndpoint();

        void setSharedEndpoint(SharedEndpoint sharedEndpoint);

        Set<YSubFlow> getSubFlows();

        void setSubFlows(Set<YSubFlow> subFlows);

        void addSubFlow(YSubFlow subFlow);

        void updateSubFlow(YSubFlow subFlow);

        boolean isAllocateProtectedPath();

        void setAllocateProtectedPath(boolean allocateProtectedPath);

        long getMaximumBandwidth();

        void setMaximumBandwidth(long maximumBandwidth);

        boolean isIgnoreBandwidth();

        void setIgnoreBandwidth(boolean ignoreBandwidth);

        boolean isStrictBandwidth();

        void setStrictBandwidth(boolean strictBandwidth);

        String getDescription();

        void setDescription(String description);

        boolean isPeriodicPings();

        void setPeriodicPings(boolean periodicPings);

        FlowEncapsulationType getEncapsulationType();

        void setEncapsulationType(FlowEncapsulationType encapsulationType);

        FlowStatus getStatus();

        void setStatus(FlowStatus status);

        Long getMaxLatency();

        void setMaxLatency(Long maxLatency);

        Long getMaxLatencyTier2();

        void setMaxLatencyTier2(Long maxLatencyTier2);

        Integer getPriority();

        void setPriority(Integer priority);

        Instant getTimeCreate();

        void setTimeCreate(Instant timeCreate);

        Instant getTimeModify();

        void setTimeModify(Instant timeModify);

        boolean isPinned();

        void setPinned(boolean pinned);

        PathComputationStrategy getPathComputationStrategy();

        void setPathComputationStrategy(PathComputationStrategy pathComputationStrategy);

        SwitchId getYPoint();

        void setYPoint(SwitchId yPoint);

        SwitchId getProtectedPathYPoint();

        void setProtectedPathYPoint(SwitchId yPoint);

        MeterId getMeterId();

        void setMeterId(MeterId meterId);

        MeterId getProtectedPathMeterId();

        void setProtectedPathMeterId(MeterId meterId);

        MeterId getSharedEndpointMeterId();

        void setSharedEndpointMeterId(MeterId meterId);
    }

    /**
     * POJO implementation of YFlowData.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static final class YFlowDataImpl implements YFlowData, Serializable {
        private static final long serialVersionUID = 1L;
        @NonNull String yFlowId;
        @NonNull SharedEndpoint sharedEndpoint;
        boolean allocateProtectedPath;
        long maximumBandwidth;
        boolean ignoreBandwidth;
        boolean strictBandwidth;
        String description;
        boolean periodicPings;
        FlowEncapsulationType encapsulationType;
        FlowStatus status;
        Long maxLatency;
        Long maxLatencyTier2;
        Integer priority;
        Instant timeCreate;
        Instant timeModify;
        boolean pinned;
        PathComputationStrategy pathComputationStrategy;
        SwitchId yPoint;
        SwitchId protectedPathYPoint;
        MeterId meterId;
        MeterId protectedPathMeterId;
        MeterId sharedEndpointMeterId;

        @Builder.Default
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        Set<YSubFlow> subFlows = new HashSet<>();

        // The reference is used to link sub-flows back to the y-flow. See {@link #setSubFlows(Set)}.
        @Setter(AccessLevel.NONE)
        @Getter(AccessLevel.NONE)
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        YFlow yFlow;

        @Override
        public Set<YSubFlow> getSubFlows() {
            return Collections.unmodifiableSet(subFlows);
        }

        @Override
        public void setSubFlows(Set<YSubFlow> subFlows) {
            subFlows.forEach(flow -> flow.setYFlow(yFlow));

            for (YSubFlow subFlow : this.subFlows) {
                boolean keepSubFlow = subFlows.stream()
                        .anyMatch(n -> n == subFlow || n.getSubFlowId().equals(subFlow.getSubFlowId()));
                if (!keepSubFlow) {
                    // Invalidate the removed entity as a sub-flow can't exist without a y-flow.
                    subFlow.setData(null);
                }
            }

            this.subFlows = new HashSet<>(subFlows);
        }

        @Override
        public void addSubFlow(YSubFlow subFlow) {
            if (subFlows.stream()
                    .noneMatch(n -> n == subFlow || n.getSubFlowId().equals(subFlow.getSubFlowId()))) {

                subFlow.setYFlow(yFlow);
                subFlows.add(subFlow);
            }
        }

        @Override
        public void updateSubFlow(YSubFlow subFlow) {
            List<YSubFlow> foundSubFlows = subFlows.stream()
                    .filter(n -> n == subFlow || n.getSubFlowId().equals(subFlow.getSubFlowId()))
                    .collect(Collectors.toList());
            for (YSubFlow foundSubFlow : foundSubFlows) {
                subFlows.remove(foundSubFlow);
            }

            subFlow.setYFlow(yFlow);
            subFlows.add(subFlow);
        }
    }

    /**
     * A cloner for Y-flow entity.
     */
    @Mapper(collectionMappingStrategy = CollectionMappingStrategy.TARGET_IMMUTABLE)
    public interface YFlowCloner {
        YFlowCloner INSTANCE = Mappers.getMapper(YFlowCloner.class);

        @Mapping(target = "subFlows", ignore = true)
        void copyWithoutSubFlows(YFlowData source, @MappingTarget YFlowData target);

        /**
         * Performs deep copy of entity data.
         *
         * @param source the y-flow to copy from.
         */
        default YFlowData deepCopy(YFlowData source, YFlow targetFlow) {
            YFlowDataImpl result = new YFlowDataImpl();
            copyWithoutSubFlows(source, result);
            result.setSubFlows(source.getSubFlows().stream()
                    .map(subFlow -> new YSubFlow(subFlow, targetFlow))
                    .collect(Collectors.toSet()));
            return result;
        }
    }

    public static class SharedEndpoint extends NetworkEndpoint {
        public SharedEndpoint(SwitchId switchId, Integer portNumber) {
            super(switchId, portNumber);
        }
    }
}
