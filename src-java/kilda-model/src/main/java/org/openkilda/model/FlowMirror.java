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

import static java.lang.String.format;

import org.openkilda.model.FlowMirror.FlowMirrorData;
import org.openkilda.model.FlowMirrorPath.FlowMirrorPathData;
import org.openkilda.model.FlowMirrorPath.FlowMirrorPathDataImpl;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
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
    public FlowMirror(@NonNull FlowMirror entityToClone, FlowMirrorPoints flowMirrorPoints) {
        this();
        data = FlowMirrorCloner.INSTANCE.deepCopy(entityToClone.getData(), flowMirrorPoints, this);
    }

    @Builder
    public FlowMirror(
            @NonNull String flowMirrorId, @NonNull Switch mirrorSwitch, @NonNull Switch egressSwitch, int egressPort,
            int egressOuterVlan, int egressInnerVlan, FlowPathStatus status, PathId forwardPathId,
            PathId reversePathId) {
        FlowMirrorDataImpl.FlowMirrorDataImplBuilder dataBuilder = FlowMirrorDataImpl.builder()
                .flowMirrorId(flowMirrorId)
                .mirrorSwitch(mirrorSwitch)
                .egressSwitch(egressSwitch)
                .egressPort(egressPort)
                .egressOuterVlan(egressOuterVlan)
                .egressInnerVlan(egressInnerVlan)
                .forwardPathId(forwardPathId)
                .reversePathId(reversePathId)
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

    /**
     * Return opposite pathId to passed pathId.
     */
    public Optional<PathId> getOppositePathId(PathId pathId) {
        Optional<Long> requestedPathCookie = getPath(pathId)
                .map(FlowMirrorPath::getCookie)
                .map(FlowSegmentCookie::getFlowEffectiveId);
        if (requestedPathCookie.isPresent()) {
            return getMirrorPaths().stream()
                    .filter(path -> !path.getMirrorPathId().equals(pathId))
                    .filter(path -> path.getCookie().getFlowEffectiveId() == requestedPathCookie.get())
                    .findAny()
                    .map(FlowMirrorPath::getMirrorPathId);
        } else {
            throw new IllegalArgumentException(format("Flow mirror %s does not have mirror path %s",
                    getFlowMirrorId(), pathId));
        }
    }

    /**
     * Returns forward path.
     */
    public FlowMirrorPath getForwardPath() {
        if (getForwardPathId() == null) {
            return null;
        }
        return getPath(getForwardPathId()).orElse(null);
    }

    /**
     * Add a path and set it as the forward path.
     */
    public void setForwardPath(FlowMirrorPath forwardPath) {
        if (forwardPath != null) {
            if (!hasPath(forwardPath)) {
                validatePath(forwardPath, FlowPathDirection.FORWARD);
                addMirrorPaths(forwardPath);
            }
            setForwardPathId(forwardPath.getMirrorPathId());
        } else {
            setForwardPathId(null);
        }
    }

    /**
     * Get the reverse path.
     */
    public FlowMirrorPath getReversePath() {
        if (getReversePathId() == null) {
            return null;
        }
        return getPath(getReversePathId()).orElse(null);
    }

    /**
     * Add a path and set it as the reverse path.
     */
    public void setReversePath(FlowMirrorPath reversePath) {
        if (reversePath != null) {
            if (!hasPath(reversePath)) {
                validatePath(reversePath, FlowPathDirection.REVERSE);
                addMirrorPaths(reversePath);
            }
            setReversePathId(reversePath.getMirrorPathId());
        } else {
            setReversePathId(null);
        }
    }

    private void validatePath(FlowMirrorPath path, FlowPathDirection direction) {
        if (!Objects.equals(path.getMirrorSwitchId(), getMirrorSwitchId())) {
            throw new IllegalArgumentException(format("Mirror path %s must have %s direction, but its mirror switch "
                    + "id %s is not equal to flow mirror switch id %s", path.getMirrorPathId(), direction,
                    path.getMirrorSwitchId(), getMirrorSwitchId()));
        }
        if (!Objects.equals(path.getEgressSwitchId(), getEgressSwitchId())) {
            throw new IllegalArgumentException(format("Mirror path %s must have %s direction, but its egress switch "
                    + "id %s is not equal to flow mirror egress switch id %s", path.getMirrorPathId(), direction,
                    path.getEgressSwitchId(), getEgressSwitchId()));
        }
        if (path.getCookie() == null) {
            throw new IllegalArgumentException(format("Mirror path %s must have %s direction, but its cookie is null "
                            + "so it is not possible to get path direction", path.getMirrorPathId(), direction));
        }

        if (path.getCookie().getDirection() != direction) {
            throw new IllegalArgumentException(format("Mirror path %s must have %s direction, but its direction is %s",
                    path.getMirrorPathId(), direction, path.getCookie().getDirection()));
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
        FlowMirror that = (FlowMirror) o;
        return new EqualsBuilder()
                .append(getFlowMirrorId(), that.getFlowMirrorId())
                .append(getForwardPathId(), that.getForwardPathId())
                .append(getReversePathId(), that.getReversePathId())
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
        return Objects.hash(getFlowMirrorId(), getForwardPathId(), getReversePathId(), getMirrorSwitchId(),
                getEgressSwitchId(), getEgressPort(), getEgressOuterVlan(), getEgressInnerVlan(), getTimeCreate(),
                getTimeModify(), getStatus(), getMirrorPaths());
    }

    /**
     * Defines persistable data of the FlowMirrorPath.
     */
    public interface FlowMirrorData {

        String getFlowMirrorId();

        void setFlowMirrorId(String flowMirrorId);

        PathId getForwardPathId();

        void setForwardPathId(PathId forwardPathId);

        PathId getReversePathId();

        void setReversePathId(PathId reversePathId);

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

        Optional<FlowMirrorPath> getPath(PathId pathId);

        void addMirrorPaths(FlowMirrorPath... mirrorPaths);

        FlowMirrorPoints getFlowMirrorPoints();

        boolean hasPath(FlowMirrorPath path);

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
        PathId reversePathId;
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
            mirrorPaths.forEach(path -> path.getData().setMirrorPathId(forwardPathId));
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

        @Override
        public Optional<FlowMirrorPath> getPath(PathId pathId) {
            return mirrorPaths.stream()
                    .filter(path -> path.getMirrorPathId().equals(pathId))
                    .findAny();
        }

        /**
         * Add and associate flow path(s) with the flow.
         */
        @Override
        public void addMirrorPaths(FlowMirrorPath... mirrorPaths) {
            for (FlowMirrorPath pathToAdd : mirrorPaths) {
                boolean toBeAdded = true;
                Iterator<FlowMirrorPath> it = this.mirrorPaths.iterator();
                while (it.hasNext()) {
                    FlowMirrorPath each = it.next();
                    if (pathToAdd == each) {
                        toBeAdded = false;
                        break;
                    }
                    if (pathToAdd.getMirrorPathId().equals(each.getMirrorPathId())) {
                        it.remove();
                        // Quit as no duplicates expected.
                        break;
                    }
                }
                if (toBeAdded) {
                    this.mirrorPaths.add(pathToAdd);
                    FlowMirrorPathData data = pathToAdd.getData();
                    if (data instanceof FlowMirrorPathDataImpl) {
                        ((FlowMirrorPathDataImpl) data).flowMirror = flowMirror;
                    }
                }
            }
        }

        @Override
        public boolean hasPath(FlowMirrorPath path) {
            return mirrorPaths.contains(path);
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
        @Mapping(target = "flowMirrorPoints", ignore = true)
        @Mapping(target = "mirrorPaths", ignore = true)
        void copyWithoutSwitchesFlowMirrorAndPaths(FlowMirrorData source, @MappingTarget FlowMirrorData target);

        /**
         * Performs deep copy of entity data.
         *
         * @param source the path data to copy from.
         */
        default FlowMirrorData deepCopy(
                FlowMirrorData source, FlowMirrorPoints flowMirrorPoints, FlowMirror targetFlowMirror) {
            FlowMirrorDataImpl result = new FlowMirrorDataImpl();
            result.flowMirrorPoints = flowMirrorPoints;
            result.flowMirror = targetFlowMirror;
            copyWithoutSwitchesFlowMirrorAndPaths(source, result);
            result.setMirrorSwitch(new Switch(source.getMirrorSwitch()));
            result.setEgressSwitch(new Switch(source.getEgressSwitch()));
            for (FlowMirrorPath mirrorPath : source.getMirrorPaths()) {
                result.addMirrorPaths(new FlowMirrorPath(mirrorPath, targetFlowMirror));
            }
            return result;
        }

    }
}
