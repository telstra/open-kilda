/* Copyright 2022 Telstra Open Source
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

package org.openkilda.messaging.info.flow;

import static org.junit.Assert.assertEquals;

import org.openkilda.messaging.StringSerializer;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.model.Ping.Errors;

import com.google.common.collect.Lists;
import org.junit.Test;

public class YFlowPingResponseTest {
    private static final String Y_FLOW_ID = "y_flow_id1";
    private static final String ERROR_MESSAGE = "ERROR";
    private static final String SUB_FLOW_1 = "flow_1";
    private static final String SUB_FLOW_2 = "flow_2";
    StringSerializer serializer = new StringSerializer();

    @Test
    public void serializeLoop() throws Exception {
        YFlowPingResponse origin = new YFlowPingResponse(Y_FLOW_ID, false, ERROR_MESSAGE, Lists.newArrayList(
                new SubFlowPingPayload(SUB_FLOW_1,
                        new UniSubFlowPingPayload(true, null, 1),
                        new UniSubFlowPingPayload(false, Errors.TIMEOUT, 2)),
                new SubFlowPingPayload(SUB_FLOW_2,
                        new UniSubFlowPingPayload(false, Errors.DEST_NOT_AVAILABLE, 3),
                        new UniSubFlowPingPayload(true, null, 4))));

        InfoMessage wrapper = new InfoMessage(origin, System.currentTimeMillis(), getClass().getSimpleName());

        serializer.serialize(wrapper);
        InfoMessage decodedWrapper = (InfoMessage) serializer.deserialize();
        InfoData decoded = decodedWrapper.getData();

        assertEquals(origin, decoded);
    }
}
