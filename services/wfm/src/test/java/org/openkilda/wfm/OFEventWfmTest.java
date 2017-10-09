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

package org.openkilda.wfm;

import static org.openkilda.messaging.Utils.MAPPER;
import static org.openkilda.wfm.topology.event.OFEventWFMTopology.DEFAULT_DISCOVERY_INTERVAL;
import static org.openkilda.wfm.topology.event.OFEventWFMTopology.DEFAULT_DISCOVERY_TIMEOUT;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.wfm.topology.OutputCollectorMock;
import org.openkilda.wfm.topology.Topology;
import org.openkilda.wfm.topology.event.OFELinkBolt;
import org.openkilda.wfm.topology.event.OFEventWFMTopology;
import org.openkilda.wfm.topology.splitter.InfoEventSplitterBolt;
import org.openkilda.wfm.topology.utils.KafkaFilerTopology;
import org.openkilda.wfm.topology.utils.LinkTracker;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.apache.storm.Constants;
import org.apache.storm.state.InMemoryKeyValueState;
import org.apache.storm.state.KeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.TupleImpl;
import org.apache.storm.utils.Utils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * OFEventWfmTest tests the critical aspects of OFEventWFMTopology
 */
@RunWith(MockitoJUnitRunner.class)
public class OFEventWfmTest extends AbstractStormTest {
    private long messagesExpected;
    private long messagesReceived;

    @Mock
    private TopologyContext topologyContext;
    private OutputCollectorMock outputCollectorMock = new OutputCollectorMock();
    private OutputCollector outputCollector = new OutputCollector(outputCollectorMock);

    @Test
    public void BasicSwitchPortEventsTest() throws Exception {
        System.out.println("==> Starting BasicSwitchEventTest");
        File file = new File(OFEventWfmTest.class.getResource(Topology.TOPOLOGY_PROPERTIES).getFile());
        OFEventWFMTopology topo = new OFEventWFMTopology(file);
        cluster.submitTopology(topo.getTopologyName(), stormConfig(), topo.createTopology());

        KafkaFilerTopology kafkaFiler = new KafkaFilerTopology(file,
                OFEventWFMTopology.DEFAULT_KAFKA_OUTPUT, server.tempDir.getAbsolutePath());
        cluster.submitTopology("utils-1", stormConfig(), kafkaFiler.createTopology());

        KafkaFilerTopology discoFiler = new KafkaFilerTopology(file,
                OFEventWFMTopology.DEFAULT_DISCOVERY_TOPIC, server.tempDir.getAbsolutePath());
        cluster.submitTopology("utils-2", stormConfig(), discoFiler.createTopology());

        Utils.sleep(5 * 1000);

        String sw1_up = OFEMessageUtils.createSwitchDataMessage(
                OFEMessageUtils.SWITCH_UP, "sw1");
        String sw2_up = OFEMessageUtils.createSwitchDataMessage(
                OFEMessageUtils.SWITCH_UP, "sw2");
        String sw1p1_up = OFEMessageUtils.createPortDataMessage(
                OFEMessageUtils.PORT_UP, "sw1", "1");
        String sw2p2_up = OFEMessageUtils.createPortDataMessage(
                OFEMessageUtils.PORT_UP, "sw2", "2");
        String sw2p2_down = OFEMessageUtils.createPortDataMessage(
                OFEMessageUtils.PORT_DOWN, "sw2", "2");
        String switch_topic = InfoEventSplitterBolt.I_SWITCH_UPDOWN;
        String port_topic = InfoEventSplitterBolt.I_PORT_UPDOWN;

        // send sw1 and sw2 up
        kProducer.pushMessage(switch_topic, sw1_up);
        kProducer.pushMessage(switch_topic, sw2_up);

        // sent sw1/port1 up ... sw2/port2 up
        kProducer.pushMessage(port_topic, sw1p1_up);
        kProducer.pushMessage(port_topic, sw2p2_up);

        // send duplicates ... NB: at present, dupes aren't detected until we do FieldGrouping
        // probably should send duplicates in another test
        kProducer.pushMessage(switch_topic, sw1_up);
        kProducer.pushMessage(switch_topic, sw2_up);
        kProducer.pushMessage(port_topic, sw1p1_up);
        kProducer.pushMessage(port_topic, sw2p2_up);

        Utils.sleep(4 * 1000);

        messagesExpected = 8; // at present, everything is passed through, no filter.
        messagesReceived = safeLinesCount(kafkaFiler.getFiler().getFile());
        Assert.assertEquals(messagesExpected, messagesReceived);

        Utils.sleep(1 * 1000);

        // sending this now just for fun .. we'll more formally test that the ISL state is correct.
        kProducer.pushMessage(port_topic, sw2p2_down);

        Utils.sleep(2 * 1000);

        // TODO: how can we programmatically determine how many ISL messages should be generated?
        messagesReceived = safeLinesCount(discoFiler.getFiler().getFile());
        if (messagesReceived == 0) {
            System.out.println("Message count failure; NO MESSAGES RECEIVED!");
            for (String s : Files.readLines(discoFiler.getFiler().getFile(), Charsets.UTF_8)) {
                System.out.println("\t\t > " + s);
            }

        }
        // NB: ISL discovery messages will be generated .. multiple .. at present 9-11.
        Assert.assertTrue(messagesReceived > 0);

        cluster.killTopology(topo.getTopologyName());
        cluster.killTopology("utils-1");
        cluster.killTopology("utils-2");
        Utils.sleep(4 * 1000);
    }

    private long safeLinesCount(File filename) {
        List<String> lines = null;
        try {
            lines = Files.readLines(filename, Charsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return (lines != null) ? lines.size() : 0;
    }

    /**
     * BasicLinkDiscoveryTest will exercise the basics of Link Discovery test.
     * The key results should show up in a kafka topic, which are dumped to file.
     */
    @Test
    public void BasicLinkDiscoveryTest() throws IOException {
        System.out.println("==> Starting BasicLinkDiscoveryTest");
        Tuple tuple;
        KeyValueState<String, LinkTracker> state = new InMemoryKeyValueState<>();
        initMocks();

        List<PathNode> nodes = Arrays.asList(new PathNode("sw1", 1, 0, 10L), new PathNode("sw2", 2, 1, 10L));
        InfoData data = new IslInfoData(10L, nodes, 10000L, IslChangeType.DISCOVERED, 9000L);
        String isl_discovered = MAPPER.writeValueAsString(data);

        OFELinkBolt linkBolt = new OFELinkBolt(DEFAULT_DISCOVERY_INTERVAL, DEFAULT_DISCOVERY_TIMEOUT);

        linkBolt.prepare(stormConfig(), topologyContext, outputCollector);
        linkBolt.initState(state);

        tuple = new TupleImpl(topologyContext, Arrays.asList("sw1", OFEMessageUtils.SWITCH_UP),
                0, InfoEventSplitterBolt.I_SWITCH_UPDOWN);
        linkBolt.execute(tuple);

        tuple = new TupleImpl(topologyContext, Arrays.asList("sw2", OFEMessageUtils.SWITCH_UP),
                0, InfoEventSplitterBolt.I_SWITCH_UPDOWN);
        linkBolt.execute(tuple);

        tuple = new TupleImpl(topologyContext, Arrays.asList("sw1", "1", OFEMessageUtils.PORT_UP),
                1, InfoEventSplitterBolt.I_PORT_UPDOWN);
        linkBolt.execute(tuple);

        tuple = new TupleImpl(topologyContext, Arrays.asList("sw1", "2", OFEMessageUtils.PORT_UP),
                1, InfoEventSplitterBolt.I_PORT_UPDOWN);
        linkBolt.execute(tuple);

        Tuple tickTuple = new TupleImpl(topologyContext, Collections.emptyList(), 2, Constants.SYSTEM_TICK_STREAM_ID);
        linkBolt.execute(tickTuple);

        tuple = new TupleImpl(topologyContext, Collections.singletonList(isl_discovered),
                3, InfoEventSplitterBolt.I_ISL_UPDOWN);
        linkBolt.execute(tuple);

        linkBolt.execute(tickTuple);
        linkBolt.execute(tickTuple);

        // 2 isls, 3 seconds interval, 9 seconds test duration == 6 discovery commands
        // +2 discovery commands triggered by port up message
        messagesExpected = 8;
        messagesReceived = outputCollectorMock.getMessagesCount(OFEventWFMTopology.DEFAULT_DISCOVERY_TOPIC);
        Assert.assertEquals(messagesExpected, messagesReceived);

        // "isl discovered" x1, "discovery failed" x1
        messagesExpected = 2;
        messagesReceived = outputCollectorMock.getMessagesCount(OFEventWFMTopology.DEFAULT_KAFKA_OUTPUT);
        Assert.assertEquals(messagesExpected, messagesReceived);

        linkBolt.execute(tickTuple);

        // +2 discovery commands
        messagesExpected = 10;
        messagesReceived = outputCollectorMock.getMessagesCount(OFEventWFMTopology.DEFAULT_DISCOVERY_TOPIC);
        Assert.assertEquals(messagesExpected, messagesReceived);

        // +2 discovery fails
        messagesExpected = 4;
        messagesReceived = outputCollectorMock.getMessagesCount(OFEventWFMTopology.DEFAULT_KAFKA_OUTPUT);
        Assert.assertEquals(messagesExpected, messagesReceived);
    }

    private void initMocks() {
        Fields switchSchema = new Fields(OFEMessageUtils.FIELD_SWITCH_ID, OFEMessageUtils.FIELD_STATE);
        when(topologyContext.getComponentId(0)).thenReturn(InfoEventSplitterBolt.I_SWITCH_UPDOWN);
        when(topologyContext.getComponentOutputFields(InfoEventSplitterBolt.I_SWITCH_UPDOWN,
                InfoEventSplitterBolt.I_SWITCH_UPDOWN)).thenReturn(switchSchema);

        Fields portSchema = new Fields(OFEMessageUtils.FIELD_SWITCH_ID,
                OFEMessageUtils.FIELD_PORT_ID, OFEMessageUtils.FIELD_STATE);
        when(topologyContext.getComponentId(1)).thenReturn(InfoEventSplitterBolt.I_PORT_UPDOWN);
        when(topologyContext.getComponentOutputFields(InfoEventSplitterBolt.I_PORT_UPDOWN,
                InfoEventSplitterBolt.I_PORT_UPDOWN)).thenReturn(portSchema);

        Fields tickSchema = new Fields();
        when(topologyContext.getComponentId(2)).thenReturn(Constants.SYSTEM_COMPONENT_ID);
        when(topologyContext.getComponentOutputFields(Constants.SYSTEM_COMPONENT_ID, Constants.SYSTEM_TICK_STREAM_ID))
                .thenReturn(tickSchema);

        Fields islSchema = new Fields(InfoEventSplitterBolt.I_ISL_UPDOWN);
        when(topologyContext.getComponentId(3)).thenReturn(InfoEventSplitterBolt.I_ISL_UPDOWN);
        when(topologyContext.getComponentOutputFields(InfoEventSplitterBolt.I_ISL_UPDOWN,
                InfoEventSplitterBolt.I_ISL_UPDOWN)).thenReturn(islSchema);
    }
}
