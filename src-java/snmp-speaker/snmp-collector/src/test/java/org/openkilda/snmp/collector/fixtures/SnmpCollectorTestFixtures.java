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

package org.openkilda.snmp.collector.fixtures;

import org.openkilda.snmp.collector.collection.data.SnmpHost;
import org.openkilda.snmp.collector.collection.data.SnmpMetricEntry;
import org.openkilda.snmp.collector.collection.data.SnmpMetricGroup;
import org.openkilda.snmp.collector.collection.data.SnmpSystemDefinition;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * This class is closely tied to the test files provided under src/main/test/resources. Changes in the test config file
 * need to populate to this class too.
 */
public final class SnmpCollectorTestFixtures {

    public static final String testCollectionFile = "sample_snmp_collection.yaml";
    public static final String testCollectionGroupFile = "modules/sample_snmp_collection_group.yaml";
    public static final String testModuleSubDir = "modules";
    public static final String testSysObjectIdMask = "1.3.6.1.4.2";

    private SnmpCollectorTestFixtures() {
    }

    /**
     * Helper method for testing.
     *
     * @return a predefined {@link SnmpSystemDefinition}
     */
    public static SnmpSystemDefinition getSystemDefinitionFixture() {
        SnmpSystemDefinition systemDefinition = new SnmpSystemDefinition(
                "systemdef1",
                "1.3.6.1.4.2",
                Arrays.asList("ifTable")
        );

        return systemDefinition;
    }

    /**
     * Helper method for testing.
     *
     * @return a predefined {@link SnmpMetricGroup}
     */
    public static SnmpMetricGroup getMetricGroupFixture() {
        SnmpMetricGroup metricGroup = new SnmpMetricGroup(
                "ifTable",
                "ifIndex",
                Arrays.asList(new SnmpMetricEntry("packetsent", "1.3.6.1.4.2"))
        );

        return metricGroup;
    }

    /**
     * Helper method for testing.
     *
     * @return a list of predefined {@link SnmpHost}
     */
    public static List<SnmpHost> getSnmpHostsFixture() {
        Map<String, String> tags = Collections.singletonMap("switchId", "SW123456789");
        SnmpHost host = new SnmpHost("ofsw3.pen.amls", tags);

        return Arrays.asList(host);
    }
}
