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
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.niceMock;
import static org.easymock.EasyMock.partialMockBuilder;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.OFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.junit.Before;
import org.junit.Test;
import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Topic;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.discovery.NetworkCommandData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.Map;

/**
 * Created by jonv on 20/3/17.
 */
public class KafkaMessageCollectorTest {

    private static final String OUTPUT_DISCO_TOPIC = Topic.TOPO_DISCO;
    private KafkaMessageCollector kafkaMessageCollector;
    private static final FloodlightModuleContext context = new FloodlightModuleContext();

    private ISwitchManager switchManager;
    private KafkaMessageProducer producer;

    @Before
    public void setUp() throws Exception {

        switchManager = createMock(SwitchManager.class);
        producer = createMock(KafkaMessageProducer.class);

        context.addService(KafkaMessageProducer.class, producer);
        context.addService(ISwitchManager.class, switchManager);

        kafkaMessageCollector = partialMockBuilder(KafkaMessageCollector.class)
                .addMockedMethod("buildSwitchInfoData").createMock();

        context.addConfigParam(kafkaMessageCollector, "topic", "");
        context.addConfigParam(kafkaMessageCollector, "bootstrap-servers", "");
        kafkaMessageCollector.init(context);
    }

    @Test
    public void testCreateFlowCommand() {
        // TODO: verify aproppriate switchManager methods run
    }

    /**
     * Simple TDD test that was used to develop warming mechanism for OFELinkBolt. We create
     * command and put it to KafkaMessageCollector then mock ISwitchManager::getAllSwitchMap and
     * verify that output message comes to producer.
     */
    @Test
    public void networkDumpTest() {

        // Create CommandMessage with NetworkCommandData for trigger network dump
        CommandMessage command = new CommandMessage(new NetworkCommandData(),
                System.currentTimeMillis(), Utils.SYSTEM_CORRELATION_ID,
                Destination.CONTROLLER);

        // Cook mock data for ISwitchManager::getAllSwitchMap
        // Two switches with two ports on each

        // switches for ISwitchManager::getAllSwitchMap
        OFSwitch iofSwitch1 = niceMock(OFSwitch.class);
        OFSwitch iofSwitch2 = niceMock(OFSwitch.class);

        expect(iofSwitch1.getId()).andReturn(DatapathId.of(1)).anyTimes();
        expect(iofSwitch2.getId()).andReturn(DatapathId.of(2)).anyTimes();

        Map<DatapathId, IOFSwitch> switches = ImmutableMap.of(
                DatapathId.of(1), iofSwitch1,
                DatapathId.of(2), iofSwitch2
        );

        expect(switchManager.getAllSwitchMap()).andReturn(switches);

        // ports for OFSwitch::getEnabledPorts
        OFPortDesc ofPortDesc1 = niceMock(OFPortDesc.class);
        OFPortDesc ofPortDesc2 = niceMock(OFPortDesc.class);
        OFPortDesc ofPortDesc3 = niceMock(OFPortDesc.class);
        OFPortDesc ofPortDesc4 = niceMock(OFPortDesc.class);

        expect(ofPortDesc1.getPortNo()).andReturn(OFPort.ofInt(1));
        expect(ofPortDesc2.getPortNo()).andReturn(OFPort.ofInt(2));
        expect(ofPortDesc3.getPortNo()).andReturn(OFPort.ofInt(3));
        expect(ofPortDesc4.getPortNo()).andReturn(OFPort.ofInt(4));

        expect(iofSwitch1.getEnabledPorts()).andReturn(ImmutableList.of(
                ofPortDesc1,
                ofPortDesc2
        ));
        expect(iofSwitch2.getEnabledPorts()).andReturn(ImmutableList.of(
                ofPortDesc3,
                ofPortDesc4
        ));

        // Logic in SwitchEventCollector.buildSwitchInfoData is too complicated and requires a lot
        // of mocking code so I replaced it with mock on kafkaMessageCollector.buildSwitchInfoData
        SwitchInfoData sw1 = new SwitchInfoData("sw1", SwitchState.ADDED, "127.0.0.1", "localhost",
                "test switch", "kilda");
        SwitchInfoData sw2 = new SwitchInfoData("sw2", SwitchState.ADDED, "127.0.0.1", "localhost",
                "test switch", "kilda");
        expect(kafkaMessageCollector.buildSwitchInfoData(anyObject())).andReturn(sw1).andReturn(sw2);

        // setup hook for verify that we create new message for producer
        producer.postMessage(eq(OUTPUT_DISCO_TOPIC), anyObject(InfoMessage.class));

        replay(ofPortDesc1);
        replay(ofPortDesc2);
        replay(ofPortDesc3);
        replay(ofPortDesc4);
        replay(kafkaMessageCollector);
        replay(switchManager);
        replay(iofSwitch1);
        replay(iofSwitch2);
        replay(producer);

        // KafkaMessageCollector contains a complicated run logic with couple nested private
        // classes, threading and that is very painful for writing clear looking test code so I
        // created the simple method in KafkaMessageCollector for simplifying test logic.
        kafkaMessageCollector.processTestControllerMsg(command);

        verify(producer);

        // TODO: verify content of InfoMessage in producer.postMessage
    }
}
