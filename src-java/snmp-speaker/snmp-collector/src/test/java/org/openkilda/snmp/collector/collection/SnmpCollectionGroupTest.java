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

package org.openkilda.snmp.collector.collection;

import static org.junit.Assert.assertEquals;

import org.openkilda.snmp.collector.collection.data.SnmpCollectionGroup;
import org.openkilda.snmp.collector.collection.data.SnmpMetricEntry;
import org.openkilda.snmp.collector.collection.data.SnmpMetricGroup;
import org.openkilda.snmp.collector.collection.data.SnmpSystemDefinition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.Test;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

public class SnmpCollectionGroupTest {

    private static String SAMPLE_SNMP_COLLECTION_GROUP_FILE = "modules/sample_snmp_collection_group.yaml";
    private ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    @Test
    public void testSnmpCollectionGroup() throws Exception {
        InputStream in = getClass().getClassLoader().getResourceAsStream(SAMPLE_SNMP_COLLECTION_GROUP_FILE);

        SnmpCollectionGroup sampleCollectionGroup = mapper.readValue(in, SnmpCollectionGroup.class);

        assertEquals("noviflow", sampleCollectionGroup.getName());

        List<SnmpSystemDefinition> systems = sampleCollectionGroup.getSystems();
        SnmpSystemDefinition sampleSystem = systems.get(0);
        assertEquals("systemdef1", sampleSystem.getName());
        assertEquals("1.3.6.1.4.2", sampleSystem.getSysObjectIdMask());
        assertEquals(Arrays.asList("ifTable"), sampleSystem.getCollections());

        List<SnmpMetricGroup> groups = sampleCollectionGroup.getGroups();
        SnmpMetricGroup sampleGroup = groups.get(0);
        assertEquals("ifTable", sampleGroup.getName());
        assertEquals("ifIndex", sampleGroup.getInstance());

        SnmpMetricEntry metricEntry = sampleGroup.getMetrics().get(0);
        assertEquals("packetsent", metricEntry.getMetric());
        assertEquals("1.3.6.1.4.2", metricEntry.getOid());

    }
}
