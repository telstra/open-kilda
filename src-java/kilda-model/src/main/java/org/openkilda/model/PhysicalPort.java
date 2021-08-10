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

import org.openkilda.model.PhysicalPort.PhysicalPortData;

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
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a physical port.
 */
@DefaultSerializer(BeanSerializer.class)
@ToString
public class PhysicalPort implements CompositeDataEntity<PhysicalPortData> {
    @Getter
    @Setter
    @Delegate
    @JsonIgnore
    private PhysicalPortData data;

    /**
     * No args constructor for deserialization purpose.
     */
    private PhysicalPort() {
        data = new PhysicalPortDataImpl();
    }

    /**
     * Cloning constructor which performs deep copy of the entity.
     *
     * @param entityToClone the entity to copy entity data from.
     */
    public PhysicalPort(@NonNull PhysicalPort entityToClone, LagLogicalPort lagLogicalPort) {
        data = PhysicalPortCloner.INSTANCE.deepCopy(entityToClone.getData(), lagLogicalPort);
    }

    @Builder
    public PhysicalPort(@NonNull SwitchId switchId, int portNumber, @NonNull LagLogicalPort lagLogicalPort) {
        data = new PhysicalPortDataImpl(switchId, portNumber, lagLogicalPort);
    }

    public PhysicalPort(@NonNull PhysicalPort.PhysicalPortData data) {
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
        PhysicalPort that = (PhysicalPort) o;
        return new EqualsBuilder()
                .append(getPortNumber(), that.getPortNumber())
                .append(getSwitchId(), that.getSwitchId())
                .isEquals();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getPortNumber(), getSwitchId());
    }

    /**
     * Defines persistable data of the PhysicalPort.
     */
    public interface PhysicalPortData {
        SwitchId getSwitchId();

        void setSwitchId(SwitchId switchId);

        LagLogicalPort getLagLogicalPort();

        int getPortNumber();

        void setPortNumber(int portNumber);
    }

    /**
     * POJO implementation of PhysicalPortData.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static final class PhysicalPortDataImpl implements PhysicalPortData, Serializable {
        private static final long serialVersionUID = 1L;
        @NonNull SwitchId switchId;
        int portNumber;

        @Setter(AccessLevel.NONE)
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        @NonNull LagLogicalPort lagLogicalPort;
    }

    /**
     * A cloner for PhysicalPort entity.
     */
    @Mapper
    public interface PhysicalPortCloner {
        PhysicalPortCloner INSTANCE = Mappers.getMapper(PhysicalPortCloner.class);

        void copy(PhysicalPortData source, @MappingTarget PhysicalPortData target);

        @Mapping(target = "lagLogicalPort", ignore = true)
        void copyWithoutLagLogicalPort(PhysicalPortData source, @MappingTarget PhysicalPortData target);

        /**
         * Performs deep copy of entity data.
         */
        default PhysicalPortData deepCopy(PhysicalPortData source, LagLogicalPort lagLogicalPort) {
            PhysicalPortDataImpl result = new PhysicalPortDataImpl();
            result.lagLogicalPort = lagLogicalPort;
            copyWithoutLagLogicalPort(source, result);
            return result;
        }
    }
}
