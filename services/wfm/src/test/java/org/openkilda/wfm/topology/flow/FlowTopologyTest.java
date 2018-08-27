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

package org.openkilda.wfm.topology.flow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.FlowCacheSyncRequest;
import org.openkilda.messaging.command.flow.FlowCreateRequest;
import org.openkilda.messaging.command.flow.FlowDeleteRequest;
import org.openkilda.messaging.command.flow.FlowReadRequest;
import org.openkilda.messaging.command.flow.FlowUpdateRequest;
import org.openkilda.messaging.command.flow.FlowsDumpRequest;
import org.openkilda.messaging.command.flow.InstallOneSwitchFlow;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.command.flow.SynchronizeCacheAction;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.messaging.ctrl.CtrlRequest;
import org.openkilda.messaging.ctrl.CtrlResponse;
import org.openkilda.messaging.ctrl.DumpStateResponseData;
import org.openkilda.messaging.ctrl.RequestData;
import org.openkilda.messaging.ctrl.ResponseData;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.flow.FlowCacheSyncResponse;
import org.openkilda.messaging.info.flow.FlowInfoData;
import org.openkilda.messaging.info.flow.FlowOperation;
import org.openkilda.messaging.info.flow.FlowReadResponse;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.messaging.info.flow.FlowsResponse;
import org.openkilda.messaging.model.BidirectionalFlow;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.messaging.payload.flow.FlowCacheSyncResults;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.messaging.payload.flow.OutputVlanType;
import org.openkilda.wfm.AbstractStormTest;
import org.openkilda.wfm.topology.TestKafkaConsumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.storm.Config;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.utils.Utils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class FlowTopologyTest extends AbstractStormTest {

    private static final long COOKIE = 0x1FFFFFFFFL;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static TestKafkaConsumer nbConsumer;
    private static TestKafkaConsumer ofsConsumer;
    private static TestKafkaConsumer cacheConsumer;
    private static TestKafkaConsumer teResponseConsumer;
    private static TestKafkaConsumer ctrlConsumer;
    private static FlowTopology flowTopology;
    private static FlowTopologyConfig topologyConfig;

    @BeforeClass
    public static void setupOnce() throws Exception {
        AbstractStormTest.setupOnce();

        flowTopology = new FlowTopology(makeLaunchEnvironment(), new MockedPathComputerAuth());
        topologyConfig = flowTopology.getConfig();

        StormTopology stormTopology = flowTopology.createTopology();
        Config config = stormConfig();
        cluster.submitTopology(FlowTopologyTest.class.getSimpleName(), config, stormTopology);

        nbConsumer = new TestKafkaConsumer(
                topologyConfig.getKafkaNorthboundTopic(), Destination.NORTHBOUND,
                kafkaProperties(UUID.nameUUIDFromBytes(Destination.NORTHBOUND.toString().getBytes()).toString()));
        nbConsumer.start();

        ofsConsumer = new TestKafkaConsumer(topologyConfig.getKafkaSpeakerTopic(),
                Destination.CONTROLLER,
                kafkaProperties(UUID.nameUUIDFromBytes(Destination.CONTROLLER.toString().getBytes()).toString()));
        ofsConsumer.start();

        cacheConsumer = new TestKafkaConsumer(topologyConfig.getKafkaTopoCacheTopic(), null,
                kafkaProperties(UUID.nameUUIDFromBytes(Destination.TOPOLOGY_ENGINE.toString().getBytes()).toString()));
        cacheConsumer.start();

        //teResponseConsumer = new TestKafkaConsumer(topologyConfig.getKafkaTopoEngTopic(),
        teResponseConsumer = new TestKafkaConsumer(topologyConfig.getKafkaFlowTopic(),
                Destination.WFM,
                kafkaProperties(UUID.nameUUIDFromBytes(Destination.WFM.toString().getBytes()).toString()));
        teResponseConsumer.start();

        ctrlConsumer = new TestKafkaConsumer(flowTopology.getConfig().getKafkaCtrlTopic(), Destination.CTRL_CLIENT,
                kafkaProperties(UUID.nameUUIDFromBytes(Destination.CTRL_CLIENT.toString().getBytes()).toString()));
        ctrlConsumer.start();

        Utils.sleep(10000);
    }

    @AfterClass
    public static void teardownOnce() throws Exception {
        nbConsumer.wakeup();
        nbConsumer.join();
        ofsConsumer.wakeup();
        ofsConsumer.join();
        cacheConsumer.wakeup();
        cacheConsumer.join();
        teResponseConsumer.wakeup();
        teResponseConsumer.join();

        AbstractStormTest.teardownOnce();
    }

    @Before
    public void setup() throws Exception {
        nbConsumer.clear();
        ofsConsumer.clear();
        cacheConsumer.clear();
        teResponseConsumer.clear();
    }

    @After
    public void teardown() throws Exception {
        nbConsumer.clear();
        ofsConsumer.clear();
        cacheConsumer.clear();
        teResponseConsumer.clear();

        // Clean the CrudBolt's state.
        sendClearState();
    }

    @Test
    public void createFlowCommandBoltTest() throws Exception {
        ConsumerRecord<String, String> record;
        String flowId = UUID.randomUUID().toString();

        createFlow(flowId);

        record = cacheConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        InfoMessage message = objectMapper.readValue(record.value(), InfoMessage.class);
        ImmutablePair<Flow, Flow> flow = getFlowPayload(message);
        assertNotNull(flow);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        InfoMessage infoMessage = objectMapper.readValue(record.value(), InfoMessage.class);
        FlowResponse response = (FlowResponse) infoMessage.getData();
        assertNotNull(response);
    }

    @Test
    public void createAlreadyExistsFlowCommandBoltTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;

        createFlow(flowId);

        record = cacheConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());
        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        createFlow(flowId);

        record = nbConsumer.pollMessage();
        checkErrorResponseType(record, ErrorType.ALREADY_EXISTS);
    }

    @Test
    public void shouldFailOnCreatingConflictingFlow() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;

        createFlow(flowId);

        record = cacheConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());
        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        createFlow(flowId + "_alt");

        record = nbConsumer.pollMessage();
        checkErrorResponseType(record, ErrorType.ALREADY_EXISTS);
    }

    @Test
    public void deleteFlowCommandBoltTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;

        createFlow(flowId);

        record = cacheConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());
        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        Flow payload = deleteFlow(flowId);

        record = cacheConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        InfoMessage message = objectMapper.readValue(record.value(), InfoMessage.class);
        assertNotNull(message);
        ImmutablePair<Flow, Flow> flow = getFlowPayload(message);
        assertNotNull(flow);

        Flow flowTePayload = flow.getLeft();
        assertEquals(payload.getFlowId(), flowTePayload.getFlowId());

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        System.out.println("record = " + record);
        InfoMessage infoMessage = objectMapper.readValue(record.value(), InfoMessage.class);
        FlowResponse response = (FlowResponse) infoMessage.getData();
        assertNotNull(response);
    }

    @Test
    public void deleteUnknownFlowCommandBoltTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;

        deleteFlow(flowId);

        record = nbConsumer.pollMessage();
        checkErrorResponseType(record, ErrorType.NOT_FOUND);
    }

    @Test
    public void updateFlowCommandBoltTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;

        createFlow(flowId);

        record = cacheConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());
        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        updateFlow(flowId);

        record = cacheConsumer.pollMessage();
        assertNotNull(record);

        InfoMessage message = objectMapper.readValue(record.value(), InfoMessage.class);
        assertNotNull(message);
        ImmutablePair<Flow, Flow> flow = getFlowPayload(message);
        assertNotNull(flow);

        Flow flowTePayload = flow.getLeft();

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        InfoMessage infoMessage = objectMapper.readValue(record.value(), InfoMessage.class);
        FlowResponse payload = (FlowResponse) infoMessage.getData();
        assertNotNull(payload);

        Flow flowNbPayload = payload.getPayload();
        assertEquals(flowNbPayload, flowTePayload);
    }

    @Test
    public void updateUnknownFlowCommandBoltTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;

        updateFlow(flowId);

        record = nbConsumer.pollMessage();
        checkErrorResponseType(record, ErrorType.NOT_FOUND);
    }

    @Test
    public void statusFlowTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;

        createFlow(flowId);

        record = cacheConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());
        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        statusFlow(flowId);

        record = nbConsumer.pollMessage();
        checkFlowReadStatus(record, flowId, FlowState.ALLOCATED);
    }

    @Test
    public void statusUnknownFlowTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;

        statusFlow(flowId);

        record = nbConsumer.pollMessage();
        checkErrorResponseType(record, ErrorType.NOT_FOUND);
    }

    @Test
    public void pathFlowTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;

        createFlow(flowId);

        record = cacheConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());
        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        PathInfoData emptyPath = pathFlow(flowId);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        InfoMessage infoMessage = objectMapper.readValue(record.value(), InfoMessage.class);
        FlowReadResponse infoData = (FlowReadResponse) infoMessage.getData();
        assertNotNull(infoData);

        BidirectionalFlow flowPayload = infoData.getPayload();
        assertEquals(emptyPath, flowPayload.getForward().getFlowPath());
        assertEquals(emptyPath, flowPayload.getReverse().getFlowPath());
    }

    @Test
    public void pathUnknownFlowTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;

        pathFlow(flowId);

        record = nbConsumer.pollMessage();
        checkErrorResponseType(record, ErrorType.NOT_FOUND);
    }

    @Test
    public void getFlowTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;

        Flow flow = createFlow(flowId);
        flow.setCookie(1);
        flow.setFlowPath(new PathInfoData(0L, Collections.emptyList()));
        flow.setMeterId(1);
        flow.setTransitVlan(2);
        flow.setState(FlowState.ALLOCATED);

        record = cacheConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());
        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        getFlow(flowId);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        InfoMessage infoMessage = objectMapper.readValue(record.value(), InfoMessage.class);
        FlowReadResponse infoData = (FlowReadResponse) infoMessage.getData();
        assertNotNull(infoData);

        Flow flowTePayload = infoData.getPayload().getForward();
        assertEquals(flow, flowTePayload);
    }

    @Test
    public void getUnknownFlowTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;

        getFlow(flowId);

        record = nbConsumer.pollMessage();
        checkErrorResponseType(record, ErrorType.NOT_FOUND);
    }

    @Test
    public void dumpFlowsTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;

        createFlow(flowId);

        record = cacheConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());
        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        dumpFlows();

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        InfoMessage infoMessage = objectMapper.readValue(record.value(), InfoMessage.class);
        FlowReadResponse infoData = (FlowReadResponse) infoMessage.getData();
        assertNotNull(infoData);
        assertNotNull(infoData.getPayload());
        assertEquals(flowId, infoData.getPayload().getFlowId());
    }

    @Test
    public void dumpFlowsWhenThereIsNoFlowsCreated() throws Exception {
        dumpFlows();

        ConsumerRecord<String, String> record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        InfoMessage infoMessage = objectMapper.readValue(record.value(), InfoMessage.class);
        assertNull(infoMessage.getData());
    }

    @Test
    public void installFlowTopologyEngineSpeakerBoltTest() throws Exception {
        /*
         * This test will verify the state transitions of a flow, through the status mechanism.
         * It achieves this by doing the following:
         *      - CreateFlow .. clear both cache and northbound consumers
         *      - GetStatus .. confirm STATE = FlowState.ALLOCATED
         *      - baseInstallFlowCommand .. read speaker .. validate data/responsedata
         *      - GetStatus .. confirm STATE = FlowState.IN_PROGRESS
         *      - baseInstallRuleCommand ..
         *      - GetStatus .. confirm STATE = FlowState.UP
         */
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;

        createFlow(flowId);

        record = cacheConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());
        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        statusFlow(flowId);

        record = nbConsumer.pollMessage();
        checkFlowReadStatus(record, flowId, FlowState.ALLOCATED);

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
        checkFlowReadStatus(record, flowId, FlowState.IN_PROGRESS);

        response.setDestination(Destination.WFM_TRANSACTION);

        baseInstallRuleCommand(response);

        statusFlow(flowId);

        record = nbConsumer.pollMessage();
        checkFlowReadStatus(record, flowId, FlowState.UP);
    }

    @Test
    public void removeFlowTopologyEngineSpeakerBoltTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> ofsRecord;
        ConsumerRecord<String, String> record;

        createFlow(flowId);

        record = cacheConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());
        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        statusFlow(flowId);

        record = nbConsumer.pollMessage();
        checkFlowReadStatus(record, flowId, FlowState.ALLOCATED);

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
        checkFlowReadStatus(record, flowId, FlowState.IN_PROGRESS);

        response.setDestination(Destination.WFM_TRANSACTION);

        removeRuleCommand(response);

        statusFlow(flowId);

        record = nbConsumer.pollMessage();
        checkFlowReadStatus(record, flowId, FlowState.UP);
    }

    @Test
    @Ignore
    public void getPathTopologyEngineBoltTest() throws Exception {
        ConsumerRecord<String, String> nbRecord;
        String flowId = UUID.randomUUID().toString();

        PathInfoData payload = pathFlowCommand(flowId);

        nbRecord = nbConsumer.pollMessage();
        assertNotNull(nbRecord);
        assertNotNull(nbRecord.value());

        InfoMessage response = objectMapper.readValue(nbRecord.value(), InfoMessage.class);
        assertNotNull(response);

        FlowReadResponse responseData = (FlowReadResponse) response.getData();
        assertNotNull(responseData);
        assertEquals(payload, responseData.getPayload().getForward().getFlowPath());
        assertEquals(payload, responseData.getPayload().getReverse().getFlowPath());
    }

    @Test
    @Ignore
    public void getFlowTopologyEngineBoltTest() throws Exception {
        ConsumerRecord<String, String> nbRecord;
        String flowId = UUID.randomUUID().toString();

        Flow payload = getFlowCommand(flowId);

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
    @Ignore
    public void dumpFlowsTopologyEngineBoltTest() throws Exception {
        ConsumerRecord<String, String> nbRecord;
        String flowId = UUID.randomUUID().toString();

        List<String> payload = dumpFlowCommand(flowId);

        nbRecord = nbConsumer.pollMessage();
        assertNotNull(nbRecord);
        assertNotNull(nbRecord.value());

        InfoMessage response = objectMapper.readValue(nbRecord.value(), InfoMessage.class);
        assertNotNull(response);

        FlowsResponse responseData = (FlowsResponse) response.getData();
        assertNotNull(responseData);
        assertEquals(payload, responseData.getFlowIds());
    }

    @Test
    public void errorFlowCreateMessageStatusBoltTopologyEngineBoltTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;

        createFlow(flowId);

        record = cacheConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());
        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        statusFlow(flowId);

        record = nbConsumer.pollMessage();
        checkFlowReadStatus(record, flowId, FlowState.ALLOCATED);

        errorFlowTopologyEngineCommand(flowId, ErrorType.CREATION_FAILURE);

        record = nbConsumer.pollMessage();
        checkErrorResponseType(record, ErrorType.CREATION_FAILURE);

        statusFlow(flowId);

        record = nbConsumer.pollMessage();
        checkErrorResponseType(record, ErrorType.NOT_FOUND);
    }

    @Test
    public void errorFlowUpdateMessageStatusBoltTopologyEngineBoltTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;

        createFlow(flowId);

        record = cacheConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());
        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        updateFlow(flowId);

        record = cacheConsumer.pollMessage();
        assertNotNull(record);

        InfoMessage message = objectMapper.readValue(record.value(), InfoMessage.class);
        assertNotNull(message);
        ImmutablePair<Flow, Flow> flow = getFlowPayload(message);
        assertNotNull(flow);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        statusFlow(flowId);

        record = nbConsumer.pollMessage();
        checkFlowReadStatus(record, flowId, FlowState.ALLOCATED);

        errorFlowTopologyEngineCommand(flowId, ErrorType.UPDATE_FAILURE);

        record = nbConsumer.pollMessage();
        checkErrorResponseType(record, ErrorType.UPDATE_FAILURE);

        statusFlow(flowId);

        record = nbConsumer.pollMessage();
        checkFlowReadStatus(record, flowId, FlowState.DOWN);
    }

    @Test
    public void errorFlowDeleteMessageStatusBoltTopologyEngineBoltTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;

        createFlow(flowId);

        record = cacheConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());
        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        deleteFlow(flowId);

        record = cacheConsumer.pollMessage();
        assertNotNull(record);

        InfoMessage message = objectMapper.readValue(record.value(), InfoMessage.class);
        assertNotNull(message);
        ImmutablePair<Flow, Flow> flow = getFlowPayload(message);
        assertNotNull(flow);

        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        statusFlow(flowId);

        record = nbConsumer.pollMessage();
        checkErrorResponseType(record, ErrorType.NOT_FOUND);

        errorFlowTopologyEngineCommand(flowId, ErrorType.DELETION_FAILURE);

        record = nbConsumer.pollMessage();
        checkErrorResponseType(record, ErrorType.DELETION_FAILURE);

        statusFlow(flowId);

        record = nbConsumer.pollMessage();
        checkErrorResponseType(record, ErrorType.NOT_FOUND);
    }

    @Test
    public void errorMessageStatusBoltSpeakerBoltTest() throws Exception {
        String flowId = UUID.randomUUID().toString();
        ConsumerRecord<String, String> record;

        createFlow(flowId);

        record = cacheConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());
        record = nbConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());

        statusFlow(flowId);

        record = nbConsumer.pollMessage();
        checkFlowReadStatus(record, flowId, FlowState.ALLOCATED);

        errorFlowSpeakerCommand(flowId);

        statusFlow(flowId);

        record = nbConsumer.pollMessage();
        checkFlowReadStatus(record, flowId, FlowState.DOWN);
    }

    @Test
    @Ignore("Not reliable during batch run")
    public void ctrlDumpHandler() throws Exception {
        CtrlRequest request = new CtrlRequest("flowtopology/" + ComponentType.CRUD_BOLT.toString(),
                new RequestData("dump"), 1, "dump-correlation-id", Destination.WFM_CTRL);

        sendMessage(request, flowTopology.getConfig().getKafkaFlowTopic());

        ConsumerRecord<String, String> raw = ctrlConsumer.pollMessage();

        assertNotNull(raw);
        assertNotNull(raw.value());

        Message responseGeneric = objectMapper.readValue(raw.value(), Message.class);
        CtrlResponse response = (CtrlResponse) responseGeneric;
        ResponseData payload = response.getData();

        assertEquals(request.getCorrelationId(), response.getCorrelationId());
        assertEquals(ComponentType.CRUD_BOLT.toString(), payload.getComponent());
        assertTrue(payload instanceof DumpStateResponseData);
    }

    @Test
    public void shouldSyncCacheProvideDifferenceWithFlowsTest() throws Exception {
        String flowId = UUID.randomUUID().toString();

        createFlow(flowId);

        nbConsumer.clear();

        FlowCacheSyncRequest commandData = new FlowCacheSyncRequest(SynchronizeCacheAction.NONE);
        CommandMessage message = new CommandMessage(commandData, 0, "sync-cache-flow", Destination.WFM);
        sendFlowMessage(message);

        String nbMessageValue = nbConsumer.pollMessageValue();
        assertNotNull(nbMessageValue);

        InfoMessage infoMessage = objectMapper.readValue(nbMessageValue, InfoMessage.class);
        FlowCacheSyncResponse infoData = (FlowCacheSyncResponse) infoMessage.getData();
        FlowCacheSyncResults flowNbPayload = infoData.getPayload();
        assertNotNull(flowNbPayload);
        assertEquals(1, flowNbPayload.getDroppedFlows().size());
        assertEquals(flowId, flowNbPayload.getDroppedFlows().get(0));
    }

    @Test
    public void shouldSyncCacheWithFlowsTest() throws Exception {
        String flowId = UUID.randomUUID().toString();

        createFlow(flowId);

        cacheConsumer.pollMessage();
        cacheConsumer.clear();

        FlowCacheSyncRequest commandData = new FlowCacheSyncRequest(SynchronizeCacheAction.SYNCHRONIZE_CACHE);
        CommandMessage message = new CommandMessage(commandData, 0, "sync-cache-flow", Destination.WFM);
        sendFlowMessage(message);

        String cacheMessageValue = cacheConsumer.pollMessageValue();
        InfoMessage infoMessage = objectMapper.readValue(cacheMessageValue, InfoMessage.class);
        FlowInfoData infoData = (FlowInfoData) infoMessage.getData();
        assertEquals(FlowOperation.CACHE, infoData.getOperation());
        assertEquals(flowId, infoData.getFlowId());

        nbConsumer.clear();

        statusFlow(flowId);

        checkErrorResponseType(nbConsumer.pollMessage(), ErrorType.NOT_FOUND);
    }

    @Test
    public void shouldInvalidateCacheWithFlowsTest() throws Exception {
        String flowId = UUID.randomUUID().toString();

        createFlow(flowId);

        cacheConsumer.pollMessage();
        cacheConsumer.clear();

        FlowCacheSyncRequest commandData = new FlowCacheSyncRequest(SynchronizeCacheAction.INVALIDATE_CACHE);
        CommandMessage message = new CommandMessage(commandData, 0, "sync-cache-flow", Destination.WFM);
        sendFlowMessage(message);

        String cacheMessageValue = cacheConsumer.pollMessageValue();
        InfoMessage infoMessage = objectMapper.readValue(cacheMessageValue, InfoMessage.class);
        FlowInfoData infoData = (FlowInfoData) infoMessage.getData();
        assertEquals(FlowOperation.CACHE, infoData.getOperation());
        assertEquals(flowId, infoData.getFlowId());

        nbConsumer.clear();

        statusFlow(flowId);

        checkErrorResponseType(nbConsumer.pollMessage(), ErrorType.NOT_FOUND);
    }

    private void checkFlowReadStatus(
            ConsumerRecord<String, String> record, String flowId, FlowState state) throws IOException {
        assertNotNull(record);
        assertNotNull(record.value());

        InfoMessage message = objectMapper.readValue(record.value(), InfoMessage.class);
        assertNotNull(message);

        FlowReadResponse flowResponse = (FlowReadResponse) message.getData();
        assertNotNull(flowResponse);

        BidirectionalFlow flowPayload = flowResponse.getPayload();
        assertNotNull(flowPayload);
        assertEquals(flowId, flowPayload.getFlowId());
        assertEquals(state, flowPayload.getForward().getState());
    }

    private void checkErrorResponseType(ConsumerRecord<String, String> record, ErrorType type) throws IOException {
        assertNotNull(record);
        assertNotNull(record.value());

        ErrorMessage errorMessage = objectMapper.readValue(record.value(), ErrorMessage.class);
        assertNotNull(errorMessage);
        assertEquals(type, errorMessage.getData().getErrorType());
    }

    private Flow deleteFlow(final String flowId) throws IOException {
        System.out.println("NORTHBOUND: Delete flow");
        Flow payload = new Flow();
        payload.setFlowId(flowId);
        FlowDeleteRequest commandData = new FlowDeleteRequest(payload);
        CommandMessage message = new CommandMessage(commandData, 0, "delete-flow", Destination.WFM);

        //sendNorthboundMessage(message);
        //sendTopologyEngineMessage(message);
        sendFlowMessage(message);

        return payload;
    }

    private Flow createFlow(final String flowId) throws IOException {
        System.out.println("NORTHBOUND: Create flow");
        Flow flowPayload =
                new Flow(flowId, 10000, false, "", new SwitchId("ff:00"), 1, 2,
                        new SwitchId("ff:00"), 1, 2);
        FlowCreateRequest commandData = new FlowCreateRequest(flowPayload);
        CommandMessage message = new CommandMessage(commandData, 0, "create-flow", Destination.WFM);
        //sendNorthboundMessage(message);
        sendFlowMessage(message);
        return flowPayload;
    }

    private Flow updateFlow(final String flowId) throws IOException {
        System.out.println("NORTHBOUND: Update flow");
        Flow flowPayload =
                new Flow(flowId, 10000, false, "", new SwitchId("ff:00"), 1, 2,
                        new SwitchId("ff:00"), 1, 2);
        FlowUpdateRequest commandData = new FlowUpdateRequest(flowPayload);
        CommandMessage message = new CommandMessage(commandData, 0, "update-flow", Destination.WFM);
        //sendNorthboundMessage(message);
        sendFlowMessage(message);
        return flowPayload;
    }

    private void statusFlow(final String flowId) throws IOException {
        System.out.println("NORTHBOUND: Status flow");
        FlowReadRequest commandData = new FlowReadRequest(flowId);
        CommandMessage message = new CommandMessage(commandData, 0, "status-flow", Destination.WFM);
        //sendNorthboundMessage(message);
        sendFlowMessage(message);
    }

    private PathInfoData pathFlow(final String flowId) throws IOException {
        System.out.println("NORTHBOUND: Path flow");
        FlowReadRequest commandData = new FlowReadRequest(flowId);
        CommandMessage message = new CommandMessage(commandData, 0, "path-flow", Destination.WFM);
        //sendNorthboundMessage(message);
        sendFlowMessage(message);
        return new PathInfoData(0L, Collections.emptyList());
    }

    private void getFlow(final String flowId) throws IOException {
        System.out.println("NORTHBOUND: Get flow");
        FlowReadRequest commandData = new FlowReadRequest(flowId);
        CommandMessage message = new CommandMessage(commandData, 0, "get-flow", Destination.WFM);
        //sendNorthboundMessage(message);
        sendFlowMessage(message);
    }

    private void dumpFlows() throws IOException {
        System.out.println("NORTHBOUND: Get flows");
        FlowsDumpRequest commandData = new FlowsDumpRequest();
        CommandMessage message = new CommandMessage(commandData, 0, "get-flows", Destination.WFM);
        //sendNorthboundMessage(message);
        sendFlowMessage(message);
    }

    private void sendTopologyEngineMessage(final Message message) throws IOException {
        String request = objectMapper.writeValueAsString(message);
        kProducer.pushMessage(topologyConfig.getKafkaTopoEngTopic(), request);
    }

    private InstallOneSwitchFlow baseInstallFlowCommand(final String flowId) throws IOException {
        System.out.println("TOPOLOGY: Install flow");
        InstallOneSwitchFlow commandData = new InstallOneSwitchFlow(0L, flowId,
                COOKIE, new SwitchId("ff:04"), 1, 2, 0, 0, OutputVlanType.NONE, 10000L, 0L);
        CommandMessage commandMessage = new CommandMessage(commandData, 0, "install-flow", Destination.WFM);
        //sendTopologyEngineMessage(commandMessage);
        //sendSpeakerMessage(commandMessage);
        sendFlowMessage(commandMessage);
        return commandData;
    }

    private RemoveFlow removeFlowCommand(final String flowId) throws IOException {
        System.out.println("TOPOLOGY: Remove flow");
        RemoveFlow commandData = new RemoveFlow(0L, flowId, COOKIE, new SwitchId("ff:04"), 0L,
                DeleteRulesCriteria.builder().cookie(COOKIE).build());
        CommandMessage commandMessage = new CommandMessage(commandData, 0, "remove-flow", Destination.WFM);
        //sendTopologyEngineMessage(commandMessage);
        sendFlowMessage(commandMessage);
        return commandData;
    }

    private Flow getFlowCommand(final String flowId) throws IOException {
        System.out.println("TOPOLOGY: Get flow");
        Flow flowPayload =
                new Flow(flowId, 10000, false, "", new SwitchId("ff:00"), 1, 2,
                        new SwitchId("ff:00"), 1, 2);
        FlowResponse infoData = new FlowResponse(flowPayload);
        InfoMessage infoMessage = new InfoMessage(infoData, 0, "get-flow", Destination.WFM);
        sendTopologyEngineMessage(infoMessage);
        return flowPayload;
    }

    private List<String> dumpFlowCommand(final String flowId) throws IOException {
        System.out.println("TOPOLOGY: Get flows");
        Flow flow =
                new Flow(flowId, 10000, false, "", new SwitchId("ff:00"), 1, 2,
                        new SwitchId("ff:00"), 1, 2);
        List<String> payload = Collections.singletonList(flow.getFlowId());
        FlowsResponse infoData = new FlowsResponse(payload);
        InfoMessage infoMessage = new InfoMessage(infoData, 0, "dump-flows", Destination.WFM);
        sendTopologyEngineMessage(infoMessage);
        return payload;
    }

    private PathInfoData pathFlowCommand(final String flowId) throws IOException {
        System.out.println("TOPOLOGY: Path flow");
        PathInfoData pathInfoData = new PathInfoData(
                0L, Collections.singletonList(new PathNode(new SwitchId("ff:00"), 1, 0, null)));
        Flow flow = Flow.builder().flowId(flowId).flowPath(pathInfoData).build();
        FlowReadResponse infoData = new FlowReadResponse(new BidirectionalFlow(flow, flow));
        InfoMessage infoMessage =
                new InfoMessage(infoData, 0, "path-flow", Destination.WFM);
        sendTopologyEngineMessage(infoMessage);
        return pathInfoData;
    }

    private ErrorMessage errorFlowTopologyEngineCommand(final String flowId, final ErrorType type) throws IOException {
        System.out.println("TOPOLOGY: Error flow");
        ErrorData errorData = new ErrorData(type, "Could not operate with flow", flowId);
        ErrorMessage errorMessage = new ErrorMessage(errorData, 0, "error-flow", Destination.WFM);
        //sendTopologyEngineMessage(errorMessage);
        sendMessage(errorMessage, topologyConfig.getKafkaFlowTopic());
        return errorMessage;
    }

    private void sendSpeakerMessage(final Message message) throws IOException {
        String request = objectMapper.writeValueAsString(message);
        kProducer.pushMessage(topologyConfig.getKafkaSpeakerTopic(), request);
    }

    private Message baseInstallRuleCommand(final Message message) throws IOException {
        System.out.println("TOPOLOGY: Install rule");
        sendMessage(message, topologyConfig.getKafkaFlowTopic());
        return message;
    }

    private Message removeRuleCommand(final Message message) throws IOException {
        System.out.println("TOPOLOGY: Remove rule");
        sendMessage(message, topologyConfig.getKafkaFlowTopic());
        return message;
    }

    private ErrorMessage errorFlowSpeakerCommand(final String flowId) throws IOException {
        System.out.println("TOPOLOGY: Error rule");
        ErrorData errorData = new ErrorData(ErrorType.REQUEST_INVALID, "Could not operate with flow", flowId);
        ErrorMessage errorMessage = new ErrorMessage(errorData, 0, "error-flow", Destination.WFM_TRANSACTION);
        //sendSpeakerMessage(errorMessage);
        sendMessage(errorMessage, topologyConfig.getKafkaFlowTopic());
        return errorMessage;
    }

    private void sendFlowMessage(final CommandMessage message) throws IOException {
        sendMessage(message, topologyConfig.getKafkaFlowTopic());
    }

    private void sendNorthboundMessage(final CommandMessage message) throws IOException {
        sendMessage(message, topologyConfig.getKafkaNorthboundTopic());
    }

    private void sendMessage(Object object, String topic) throws IOException {
        String request = objectMapper.writeValueAsString(object);
        kProducer.pushMessage(topic, request);
    }

    private ImmutablePair<Flow, Flow> getFlowPayload(InfoMessage message) {
        InfoData data = message.getData();
        FlowInfoData flow = (FlowInfoData) data;
        return flow.getPayload();
    }

    private void sendClearState() throws IOException, InterruptedException {
        CtrlRequest request = new CtrlRequest("flowtopology/" + ComponentType.CRUD_BOLT.toString(),
                new RequestData("clearState"), 1, "clear-state-correlation-id", Destination.WFM_CTRL);
        sendMessage(request, topologyConfig.getKafkaCtrlTopic());

        ConsumerRecord<String, String> raw = ctrlConsumer.pollMessage();
        assertNotNull(raw);

        CtrlResponse response = (CtrlResponse) objectMapper.readValue(raw.value(), Message.class);
        assertEquals(request.getCorrelationId(), response.getCorrelationId());
    }
}
