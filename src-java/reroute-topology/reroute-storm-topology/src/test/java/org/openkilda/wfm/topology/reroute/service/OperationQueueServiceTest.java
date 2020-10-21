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

package org.openkilda.wfm.topology.reroute.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import org.openkilda.messaging.command.flow.FlowPathSwapRequest;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.wfm.topology.reroute.bolts.OperationQueueCarrier;
import org.openkilda.wfm.topology.reroute.service.OperationQueueService.FlowQueueData;

import org.junit.Test;

import java.util.Map;

public class OperationQueueServiceTest {
    private static final String TEST_FLOW_ID = "test_flow_id";
    private static final String TEST_CORRELATION_ID_A = "test_correlation_id_a";
    private static final String TEST_CORRELATION_ID_B = "test_correlation_id_b";
    private static final String TEST_CORRELATION_ID_C = "test_correlation_id_c";

    private final OperationQueueCarrier carrier = mock(OperationQueueCarrier.class);
    private final OperationQueueService service = new OperationQueueService(carrier);

    @Test
    public void shouldAddFirst() {
        service.addLast(TEST_FLOW_ID, TEST_CORRELATION_ID_A, new FlowRerouteRequest(TEST_FLOW_ID, false, false, ""));
        service.addLast(TEST_FLOW_ID, TEST_CORRELATION_ID_C, new FlowRerouteRequest(TEST_FLOW_ID, false, false, ""));

        FlowQueueData flowQueueData = service.getFlowCommands().get(TEST_FLOW_ID);

        assertEquals(TEST_CORRELATION_ID_A, flowQueueData.getTaskInProgress());
        assertEquals(1, flowQueueData.getQueue().size());

        service.addFirst(TEST_FLOW_ID, TEST_CORRELATION_ID_B, new FlowPathSwapRequest(TEST_FLOW_ID));

        service.operationCompleted(TEST_FLOW_ID, null);

        flowQueueData = service.getFlowCommands().get(TEST_FLOW_ID);

        assertEquals(TEST_CORRELATION_ID_B, flowQueueData.getTaskInProgress());
        assertEquals(1, flowQueueData.getQueue().size());

        service.operationCompleted(TEST_FLOW_ID, null);

        flowQueueData = service.getFlowCommands().get(TEST_FLOW_ID);

        assertEquals(TEST_CORRELATION_ID_C, flowQueueData.getTaskInProgress());
        assertEquals(0, flowQueueData.getQueue().size());

        service.operationCompleted(TEST_FLOW_ID, null);

        Map<String, FlowQueueData> flowCommands = service.getFlowCommands();

        assertEquals(0, flowCommands.size());
    }

    @Test
    public void shouldAddLast() {
        service.addLast(TEST_FLOW_ID, TEST_CORRELATION_ID_A, new FlowRerouteRequest(TEST_FLOW_ID, false, false, ""));
        service.addLast(TEST_FLOW_ID, TEST_CORRELATION_ID_B, new FlowRerouteRequest(TEST_FLOW_ID, false, false, ""));

        FlowQueueData flowQueueData = service.getFlowCommands().get(TEST_FLOW_ID);

        assertEquals(TEST_CORRELATION_ID_A, flowQueueData.getTaskInProgress());
        assertEquals(1, flowQueueData.getQueue().size());

        service.operationCompleted(TEST_FLOW_ID, null);

        flowQueueData = service.getFlowCommands().get(TEST_FLOW_ID);

        assertEquals(TEST_CORRELATION_ID_B, flowQueueData.getTaskInProgress());
        assertEquals(0, flowQueueData.getQueue().size());

        service.operationCompleted(TEST_FLOW_ID, null);

        Map<String, FlowQueueData> flowCommands = service.getFlowCommands();

        assertEquals(0, flowCommands.size());
    }

    @Test
    public void shouldHandleTimeout() {
        service.addLast(TEST_FLOW_ID, TEST_CORRELATION_ID_A, new FlowRerouteRequest(TEST_FLOW_ID, false, false, ""));
        service.addLast(TEST_FLOW_ID, TEST_CORRELATION_ID_B, new FlowRerouteRequest(TEST_FLOW_ID, false, false, ""));

        FlowQueueData flowQueueData = service.getFlowCommands().get(TEST_FLOW_ID);

        assertEquals(TEST_CORRELATION_ID_A, flowQueueData.getTaskInProgress());
        assertEquals(1, flowQueueData.getQueue().size());

        service.handleTimeout(TEST_CORRELATION_ID_A);

        flowQueueData = service.getFlowCommands().get(TEST_FLOW_ID);

        assertEquals(TEST_CORRELATION_ID_B, flowQueueData.getTaskInProgress());
        assertEquals(0, flowQueueData.getQueue().size());

        service.operationCompleted(TEST_FLOW_ID, null);

        Map<String, FlowQueueData> flowCommands = service.getFlowCommands();

        assertEquals(0, flowCommands.size());
    }
}
