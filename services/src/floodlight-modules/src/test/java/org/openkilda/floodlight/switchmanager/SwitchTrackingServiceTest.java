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

import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.floodlight.error.SwitchNotFoundException;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.ChunkedInfoMessage;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.NetworkDumpSwitchData;
import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.model.Switch;
import org.openkilda.messaging.model.SwitchPort;
import org.openkilda.model.SwitchId;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
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

import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SwitchTrackingServiceTest extends EasyMockSupport {
    private static final String KAFKA_ISL_DISCOVERY_TOPIC = "kilda.topo.disco";
    private static final DatapathId dpId = DatapathId.of(0x7fff);
    private static String switchIpAddress;
    private static final Set<Switch.Feature> switchFeatures = Collections.singleton(Switch.Feature.METERS);

    private final SwitchTrackingService service = new SwitchTrackingService();

    private final FloodlightModuleContext moduleContext = new FloodlightModuleContext();

    @Mock
    private SwitchManager switchManager;

    @Mock
    private FeatureDetectorService featureDetector;

    @Mock
    private IKafkaProducerService producerService;

    @Before
    public void setUp() throws Exception {
        injectMocks(this);

        switchIpAddress = "127.0.1.1";

        moduleContext.addService(ISwitchManager.class, switchManager);
        moduleContext.addService(FeatureDetectorService.class, featureDetector);
        moduleContext.addService(IKafkaProducerService.class, producerService);

        IOFSwitchService iofSwitchService = createMock(IOFSwitchService.class);
        iofSwitchService.addOFSwitchListener(eq(service));
        moduleContext.addService(IOFSwitchService.class, iofSwitchService);

        KafkaChannel topics = createMock(KafkaChannel.class);
        expect(topics.getTopoDiscoTopic()).andReturn(KAFKA_ISL_DISCOVERY_TOPIC);
        expect(topics.getRegion()).andReturn("1");
        KafkaUtilityService kafkaUtility = createMock(KafkaUtilityService.class);
        expect(kafkaUtility.getKafkaChannel()).andReturn(topics);
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
        Switch expectedSwitchRecord = makeSwitchRecord(true, true);
        Capture<Message> producedMessage = prepareAliveSwitchEvent(expectedSwitchRecord);
        replayAll();
        service.switchAdded(dpId);
        verifySwitchEvent(SwitchChangeType.ADDED, expectedSwitchRecord, producedMessage);
    }

    @Test
    public void switchAddedMissing() throws Exception {
        Capture<Message> producedMessage = prepareRemovedSwitchEvent();
        replayAll();
        service.switchAdded(dpId);
        verifySwitchEvent(SwitchChangeType.ADDED, null, producedMessage);
    }

    @Test
    public void switchRemoved() {
        Capture<Message> producedMessage = prepareSwitchEventCommon(dpId);
        switchManager.deactivate(eq(dpId));
        replayAll();
        service.switchRemoved(dpId);

        verifySwitchEvent(SwitchChangeType.REMOVED, null, producedMessage);
    }

    @Test
    public void switchActivate() throws Exception {
        Switch expectedSwitchRecord = makeSwitchRecord(true, true);
        switchActivateTest(prepareAliveSwitchEvent(expectedSwitchRecord), expectedSwitchRecord);
    }

    @Test
    public void switchActivateMissing() throws Exception {
        switchActivateTest(prepareRemovedSwitchEvent(), null);
    }

    private void switchActivateTest(Capture<Message> producedMessage, Switch expectedSwitchRecord) throws Exception {
        switchManager.activate(dpId);
        expectLastCall().andAnswer(new IAnswer<Object>() {
            @Override
            public Object answer() throws Throwable {
                service.completeSwitchActivation((DatapathId) getCurrentArguments()[0]);
                return null;
            }
        });

        replayAll();

        service.switchActivated(dpId);
        verifySwitchEvent(SwitchChangeType.ACTIVATED, expectedSwitchRecord, producedMessage);
        assertEquals(1, producedMessage.getValues().size());
    }

    @Test
    public void switchDeactivated() {
        Capture<Message> producedMessage = prepareSwitchEventCommon(dpId);
        switchManager.deactivate(eq(dpId));
        replayAll();
        service.switchDeactivated(dpId);
        verifySwitchEvent(SwitchChangeType.DEACTIVATED, null, producedMessage);
    }

    @Test
    public void switchChanged() throws Exception {
        Switch expectedSwitchRecord = makeSwitchRecord(true, true);
        Capture<Message> producedMessage = prepareAliveSwitchEvent(expectedSwitchRecord);
        replayAll();
        service.switchChanged(dpId);
        verifySwitchEvent(SwitchChangeType.CHANGED, expectedSwitchRecord, producedMessage);
    }

    @Test
    public void switchChangedMissing() throws Exception {
        Capture<Message> producedMessage = prepareRemovedSwitchEvent();
        replayAll();
        service.switchChanged(dpId);
        verifySwitchEvent(SwitchChangeType.CHANGED, null, producedMessage);
    }

    private Capture<Message> prepareAliveSwitchEvent(Switch switchRecord) throws Exception {
        IOFSwitch sw = createMock(IOFSwitch.class);

        expect(sw.getId()).andReturn(dpId).anyTimes();
        expect(sw.getInetAddress())
                .andReturn(new InetSocketAddress(switchIpAddress, 32769));

        OFConnection connect = createMock(OFConnection.class);
        expect(connect.getRemoteInetAddress())
                .andReturn(new InetSocketAddress(Inet4Address.getByName("127.0.1.1"), 6653));
        expect(sw.getConnectionByCategory(eq(LogicalOFMessageCategory.MAIN))).andReturn(connect);

        SwitchDescription description = createMock(SwitchDescription.class);
        expect(description.getManufacturerDescription()).andReturn("(mock) getManufacturerDescription()");
        expect(description.getSoftwareDescription()).andReturn("(mock) getSoftwareDescription()");
        expect(sw.getSwitchDescription()).andReturn(description).times(2);

        expect(sw.getOFFactory()).andReturn(new OFFactoryVer13());

        expect(switchManager.lookupSwitch(eq(dpId))).andReturn(sw);

        List<OFPortDesc> physicalPorts = new ArrayList<>(switchRecord.getPorts().size());
        int idx = 1;
        for (SwitchPort port : switchRecord.getPorts()) {
            physicalPorts.add(makePhysicalPortMock(idx++, port.getState() == SwitchPort.State.UP));
        }
        expect(switchManager.getPhysicalPorts(sw)).andReturn(physicalPorts);

        expect(switchManager.getSwitchIpAddress(sw)).andReturn(switchIpAddress);
        expect(featureDetector.detectSwitch(sw)).andReturn(ImmutableSet.of(Switch.Feature.METERS));

        return prepareSwitchEventCommon(dpId);
    }

    private Capture<Message> prepareRemovedSwitchEvent() throws Exception {
        expect(switchManager.lookupSwitch(eq(dpId))).andThrow(new SwitchNotFoundException(dpId));
        return prepareSwitchEventCommon(dpId);
    }

    private Capture<Message> prepareSwitchEventCommon(DatapathId dpId) {
        Capture<Message> producedMessage = newCapture(CaptureType.ALL);
        producerService.sendMessageAndTrack(eq(KAFKA_ISL_DISCOVERY_TOPIC), eq(dpId.toString()),
                capture(producedMessage));
        expectLastCall().anyTimes();

        return producedMessage;
    }

    private void verifySwitchEvent(SwitchChangeType expectedState, Switch expectedSwitchRecord,
                                   Capture<Message> producedMessage) {
        assertTrue(producedMessage.hasCaptured());

        Message message = producedMessage.getValues().get(0);
        assertTrue(message instanceof InfoMessage);

        InfoData data = ((InfoMessage) message).getData();
        assertTrue(data instanceof SwitchInfoData);

        SwitchInfoData switchInfo = (SwitchInfoData) data;
        assertEquals(new SwitchId(dpId.getLong()), switchInfo.getSwitchId());
        assertEquals(expectedState, switchInfo.getState());

        assertEquals(expectedSwitchRecord, switchInfo.getSwitchRecord());
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

        expect(switchManager.getPhysicalPorts(eq(iofSwitch1))).andReturn(ImmutableList.of(
                makePhysicalPortMock(1, true),
                makePhysicalPortMock(2, true)
        ));
        expect(switchManager.getPhysicalPorts(eq(iofSwitch2))).andReturn(ImmutableList.of(
                makePhysicalPortMock(3, true),
                makePhysicalPortMock(4, true),
                makePhysicalPortMock(5, false)
        ));

        expect(switchManager.getSwitchIpAddress(iofSwitch1)).andReturn("127.0.2.1");
        expect(switchManager.getSwitchIpAddress(iofSwitch2)).andReturn("127.0.2.2");

        expect(featureDetector.detectSwitch(iofSwitch1))
                .andReturn(ImmutableSet.of(Switch.Feature.METERS));
        expect(featureDetector.detectSwitch(iofSwitch2))
                .andReturn(ImmutableSet.of(Switch.Feature.METERS, Switch.Feature.BFD));

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
        expectedMessages.add(new ChunkedInfoMessage(
                new NetworkDumpSwitchData(new Switch(
                        new SwitchId(swAid.getLong()), "127.0.2.1",
                        ImmutableSet.of(Switch.Feature.METERS),
                        ImmutableList.of(
                                new SwitchPort(1, SwitchPort.State.UP),
                                new SwitchPort(2, SwitchPort.State.UP)))), 0, correlationId, 0, 2, "1"));
        expectedMessages.add(new ChunkedInfoMessage(
                new NetworkDumpSwitchData(new Switch(
                        new SwitchId(swBid.getLong()), "127.0.2.2",
                        ImmutableSet.of(Switch.Feature.METERS, Switch.Feature.BFD),
                        ImmutableList.of(
                                new SwitchPort(3, SwitchPort.State.UP),
                                new SwitchPort(4, SwitchPort.State.UP),
                                new SwitchPort(5, SwitchPort.State.DOWN)))), 0, correlationId, 1, 2, "1"));

        assertEquals(expectedMessages, producedMessages);
    }

    private Switch makeSwitchRecord(boolean... portState) {
        return this.makeSwitchRecord(dpId, switchIpAddress, switchFeatures, portState);
    }

    private Switch makeSwitchRecord(DatapathId datapath, String ipAddress, Set<Switch.Feature> features,
                                    boolean... portState) {
        List<SwitchPort> ports = new ArrayList<>(portState.length);
        for (int idx = 0; idx < portState.length; idx++) {
            ports.add(new SwitchPort(idx + 1, portState[idx] ? SwitchPort.State.UP : SwitchPort.State.DOWN));
        }
        return new Switch(new SwitchId(datapath.getLong()), ipAddress, features, ports);
    }

    private OFPortDesc makePhysicalPortMock(int number, boolean isEnabled) {
        OFPortDesc port = createMock(OFPortDesc.class);
        expect(port.getPortNo()).andReturn(OFPort.of(number)).anyTimes();
        expect(port.isEnabled()).andReturn(isEnabled).anyTimes();
        return port;
    }
}
