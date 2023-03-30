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

import static java.lang.String.format;

import org.openkilda.model.HaFlow.HaFlowData;
import org.openkilda.model.HaFlowPath.HaFlowPathData;
import org.openkilda.model.HaFlowPath.HaFlowPathDataImpl;

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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents HA-flow.
 */
@DefaultSerializer(BeanSerializer.class)
@ToString
public class HaFlow implements CompositeDataEntity<HaFlowData> {
    @Getter
    @Setter
    @Delegate
    @JsonIgnore
    private HaFlowData data;

    /**
     * No args constructor for deserialization purpose.
     */
    private HaFlow() {
        data = new HaFlowDataImpl();
        // The reference is used to link sub-flows back to the HA-flow. See {@link #setSubFlows(Set)}.
        ((HaFlowDataImpl) data).haFlow = this;
    }

    /**
     * Cloning constructor which performs deep copy of the entity.
     *
     * @param entityToClone the HA-flow entity to copy entity data from.
     */
    public HaFlow(@NonNull HaFlow entityToClone) {
        data = HaFlowCloner.INSTANCE.deepCopy(entityToClone.getData(), this);
    }

    @Builder
    public HaFlow(@NonNull String haFlowId, @NonNull Switch sharedSwitch, int sharedPort, int sharedOuterVlan,
                  int sharedInnerVlan, long maximumBandwidth,
                  PathComputationStrategy pathComputationStrategy, FlowEncapsulationType encapsulationType,
                  Long maxLatency, Long maxLatencyTier2, boolean ignoreBandwidth, boolean periodicPings,
                  boolean pinned, Integer priority, boolean strictBandwidth, String description,
                  boolean allocateProtectedPath, FlowStatus status, String affinityGroupId, String diverseGroupId) {
        HaFlowDataImpl.HaFlowDataImplBuilder builder = HaFlowDataImpl.builder()
                .haFlowId(haFlowId).sharedSwitch(sharedSwitch).sharedPort(sharedPort).sharedOuterVlan(sharedOuterVlan)
                .sharedInnerVlan(sharedInnerVlan).maximumBandwidth(maximumBandwidth)
                .pathComputationStrategy(pathComputationStrategy).encapsulationType(encapsulationType)
                .maxLatency(maxLatency).maxLatencyTier2(maxLatencyTier2).ignoreBandwidth(ignoreBandwidth)
                .periodicPings(periodicPings).pinned(pinned).priority(priority).strictBandwidth(strictBandwidth)
                .description(description).allocateProtectedPath(allocateProtectedPath).status(status)
                .affinityGroupId(affinityGroupId).diverseGroupId(diverseGroupId);

        data = builder.build();

        // The reference is used to link sub-flows back to the HA-flow. See {@link #setSubFlows(Set)}.
        ((HaFlowDataImpl) data).haFlow = this;
    }

    public HaFlow(@NonNull HaFlow.HaFlowData data) {
        this.data = data;
    }

    /**
     * Get the forward path.
     */
    public HaFlowPath getForwardPath() {
        if (getForwardPathId() == null) {
            return null;
        }
        return getPath(getForwardPathId()).orElse(null);
    }

    /**
     * Add a path and set it as the forward path.
     */
    public void setForwardPath(HaFlowPath forwardPath) {
        if (forwardPath != null) {
            validateForwardPath(forwardPath);
            if (!hasPath(forwardPath)) {
                addPaths(forwardPath);
            }
            setForwardPathId(forwardPath.getHaPathId());
        } else {
            setForwardPathId(null);
        }
    }

    /**
     * Get the protected forward path.
     */
    public final HaFlowPath getProtectedForwardPath() {
        if (getProtectedForwardPathId() == null) {
            return null;
        }
        return getPath(getProtectedForwardPathId()).orElse(null);
    }

    /**
     * Add a path and set it as the protected forward path.
     */
    public void setProtectedForwardPath(HaFlowPath forwardPath) {
        if (forwardPath != null) {
            validateForwardPath(forwardPath);
            if (!hasPath(forwardPath)) {
                addPaths(forwardPath);
            }
            setProtectedForwardPathId(forwardPath.getHaPathId());
        } else {
            setProtectedForwardPathId(null);
        }
    }

    /**
     * Get the reverse path.
     */
    public HaFlowPath getReversePath() {
        if (getReversePathId() == null) {
            return null;
        }
        return getPath(getReversePathId()).orElse(null);
    }


    /**
     * Add a path and set it as the reverse path.
     */
    public void setReversePath(HaFlowPath reversePath) {
        if (reversePath != null) {
            if (!hasPath(reversePath)) {
                validateReversePath(reversePath);
                addPaths(reversePath);
            }
            setReversePathId(reversePath.getHaPathId());
        } else {
            setReversePathId(null);
        }
    }

    /**
     * Get the protected reverse path.
     */
    public HaFlowPath getProtectedReversePath() {
        if (getProtectedReversePathId() == null) {
            return null;
        }
        return getPath(getProtectedReversePathId()).orElse(null);
    }

    /**
     * Add a path and set it as the protected reverse.
     */
    public void setProtectedReversePath(HaFlowPath reversePath) {
        if (reversePath != null) {
            if (!hasPath(reversePath)) {
                validateReversePath(reversePath);
                addPaths(reversePath);
            }
            setProtectedReversePathId(reversePath.getHaPathId());
        } else {
            setProtectedReversePathId(null);
        }
    }

    /**
     * Checks if specified path is protected.
     */
    public boolean isProtectedPath(PathId pathId) {
        if (pathId == null) {
            throw new IllegalArgumentException("Path id can't be null");
        }
        return pathId.equals(getProtectedForwardPathId()) || pathId.equals(getProtectedReversePathId());
    }

    private void validateEndpoints(HaFlowPath path) {
        if (!path.getSharedSwitchId().equals(getSharedSwitchId())) {
            throw new IllegalArgumentException(format("HA-path %s has the shared endpoint switch ID %s, but %s is "
                            + "expected", path.getHaPathId(), path.getSharedSwitchId(), getSharedSwitchId()));
        }
        Set<SwitchId> subFlowSwitchIds = getSubFlows().stream()
                .map(HaSubFlow::getEndpointSwitchId)
                .collect(Collectors.toSet());
        Set<SwitchId> pathSwitchIds = path.getSubFlowSwitchIds();

        if (!subFlowSwitchIds.equals(pathSwitchIds)) {
            throw new IllegalArgumentException(format("HA-path %s must have %s switch endpoints, but has the following "
                            + "endpoints: %s",
                    path.getHaPathId(), subFlowSwitchIds, pathSwitchIds));
        }
    }

    private void validateForwardPath(HaFlowPath haPath) {
        validateEndpoints(haPath);
        if (!haPath.isForward()) {
            throw new IllegalArgumentException(format("Cookie %s of HA-path %s must have a forward direction",
                    haPath.getCookie(), haPath.getHaPathId()));
        }
    }

    private void validateReversePath(HaFlowPath haPath) {
        validateEndpoints(haPath);
        if (!haPath.isReverse()) {
            throw new IllegalArgumentException(format("Cookie %s of HA-path %s must have a reverse direction",
                    haPath.getCookie(), haPath.getHaPathId()));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        HaFlow that = (HaFlow) o;
        return new EqualsBuilder()
                .append(getHaFlowId(), that.getHaFlowId())
                .append(getSharedSwitchId(), that.getSharedSwitchId())
                .append(getSharedPort(), that.getSharedPort())
                .append(getSharedOuterVlan(), that.getSharedOuterVlan())
                .append(getSharedInnerVlan(), that.getSharedInnerVlan())
                .append(getMaximumBandwidth(), that.getMaximumBandwidth())
                .append(getPathComputationStrategy(), that.getPathComputationStrategy())
                .append(getEncapsulationType(), that.getEncapsulationType())
                .append(getMaxLatency(), that.getMaxLatency())
                .append(getMaxLatencyTier2(), that.getMaxLatencyTier2())
                .append(isIgnoreBandwidth(), that.isIgnoreBandwidth())
                .append(isPeriodicPings(), that.isPeriodicPings())
                .append(isPinned(), that.isPinned())
                .append(getPriority(), that.getPriority())
                .append(isStrictBandwidth(), that.isStrictBandwidth())
                .append(getDescription(), that.getDescription())
                .append(isAllocateProtectedPath(), that.isAllocateProtectedPath())
                .append(getForwardPathId(), that.getForwardPathId())
                .append(getReversePathId(), that.getReversePathId())
                .append(getProtectedForwardPathId(), that.getProtectedForwardPathId())
                .append(getProtectedReversePathId(), that.getProtectedReversePathId())
                .append(getSubFlows(), that.getSubFlows())
                .append(getStatus(), that.getStatus())
                .append(getAffinityGroupId(), that.getAffinityGroupId())
                .append(getDiverseGroupId(), that.getDiverseGroupId())
                .append(getTimeCreate(), that.getTimeCreate())
                .append(getTimeModify(), that.getTimeModify())
                .isEquals();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getHaFlowId(), getSharedSwitchId(), getSharedPort(), getSharedOuterVlan(),
                getSharedInnerVlan(), getMaximumBandwidth(), getPathComputationStrategy(),
                getEncapsulationType(), getMaxLatency(), getMaxLatencyTier2(), isIgnoreBandwidth(), isPeriodicPings(),
                isPinned(), getPriority(), isStrictBandwidth(), getDescription(), isAllocateProtectedPath(),
                getForwardPathId(), getReversePathId(), getProtectedForwardPathId(), getProtectedReversePathId(),
                getSubFlows(), getStatus(), getTimeCreate(), getTimeModify(), getAffinityGroupId(),
                getDiverseGroupId());
    }

    /**
     * Recalculate the HA-flow status based on sub-flow statuses.
     */
    public void recalculateStatus() {
        FlowStatus haFlowStatus = null;
        for (HaSubFlow subFlow : getSubFlows()) {
            FlowStatus subFlowStatus = subFlow.getStatus();
            if (subFlowStatus == FlowStatus.IN_PROGRESS) {
                haFlowStatus = FlowStatus.IN_PROGRESS;
                break;
            }
            if (haFlowStatus == null) {
                haFlowStatus = subFlowStatus;
            } else if (haFlowStatus == FlowStatus.DOWN && subFlowStatus != FlowStatus.DOWN
                    || haFlowStatus == FlowStatus.UP && subFlowStatus != FlowStatus.UP) {
                haFlowStatus = FlowStatus.DEGRADED;
            }
        }
        setStatus(haFlowStatus);
    }

    /**
     * Defines persistable data of the HA-flow.
     */
    public interface HaFlowData {
        String getHaFlowId();

        void setHaFlowId(String haFlowId);

        Switch getSharedSwitch();

        void setSharedSwitch(Switch sharedSwitch);

        SwitchId getSharedSwitchId();

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

        Set<PathId> getPathIds();

        Collection<HaFlowPath> getPaths();

        Optional<HaFlowPath> getPath(PathId pathId);

        boolean hasPath(HaFlowPath path);

        void addPaths(HaFlowPath... paths);

        Optional<HaSubFlow> getSubFlow(String subFlowId);

        List<HaSubFlow> getSubFlows();

        void setSubFlows(Set<HaSubFlow> subFlows);

        FlowStatus getStatus();

        void setStatus(FlowStatus status);

        Instant getTimeCreate();

        void setTimeCreate(Instant timeCreate);

        Instant getTimeModify();

        void setTimeModify(Instant timeModify);
    }

    /**
     * POJO implementation of HaFlowData.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static final class HaFlowDataImpl implements HaFlowData, Serializable {
        private static final long serialVersionUID = 1L;
        @NonNull String haFlowId;
        @NonNull Switch sharedSwitch;
        int sharedPort;
        int sharedOuterVlan;
        int sharedInnerVlan;
        PathId forwardPathId;
        PathId reversePathId;
        PathId protectedForwardPathId;
        PathId protectedReversePathId;
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
        Instant timeCreate;
        Instant timeModify;
        boolean pinned;
        Integer priority;
        PathComputationStrategy pathComputationStrategy;
        String affinityGroupId;
        String diverseGroupId;

        @Builder.Default
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        List<HaSubFlow> subFlows = new ArrayList<>();

        @Builder.Default
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        Set<HaFlowPath> paths = new HashSet<>();

        // The reference is used to link sub-flows back to the ha-flow. See {@link #setSubFlows(Set)}.
        @Setter(AccessLevel.NONE)
        @Getter(AccessLevel.NONE)
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        HaFlow haFlow;

        @Override
        public SwitchId getSharedSwitchId() {
            return sharedSwitch.getSwitchId();
        }

        @Override
        public Optional<HaSubFlow> getSubFlow(String subFlowId) {
            return subFlows.stream()
                    .filter(subFlow -> subFlow.getHaSubFlowId().equals(subFlowId))
                    .findAny();
        }

        @Override
        public List<HaSubFlow> getSubFlows() {
            return Collections.unmodifiableList(subFlows);
        }

        @Override
        public void setSubFlows(Set<HaSubFlow> subFlows) {
            for (HaSubFlow subFlow : this.subFlows) {
                boolean keepSubFlow = subFlows.stream()
                        .anyMatch(sub -> sub.getHaSubFlowId().equals(subFlow.getHaSubFlowId()));
                if (!keepSubFlow) {
                    // Invalidate the removed entity as a sub-flow can't exist without a HA-flow.
                    subFlow.setData(null);
                }
            }
            this.subFlows = new ArrayList<>(subFlows);
        }

        @Override
        public Optional<HaFlowPath> getPath(PathId pathId) {
            return paths.stream()
                    .filter(path -> path.getHaPathId().equals(pathId))
                    .findAny();
        }

        @Override
        public boolean hasPath(HaFlowPath haPath) {
            return paths.contains(haPath);
        }

        @Override
        public Set<PathId> getPathIds() {
            return paths.stream().map(HaFlowPath::getHaPathId).collect(Collectors.toSet());
        }

        @Override
        public void addPaths(HaFlowPath... haPaths) {
            for (HaFlowPath pathToAdd : haPaths) {
                boolean toBeAdded = true;
                Iterator<HaFlowPath> it = this.paths.iterator();
                while (it.hasNext()) {
                    HaFlowPath each = it.next();
                    if (pathToAdd == each) {
                        toBeAdded = false;
                        break;
                    }
                    if (pathToAdd.getHaPathId().equals(each.getHaPathId())) {
                        it.remove();
                        // Quit as no duplicates expected.
                        break;
                    }
                }
                if (toBeAdded) {
                    this.paths.add(pathToAdd);
                    HaFlowPathData data = pathToAdd.getData();
                    if (data instanceof HaFlowPathDataImpl) {
                        ((HaFlowPathDataImpl) data).haFlow = haFlow;
                    }
                }
            }

        }
    }

    /**
     * A cloner for HA-flow entity.
     */
    @Mapper(collectionMappingStrategy = CollectionMappingStrategy.TARGET_IMMUTABLE)
    public interface HaFlowCloner {
        HaFlowCloner INSTANCE = Mappers.getMapper(HaFlowCloner.class);

        @Mapping(target = "subFlows", ignore = true)
        @Mapping(target = "paths", ignore = true)
        @Mapping(target = "pathIds", ignore = true)
        void copyWithoutSubFlowsAndPaths(HaFlowData source, @MappingTarget HaFlowData target);

        @Mapping(target = "subFlows", ignore = true)
        @Mapping(target = "paths", ignore = true)
        @Mapping(target = "pathIds", ignore = true)
        @Mapping(target = "sharedSwitch", ignore = true)
        void copyWithoutSwitchSubFlowsAndPaths(HaFlowData source, @MappingTarget HaFlowData target);

        /**
         * Performs deep copy of entity data.
         *
         * @param source the HA-flow to copy from.
         */
        default HaFlowData deepCopy(HaFlowData source, HaFlow targetFlow) {
            HaFlowDataImpl result = new HaFlowDataImpl();
            result.haFlow = targetFlow;
            copyWithoutSwitchSubFlowsAndPaths(source, result);
            result.setSharedSwitch(new Switch(source.getSharedSwitch()));
            result.setSubFlows(source.getSubFlows().stream()
                    .map(subFlow -> new HaSubFlow(subFlow, targetFlow))
                    .collect(Collectors.toSet()));

            result.addPaths(source.getPaths().stream()
                    .map(path -> new HaFlowPath(path, targetFlow))
                    .toArray(HaFlowPath[]::new));
            return result;
        }
    }
}
