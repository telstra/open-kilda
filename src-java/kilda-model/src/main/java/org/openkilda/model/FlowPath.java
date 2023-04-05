/* Copyright 2019 Telstra Open Source
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

import org.openkilda.model.FlowMirrorPoints.FlowMirrorPointsData;
import org.openkilda.model.FlowMirrorPoints.FlowMirrorPointsDataImpl;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a flow path.
 */
@DefaultSerializer(BeanSerializer.class)
@ToString
public class FlowPath implements CompositeDataEntity<FlowPath.FlowPathData> {
    @Getter
    @Setter
    @Delegate(excludes = FlowPathInternalData.class)
    @JsonIgnore
    private FlowPathData data;

    /**
     * No args constructor for deserialization purpose.
     */
    private FlowPath() {
        data = new FlowPathDataImpl();
    }

    /**
     * Cloning constructor which performs deep copy of the entity.
     *
     * @param entityToClone the path entity to copy data from.
     * @param flow the flow to be referred ({@code FlowPath.getFlow()}) by the new path.
     */
    public FlowPath(@NonNull FlowPath entityToClone, Flow flow, HaSubFlow haSubFlow) {
        data = FlowPathCloner.INSTANCE.deepCopy(entityToClone.getData(), flow, haSubFlow);
    }

    @Builder
    public FlowPath(@NonNull PathId pathId, @NonNull Switch srcSwitch, @NonNull Switch destSwitch,
                    FlowSegmentCookie cookie, MeterId meterId, GroupId ingressMirrorGroupId,
                    long latency, long bandwidth,
                    boolean ignoreBandwidth, FlowPathStatus status, List<PathSegment> segments,
                    Set<FlowApplication> applications, boolean srcWithMultiTable, boolean destWithMultiTable,
                    String sharedBandwidthGroupId, HaFlowPath haFlowPath) {
        data = FlowPathDataImpl.builder().pathId(pathId).srcSwitch(srcSwitch).destSwitch(destSwitch)
                .cookie(cookie).meterId(meterId).ingressMirrorGroupId(ingressMirrorGroupId)
                .latency(latency).bandwidth(bandwidth)
                .ignoreBandwidth(ignoreBandwidth).status(status)
                .applications(applications).srcWithMultiTable(srcWithMultiTable).destWithMultiTable(destWithMultiTable)
                .sharedBandwidthGroupId(sharedBandwidthGroupId).haFlowPath(haFlowPath)
                .build();
        // The reference is used to link path segments back to the path. See {@link #setSegments(List)}.
        ((FlowPathDataImpl) data).flowPath = this;

        if (segments != null && !segments.isEmpty()) {
            data.setSegments(segments);
        }
    }

    public FlowPath(@NonNull FlowPathData data) {
        this.data = data;
    }

    /**
     * Sets the current flow path status corresponds with passed {@link FlowStatus} .
     */
    public void setStatusLikeFlow(FlowStatus flowStatus) {
        switch (flowStatus) {
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
                throw new IllegalArgumentException(format("Unsupported status value: %s", flowStatus));
        }
    }

    /**
     * Checks whether the flow path goes through a single switch.
     *
     * @return true if source and destination switches are the same, otherwise false
     */
    public boolean isOneSwitchFlow() {
        return getSrcSwitchId().equals(getDestSwitchId());
    }

    public boolean isForward() {
        return getCookie().getDirection() == FlowPathDirection.FORWARD;
    }

    /**
     * Checks whether this path is a protected path for the flow.
     */
    public boolean isProtected() {
        Flow flow = getFlow();
        return flow != null && flow.isProtectedPath(getPathId());
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
     * Checks if ingress endpoint has mirror.
     * NOTE: this method needs external transaction
     */
    public boolean hasIngressMirror() {
        return getFlowMirrorPointsSet().stream()
                .anyMatch(point -> point.getMirrorSwitchId().equals(getSrcSwitchId()));
    }

    /**
     * Checks if ingress endpoint has mirror.
     * NOTE: this method needs external transaction
     */
    public boolean hasEgressMirror() {
        return getFlowMirrorPointsSet().stream()
                .anyMatch(point -> point.getMirrorSwitchId().equals(getDestSwitchId()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FlowPath that = (FlowPath) o;
        return new EqualsBuilder()
                .append(getLatency(), that.getLatency())
                .append(getBandwidth(), that.getBandwidth())
                .append(isIgnoreBandwidth(), that.isIgnoreBandwidth())
                .append(getPathId(), that.getPathId())
                .append(getSrcSwitchId(), that.getSrcSwitchId())
                .append(getDestSwitchId(), that.getDestSwitchId())
                .append(getFlowId(), that.getFlowId())
                .append(getHaFlowPathId(), that.getHaFlowPathId())
                .append(getCookie(), that.getCookie())
                .append(getMeterId(), that.getMeterId())
                .append(getIngressMirrorGroupId(), that.getIngressMirrorGroupId())
                .append(getTimeCreate(), that.getTimeCreate())
                .append(getTimeModify(), that.getTimeModify())
                .append(getStatus(), that.getStatus())
                .append(getSegments(), that.getSegments())
                .append(getApplications(), that.getApplications())
                .append(isSrcWithMultiTable(), that.isSrcWithMultiTable())
                .append(isDestWithMultiTable(), that.isDestWithMultiTable())
                .append(getSharedBandwidthGroupId(), that.getSharedBandwidthGroupId())
                .isEquals();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getPathId(), getSrcSwitchId(), getDestSwitchId(), getFlowId(), getCookie(), getMeterId(),
                getLatency(), getBandwidth(), isIgnoreBandwidth(), getTimeCreate(), getTimeModify(), getStatus(),
                getSegments(), getApplications(), isSrcWithMultiTable(), isDestWithMultiTable(),
                getSharedBandwidthGroupId());
    }

    /**
     * Defines persistable data of the FlowPath.
     */
    public interface FlowPathData {
        PathId getPathId();

        void setPathId(PathId pathId);

        SwitchId getSrcSwitchId();

        Switch getSrcSwitch();

        void setSrcSwitch(Switch srcSwitch);

        SwitchId getDestSwitchId();

        Switch getDestSwitch();

        void setDestSwitch(Switch destSwitch);

        String getFlowId();

        Flow getFlow();

        PathId getHaFlowPathId();

        HaFlowPath getHaFlowPath();

        String getHaSubFlowId();

        HaSubFlow getHaSubFlow();

        void setHaSubFlow(HaSubFlow haSubFlow);

        FlowSegmentCookie getCookie();

        void setCookie(FlowSegmentCookie cookie);

        MeterId getMeterId();

        void setIngressMirrorGroupId(GroupId meterId);

        GroupId getIngressMirrorGroupId();

        void setMeterId(MeterId meterId);

        long getLatency();

        void setLatency(long latency);

        long getBandwidth();

        void setBandwidth(long bandwidth);

        boolean isIgnoreBandwidth();

        void setIgnoreBandwidth(boolean ignoreBandwidth);

        Instant getTimeCreate();

        void setTimeCreate(Instant timeCreate);

        Instant getTimeModify();

        void setTimeModify(Instant timeModify);

        FlowPathStatus getStatus();

        void setStatus(FlowPathStatus status);

        List<PathSegment> getSegments();

        void setSegments(List<PathSegment> segments);

        Set<FlowApplication> getApplications();

        void setApplications(Set<FlowApplication> applications);

        @Deprecated
        boolean isSrcWithMultiTable();

        @Deprecated
        void setSrcWithMultiTable(boolean srcWithMultiTable);

        @Deprecated
        boolean isDestWithMultiTable();

        @Deprecated
        void setDestWithMultiTable(boolean destWithMultiTable);

        Set<FlowMirrorPoints> getFlowMirrorPointsSet();

        void addFlowMirrorPoints(FlowMirrorPoints flowMirrorPoints);

        String getSharedBandwidthGroupId();

        void setSharedBandwidthGroupId(String sharedBandwidthGroupId);
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
    static final class FlowPathDataImpl implements FlowPathData, Serializable {
        private static final long serialVersionUID = 1L;
        @NonNull PathId pathId;
        @NonNull Switch srcSwitch;
        @NonNull Switch destSwitch;
        @Setter(AccessLevel.NONE)
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        Flow flow;
        @Setter(AccessLevel.NONE)
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        HaFlowPath haFlowPath;
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        HaSubFlow haSubFlow;
        FlowSegmentCookie cookie;
        MeterId meterId;
        GroupId ingressMirrorGroupId;
        long latency;
        long bandwidth;
        boolean ignoreBandwidth;
        Instant timeCreate;
        Instant timeModify;
        FlowPathStatus status;
        @Builder.Default
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        @NonNull List<PathSegment> segments = new ArrayList<>();
        Set<FlowApplication> applications;

        @Setter(AccessLevel.NONE)
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        final Set<FlowMirrorPoints> flowMirrorPointsSet = new HashSet<>();

        // The reference is used to link path segments back to the path. See {@link #setSegments(List)}.
        @Setter(AccessLevel.NONE)
        @Getter(AccessLevel.NONE)
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        FlowPath flowPath;

        @Deprecated
        boolean srcWithMultiTable;
        @Deprecated
        boolean destWithMultiTable;
        String sharedBandwidthGroupId;

        public void setPathId(PathId pathId) {
            this.pathId = pathId;

            if (segments != null) {
                segments.forEach(segment -> segment.getData().setPathId(pathId));
            }
        }

        @Override
        public String getFlowId() {
            return flow != null ? flow.getFlowId() : null;
        }

        @Override
        public PathId getHaFlowPathId() {
            return haFlowPath != null ? haFlowPath.getHaPathId() : null;
        }

        @Override
        public String getHaSubFlowId() {
            return haSubFlow != null ? haSubFlow.getHaSubFlowId() : null;
        }

        @Override
        public SwitchId getSrcSwitchId() {
            return srcSwitch.getSwitchId();
        }

        @Override
        public SwitchId getDestSwitchId() {
            return destSwitch.getSwitchId();
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
                data.setPathId(pathId);
                data.setSeqId(idx);
                data.setIgnoreBandwidth(ignoreBandwidth);
                data.setBandwidth(bandwidth);
                data.setSharedBandwidthGroupId(sharedBandwidthGroupId);
            }

            this.segments = new ArrayList<>(segments);
        }

        @Override
        public void addFlowMirrorPoints(FlowMirrorPoints flowMirrorPoints) {
            boolean toBeAdded = true;
            Iterator<FlowMirrorPoints> it = this.flowMirrorPointsSet.iterator();
            while (it.hasNext()) {
                FlowMirrorPoints each = it.next();
                if (flowMirrorPoints == each) {
                    toBeAdded = false;
                    break;
                }
                if (flowMirrorPoints.getMirrorSwitchId().equals(each.getMirrorSwitchId())
                        && flowMirrorPoints.getMirrorGroup().getGroupId().equals(each.getMirrorGroup().getGroupId())) {
                    it.remove();
                    // Quit as no duplicates expected.
                    break;
                }
            }
            if (toBeAdded) {
                this.flowMirrorPointsSet.add(flowMirrorPoints);
                FlowMirrorPointsData data = flowMirrorPoints.getData();
                if (data instanceof FlowMirrorPointsDataImpl) {
                    ((FlowMirrorPointsDataImpl) data).flowPath = this.flowPath;
                    data.getMirrorGroup().setPathId(this.flowPath.getPathId());
                }
            }
        }
    }

    /**
     * A cloner for FlowPath entity.
     */
    @Mapper(collectionMappingStrategy = CollectionMappingStrategy.TARGET_IMMUTABLE)
    public interface FlowPathCloner {
        FlowPathCloner INSTANCE = Mappers.getMapper(FlowPathCloner.class);

        void copy(FlowPathData source, @MappingTarget FlowPathData target);

        @Mapping(target = "srcSwitch", ignore = true)
        @Mapping(target = "destSwitch", ignore = true)
        @Mapping(target = "segments", ignore = true)
        @Mapping(target = "haSubFlow", ignore = true)
        void copyWithoutSwitchesHaSubFlowAndSegments(FlowPathData source, @MappingTarget FlowPathData target);

        /**
         * Performs deep copy of entity data.
         *
         * @param source the path data to copy from.
         * @param targetFlow the flow to be referred ({@code FlowPathData.getFlow()}) by the new path data.
         */
        default FlowPathData deepCopy(FlowPathData source, Flow targetFlow, HaSubFlow targetHaSubFlow) {
            FlowPathDataImpl result = new FlowPathDataImpl();
            copyWithoutSwitchesHaSubFlowAndSegments(source, result);
            result.flow = targetFlow;
            result.haSubFlow = targetHaSubFlow;
            result.setSrcSwitch(new Switch(source.getSrcSwitch()));
            result.setDestSwitch(new Switch(source.getDestSwitch()));
            result.setSegments(source.getSegments().stream()
                    .map(PathSegment::new)
                    .collect(Collectors.toList()));
            return result;
        }
    }
}
