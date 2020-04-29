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

import static java.lang.String.format;

import org.openkilda.model.SwitchFeature;
import org.openkilda.model.bitops.BitField;

import lombok.Builder;
import org.apache.commons.lang3.ArrayUtils;
import org.projectfloodlight.openflow.types.U64;

import java.util.Set;

public class RoutingMetadata extends MetadataBase {
    // update ALL_FIELDS if modify fields list
    //                                                  used by parent -> 0x0000_0000_F000_0000L
    private static final BitField LLDP_MARKER_FLAG         = new BitField(0x0000_0000_0000_0001L);
    private static final BitField ONE_SWITCH_FLOW_FLAG     = new BitField(0x0000_0000_0000_0002L);
    private static final BitField ARP_MARKER_FLAG          = new BitField(0x0000_0000_0000_0004L);
    private static final BitField OUTER_VLAN_PRESENCE_FLAG = new BitField(0x0000_0000_0000_0008L);
    private static final BitField OUTER_VLAN_FIELD         = new BitField(0x0000_0000_0000_fff0L);
    private static final BitField INPUT_PORT_FIELD         = new BitField(0x0000_0000_007F_0000L);

    static final long MAX_INPUT_PORT = INPUT_PORT_FIELD.getMask() >> INPUT_PORT_FIELD.getOffset();

    static final BitField[] ALL_FIELDS = ArrayUtils.addAll(
            MetadataBase.ALL_FIELDS,
            LLDP_MARKER_FLAG, ONE_SWITCH_FLOW_FLAG, ARP_MARKER_FLAG, OUTER_VLAN_PRESENCE_FLAG, OUTER_VLAN_FIELD,
            INPUT_PORT_FIELD);

    @Builder
    protected RoutingMetadata(
            Boolean lldpFlag, Boolean arpFlag, Boolean oneSwitchFlowFlag, Integer outerVlanId, Integer inputPort) {
        super(
                MetadataType.ROUTING,
                makeValue(lldpFlag, arpFlag, oneSwitchFlowFlag, outerVlanId, inputPort),
                makeMask(lldpFlag, arpFlag, oneSwitchFlowFlag, outerVlanId, inputPort));
    }

    private static U64 makeValue(
            Boolean lldpFlag, Boolean arpFlag, Boolean oneSwitchFlowFlag, Integer outerVlanId, Integer inputPort) {
        U64 result = U64.ZERO;
        if (lldpFlag != null) {
            result = setField(result, lldpFlag ? 1 : 0, LLDP_MARKER_FLAG);
        }
        if (arpFlag != null) {
            result = setField(result, arpFlag ? 1 : 0, ARP_MARKER_FLAG);
        }
        if (oneSwitchFlowFlag != null) {
            result = setField(result, oneSwitchFlowFlag ? 1 : 0, ONE_SWITCH_FLOW_FLAG);
        }
        if (outerVlanId != null) {
            result = setField(result, 1, OUTER_VLAN_PRESENCE_FLAG);
            result = setField(result, outerVlanId, OUTER_VLAN_FIELD);
        }
        if (inputPort != null) {
            if (inputPort < 0 || inputPort > MAX_INPUT_PORT) {
                throw new IllegalArgumentException(
                        format("Invalid inputPort %s. Valid range [0, %d]", inputPort, MAX_INPUT_PORT));
            }
            result = setField(result, inputPort, INPUT_PORT_FIELD);
        }
        return result;
    }

    private static U64 makeMask(
            Boolean lldpFlag, Boolean arpFlag, Boolean oneSwitchFlowFlag, Integer outerVlanId, Integer inputPort) {
        U64 result = U64.ZERO;
        if (lldpFlag != null) {
            result = setField(result, -1, LLDP_MARKER_FLAG);
        }
        if (arpFlag != null) {
            result = setField(result, -1, ARP_MARKER_FLAG);
        }
        if (oneSwitchFlowFlag != null) {
            result = setField(result, -1, ONE_SWITCH_FLOW_FLAG);
        }
        if (outerVlanId != null) {
            result = setField(result, -1, OUTER_VLAN_PRESENCE_FLAG);
            result = setField(result, -1, OUTER_VLAN_FIELD);
        }
        if (inputPort != null) {
            result = setField(result, -1, INPUT_PORT_FIELD);
        }
        return result;
    }

    public static class RoutingMetadataBuilder {
        /**
         * Choose correct metadata representation and build it's instance.
         */
        public RoutingMetadata build(Set<SwitchFeature> features) {
            if (isMetadataTruncatedTo32Bits(features)) {
                return buildTruncatedTo32Bits();
            } else {
                return buildGeneric();
            }
        }

        private RoutingMetadata buildTruncatedTo32Bits() {
            return new RoutingMetadata32(lldpFlag, arpFlag, oneSwitchFlowFlag, outerVlanId, inputPort);
        }

        private RoutingMetadata buildGeneric() {
            return new RoutingMetadata(lldpFlag, arpFlag, oneSwitchFlowFlag, outerVlanId, inputPort);
        }
    }
}
