package org.bitbucket.openkilda.wfm.topology.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.storm.Config;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.utils.Utils;

import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.Topic;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.command.flow.*;
import org.bitbucket.openkilda.messaging.error.ErrorData;
import org.bitbucket.openkilda.messaging.error.ErrorMessage;
import org.bitbucket.openkilda.messaging.error.ErrorType;
import org.bitbucket.openkilda.messaging.info.InfoMessage;
import org.bitbucket.openkilda.messaging.info.flow.FlowPathResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowStatusResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowsResponse;
import org.bitbucket.openkilda.messaging.payload.flow.*;
import org.bitbucket.openkilda.wfm.AbstractStormTest;
import org.bitbucket.openkilda.wfm.topology.TestKafkaConsumer;
import org.junit.*;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.*;

public class FlowTopologyTest extends AbstractStormTest {
    private static final long COOKIE = 0x1FFFFFFFFL;
    private static final FlowEndpointPayload flowEndpointPayload = new FlowEndpointPayload("test-switch", 1, 2);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static TestKafkaConsumer nbConsumer;
    private static TestKafkaConsumer ofsConsumer;
    private static TestKafkaConsumer teRequestConsumer;
    private static TestKafkaConsumer teResponseConsumer;
    private static FlowTopology flowTopology;

    @BeforeClass
    public static void setupOnce() throws Exception {
        AbstractStormTest.setupOnce();

        flowTopology = new FlowTopology();
        StormTopology stormTopology = flowTopology.createTopology();
        Config config = stormConfig();
        cluster.submitTopology(FlowTopologyTest.class.getSimpleName(), config, stormTopology);

        nbConsumer = new TestKafkaConsumer(Topic.TEST.getId(), Destination.NORTHBOUND,
                kafkaProperties(UUID.nameUUIDFromBytes(Destination.NORTHBOUND.toString().getBytes()).toString()));
        nbConsumer.start();
        ofsConsumer = new TestKafkaConsumer(Topic.TEST.getId(), Destination.CONTROLLER,
        kafkaProperties(UUID.nameUUIDFromBytes(Destination.CONTROLLER.toString().getBytes()).toString()));
        ofsConsumer.start();
        teRequestConsumer = new TestKafkaConsumer(Topic.TEST.getId(), Destination.TOPOLOGY_ENGINE,
        kafkaProperties(UUID.nameUUIDFromBytes(Destination.TOPOLOGY_ENGINE.toString().getBytes()).toString()));
        teRequestConsumer.start();
        teResponseConsumer = new TestKafkaConsumer(Topic.TEST.getId(), Destination.WFM,
        kafkaProperties(UUID.nameUUIDFromBytes(Destination.WFM.toString().getBytes()).toString()));
        teResponseConsumer.start();

        Utils.sleep(10000);
    }

    @AfterClass
    public static void teardownOnce() throws Exception {
        nbConsumer.wakeup();
        nbConsumer.join();
        ofsConsumer.wakeup();
        ofsConsumer.join();
        teRequestConsumer.wakeup();
        teRequestConsumer.join();
        teResponseConsumer.wakeup();
        teResponseConsumer.join();
        AbstractStormTest.teardownOnce();
    }

    @Before
    public void setup() throws Exception {
        nbConsumer.clear();
        ofsConsumer.clear();
        teRequestConsumer.clear();
        teResponseConsumer.clear();
    }

    @After
    public void teardown() throws Exception {
        nbConsumer.clear();
        ofsConsumer.clear();
        teRequestConsumer.clear();
        teResponseConsumer.clear();
    }

    @Test
    public void createFlowCommandBoltTest() throws Exception {
        ConsumerRecord<String, String> nbRecord;
        ConsumerRecord<String, String> teRecord;
        String flowId = UUID.randomUUID().toString();
        FlowPayload payload = createFlow(flowId);

        nbRecord = nbConsumer.pollMessage();
        assertNotNull(nbRecord);
        assertNotNull(nbRecord.value());

        InfoMessage infoMessage = objectMapper.readValue(nbRecord.value(), InfoMessage.class);
        FlowResponse infoData = (FlowResponse) infoMessage.getData();
        assertNotNull(infoData);

        teRecord = teRequestConsumer.pollMessage();
        assertNotNull(teRecord);
        assertNotNull(teRecord.value());

        CommandMessage commandMessage = objectMapper.readValue(teRecord.value(), CommandMessage.class);
        FlowCreateRequest commandData = (FlowCreateRequest) commandMessage.getData();
        assertNotNull(commandData);

        FlowPayload flowTePayload = commandData.getPayload();
    }

    @Test
    public void createAlreadyExistsFlowCommandBoltTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;
        createFlow(flowId);
        nbConsumer.pollMessage();
        teRequestConsumer.pollMessage();
        createFlow(flowId);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        ErrorMessage errorMessage = objectMapper.readValue(record.value(), ErrorMessage.class);
        assertNotNull(errorMessage);

        ErrorData errorData = errorMessage.getData();
        assertEquals(ErrorType.ALREADY_EXISTS, errorData.getErrorType());
    }

    @Test
    public void deleteFlowCommandBoltTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;
        createFlow(flowId);
        nbConsumer.pollMessage();
        teRequestConsumer.pollMessage();
        FlowIdStatusPayload payload = deleteFlow(flowId);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        InfoMessage infoMessage = objectMapper.readValue(record.value(), InfoMessage.class);
        FlowStatusResponse infoData = (FlowStatusResponse) infoMessage.getData();
        assertNotNull(infoData);

        FlowIdStatusPayload flowNbPayload = infoData.getPayload();
        assertEquals(payload, flowNbPayload);

        record = teRequestConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        CommandMessage commandMessage = objectMapper.readValue(record.value(), CommandMessage.class);
        FlowDeleteRequest commandData = (FlowDeleteRequest) commandMessage.getData();
        assertNotNull(commandData);

        FlowIdStatusPayload flowTePayload = commandData.getPayload();
        assertEquals(payload, flowTePayload);
    }

    @Test
    public void deleteUnknownFlowCommandBoltTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;
        FlowIdStatusPayload payload = deleteFlow(flowId);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        ErrorMessage errorMessage = objectMapper.readValue(record.value(), ErrorMessage.class);
        assertNotNull(errorMessage);

        ErrorData errorData = errorMessage.getData();
        assertEquals(ErrorType.NOT_FOUND, errorData.getErrorType());
    }

    @Test
    public void updateFlowCommandBoltTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;
        ConsumerRecord<String, String> newRecord;
        createFlow(flowId);
        nbConsumer.pollMessage();
        teRequestConsumer.pollMessage();
        FlowPayload payload = updateFlow(flowId);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        InfoMessage infoMessage = objectMapper.readValue(record.value(), InfoMessage.class);
        FlowResponse infoData = (FlowResponse) infoMessage.getData();
        assertNotNull(infoData);

        FlowPayload flowNbPayload = infoData.getPayload();
        assertNull(flowNbPayload.getCookie());

        newRecord = teRequestConsumer.pollMessage();
        assertNotNull(newRecord);

        CommandMessage commandMessage = objectMapper.readValue(newRecord.value(), CommandMessage.class);
        FlowUpdateRequest commandData = (FlowUpdateRequest) commandMessage.getData();
        assertNotNull(commandData);

        FlowPayload flowTePayload = commandData.getPayload();
        assertEquals(flowNbPayload, flowTePayload);
    }

    @Test
    public void updateUnknownFlowCommandBoltTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;
        updateFlow(flowId);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        ErrorMessage errorMessage = objectMapper.readValue(record.value(), ErrorMessage.class);
        assertNotNull(errorMessage);

        ErrorData errorData = errorMessage.getData();
        assertEquals(ErrorType.NOT_FOUND, errorData.getErrorType());
    }

    @Test
    public void statusFlowTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;

        createFlow(flowId);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        record = teRequestConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        statusFlow(flowId);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        InfoMessage infoMessage = objectMapper.readValue(record.value(), InfoMessage.class);
        assertNotNull(infoMessage);

        FlowStatusResponse infoData = (FlowStatusResponse) infoMessage.getData();
        assertNotNull(infoData);

        FlowIdStatusPayload flowNbPayload = infoData.getPayload();
        assertNotNull(flowNbPayload);
        assertEquals(flowId, flowNbPayload.getId());
        assertEquals(FlowStatusType.ALLOCATED, flowNbPayload.getStatus());
    }

    @Test
    public void statusUnknownFlowTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;
        statusFlow(flowId);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        ErrorMessage errorMessage = objectMapper.readValue(record.value(), ErrorMessage.class);
        assertNotNull(errorMessage);

        ErrorData errorData = errorMessage.getData();
        assertEquals(ErrorType.NOT_FOUND, errorData.getErrorType());
    }

    @Test
    public void pathFlowTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> nbRecord;
        ConsumerRecord<String, String> teRecord;
        createFlow(flowId);
        nbConsumer.pollMessage();
        teRequestConsumer.pollMessage();
        FlowIdStatusPayload payload = pathFlow(flowId);

        teRecord = teRequestConsumer.pollMessage();
        assertNotNull(teRecord);
        assertNotNull(teRecord.value());

        CommandMessage commandMessage = objectMapper.readValue(teRecord.value(), CommandMessage.class);
        FlowPathRequest commandData = (FlowPathRequest) commandMessage.getData();
        assertNotNull(commandData);

        FlowIdStatusPayload flowTePayload = commandData.getPayload();
        assertEquals(payload, flowTePayload);
    }

    @Test
    public void pathUnknownFlowTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;
        pathFlow(flowId);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        ErrorMessage errorMessage = objectMapper.readValue(record.value(), ErrorMessage.class);
        assertNotNull(errorMessage);

        ErrorData errorData = errorMessage.getData();
        assertEquals(ErrorType.NOT_FOUND, errorData.getErrorType());
    }

    @Test
    public void getFlowTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> nbRecord;
        ConsumerRecord<String, String> teRecord;
        createFlow(flowId);
        nbConsumer.pollMessage();
        teRequestConsumer.pollMessage();
        FlowIdStatusPayload payload = getFlow(flowId);

        teRecord = teRequestConsumer.pollMessage();
        assertNotNull(teRecord);
        assertNotNull(teRecord.value());

        CommandMessage commandMessage = objectMapper.readValue(teRecord.value(), CommandMessage.class);
        FlowGetRequest commandData = (FlowGetRequest) commandMessage.getData();
        assertNotNull(commandData);

        FlowIdStatusPayload flowTePayload = commandData.getPayload();
        assertEquals(payload, flowTePayload);
    }

    @Test
    public void getUnknownFlowTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;
        getFlow(flowId);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        ErrorMessage errorMessage = objectMapper.readValue(record.value(), ErrorMessage.class);
        assertNotNull(errorMessage);

        ErrorData errorData = errorMessage.getData();
        assertEquals(ErrorType.NOT_FOUND, errorData.getErrorType());
    }

    @Test
    public void dumpFlowsTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;
        dumpFlows(flowId);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        ErrorMessage errorMessage = objectMapper.readValue(record.value(), ErrorMessage.class);
        assertNotNull(errorMessage);

        ErrorData errorData = errorMessage.getData();
        assertEquals(ErrorType.NOT_IMPLEMENTED, errorData.getErrorType());
    }

    @Test
    public void installFlowTopologyEngineSpeakerBoltTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> ofsRecord;
        ConsumerRecord<String, String> record;

        createFlow(flowId);
        assertNotNull(nbConsumer.pollMessage());
        assertNotNull(teRequestConsumer.pollMessage());

        statusFlow(flowId);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        InfoMessage infoMessage = objectMapper.readValue(record.value(), InfoMessage.class);
        assertNotNull(infoMessage);

        FlowStatusResponse infoData = (FlowStatusResponse) infoMessage.getData();
        assertNotNull(infoData);

        FlowIdStatusPayload flowNbPayload = infoData.getPayload();
        assertNotNull(flowNbPayload);
        assertEquals(flowId, flowNbPayload.getId());
        assertEquals(FlowStatusType.ALLOCATED, flowNbPayload.getStatus());

        InstallOneSwitchFlow data = baseInstallFlowCommand(flowId);

        ofsRecord = ofsConsumer.pollMessage();
        assertNotNull(ofsRecord);
        assertNotNull(ofsRecord.value());

        CommandMessage response = objectMapper.readValue(ofsRecord.value(), CommandMessage.class);
        assertNotNull(response);

        InstallOneSwitchFlow responseData = (InstallOneSwitchFlow) response.getData();
        Long transactionId = responseData.getTransactionId();
        responseData.setTransactionId(0L);
        assertEquals(data, responseData);
        responseData.setTransactionId(transactionId);

        statusFlow(flowId);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        infoMessage = objectMapper.readValue(record.value(), InfoMessage.class);
        assertNotNull(infoMessage);

        infoData = (FlowStatusResponse) infoMessage.getData();
        assertNotNull(infoData);

        flowNbPayload = infoData.getPayload();
        assertNotNull(flowNbPayload);
        assertEquals(flowId, flowNbPayload.getId());
        assertEquals(FlowStatusType.IN_PROGRESS, flowNbPayload.getStatus());

        response.setDestination(Destination.WFM_TRANSACTION);
        baseInstallRuleCommand(response);

        statusFlow(flowId);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        infoMessage = objectMapper.readValue(record.value(), InfoMessage.class);
        assertNotNull(infoMessage);

        infoData = (FlowStatusResponse) infoMessage.getData();
        assertNotNull(infoData);

        flowNbPayload = infoData.getPayload();
        assertNotNull(flowNbPayload);
        assertEquals(flowId, flowNbPayload.getId());
        assertEquals(FlowStatusType.UP, flowNbPayload.getStatus());
    }

    @Test
    public void removeFlowTopologyEngineSpeakerBoltTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> ofsRecord;
        ConsumerRecord<String, String> record;

        createFlow(flowId);
        nbConsumer.pollMessage();
        teRequestConsumer.pollMessage();

        statusFlow(flowId);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        InfoMessage infoMessage = objectMapper.readValue(record.value(), InfoMessage.class);
        assertNotNull(infoMessage);

        FlowStatusResponse infoData = (FlowStatusResponse) infoMessage.getData();
        assertNotNull(infoData);

        FlowIdStatusPayload flowNbPayload = infoData.getPayload();
        assertNotNull(flowNbPayload);
        assertEquals(flowId, flowNbPayload.getId());
        assertEquals(FlowStatusType.ALLOCATED, flowNbPayload.getStatus());

        RemoveFlow data = removeFlowCommand(flowId);

        ofsRecord = ofsConsumer.pollMessage();
        assertNotNull(ofsRecord);
        assertNotNull(ofsRecord.value());

        CommandMessage response = objectMapper.readValue(ofsRecord.value(), CommandMessage.class);
        assertNotNull(response);

        RemoveFlow responseData = (RemoveFlow) response.getData();
        Long transactionId = responseData.getTransactionId();
        responseData.setTransactionId(0L);
        assertEquals(data, responseData);
        responseData.setTransactionId(transactionId);

        statusFlow(flowId);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        infoMessage = objectMapper.readValue(record.value(), InfoMessage.class);
        assertNotNull(infoMessage);

        infoData = (FlowStatusResponse) infoMessage.getData();
        assertNotNull(infoData);

        flowNbPayload = infoData.getPayload();
        assertNotNull(flowNbPayload);
        assertEquals(flowId, flowNbPayload.getId());
        assertEquals(FlowStatusType.IN_PROGRESS, flowNbPayload.getStatus());

        response.setDestination(Destination.WFM_TRANSACTION);
        removeRuleCommand(response);

        statusFlow(flowId);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        infoMessage = objectMapper.readValue(record.value(), InfoMessage.class);
        assertNotNull(infoMessage);

        infoData = (FlowStatusResponse) infoMessage.getData();
        assertNotNull(infoData);

        flowNbPayload = infoData.getPayload();
        assertNotNull(flowNbPayload);
        assertEquals(flowId, flowNbPayload.getId());
        assertEquals(FlowStatusType.UP, flowNbPayload.getStatus());
    }

    @Test
    public void getPathTopologyEngineBoltTest() throws Exception {
        ConsumerRecord<String, String> nbRecord;
        String flowId = UUID.randomUUID().toString();
        FlowPathPayload payload = pathFlowCommand(flowId);

        nbRecord = nbConsumer.pollMessage();
        assertNotNull(nbRecord);
        assertNotNull(nbRecord.value());

        InfoMessage response = objectMapper.readValue(nbRecord.value(), InfoMessage.class);
        assertNotNull(response);

        FlowPathResponse responseData = (FlowPathResponse) response.getData();
        assertNotNull(responseData);
        assertEquals(payload, responseData.getPayload());
    }

    @Test
    public void getFlowTopologyEngineBoltTest() throws Exception {
        ConsumerRecord<String, String> nbRecord;
        String flowId = UUID.randomUUID().toString();
        FlowPayload payload = getFlowCommand(flowId);

        nbRecord = nbConsumer.pollMessage();
        assertNotNull(nbRecord);
        assertNotNull(nbRecord.value());

        InfoMessage response = objectMapper.readValue(nbRecord.value(), InfoMessage.class);
        assertNotNull(response);

        FlowResponse responseData = (FlowResponse) response.getData();
        assertNotNull(responseData);
        assertEquals(payload, responseData.getPayload());
    }

    @Test
    public void dumpFlowsTopologyEngineBoltTest() throws Exception {
        ConsumerRecord<String, String> nbRecord;
        String flowId = UUID.randomUUID().toString();
        FlowsPayload payload = dumpFlowCommand(flowId);

        nbRecord = nbConsumer.pollMessage();
        assertNotNull(nbRecord);
        assertNotNull(nbRecord.value());

        InfoMessage response = objectMapper.readValue(nbRecord.value(), InfoMessage.class);
        assertNotNull(response);

        FlowsResponse responseData = (FlowsResponse) response.getData();
        assertNotNull(responseData);
        assertEquals(payload, responseData.getPayload());
    }

    @Test
    public void errorMessageStatusBoltTopologyEngineBoltTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> recordUp;
        ConsumerRecord<String, String> recordDown;
        createFlow(flowId);
        nbConsumer.pollMessage();
        teRequestConsumer.pollMessage();
        statusFlow(flowId);

        recordUp = nbConsumer.pollMessage();
        assertNotNull(recordUp);
        assertNotNull(recordUp.value());

        InfoMessage infoMessageUp = objectMapper.readValue(recordUp.value(), InfoMessage.class);
        assertNotNull(infoMessageUp);

        FlowStatusResponse infoDataUp = (FlowStatusResponse) infoMessageUp.getData();
        assertNotNull(infoDataUp);

        FlowIdStatusPayload flowNbPayloadUp = infoDataUp.getPayload();
        assertNotNull(flowNbPayloadUp);
        assertEquals(flowId, flowNbPayloadUp.getId());
        assertEquals(FlowStatusType.ALLOCATED, flowNbPayloadUp.getStatus());

        ErrorMessage errorMessage = errorFlowTopologyEngineCommand(flowId);
        statusFlow(flowId);

        recordDown = nbConsumer.pollMessage();
        assertNotNull(recordDown);
        assertNotNull(recordDown.value());

        InfoMessage infoMessageDown = objectMapper.readValue(recordDown.value(), InfoMessage.class);
        assertNotNull(infoMessageDown);

        FlowStatusResponse infoDataDown = (FlowStatusResponse) infoMessageDown.getData();
        assertNotNull(infoDataDown);

        FlowIdStatusPayload flowNbPayloadDown = infoDataDown.getPayload();
        assertNotNull(flowNbPayloadDown);
        assertEquals(flowId, flowNbPayloadDown.getId());
        assertEquals(FlowStatusType.DOWN, flowNbPayloadDown.getStatus());
    }

    @Test
    public void errorMessageStatusBoltSpeakerBoltTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> recordUp;
        ConsumerRecord<String, String> recordDown;
        createFlow(flowId);
        nbConsumer.pollMessage();
        teRequestConsumer.pollMessage();
        statusFlow(flowId);

        recordUp = nbConsumer.pollMessage();
        assertNotNull(recordUp);
        assertNotNull(recordUp.value());

        InfoMessage infoMessageUp = objectMapper.readValue(recordUp.value(), InfoMessage.class);
        assertNotNull(infoMessageUp);

        FlowStatusResponse infoDataUp = (FlowStatusResponse) infoMessageUp.getData();
        assertNotNull(infoDataUp);

        FlowIdStatusPayload flowNbPayloadUp = infoDataUp.getPayload();
        assertNotNull(flowNbPayloadUp);
        assertEquals(flowId, flowNbPayloadUp.getId());
        assertEquals(FlowStatusType.ALLOCATED, flowNbPayloadUp.getStatus());

        ErrorMessage errorMessage = errorFlowSpeakerCommand(flowId);
        statusFlow(flowId);

        recordDown = nbConsumer.pollMessage();
        assertNotNull(recordDown);
        assertNotNull(recordDown.value());

        InfoMessage infoMessageDown = objectMapper.readValue(recordDown.value(), InfoMessage.class);
        assertNotNull(infoMessageDown);

        FlowStatusResponse infoDataDown = (FlowStatusResponse) infoMessageDown.getData();
        assertNotNull(infoDataDown);

        FlowIdStatusPayload flowNbPayloadDown = infoDataDown.getPayload();
        assertNotNull(flowNbPayloadDown);
        assertEquals(flowId, flowNbPayloadDown.getId());
        assertEquals(FlowStatusType.DOWN, flowNbPayloadDown.getStatus());
    }

    private void sendNorthboundMessage(final CommandMessage message) throws IOException {
        String request = objectMapper.writeValueAsString(message);
        kProducer.pushMessage(Topic.TEST.getId(), request);
    }

    private FlowIdStatusPayload deleteFlow(final String flowId) throws IOException {
        System.out.println("NORTHBOUND: Delete flow");
        FlowIdStatusPayload payload = new FlowIdStatusPayload(flowId);
        FlowDeleteRequest commandData = new FlowDeleteRequest(payload);
        CommandMessage message = new CommandMessage(commandData, 0, "delete-flow", Destination.WFM);
        sendNorthboundMessage(message);
        return payload;
    }

    private FlowPayload createFlow(final String flowId) throws IOException {
        System.out.println("NORTHBOUND: Create flow");
        FlowPayload flowPayload = new FlowPayload(flowId, flowEndpointPayload, flowEndpointPayload, 10000L, "", "");
        FlowCreateRequest commandData = new FlowCreateRequest(flowPayload);
        CommandMessage message = new CommandMessage(commandData, 0, "create-flow", Destination.WFM);
        sendNorthboundMessage(message);
        return flowPayload;
    }

    private FlowPayload updateFlow(final String flowId) throws IOException {
        System.out.println("NORTHBOUND: Update flow");
        FlowPayload payload = new FlowPayload(flowId, flowEndpointPayload, flowEndpointPayload, 10000L, "", "");
        FlowUpdateRequest commandData = new FlowUpdateRequest(payload);
        CommandMessage message = new CommandMessage(commandData, 0, "update-flow", Destination.WFM);
        sendNorthboundMessage(message);
        return payload;
    }

    private FlowIdStatusPayload statusFlow(final String flowId) throws IOException {
        System.out.println("NORTHBOUND: Status flow");
        FlowIdStatusPayload payload = new FlowIdStatusPayload(flowId);
        FlowStatusRequest commandData = new FlowStatusRequest(payload);
        CommandMessage message = new CommandMessage(commandData, 0, "status-flow", Destination.WFM);
        sendNorthboundMessage(message);
        return payload;
    }

    private FlowIdStatusPayload pathFlow(final String flowId) throws IOException {
        System.out.println("NORTHBOUND: Path flow");
        FlowIdStatusPayload payload = new FlowIdStatusPayload(flowId);
        FlowPathRequest commandData = new FlowPathRequest(payload);
        CommandMessage message = new CommandMessage(commandData, 0, "path-flow", Destination.WFM);
        sendNorthboundMessage(message);
        return payload;
    }

    private FlowIdStatusPayload getFlow(final String flowId) throws IOException {
        System.out.println("NORTHBOUND: Get flow");
        FlowIdStatusPayload payload = new FlowIdStatusPayload(flowId);
        FlowGetRequest commandData = new FlowGetRequest(payload);
        CommandMessage message = new CommandMessage(commandData, 0, "get-flow", Destination.WFM);
        sendNorthboundMessage(message);
        return payload;
    }

    private FlowIdStatusPayload dumpFlows(final String flowId) throws IOException {
        System.out.println("NORTHBOUND: Get flows");
        FlowIdStatusPayload payload = new FlowIdStatusPayload(flowId);
        FlowsGetRequest commandData = new FlowsGetRequest(payload);
        CommandMessage message = new CommandMessage(commandData, 0, "get-flows", Destination.WFM);
        sendNorthboundMessage(message);
        return payload;
    }

    private void sendTopologyEngineMessage(final Message message) throws IOException {
        String request = objectMapper.writeValueAsString(message);
        kProducer.pushMessage(Topic.TEST.getId(), request);
    }

    private InstallOneSwitchFlow baseInstallFlowCommand(final String flowId) throws IOException {
        System.out.println("TOPOLOGY: Install flow");
        InstallOneSwitchFlow commandData = new InstallOneSwitchFlow(0L, flowId,
                COOKIE, "switch-id", 1, 2, 0, 0, OutputVlanType.NONE, 10000L, 0L, 0L);
        CommandMessage commandMessage = new CommandMessage(commandData, 0, "install-flow", Destination.WFM);
        sendTopologyEngineMessage(commandMessage);
        return commandData;
    }

    private RemoveFlow removeFlowCommand(final String flowId) throws IOException {
        System.out.println("TOPOLOGY: Remove flow");
        RemoveFlow commandData = new RemoveFlow(0L, flowId, COOKIE, "switch-id", 0L);
        CommandMessage commandMessage = new CommandMessage(commandData, 0, "remove-flow", Destination.WFM);
        sendTopologyEngineMessage(commandMessage);
        return commandData;
    }

    private FlowPayload getFlowCommand(final String flowId) throws IOException {
        System.out.println("TOPOLOGY: Get flow");
        FlowPayload payload = new FlowPayload(flowId, flowEndpointPayload, flowEndpointPayload, 10000L, "", "");
        FlowResponse infoData = new FlowResponse(payload);
        InfoMessage infoMessage = new InfoMessage(infoData, 0, "get-flow", Destination.WFM);
        sendTopologyEngineMessage(infoMessage);
        return payload;
    }

    private FlowsPayload dumpFlowCommand(final String flowId) throws IOException {
        System.out.println("TOPOLOGY: Get flows");
        FlowPayload flow = new FlowPayload(flowId, flowEndpointPayload, flowEndpointPayload, 10000L, "", "");
        FlowsPayload payload = new FlowsPayload(Collections.singletonList(flow));
        FlowsResponse infoData = new FlowsResponse(payload);
        InfoMessage infoMessage = new InfoMessage(infoData, 0, "dump-flows", Destination.WFM);
        sendTopologyEngineMessage(infoMessage);
        return payload;
    }

    private FlowPathPayload pathFlowCommand(final String flowId) throws IOException {
        System.out.println("TOPOLOGY: Path flow");
        FlowPathPayload payload = new FlowPathPayload(flowId, Collections.singletonList("switch-id"));
        FlowPathResponse infoData = new FlowPathResponse(payload);
        InfoMessage infoMessage = new InfoMessage(infoData, 0, "path-flow", Destination.WFM);
        sendTopologyEngineMessage(infoMessage);
        return payload;
    }

    private ErrorMessage errorFlowTopologyEngineCommand(final String flowId) throws IOException {
        System.out.println("TOPOLOGY: Error flow");
        ErrorData errorData = new ErrorData(0, null, ErrorType.REQUEST_INVALID, flowId);
        ErrorMessage errorMessage = new ErrorMessage(errorData, 0, "error-flow", Destination.WFM);
        sendTopologyEngineMessage(errorMessage);
        return errorMessage;
    }

    private void sendSpeakerMessage(final Message message) throws IOException {
        String request = objectMapper.writeValueAsString(message);
        kProducer.pushMessage(Topic.TEST.getId(), request);
    }

    private Message baseInstallRuleCommand(final Message message) throws IOException {
        System.out.println("TOPOLOGY: Install rule");
        sendSpeakerMessage(message);
        return message;
    }

    private Message removeRuleCommand(final Message message) throws IOException {
        System.out.println("TOPOLOGY: Remove rule");
        sendSpeakerMessage(message);
        return message;
    }

    private ErrorMessage errorFlowSpeakerCommand(final String flowId) throws IOException {
        System.out.println("TOPOLOGY: Error rule");
        ErrorData errorData = new ErrorData(0, null, ErrorType.REQUEST_INVALID, flowId);
        ErrorMessage errorMessage = new ErrorMessage(errorData, 0, "error-flow", Destination.WFM);
        sendSpeakerMessage(errorMessage);
        return errorMessage;
    }
}
