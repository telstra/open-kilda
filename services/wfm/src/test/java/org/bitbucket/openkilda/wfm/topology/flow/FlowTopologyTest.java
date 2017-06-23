package org.bitbucket.openkilda.wfm.topology.flow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.Topic;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.command.flow.FlowCreateRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowDeleteRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowGetRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowPathRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowStatusRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowUpdateRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowsGetRequest;
import org.bitbucket.openkilda.messaging.command.flow.InstallOneSwitchFlow;
import org.bitbucket.openkilda.messaging.command.flow.RemoveFlow;
import org.bitbucket.openkilda.messaging.error.ErrorData;
import org.bitbucket.openkilda.messaging.error.ErrorMessage;
import org.bitbucket.openkilda.messaging.error.ErrorType;
import org.bitbucket.openkilda.messaging.info.InfoMessage;
import org.bitbucket.openkilda.messaging.info.flow.FlowPathResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowStatusResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowsResponse;
import org.bitbucket.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPathPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowStatusType;
import org.bitbucket.openkilda.messaging.payload.flow.FlowsPayload;
import org.bitbucket.openkilda.messaging.payload.flow.OutputVlanType;
import org.bitbucket.openkilda.wfm.AbstractStormTest;
import org.bitbucket.openkilda.wfm.topology.TestKafkaConsumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.storm.Config;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.utils.Utils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

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
        ConsumerRecord<String, String> record;
        String flowId = UUID.randomUUID().toString();

        createFlow(flowId);

        record = teRequestConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        CommandMessage commandMessage = objectMapper.readValue(record.value(), CommandMessage.class);
        FlowCreateRequest commandData = (FlowCreateRequest) commandMessage.getData();
        assertNotNull(commandData);

        flowInfo(flowId);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        InfoMessage infoMessage = objectMapper.readValue(record.value(), InfoMessage.class);
        FlowResponse infoData = (FlowResponse) infoMessage.getData();
        assertNotNull(infoData);
    }

    @Test
    public void createAlreadyExistsFlowCommandBoltTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;

        createFlow(flowId);

        record = teRequestConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

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

        record = teRequestConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        FlowIdStatusPayload payload = deleteFlow(flowId);

        record = teRequestConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        CommandMessage commandMessage = objectMapper.readValue(record.value(), CommandMessage.class);
        FlowDeleteRequest commandData = (FlowDeleteRequest) commandMessage.getData();
        assertNotNull(commandData);

        FlowIdStatusPayload flowTePayload = commandData.getPayload();
        assertEquals(payload, flowTePayload);

        flowInfo(flowId);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        InfoMessage infoMessage = objectMapper.readValue(record.value(), InfoMessage.class);
        FlowResponse infoData = (FlowResponse) infoMessage.getData();
        assertNotNull(infoData);
    }

    @Test
    public void deleteUnknownFlowCommandBoltTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;

        deleteFlow(flowId);

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

        createFlow(flowId);

        record = teRequestConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        updateFlow(flowId);

        record = teRequestConsumer.pollMessage();
        assertNotNull(record);

        CommandMessage commandMessage = objectMapper.readValue(record.value(), CommandMessage.class);
        FlowUpdateRequest commandData = (FlowUpdateRequest) commandMessage.getData();
        assertNotNull(commandData);

        FlowPayload flowTePayload = commandData.getPayload();

        flowInfo(flowId);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        InfoMessage infoMessage = objectMapper.readValue(record.value(), InfoMessage.class);
        FlowResponse infoData = (FlowResponse) infoMessage.getData();
        assertNotNull(infoData);

        FlowPayload flowNbPayload = infoData.getPayload();
        assertNull(flowNbPayload.getCookie());

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
        ConsumerRecord<String, String> record;

        createFlow(flowId);

        record = teRequestConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        FlowIdStatusPayload payload = pathFlow(flowId);

        record = teRequestConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        CommandMessage commandMessage = objectMapper.readValue(record.value(), CommandMessage.class);
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
        ConsumerRecord<String, String> record;

        createFlow(flowId);

        record = teRequestConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        FlowIdStatusPayload payload = getFlow(flowId);

        record = teRequestConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        CommandMessage commandMessage = objectMapper.readValue(record.value(), CommandMessage.class);
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

        FlowIdStatusPayload payload = dumpFlows(flowId);

        record = teRequestConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        CommandMessage commandMessage = objectMapper.readValue(record.value(), CommandMessage.class);
        FlowsGetRequest commandData = (FlowsGetRequest) commandMessage.getData();
        assertNotNull(commandData);

        FlowIdStatusPayload flowTePayload = commandData.getPayload();
        assertEquals(payload, flowTePayload);
    }

    @Test
    public void installFlowTopologyEngineSpeakerBoltTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;

        createFlow(flowId);

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

        InstallOneSwitchFlow data = baseInstallFlowCommand(flowId);

        record = ofsConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        CommandMessage response = objectMapper.readValue(record.value(), CommandMessage.class);
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
    public void errorFlowCreateMessageStatusBoltTopologyEngineBoltTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;

        createFlow(flowId);

        record = teRequestConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        statusFlow(flowId);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        InfoMessage infoMessageUp = objectMapper.readValue(record.value(), InfoMessage.class);
        assertNotNull(infoMessageUp);

        FlowStatusResponse infoDataUp = (FlowStatusResponse) infoMessageUp.getData();
        assertNotNull(infoDataUp);

        FlowIdStatusPayload flowNbPayloadUp = infoDataUp.getPayload();
        assertNotNull(flowNbPayloadUp);
        assertEquals(flowId, flowNbPayloadUp.getId());
        assertEquals(FlowStatusType.ALLOCATED, flowNbPayloadUp.getStatus());

        errorFlowTopologyEngineCommand(flowId, ErrorType.CREATION_FAILURE);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        ErrorMessage errorMessage = objectMapper.readValue(record.value(), ErrorMessage.class);
        assertNotNull(errorMessage);

        ErrorData errorData = errorMessage.getData();
        assertEquals(ErrorType.CREATION_FAILURE, errorData.getErrorType());

        statusFlow(flowId);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        errorMessage = objectMapper.readValue(record.value(), ErrorMessage.class);
        assertNotNull(errorMessage);

        errorData = errorMessage.getData();
        assertEquals(ErrorType.NOT_FOUND, errorData.getErrorType());
    }

    @Test
    public void errorFlowUpdateMessageStatusBoltTopologyEngineBoltTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;

        createFlow(flowId);

        record = teRequestConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        updateFlow(flowId);

        record = teRequestConsumer.pollMessage();
        assertNotNull(record);

        CommandMessage commandMessage = objectMapper.readValue(record.value(), CommandMessage.class);
        FlowUpdateRequest commandData = (FlowUpdateRequest) commandMessage.getData();
        assertNotNull(commandData);

        statusFlow(flowId);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        InfoMessage infoMessageUp = objectMapper.readValue(record.value(), InfoMessage.class);
        assertNotNull(infoMessageUp);

        FlowStatusResponse infoDataUp = (FlowStatusResponse) infoMessageUp.getData();
        assertNotNull(infoDataUp);

        FlowIdStatusPayload flowNbPayloadUp = infoDataUp.getPayload();
        assertNotNull(flowNbPayloadUp);
        assertEquals(flowId, flowNbPayloadUp.getId());
        assertEquals(FlowStatusType.ALLOCATED, flowNbPayloadUp.getStatus());

        errorFlowTopologyEngineCommand(flowId, ErrorType.UPDATE_FAILURE);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        ErrorMessage errorMessage = objectMapper.readValue(record.value(), ErrorMessage.class);
        assertNotNull(errorMessage);

        ErrorData errorData = errorMessage.getData();
        assertEquals(ErrorType.UPDATE_FAILURE, errorData.getErrorType());

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
        assertEquals(FlowStatusType.DOWN, flowNbPayload.getStatus());
    }

    @Test
    public void errorFlowDeleteMessageStatusBoltTopologyEngineBoltTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;

        createFlow(flowId);

        record = teRequestConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        deleteFlow(flowId);

        record = teRequestConsumer.pollMessage();
        assertNotNull(record);

        CommandMessage commandMessage = objectMapper.readValue(record.value(), CommandMessage.class);
        FlowDeleteRequest commandData = (FlowDeleteRequest) commandMessage.getData();
        assertNotNull(commandData);

        statusFlow(flowId);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        ErrorMessage errorMessage = objectMapper.readValue(record.value(), ErrorMessage.class);
        assertNotNull(errorMessage);

        ErrorData errorData = errorMessage.getData();
        assertEquals(ErrorType.NOT_FOUND, errorData.getErrorType());

        errorFlowTopologyEngineCommand(flowId, ErrorType.DELETION_FAILURE);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        errorMessage = objectMapper.readValue(record.value(), ErrorMessage.class);
        assertNotNull(errorMessage);

        errorData = errorMessage.getData();
        assertEquals(ErrorType.DELETION_FAILURE, errorData.getErrorType());

        statusFlow(flowId);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        errorMessage = objectMapper.readValue(record.value(), ErrorMessage.class);
        assertNotNull(errorMessage);

        errorData = errorMessage.getData();
        assertEquals(ErrorType.NOT_FOUND, errorData.getErrorType());
    }

    @Test
    public void errorMessageStatusBoltSpeakerBoltTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;

        createFlow(flowId);

        record = teRequestConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        statusFlow(flowId);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        InfoMessage infoMessageUp = objectMapper.readValue(record.value(), InfoMessage.class);
        assertNotNull(infoMessageUp);

        FlowStatusResponse infoDataUp = (FlowStatusResponse) infoMessageUp.getData();
        assertNotNull(infoDataUp);

        FlowIdStatusPayload flowNbPayloadUp = infoDataUp.getPayload();
        assertNotNull(flowNbPayloadUp);
        assertEquals(flowId, flowNbPayloadUp.getId());
        assertEquals(FlowStatusType.ALLOCATED, flowNbPayloadUp.getStatus());

        errorFlowSpeakerCommand(flowId);

        statusFlow(flowId);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        InfoMessage infoMessageDown = objectMapper.readValue(record.value(), InfoMessage.class);
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
                COOKIE, "switch-id", 1, 2, 0, 0, OutputVlanType.NONE, 10000L, 0L);
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

    private FlowPayload flowInfo(final String flowId) throws IOException {
        System.out.println("TOPOLOGY: Flow response");
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

    private ErrorMessage errorFlowTopologyEngineCommand(final String flowId, final ErrorType type) throws IOException {
        System.out.println("TOPOLOGY: Error flow");
        ErrorData errorData = new ErrorData(type, "Could not operate with flow", flowId);
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
        ErrorData errorData = new ErrorData(ErrorType.REQUEST_INVALID, "Could not operate with flow", flowId);
        ErrorMessage errorMessage = new ErrorMessage(errorData, 0, "error-flow", Destination.WFM_TRANSACTION);
        sendSpeakerMessage(errorMessage);
        return errorMessage;
    }
}
