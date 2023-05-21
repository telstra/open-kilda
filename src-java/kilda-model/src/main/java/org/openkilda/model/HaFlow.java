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
import org.openkilda.model.cookie.FlowSegmentCookie;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.BeanSerializer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
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
import java.util.Arrays;
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
        this();
        HaFlowCloner.INSTANCE.deepCopy(entityToClone.getData(), (HaFlowDataImpl) data, this);
    }

    @Builder
    public HaFlow(@NonNull String haFlowId, @NonNull Switch sharedSwitch, int sharedPort, int sharedOuterVlan,
                  int sharedInnerVlan, long maximumBandwidth,
                  PathComputationStrategy pathComputationStrategy, FlowEncapsulationType encapsulationType,
                  Long maxLatency, Long maxLatencyTier2, boolean ignoreBandwidth, boolean periodicPings,
                  boolean pinned, Integer priority, boolean strictBandwidth, String description,
                  boolean allocateProtectedPath, FlowStatus status, String statusInfo, String affinityGroupId,
                  String diverseGroupId) {
        HaFlowDataImpl.HaFlowDataImplBuilder builder = HaFlowDataImpl.builder()
                .haFlowId(haFlowId).sharedSwitch(sharedSwitch).sharedPort(sharedPort).sharedOuterVlan(sharedOuterVlan)
                .sharedInnerVlan(sharedInnerVlan).maximumBandwidth(maximumBandwidth)
                .pathComputationStrategy(pathComputationStrategy).encapsulationType(encapsulationType)
                .maxLatency(maxLatency).maxLatencyTier2(maxLatencyTier2).ignoreBandwidth(ignoreBandwidth)
                .periodicPings(periodicPings).pinned(pinned).priority(priority).strictBandwidth(strictBandwidth)
                .description(description).allocateProtectedPath(allocateProtectedPath).status(status)
                .statusInfo(statusInfo).affinityGroupId(affinityGroupId).diverseGroupId(diverseGroupId);

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
     * Checks if the specified path is a protected path.
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
        Set<SwitchId> subFlowSwitchIds = getHaSubFlows().stream()
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

    /**
     * Get up to 3 endpoint switchIds.
     */
    public Set<SwitchId> getEndpointSwitchIds() {
        Set<SwitchId> switchIds = Sets.newHashSet(getSharedSwitchId());
        for (HaSubFlow subFlow : getHaSubFlows()) {
            switchIds.add(subFlow.getEndpointSwitchId());
        }
        return switchIds;
    }

    public FlowEndpoint getSharedEndpoint() {
        return new FlowEndpoint(getSharedSwitchId(), getSharedPort(), getSharedOuterVlan(), getSharedInnerVlan());
    }

    /**
     * Return opposite pathId to passed pathId.
     */
    public Optional<PathId> getOppositePathId(@NonNull PathId pathId) {
        if (pathId.equals(getForwardPathId()) && getReversePathId() != null) {
            return Optional.of(getReversePathId());
        } else if (pathId.equals(getReversePathId()) && getForwardPathId() != null) {
            return Optional.of(getForwardPathId());
        } else if (pathId.equals(getProtectedForwardPathId()) && getProtectedReversePathId() != null) {
            return Optional.of(getProtectedReversePathId());
        } else if (pathId.equals(getProtectedReversePathId()) && getProtectedForwardPathId() != null) {
            return Optional.of(getProtectedForwardPathId());
        } else {
            // Handling the case of non-active paths.
            Optional<Long> requestedPathCookie = getPath(pathId)
                    .map(HaFlowPath::getCookie)
                    .map(FlowSegmentCookie::getFlowEffectiveId);
            if (requestedPathCookie.isPresent()) {
                return getPaths().stream()
                        .filter(path -> !path.getHaPathId().equals(pathId))
                        .filter(path -> path.getCookie().getFlowEffectiveId() == requestedPathCookie.get())
                        .findAny()
                        .map(HaFlowPath::getHaPathId);
            } else {
                throw new IllegalArgumentException(format("Ha flow %s does not have path %s", getHaFlowId(), pathId));
            }
        }
    }

    /**
     * Gets path IDs of primary sub paths.
     */
    public Collection<PathId> getPrimarySubPathIds() {
        return getSubPathIds(Lists.newArrayList(getForwardPath(), getReversePath()));
    }

    /**
     * Gets path IDs of protected sub paths.
     */
    public Collection<PathId> getProtectedSubPathIds() {
        return getSubPathIds(Lists.newArrayList(getProtectedForwardPath(), getProtectedReversePath()));
    }

    /**
     * Gets path IDs of all sub paths.
     * This method can return not only primary and protected sub paths.
     * For example during update/reroute operations HA-flow can temporally have more sub paths.
     */
    public Collection<PathId> getSubPathIds() {
        return getSubPathIds(getPaths());
    }

    private Collection<PathId> getSubPathIds(Collection<HaFlowPath> haPaths) {
        List<PathId> subPathIds = new ArrayList<>();
        if (haPaths == null) {
            return subPathIds;
        }
        for (HaFlowPath haPath : haPaths) {
            if (haPath != null) {
                for (FlowPath subPath : haPath.getSubPaths()) {
                    subPathIds.add(subPath.getPathId());
                }
            }
        }
        return subPathIds;
    }

    /**
     * Gets sub path byt its path ID.
     */
    public Optional<FlowPath> getSubPath(PathId pathId) {
        //TODO maybe improve performance?
        Collection<HaFlowPath> haPaths = getPaths();
        if (haPaths == null) {
            return Optional.empty();
        }

        for (HaFlowPath haPath : haPaths) {
            if (haPath.getSubPaths() != null) {
                for (FlowPath subPath : haPath.getSubPaths()) {
                    if (subPath.getPathId().equals(pathId)) {
                        return Optional.of(subPath);
                    }
                }
            }
        }
        return Optional.empty();
    }

    public HaFlowPath getPathOrThrowException(PathId pathId) {
        return getPath(pathId).orElseThrow(() -> new IllegalArgumentException(
                format("HA-flow %s has no HA-path %s", getHaFlowId(), pathId)));
    }

    public HaSubFlow getHaSubFlowOrThrowException(String haSubFlowId) {
        return getHaSubFlow(haSubFlowId).orElseThrow(() -> new IllegalArgumentException(
                format("HA-flow %s has no HA-sub flow %s", getHaFlowId(), haSubFlowId)));
    }

    /**
     * Swap primary and protected path IDs.
     */
    public void swapPathIds() {
        final PathId primaryForward = getForwardPathId();
        final PathId primaryReverse = getReversePathId();
        final PathId protectedForward = getProtectedForwardPathId();
        final PathId protectedReverse = getProtectedReversePathId();

        setForwardPathId(protectedForward);
        setReversePathId(protectedReverse);
        setProtectedForwardPathId(primaryForward);
        setProtectedReversePathId(primaryReverse);
    }

    public Collection<HaFlowPath> getPrimaryPaths() {
        return getHaFlowPaths(getForwardPath(), getReversePath());
    }

    public Collection<HaFlowPath> getProtectedPaths() {
        return getHaFlowPaths(getProtectedForwardPath(), getProtectedReversePath());
    }

    /**
     * Returns HA-flow paths which are currently in use.
     * This method doesn't return unused paths, like getPath() method does.
     * Paths can be set unused in the middle of update/reroute operations.
     */
    public Collection<HaFlowPath> getUsedPaths() {
        Collection<HaFlowPath> paths = getPrimaryPaths();
        paths.addAll(getProtectedPaths());
        return paths;
    }

    private Collection<HaFlowPath> getHaFlowPaths(HaFlowPath... paths) {
        return Arrays.stream(paths).filter(Objects::nonNull).collect(Collectors.toList());
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
                .append(getHaSubFlows(), that.getHaSubFlows())
                .append(getStatus(), that.getStatus())
                .append(getStatusInfo(), that.getStatusInfo())
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
                getHaSubFlows(), getStatus(), getStatusInfo(), getTimeCreate(), getTimeModify(), getAffinityGroupId(),
                getDiverseGroupId());
    }

    /**
     * Computes the HA-flow status based on sub-flow statuses.
     */
    public FlowStatus computeStatus() {
        FlowStatus haFlowStatus = null;
        for (HaSubFlow subFlow : getHaSubFlows()) {
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
        return haFlowStatus;
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

        Optional<HaSubFlow> getHaSubFlow(String subFlowId);

        Collection<HaSubFlow> getHaSubFlows();

        void setHaSubFlows(Collection<HaSubFlow> haSubFlows);

        FlowStatus getStatus();

        void setStatus(FlowStatus status);

        String getStatusInfo();

        void setStatusInfo(String statusInfo);

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
        String statusInfo;
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
        public Optional<HaSubFlow> getHaSubFlow(String subFlowId) {
            return subFlows.stream()
                    .filter(subFlow -> subFlow.getHaSubFlowId().equals(subFlowId))
                    .findAny();
        }

        @Override
        public Collection<HaSubFlow> getHaSubFlows() {
            return Collections.unmodifiableList(subFlows);
        }

        @Override
        public void setHaSubFlows(Collection<HaSubFlow> subFlows) {
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

        @Mapping(target = "haSubFlows", ignore = true)
        @Mapping(target = "paths", ignore = true)
        @Mapping(target = "pathIds", ignore = true)
        void copyWithoutSubFlowsAndPaths(HaFlowData source, @MappingTarget HaFlowData target);

        @Mapping(target = "haSubFlows", ignore = true)
        @Mapping(target = "paths", ignore = true)
        @Mapping(target = "pathIds", ignore = true)
        @Mapping(target = "sharedSwitch", ignore = true)
        void copyWithoutSwitchSubFlowsAndPaths(HaFlowData source, @MappingTarget HaFlowData target);

        /**
         * Performs deep copy of entity data.
         *
         * @param source the HA-flow to copy from.
         */
        default void deepCopy(HaFlowData source, HaFlowDataImpl target, HaFlow targetFlow) {
            target.haFlow = targetFlow;
            copyWithoutSwitchSubFlowsAndPaths(source, target);
            target.setSharedSwitch(new Switch(source.getSharedSwitch()));
            target.setHaSubFlows(source.getHaSubFlows().stream()
                    .map(subFlow -> new HaSubFlow(subFlow, targetFlow))
                    .collect(Collectors.toList()));

            target.addPaths(source.getPaths().stream()
                    .map(path -> new HaFlowPath(path, targetFlow))
                    .toArray(HaFlowPath[]::new));
        }

        /**
         * Performs deep copy of entity data.
         */
        default HaFlowData deepCopy(HaFlowData source, HaFlow targetFlow) {
            HaFlowDataImpl result = new HaFlowDataImpl();
            deepCopy(source, result, targetFlow);
            return result;
        }
    }
}
