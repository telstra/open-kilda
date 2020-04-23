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

import lombok.Builder;
import org.apache.commons.lang3.ArrayUtils;
import org.projectfloodlight.openflow.types.U64;

import java.util.Set;

public class AppsMetadata extends MetadataBase {
    // update ALL_FIELDS if modify fields list
    //                                                used by parent -> 0x0000_0000_F000_0000L
    private static final BitField ENCAPSULATION_ID_FIELD = new BitField(0x0000_0000_00FF_FFFFL);
    private static final BitField FORWARD_REVERSE_FLAG   = new BitField(0x0000_0000_0100_0000L);

    static final BitField[] ALL_FIELDS = ArrayUtils.addAll(
            MetadataBase.ALL_FIELDS,
            ENCAPSULATION_ID_FIELD, FORWARD_REVERSE_FLAG);

    @Builder
    protected AppsMetadata(Integer encapsulationId, boolean isForward) {
        super(
                MetadataType.APPS,
                makeValue(encapsulationId, isForward),
                makeMask(encapsulationId));
    }

    private static U64 makeValue(Integer encapsulationId, boolean isForward) {
        U64 value = U64.ZERO;
        if (encapsulationId != null) {
            value = setField(value, encapsulationId, ENCAPSULATION_ID_FIELD);
            value = setField(value, isForward ? 1 : 0, FORWARD_REVERSE_FLAG);
        }
        return value;
    }

    private static U64 makeMask(Integer encapsulationId) {
        U64 mask = U64.ZERO;
        if (encapsulationId != null) {
            mask = setField(mask, -1, ENCAPSULATION_ID_FIELD);
            mask = setField(mask, -1, FORWARD_REVERSE_FLAG);
        }
        return mask;
    }

    public static class AppsMetadataBuilder {
        /**
         * Choose correct metadata representation and build it's instance.
         */
        public AppsMetadata build(Set<SwitchFeature> features) {
            if (isMetadataTruncatedTo32Bits(features)) {
                return buildTruncatedTo32Bits();
            } else {
                return buildGeneric();
            }
        }

        private AppsMetadata buildTruncatedTo32Bits() {
            return new AppsMetadata32(encapsulationId, isForward);
        }

        private AppsMetadata buildGeneric() {
            return new AppsMetadata(encapsulationId, isForward);
        }
    }
}
