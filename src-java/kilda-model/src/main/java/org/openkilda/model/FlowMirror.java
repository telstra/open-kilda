/* Copyright 2022 Telstra Open Source
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

import org.openkilda.model.FlowMirror.FlowMirrorData;

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
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a flow mirror path.
 */
@DefaultSerializer(BeanSerializer.class)
@ToString
public class FlowMirror implements CompositeDataEntity<FlowMirrorData> {
    @Getter
    @Setter
    @Delegate
    @JsonIgnore
    private FlowMirrorData data;

    /**
     * No args constructor for deserialization purpose.
     */
    private FlowMirror() {
        data = new FlowMirrorDataImpl();
    }

    /**
     * Cloning constructor which performs deep copy of the entity.
     *
     * @param entityToClone the path entity to copy data from.
     */
    public FlowMirror(@NonNull FlowMirror entityToClone) {
        this();
        data = FlowMirrorCloner.INSTANCE.deepCopy(entityToClone.getData(), this);
    }

    @Builder
    public FlowMirror(
            @NonNull String flowMirrorId, @NonNull Switch mirrorSwitch, @NonNull Switch egressSwitch, int egressPort,
            int egressOuterVlan, int egressInnerVlan, FlowPathStatus status) {
        FlowMirrorDataImpl.FlowMirrorDataImplBuilder dataBuilder = FlowMirrorDataImpl.builder()
                .flowMirrorId(flowMirrorId)
                .mirrorSwitch(mirrorSwitch)
                .egressSwitch(egressSwitch)
                .egressPort(egressPort)
                .egressOuterVlan(egressOuterVlan)
                .egressInnerVlan(egressInnerVlan)
                .status(status);

        data = dataBuilder.build();

        // The reference is used to link flow mirror paths back to the flow mirror.
        // See {@link FlowMirrorDataImpl#addPaths(FlowMirrorPath...)}.
        ((FlowMirrorDataImpl) data).flowMirror = this;
    }

    public FlowMirror(@NonNull FlowMirror.FlowMirrorData data) {
        this.data = data;
    }

    /**
     * Checks whether the flow mirror goes through a single switch.
     *
     * @return true if source and destination switches are the same, otherwise false
     */
    public boolean isOneSwitchMirror() {
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
        FlowMirror that = (FlowMirror) o;
        return new EqualsBuilder()
                .append(getFlowMirrorId(), that.getFlowMirrorId())
                .append(getForwardPathId(), that.getForwardPathId())
                .append(getMirrorSwitchId(), that.getMirrorSwitchId())
                .append(getEgressSwitchId(), that.getEgressSwitchId())
                .append(getEgressPort(), that.getEgressPort())
                .append(getEgressOuterVlan(), that.getEgressOuterVlan())
                .append(getEgressInnerVlan(), that.getEgressInnerVlan())
                .append(getTimeCreate(), that.getTimeCreate())
                .append(getTimeModify(), that.getTimeModify())
                .append(getStatus(), that.getStatus())
                .append(getMirrorPaths(), that.getMirrorPaths())
                .isEquals();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getFlowMirrorId(), getForwardPathId(), getMirrorSwitchId(), getEgressSwitchId(),
                getEgressPort(), getEgressOuterVlan(), getEgressInnerVlan(), getTimeCreate(), getTimeModify(),
                getStatus(), getMirrorPaths());
    }

    /**
     * Defines persistable data of the FlowMirrorPath.
     */
    public interface FlowMirrorData {

        String getFlowMirrorId();

        void setFlowMirrorId(String flowMirrorId);

        PathId getForwardPathId();

        void setForwardPathId(PathId forwardPathId);

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

        FlowPathStatus getStatus();

        void setStatus(FlowPathStatus status);

        Set<FlowMirrorPath> getMirrorPaths();

        Instant getTimeCreate();

        void setTimeCreate(Instant timeCreate);

        Instant getTimeModify();

        void setTimeModify(Instant timeModify);
    }

    /**
     * POJO implementation of FlowMirrorPathData.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static final class FlowMirrorDataImpl implements FlowMirrorData, Serializable {
        private static final long serialVersionUID = 1L;
        @NonNull String flowMirrorId;
        PathId forwardPathId;
        @NonNull Switch mirrorSwitch;
        @NonNull Switch egressSwitch;
        int egressPort;
        int egressOuterVlan;
        int egressInnerVlan;
        @Setter(AccessLevel.NONE)
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        FlowMirrorPoints flowMirrorPoints;
        Instant timeCreate;
        Instant timeModify;
        FlowPathStatus status;
        @Builder.Default
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        @Setter(AccessLevel.NONE)
        @NonNull Set<FlowMirrorPath> mirrorPaths = new HashSet<>();
        // The reference is used to link mirror paths back to the mirror.
        // See {@link FlowMirrorDataImpl#addPaths(FlowMirrorPath...)}.
        @Setter(AccessLevel.NONE)
        @Getter(AccessLevel.NONE)
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        FlowMirror flowMirror;

        public void setForwardPathId(PathId forwardPathId) {
            this.forwardPathId = forwardPathId;
        }

        @Override
        public SwitchId getMirrorSwitchId() {
            return mirrorSwitch.getSwitchId();
        }

        @Override
        public SwitchId getEgressSwitchId() {
            return egressSwitch.getSwitchId();
        }

        @Override
        public Set<FlowMirrorPath> getMirrorPaths() {
            return new HashSet<>(mirrorPaths);
        }
    }

    /**
     * A cloner for FlowPath entity.
     */
    @Mapper(collectionMappingStrategy = CollectionMappingStrategy.TARGET_IMMUTABLE)
    public interface FlowMirrorCloner {
        FlowMirrorCloner INSTANCE = Mappers.getMapper(FlowMirrorCloner.class);

        @Mapping(target = "mirrorPaths", ignore = true)
        void copyWithoutPaths(FlowMirrorData source, @MappingTarget FlowMirrorData target);

        @Mapping(target = "mirrorSwitch", ignore = true)
        @Mapping(target = "egressSwitch", ignore = true)
        @Mapping(target = "mirrorPaths", ignore = true)
        void copyWithoutSwitchesFlowMirrorAndPaths(FlowMirrorData source, @MappingTarget FlowMirrorData target);

        /**
         * Performs deep copy of entity data.
         *
         * @param source the path data to copy from.
         */
        default FlowMirrorData deepCopy(FlowMirrorData source, FlowMirror targetFlowMirror) {
            FlowMirrorDataImpl result = new FlowMirrorDataImpl();
            result.flowMirror = targetFlowMirror;
            copyWithoutSwitchesFlowMirrorAndPaths(source, result);
            result.setMirrorSwitch(new Switch(source.getMirrorSwitch()));
            result.setEgressSwitch(new Switch(source.getEgressSwitch()));
            return result;
        }

    }
}
