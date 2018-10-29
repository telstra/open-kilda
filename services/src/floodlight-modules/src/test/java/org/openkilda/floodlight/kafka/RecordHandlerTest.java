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

package org.openkilda.floodlight.kafka;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;

import org.openkilda.config.KafkaTopicsConfig;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.service.kafka.KafkaProducerService;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;
import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.discovery.NetworkCommandData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.NetworkDumpSwitchData;
import org.openkilda.messaging.model.SwitchId;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.OFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.Map;

public class RecordHandlerTest extends EasyMockSupport {
    private static final String KAFKA_ISL_DISCOVERY_TOPIC = "kilda.topo.disco";
    private static final String KAFKA_FLOW_TOPIC = "kilda.flow";
    private static final String KAFKA_NORTHBOUND_TOPIC = "kilda.northbound";

    private static final FloodlightModuleContext context = new FloodlightModuleContext();

    private ISwitchManager switchManager;
    private KafkaProducerService producerService;
    private ConsumerContext consumerContext;
    private RecordHandlerMock handler;

    @Before
    public void setUp() throws Exception {
        switchManager = createStrictMock(SwitchManager.class);
        producerService = createMock(KafkaProducerService.class);

        context.addService(IKafkaProducerService.class, producerService);
        context.addService(ISwitchManager.class, switchManager);

        KafkaMessageCollector collectorModule = new KafkaMessageCollector();
        context.addConfigParam(collectorModule, "bootstrap-servers", "test.local");

        KafkaUtilityService kafkaUtility = createMock(KafkaUtilityService.class);
        KafkaTopicsConfig topics = createMock(KafkaTopicsConfig.class);
        expect(topics.getTopoDiscoTopic()).andReturn(KAFKA_ISL_DISCOVERY_TOPIC).anyTimes();
        expect(topics.getFlowTopic()).andReturn(KAFKA_FLOW_TOPIC).anyTimes();
        expect(topics.getNorthboundTopic()).andReturn(KAFKA_NORTHBOUND_TOPIC).anyTimes();
        expect(kafkaUtility.getTopics()).andReturn(topics).anyTimes();

        context.addService(KafkaUtilityService.class, kafkaUtility);

        replay(kafkaUtility);

        consumerContext = new ConsumerContext(context);
        handler = new RecordHandlerMock(consumerContext);

        reset(kafkaUtility);
    }

    /**
     * Simple TDD test that was used to develop warming mechanism for OFELinkBolt. We create
     * command and put it to KafkaMessageCollector then mock ISwitchManager::getAllSwitchMap and
     * verify that output message comes to producerService.
     */
    @Test
    public void networkDumpTest() {
        // Cook mock data for ISwitchManager::getAllSwitchMap
        // Two switches with two ports on each

        // switches for ISwitchManager::getAllSwitchMap
        OFSwitch iofSwitch1 = mock(OFSwitch.class);
        OFSwitch iofSwitch2 = mock(OFSwitch.class);

        Map<DatapathId, IOFSwitch> switches = ImmutableMap.of(
                DatapathId.of(1), iofSwitch1,
                DatapathId.of(2), iofSwitch2
        );

        for (DatapathId swId : switches.keySet()) {
            IOFSwitch sw = switches.get(swId);
            expect(sw.isActive()).andReturn(true).anyTimes();
            expect(sw.getId()).andReturn(swId).anyTimes();
        }

        expect(switchManager.getAllSwitchMap()).andReturn(switches);

        // ports for OFSwitch::getEnabledPorts
        OFPortDesc ofPortDesc1 = mock(OFPortDesc.class);
        OFPortDesc ofPortDesc2 = mock(OFPortDesc.class);
        OFPortDesc ofPortDesc3 = mock(OFPortDesc.class);
        OFPortDesc ofPortDesc4 = mock(OFPortDesc.class);
        OFPortDesc ofPortDesc5 = mock(OFPortDesc.class);

        expect(ofPortDesc1.getPortNo()).andReturn(OFPort.ofInt(1)).times(2);
        expect(ofPortDesc2.getPortNo()).andReturn(OFPort.ofInt(2)).times(2);
        expect(ofPortDesc3.getPortNo()).andReturn(OFPort.ofInt(3)).times(2);
        expect(ofPortDesc4.getPortNo()).andReturn(OFPort.ofInt(4)).times(2);
        // we don't want disco on -2 port
        expect(ofPortDesc5.getPortNo()).andReturn(OFPort.ofInt(-2)).times(2);


        expect(iofSwitch1.getEnabledPorts()).andReturn(ImmutableList.of(
                ofPortDesc1,
                ofPortDesc2
        ));
        expect(iofSwitch2.getEnabledPorts()).andReturn(ImmutableList.of(
                ofPortDesc3,
                ofPortDesc4,
                ofPortDesc5
        ));

        // Logic in SwitchEventCollector.buildSwitchInfoData is too complicated and requires a lot
        // of mocking code so I replaced it with mock on kafkaMessageCollector.buildSwitchInfoData
        handler.overrideNetworkDumpSwitchData(
                DatapathId.of(1),
                new NetworkDumpSwitchData(new SwitchId("ff:01")));
        handler.overrideNetworkDumpSwitchData(
                DatapathId.of(2),
                new NetworkDumpSwitchData(new SwitchId("ff:02")));

        // setup hook for verify that we create new message for producerService
        producerService.sendMessageAndTrack(eq(KAFKA_ISL_DISCOVERY_TOPIC), anyObject(InfoMessage.class));
        expectLastCall().times(8);

        producerService.enableGuaranteedOrder(eq(KAFKA_ISL_DISCOVERY_TOPIC));
        producerService.disableGuaranteedOrder(eq(KAFKA_ISL_DISCOVERY_TOPIC));

        replayAll();

        // Create CommandMessage with NetworkCommandData for trigger network dump
        CommandMessage command = new CommandMessage(new NetworkCommandData(),
                System.currentTimeMillis(), Utils.SYSTEM_CORRELATION_ID,
                Destination.CONTROLLER);


        // KafkaMessageCollector contains a complicated run logic with couple nested private
        // classes, threading and that is very painful for writing clear looking test code so I
        // created the simple method in KafkaMessageCollector for simplifying test logic.
        handler.handleMessage(command);

        verify(producerService);

        // TODO: verify content of InfoMessage in producerService.sendMessageAndTrack
    }

}
