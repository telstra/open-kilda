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
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

import java.io.Serializable;

@DefaultSerializer(BeanSerializer.class)
public class Speaker implements CompositeDataEntity<Speaker.SpeakerData> {
    @Getter
    @Setter
    @Delegate
    @JsonIgnore
    private SpeakerData data;

    public Speaker() {
        data = new SpeakerDataImpl();
    }

    public Speaker(@NonNull SpeakerData data) {
        this.data = data;
    }

    public Speaker(@NonNull Speaker entityToClone) {
        data = SpeakerCloner.INSTANCE.deepCopy(entityToClone.getData());
    }

    @Builder
    public Speaker(String name) {
        this.data = SpeakerDataImpl.builder()
                .name(name)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Speaker that = (Speaker) o;
        return new EqualsBuilder()
                .append(getName(), that.getName())
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(getName())
                .toHashCode();
    }

    public interface SpeakerData {
        String getName();

        void setName(String name);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static final class SpeakerDataImpl implements SpeakerData, Serializable {
        private static final long serialVersionUID = 1L;

        @NonNull String name;
    }

    @Mapper
    public abstract static class SpeakerCloner {
        public static SpeakerCloner INSTANCE = Mappers.getMapper(SpeakerCloner.class);

        public SpeakerData deepCopy(SpeakerData source) {
            return copy(source, new SpeakerDataImpl());
        }

        public abstract SpeakerData copy(SpeakerData source, @MappingTarget SpeakerData target);
    }
}
