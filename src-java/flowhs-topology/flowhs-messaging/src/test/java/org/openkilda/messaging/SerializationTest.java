/* Copyright 2023 Telstra Open Source
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

package org.openkilda.messaging;

import static org.junit.Assert.assertEquals;

import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.command.haflow.HaFlowRerouteRequest;
import org.openkilda.messaging.command.yflow.YFlowRerouteRequest;
import org.openkilda.model.IslEndpoint;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import org.junit.Test;

import java.io.IOException;
import java.util.Set;

public class SerializationTest {
    private static final String FLOW_ID = "flow_1";
    private static final String REASON = "some reason";
    private static final Set<IslEndpoint> AFFECTED_ISLS = Sets.newHashSet(
            new IslEndpoint(new SwitchId(1), 1),
            new IslEndpoint(new SwitchId(2), 2));

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void flowRerouteRequestSerialization() throws IOException {
        FlowRerouteRequest request = new FlowRerouteRequest(FLOW_ID, true, false, AFFECTED_ISLS, REASON, true);
        assertEquals(request, pass(request, FlowRerouteRequest.class));
    }

    @Test
    public void yFlowRerouteRequestSerialization() throws IOException {
        YFlowRerouteRequest request1 = new YFlowRerouteRequest(FLOW_ID, REASON);
        YFlowRerouteRequest request2 = new YFlowRerouteRequest(FLOW_ID, AFFECTED_ISLS, REASON, true);
        assertEquals(request1, pass(request1, YFlowRerouteRequest.class));
        assertEquals(request2, pass(request2, YFlowRerouteRequest.class));
    }

    @Test
    public void haFlowRerouteRequestSerialization() throws IOException {
        HaFlowRerouteRequest request = new HaFlowRerouteRequest(FLOW_ID, AFFECTED_ISLS, true, REASON, false, true);
        assertEquals(request, pass(request, HaFlowRerouteRequest.class));
    }

    private <T> T pass(T entity, Class<T> clazz) throws IOException {
        return MAPPER.readValue(MAPPER.writeValueAsString(entity), clazz);
    }
}
