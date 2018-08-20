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

package org.openkilda.wfm.topology.cache.transport;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.ctrl.CtrlRequest;
import org.openkilda.messaging.ctrl.CtrlResponse;
import org.openkilda.messaging.ctrl.DumpStateResponseData;
import org.openkilda.messaging.ctrl.RequestData;
import org.openkilda.messaging.ctrl.ResponseData;
import org.openkilda.messaging.ctrl.state.CacheBoltState;
import org.openkilda.messaging.ctrl.state.NetworkDump;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.NetworkInfoData;
import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;
import org.openkilda.messaging.info.flow.FlowInfoData;
import org.openkilda.messaging.info.flow.FlowOperation;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.FlowPair;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.wfm.AbstractStormTest;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.Neo4jFixture;
import org.openkilda.wfm.topology.TestKafkaConsumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import org.apache.commons.lang.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.storm.Config;
import org.apache.storm.generated.StormTopology;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public class CacheTopologyTest extends AbstractStormTest {
    private static CacheTopology topology;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String firstFlowId = "first-flow";
    private static final String secondFlowId = "second-flow";
    private static final String thirdFlowId = "third-flow";
    private static final SwitchInfoData sw = new SwitchInfoData(new SwitchId("ff:03"),
            SwitchState.ADDED, "127.0.0.1", "localhost", "test switch", "kilda");
    private static final FlowPair<Flow, Flow> firstFlow = new FlowPair<>(
            new Flow(firstFlowId, 10000, false, "", sw.getSwitchId(), 1, 2, sw.getSwitchId(), 1, 2),
            new Flow(firstFlowId, 10000, false, "", sw.getSwitchId(), 1, 2, sw.getSwitchId(), 1, 2));
    private static final FlowPair<Flow, Flow> secondFlow = new FlowPair<>(
            new Flow(secondFlowId, 10000, false, "", new SwitchId("ff:00"), 1, 2,
                    new SwitchId("ff:00"), 1, 2),
            new Flow(secondFlowId, 10000, false, "", new SwitchId("ff:00"), 1, 2,
                    new SwitchId("ff:00"), 1, 2));
    private static final FlowPair<Flow, Flow> thirdFlow = new FlowPair<>(
            new Flow(thirdFlowId, 10000, false, "", new SwitchId("ff:00"), 1, 2,
                    new SwitchId("ff:00"), 1, 2),
            new Flow(thirdFlowId, 10000, false, "", new SwitchId("ff:00"), 1, 2,
                    new SwitchId("ff:00"), 1, 2));
    private static final Set<FlowPair<Flow, Flow>> flows = new HashSet<>();
    private static final NetworkInfoData dump = new NetworkInfoData(
            "test", Collections.singleton(sw), Collections.emptySet(), Collections.emptySet(), flows);

    private static TestKafkaConsumer teConsumer;
    private static TestKafkaConsumer flowConsumer;
    private static TestKafkaConsumer ctrlConsumer;

    private static Neo4jFixture fakeNeo4jDb;

    @BeforeClass
    public static void setupOnce() throws Exception {
        AbstractStormTest.setupOnce();

        Properties configOverlay = new Properties();

        fakeNeo4jDb = new Neo4jFixture(fsData.getRoot().toPath(), NEO4J_LISTEN_ADDRESS);
        fakeNeo4jDb.start();
        configOverlay.setProperty("neo4j.hosts", fakeNeo4jDb.getListenAddress());

        flows.add(firstFlow);
        flows.add(secondFlow);

        LaunchEnvironment launchEnvironment = makeLaunchEnvironment();
        launchEnvironment.setupOverlay(configOverlay);
        topology = new CacheTopology(launchEnvironment);
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
    }

    @Before
    public void init() throws Exception {
        sw.setState(SwitchState.ADDED);
        flowConsumer.clear();
        teConsumer.clear();
        ctrlConsumer.clear();

        sendClearState();
    }

    @AfterClass
    public static void teardownOnce() throws Exception {

        flowConsumer.wakeup();
        flowConsumer.join();
        teConsumer.wakeup();
        teConsumer.join();
        ctrlConsumer.wakeup();
        ctrlConsumer.join();

        fakeNeo4jDb.stop();

        AbstractStormTest.teardownOnce();
    }


    @Test
    public void cacheReceivesFlowTopologyUpdatesAndSendsToTopologyEngine() throws Exception {
        System.out.println("Flow Update Test");
        teConsumer.clear();
        sendFlowUpdate(thirdFlow);

        ConsumerRecord<String, String> flow = teConsumer.pollMessage();

        Assert.assertNotNull(flow);
        Assert.assertNotNull(flow.value());

        InfoMessage infoMessage = objectMapper.readValue(flow.value(), InfoMessage.class);
        FlowInfoData infoData = (FlowInfoData) infoMessage.getData();
        Assert.assertNotNull(infoData);

        Assert.assertEquals(thirdFlow, infoData.getPayload());
    }

    @Test
    public void ctrlListHandler() throws Exception {
        CtrlRequest request = new CtrlRequest(
                "cachetopology/*", new RequestData("list"), 1, "list-correlation-id", Destination.WFM_CTRL);

        sendMessage(request, topology.getConfig().getKafkaCtrlTopic());

        ConsumerRecord<String, String> raw = ctrlConsumer.pollMessage();

        Assert.assertNotNull(raw);  // TODO: FAILED
        Assert.assertNotNull(raw.value());

        Message responseGeneric = objectMapper.readValue(raw.value(), Message.class);
        CtrlResponse response = (CtrlResponse) responseGeneric;
        ResponseData payload = response.getData();

        Assert.assertEquals(request.getCorrelationId(), response.getCorrelationId());
        Assert.assertEquals(CacheTopology.BOLT_ID_CACHE, payload.getComponent());
    }

    @Test
    public void ctrlDumpHandler() throws Exception {
        CtrlRequest request = new CtrlRequest(
                "cachetopology/*", new RequestData("dump"), 1, "dump-correlation-id", Destination.WFM_CTRL);

        sendMessage(request, topology.getConfig().getKafkaCtrlTopic());

        ConsumerRecord<String, String> raw = ctrlConsumer.pollMessage();

        Assert.assertNotNull(raw);   // TODO: FAILED
        Assert.assertNotNull(raw.value());

        Message responseGeneric = objectMapper.readValue(raw.value(), Message.class);
        CtrlResponse response = (CtrlResponse) responseGeneric;
        ResponseData payload = response.getData();

        Assert.assertEquals(request.getCorrelationId(), response.getCorrelationId());
        Assert.assertEquals(CacheTopology.BOLT_ID_CACHE, payload.getComponent());
        Assert.assertTrue(payload instanceof DumpStateResponseData);
    }

    @Test
    public void ctrlSpecificRoute() throws Exception {
        CtrlRequest request = new CtrlRequest(
                "cachetopology/cache", new RequestData("dump"), 1, "route-correlation-id", Destination.WFM_CTRL);
        sendMessage(request, topology.getConfig().getKafkaCtrlTopic());

        ConsumerRecord<String, String> raw = ctrlConsumer.pollMessage();

        Assert.assertNotNull(raw);   // TODO: FAILED
        Assert.assertNotNull(raw.value());

        Message responseGeneric = objectMapper.readValue(raw.value(), Message.class);
        CtrlResponse response = (CtrlResponse) responseGeneric;
        Assert.assertEquals(request.getCorrelationId(), response.getCorrelationId());
    }

    @Ignore
    @Test
    public void flowShouldBeReroutedWhenIslDies() throws Exception {
        final SwitchId destSwitchId = new SwitchId("ff:02");
        final String flowId = "flowId";
        sendData(sw);

        SwitchInfoData destSwitch = new SwitchInfoData(destSwitchId, SwitchState.ACTIVATED, StringUtils.EMPTY,
                StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY);
        sendData(destSwitch);

        List<PathNode> path = ImmutableList.of(
                new PathNode(sw.getSwitchId(), 0, 0),
                new PathNode(destSwitch.getSwitchId(), 0, 1)
        );
        IslInfoData isl = new IslInfoData(0L, path, 0L, IslChangeType.DISCOVERED, 0L);
        sendData(isl);

        FlowInfoData flowData = buildFlowInfoData(flowId, sw.getSwitchId(), destSwitchId, path);
        sendData(flowData);

        //mark isl as failed
        flowConsumer.clear();
        isl.setState(IslChangeType.FAILED);
        sendData(isl);

        //we are expecting that flow should be rerouted
        ConsumerRecord<String, String> record = flowConsumer.pollMessage();
        Assert.assertNotNull(record);
        CommandMessage message = objectMapper.readValue(record.value(), CommandMessage.class);
        Assert.assertNotNull(message);
        FlowRerouteRequest command = (FlowRerouteRequest) message.getData();
        Assert.assertEquals(command.getFlowId(), flowId);
    }

    private static <T extends Message> void sendMessage(T message, String topic) throws IOException {
        String request = objectMapper.writeValueAsString(message);
        kProducer.pushMessage(topic, request);
    }

    private static void sendNetworkDump(NetworkInfoData data, String correlationId) throws IOException {
        System.out.println("Topology-Engine: Send Network Dump");
        InfoMessage info = new InfoMessage(data, 0, correlationId, Destination.WFM_CACHE);
        sendMessage(info, topology.getConfig().getKafkaTopoCacheTopic());
    }

    private static <T extends InfoData> void sendData(T infoData) throws IOException {
        InfoMessage info = new InfoMessage(infoData, 0, UUID.randomUUID().toString(), Destination.WFM_CACHE);
        sendMessage(info, topology.getConfig().getKafkaTopoCacheTopic());
    }

    private static void sendFlowUpdate(FlowPair<Flow, Flow> flow) throws IOException {
        System.out.println("Flow Topology: Send Flow Creation Request");
        String correlationId = UUID.randomUUID().toString();
        FlowInfoData data = new FlowInfoData(flow.getLeft().getFlowId(),
                flow, FlowOperation.CREATE, correlationId);
        // TODO: as part of getting rid of OutputTopic, used TopoDiscoTopic. This feels wrong for
        // Flows.
        InfoMessage message = new InfoMessage(data, System.currentTimeMillis(), correlationId);
        sendMessage(message, topology.getConfig().getKafkaTopoCacheTopic());
    }

    private static void sendClearState() throws IOException, InterruptedException {
        CtrlRequest request = new CtrlRequest(
                "cachetopology/cache", new RequestData("clearState"), 1, "route-correlation-id",
                Destination.WFM_CTRL);
        sendMessage(request, topology.getConfig().getKafkaCtrlTopic());

        ConsumerRecord<String, String> raw = ctrlConsumer.pollMessage();
        // assertNotNull(raw);
        if (raw != null) {
            CtrlResponse response = (CtrlResponse) objectMapper.readValue(raw.value(), Message.class);
            Assert.assertEquals(request.getCorrelationId(), response.getCorrelationId());
        }
    }

    private static void sendNetworkDumpRequest() throws IOException, InterruptedException {
        CtrlRequest request = new CtrlRequest("cachetopology/cache", new RequestData("dump"),
                System.currentTimeMillis(), UUID.randomUUID().toString(), Destination.WFM_CTRL);
        sendMessage(request, topology.getConfig().getKafkaCtrlTopic());
    }

    private NetworkDump getNetworkDump(ConsumerRecord<String, String> raw) throws IOException {
        Message responseGeneric = objectMapper.readValue(raw.value(), Message.class);
        CtrlResponse response = (CtrlResponse) responseGeneric;
        DumpStateResponseData data = (DumpStateResponseData) response.getData();
        CacheBoltState cacheState = (CacheBoltState) data.getState();
        return cacheState.getNetwork();
    }

    private FlowInfoData buildFlowInfoData(String flowId, SwitchId srcSwitch, SwitchId dstSwitch, List<PathNode> path) {
        Flow flow = new Flow();
        flow.setFlowId(flowId);
        flow.setSourceSwitch(srcSwitch);
        flow.setDestinationSwitch(dstSwitch);
        flow.setState(FlowState.UP);

        PathInfoData pathInfoData = new PathInfoData(0L, path);
        flow.setFlowPath(pathInfoData);
        FlowPair<Flow, Flow> flowPair = new FlowPair<>(flow, flow);
        return new FlowInfoData(flowId, flowPair, FlowOperation.CREATE, UUID.randomUUID().toString());
    }

    private static String waitDumpRequest() throws InterruptedException, IOException {
        ConsumerRecord<String, String> raw;
        int sec = 0;
        while ((raw = teConsumer.pollMessage(1000)) == null) {
            System.out.println("Waiting For Dump Request");
            Assert.assertTrue("Waiting For Dump Request failed", ++sec < 20);
        }
        System.out.println("Waiting For Dump Request");

        Message request = objectMapper.readValue(raw.value(), Message.class);
        return request.getCorrelationId();
    }
}
