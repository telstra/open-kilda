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

import org.openkilda.model.LagLogicalPort.LagLogicalPortData;
import org.openkilda.model.PhysicalPort.PhysicalPortDataImpl;

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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@DefaultSerializer(BeanSerializer.class)
@ToString
public class LagLogicalPort implements CompositeDataEntity<LagLogicalPortData> {
    @Getter
    @Setter
    @Delegate
    @JsonIgnore
    private LagLogicalPortData data;

    /**
     * No args constructor for deserialization purpose.
     */
    private LagLogicalPort() {
        data = new LagLogicalPortDataImpl();
    }

    /**
     * Cloning constructor which performs deep copy of the entity.
     *
     * @param entityToClone the LAG logical port entity to copy data from.
     */
    public LagLogicalPort(@NonNull LagLogicalPort entityToClone) {
        this();
        data = LagLogicalPortCloner.INSTANCE.deepCopy(entityToClone.getData(), this);
    }

    public LagLogicalPort(@NonNull SwitchId switchId, int logicalPortNumber) {
        this(switchId, logicalPortNumber, new ArrayList<>());
    }

    @Builder
    public LagLogicalPort(@NonNull SwitchId switchId, int logicalPortNumber, List<PhysicalPort> physicalPorts) {
        data = LagLogicalPortDataImpl.builder()
                .switchId(switchId)
                .logicalPortNumber(logicalPortNumber)
                .build();
        // The reference is used to link physical ports back to the LAG port. See {@link #setPhysicalPorts(List)}.
        ((LagLogicalPortDataImpl) data).lagLogicalPort = this;

        if (physicalPorts != null && !physicalPorts.isEmpty()) {
            data.setPhysicalPorts(physicalPorts);
        }
    }

    public LagLogicalPort(@NonNull LagLogicalPort.LagLogicalPortData data) {
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
        LagLogicalPort that = (LagLogicalPort) o;
        return new EqualsBuilder()
                .append(getSwitchId(), that.getSwitchId())
                .append(getLogicalPortNumber(), that.getLogicalPortNumber())
                .isEquals();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSwitchId(), getLogicalPortNumber());
    }

    public static int generateLogicalPortNumber(Collection<Integer> physicalPorts, int lagPortOffset) {
        return physicalPorts.stream().min(Integer::compareTo).map(port -> port + lagPortOffset).orElseThrow(
                () -> new IllegalArgumentException("No physical ports provided"));
    }

    /**
     * Defines persistable data of the LagLogicalPort.
     */
    public interface LagLogicalPortData {
        int getLogicalPortNumber();

        void setLogicalPortNumber(int logicalPortNumber);

        SwitchId getSwitchId();

        void setSwitchId(SwitchId switchId);

        List<PhysicalPort> getPhysicalPorts();

        void setPhysicalPorts(List<PhysicalPort> physicalPorts);
    }

    /**
     * POJO implementation of LagLogicalPortData.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static final class LagLogicalPortDataImpl implements LagLogicalPortData, Serializable {
        private static final long serialVersionUID = 1L;
        int logicalPortNumber;
        @NonNull SwitchId switchId;

        @Builder.Default
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        @NonNull List<PhysicalPort> physicalPorts = new ArrayList<>();

        // The reference is used to link physical ports back to the LAG port. See {@link #setPhysicalPorts(List)}.
        @Setter(AccessLevel.NONE)
        @Getter(AccessLevel.NONE)
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        LagLogicalPort lagLogicalPort;

        @Override
        public List<PhysicalPort> getPhysicalPorts() {
            return Collections.unmodifiableList(physicalPorts);
        }

        @Override
        public void setPhysicalPorts(List<PhysicalPort> physicalPorts) {
            this.physicalPorts = new ArrayList<>(physicalPorts);
            for (PhysicalPort port : this.physicalPorts) {
                if (port.getData() instanceof PhysicalPortDataImpl) {
                    ((PhysicalPortDataImpl) port.getData()).lagLogicalPort = this.lagLogicalPort;
                } else {
                    throw new IllegalArgumentException(String.format("Unexpected instance of PhysicalPort class %s. "
                            + "Data: %s. Please provide PhysicalPortDataImpl class.",
                            port.getData().getClass().toString(), port.getData()));
                }
            }
        }
    }

    /**
     * A cloner for LagLogicalPort entity.
     */
    @Mapper(collectionMappingStrategy = CollectionMappingStrategy.TARGET_IMMUTABLE)
    public interface LagLogicalPortCloner {
        LagLogicalPortCloner INSTANCE = Mappers.getMapper(LagLogicalPortCloner.class);

        void copy(LagLogicalPortData source, @MappingTarget LagLogicalPortData target);

        @Mapping(target = "physicalPorts", ignore = true)
        void copyWithoutPhysicalPorts(LagLogicalPortData source, @MappingTarget LagLogicalPortData target);

        /**
         * Performs deep copy of entity data.
         *
         * @param source the LAG data to copy from.
         */
        default LagLogicalPortData deepCopy(LagLogicalPortData source, LagLogicalPort lagLogicalPort) {
            LagLogicalPortDataImpl result = new LagLogicalPortDataImpl();
            result.lagLogicalPort = lagLogicalPort;
            copyWithoutPhysicalPorts(source, result);
            result.physicalPorts = (source.getPhysicalPorts().stream()
                    .map(physicalPort -> new PhysicalPort(physicalPort, lagLogicalPort))
                    .collect(Collectors.toList()));
            return result;
        }
    }
}
