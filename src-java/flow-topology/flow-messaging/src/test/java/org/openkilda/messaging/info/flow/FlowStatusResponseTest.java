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

package org.openkilda.messaging.info.flow;

import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowState;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class FlowStatusResponseTest {
    /*
     * JSON: {  "clazz":"org.openkilda.messaging.info.InfoMessage",
     *          "destination":"NORTHBOUND",
     *          "payload":{
     *              "clazz":"org.openkilda.messaging.info.flow.FlowStatusResponse",
     *              "payload":{
     *                  "flowid":"FLOW",
     *                  "status":"UP"},
     *              "timestamp":1520475170274},
     *          "timestamp":10,
     *          "correlation_id":"CORRELATION"}

     */

    private final String json = "{"
            + "\"clazz\":\"org.openkilda.messaging.info.InfoMessage\","
            + "\"destination\":\"NORTHBOUND\","
            + "\"payload\":{"
            +     "\"clazz\":\"org.openkilda.messaging.info.flow.FlowStatusResponse\","
            +     "\"payload\":{"
            +         "\"flowid\":\"FLOW\","
            +         "\"status\":\"UP\"},"
            +     "\"timestamp\":1520474258050},"
            + "\"timestamp\":10,"
            + "\"correlation_id\":\"CORRELATION\"}";

    @Test
    public void testJsonSerialization() throws IOException {
        /*
         * Start with serializing to JSON.
         * Then re-populate from JSON.
         */
        InfoMessage msg = new InfoMessage(new FlowStatusResponse(
                new FlowIdStatusPayload("FLOW", FlowState.UP)), 10L, "CORRELATION", Destination.NORTHBOUND, null);

        InfoMessage fromJson = MAPPER.readValue(json, InfoMessage.class);
        FlowStatusResponse fsrJson = (FlowStatusResponse) fromJson.getData();
        FlowStatusResponse fsrObj = (FlowStatusResponse) msg.getData();

        Assert.assertEquals(fsrJson.getPayload().getId(), fsrObj.getPayload().getId());
        Assert.assertEquals(fsrJson.getPayload().getStatus(), fsrObj.getPayload().getStatus());
        Assert.assertEquals(fsrJson.getPayload().getStatus(), FlowState.UP);
        Assert.assertEquals(fromJson.getCorrelationId(), msg.getCorrelationId());


        System.out.println("JSON: " + MAPPER.writeValueAsString(msg));
    }
}
