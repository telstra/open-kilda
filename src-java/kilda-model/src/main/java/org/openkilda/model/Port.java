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
 * Port entity.
 */
@DefaultSerializer(BeanSerializer.class)
@ToString
public class Port implements CompositeDataEntity<Port.PortData> {
    @Getter
    @Setter
    @Delegate
    @JsonIgnore
    private PortData data;

    /**
     * No args constructor for deserialization purpose.
     */
    private Port() {
        data = new PortDataImpl();
    }

    /**
     * Cloning constructor which performs deep copy of the entity.
     *
     * @param entityToClone the entity to copy properties data from.
     */
    public Port(@NonNull Port entityToClone) {
        data = PortCloner.INSTANCE.deepCopy(entityToClone.getData());
    }

    @Builder
    public Port(@NonNull Switch switchObj, SwitchId switchId, int portNo, long maxSpeed,
                long currentSpeed) {
        this.data = PortDataImpl.builder().switchObj(switchObj).switchId(switchId).portNo(portNo)
                .maxSpeed(maxSpeed).currentSpeed(currentSpeed).build();
    }

    public Port(@NonNull Port.PortData data) {
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
        Port that = (Port) o;
        return new EqualsBuilder()
                .append(getPortNo(), that.getPortNo())
                .append(getCurrentSpeed(), that.getCurrentSpeed())
                .append(getMaxSpeed(), that.getMaxSpeed())
                .append(getSwitchId(), that.getSwitchId())
                .isEquals();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSwitchId(), getPortNo(), getCurrentSpeed(), getMaxSpeed());
    }

    public interface PortData {
        SwitchId getSwitchId();

        Switch getSwitchObj();

        void setSwitchObj(Switch switchObj);

        int getPortNo();

        void setPortNo(int portNumber);

        long getMaxSpeed();

        void setMaxSpeed(long maxSpeed);

        long getCurrentSpeed();

        void setCurrentSpeed(long currentSpeed);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static final class PortDataImpl implements PortData, Serializable {
        private static final long serialVersionUID = 1;
        @NonNull SwitchId switchId;
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        @NonNull Switch switchObj;
        int portNo;
        long maxSpeed;
        long currentSpeed;
    }

    /**
     * A cloner for Port entity.
     */
    @Mapper
    public interface PortCloner {
        PortCloner INSTANCE = Mappers.getMapper(PortCloner.class);

        default void copy(PortData source, PortData target) {
            copyWithoutSwitch(source, target);
            target.setSwitchObj(new Switch(source.getSwitchObj()));
        }

        @Mapping(target = "switchObj", ignore = true)
        void copyWithoutSwitch(PortData source, @MappingTarget PortData target);

        /**
         * Performs deep copy of entity data.
         */
        default PortData deepCopy(PortData source) {
            PortData result = new PortDataImpl();
            copyWithoutSwitch(source, result);
            result.setSwitchObj(new Switch(source.getSwitchObj()));
            return result;
        }
    }
}
