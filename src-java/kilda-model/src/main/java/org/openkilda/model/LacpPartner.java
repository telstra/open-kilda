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
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a physical port.
 */
@DefaultSerializer(BeanSerializer.class)
@ToString
public class LacpPartner implements CompositeDataEntity<LacpPartner.LacpPartnerData> {
    @Getter
    @Setter
    @Delegate
    @JsonIgnore
    private LacpPartnerData data;

    /**
     * No args constructor for deserialization purpose.
     */
    private LacpPartner() {
        data = new LacpPartnerDataImpl();
    }

    @Builder
    public LacpPartner(@NonNull SwitchId switchId, int logicalPortNumber, int systemPriority,
                       MacAddress systemId, int key, int portPriority, int portNumber, boolean stateActive,
                       boolean stateShortTimeout, boolean stateAggregatable, boolean stateSynchronised,
                       boolean stateCollecting, boolean stateDistributing, boolean stateDefaulted,
                       boolean stateExpired) {
        data = LacpPartnerDataImpl.builder()
                .switchId(switchId)
                .logicalPortNumber(logicalPortNumber)
                .systemPriority(systemPriority)
                .systemId(systemId)
                .key(key)
                .portPriority(portPriority)
                .portNumber(portNumber)
                .stateActive(stateActive)
                .stateShortTimeout(stateShortTimeout)
                .stateAggregatable(stateAggregatable)
                .stateSynchronised(stateSynchronised)
                .stateCollecting(stateCollecting)
                .stateDistributing(stateDistributing)
                .stateDefaulted(stateDefaulted)
                .stateExpired(stateExpired)
                .build();
    }

    public LacpPartner(@NonNull LacpPartner.LacpPartnerData data) {
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
        LacpPartner that = (LacpPartner) o;
        return new EqualsBuilder()
                .append(getSwitchId(), that.getSwitchId())
                .append(getLogicalPortNumber(), that.getLogicalPortNumber())
                .append(getSystemPriority(), that.getSystemPriority())
                .append(getSystemId(), that.getSystemId())
                .append(getKey(), that.getKey())
                .append(getPortPriority(), that.getPortPriority())
                .append(getPortNumber(), that.getPortNumber())
                .append(isStateActive(), that.isStateActive())
                .append(isStateShortTimeout(), that.isStateShortTimeout())
                .append(isStateAggregatable(), that.isStateAggregatable())
                .append(isStateSynchronised(), that.isStateSynchronised())
                .append(isStateCollecting(), that.isStateCollecting())
                .append(isStateDistributing(), that.isStateDistributing())
                .append(isStateDefaulted(), that.isStateDefaulted())
                .append(isStateExpired(), that.isStateExpired())
                .isEquals();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSwitchId(), getLogicalPortNumber(), getSystemPriority(), getSystemId(),
                getKey(), getPortPriority(), getPortNumber(), isStateActive(), isStateShortTimeout(),
                isStateAggregatable(), isStateSynchronised(), isStateCollecting(), isStateDistributing(),
                isStateDefaulted(), isStateExpired());
    }

    /**
     * Defines persistable data of the PhysicalPort.
     */
    public interface LacpPartnerData {
        SwitchId getSwitchId();

        void setSwitchId(SwitchId switchId);

        public int getLogicalPortNumber();

        public void setLogicalPortNumber(int logicalPortNumber);

        int getSystemPriority();

        void setSystemPriority(int systemPriority);

        MacAddress getSystemId();

        void setSystemId(MacAddress systemId);

        int getKey();

        void setKey(int key);

        int getPortPriority();

        void setPortPriority(int portPriority);

        int getPortNumber();

        void setPortNumber(int portNumber);

        boolean isStateActive();

        void setStateActive(boolean stateActive);

        boolean isStateShortTimeout();

        void setStateShortTimeout(boolean stateShortTimeout);

        boolean isStateAggregatable();

        void setStateAggregatable(boolean stateAggregatable);

        boolean isStateSynchronised();

        void setStateSynchronised(boolean stateSynchronised);

        boolean isStateCollecting();

        void setStateCollecting(boolean stateCollecting);

        boolean isStateDistributing();

        void setStateDistributing(boolean stateDistributing);

        boolean isStateDefaulted();

        void setStateDefaulted(boolean stateDefaulted);

        boolean isStateExpired();

        void setStateExpired(boolean stateExpired);
    }

    /**
     * A cloner for LacpPartner entity.
     */
    @Mapper
    public interface LacpPartnerCloner {
        LacpPartner.LacpPartnerCloner INSTANCE = Mappers.getMapper(LacpPartner.LacpPartnerCloner.class);

        /**
         * Performs copy of entity data.
         */
        default void copy(LacpPartnerData source, @MappingTarget LacpPartnerData target) {
            if (source == null) {
                return;
            }
            target.setSwitchId(source.getSwitchId());
            target.setLogicalPortNumber(source.getLogicalPortNumber());
            target.setSystemPriority(source.getSystemPriority());
            target.setSystemId(source.getSystemId());
            target.setKey(source.getKey());
            target.setPortPriority(source.getPortPriority());
            target.setPortNumber(source.getPortNumber());
            target.setStateActive(source.isStateActive());
            target.setStateShortTimeout(source.isStateShortTimeout());
            target.setStateAggregatable(source.isStateAggregatable());
            target.setStateSynchronised(source.isStateSynchronised());
            target.setStateCollecting(source.isStateCollecting());
            target.setStateDistributing(source.isStateDistributing());
            target.setStateDefaulted(source.isStateDefaulted());
            target.setStateExpired(source.isStateExpired());
        }

        /**
         * Performs deep copy of entity data.
         */
        default LacpPartnerData deepCopy(LacpPartnerData source) {
            LacpPartnerData result = new LacpPartnerDataImpl();
            copy(source, result);
            return result;
        }
    }

    /**
     * POJO implementation of PhysicalPortData.
     */

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static final class LacpPartnerDataImpl implements LacpPartnerData, Serializable {
        private static final long serialVersionUID = 1L;
        @NonNull SwitchId switchId;

        int logicalPortNumber;

        int systemPriority;
        MacAddress systemId;
        int key;
        int portPriority;
        int portNumber;
        boolean stateActive;
        boolean stateShortTimeout;
        boolean stateAggregatable;
        boolean stateSynchronised;
        boolean stateCollecting;
        boolean stateDistributing;
        boolean stateDefaulted;
        boolean stateExpired;
    }
}
