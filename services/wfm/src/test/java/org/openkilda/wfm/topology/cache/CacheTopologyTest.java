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

package org.openkilda.wfm.topology.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.emory.mathcs.backport.java.util.Collections;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.storm.Config;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.utils.Utils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.FlowRestoreRequest;
import org.openkilda.messaging.ctrl.CtrlRequest;
import org.openkilda.messaging.ctrl.CtrlResponse;
import org.openkilda.messaging.ctrl.DumpStateResponseData;
import org.openkilda.messaging.ctrl.RequestData;
import org.openkilda.messaging.ctrl.ResponseData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.NetworkInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;
import org.openkilda.messaging.info.flow.FlowInfoData;
import org.openkilda.messaging.info.flow.FlowOperation;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;
import org.openkilda.wfm.AbstractStormTest;
import org.openkilda.wfm.topology.TestKafkaConsumer;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class CacheTopologyTest extends AbstractStormTest {
    private static CacheTopology topology;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String firstFlowId = "first-flow";
    private static final String secondFlowId = "second-flow";
    private static final String thirdFlowId = "third-flow";
    private static final SwitchInfoData sw = new SwitchInfoData("sw",
            SwitchState.ADDED, "127.0.0.1", "localhost", "test switch", "kilda");
    private static final ImmutablePair<Flow, Flow> firstFlow = new ImmutablePair<>(
            new Flow(firstFlowId, 10000, "", "test-switch", 1, 2, "test-switch", 1, 2),
            new Flow(firstFlowId, 10000, "", "test-switch", 1, 2, "test-switch", 1, 2));
    private static final ImmutablePair<Flow, Flow> secondFlow = new ImmutablePair<>(
            new Flow(secondFlowId, 10000, "", "test-switch", 1, 2, "test-switch", 1, 2),
            new Flow(secondFlowId, 10000, "", "test-switch", 1, 2, "test-switch", 1, 2));
    private static final ImmutablePair<Flow, Flow> thirdFlow = new ImmutablePair<>(
            new Flow(thirdFlowId, 10000, "", "test-switch", 1, 2, "test-switch", 1, 2),
            new Flow(thirdFlowId, 10000, "", "test-switch", 1, 2, "test-switch", 1, 2));
    private static final Set<ImmutablePair<Flow, Flow>> flows = new HashSet<>();
    private static final NetworkInfoData dump = new NetworkInfoData(
            "test", Collections.emptySet(), Collections.emptySet(), flows);

    private static TestKafkaConsumer teConsumer;
    private static TestKafkaConsumer flowConsumer;
    private static TestKafkaConsumer ctrlConsumer;

    @BeforeClass
    public static void setupOnce() throws Exception {
        AbstractStormTest.setupOnce();

        flows.add(firstFlow);
        flows.add(secondFlow);

        topology = new CacheTopology(makeLaunchEnvironment());
        StormTopology stormTopology = topology.createTopology();
        Config config = stormConfig();
        cluster.submitTopology(CacheTopologyTest.class.getSimpleName(), config, stormTopology);

        teConsumer = new TestKafkaConsumer(
                topology.getConfig().getKafkaTopoEngTopic(), Destination.TOPOLOGY_ENGINE,
                kafkaProperties(UUID.nameUUIDFromBytes(Destination.TOPOLOGY_ENGINE.toString().getBytes()).toString())
        );
        teConsumer.start();

        flowConsumer = new TestKafkaConsumer(
                topology.getConfig().getKafkaFlowTopic(), Destination.WFM,
                kafkaProperties(UUID.nameUUIDFromBytes(Destination.WFM.toString().getBytes()).toString())
        );
        flowConsumer.start();

        ctrlConsumer = new TestKafkaConsumer(
                topology.getConfig().getKafkaCtrlTopic(), Destination.CTRL_CLIENT,
                kafkaProperties(UUID.nameUUIDFromBytes(Destination.CTRL_CLIENT.toString().getBytes()).toString())
        );
        ctrlConsumer.start();

        waitDumpRequest();
        sendNetworkDump(dump);
    }


    @AfterClass
    public static void teardownOnce() throws Exception {
        cluster.killTopology(CacheTopologyTest.class.getSimpleName());
        Utils.sleep(4 * 1000);
        AbstractStormTest.teardownOnce();
    }


    @Test
    public void cacheReceivesFlowTopologyUpdatesAndSendsToTopologyEngine() throws Exception {
        System.out.println("Flow Update Test");

        sendFlowUpdate(thirdFlow);

        ConsumerRecord<String, String> flow = teConsumer.pollMessage();

        assertNotNull(flow);
        assertNotNull(flow.value());

        InfoMessage infoMessage = objectMapper.readValue(flow.value(), InfoMessage.class);
        FlowInfoData infoData = (FlowInfoData) infoMessage.getData();
        assertNotNull(infoData);

        assertEquals(thirdFlow, infoData.getPayload());
    }

    @Test
    public void cacheReceivesWfmTopologyUpdatesAndSendsToTopologyEngine() throws Exception {
        System.out.println("Network Update Test");

        sendSwitchUpdate(sw);

        ConsumerRecord<String, String> record = teConsumer.pollMessage();

        assertNotNull(record);
        assertNotNull(record.value());

        InfoMessage infoMessage = objectMapper.readValue(record.value(), InfoMessage.class);
        SwitchInfoData data = (SwitchInfoData) infoMessage.getData();
        assertNotNull(data);

        assertEquals(sw, data);
    }

    @Test
    public void cacheReceivesNetworkDumpAndSendsToFlowTopology() throws Exception {
        System.out.println("Dump Test");

        ConsumerRecord<String, String> firstRecord = flowConsumer.pollMessage();
        assertNotNull(firstRecord);
        assertNotNull(firstRecord.value());

        Set<String> flowIds = new HashSet<>(Arrays.asList(firstFlowId, secondFlowId));
        CommandMessage commandMessage = objectMapper.readValue(firstRecord.value(), CommandMessage.class);
        FlowRestoreRequest commandData = (FlowRestoreRequest) commandMessage.getData();
        assertNotNull(commandData);
        assertTrue(flowIds.contains(commandData.getPayload().getLeft().getFlowId()));

        ConsumerRecord<String, String> secondRecord = flowConsumer.pollMessage();
        assertNotNull(secondRecord);
        assertNotNull(secondRecord.value());

        commandMessage = objectMapper.readValue(secondRecord.value(), CommandMessage.class);
        commandData = (FlowRestoreRequest) commandMessage.getData();
        assertNotNull(commandData);
        assertTrue(flowIds.contains(commandData.getPayload().getLeft().getFlowId()));
    }

    @Test
    public void cacheReceivesInfoDataBeforeNetworkDump() throws Exception {
        System.out.println("Cache receives InfoData before NetworkDump Test");

        sendClearState();
        waitDumpRequest();

        // Send switchUpdate info to not initialized bolt
        sendSwitchUpdate(sw);

        // Bolt must fail that tuple
        ConsumerRecord<String, String> record = teConsumer.pollMessage();
        assertNull(record);

        // Init bolt with dump from TE
        sendNetworkDump(dump);

        // Check if SwitchInfoData is ok
        record = teConsumer.pollMessage();
        assertNotNull(record);
        assertNotNull(record.value());
        InfoMessage infoMessage = objectMapper.readValue(record.value(), InfoMessage.class);
        SwitchInfoData data = (SwitchInfoData) infoMessage.getData();
        assertNotNull(data);
        assertEquals(sw, data);

        Set<String> flowIds = new HashSet<>(Arrays.asList(firstFlowId, secondFlowId));
        ConsumerRecord<String, String> firstRecord = flowConsumer.pollMessage();
        assertNotNull(firstRecord);
        assertNotNull(firstRecord.value());
        CommandMessage commandMessage = objectMapper.readValue(firstRecord.value(), CommandMessage.class);
        FlowRestoreRequest commandData = (FlowRestoreRequest) commandMessage.getData();
        assertNotNull(commandData);
        assertTrue(flowIds.contains(commandData.getPayload().getLeft().getFlowId()));

        ConsumerRecord<String, String> secondRecord = flowConsumer.pollMessage();
        assertNotNull(secondRecord);
        assertNotNull(secondRecord.value());
        commandMessage = objectMapper.readValue(secondRecord.value(), CommandMessage.class);
        commandData = (FlowRestoreRequest) commandMessage.getData();
        assertNotNull(commandData);
        assertTrue(flowIds.contains(commandData.getPayload().getLeft().getFlowId()));
    }

    @Test
    @Ignore // TODO: ignoring on 2018.01.04 - failing in GCP but not Mac - needs troubleshooting
    public void ctrlListHandler() throws Exception {
        CtrlRequest request = new CtrlRequest(
                "cachetopology/*", new RequestData("list"), 1, "list-correlation-id", Destination.WFM_CTRL);

        sendMessage(request, topology.getConfig().getKafkaCtrlTopic());

        ConsumerRecord<String, String> raw = ctrlConsumer.pollMessage();

        assertNotNull(raw);  // TODO: FAILED
        assertNotNull(raw.value());

        Message responseGeneric = objectMapper.readValue(raw.value(), Message.class);
        CtrlResponse response = (CtrlResponse) responseGeneric;
        ResponseData payload = response.getData();

        assertEquals(request.getCorrelationId(), response.getCorrelationId());
        assertEquals(CacheTopology.BOLT_ID_CACHE, payload.getComponent());
    }

    @Test
    @Ignore // TODO: ignoring on 2018.01.04 - failing in GCP but not Mac - needs troubleshooting
    public void ctrlDumpHandler() throws Exception {
        CtrlRequest request = new CtrlRequest(
                "cachetopology/*", new RequestData("dump"), 1, "dump-correlation-id", Destination.WFM_CTRL);

        sendMessage(request, topology.getConfig().getKafkaCtrlTopic());

        ConsumerRecord<String, String> raw = ctrlConsumer.pollMessage();

        assertNotNull(raw);   // TODO: FAILED
        assertNotNull(raw.value());

        Message responseGeneric = objectMapper.readValue(raw.value(), Message.class);
        CtrlResponse response = (CtrlResponse) responseGeneric;
        ResponseData payload = response.getData();

        assertEquals(request.getCorrelationId(), response.getCorrelationId());
        assertEquals(CacheTopology.BOLT_ID_CACHE, payload.getComponent());
        assertTrue(payload instanceof DumpStateResponseData);
    }

    @Test
    @Ignore // TODO: ignoring on 2018.01.04 - failing in GCP but not Mac - needs troubleshooting
    public void ctrlSpecificRoute() throws Exception {
        CtrlRequest request = new CtrlRequest(
                "cachetopology/cache", new RequestData("dump"), 1, "route-correlation-id", Destination.WFM_CTRL);
        sendMessage(request, topology.getConfig().getKafkaCtrlTopic());

        ConsumerRecord<String, String> raw = ctrlConsumer.pollMessage();

        assertNotNull(raw);   // TODO: FAILED
        assertNotNull(raw.value());

        Message responseGeneric = objectMapper.readValue(raw.value(), Message.class);
        CtrlResponse response = (CtrlResponse) responseGeneric;
        assertEquals(request.getCorrelationId(), response.getCorrelationId());
    }

    private static void sendMessage(Object object, String topic) throws IOException {
        String request = objectMapper.writeValueAsString(object);
        kProducer.pushMessage(topic, request);
    }

    private static void sendNetworkDump(NetworkInfoData data) throws IOException {
        System.out.println("Topology-Engine: Send Network Dump");
        InfoMessage info = new InfoMessage(data, 0, DEFAULT_CORRELATION_ID, Destination.WFM_CACHE);
        sendMessage(info, topology.getConfig().getKafkaTopoCacheTopic());
    }

    private static void sendSwitchUpdate(SwitchInfoData sw) throws IOException {
        System.out.println("Wfm Topology: Send Switch Add Request");
        sendMessage(sw, topology.getConfig().getKafkaTopoCacheTopic());
    }

    private static void sendFlowUpdate(ImmutablePair<Flow, Flow> flow) throws IOException {
        System.out.println("Flow Topology: Send Flow Creation Request");
        FlowInfoData data = new FlowInfoData(flow.getLeft().getFlowId(),
                flow, FlowOperation.CREATE, DEFAULT_CORRELATION_ID);
        // TODO: as part of getting rid of OutputTopic, used TopoDiscoTopic. This feels wrong for
        // Flows.
        sendMessage(data, topology.getConfig().getKafkaTopoCacheTopic());
    }

    private static void sendClearState() throws IOException, InterruptedException {
        CtrlRequest request = new CtrlRequest(
                "cachetopology/cache", new RequestData("clearState"), 1, "route-correlation-id",
                Destination.WFM_CTRL);
        sendMessage(request, topology.getConfig().getKafkaCtrlTopic());
    }

    private static void waitDumpRequest() throws InterruptedException {
        int sec = 0;
        while (teConsumer.pollMessage(1000) == null)
        {
            System.out.println("Waiting For Dump Request");
            assertTrue("Waiting For Dump Request failed", ++sec < 20);
        }
        System.out.println("Waiting For Dump Request");
    }
}
