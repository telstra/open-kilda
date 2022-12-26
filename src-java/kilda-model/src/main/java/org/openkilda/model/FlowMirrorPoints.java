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

import org.openkilda.model.FlowMirror.FlowMirrorData;
import org.openkilda.model.FlowMirror.FlowMirrorDataImpl;
import org.openkilda.model.FlowMirrorPoints.FlowMirrorPointsData;

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
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a flow mirror data.
 */
@DefaultSerializer(BeanSerializer.class)
@ToString
public class FlowMirrorPoints implements CompositeDataEntity<FlowMirrorPointsData> {
    @Getter
    @Setter
    @Delegate
    @JsonIgnore
    private FlowMirrorPointsData data;

    /**
     * No args constructor for deserialization purpose.
     */
    private FlowMirrorPoints() {
        data = new FlowMirrorPointsDataImpl();
    }

    /**
     * Cloning constructor which performs deep copy of the entity.
     *
     * @param entityToClone the path entity to copy data from.
     * @param flowPath the flow path to be referred ({@code FlowMirrorPoints.getFlowPath()}) by the new mirror points.
     */
    public FlowMirrorPoints(@NonNull FlowMirrorPoints entityToClone, FlowPath flowPath) {
        this();
        data = FlowMirrorPointsCloner.INSTANCE.deepCopy(entityToClone.getData(), flowPath, this);
    }

    @Builder
    public FlowMirrorPoints(@NonNull Switch mirrorSwitch, @NonNull MirrorGroup mirrorGroup) {
        FlowMirrorPointsDataImpl.FlowMirrorPointsDataImplBuilder dataBuilder = FlowMirrorPointsDataImpl.builder()
                .mirrorSwitch(mirrorSwitch).mirrorGroup(mirrorGroup);
        // The reference is used to link flow mirror paths back to the flow mirror points.
        // See {@link FlowMirrorPointsDataImpl#addPaths(FlowPath...)}.
        dataBuilder.flowMirrorPoints(this);

        data = dataBuilder.build();
    }

    public FlowMirrorPoints(@NonNull FlowMirrorPoints.FlowMirrorPointsData data) {
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
        FlowMirrorPoints that = (FlowMirrorPoints) o;
        return new EqualsBuilder()
                .append(getMirrorSwitchId(), that.getMirrorSwitchId())
                .append(getMirrorGroupId(), that.getMirrorGroupId())
                .append(getFlowPathId(), that.getFlowPathId())
                .append(new HashSet<>(getFlowMirrors()), new HashSet<>(that.getFlowMirrors()))
                .isEquals();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getMirrorSwitchId(), getMirrorGroupId(), getFlowPathId(), getFlowMirrors());
    }

    /**
     * Defines persistable data of the FlowMirrorPoints.
     */
    public interface FlowMirrorPointsData {

        SwitchId getMirrorSwitchId();

        Switch getMirrorSwitch();

        void setMirrorSwitch(Switch srcSwitch);

        GroupId getMirrorGroupId();

        MirrorGroup getMirrorGroup();

        void setMirrorGroup(MirrorGroup mirrorGroup);

        PathId getFlowPathId();

        FlowPath getFlowPath();

        Collection<FlowMirror> getFlowMirrors();

        Set<String> getFlowMirrorIds();

        Optional<FlowMirror> getFlowMirror(String flowMirror);

        void addFlowMirrors(FlowMirror... flowMirrors);
    }

    /**
     * POJO implementation of FlowPathData.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static final class FlowMirrorPointsDataImpl implements FlowMirrorPointsData, Serializable {
        private static final long serialVersionUID = 1L;
        @NonNull Switch mirrorSwitch;
        @NonNull MirrorGroup mirrorGroup;

        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        final Set<FlowMirror> flowMirrors = new HashSet<>();

        @Setter(AccessLevel.NONE)
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        FlowPath flowPath;

        // The reference is used to link flow mirror paths back to the flow mirror points.
        // See {@link FlowMirrorPointsDataImpl#addPaths(FlowPath...)}.
        @Setter(AccessLevel.NONE)
        @Getter(AccessLevel.NONE)
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        FlowMirrorPoints flowMirrorPoints;

        @Override
        public SwitchId getMirrorSwitchId() {
            return mirrorSwitch.getSwitchId();
        }

        @Override
        public GroupId getMirrorGroupId() {
            return mirrorGroup.getGroupId();
        }

        @Override
        public Set<String> getFlowMirrorIds() {
            return flowMirrors.stream().map(FlowMirror::getFlowMirrorId).collect(Collectors.toSet());
        }

        @Override
        public PathId getFlowPathId() {
            return flowPath != null ? flowPath.getPathId() : null;
        }

        /**
         * Add and associate flow path(s) with the flow.
         */
        @Override
        public void addFlowMirrors(FlowMirror... flowMirrors) {
            for (FlowMirror mirrorToAdd : flowMirrors) {
                boolean toBeAdded = true;
                Iterator<FlowMirror> it = this.flowMirrors.iterator();
                while (it.hasNext()) {
                    FlowMirror each = it.next();
                    if (mirrorToAdd == each) {
                        toBeAdded = false;
                        break;
                    }
                    if (mirrorToAdd.getFlowMirrorId().equals(each.getFlowMirrorId())) {
                        it.remove();
                        // Quit as no duplicates expected.
                        break;
                    }
                }
                if (toBeAdded) {
                    this.flowMirrors.add(mirrorToAdd);
                    FlowMirrorData data = mirrorToAdd.getData();
                    if (data instanceof FlowMirrorDataImpl) {
                        ((FlowMirrorDataImpl) data).flowMirrorPoints = flowMirrorPoints;
                    }
                }
            }
        }

        /**
         * Get an associated flow mirror by id.
         */
        @Override
        public Optional<FlowMirror> getFlowMirror(String flowMirrorId) {
            return flowMirrors.stream()
                    .filter(mirror -> mirror.getFlowMirrorId().equals(flowMirrorId))
                    .findAny();
        }
    }

    /**
     * A cloner for FlowPath entity.
     */
    @Mapper(collectionMappingStrategy = CollectionMappingStrategy.TARGET_IMMUTABLE)
    public interface FlowMirrorPointsCloner {
        FlowMirrorPointsCloner INSTANCE = Mappers.getMapper(FlowMirrorPointsCloner.class);

        @Mapping(target = "flowMirrors", ignore = true)
        @Mapping(target = "flowMirrorIds", ignore = true)
        void copyWithoutMirrors(FlowMirrorPointsData source, @MappingTarget FlowMirrorPointsData target);

        @Mapping(target = "mirrorSwitch", ignore = true)
        @Mapping(target = "flowMirrors", ignore = true)
        @Mapping(target = "flowMirrorIds", ignore = true)
        void copyWithoutSwitchesAndMirrors(FlowMirrorPointsData source, @MappingTarget FlowMirrorPointsData target);

        /**
         * Performs deep copy of entity data.
         *
         * @param source the path data to copy from.
         */
        default FlowMirrorPointsData deepCopy(FlowMirrorPointsData source,
                                              FlowPath flowPath, FlowMirrorPoints targetFlowMirrorPoints) {
            FlowMirrorPointsDataImpl result = new FlowMirrorPointsDataImpl();
            result.flowPath = flowPath;
            result.flowMirrorPoints = targetFlowMirrorPoints;
            copyWithoutSwitchesAndMirrors(source, result);
            result.setMirrorSwitch(new Switch(source.getMirrorSwitch()));
            result.addFlowMirrors(source.getFlowMirrors().stream()
                    .map(mirror -> new FlowMirror(mirror, targetFlowMirrorPoints))
                    .toArray(FlowMirror[]::new));
            return result;
        }
    }
}
