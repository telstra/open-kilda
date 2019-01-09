/* Copyright 2018 Telstra Open Source
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

package org.openkilda.floodlight.switchmanager;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.newCapture;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.openkilda.config.KafkaTopicsConfig;
import org.openkilda.floodlight.error.SwitchNotFoundException;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.NetworkDumpBeginMarker;
import org.openkilda.messaging.info.discovery.NetworkDumpEndMarker;
import org.openkilda.messaging.info.discovery.NetworkDumpPortData;
import org.openkilda.messaging.info.discovery.NetworkDumpSwitchData;
import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.model.SwitchId;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.LogicalOFMessageCategory;
import net.floodlightcontroller.core.SwitchDescription;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.internal.OFConnection;
import net.floodlightcontroller.core.internal.OFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMockSupport;
import org.easymock.IAnswer;
import org.easymock.Mock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SwitchTrackingServiceTest extends EasyMockSupport {
    private static final String KAFKA_ISL_DISCOVERY_TOPIC = "kilda.topo.disco";
    private static final DatapathId dpId = DatapathId.of(0x7fff);

    private final SwitchTrackingService service = new SwitchTrackingService();

    private final FloodlightModuleContext moduleContext = new FloodlightModuleContext();

    @Mock
    private SwitchManager switchManager;

    @Mock
    private IKafkaProducerService producerService;

    @Before
    public void setUp() throws Exception {
        injectMocks(this);

        moduleContext.addService(ISwitchManager.class, switchManager);
        moduleContext.addService(IKafkaProducerService.class, producerService);

        IOFSwitchService iofSwitchService = createMock(IOFSwitchService.class);
        iofSwitchService.addOFSwitchListener(eq(service));
        moduleContext.addService(IOFSwitchService.class, iofSwitchService);

        KafkaTopicsConfig topics = createMock(KafkaTopicsConfig.class);
        expect(topics.getTopoDiscoTopic()).andReturn(KAFKA_ISL_DISCOVERY_TOPIC);

        KafkaUtilityService kafkaUtility = createMock(KafkaUtilityService.class);
        expect(kafkaUtility.getTopics()).andReturn(topics);
        moduleContext.addService(KafkaUtilityService.class, kafkaUtility);

        replay(kafkaUtility, topics, iofSwitchService);
        service.setup(moduleContext);
        verify(iofSwitchService);

        reset(kafkaUtility, topics, iofSwitchService);
    }

    @After
    public void tearDown() {
        verifyAll();
    }

    @Test
    public void switchAdded() throws Exception {
        Capture<Message> producedMessage = prepareSwitchEvent();
        replayAll();
        service.switchAdded(dpId);
        verifySwitchEvent(SwitchChangeType.ADDED, producedMessage);
    }

    @Test
    public void switchAddedMissing() throws Exception {
        Capture<Message> producedMessage = prepareMissingSwitchEvent();
        replayAll();
        service.switchAdded(dpId);
        verifySwitchEvent(SwitchChangeType.ADDED, producedMessage);
    }

    @Test
    public void switchRemoved() {
        Capture<Message> producedMessage = prepareSwitchEventCommon();
        switchManager.deactivate(eq(dpId));
        replayAll();
        service.switchRemoved(dpId);
        verifySwitchEvent(SwitchChangeType.REMOVED, producedMessage);
    }

    @Test
    public void switchActivated() throws Exception {
        switchActivatedTest(prepareSwitchEvent());
    }

    @Test
    public void switchActivatedMissing() throws Exception {
        switchActivatedTest(prepareMissingSwitchEvent());
    }

    private void switchActivatedTest(Capture<Message> producedMessage) throws Exception {
        switchManager.activate(dpId);
        expectLastCall().andAnswer(new IAnswer<Object>() {
            @Override
            public Object answer() throws Throwable {
                service.completeSwitchActivation((DatapathId) getCurrentArguments()[0]);
                return null;
            }
        });

        OFPortDesc ofPortDesc1 = mock(OFPortDesc.class);
        OFPortDesc ofPortDesc2 = mock(OFPortDesc.class);
        expect(ofPortDesc1.getPortNo()).andReturn(OFPort.ofInt(1));
        expect(ofPortDesc2.getPortNo()).andReturn(OFPort.ofInt(2));
        expect(switchManager.getEnabledPhysicalPorts(eq(dpId))).andReturn(ImmutableList.of(
                ofPortDesc1,
                ofPortDesc2
        ));

        replayAll();

        service.switchActivated(dpId);
        verifySwitchEvent(SwitchChangeType.ACTIVATED, producedMessage);

        List<InfoData> actualProducedMessages = producedMessage.getValues().stream()
                .skip(1)
                .map(entry -> ((InfoMessage) entry).getData())
                .collect(Collectors.toList());
        List<InfoData> expectProducedMessages = new ArrayList<>();
        expectProducedMessages.add(new PortInfoData(new SwitchId(dpId.getLong()), 1, PortChangeType.UP));
        expectProducedMessages.add(new PortInfoData(new SwitchId(dpId.getLong()), 2, PortChangeType.UP));
        assertEquals(expectProducedMessages, actualProducedMessages);
    }

    @Test
    public void switchDeactivated() {
        Capture<Message> producedMessage = prepareSwitchEventCommon();
        switchManager.deactivate(eq(dpId));
        replayAll();
        service.switchDeactivated(dpId);
        verifySwitchEvent(SwitchChangeType.DEACTIVATED, producedMessage);
    }

    @Test
    public void switchChanged() throws Exception {
        Capture<Message> producedMessage = prepareSwitchEvent();
        replayAll();
        service.switchChanged(dpId);
        verifySwitchEvent(SwitchChangeType.CHANGED, producedMessage);
    }

    @Test
    public void switchChangedMissing() throws Exception {
        Capture<Message> producedMessage = prepareMissingSwitchEvent();
        replayAll();
        service.switchChanged(dpId);
        verifySwitchEvent(SwitchChangeType.CHANGED, producedMessage);
    }

    private Capture<Message> prepareSwitchEvent() throws Exception {
        IOFSwitch sw = createMock(IOFSwitch.class);
        expect(sw.getId()).andReturn(dpId).anyTimes();
        expect(sw.getInetAddress())
                .andReturn(new InetSocketAddress(InetAddress.getByName("127.0.1.1"), 6653));

        OFConnection connect = createMock(OFConnection.class);
        expect(connect.getRemoteInetAddress())
                .andReturn(new InetSocketAddress(InetAddress.getByName("127.0.1.2"), 32769));
        expect(sw.getConnectionByCategory(eq(LogicalOFMessageCategory.MAIN))).andReturn(connect);

        SwitchDescription description = createMock(SwitchDescription.class);
        expect(description.getManufacturerDescription()).andReturn("(mock) getManufacturerDescription()");
        expect(description.getSoftwareDescription()).andReturn("(mock) getSoftwareDescription()");
        expect(sw.getSwitchDescription()).andReturn(description).times(2);

        expect(sw.getOFFactory()).andReturn(new OFFactoryVer13());

        expect(switchManager.lookupSwitch(eq(dpId))).andReturn(sw);

        return prepareSwitchEventCommon();
    }

    private Capture<Message> prepareMissingSwitchEvent() throws Exception {
        expect(switchManager.lookupSwitch(eq(dpId))).andThrow(new SwitchNotFoundException(dpId));
        return prepareSwitchEventCommon();
    }

    private Capture<Message> prepareSwitchEventCommon() {
        Capture<Message> producedMessage = newCapture(CaptureType.ALL);
        producerService.sendMessageAndTrack(eq(KAFKA_ISL_DISCOVERY_TOPIC), eq(dpId.toString()),
                capture(producedMessage));
        expectLastCall().anyTimes();

        return producedMessage;
    }

    private void verifySwitchEvent(SwitchChangeType expectedState, Capture<Message> producedMessage) {
        assertTrue(producedMessage.hasCaptured());

        Message message = producedMessage.getValues().get(0);
        assertTrue(message instanceof InfoMessage);

        InfoData data = ((InfoMessage) message).getData();
        assertTrue(data instanceof SwitchInfoData);

        SwitchInfoData switchInfo = (SwitchInfoData) data;
        assertEquals(new SwitchId(dpId.getLong()), switchInfo.getSwitchId());
        assertEquals(expectedState, switchInfo.getState());
    }

    @Test
    public void networkDumpTest() throws Exception {
        // Cook mock data for ISwitchManager::getAllSwitchMap
        // Two switches with two ports on each

        // switches for ISwitchManager::getAllSwitchMap
        OFSwitch iofSwitch1 = mock(OFSwitch.class);
        OFSwitch iofSwitch2 = mock(OFSwitch.class);

        final DatapathId swAid = DatapathId.of(1);
        final DatapathId swBid = DatapathId.of(2);
        Map<DatapathId, IOFSwitch> switches = ImmutableMap.of(
                swAid, iofSwitch1,
                swBid, iofSwitch2
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

        expect(ofPortDesc1.getPortNo()).andReturn(OFPort.ofInt(1));
        expect(ofPortDesc2.getPortNo()).andReturn(OFPort.ofInt(2));
        expect(ofPortDesc3.getPortNo()).andReturn(OFPort.ofInt(3));
        expect(ofPortDesc4.getPortNo()).andReturn(OFPort.ofInt(4));
        // we don't want disco on -2 port
        // expect(ofPortDesc5.getPortNo()).andReturn(OFPort.ofInt(-2));
        expect(ofPortDesc5.getPortNo()).andReturn(OFPort.ofInt(5));

        expect(switchManager.getEnabledPhysicalPorts(eq(swAid))).andReturn(ImmutableList.of(
                ofPortDesc1,
                ofPortDesc2
        ));
        expect(switchManager.getEnabledPhysicalPorts(eq(swBid))).andReturn(ImmutableList.of(
                ofPortDesc3,
                ofPortDesc4,
                ofPortDesc5
        ));

        ArrayList<Message> producedMessages = new ArrayList<>();
        // setup hook for verify that we create new message for producer
        producerService.sendMessageAndTrack(eq(KAFKA_ISL_DISCOVERY_TOPIC), anyObject(InfoMessage.class));
        expectLastCall().andAnswer(new IAnswer<Object>() {
            @Override
            public Object answer() {
                Message sentMessage = (Message) getCurrentArguments()[1];
                sentMessage.setTimestamp(0);
                producedMessages.add(sentMessage);
                return null;
            }
        }).anyTimes();

        producerService.enableGuaranteedOrder(eq(KAFKA_ISL_DISCOVERY_TOPIC));
        producerService.disableGuaranteedOrder(eq(KAFKA_ISL_DISCOVERY_TOPIC));

        replayAll();

        String correlationId = "unit-test-correlation-id";
        service.dumpAllSwitches(correlationId);

        verify(producerService);

        ArrayList<Message> expectedMessages = new ArrayList<>();
        expectedMessages.add(new InfoMessage(new NetworkDumpBeginMarker(), 0, correlationId));
        expectedMessages.add(new InfoMessage(
                new NetworkDumpSwitchData(new SwitchId(swAid.getLong())), 0, correlationId));
        expectedMessages.add(new InfoMessage(
                new NetworkDumpPortData(new SwitchId(swAid.getLong()), 1), 0, correlationId));
        expectedMessages.add(new InfoMessage(
                new NetworkDumpPortData(new SwitchId(swAid.getLong()), 2), 0, correlationId));
        expectedMessages.add(new InfoMessage(
                new NetworkDumpSwitchData(new SwitchId(swBid.getLong())), 0, correlationId));
        expectedMessages.add(new InfoMessage(
                new NetworkDumpPortData(new SwitchId(swBid.getLong()), 3), 0, correlationId));
        expectedMessages.add(new InfoMessage(
                new NetworkDumpPortData(new SwitchId(swBid.getLong()), 4), 0, correlationId));
        expectedMessages.add(new InfoMessage(
                new NetworkDumpPortData(new SwitchId(swBid.getLong()), 5), 0, correlationId));
        expectedMessages.add(new InfoMessage(new NetworkDumpEndMarker(), 0, correlationId));

        assertEquals(expectedMessages, producedMessages);
    }
}
