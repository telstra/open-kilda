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

package org.openkilda.floodlight.model;

import static org.junit.Assert.assertTrue;

import org.openkilda.floodlight.error.CorruptedNetworkDataException;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.junit.Test;

import java.util.UUID;

public class PingDataSerializationTest {

    @Test
    public void pingDataSerialization() throws CorruptedNetworkDataException {
        PingData data = new PingData(UUID.randomUUID());
        data.setSenderLatency(100L);
        data.setSendTime(3L);

        PingData copied = PingData.of(data.serialize());
        assertTrue(EqualsBuilder.reflectionEquals(data, copied));
    }
}
