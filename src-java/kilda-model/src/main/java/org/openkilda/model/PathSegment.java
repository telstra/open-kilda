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
import java.util.Objects;

/**
 * Represents a segment of a flow path.
 */
@DefaultSerializer(BeanSerializer.class)
@ToString
public class PathSegment implements CompositeDataEntity<PathSegment.PathSegmentData> {
    @Getter
    @Setter
    @Delegate(excludes = PathSegmentInternalData.class)
    @JsonIgnore
    private PathSegmentData data;

    /**
     * No args constructor for deserialization purpose.
     */
    private PathSegment() {
        data = new PathSegmentDataImpl();
    }

    /**
     * Cloning constructor which performs deep copy of the entity.
     *
     * @param entityToClone the segment entity to copy entity data from.
     */
    public PathSegment(@NonNull PathSegment entityToClone) {
        data = PathSegmentCloner.INSTANCE.deepCopy(entityToClone.getData());
    }

    @Builder
    public PathSegment(@NonNull PathId pathId, @NonNull Switch srcSwitch, @NonNull Switch destSwitch,
                       int srcPort, int destPort,
                       boolean srcWithMultiTable, boolean destWithMultiTable, int seqId, Long latency, long bandwidth,
                       boolean ignoreBandwidth, boolean failed, String sharedBandwidthGroupId) {
        data = PathSegmentDataImpl.builder().pathId(pathId).srcSwitch(srcSwitch).destSwitch(destSwitch)
                .srcPort(srcPort).destPort(destPort).srcWithMultiTable(srcWithMultiTable)
                .destWithMultiTable(destWithMultiTable).seqId(seqId).latency(latency).bandwidth(bandwidth)
                .ignoreBandwidth(ignoreBandwidth).failed(failed).sharedBandwidthGroupId(sharedBandwidthGroupId).build();
    }

    public PathSegment(@NonNull PathSegmentData data) {
        this.data = data;
    }

    /**
     * Checks whether endpoint belongs to segment or not.
     *
     * @param switchId target switch
     * @param port target port
     * @return result of check
     */
    public boolean containsNode(SwitchId switchId, int port) {
        if (switchId == null) {
            throw new IllegalArgumentException("Switch id must be not null");
        }
        return (switchId.equals(getSrcSwitchId()) && port == getSrcPort())
                || (switchId.equals(getDestSwitchId()) && port == getDestPort());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PathSegment that = (PathSegment) o;
        return new EqualsBuilder()
                .append(getSrcPort(), that.getSrcPort())
                .append(getDestPort(), that.getDestPort())
                .append(isSrcWithMultiTable(), that.isSrcWithMultiTable())
                .append(isDestWithMultiTable(), that.isDestWithMultiTable())
                .append(getSeqId(), that.getSeqId())
                .append(isFailed(), that.isFailed())
                .append(getPathId(), that.getPathId())
                .append(getSrcSwitchId(), that.getSrcSwitchId())
                .append(getDestSwitchId(), that.getDestSwitchId())
                .append(getLatency(), that.getLatency())
                .append(getBandwidth(), that.getBandwidth())
                .append(getSharedBandwidthGroupId(), that.getSharedBandwidthGroupId())
                .isEquals();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getPathId(), getSrcSwitchId(), getDestSwitchId(), getSrcPort(), getDestPort(),
                isSrcWithMultiTable(), isDestWithMultiTable(), getSeqId(), getLatency(), getBandwidth(), isFailed(),
                getSharedBandwidthGroupId());
    }

    /**
     * Defines persistable data of the PathSegment.
     */
    public interface PathSegmentData {
        PathId getPathId();

        void setPathId(PathId pathId);

        SwitchId getSrcSwitchId();

        Switch getSrcSwitch();

        void setSrcSwitch(Switch srcSwitch);

        SwitchId getDestSwitchId();

        Switch getDestSwitch();

        void setDestSwitch(Switch destSwitch);

        int getSrcPort();

        void setSrcPort(int srcPort);

        int getDestPort();

        void setDestPort(int destPort);

        @Deprecated
        boolean isSrcWithMultiTable();

        @Deprecated
        void setSrcWithMultiTable(boolean srcWithMultiTable);

        @Deprecated
        boolean isDestWithMultiTable();

        @Deprecated
        void setDestWithMultiTable(boolean destWithMultiTable);

        int getSeqId();

        void setSeqId(int seqId);

        Long getLatency();

        void setLatency(Long latency);

        long getBandwidth();

        void setBandwidth(long bandwidth);

        boolean isIgnoreBandwidth();

        void setIgnoreBandwidth(boolean ignoreBandwidth);

        boolean isFailed();

        void setFailed(boolean failed);

        String getSharedBandwidthGroupId();

        void setSharedBandwidthGroupId(String sharedBandwidthGroupId);
    }

    /**
     * Defines methods which don't need to be delegated.
     */
    interface PathSegmentInternalData {
        void setPathId(PathId pathId);

        void setSeqId(int seqId);

        void setBandwidth(long bandwidth);

        void setIgnoreBandwidth(boolean ignoreBandwidth);

        void setSharedBandwidthGroupId(String sharedBandwidthGroupId);
    }

    /**
     * POJO implementation of PathSegmentData.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static final class PathSegmentDataImpl implements PathSegmentData, Serializable {
        private static final long serialVersionUID = 1L;
        @NonNull PathId pathId;
        @NonNull Switch srcSwitch;
        @NonNull Switch destSwitch;
        int srcPort;
        int destPort;
        @Deprecated
        boolean srcWithMultiTable;
        @Deprecated
        boolean destWithMultiTable;
        int seqId;
        Long latency;
        long bandwidth;
        boolean ignoreBandwidth;
        boolean failed;
        String sharedBandwidthGroupId;

        @Override
        public SwitchId getSrcSwitchId() {
            return srcSwitch.getSwitchId();
        }

        @Override
        public SwitchId getDestSwitchId() {
            return destSwitch.getSwitchId();
        }
    }

    /**
     * A cloner for PathSegment entity.
     */
    @Mapper
    public interface PathSegmentCloner {
        PathSegmentCloner INSTANCE = Mappers.getMapper(PathSegmentCloner.class);

        void copy(PathSegmentData source, @MappingTarget PathSegmentData target);

        @Mapping(target = "srcSwitch", ignore = true)
        @Mapping(target = "destSwitch", ignore = true)
        void copyWithoutSwitches(PathSegmentData source, @MappingTarget PathSegmentData target);

        /**
         * Performs deep copy of entity data.
         */
        default PathSegmentData deepCopy(PathSegmentData source) {
            PathSegmentDataImpl result = new PathSegmentDataImpl();
            copyWithoutSwitches(source, result);
            result.setSrcSwitch(new Switch(source.getSrcSwitch()));
            result.setDestSwitch(new Switch(source.getDestSwitch()));
            return result;
        }
    }
}
