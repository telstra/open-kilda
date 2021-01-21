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

import static org.junit.Assert.assertEquals;
import static org.openkilda.floodlight.utils.metadata.MetadataBase.TYPE_FIELD;

import org.junit.Test;

import java.util.HashSet;

public class RoutingMetadataTest extends MetadataBaseTest {
    @Test
    public void testFieldsIntersection() {
        testFieldsIntersection(RoutingMetadata.ALL_FIELDS);
    }

    @Test
    public void testInputPortMetadata() {
        int offset = 16;
        for (int port = 0; port <= 4095; port++) {
            RoutingMetadata metadata = RoutingMetadata.builder().inputPort(port).build(new HashSet<>());
            long withoutType = ~TYPE_FIELD.getMask() & metadata.getValue().getValue();
            assertEquals(port, withoutType >> offset);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativePortMetadata() {
        RoutingMetadata.builder().inputPort(-1).build(new HashSet<>());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBigPortMetadata() {
        RoutingMetadata.builder().inputPort((int) (RoutingMetadata.MAX_INPUT_PORT + 1)).build(new HashSet<>());
    }
}
