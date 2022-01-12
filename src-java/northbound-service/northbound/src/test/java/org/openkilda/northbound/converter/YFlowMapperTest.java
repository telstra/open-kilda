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

package org.openkilda.northbound.converter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.openkilda.messaging.info.flow.SubFlowPingPayload;
import org.openkilda.messaging.info.flow.UniSubFlowPingPayload;
import org.openkilda.messaging.info.flow.YFlowPingResponse;
import org.openkilda.messaging.model.Ping.Errors;
import org.openkilda.northbound.dto.v2.yflows.YFlowPingResult;

import com.google.common.collect.Lists;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
public class YFlowMapperTest {
    public static final String ERROR_MESSAGE = "Error";
    public static final String Y_FLOW_ID = "y_flow_id";
    public static final String SUB_FLOW_1 = "flow_1";
    public static final String SUB_FLOW_2 = "flow_2";

    @Autowired
    private YFlowMapper mapper;

    @Test
    public void pingResultTest() {
        YFlowPingResponse response = new YFlowPingResponse(Y_FLOW_ID, false, ERROR_MESSAGE, Lists.newArrayList(
                new SubFlowPingPayload(SUB_FLOW_1,
                        new UniSubFlowPingPayload(true, null, 1),
                        new UniSubFlowPingPayload(false, Errors.TIMEOUT, 2)),
                new SubFlowPingPayload(SUB_FLOW_2,
                        new UniSubFlowPingPayload(false, Errors.DEST_NOT_AVAILABLE, 3),
                        new UniSubFlowPingPayload(true, null, 4))));

        YFlowPingResult result = mapper.toPingResult(response);
        assertEquals(response.getYFlowId(), result.getYFlowId());
        assertEquals(response.isPingSuccess(), result.isPingSuccess());
        assertEquals(response.getError(), result.getError());
        assertEquals(response.getSubFlows().size(), result.getSubFlows().size());
        assertSubFlowPingPayload(response.getSubFlows().get(0), result.getSubFlows().get(0));
    }

    private void assertSubFlowPingPayload(
            SubFlowPingPayload expected, org.openkilda.northbound.dto.v2.yflows.SubFlowPingPayload actual) {
        assertEquals(expected.getFlowId(), actual.getFlowId());
        assertUniSubFlowPingPayload(expected.getForward(), actual.getForward());
        assertUniSubFlowPingPayload(expected.getReverse(), actual.getReverse());
    }

    private void assertUniSubFlowPingPayload(
            UniSubFlowPingPayload expected, org.openkilda.northbound.dto.v2.yflows.UniSubFlowPingPayload actual) {
        assertEquals(expected.isPingSuccess(), actual.isPingSuccess());
        assertEquals(expected.getLatency(), actual.getLatency());
        assertError(expected, actual);
    }

    private void assertError(
            UniSubFlowPingPayload expected, org.openkilda.northbound.dto.v2.yflows.UniSubFlowPingPayload actual) {
        if (expected.getError() == null) {
            assertNull(actual.getError());
        } else if (Errors.TIMEOUT.equals(expected.getError())) {
            assertEquals(PingMapper.TIMEOUT_ERROR_MESSAGE, actual.getError());
        } else if (Errors.DEST_NOT_AVAILABLE.equals(expected.getError())) {
            assertEquals(PingMapper.ENDPOINT_NOT_AVAILABLE_ERROR_MESSAGE, actual.getError());
        } else {
            throw new AssertionError(String.format("Unknown error type: %s", expected.getError()));
        }
    }

    @TestConfiguration
    @ComponentScan({"org.openkilda.northbound.converter"})
    static class Config {
        // nothing to define here
    }
}
