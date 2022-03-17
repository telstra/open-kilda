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

package org.openkilda.messaging.swmanager.response;

import org.openkilda.messaging.StringSerializer;
import org.openkilda.messaging.info.InfoMessage;

import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Test;

public class LagPortResponseTest {
    StringSerializer serializer = new StringSerializer();

    @Test
    public void serializeLoop() throws Exception {
        LagPortResponse origin = makeResponse();
        InfoMessage message = new InfoMessage(origin, System.currentTimeMillis(), getClass().getCanonicalName());

        serializer.serialize(message);
        InfoMessage reconstructedMessage = (InfoMessage) serializer.deserialize();
        LagPortResponse reconstructed = (LagPortResponse) reconstructedMessage.getData();

        Assert.assertEquals(origin, reconstructed);
    }

    public static LagPortResponse makeResponse() {
        return new LagPortResponse(1, Sets.newHashSet(1, 2, 3));
    }
}
