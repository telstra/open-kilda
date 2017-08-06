package org.bitbucket.openkilda.messaging.command.flow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.bitbucket.openkilda.messaging.AbstractSerializer;
import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.command.routing.FlowReroute;
import org.bitbucket.openkilda.messaging.error.ErrorData;
import org.bitbucket.openkilda.messaging.error.ErrorMessage;
import org.bitbucket.openkilda.messaging.error.ErrorType;
import org.bitbucket.openkilda.messaging.info.InfoMessage;
import org.bitbucket.openkilda.messaging.info.event.IslChangeType;
import org.bitbucket.openkilda.messaging.info.event.IslInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathNode;
import org.bitbucket.openkilda.messaging.info.event.PortChangeType;
import org.bitbucket.openkilda.messaging.info.event.PortInfoData;
import org.bitbucket.openkilda.messaging.info.event.SwitchState;
import org.bitbucket.openkilda.messaging.info.event.SwitchInfoData;
import org.bitbucket.openkilda.messaging.info.flow.FlowPathResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowStatusResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowsResponse;
import org.bitbucket.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPathPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowState;
import org.bitbucket.openkilda.messaging.payload.flow.FlowsPayload;
import org.bitbucket.openkilda.messaging.payload.flow.OutputVlanType;

import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
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
    private static final FlowPayload flow = new FlowPayload(FLOW_NAME,
            new FlowEndpointPayload(SWITCH_ID, INPUT_PORT, INPUT_VLAN_ID),
            new FlowEndpointPayload(SWITCH_ID, OUTPUT_PORT, OUTPUT_VLAN_ID),
            BANDWIDTH, CORRELATION_ID, String.valueOf(TIMESTAMP));
    private static final FlowPayload cookieFlow = new FlowPayload(FLOW_NAME, COOKIE,
            new FlowEndpointPayload(SWITCH_ID, INPUT_PORT, INPUT_VLAN_ID),
            new FlowEndpointPayload(SWITCH_ID, OUTPUT_PORT, OUTPUT_VLAN_ID),
            BANDWIDTH, CORRELATION_ID, String.valueOf(TIMESTAMP), OUTPUT_VLAN_TYPE);

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
        FlowCreateRequest data = new FlowCreateRequest(flow);
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
        assertEquals(flow.hashCode(), resultData.getPayload().hashCode());
    }

    @Test
    public void flowUpdateRequestTest() throws IOException, ClassNotFoundException {
        FlowUpdateRequest data = new FlowUpdateRequest(cookieFlow);
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
        assertEquals(cookieFlow.hashCode(), resultData.getPayload().hashCode());
    }

    @Test
    public void flowDeleteRequestTest() throws IOException, ClassNotFoundException {
        FlowDeleteRequest data = new FlowDeleteRequest(flowIdStatusRequest);
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
        assertEquals(flowIdStatusRequest.hashCode(), resultData.getPayload().hashCode());
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
    public void flowsGetRequestTest() throws IOException, ClassNotFoundException {
        FlowsGetRequest data = new FlowsGetRequest(flowsIdStatusRequest);
        System.out.println(data);

        CommandMessage command = new CommandMessage(data, System.currentTimeMillis(), CORRELATION_ID, DESTINATION);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() instanceof FlowsGetRequest);

        FlowsGetRequest resultData = (FlowsGetRequest) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
        assertEquals(flowsIdStatusRequest.hashCode(), resultData.getPayload().hashCode());
    }

    @Test
    public void flowPathResponseTest() throws IOException, ClassNotFoundException {
        FlowPathPayload payload = new FlowPathPayload(FLOW_NAME, Collections.singletonList(SWITCH_ID));
        FlowPathResponse data = new FlowPathResponse(payload);
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
        assertEquals(payload.hashCode(), resultData.getPayload().hashCode());
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
        FlowResponse data = new FlowResponse(flow);
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
        assertEquals(flow.hashCode(), resultData.getPayload().hashCode());
    }

    @Test
    public void flowsResponseTest() throws IOException, ClassNotFoundException {
        FlowsPayload payload = new FlowsPayload(Collections.singletonList(flow));
        FlowsResponse data = new FlowsResponse(payload);
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
        assertEquals(payload.hashCode(), resultData.getPayload().hashCode());
    }

    @Test
    public void eventIslInfoTest() throws IOException, ClassNotFoundException {
        PathNode payload = new PathNode(SWITCH_ID, INPUT_PORT, 0);
        IslInfoData data = new IslInfoData(0L, Collections.singletonList(payload), 1000000, IslChangeType.DISCOVERED);
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
        SwitchInfoData data = new SwitchInfoData(SWITCH_ID, SWITCH_EVENT, "127.0.0.1", "localhost", "Unknown");
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
        RemoveFlow data = new RemoveFlow(TIMESTAMP, FLOW_NAME, COOKIE, SWITCH_ID, METER_ID);
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
        FlowReroute data = new FlowReroute(FLOW_NAME, SWITCH_ID, OUTPUT_PORT);
        System.out.println(data);

        CommandMessage command = new CommandMessage(data, System.currentTimeMillis(), CORRELATION_ID, DESTINATION);
        command.setData(data);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() != null);

        FlowReroute resultData = (FlowReroute) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
    }

    @Test
    public void flowRerouteEmptyCommandTest() throws IOException, ClassNotFoundException {
        FlowReroute data = new FlowReroute();
        System.out.println(data);

        CommandMessage command = new CommandMessage(data, System.currentTimeMillis(), CORRELATION_ID, DESTINATION);
        command.setData(data);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() != null);

        FlowReroute resultData = (FlowReroute) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
    }
}
