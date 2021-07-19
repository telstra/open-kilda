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
import lombok.experimental.Delegate;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

import java.io.Serializable;
import java.time.Instant;

@DefaultSerializer(BeanSerializer.class)
public class SwitchConnect implements CompositeDataEntity<SwitchConnect.SwitchConnectData> {
    @Getter
    @Setter
    @Delegate
    @JsonIgnore
    private SwitchConnectData data;

    public SwitchConnect() {
        this.data = new SwitchConnectDataImpl();
    }

    public SwitchConnect(SwitchConnectData data) {
        this.data = data;
    }

    @Builder
    public SwitchConnect(
            @NonNull Speaker speaker, @NonNull Switch owner, SwitchConnectMode mode, boolean isMaster,
            Instant connectedAt, IpSocketAddress switchAddress, IpSocketAddress speakerAddress) {
        this.data = SwitchConnectDataImpl.builder()
                .speaker(speaker)
                .owner(owner)
                .mode(mode)
                .master(isMaster)
                .connectedAt(connectedAt)
                .switchAddress(switchAddress)
                .speakerAddress(speakerAddress)
                .build();
    }

    public static SwitchConnectBuilder builder(@NonNull Speaker speaker, @NonNull Switch owner) {
        return new SwitchConnectBuilder()
                .speaker(speaker).owner(owner);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SwitchConnect that = (SwitchConnect) o;
        return new EqualsBuilder()
                .append(getSpeaker(), that.getSpeaker())
                .append(getOwner(), that.getOwner())
                .append(getMode(), that.getMode())
                .append(isMaster(), that.isMaster())
                .append(getConnectedAt(), that.getConnectedAt())
                .append(getSwitchAddress(), that.getSwitchAddress())
                .append(getSpeakerAddress(), that.getSpeakerAddress())
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(getSpeaker())
                .append(getOwner())
                .append(getMode())
                .append(isMaster())
                .append(getConnectedAt())
                .append(getSwitchAddress())
                .append(getSpeakerAddress())
                .toHashCode();
    }

    public interface SwitchConnectData {
        SwitchId getOwnerSwitchId();

        Speaker getSpeaker();

        void setSpeaker(Speaker speaker);

        Switch getOwner();

        void setOwner(Switch owner);

        SwitchConnectMode getMode();

        void setMode(SwitchConnectMode mode);

        boolean isMaster();

        void setMaster(boolean isMaster);

        Instant getConnectedAt();

        void setConnectedAt(Instant connectedAt);

        IpSocketAddress getSwitchAddress();

        void setSwitchAddress(IpSocketAddress address);

        IpSocketAddress getSpeakerAddress();

        void setSpeakerAddress(IpSocketAddress address);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static final class SwitchConnectDataImpl implements SwitchConnectData, Serializable {
        private static final long serialVersionUID = 1L;

        @NonNull
        private Speaker speaker;

        @NonNull
        private Switch owner;

        private SwitchConnectMode mode;

        boolean master;

        private Instant connectedAt;

        private IpSocketAddress switchAddress;
        private IpSocketAddress speakerAddress;

        public SwitchId getOwnerSwitchId() {
            return owner.getSwitchId();
        }
    }

    @Mapper
    public abstract static class SwitchConnectCloner {
        public static SwitchConnectCloner INSTANCE = Mappers.getMapper(SwitchConnectCloner.class);

        /**
         * Performs deep copy of entity data.
         */
        public SwitchConnectData deepCopy(SwitchConnectData source) {
            SwitchConnectDataImpl result = new SwitchConnectDataImpl();
            copyWithoutRelations(source, result);
            result.setOwner(new Switch(source.getOwner()));
            result.setSpeaker(new Speaker(source.getSpeaker()));
            return result;
        }

        @Mapping(target = "owner", ignore = true)
        @Mapping(target = "speaker", ignore = true)
        public abstract void copyWithoutRelations(SwitchConnectData source, @MappingTarget SwitchConnectData target);
    }
}
