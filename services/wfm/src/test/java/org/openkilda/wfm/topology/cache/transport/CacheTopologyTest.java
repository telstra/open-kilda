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
import org.openkilda.messaging.command.switches.SwitchDeleteRequest;
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
import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.flow.FlowInfoData;
import org.openkilda.messaging.model.FlowDto;
import org.openkilda.messaging.model.FlowPairDto;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.Neo4jConfig;
import org.openkilda.persistence.Neo4jPersistenceManager;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.impl.Neo4jSessionFactory;
import org.openkilda.wfm.AbstractStormTest;
import org.openkilda.wfm.EmbeddedNeo4jDatabase;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.topology.TestKafkaConsumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;
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
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public class CacheTopologyTest extends AbstractStormTest {
    private static CacheTopology topology;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String firstFlowId = "first-flow";
    private static final String secondFlowId = "second-flow";
    private static final String thirdFlowId = "third-flow";
    private static final SwitchInfoData sw = new SwitchInfoData(new SwitchId("ff:03"),
            SwitchChangeType.ADDED, "127.0.0.1", "localhost", "test switch", "kilda");
    private static final FlowPairDto<FlowDto, FlowDto> firstFlow = new FlowPairDto<>(
            new FlowDto(firstFlowId, 10000, false, "", sw.getSwitchId(), 1, 2, sw.getSwitchId(), 1, 2),
            new FlowDto(firstFlowId, 10000, false, "", sw.getSwitchId(), 1, 2, sw.getSwitchId(), 1, 2));
    private static final FlowPairDto<FlowDto, FlowDto> secondFlow = new FlowPairDto<>(
            new FlowDto(secondFlowId, 10000, false, "", new SwitchId("ff:00"), 1, 2,
                    new SwitchId("ff:00"), 1, 2),
            new FlowDto(secondFlowId, 10000, false, "", new SwitchId("ff:00"), 1, 2,
                    new SwitchId("ff:00"), 1, 2));
    private static final FlowPairDto<FlowDto, FlowDto> thirdFlow = new FlowPairDto<>(
            new FlowDto(thirdFlowId, 10000, false, "", new SwitchId("ff:00"), 1, 2,
                    new SwitchId("ff:00"), 1, 2),
            new FlowDto(thirdFlowId, 10000, false, "", new SwitchId("ff:00"), 1, 2,
                    new SwitchId("ff:00"), 1, 2));

    private static TestKafkaConsumer flowConsumer;
    private static TestKafkaConsumer ctrlConsumer;

    private static EmbeddedNeo4jDatabase embeddedNeo4jDb;
    private static PersistenceManager persistenceManager;

    @BeforeClass
    public static void setupOnce() throws Exception {
        AbstractStormTest.startZooKafkaAndStorm();

        embeddedNeo4jDb = new EmbeddedNeo4jDatabase(fsData.getRoot());

        LaunchEnvironment launchEnvironment = makeLaunchEnvironment();
        Properties configOverlay = new Properties();
        configOverlay.setProperty("neo4j.uri", embeddedNeo4jDb.getConnectionUri());
        launchEnvironment.setupOverlay(configOverlay);

        Neo4jConfig neo4jConfig = launchEnvironment.getConfigurationProvider().getConfiguration(Neo4jConfig.class);
        persistenceManager = new Neo4jPersistenceManager(neo4jConfig);

        topology = new CacheTopology(launchEnvironment);
        StormTopology stormTopology = topology.createTopology();

        Config config = stormConfig();
        cluster.submitTopology(CacheTopologyTest.class.getSimpleName(), config, stormTopology);

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
        sw.setState(SwitchChangeType.ADDED);
        flowConsumer.clear();
        ctrlConsumer.clear();

        sendClearState();

        ((Neo4jSessionFactory) persistenceManager.getTransactionManager()).getSession().purgeDatabase();
    }

    @AfterClass
    public static void teardownOnce() throws Exception {
        flowConsumer.wakeup();
        flowConsumer.join();
        ctrlConsumer.wakeup();
        ctrlConsumer.join();

        embeddedNeo4jDb.stop();

        AbstractStormTest.stopZooKafkaAndStorm();
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

    @Test
    public void switchDelete() throws Exception {
        checkSwitchesCount(0);
        SwitchInfoData data = new SwitchInfoData(new SwitchId("ff:02"), SwitchChangeType.ADDED, "", "", "", "");
        InfoMessage infoMessage = new InfoMessage(data, System.currentTimeMillis(), UUID.randomUUID().toString());
        sendMessage(infoMessage, topology.getConfig().getKafkaTopoCacheTopic());
        checkSwitchesCount(1);
        SwitchDeleteRequest switchDeleteRequest = new SwitchDeleteRequest(new SwitchId("ff:02"));
        CommandMessage commandMessage =
                new CommandMessage(switchDeleteRequest, System.currentTimeMillis(), UUID.randomUUID().toString());
        sendMessage(commandMessage, topology.getConfig().getKafkaTopoCacheTopic());
        checkSwitchesCount(0);
    }

    private void checkSwitchesCount(int expectedCount) throws Exception {
        CtrlRequest dumpRequest = new CtrlRequest(
                "cachetopology/*", new RequestData("dump"), 1, "dump-correlation-id", Destination.WFM_CTRL);

        sendMessage(dumpRequest, topology.getConfig().getKafkaCtrlTopic());

        ConsumerRecord<String, String> raw = ctrlConsumer.pollMessage();

        CtrlResponse response = objectMapper.readValue(raw.value(), CtrlResponse.class);
        DumpStateResponseData dumpState = (DumpStateResponseData) response.getData();
        CacheBoltState cacheBoltState = (CacheBoltState) dumpState.getState();
        Assert.assertEquals(expectedCount, cacheBoltState.getNetwork().getSwitches().size());
    }

    @Ignore
    @Test
    public void flowShouldBeReroutedWhenIslDies() throws Exception {
        final SwitchId destSwitchId = new SwitchId("ff:02");
        final String flowId = "flowId";
        sendData(sw);

        SwitchInfoData destSwitch = new SwitchInfoData(destSwitchId, SwitchChangeType.ACTIVATED, StringUtils.EMPTY,
                StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY);
        sendData(destSwitch);

        List<PathNode> path = ImmutableList.of(
                new PathNode(sw.getSwitchId(), 0, 0),
                new PathNode(destSwitch.getSwitchId(), 0, 1)
        );
        IslInfoData isl = new IslInfoData(0L, path.get(0), path.get(1), 0L, IslChangeType.DISCOVERED, 0L);
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

    private static void sendFlowUpdate(FlowPairDto<FlowDto, FlowDto> flow) throws IOException {
        System.out.println("Flow Topology: Send Flow Creation Request");
        String correlationId = UUID.randomUUID().toString();
        FlowInfoData data = new FlowInfoData(flow.getLeft().getFlowId(),
                flow, null, correlationId);
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
        if (raw != null) {
            CtrlResponse response = (CtrlResponse) objectMapper.readValue(raw.value(), Message.class);
            Assert.assertEquals(request.getCorrelationId(), response.getCorrelationId());
        }
    }

    private static void sendNetworkDumpRequest() throws IOException {
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
        FlowDto flow = new FlowDto();
        flow.setFlowId(flowId);
        flow.setSourceSwitch(srcSwitch);
        flow.setDestinationSwitch(dstSwitch);
        flow.setState(FlowState.UP);

        PathInfoData pathInfoData = new PathInfoData(0L, path);
        flow.setFlowPath(pathInfoData);
        FlowPairDto<FlowDto, FlowDto> flowPair = new FlowPairDto<>(flow, flow);
        return new FlowInfoData(flowId, flowPair, null, UUID.randomUUID().toString());
    }
}
