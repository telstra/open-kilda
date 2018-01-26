package org.openkilda.floodlight.kafka;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.verify;

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
    private static final String OUTPUT_DISCO_TOPIC = Topic.TOPO_DISCO;
    private static final FloodlightModuleContext context = new FloodlightModuleContext();

    private ISwitchManager switchManager;
    private KafkaMessageProducer producer;
    private ConsumerContext consumerContext;
    private RecordHandlerMock handler;

    @Before
    public void setUp() throws Exception {
        switchManager = createStrictMock(SwitchManager.class);
        producer = createMock(KafkaMessageProducer.class);

        context.addService(KafkaMessageProducer.class, producer);
        context.addService(ISwitchManager.class, switchManager);

        KafkaMessageCollector collectorModule = new KafkaMessageCollector();
        context.addConfigParam(collectorModule, "topic", "");
        context.addConfigParam(collectorModule, "bootstrap-servers", "");
        collectorModule.init(context);

        consumerContext = new ConsumerContext(context, collectorModule);

        handler = new RecordHandlerMock(consumerContext);
    }

    /**
     * Simple TDD test that was used to develop warming mechanism for OFELinkBolt. We create
     * command and put it to KafkaMessageCollector then mock ISwitchManager::getAllSwitchMap and
     * verify that output message comes to producer.
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
        handler.overrideSwitchInfoData(
                DatapathId.of(1),
                new SwitchInfoData("sw1", SwitchState.ADDED, "127.0.0.1", "localhost", "test switch", "kilda"));
        handler.overrideSwitchInfoData(
                DatapathId.of(2),
                new SwitchInfoData("sw2", SwitchState.ADDED, "127.0.0.1", "localhost", "test switch", "kilda"));

        // setup hook for verify that we create new message for producer
        producer.postMessage(eq(OUTPUT_DISCO_TOPIC), anyObject(InfoMessage.class));

        replayAll();

        // Create CommandMessage with NetworkCommandData for trigger network dump
        CommandMessage command = new CommandMessage(new NetworkCommandData(),
                System.currentTimeMillis(), Utils.SYSTEM_CORRELATION_ID,
                Destination.CONTROLLER);

        // KafkaMessageCollector contains a complicated run logic with couple nested private
        // classes, threading and that is very painful for writing clear looking test code so I
        // created the simple method in KafkaMessageCollector for simplifying test logic.
        handler.handleMessage(command);

        verify(producer);

        // TODO: verify content of InfoMessage in producer.postMessage
    }
}
