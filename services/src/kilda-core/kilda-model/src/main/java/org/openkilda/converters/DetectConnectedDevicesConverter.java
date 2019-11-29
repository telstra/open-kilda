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

package org.openkilda.converters;

import org.openkilda.model.DetectConnectedDevices;

import org.neo4j.ogm.typeconversion.CompositeAttributeConverter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class DetectConnectedDevicesConverter implements CompositeAttributeConverter<DetectConnectedDevices> {
    public static final String SRC_LLDP = "detect_src_lldp_connected_devices";
    public static final String SRC_ARP = "detect_src_arp_connected_devices";
    public static final String DST_LLDP = "detect_dst_lldp_connected_devices";
    public static final String DST_ARP = "detect_dst_arp_connected_devices";
    public static final String SRC_SWITCH_LLDP = "src_lldp_switch_connected_devices";
    public static final String DST_SWITCH_LLDP = "dst_lldp_switch_connected_devices";

    @Override
    public Map<String, ?> toGraphProperties(DetectConnectedDevices value) {
        Map<String, Boolean> properties = new HashMap<>();
        if (value != null) {
            properties.put(SRC_LLDP, value.isSrcLldp());
            properties.put(SRC_ARP, value.isSrcArp());
            properties.put(DST_LLDP, value.isDstLldp());
            properties.put(DST_ARP, value.isDstArp());
            properties.put(SRC_SWITCH_LLDP, value.isSrcSwitchLldp());
            properties.put(DST_SWITCH_LLDP, value.isDstSwitchLldp());
        }
        return properties;
    }

    @Override
    public DetectConnectedDevices toEntityAttribute(Map<String, ?> properties) {
        boolean srcLldp = getPropertyValue(SRC_LLDP, properties);
        boolean srcArp = getPropertyValue(SRC_ARP, properties);
        boolean dstLldp = getPropertyValue(DST_LLDP, properties);
        boolean dstArp = getPropertyValue(DST_ARP, properties);
        boolean srcSwitchLldp = getPropertyValue(SRC_SWITCH_LLDP, properties);
        boolean dstSwitchLldp = getPropertyValue(DST_SWITCH_LLDP, properties);
        return new DetectConnectedDevices(srcLldp, srcArp, dstLldp, dstArp, srcSwitchLldp, dstSwitchLldp);
    }

    private boolean getPropertyValue(String property, Map<String, ?> properties) {
        return Optional.ofNullable(properties.get(property)).map(Boolean.class::cast).orElse(false);
    }
}
