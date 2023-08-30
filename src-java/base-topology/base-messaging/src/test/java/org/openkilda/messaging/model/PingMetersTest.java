/* Copyright 2018 Telstra Open Source
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

package org.openkilda.messaging.model;

import org.openkilda.messaging.ObjectSerializer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class PingMetersTest {
    ObjectSerializer serializer;

    public PingMetersTest() throws IOException {
        serializer = new ObjectSerializer();
    }

    @Test
    public void serializeLoop() throws Exception {
        PingMeters origin = new PingMeters(1L, 2L, 3L);

        serializer.serialize(origin);
        PingMeters decoded = (PingMeters) serializer.deserialize();

        Assertions.assertEquals(origin, decoded, String.format("%s object have been mangled"
                + " in serialisation/deserialization loop", origin.getClass().getName()));
    }
}
