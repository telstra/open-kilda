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

import org.openkilda.model.FlowMirrorPath.FlowMirrorPathData;
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
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents a flow mirror path.
 */
@DefaultSerializer(BeanSerializer.class)
@ToString
public class FlowMirrorPath implements CompositeDataEntity<FlowMirrorPathData> {
    @Getter
    @Setter
    @Delegate
    @JsonIgnore
    private FlowMirrorPathData data;

    /**
     * No args constructor for deserialization purpose.
     */
    private FlowMirrorPath() {
        data = new FlowMirrorPathDataImpl();
    }

    /**
     * Cloning constructor which performs deep copy of the entity.
     *
     * @param entityToClone the path entity to copy data from.
     */
    public FlowMirrorPath(@NonNull FlowMirrorPath entityToClone, FlowMirrorPoints flowMirrorPoints) {
        data = FlowMirrorPathCloner.INSTANCE.deepCopy(entityToClone.getData(), flowMirrorPoints);
    }

    @Builder
    public FlowMirrorPath(@NonNull PathId pathId, @NonNull Switch mirrorSwitch, @NonNull Switch egressSwitch,
                          int egressPort, int egressOuterVlan, int egressInnerVlan, FlowSegmentCookie cookie,
                          long bandwidth, boolean ignoreBandwidth, FlowPathStatus status, List<PathSegment> segments,
                          boolean egressWithMultiTable) {
        FlowMirrorPathDataImpl.FlowMirrorPathDataImplBuilder dataBuilder = FlowMirrorPathDataImpl.builder()
                .pathId(pathId).mirrorSwitch(mirrorSwitch).egressSwitch(egressSwitch).egressPort(egressPort)
                .egressOuterVlan(egressOuterVlan).egressInnerVlan(egressInnerVlan).cookie(cookie).bandwidth(bandwidth)
                .ignoreBandwidth(ignoreBandwidth).status(status).egressWithMultiTable(egressWithMultiTable);

        if (segments != null && !segments.isEmpty()) {
            dataBuilder.segments(segments);
        }

        data = dataBuilder.build();
    }

    public FlowMirrorPath(@NonNull FlowMirrorPath.FlowMirrorPathData data) {
        this.data = data;
    }

    /**
     * Checks whether the flow path goes through a single switch.
     *
     * @return true if source and destination switches are the same, otherwise false
     */
    public boolean isSingleSwitchPath() {
        return getMirrorSwitchId().equals(getEgressSwitchId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FlowMirrorPath that = (FlowMirrorPath) o;
        return new EqualsBuilder()
                .append(getPathId(), that.getPathId())
                .append(getMirrorSwitchId(), that.getMirrorSwitchId())
                .append(getEgressSwitchId(), that.getEgressSwitchId())
                .append(getEgressPort(), that.getEgressPort())
                .append(getEgressOuterVlan(), that.getEgressOuterVlan())
                .append(getEgressInnerVlan(), that.getEgressInnerVlan())
                .append(getCookie(), that.getCookie())
                .append(getBandwidth(), that.getBandwidth())
                .append(isIgnoreBandwidth(), that.isIgnoreBandwidth())
                .append(getTimeCreate(), that.getTimeCreate())
                .append(getTimeModify(), that.getTimeModify())
                .append(getStatus(), that.getStatus())
                .append(getSegments(), that.getSegments())
                .append(isEgressWithMultiTable(), that.isEgressWithMultiTable())
                .isEquals();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getPathId(), getMirrorSwitchId(), getEgressSwitchId(), getEgressPort(),
                getEgressOuterVlan(), getEgressInnerVlan(), getBandwidth(), isIgnoreBandwidth(), getTimeCreate(),
                getTimeModify(), getStatus(), getSegments(), isEgressWithMultiTable());
    }

    /**
     * Defines persistable data of the FlowMirrorPath.
     */
    public interface FlowMirrorPathData {
        PathId getPathId();

        void setPathId(PathId pathId);

        SwitchId getMirrorSwitchId();

        Switch getMirrorSwitch();

        void setMirrorSwitch(Switch mirrorSwitch);

        SwitchId getEgressSwitchId();

        Switch getEgressSwitch();

        void setEgressSwitch(Switch egressSwitch);

        int getEgressPort();

        void setEgressPort(int egressPort);

        int getEgressOuterVlan();

        void setEgressOuterVlan(int egressOuterVlan);

        int getEgressInnerVlan();

        void setEgressInnerVlan(int egressInnerVlan);

        FlowSegmentCookie getCookie();

        void setCookie(FlowSegmentCookie cookie);

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

        boolean isEgressWithMultiTable();

        void setEgressWithMultiTable(boolean destWithMultiTable);

        FlowMirrorPoints getFlowMirrorPoints();
    }

    /**
     * POJO implementation of FlowMirrorPathData.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static final class FlowMirrorPathDataImpl implements FlowMirrorPathData, Serializable {
        private static final long serialVersionUID = 1L;
        @NonNull PathId pathId;
        @NonNull Switch mirrorSwitch;
        @NonNull Switch egressSwitch;
        int egressPort;
        int egressOuterVlan;
        int egressInnerVlan;
        @Setter(AccessLevel.NONE)
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        FlowMirrorPoints flowMirrorPoints;
        FlowSegmentCookie cookie;
        long bandwidth;
        boolean ignoreBandwidth;
        Instant timeCreate;
        Instant timeModify;
        FlowPathStatus status;
        @Builder.Default
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        @NonNull List<PathSegment> segments = new ArrayList<>();

        public void setPathId(PathId pathId) {
            this.pathId = pathId;

            if (segments != null) {
                segments.forEach(segment -> segment.getData().setPathId(pathId));
            }
        }

        boolean egressWithMultiTable;

        @Override
        public SwitchId getMirrorSwitchId() {
            return mirrorSwitch.getSwitchId();
        }

        @Override
        public SwitchId getEgressSwitchId() {
            return egressSwitch.getSwitchId();
        }

        public void setBandwidth(long bandwidth) {
            this.bandwidth = bandwidth;

            if (segments != null) {
                segments.forEach(segment -> segment.getData().setBandwidth(bandwidth));
            }
        }

        public void setIgnoreBandwidth(boolean ignoreBandwidth) {
            this.ignoreBandwidth = ignoreBandwidth;

            if (segments != null) {
                segments.forEach(segment -> segment.getData().setIgnoreBandwidth(ignoreBandwidth));
            }
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
                // The reference is used to link path segments back to the mirror path. See {@link #setSegments(List)}.
                data.setPathId(pathId);
                data.setSeqId(idx);
                data.setIgnoreBandwidth(ignoreBandwidth);
                data.setBandwidth(bandwidth);
            }

            this.segments = new ArrayList<>(segments);
        }
    }

    /**
     * A cloner for FlowPath entity.
     */
    @Mapper(collectionMappingStrategy = CollectionMappingStrategy.TARGET_IMMUTABLE)
    public interface FlowMirrorPathCloner {
        FlowMirrorPathCloner INSTANCE = Mappers.getMapper(FlowMirrorPathCloner.class);

        void copy(FlowMirrorPathData source, @MappingTarget FlowMirrorPathData target);

        @Mapping(target = "mirrorSwitch", ignore = true)
        @Mapping(target = "egressSwitch", ignore = true)
        @Mapping(target = "segments", ignore = true)
        void copyWithoutSwitchesAndSegments(FlowMirrorPathData source, @MappingTarget FlowMirrorPathData target);

        /**
         * Performs deep copy of entity data.
         *
         * @param source the path data to copy from.
         */
        default FlowMirrorPathData deepCopy(FlowMirrorPathData source, FlowMirrorPoints flowMirrorPoints) {
            FlowMirrorPathDataImpl result = new FlowMirrorPathDataImpl();
            result.flowMirrorPoints = flowMirrorPoints;
            copyWithoutSwitchesAndSegments(source, result);
            result.setMirrorSwitch(new Switch(source.getMirrorSwitch()));
            result.setEgressSwitch(new Switch(source.getEgressSwitch()));
            result.setSegments(source.getSegments().stream()
                    .map(PathSegment::new)
                    .collect(Collectors.toList()));
            return result;
        }
    }
}
