/* Copyright 2020 Telstra Open Source
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

package org.openkilda.floodlight.utils.metadata;

import org.openkilda.model.SwitchFeature;
import org.openkilda.model.bitops.BitField;
import org.openkilda.model.bitops.NumericEnumField;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.projectfloodlight.openflow.types.U64;

import java.util.Set;

@Getter
@EqualsAndHashCode(of = {"value", "mask"})
public abstract class MetadataBase {
    // update ALL_FIELDS if modify fields list
    static final BitField TYPE_FIELD = new BitField(0x0000_0000_F000_0000L);

    // used by unit tests to check fields intersections
    static final BitField[] ALL_FIELDS = new BitField[]{TYPE_FIELD};

    protected final U64 value;
    protected final U64 mask;

    protected MetadataBase(MetadataType type, U64 value, U64 mask) {
        this.value = setField(value, type.getValue(), TYPE_FIELD);
        this.mask = setField(mask, -1, TYPE_FIELD);
    }

    protected static U64 setField(U64 target, long value, BitField field) {
        U64 result = target.and(U64.of(~field.getMask()));
        value <<= field.getOffset();
        return result.or(U64.of(value & field.getMask()));
    }

    public enum MetadataType implements NumericEnumField {
        RESERVED(0),
        ROUTING(1),
        APPS(2);

        private final int value;

        MetadataType(int value) {
            this.value = value;
        }

        @Override
        public int getValue() {
            return value;
        }
    }

    protected static boolean isMetadataTruncatedTo32Bits(Set<SwitchFeature> features) {
        return features.contains(SwitchFeature.HALF_SIZE_METADATA);
    }
}
