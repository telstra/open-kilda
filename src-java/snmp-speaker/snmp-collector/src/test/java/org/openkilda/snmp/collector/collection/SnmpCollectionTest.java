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
import static org.junit.Assert.assertTrue;

import org.openkilda.snmp.collector.collection.data.SnmpCollection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.Test;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;


public class SnmpCollectionTest {

    List<String> fixtureGroup = Arrays.asList("mib2", "noviflow");
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    @Test
    public void testSnmpCollection() throws Exception {

        InputStream in = getClass().getClassLoader().getResourceAsStream("sample_snmp_collection.yaml");
        SnmpCollection[] values = mapper.readValue(in, SnmpCollection[].class);

        SnmpCollection[] activeCollections = Arrays.stream(values)
                .filter(SnmpCollection::isActive).toArray(SnmpCollection[]::new);

        assertEquals(1, activeCollections.length);

        SnmpCollection target = activeCollections[0];
        assertEquals("kilda", target.getName());
        assertTrue(target.isActive());
        assertEquals(fixtureGroup, target.getCollectionGroups());

    }
}
