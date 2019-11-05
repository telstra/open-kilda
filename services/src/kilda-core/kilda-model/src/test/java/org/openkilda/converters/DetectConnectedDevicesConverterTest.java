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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.openkilda.converters.DetectConnectedDevicesConverter.DST_ARP;
import static org.openkilda.converters.DetectConnectedDevicesConverter.DST_LLDP;
import static org.openkilda.converters.DetectConnectedDevicesConverter.SRC_ARP;
import static org.openkilda.converters.DetectConnectedDevicesConverter.SRC_LLDP;

import org.openkilda.model.DetectConnectedDevices;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class DetectConnectedDevicesConverterTest {
    private static DetectConnectedDevicesConverter converter;

    @BeforeClass
    public static void setupOnce() {
        converter = new DetectConnectedDevicesConverter();
    }

    @Test
    public void toGraphPropertyTest() {
        runToGraphPropertyTest(true, true, true, true);
        runToGraphPropertyTest(false, false, false, false);
        runToGraphPropertyTest(false, true, true, false);
        runToGraphPropertyTest(false, false, true, true);
    }

    private void runToGraphPropertyTest(Boolean srcLldp, Boolean srcArp, Boolean dstLldp, Boolean dstArp) {
        DetectConnectedDevices value = new DetectConnectedDevices(srcLldp, srcArp, dstLldp, dstArp);
        Map<String, ?> properties = converter.toGraphProperties(value);
        assertEquals(srcLldp, properties.get(SRC_LLDP));
        assertEquals(srcArp, properties.get(SRC_ARP));
        assertEquals(dstLldp, properties.get(DST_LLDP));
        assertEquals(dstArp, properties.get(DST_ARP));
    }

    @Test
    public void toEntityAttributeTest() {
        runToEntityAttributeTest(true, true, true, true);
        runToEntityAttributeTest(false, false, false, false);
        runToEntityAttributeTest(true, false, false, true);
        runToEntityAttributeTest(false, false, true, true);
    }

    private void runToEntityAttributeTest(Boolean srcLldp, Boolean srcArp, Boolean dstLldp, Boolean dstArp) {
        Map<String, Boolean> properties = createProperties(srcLldp, srcArp, dstLldp, dstArp);
        DetectConnectedDevices detectConnectedDevices = converter.toEntityAttribute(properties);
        assertEquals(srcLldp, detectConnectedDevices.isSrcLldp());
        assertEquals(srcArp, detectConnectedDevices.isSrcArp());
        assertEquals(dstLldp, detectConnectedDevices.isDstLldp());
        assertEquals(dstArp, detectConnectedDevices.isDstArp());
    }

    @Test
    public void toEntityAttributeEmptyPropertiesTest() {
        DetectConnectedDevices detectConnectedDevices = converter.toEntityAttribute(new HashMap<>());
        assertFalse(detectConnectedDevices.isSrcLldp());
        assertFalse(detectConnectedDevices.isSrcArp());
        assertFalse(detectConnectedDevices.isDstLldp());
        assertFalse(detectConnectedDevices.isDstArp());
    }

    private Map<String, Boolean> createProperties(Boolean srcLldp, Boolean srcArp, Boolean dstLldp, Boolean dstArp) {
        Map<String, Boolean> properties = new HashMap<>();
        properties.put(SRC_LLDP, srcLldp);
        properties.put(SRC_ARP, srcArp);
        properties.put(DST_LLDP, dstLldp);
        properties.put(DST_ARP, dstArp);
        return properties;
    }
}
