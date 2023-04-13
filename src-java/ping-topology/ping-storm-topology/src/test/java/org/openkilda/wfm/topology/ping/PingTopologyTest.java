/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.ping;

import static java.lang.String.format;
import static org.apache.storm.utils.Utils.sleep;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildHaFlowPath;
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildHaSubFlow;
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildPath;
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildSegments;
import static org.openkilda.wfm.config.KafkaConfig.FLOW_PING_TOPOLOGY_TEST_KAFKA_PORT;
import static org.openkilda.wfm.config.ZookeeperConfig.FLOW_PING_TOPOLOGY_TEST_ZOOKEEPER_PORT;

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.HaFlowPingRequest;
import org.openkilda.messaging.floodlight.request.PingRequest;
import org.openkilda.messaging.floodlight.response.PingResponse;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.HaFlowPingResponse;
import org.openkilda.messaging.info.flow.SubFlowPingPayload;
import org.openkilda.messaging.info.flow.UniSubFlowPingPayload;
import org.openkilda.messaging.model.Ping.Errors;
import org.openkilda.messaging.model.PingMeters;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.GroupId;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.TransitVlan;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.inmemory.InMemoryGraphPersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.HaFlowPathRepository;
import org.openkilda.persistence.repositories.HaFlowRepository;
import org.openkilda.persistence.repositories.HaSubFlowRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.wfm.AbstractStormTest;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.topology.TestKafkaConsumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Lists;
import org.apache.storm.Config;
import org.apache.storm.generated.StormTopology;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public class PingTopologyTest extends AbstractStormTest {
    public static final String COMPONENT_NAME = "ping";
    public static final String RUN_ID = "blue";
    public static final String ROOT_NODE = "kilda";

    private static final String HA_FLOW_ID_1 = "test_ha_flow_1";
    private static final PathId SUB_PATH_ID_4 = new PathId("sub_path_id_4");
    private static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    private static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    private static final SwitchId SWITCH_ID_3 = new SwitchId(3);
    private static final SwitchId SWITCH_ID_4 = new SwitchId(4);
    private static final PathId PATH_ID_1 = new PathId("path_1");
    private static final PathId PATH_ID_2 = new PathId("path_2");

    private static final String SUB_FLOW_ID_A = "test_ha_flow_1-a";
    private static final String SUB_FLOW_ID_B = "test_ha_flow_1-b";

    private static final String DESCRIPTION_1 = "description_1";
    private static final String DESCRIPTION_2 = "description_2";
    private static final PathId SUB_PATH_ID_1 = new PathId("sub_path_id_1");
    private static final PathId SUB_PATH_ID_2 = new PathId("sub_path_id_2");
    private static final PathId SUB_PATH_ID_3 = new PathId("sub_path_id_3");
    private static final int PORT_1 = 1;
    private static final int PORT_2 = 2;
    private static final int VLAN_1 = 5;
    private static final int VLAN_2 = 6;

    private static final MeterId METER_ID_1 = new MeterId(11);
    private static final MeterId METER_ID_2 = new MeterId(12);

    private static final GroupId GROUP_ID_1 = new GroupId(15);

    private static final FlowSegmentCookie COOKIE_1 = FlowSegmentCookie.builder().flowEffectiveId(1)
            .direction(FlowPathDirection.FORWARD).build();
    private static final FlowSegmentCookie COOKIE_2 = FlowSegmentCookie.builder().flowEffectiveId(2)
            .direction(FlowPathDirection.REVERSE).build();
    private static final int BANDWIDTH_1 = 1000;

    private static InMemoryGraphPersistenceManager persistenceManager;
    private static PingTopologyConfig pingTopologyConfig;
    private static TestKafkaConsumer speakerConsumer;
    private static TestKafkaConsumer northboundConsumer;
    private static FlowPathRepository flowPathRepository;
    private static TransitVlanRepository transitVlanRepository;
    private static HaFlowRepository haFlowRepository;
    private static HaFlowPathRepository haFlowPathRepository;
    private static HaSubFlowRepository haSubFlowRepository;
    private static SwitchRepository switchRepository;

    private Switch switch1;
    private Switch switch2;
    private Switch switch3;
    private Switch switch4;

    @BeforeClass
    public static void setupOnce() throws Exception {
        Properties configOverlay = getZooKeeperProperties(FLOW_PING_TOPOLOGY_TEST_ZOOKEEPER_PORT, ROOT_NODE);
        configOverlay.putAll(getKafkaProperties(FLOW_PING_TOPOLOGY_TEST_KAFKA_PORT));

        AbstractStormTest.startZooKafka(configOverlay);
        setStartSignal(FLOW_PING_TOPOLOGY_TEST_ZOOKEEPER_PORT, ROOT_NODE, COMPONENT_NAME, RUN_ID);
        AbstractStormTest.startStorm(COMPONENT_NAME, RUN_ID);

        LaunchEnvironment launchEnvironment = makeLaunchEnvironment();
        launchEnvironment.setupOverlay(configOverlay);

        persistenceManager = InMemoryGraphPersistenceManager.newInstance();
        persistenceManager.install();

        PingTopology pingTopology = new PingTopology(launchEnvironment, persistenceManager);
        pingTopologyConfig = pingTopology.getConfig();
        StormTopology stormTopology = pingTopology.createTopology();
        Config config = stormConfig();
        cluster.submitTopology(PingTopologyTest.class.getSimpleName(), config, stormTopology);

        speakerConsumer = new TestKafkaConsumer(pingTopologyConfig.getKafkaSpeakerFlowPingTopic(),
                kafkaConsumerProperties(UUID.randomUUID().toString(), "floodlight", RUN_ID), 500);
        speakerConsumer.start();

        northboundConsumer = new TestKafkaConsumer(pingTopologyConfig.getKafkaNorthboundTopic(),
                kafkaConsumerProperties(UUID.randomUUID().toString(), "northbound", RUN_ID), 1000);
        northboundConsumer.start();

        haFlowPathRepository = persistenceManager.getRepositoryFactory().createHaFlowPathRepository();
        flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
        haFlowRepository = persistenceManager.getRepositoryFactory().createHaFlowRepository();
        haSubFlowRepository = persistenceManager.getRepositoryFactory().createHaSubFlowRepository();
        switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        transitVlanRepository = persistenceManager.getRepositoryFactory().createTransitVlanRepository();

        sleep(TOPOLOGY_START_TIMEOUT);
    }

    @AfterClass
    public static void teardownOnce() throws Exception {
        speakerConsumer.wakeup();
        speakerConsumer.join();
        northboundConsumer.wakeup();
        northboundConsumer.join();
        AbstractStormTest.stopZooKafkaAndStorm();
    }

    @After
    public void teardown() {
        persistenceManager.getInMemoryImplementation().purgeData();
        speakerConsumer.clear();
        northboundConsumer.clear();
    }

    @Before
    public void setup() {
        switch1 = createTestSwitch(SWITCH_ID_1);
        switch2 = createTestSwitch(SWITCH_ID_2);
        switch3 = createTestSwitch(SWITCH_ID_3);
        switch4 = createTestSwitch(SWITCH_ID_4);
    }

    @Test
    public void flowFetcherHaPingOk() {
        long pingTimeout = 10;
        List<SubFlowPingPayload> expectedSubFlowsPayload = Lists.newArrayList(
                new SubFlowPingPayload(SUB_FLOW_ID_B,
                        new UniSubFlowPingPayload(true, null, 1),
                        new UniSubFlowPingPayload(true, null, 1)),
                new SubFlowPingPayload(SUB_FLOW_ID_A,
                        new UniSubFlowPingPayload(true, null, 1),
                        new UniSubFlowPingPayload(true, null, 1)));

        final HaFlowPingResponse expectedResponse =
                new HaFlowPingResponse(HA_FLOW_ID_1, null, expectedSubFlowsPayload);

        createCompleteHaFlow();

        sendNorthboundHaFlowPingCommand(HA_FLOW_ID_1, pingTimeout);

        List<CommandMessage> pingCommands = speakerConsumer.assertNAndPoll(4, CommandMessage.class);
        pingCommands.forEach(this::sendSpeakerAnswer);

        InfoMessage infoMessage = northboundConsumer.assertNAndPoll(1, InfoMessage.class).get(0);

        HaFlowPingResponse response = (HaFlowPingResponse) infoMessage.getData();
        Assertions.assertEquals(expectedResponse, response);
    }

    @Test
    public void flowFetcherHaPingTimeoutErrors() {
        long pingTimeout = 1;
        List<SubFlowPingPayload> expectedSubFlowsPayload = Lists.newArrayList(
                new SubFlowPingPayload(SUB_FLOW_ID_B,
                        new UniSubFlowPingPayload(false, Errors.TIMEOUT, 0),
                        new UniSubFlowPingPayload(false, Errors.TIMEOUT, 0)),
                new SubFlowPingPayload(SUB_FLOW_ID_A,
                        new UniSubFlowPingPayload(false, Errors.TIMEOUT, 0),
                        new UniSubFlowPingPayload(false, Errors.TIMEOUT, 0)));

        final HaFlowPingResponse expectedResponse =
                new HaFlowPingResponse(HA_FLOW_ID_1, null, expectedSubFlowsPayload);

        createCompleteHaFlow();

        sendNorthboundHaFlowPingCommand(HA_FLOW_ID_1, pingTimeout);

        speakerConsumer.assertNAndPoll(4, CommandMessage.class);

        InfoMessage infoMessage = northboundConsumer.assertNAndPoll(1, InfoMessage.class).get(0);

        HaFlowPingResponse response = (HaFlowPingResponse) infoMessage.getData();
        Assertions.assertEquals(expectedResponse, response);
    }

    @Test
    public void flowFetcherTwoHaPingsTimeout() {
        long pingTimeout = 10;
        List<SubFlowPingPayload> expectedSubFlowsPayload = Lists.newArrayList(
                new SubFlowPingPayload(SUB_FLOW_ID_B,
                        new UniSubFlowPingPayload(true, null, 1),
                        new UniSubFlowPingPayload(false, Errors.TIMEOUT, 0)),
                new SubFlowPingPayload(SUB_FLOW_ID_A,
                        new UniSubFlowPingPayload(true, null, 1),
                        new UniSubFlowPingPayload(false, Errors.TIMEOUT, 0)));

        final HaFlowPingResponse expectedResponse =
                new HaFlowPingResponse(HA_FLOW_ID_1, null, expectedSubFlowsPayload);

        createCompleteHaFlow();

        sendNorthboundHaFlowPingCommand(HA_FLOW_ID_1, pingTimeout);

        List<CommandMessage> pingCommands = speakerConsumer.assertNAndPoll(4, CommandMessage.class);

        // send only 2 answers
        for (int i = 0; i < 2; i++) {
            sendSpeakerAnswer(pingCommands.get(i));
        }

        InfoMessage infoMessage = northboundConsumer.assertNAndPoll(1, InfoMessage.class).get(0);

        HaFlowPingResponse response = (HaFlowPingResponse) infoMessage.getData();
        Assertions.assertEquals(expectedResponse, response);
    }

    @Test
    public void flowFetcherHaFlowDoesNotExist() {
        long pingTimeout = 0;
        String notExistingHaFlowId = "not_existing_ha_flow";
        String expectedError = "HaFlow not_existing_ha_flow does not exist";
        final HaFlowPingResponse expectedResponse = new HaFlowPingResponse(notExistingHaFlowId, expectedError, null);

        sendNorthboundHaFlowPingCommand(notExistingHaFlowId, pingTimeout);

        InfoMessage infoMessage = northboundConsumer.assertNAndPoll(1, InfoMessage.class).get(0);

        HaFlowPingResponse response = (HaFlowPingResponse) infoMessage.getData();
        Assertions.assertEquals(expectedResponse, response);

        speakerConsumer.assertNAndPoll(0, CommandMessage.class);
    }

    @Test
    public void flowFetcherHaFlowHasNoSubFlows() {
        long pingTimeout = 0;
        String expectedError = format("HaFlow %s has no sub-flows", HA_FLOW_ID_1);
        final HaFlowPingResponse expectedResponse = new HaFlowPingResponse(HA_FLOW_ID_1, expectedError, null);

        createHaFlowWithoutHaSubFlows();

        sendNorthboundHaFlowPingCommand(HA_FLOW_ID_1, pingTimeout);

        InfoMessage infoMessage = northboundConsumer.assertNAndPoll(1, InfoMessage.class).get(0);

        HaFlowPingResponse response = (HaFlowPingResponse) infoMessage.getData();
        Assertions.assertEquals(expectedResponse, response);

        speakerConsumer.assertNAndPoll(0, CommandMessage.class);
    }

    @Test
    public void flowFetcherHaFlowHasOneSubFlowOneSwitchFlow() {
        final long pingTimeout = 10;
        final String expectedError = "One sub flow is one-switch flow";

        List<SubFlowPingPayload> expectedSubFlowsPayload = Lists.newArrayList(
                new SubFlowPingPayload(SUB_FLOW_ID_B,
                        new UniSubFlowPingPayload(true, null, 1),
                        new UniSubFlowPingPayload(true, null, 1)));

        final HaFlowPingResponse expectedResponse =
                new HaFlowPingResponse(HA_FLOW_ID_1, expectedError, expectedSubFlowsPayload);

        createHaFlowWithOneSubFlowOneSwitchFlow();

        sendNorthboundHaFlowPingCommand(HA_FLOW_ID_1, pingTimeout);

        List<CommandMessage> pingCommands = speakerConsumer.assertNAndPoll(2, CommandMessage.class);
        pingCommands.forEach(this::sendSpeakerAnswer);

        InfoMessage infoMessage = northboundConsumer.assertNAndPoll(1, InfoMessage.class).get(0);

        HaFlowPingResponse response = (HaFlowPingResponse) infoMessage.getData();
        Assertions.assertEquals(expectedResponse, response);
    }

    @Test
    public void flowFetcherHaFlowEncapsulationResourceNotFound() {
        long pingTimeout = 0;
        String expectedError = format("Encapsulation resource not found for ha-flow %s", HA_FLOW_ID_1);
        final HaFlowPingResponse expectedResponse = new HaFlowPingResponse(HA_FLOW_ID_1, expectedError, null);

        createHaFlowWithoutTransitVlan();
        sendNorthboundHaFlowPingCommand(HA_FLOW_ID_1, pingTimeout);

        InfoMessage infoMessage = northboundConsumer.assertNAndPoll(1, InfoMessage.class).get(0);

        HaFlowPingResponse response = (HaFlowPingResponse) infoMessage.getData();
        Assertions.assertEquals(expectedResponse, response);

        speakerConsumer.assertNAndPoll(0, CommandMessage.class);
    }

    @Test
    public void flowFetcherHaFlowOneSubFlowWithEndpointEqualsYPoint() {
        long pingTimeout = 0;
        String expectedError =
                format("Temporary disabled. HaFlow %s has one sub-flow with endpoint switch equals to Y-point switch",
                        HA_FLOW_ID_1);
        final HaFlowPingResponse expectedResponse = new HaFlowPingResponse(HA_FLOW_ID_1, expectedError, null);

        createHaFlowWithOneSubFlowWithEndpointEqualsYPoint();
        sendNorthboundHaFlowPingCommand(HA_FLOW_ID_1, pingTimeout);

        InfoMessage infoMessage = northboundConsumer.assertNAndPoll(1, InfoMessage.class).get(0);

        HaFlowPingResponse response = (HaFlowPingResponse) infoMessage.getData();
        Assertions.assertEquals(expectedResponse, response);

        speakerConsumer.assertNAndPoll(0, CommandMessage.class);
    }

    private void createCompleteHaFlow() {
        createHaFlow(true, true, false, false);
    }

    private void createHaFlowWithoutTransitVlan() {
        createHaFlow(true, false, false, false);
    }

    private void createHaFlowWithoutHaSubFlows() {
        createHaFlow(false, false, false, false);
    }

    private void createHaFlowWithOneSubFlowOneSwitchFlow() {
        createHaFlow(true, true, true, false);
    }

    private void createHaFlowWithOneSubFlowWithEndpointEqualsYPoint() {
        createHaFlow(true, true, false, true);
    }

    private void createHaFlow(boolean addSubFlows, boolean addTransitVlan, boolean oneIsOneSwitchFlow,
                              boolean endPointsEqualsYPoint) {
        final Switch sharedSwitch = switch1;
        Switch endpointSwitchA = switch3;
        if (oneIsOneSwitchFlow) {
            endpointSwitchA = sharedSwitch;
        } else if (endPointsEqualsYPoint) {
            endpointSwitchA = switch2;
        }
        final Switch endpointSwitchB = switch4;
        final HaFlow haFlow = HaFlow.builder()
                .haFlowId(HA_FLOW_ID_1)
                .sharedSwitch(sharedSwitch)
                .sharedPort(PORT_1)
                .sharedOuterVlan(0)
                .sharedInnerVlan(0)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .build();

        if (addTransitVlan) {
            TransitVlan transitVlan = TransitVlan.builder().flowId(HA_FLOW_ID_1).pathId(PATH_ID_1).vlan(0).build();
            transitVlanRepository.add(transitVlan);
        }

        if (addSubFlows) {
            final Set<HaSubFlow> haSubFlows = new HashSet<>();
            final List<FlowPath> forwardSubPaths = new ArrayList<>();
            final List<FlowPath> reverseSubPaths = new ArrayList<>();

            final HaFlowPath pathA = createHaFlowPath(PATH_ID_1, COOKIE_1, sharedSwitch);
            final HaFlowPath pathB = createHaFlowPath(PATH_ID_2, COOKIE_2, sharedSwitch);

            HaSubFlow subFlowA = buildHaSubFlow(SUB_FLOW_ID_A, endpointSwitchA, PORT_1, VLAN_1, 0, DESCRIPTION_1);
            haSubFlows.add(subFlowA);
            haSubFlowRepository.add(subFlowA);
            forwardSubPaths.add(createPathWithSegments(SUB_PATH_ID_1, pathA, subFlowA,
                    Lists.newArrayList(switch1, switch2, switch3)));
            reverseSubPaths.add(createPathWithSegments(SUB_PATH_ID_3, pathB, subFlowA,
                    Lists.newArrayList(switch3, switch2, switch1)));


            HaSubFlow subFlowB = buildHaSubFlow(SUB_FLOW_ID_B, endpointSwitchB, PORT_2, VLAN_2, 0, DESCRIPTION_2);
            haSubFlows.add(subFlowB);
            haSubFlowRepository.add(subFlowB);
            forwardSubPaths.add(createPathWithSegments(SUB_PATH_ID_2, pathA, subFlowB,
                    Lists.newArrayList(switch1, switch2, switch4)));
            reverseSubPaths.add(createPathWithSegments(SUB_PATH_ID_4, pathB, subFlowB,
                    Lists.newArrayList(switch4, switch2, switch1)));

            haFlow.setHaSubFlows(haSubFlows);

            pathA.setSubPaths(forwardSubPaths);
            pathA.setHaSubFlows(haSubFlows);
            haFlow.setForwardPath(pathA);

            pathB.setSubPaths(reverseSubPaths);
            pathB.setHaSubFlows(haSubFlows);
            haFlow.setReversePath(pathB);
        }

        haFlowRepository.add(haFlow);
    }

    private void sendSpeakerAnswer(CommandMessage command) {
        sendSpeakerToKildaPingResponse(generateInfoMessageFromCommandMessage(command));
    }

    private InfoMessage generateInfoMessageFromCommandMessage(CommandMessage commandMessage) {
        PingMeters meters = new PingMeters(1, 2, 3);
        UUID pingId = ((PingRequest) commandMessage.getData()).getPingId();
        PingResponse response = new PingResponse(System.currentTimeMillis(), pingId, meters);
        return new InfoMessage(response, System.currentTimeMillis(), commandMessage.getCorrelationId());
    }

    private void sendSpeakerToKildaPingResponse(InfoMessage message) {
        sendMessage(message, pingTopologyConfig.getKafkaPingTopic());
    }

    private void sendNorthboundHaFlowPingCommand(String haFlowId, long timeout) {
        CommandMessage command = new CommandMessage(new HaFlowPingRequest(haFlowId, timeout),
                System.currentTimeMillis(), UUID.randomUUID().toString());
        sendMessage(command, pingTopologyConfig.getKafkaPingTopic());
    }

    private void sendMessage(Object object, String topic) {
        String request = null;
        try {
            request = Utils.MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            fail("Unexpected error: " + e.getMessage());
        }
        kProducer.pushMessageAsync(topic, request);
    }

    private static Switch createTestSwitch(SwitchId switchId) {
        Switch sw = Switch.builder().switchId(switchId).build();
        switchRepository.add(sw);
        return sw;
    }

    private HaFlowPath createHaFlowPath(PathId pathId, FlowSegmentCookie cookie, Switch sharedSwitch) {
        HaFlowPath path = buildHaFlowPath(pathId, BANDWIDTH_1, cookie, METER_ID_1, METER_ID_2, sharedSwitch,
                SWITCH_ID_2, GROUP_ID_1);
        haFlowPathRepository.add(path);
        return path;
    }

    private FlowPath createPathWithSegments(
            PathId pathId, HaFlowPath haFlowPath, HaSubFlow haSubFlow, List<Switch> switches) {
        FlowPath path = buildPath(pathId, haFlowPath, switches.get(0), switches.get(switches.size() - 1));
        flowPathRepository.add(path);
        path.setSegments(buildSegments(path.getPathId(), switches));
        path.setHaSubFlow(haSubFlow);
        return path;
    }

}
