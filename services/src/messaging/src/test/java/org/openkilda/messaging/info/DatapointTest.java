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

package org.openkilda.messaging.info;

import org.openkilda.messaging.StringSerializer;

import com.google.common.collect.ImmutableMap;
import org.junit.Assert;
import org.junit.Test;

public class DatapointTest implements StringSerializer {
    @Test
    public void serializeLoop() throws Exception {
        // 2 ** 31 == 2147483648
        Datapoint origin = new Datapoint(
                "test.metric",
                System.currentTimeMillis(), ImmutableMap.of(
                    "keyAlpha", "valueAlpha",
                    "keyBeta", "valueBeta"),
                4294967296L);
        InfoMessage wrapper = new InfoMessage(origin, origin.getTimestamp(), "serilization-loop");
        serialize(wrapper);

        Datapoint reconstruct = (Datapoint) ((InfoMessage) deserialize()).getData();

        Assert.assertEquals(origin, reconstruct);
    }
}
