package org.bitbucket.openkilda.messaging.command.flow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.bitbucket.openkilda.messaging.AbstractSerializer;
import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.error.ErrorData;
import org.bitbucket.openkilda.messaging.error.ErrorMessage;
import org.bitbucket.openkilda.messaging.error.ErrorType;
import org.bitbucket.openkilda.messaging.info.InfoMessage;
import org.bitbucket.openkilda.messaging.info.event.IslInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathNode;
import org.bitbucket.openkilda.messaging.info.event.PortChangeType;
import org.bitbucket.openkilda.messaging.info.event.PortInfoData;
import org.bitbucket.openkilda.messaging.info.event.SwitchEventType;
import org.bitbucket.openkilda.messaging.info.event.SwitchInfoData;
import org.bitbucket.openkilda.messaging.info.flow.FlowPathResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowStatusResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowsResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowsStatusResponse;
import org.bitbucket.openkilda.messaging.payload.FlowPayload;
import org.bitbucket.openkilda.messaging.payload.request.FlowIdRequestPayload;
import org.bitbucket.openkilda.messaging.payload.request.FlowStatusRequestPayload;
import org.bitbucket.openkilda.messaging.payload.FlowEndpointPayload;
import org.bitbucket.openkilda.messaging.payload.response.FlowPathResponsePayload;
import org.bitbucket.openkilda.messaging.payload.response.FlowStatusResponsePayload;
import org.bitbucket.openkilda.messaging.payload.response.FlowStatusType;
import org.bitbucket.openkilda.messaging.payload.response.FlowsResponsePayload;
import org.bitbucket.openkilda.messaging.payload.response.FlowsStatusResponsePayload;
import org.bitbucket.openkilda.messaging.payload.response.OutputVlanType;

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
    private static final String TIMESTAMP = String.valueOf(System.currentTimeMillis());
    private static final int INPUT_PORT = 1;
    private static final int OUTPUT_PORT = 2;
    private static final int INPUT_VLAN_ID = 101;
    private static final int OUTPUT_VLAN_ID = 102;
    private static final int TRANSIT_VLAN_ID = 103;
    private static final int BANDWIDTH = 10000;
    private static final int METER_ID = 1;
    private static final int SOURCE_METER_ID = 2;
    private static final int DESTINATION_METER_ID = 3;
    private static final OutputVlanType OUTPUT_VLAN_TYPE = OutputVlanType.REPLACE;
    private static final FlowStatusType FLOW_STATUS = FlowStatusType.UP;
    private static final PortChangeType PORT_CHANGE = PortChangeType.OTHER_UPDATE;
    private static final SwitchEventType SWITCH_EVENT = SwitchEventType.CHANGED;

    private static final FlowIdRequestPayload flowId = new FlowIdRequestPayload(FLOW_NAME);
    private static final FlowStatusRequestPayload flowStatusRequest =
            new FlowStatusRequestPayload(FLOW_STATUS);
    private static final FlowStatusResponsePayload flowStatusResponse =
            new FlowStatusResponsePayload(FLOW_NAME, FLOW_STATUS);
    private static final FlowPayload flow =
            new FlowPayload(FLOW_NAME,
                    new FlowEndpointPayload(SWITCH_ID, INPUT_PORT, INPUT_VLAN_ID),
                    new FlowEndpointPayload(SWITCH_ID, OUTPUT_PORT, OUTPUT_VLAN_ID),
                    BANDWIDTH, CORRELATION_ID, TIMESTAMP);

    @Test
    public void serializeInstallEgressFlowMessageTest() throws IOException, ClassNotFoundException {
        InstallEgressFlowCommandData data = new InstallEgressFlowCommandData(FLOW_NAME, SWITCH_ID, INPUT_PORT, OUTPUT_PORT,
                TRANSIT_VLAN_ID, OUTPUT_VLAN_ID, OUTPUT_VLAN_TYPE);
        System.out.println(data);

        CommandMessage command = new CommandMessage(data, System.currentTimeMillis(), CORRELATION_ID);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() instanceof InstallEgressFlowCommandData);

        InstallEgressFlowCommandData resultData = (InstallEgressFlowCommandData) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
    }

    @Test
    public void serializeInstallIngressFlowMessageTest() throws IOException, ClassNotFoundException {
        InstallIngressFlowCommandData data = new InstallIngressFlowCommandData(FLOW_NAME, SWITCH_ID, INPUT_PORT, OUTPUT_PORT,
                INPUT_VLAN_ID, TRANSIT_VLAN_ID, OUTPUT_VLAN_TYPE, BANDWIDTH, METER_ID);
        System.out.println(data);

        CommandMessage command = new CommandMessage(data, System.currentTimeMillis(), CORRELATION_ID);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() instanceof InstallIngressFlowCommandData);

        InstallIngressFlowCommandData resultData = (InstallIngressFlowCommandData) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
    }

    @Test
    public void serializeInstallTransitFlowMessageTest() throws IOException, ClassNotFoundException {
        InstallTransitFlowCommandData data = new InstallTransitFlowCommandData(FLOW_NAME, SWITCH_ID, INPUT_PORT, OUTPUT_PORT,
                TRANSIT_VLAN_ID);
        System.out.println(data);

        CommandMessage command = new CommandMessage(data, System.currentTimeMillis(), CORRELATION_ID);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() instanceof InstallTransitFlowCommandData);

        InstallTransitFlowCommandData resultData = (InstallTransitFlowCommandData) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
    }

    @Test
    public void serializeInstallOneSwitchFlowMessageTest() throws IOException, ClassNotFoundException {
        InstallOneSwitchFlowCommandData data = new InstallOneSwitchFlowCommandData(FLOW_NAME, SWITCH_ID, INPUT_PORT, OUTPUT_PORT,
                INPUT_VLAN_ID, OUTPUT_VLAN_ID, OUTPUT_VLAN_TYPE, BANDWIDTH, SOURCE_METER_ID, DESTINATION_METER_ID);
        System.out.println(data);

        CommandMessage command = new CommandMessage(data, System.currentTimeMillis(), CORRELATION_ID);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() instanceof InstallOneSwitchFlowCommandData);

        InstallOneSwitchFlowCommandData resultData = (InstallOneSwitchFlowCommandData) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
    }

    @Test
    public void flowCreateRequestTest() throws IOException, ClassNotFoundException {
        FlowCreateRequest data = new FlowCreateRequest(flow);
        System.out.println(data);

        CommandMessage command = new CommandMessage(data, System.currentTimeMillis(), CORRELATION_ID);
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
        FlowUpdateRequest data = new FlowUpdateRequest(flow);
        System.out.println(data);

        CommandMessage command = new CommandMessage(data, System.currentTimeMillis(), CORRELATION_ID);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() instanceof FlowUpdateRequest);

        FlowUpdateRequest resultData = (FlowUpdateRequest) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
        assertEquals(flow.hashCode(), resultData.getPayload().hashCode());
    }

    @Test
    public void flowDeleteRequestTest() throws IOException, ClassNotFoundException {
        FlowDeleteRequest data = new FlowDeleteRequest(flowId);
        System.out.println(data);

        CommandMessage command = new CommandMessage(data, System.currentTimeMillis(), CORRELATION_ID);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() instanceof FlowDeleteRequest);

        FlowDeleteRequest resultData = (FlowDeleteRequest) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
        assertEquals(flowId.hashCode(), resultData.getPayload().hashCode());
    }

    @Test
    public void flowGetRequestTest() throws IOException, ClassNotFoundException {
        FlowGetRequest data = new FlowGetRequest(flowId);
        System.out.println(data);

        CommandMessage command = new CommandMessage(data, System.currentTimeMillis(), CORRELATION_ID);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() instanceof FlowGetRequest);

        FlowGetRequest resultData = (FlowGetRequest) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
        assertEquals(flowId.hashCode(), resultData.getPayload().hashCode());
    }

    @Test
    public void flowStatusRequestTest() throws IOException, ClassNotFoundException {
        FlowStatusRequest data = new FlowStatusRequest(flowId);
        System.out.println(data);

        CommandMessage command = new CommandMessage(data, System.currentTimeMillis(), CORRELATION_ID);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() instanceof FlowStatusRequest);

        FlowStatusRequest resultData = (FlowStatusRequest) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
        assertEquals(flowId.hashCode(), resultData.getPayload().hashCode());
    }

    @Test
    public void flowPathRequestTest() throws IOException, ClassNotFoundException {
        FlowPathRequest data = new FlowPathRequest(flowId);
        System.out.println(data);

        CommandMessage command = new CommandMessage(data, System.currentTimeMillis(), CORRELATION_ID);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() instanceof FlowPathRequest);

        FlowPathRequest resultData = (FlowPathRequest) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
        assertEquals(flowId.hashCode(), resultData.getPayload().hashCode());
    }

    @Test
    public void flowsStatusRequestTest() throws IOException, ClassNotFoundException {
        FlowsStatusRequest data = new FlowsStatusRequest(flowStatusRequest);
        System.out.println(data);

        CommandMessage command = new CommandMessage(data, System.currentTimeMillis(), CORRELATION_ID);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() instanceof FlowsStatusRequest);

        FlowsStatusRequest resultData = (FlowsStatusRequest) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
        assertEquals(flowStatusRequest.hashCode(), resultData.getPayload().hashCode());
    }

    @Test
    public void flowsGetRequestTest() throws IOException, ClassNotFoundException {
        FlowsGetRequest data = new FlowsGetRequest(flowStatusRequest);
        System.out.println(data);

        CommandMessage command = new CommandMessage(data, System.currentTimeMillis(), CORRELATION_ID);
        serialize(command);

        Message message = (Message) deserialize();
        assertTrue(message instanceof CommandMessage);

        CommandMessage resultCommand = (CommandMessage) message;
        assertTrue(resultCommand.getData() instanceof FlowsGetRequest);

        FlowsGetRequest resultData = (FlowsGetRequest) resultCommand.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
        assertEquals(flowStatusRequest.hashCode(), resultData.getPayload().hashCode());
    }

    @Test
    public void flowPathResponseTest() throws IOException, ClassNotFoundException {
        FlowPathResponsePayload payload = new FlowPathResponsePayload(FLOW_NAME, Collections.singletonList(SWITCH_ID));
        FlowPathResponse data = new FlowPathResponse(payload);
        System.out.println(data);

        InfoMessage info = new InfoMessage(data, System.currentTimeMillis(), CORRELATION_ID);
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
        FlowStatusResponse data = new FlowStatusResponse(flowStatusResponse);
        System.out.println(data);

        InfoMessage info = new InfoMessage(data, System.currentTimeMillis(), CORRELATION_ID);
        serialize(info);

        Message message = (Message) deserialize();
        assertTrue(message instanceof InfoMessage);

        InfoMessage resultInfo = (InfoMessage) message;
        assertTrue(resultInfo.getData() instanceof FlowStatusResponse);

        FlowStatusResponse resultData = (FlowStatusResponse) resultInfo.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
        assertEquals(flowStatusResponse.hashCode(), resultData.getPayload().hashCode());
    }

    @Test
    public void flowsStatusResponseTest() throws IOException, ClassNotFoundException {
        FlowsStatusResponsePayload payload = new FlowsStatusResponsePayload(Collections.singletonList(flowStatusResponse));
        FlowsStatusResponse data = new FlowsStatusResponse(payload);
        System.out.println(data);

        InfoMessage info = new InfoMessage(data, System.currentTimeMillis(), CORRELATION_ID);
        serialize(info);

        Message message = (Message) deserialize();
        assertTrue(message instanceof InfoMessage);

        InfoMessage resultInfo = (InfoMessage) message;
        assertTrue(resultInfo.getData() instanceof FlowsStatusResponse);

        FlowsStatusResponse resultData = (FlowsStatusResponse) resultInfo.getData();
        System.out.println(resultData);
        assertEquals(data, resultData);
        assertEquals(data.hashCode(), resultData.hashCode());
        assertEquals(payload.hashCode(), resultData.getPayload().hashCode());
    }

    @Test
    public void flowResponseTest() throws IOException, ClassNotFoundException {
        FlowResponse data = new FlowResponse(flow);
        System.out.println(data);

        InfoMessage info = new InfoMessage(data, System.currentTimeMillis(), CORRELATION_ID);
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
        FlowsResponsePayload payload = new FlowsResponsePayload(Collections.singletonList(flow));
        FlowsResponse data = new FlowsResponse(payload);
        System.out.println(data);

        InfoMessage info = new InfoMessage(data, System.currentTimeMillis(), CORRELATION_ID);
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
        IslInfoData data = new IslInfoData(0, Collections.singletonList(payload));
        System.out.println(data);

        InfoMessage info = new InfoMessage(data, System.currentTimeMillis(), CORRELATION_ID);
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

        InfoMessage info = new InfoMessage(data, System.currentTimeMillis(), CORRELATION_ID);
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

        InfoMessage info = new InfoMessage(data, System.currentTimeMillis(), CORRELATION_ID);
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
        SwitchInfoData data = new SwitchInfoData(SWITCH_ID, SWITCH_EVENT);
        System.out.println(data);

        InfoMessage info = new InfoMessage(data, System.currentTimeMillis(), CORRELATION_ID);
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
        ErrorData data = new ErrorData(1, null, ErrorType.AUTH_FAILED, "exception");
        System.out.println(data);

        ErrorMessage info = new ErrorMessage(data, System.currentTimeMillis(), CORRELATION_ID);
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
}
