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

import org.openkilda.model.cookie.FlowSegmentCookie;

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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Represents a flow path.
 */
@DefaultSerializer(BeanSerializer.class)
@ToString
public class HaFlowPath implements CompositeDataEntity<HaFlowPath.HaFlowPathData> {
    @Getter
    @Setter
    @Delegate(excludes = FlowPathInternalData.class)
    @JsonIgnore
    private HaFlowPathData data;

    /**
     * No args constructor for deserialization purpose.
     */
    private HaFlowPath() {
        data = new HaFlowPathDataImpl();
    }

    /**
     * Cloning constructor which performs deep copy of the entity.
     *
     * @param entityToClone the HA-path entity to copy data from.
     * @param haFlow the HA-flow to be referred ({@code HaFlowPath.getHaFlow()}) by the new HA-path.
     */
    public HaFlowPath(@NonNull HaFlowPath entityToClone, HaFlow haFlow) {
        this();
        data = HaFlowPathCloner.INSTANCE.deepCopy(entityToClone.getData(), haFlow, this);
    }

    @Builder
    public HaFlowPath(
            @NonNull PathId haPathId, @NonNull Switch sharedSwitch, SwitchId yPointSwitchId,
            FlowSegmentCookie cookie, MeterId sharedPointMeterId,
            MeterId yPointMeterId, GroupId yPointGroupId, long bandwidth, boolean ignoreBandwidth,
            FlowPathStatus status, String sharedBandwidthGroupId, List<PathSegment> segments) {
        data = HaFlowPathDataImpl.builder().haPathId(haPathId).sharedSwitch(sharedSwitch).yPointSwitchId(yPointSwitchId)
                .cookie(cookie).yPointMeterId(yPointMeterId).sharedPointMeterId(sharedPointMeterId)
                .yPointGroupId(yPointGroupId).bandwidth(bandwidth)
                .ignoreBandwidth(ignoreBandwidth).status(status).sharedBandwidthGroupId(sharedBandwidthGroupId)
                .build();
        // The reference is used to link sub flow edges back to the path. See {@link #setHaSubFlowEdges(Collection)}.
        ((HaFlowPathDataImpl) data).haFlowPath = this;

        if (segments != null && !segments.isEmpty()) {
            data.setSegments(segments);
        }
    }

    public HaFlowPath(@NonNull HaFlowPathData data) {
        this.data = data;
    }

    /**
     * Sets the current flow path status corresponds with passed {@link FlowStatus} .
     */
    public void setStatusLikeFlow(FlowStatus haFlowStatus) {
        switch (haFlowStatus) {
            case UP:
                setStatus(FlowPathStatus.ACTIVE);
                break;
            case DOWN:
                setStatus(FlowPathStatus.INACTIVE);
                break;
            case IN_PROGRESS:
                setStatus(FlowPathStatus.IN_PROGRESS);
                break;
            default:
                throw new IllegalArgumentException(format("Unsupported status value: %s", haFlowStatus));
        }
    }

    public boolean isForward() {
        return getCookie().getDirection() == FlowPathDirection.FORWARD;
    }

    public boolean isReverse() {
        return getCookie().getDirection() == FlowPathDirection.REVERSE;
    }

    /**
     * Sets the bandwidth.
     * This also updates the corresponding path segments.
     */
    public void setBandwidth(long bandwidth) {
        data.setBandwidth(bandwidth);
        List<PathSegment> segments = getSegments();
        if (segments != null) {
            segments.forEach(segment -> segment.getData().setBandwidth(bandwidth));
        }
    }

    /**
     * Sets the ignoreBandwidth flag.
     * This also updates the corresponding path segments.
     */
    public void setIgnoreBandwidth(boolean ignoreBandwidth) {
        data.setIgnoreBandwidth(ignoreBandwidth);
        List<PathSegment> segments = getSegments();
        if (segments != null) {
            segments.forEach(segment -> segment.getData().setIgnoreBandwidth(ignoreBandwidth));
        }
    }

    /**
     * Sets the sharedBandwidthGroupId.
     * This also updates the corresponding path segments.
     */
    public void setSharedBandwidthGroupId(String sharedBandwidthGroupId) {
        data.setSharedBandwidthGroupId(sharedBandwidthGroupId);

        List<PathSegment> segments = getSegments();
        if (segments != null) {
            segments.forEach(segment -> segment.getData().setSharedBandwidthGroupId(sharedBandwidthGroupId));
        }
    }

    /**
     * Returns set of sub flow endpoint switch ids.
     * Shared switch id is not included into this set.
     */
    public Set<SwitchId> getSubFlowSwitchIds() {
        return getHaSubFlowEdges().stream()
                .map(HaSubFlowEdge::getSubFlowEndpointSwitchId)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        HaFlowPath that = (HaFlowPath) o;
        return new EqualsBuilder()
                .append(getHaPathId(), that.getHaPathId())
                .append(getHaFlowId(), that.getHaFlowId())
                .append(getSharedSwitchId(), that.getSharedSwitchId())
                .append(getYPointSwitchId(), that.getYPointSwitchId())
                .append(getCookie(), that.getCookie())
                .append(getYPointMeterId(), that.getYPointMeterId())
                .append(getSharedPointMeterId(), that.getSharedPointMeterId())
                .append(getYPointGroupId(), that.getYPointGroupId())
                .append(getBandwidth(), that.getBandwidth())
                .append(isIgnoreBandwidth(), that.isIgnoreBandwidth())
                .append(getStatus(), that.getStatus())
                .append(getSegments(), that.getSegments())
                .append(getHaSubFlowEdges(), that.getHaSubFlowEdges())
                .append(getTimeCreate(), that.getTimeCreate())
                .append(getTimeModify(), that.getTimeModify())
                .append(getSharedBandwidthGroupId(), that.getSharedBandwidthGroupId())
                .build();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getHaPathId(), getHaFlowId(), getSharedSwitchId(), getYPointSwitchId(), getCookie(),
                getYPointMeterId(), getSharedPointMeterId(), getYPointGroupId(), getBandwidth(),
                isIgnoreBandwidth(), getStatus(), getSegments(), getHaSubFlowEdges(), getSharedBandwidthGroupId(),
                getTimeCreate(), getTimeModify());
    }

    /**
     * Defines persistable data of the FlowPath.
     */
    public interface HaFlowPathData {
        PathId getHaPathId();

        void setHaPathId(PathId haPathId);

        String getHaFlowId();

        HaFlow getHaFlow();

        SwitchId getSharedSwitchId();

        Switch getSharedSwitch();

        void setSharedSwitch(Switch sharedSwitch);

        SwitchId getYPointSwitchId();

        void setYPointSwitchId(SwitchId yPointSwitchId);

        FlowSegmentCookie getCookie();

        void setCookie(FlowSegmentCookie cookie);

        MeterId getYPointMeterId();

        void setYPointMeterId(MeterId meterId);

        MeterId getSharedPointMeterId();

        void setSharedPointMeterId(MeterId meterId);

        GroupId getYPointGroupId();

        void setYPointGroupId(GroupId groupId);

        long getBandwidth();

        void setBandwidth(long bandwidth);

        boolean isIgnoreBandwidth();

        void setIgnoreBandwidth(boolean ignoreBandwidth);

        FlowPathStatus getStatus();

        void setStatus(FlowPathStatus status);

        List<PathSegment> getSegments();

        void setSegments(List<PathSegment> segments);

        Set<HaSubFlowEdge> getHaSubFlowEdges();

        void setHaSubFlowEdges(Collection<HaSubFlowEdge> haSubFlowEdges);

        String getSharedBandwidthGroupId();

        void setSharedBandwidthGroupId(String sharedBandwidthGroupId);

        Instant getTimeCreate();

        void setTimeCreate(Instant timeCreate);

        Instant getTimeModify();

        void setTimeModify(Instant timeModify);
    }

    /**
     * Defines methods which don't need to be delegated.
     */
    interface FlowPathInternalData {
        void setBandwidth(long bandwidth);

        void setIgnoreBandwidth(boolean ignoreBandwidth);

        void setSharedBandwidthGroupId(String sharedBandwidthGroupId);
    }

    /**
     * POJO implementation of FlowPathData.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static final class HaFlowPathDataImpl implements HaFlowPathData, Serializable {
        private static final long serialVersionUID = 1L;
        @NonNull PathId haPathId;
        @NonNull Switch sharedSwitch;
        SwitchId yPointSwitchId;
        @Setter(AccessLevel.NONE)
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        HaFlow haFlow;
        FlowSegmentCookie cookie;
        MeterId yPointMeterId;
        MeterId sharedPointMeterId;
        GroupId yPointGroupId;
        long bandwidth;
        boolean ignoreBandwidth;
        Instant timeCreate;
        Instant timeModify;
        FlowPathStatus status;
        String sharedBandwidthGroupId;
        @Builder.Default
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        @NonNull List<PathSegment> segments = new ArrayList<>();

        @Builder.Default
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        @NonNull Set<HaSubFlowEdge> haSubFlowEdges = new HashSet<>();

        // The reference is used to link sub flow edges back to the path. See {@link #setHaSubFlowEdges(Collection)}.
        @Setter(AccessLevel.NONE)
        @Getter(AccessLevel.NONE)
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        HaFlowPath haFlowPath;


        public void setHaPathId(PathId pathId) {
            this.haPathId = pathId;
            segments.forEach(segment -> segment.getData().setPathId(pathId));
        }

        @Override
        public String getHaFlowId() {
            return haFlow != null ? haFlow.getHaFlowId() : null;
        }

        @Override
        public SwitchId getSharedSwitchId() {
            return sharedSwitch.getSwitchId();
        }

        @Override
        public List<PathSegment> getSegments() {
            return Collections.unmodifiableList(segments);
        }

        /**
         * Set the segments.
         */
        @Override
        public void setSegments(List<PathSegment> segments) {
            for (int idx = 0; idx < segments.size(); idx++) {
                PathSegment segment = segments.get(idx);
                PathSegment.PathSegmentData data = segment.getData();
                data.setPathId(haPathId);
                data.setSeqId(idx);
                data.setIgnoreBandwidth(ignoreBandwidth);
                data.setBandwidth(bandwidth);
                data.setSharedBandwidthGroupId(sharedBandwidthGroupId);
            }
            this.segments = new ArrayList<>(segments);
        }

        @Override
        public Set<HaSubFlowEdge> getHaSubFlowEdges() {
            return Collections.unmodifiableSet(haSubFlowEdges);
        }

        @Override
        public void setHaSubFlowEdges(Collection<HaSubFlowEdge> haSubFlowEdges) {
            haSubFlowEdges.forEach(edge -> edge.setHaFlowPath(haFlowPath));

            for (HaSubFlowEdge subFlowEdge : this.haSubFlowEdges) {
                boolean keepSubFlowEdge = haSubFlowEdges.stream().anyMatch(n -> n.equals(subFlowEdge));
                if (!keepSubFlowEdge) {
                    // Invalidate the removed entity as a sub-flow-edge can't exist without a ha-subflow and ha-path.
                    subFlowEdge.setData(null);
                }
            }
            this.haSubFlowEdges = new HashSet<>(haSubFlowEdges);
        }
    }

    /**
     * A cloner for FlowPath entity.
     */
    @Mapper(collectionMappingStrategy = CollectionMappingStrategy.TARGET_IMMUTABLE)
    public interface HaFlowPathCloner {
        HaFlowPathCloner INSTANCE = Mappers.getMapper(HaFlowPathCloner.class);

        @Mapping(target = "haSubFlowEdges", ignore = true)
        void copyWithoutHaSubFlowEdges(HaFlowPathData source, @MappingTarget HaFlowPathData target);

        @Mapping(target = "sharedSwitch", ignore = true)
        @Mapping(target = "segments", ignore = true)
        @Mapping(target = "haSubFlowEdges", ignore = true)
        void copyWithoutSwitchesAndSegments(HaFlowPathData source, @MappingTarget HaFlowPathData target);

        /**
         * Performs deep copy of entity data.
         *
         * @param source the path data to copy from.
         * @param targetHaFlow the HA-flow to be referred ({@code HaFlowPathData.getHaFlow()}) by the new path data.
         */
        default HaFlowPathData deepCopy(
                HaFlowPathData source, HaFlow targetHaFlow, HaFlowPath targetHaFlowPath) {
            HaFlowPathDataImpl result = new HaFlowPathDataImpl();
            result.haFlow = targetHaFlow;

            copyWithoutSwitchesAndSegments(source, result);
            result.setSharedSwitch(new Switch(source.getSharedSwitch()));
            result.setSegments(source.getSegments().stream()
                    .map(PathSegment::new)
                    .collect(Collectors.toList()));

            Map<String, HaSubFlow> subFlowMap = targetHaFlow.getSubFlows().stream()
                    .collect(Collectors.toMap(HaSubFlow::getHaSubFlowId, Function.identity()));

            Set<HaSubFlowEdge> subFlowEdges = new HashSet<>();
            for (HaSubFlowEdge subFlowEdge : source.getHaSubFlowEdges()) {
                HaSubFlow targetHaSubFlow = subFlowMap.get(subFlowEdge.getHaSubFlowId());
                if (targetHaSubFlow == null) {
                    throw new IllegalArgumentException(format("Couldn't copy HaFlowPath %s because target ha-flow has "
                            + "no ha-subflow %s", source, subFlowEdge.getHaSubFlowId()));
                }
                subFlowEdges.add(new HaSubFlowEdge(subFlowEdge, targetHaFlowPath, targetHaSubFlow));
            }
            result.setHaSubFlowEdges(subFlowEdges);
            return result;
        }
    }
}
