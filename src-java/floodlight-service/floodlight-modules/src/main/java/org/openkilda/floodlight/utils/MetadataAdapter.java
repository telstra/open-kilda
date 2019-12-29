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

package org.openkilda.floodlight.utils;

import lombok.Value;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.U64;

public final class MetadataAdapter {
    public static final MetadataAdapter INSTANCE = new MetadataAdapter();

    // bits layout
    // 0x01000000_00000fff - outer vlan presence flag and value

    private static final U64 OUTER_VLAN_PRESENCE_FLAG = U64.of(0x01000000_00000000L);
    private static final U64 OUTER_VLAN_MASK          = U64.of(0x00000000_00000fffL);

    public MetadataMatch addressOuterVlan(OFVlanVidMatch vlanMatch) {
        return addressOuterVlan(MetadataMatch.ZERO, vlanMatch);
    }

    /**
     * Address outer VLAN ID bits inside metadata.
     */
    public MetadataMatch addressOuterVlan(MetadataMatch base, OFVlanVidMatch vlanMatch) {
        U64 value = OUTER_VLAN_PRESENCE_FLAG.or(U64.of(vlanMatch.getVlan()));
        U64 mask = OUTER_VLAN_PRESENCE_FLAG.or(OUTER_VLAN_MASK);
        return base.merge(value, mask);
    }

    @Value
    public static class MetadataMatch {
        private static final MetadataMatch ZERO = new MetadataMatch(U64.ZERO, U64.ZERO);

        private final U64 value;
        private final U64 mask;

        private MetadataMatch merge(U64 extendValue, U64 extendMask) {
            return new MetadataMatch(
                    value.or(extendValue),
                    mask.or(extendMask));
        }
    }

    private MetadataAdapter() {
        // nothing to do here
    }
}
