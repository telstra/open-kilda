package org.openkilda.simulator.bolts;

import static org.hamcrest.CoreMatchers.instanceOf;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.storm.tuple.Values;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.simulator.classes.IPortImpl;
import org.openkilda.simulator.classes.ISwitchImpl;
import org.openkilda.simulator.messages.LinkMessage;
import org.openkilda.simulator.messages.SwitchMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class SpeakerBoltTest {
    SpeakerBolt speakerBolt;
    String dpid = "00:00:00:00:00:01";
    int numOfPorts = 10;
    int linkLatency = 10;
    int localLinkPort = 1;
    List<LinkMessage> links = new ArrayList<>();
    String peerSwitch = "00:00:00:00:00:05";
    int peerPort = 1;
    LinkMessage link;
    SwitchMessage switchMessage;
    ObjectMapper mapper;

    @Rule
    public ExpectedException thrown = ExpectedException.none();


    @Before
    public void setUp() throws Exception {
        mapper = new ObjectMapper();
        speakerBolt = new SpeakerBolt();
        speakerBolt.prepare(null, null, null);

        link = new LinkMessage(linkLatency, localLinkPort, peerSwitch, peerPort);
        links.add(link);

        switchMessage = new SwitchMessage(dpid, numOfPorts, links);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void addSwitch() throws Exception {
        speakerBolt.addSwitch(switchMessage);
        assertEquals(1, speakerBolt.switches.size());

        ISwitchImpl sw = speakerBolt.switches.get("00:00:" + dpid);
        assertTrue(sw.isActive());

        List<IPortImpl> ports = sw.getPorts();
        assertEquals(numOfPorts, ports.size());
        for (IPortImpl port : ports) {
            if (port.getNumber() != localLinkPort) {
                assertFalse(port.isActive());
                assertFalse(port.isActiveIsl());
            } else {
                assertTrue(port.isActive());
                assertTrue(port.isActiveIsl());
            }
        }
    }

    @Test
    public void testAddSwitchValues() throws Exception {
        List<Values> values = speakerBolt.addSwitch(switchMessage);

        assertEquals(3, values.size());
        int count = 0;
        for (Values value : values) {
            InfoMessage infoMessage = mapper.readValue((String) value.get(1), InfoMessage.class);
            if (count < 2) {
                assertThat(infoMessage.getData(), instanceOf(SwitchInfoData.class));
                SwitchInfoData sw = (SwitchInfoData) infoMessage.getData();
                assertEquals("00:00:" + dpid, sw.getSwitchId());
            } else {
                assertThat(infoMessage.getData(), instanceOf(PortInfoData.class));
                PortInfoData port = (PortInfoData) infoMessage.getData();
                assertEquals("00:00:" + dpid, port.getSwitchId());
                if (port.getPortNo() == localLinkPort) {
                    assertEquals(PortChangeType.UP, port.getState());
                } else {
                    assertEquals(PortChangeType.DOWN, port.getState());
                }
            }
            count++;
        }
    }
}