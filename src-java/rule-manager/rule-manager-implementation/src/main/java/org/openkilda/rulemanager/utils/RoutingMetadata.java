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

package org.openkilda.rulemanager.utils;

import static java.lang.String.format;

import org.openkilda.model.SwitchFeature;
import org.openkilda.model.bitops.BitField;
import org.openkilda.model.bitops.NumericEnumField;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Set;

@Getter
@EqualsAndHashCode(of = {"value", "mask"})
public class RoutingMetadata {
    // update ALL_FIELDS if modify fields list
    private static final BitField TYPE_FIELD               = new BitField(0x0000_0000_F000_0000L);
    private static final BitField LLDP_MARKER_FLAG         = new BitField(0x0000_0000_0000_0001L);
    private static final BitField ONE_SWITCH_FLOW_FLAG     = new BitField(0x0000_0000_0000_0002L);
    private static final BitField ARP_MARKER_FLAG          = new BitField(0x0000_0000_0000_0004L);
    private static final BitField OUTER_VLAN_PRESENCE_FLAG = new BitField(0x0000_0000_0000_0008L);
    private static final BitField OUTER_VLAN_FIELD         = new BitField(0x0000_0000_0000_FFF0L);
    // NOTE: port count was increased from 128 to 4096. At this moment only 1000 ports can be used
    // on Noviflow switches. But according to open flow specs port count could be up to 65536.
    // So we increased port count to maximum possible value.
    private static final BitField INPUT_PORT_FIELD         = new BitField(0x0000_0000_0FFF_0000L);

    public static final int FULL_MASK = -1;
    static final long MAX_INPUT_PORT = INPUT_PORT_FIELD.getMask() >> INPUT_PORT_FIELD.getOffset();

    static final BitField[] ALL_FIELDS = new BitField[] {
            TYPE_FIELD, LLDP_MARKER_FLAG, ONE_SWITCH_FLOW_FLAG, ARP_MARKER_FLAG, OUTER_VLAN_PRESENCE_FLAG,
            OUTER_VLAN_FIELD, INPUT_PORT_FIELD};

    private final long value;
    private final long mask;
    
    @Builder
    protected RoutingMetadata(
            Boolean lldpFlag, Boolean arpFlag, Boolean oneSwitchFlowFlag, Integer outerVlanId, Integer inputPort) {
        this.value = setField(makeValue(lldpFlag, arpFlag, oneSwitchFlowFlag, outerVlanId, inputPort),
                MetadataType.ROUTING.getValue(), TYPE_FIELD);
        this.mask = setField(makeMask(lldpFlag, arpFlag, oneSwitchFlowFlag, outerVlanId, inputPort),
                FULL_MASK, TYPE_FIELD);
    }


    protected static long setField(long target, long value, BitField field) {
        long result = target & (~field.getMask());
        value <<= field.getOffset();
        return result | (value & field.getMask());
    }

    public enum MetadataType implements NumericEnumField {
        RESERVED(0),
        ROUTING(1);

        private final int value;

        MetadataType(int value) {
            this.value = value;
        }

        @Override
        public int getValue() {
            return value;
        }
    }

    private static long makeValue(
            Boolean lldpFlag, Boolean arpFlag, Boolean oneSwitchFlowFlag, Integer outerVlanId, Integer inputPort) {
        long result = 0;
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

    private static long makeMask(
            Boolean lldpFlag, Boolean arpFlag, Boolean oneSwitchFlowFlag, Integer outerVlanId, Integer inputPort) {
        long result = 0;
        if (lldpFlag != null) {
            result = setField(result, FULL_MASK, LLDP_MARKER_FLAG);
        }
        if (arpFlag != null) {
            result = setField(result, FULL_MASK, ARP_MARKER_FLAG);
        }
        if (oneSwitchFlowFlag != null) {
            result = setField(result, FULL_MASK, ONE_SWITCH_FLOW_FLAG);
        }
        if (outerVlanId != null) {
            result = setField(result, FULL_MASK, OUTER_VLAN_PRESENCE_FLAG);
            result = setField(result, FULL_MASK, OUTER_VLAN_FIELD);
        }
        if (inputPort != null) {
            result = setField(result, FULL_MASK, INPUT_PORT_FIELD);
        }
        return result;
    }

    public static class RoutingMetadataBuilder {
        /**
         * Choose correct metadata representation and build instance.
         */
        public RoutingMetadata build(Set<SwitchFeature> features) {
            if (isMetadataTruncatedTo32Bits(features)) {
                return buildTruncatedTo32Bits();
            } else {
                return buildGeneric();
            }
        }

        private RoutingMetadata buildTruncatedTo32Bits() {
            // todo fix 32-bits metadata
            return new RoutingMetadata(lldpFlag, arpFlag, oneSwitchFlowFlag, outerVlanId, inputPort);
        }

        private RoutingMetadata buildGeneric() {
            return new RoutingMetadata(lldpFlag, arpFlag, oneSwitchFlowFlag, outerVlanId, inputPort);
        }
    }

    protected static boolean isMetadataTruncatedTo32Bits(Set<SwitchFeature> features) {
        return features.contains(SwitchFeature.HALF_SIZE_METADATA);
    }
}
