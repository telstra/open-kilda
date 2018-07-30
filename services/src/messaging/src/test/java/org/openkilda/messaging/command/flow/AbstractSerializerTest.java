/* Copyright 2017 Telstra Open Source
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

package org.openkilda.messaging.command.flow;

import static org.openkilda.messaging.command.Constants.flowName;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.openkilda.messaging.AbstractSerializer;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.discovery.HealthCheckCommandData;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.NetworkInfoData;
import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;
import org.openkilda.messaging.info.flow.FlowOperation;
import org.openkilda.messaging.info.flow.FlowPathResponse;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.messaging.info.flow.FlowStatusResponse;
import org.openkilda.messaging.info.flow.FlowsResponse;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.messaging.payload.flow.OutputVlanType;

import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Ignore
public abstract class AbstractSerializerTest implements AbstractSerializer {
    private static final String FLOW_NAME = "test_flow";
    private static final String SWITCH_ID = "00:00:00:00:00:00:00:00";
    private static final String CORRELATION_ID = UUID.randomUUID().toString();
    private static final long TIMESTAMP = System.currentTimeMillis();
    private static final int INPUT_PORT = 1;
    private static final int OUTPUT_PORT = 2;
    private static final int INPUT_VLAN_ID = 101;
    private static final int OUTPUT_VLAN_ID = 102;
    private static final int TRANSIT_VLAN_ID = 103;
    private static final long BANDWIDTH = 10000L;
    private static final long COOKIE = 0x1L;
    private static final long METER_ID = 0L;
    private static final OutputVlanType OUTPUT_VLAN_TYPE = OutputVlanType.REPLACE;
    private static final FlowState FLOW_STATUS = FlowState.UP;
    private static final PortChangeType PORT_CHANGE = PortChangeType.OTHER_UPDATE;
    private static final SwitchState SWITCH_EVENT = SwitchState.CHANGED;
    private static final Destination DESTINATION = null;

    private static final FlowIdStatusPayload flowsIdStatusRequest = new FlowIdStatusPayload();
    private static final FlowIdStatusPayload flowIdStatusRequest = new FlowIdStatusPayload(FLOW_NAME);
    private static final FlowIdStatusPayload flowIdStatusResponse = new FlowIdStatusPayload(FLOW_NAME, FLOW_STATUS);

    private static final String requester = "requester-id";
    private static final SwitchInfoData sw1 = new SwitchInfoData("sw1",
            SwitchState.ACTIVATED, "1.1.1.1", "sw1", "switch-1", "kilda");
    private static final SwitchInfoData sw2 = new SwitchInfoData("sw2",
            SwitchState.ACTIVATED, "2.2.2.2", "sw2", "switch-2", "kilda");
    private static final List<PathNode> nodes = Arrays.asList(
            new PathNode("sw1", 1, 0, 0L),
            new PathNode("sw2", 2, 1, 0L));
    private static final IslInfoData isl = new IslInfoData(0L, nodes, 1000L, IslChangeType.DISCOVERED, 900L);
    private static final PathInfoData path = new PathInfoData(0L, nodes);
    private static final Flow flowModel = new Flow(FLOW_NAME, 1000, false, COOKIE, FLOW_NAME, String.valueOf(TIMESTAMP),
            "sw1", "sw2", 10, 20, 100, 200, 1, 1024, path, FLOW_STATUS);

    @Test
    public void serializeInstallEgressFlowMessageTest() throws IOException, ClassNotFoundException {
        InstallEgressFlow data = new InstallEgressFlow(TIMESTAMP, FLOW_NAME, COOKIE,
                SWITCH_ID, INPUT_PORT, OUTPUT_PORT, TRANSIT_VLAN_ID, OUTPUT_VLAN_ID, OUTPUT_VLAN_TYPE);
        System.out.println(data);

        CommandMessage command = new CommandMessage(data, System.currentTimeMillis(), CORRELATION_ID, DESTINATION);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() instanceof InstallEgressFlow);

        InstallEgressFlow resultData = (InstallEgressFlow) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
    }

    @Test
    public void serializeInstallIngressFlowMessageTest() throws IOException, ClassNotFoundException {
        InstallIngressFlow data = new InstallIngressFlow(TIMESTAMP, FLOW_NAME, COOKIE, SWITCH_ID,
                INPUT_PORT, OUTPUT_PORT, INPUT_VLAN_ID, TRANSIT_VLAN_ID, OUTPUT_VLAN_TYPE, BANDWIDTH, METER_ID);
        System.out.println(data);

        CommandMessage command = new CommandMessage(data, System.currentTimeMillis(), CORRELATION_ID, DESTINATION);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() instanceof InstallIngressFlow);

        InstallIngressFlow resultData = (InstallIngressFlow) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
    }

    @Test
    public void serializeInstallTransitFlowMessageTest() throws IOException, ClassNotFoundException {
        InstallTransitFlow data = new InstallTransitFlow(TIMESTAMP, FLOW_NAME, COOKIE,
                SWITCH_ID, INPUT_PORT, OUTPUT_PORT, TRANSIT_VLAN_ID);
        System.out.println(data);

        CommandMessage command = new CommandMessage(data, System.currentTimeMillis(), CORRELATION_ID, DESTINATION);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() instanceof InstallTransitFlow);

        InstallTransitFlow resultData = (InstallTransitFlow) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
    }

    @Test
    public void serializeInstallOneSwitchFlowMessageTest() throws IOException, ClassNotFoundException {
        InstallOneSwitchFlow data = new InstallOneSwitchFlow(TIMESTAMP, FLOW_NAME, COOKIE, SWITCH_ID, INPUT_PORT,
                OUTPUT_PORT, INPUT_VLAN_ID, OUTPUT_VLAN_ID, OUTPUT_VLAN_TYPE, BANDWIDTH, METER_ID);
        System.out.println(data);

        CommandMessage command = new CommandMessage(data, System.currentTimeMillis(), CORRELATION_ID, DESTINATION);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() instanceof InstallOneSwitchFlow);

        InstallOneSwitchFlow resultData = (InstallOneSwitchFlow) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
    }

    @Test
    public void flowCreateRequestTest() throws IOException, ClassNotFoundException {
        FlowCreateRequest data = new FlowCreateRequest(flowModel);
        System.out.println(data);

        CommandMessage command = new CommandMessage(data, System.currentTimeMillis(), CORRELATION_ID, DESTINATION);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() instanceof FlowCreateRequest);

        FlowCreateRequest resultData = (FlowCreateRequest) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
        assertEquals(flowModel.hashCode(), resultData.getPayload().hashCode());
    }

    @Test
    public void flowUpdateRequestTest() throws IOException, ClassNotFoundException {
        FlowUpdateRequest data = new FlowUpdateRequest(flowModel);
        System.out.println(data);

        CommandMessage command = new CommandMessage(data, System.currentTimeMillis(), CORRELATION_ID, DESTINATION);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() instanceof FlowUpdateRequest);

        FlowUpdateRequest resultData = (FlowUpdateRequest) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
        assertEquals(flowModel.hashCode(), resultData.getPayload().hashCode());
    }

    @Test
    public void flowDeleteRequestTest() throws IOException, ClassNotFoundException {
        Flow deleteFlow = new Flow();
        deleteFlow.setFlowId(flowName);
        FlowDeleteRequest data = new FlowDeleteRequest(deleteFlow);
        System.out.println(data);

        CommandMessage command = new CommandMessage(data, System.currentTimeMillis(), CORRELATION_ID, DESTINATION);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() instanceof FlowDeleteRequest);

        FlowDeleteRequest resultData = (FlowDeleteRequest) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
        assertEquals(deleteFlow.hashCode(), resultData.getPayload().hashCode());
    }

    @Test
    public void flowGetRequestTest() throws IOException, ClassNotFoundException {
        FlowGetRequest data = new FlowGetRequest(flowIdStatusRequest);
        System.out.println(data);

        CommandMessage command = new CommandMessage(data, System.currentTimeMillis(), CORRELATION_ID, DESTINATION);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() instanceof FlowGetRequest);

        FlowGetRequest resultData = (FlowGetRequest) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
        assertEquals(flowIdStatusRequest.hashCode(), resultData.getPayload().hashCode());
    }

    @Test
    public void flowStatusRequestTest() throws IOException, ClassNotFoundException {
        FlowStatusRequest data = new FlowStatusRequest(flowIdStatusRequest);
        System.out.println(data);

        CommandMessage command = new CommandMessage(data, System.currentTimeMillis(), CORRELATION_ID, DESTINATION);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() instanceof FlowStatusRequest);

        FlowStatusRequest resultData = (FlowStatusRequest) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
        assertEquals(flowIdStatusRequest.hashCode(), resultData.getPayload().hashCode());
    }

    @Test
    public void flowPathRequestTest() throws IOException, ClassNotFoundException {
        FlowPathRequest data = new FlowPathRequest(flowIdStatusRequest);
        System.out.println(data);

        CommandMessage command = new CommandMessage(data, System.currentTimeMillis(), CORRELATION_ID, DESTINATION);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() instanceof FlowPathRequest);

        FlowPathRequest resultData = (FlowPathRequest) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
        assertEquals(flowIdStatusRequest.hashCode(), resultData.getPayload().hashCode());
    }

    @Test
    public void flowPathResponseTest() throws IOException, ClassNotFoundException {
        FlowPathResponse data = new FlowPathResponse(path);
        System.out.println(data);

        InfoMessage info = new InfoMessage(data, System.currentTimeMillis(), CORRELATION_ID, DESTINATION);
        serialize(info);

        Message message = (Message) deserialize();
        assertTrue(message instanceof InfoMessage);

        InfoMessage resultInfo = (InfoMessage) message;
        assertTrue(resultInfo.getData() instanceof FlowPathResponse);

        FlowPathResponse resultData = (FlowPathResponse) resultInfo.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
        assertEquals(path.hashCode(), resultData.getPayload().hashCode());
    }

    @Test
    public void flowStatusResponseTest() throws IOException, ClassNotFoundException {
        FlowStatusResponse data = new FlowStatusResponse(flowIdStatusResponse);
        System.out.println(data);

        InfoMessage info = new InfoMessage(data, System.currentTimeMillis(), CORRELATION_ID, DESTINATION);
        serialize(info);

        Message message = (Message) deserialize();
        assertTrue(message instanceof InfoMessage);

        InfoMessage resultInfo = (InfoMessage) message;
        assertTrue(resultInfo.getData() instanceof FlowStatusResponse);

        FlowStatusResponse resultData = (FlowStatusResponse) resultInfo.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
        assertEquals(flowIdStatusResponse.hashCode(), resultData.getPayload().hashCode());
    }

    @Test
    public void flowResponseTest() throws IOException, ClassNotFoundException {
        FlowResponse data = new FlowResponse(flowModel);
        System.out.println(data);

        InfoMessage info = new InfoMessage(data, System.currentTimeMillis(), CORRELATION_ID, DESTINATION);
        serialize(info);

        Message message = (Message) deserialize();
        assertTrue(message instanceof InfoMessage);

        InfoMessage resultInfo = (InfoMessage) message;
        assertTrue(resultInfo.getData() instanceof FlowResponse);

        FlowResponse resultData = (FlowResponse) resultInfo.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
        assertEquals(flowModel.hashCode(), resultData.getPayload().hashCode());
    }

    @Test
    public void flowsResponseTest() throws IOException, ClassNotFoundException {
        FlowsResponse data = new FlowsResponse(Collections.singletonList(flowModel.getFlowId()));
        System.out.println(data);

        InfoMessage info = new InfoMessage(data, System.currentTimeMillis(), CORRELATION_ID, DESTINATION);
        serialize(info);

        Message message = (Message) deserialize();
        assertTrue(message instanceof InfoMessage);

        InfoMessage resultInfo = (InfoMessage) message;
        assertTrue(resultInfo.getData() instanceof FlowsResponse);

        FlowsResponse resultData = (FlowsResponse) resultInfo.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
        assertEquals(Collections.singletonList(flowModel.getFlowId()).hashCode(), resultData.getFlowIds().hashCode());
    }

    @Test
    public void eventIslInfoTest() throws IOException, ClassNotFoundException {
        PathNode payload = new PathNode(SWITCH_ID, INPUT_PORT, 0);
        IslInfoData data = new IslInfoData(0L, Collections.singletonList(payload),
                1000000L, IslChangeType.DISCOVERED, 900000L);
        assertEquals(SWITCH_ID + "_" + String.valueOf(INPUT_PORT), data.getId());
        System.out.println(data);

        InfoMessage info = new InfoMessage(data, System.currentTimeMillis(), CORRELATION_ID, DESTINATION);
        serialize(info);

        Message message = (Message) deserialize();
        assertTrue(message instanceof InfoMessage);

        InfoMessage resultInfo = (InfoMessage) message;
        assertTrue(resultInfo.getData() instanceof IslInfoData);

        IslInfoData resultData = (IslInfoData) resultInfo.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
        assertEquals(payload.hashCode(), resultData.getPath().get(0).hashCode());
    }

    @Test
    public void eventPathInfoTest() throws IOException, ClassNotFoundException {
        PathInfoData data = new PathInfoData();
        System.out.println(data);

        InfoMessage info = new InfoMessage(data, System.currentTimeMillis(), CORRELATION_ID, DESTINATION);
        serialize(info);

        Message message = (Message) deserialize();
        assertTrue(message instanceof InfoMessage);

        InfoMessage resultInfo = (InfoMessage) message;
        assertTrue(resultInfo.getData() instanceof PathInfoData);

        PathInfoData resultData = (PathInfoData) resultInfo.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
    }

    @Test
    public void eventPortInfoTest() throws IOException, ClassNotFoundException {
        PortInfoData data = new PortInfoData(SWITCH_ID, INPUT_PORT, 0, PORT_CHANGE);
        System.out.println(data);

        InfoMessage info = new InfoMessage(data, System.currentTimeMillis(), CORRELATION_ID, DESTINATION);
        serialize(info);

        Message message = (Message) deserialize();
        assertTrue(message instanceof InfoMessage);

        InfoMessage resultInfo = (InfoMessage) message;
        assertTrue(resultInfo.getData() instanceof PortInfoData);

        PortInfoData resultData = (PortInfoData) resultInfo.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
    }

    @Test
    public void eventSwitchInfoTest() throws IOException, ClassNotFoundException {
        SwitchInfoData data = new SwitchInfoData(SWITCH_ID, SWITCH_EVENT, "127.0.0.1", "localhost", "sw", "controller");
        System.out.println(data);

        InfoMessage info = new InfoMessage(data, System.currentTimeMillis(), CORRELATION_ID, DESTINATION);
        serialize(info);

        Message message = (Message) deserialize();
        assertTrue(message instanceof InfoMessage);

        InfoMessage resultInfo = (InfoMessage) message;
        assertTrue(resultInfo.getData() instanceof SwitchInfoData);

        SwitchInfoData resultData = (SwitchInfoData) resultInfo.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
    }

    @Test
    public void errorMessageTest() throws IOException, ClassNotFoundException {
        ErrorData data = new ErrorData(ErrorType.AUTH_FAILED, FLOW_NAME, "Bad credentials");
        System.out.println(data);

        ErrorMessage info = new ErrorMessage(data, System.currentTimeMillis(), CORRELATION_ID, DESTINATION);
        info.setData(data);
        serialize(info);

        Message message = (Message) deserialize();
        assertTrue(message instanceof ErrorMessage);

        ErrorMessage resultInfo = (ErrorMessage) message;
        assertTrue(resultInfo.getData() != null);

        ErrorData resultData = resultInfo.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
    }

    @Test
    public void removeCommandTest() throws IOException, ClassNotFoundException {
        RemoveFlow data = new RemoveFlow(TIMESTAMP, FLOW_NAME, COOKIE, SWITCH_ID, METER_ID,
                DeleteRulesCriteria.builder().cookie(COOKIE).build());
        System.out.println(data);

        CommandMessage command = new CommandMessage(data, System.currentTimeMillis(), CORRELATION_ID, DESTINATION);
        command.setData(data);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() != null);

        RemoveFlow resultData = (RemoveFlow) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
    }

    @Test
    public void flowRerouteCommandTest() throws IOException, ClassNotFoundException {
        FlowRerouteRequest data = new FlowRerouteRequest(flowModel, FlowOperation.CREATE);
        System.out.println(data);

        CommandMessage command = new CommandMessage(data, System.currentTimeMillis(), CORRELATION_ID, DESTINATION);
        command.setData(data);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() != null);

        FlowRerouteRequest resultData = (FlowRerouteRequest) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
    }

    @Test
    public void dumpNetworkCommandTest() throws IOException, ClassNotFoundException {
        HealthCheckCommandData data = new HealthCheckCommandData("requester");
        System.out.println(data);

        CommandMessage command = new CommandMessage(data, System.currentTimeMillis(), CORRELATION_ID, DESTINATION);
        command.setData(data);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() != null);

        HealthCheckCommandData resultData = (HealthCheckCommandData) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
    }

    @Test
    public void dumpNetworkResponseTest() throws IOException, ClassNotFoundException {
        NetworkInfoData data = new NetworkInfoData(requester,
                new HashSet<>(Arrays.asList(sw1, sw2)),
                new HashSet<>(),
                Collections.singleton(isl),
                Collections.singleton(new ImmutablePair<>(flowModel, flowModel)));
        System.out.println(data);

        InfoMessage info = new InfoMessage(data, System.currentTimeMillis(), CORRELATION_ID, DESTINATION);
        info.setData(data);
        serialize(info);

        Message message = (Message) deserialize();
        assertTrue(message instanceof InfoMessage);

        InfoMessage resultInfo = (InfoMessage) message;
        assertTrue(resultInfo.getData() != null);

        NetworkInfoData resultData = (NetworkInfoData) resultInfo.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
    }
}
