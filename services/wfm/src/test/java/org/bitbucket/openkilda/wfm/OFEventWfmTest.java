package org.bitbucket.openkilda.wfm;

import org.bitbucket.openkilda.wfm.topology.event.InfoEventSplitterBolt;
import org.bitbucket.openkilda.wfm.topology.event.OFELinkBolt;
import org.bitbucket.openkilda.wfm.topology.event.OFEventWFMTopology;
import org.bitbucket.openkilda.wfm.topology.utils.KafkaFilerTopology;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.apache.storm.utils.Utils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * OFEventWfmTest tests the critical aspects of OFEventWFMTopology
 */
public class OFEventWfmTest extends AbstractStormTest {

    // Leaving these here as a tickler if needed.
    @Before
    public void setupEach() {}
    @After
    public void teardownEach() {}

    @Test
    public void BasicSwitchEventTest() throws IOException {
        System.out.println("==> Starting BasicSwitchEventTest");

        kutils.createTopics(new String[]{
                InfoEventSplitterBolt.I_SWITCH_UPDOWN,
                InfoEventSplitterBolt.I_PORT_UPDOWN,
                InfoEventSplitterBolt.I_ISL_UPDOWN,
                OFELinkBolt.DEFAULT_DISCO_TOPIC,
                OFEventWFMTopology.DEFAULT_KAFKA_OUTPUT});

        OFEventWFMTopology topo = new OFEventWFMTopology(kutils);

        cluster.submitTopology(topo.getTopoName(), stormConfig(), topo.createTopology());

        KafkaFilerTopology kfiler1 = new KafkaFilerTopology();
        cluster.submitTopology("utils-1", stormConfig(),
                kfiler1.createTopology(topo.getKafkaOutputTopic(),
                        server.tempDir.getAbsolutePath(), TestUtils.zookeeperUrl));

        KafkaFilerTopology kfiler2 = new KafkaFilerTopology();
        cluster.submitTopology("utils-2", stormConfig(),
                kfiler2.createTopology(OFELinkBolt.DEFAULT_DISCO_TOPIC,
                        server.tempDir.getAbsolutePath(), TestUtils.zookeeperUrl));

        Utils.sleep(4 * 1000);

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

        Utils.sleep(5 * 1000);

        long messagesExpected = 8; // at present, everything is passed through, no filter.
        long messagesReceived = safeLinesCount(kfiler1.getFiler().getFile());
        Assert.assertEquals(messagesExpected, messagesReceived);

        Utils.sleep(1 * 1000);

        // sending this now just for fun .. we'll more formally test that the ISL state is correct.
        kProducer.pushMessage(port_topic, sw2p2_down);

        Utils.sleep(2 * 1000);

        messagesExpected = 8;
        messagesReceived = safeLinesCount(kfiler2.getFiler().getFile());
        if (messagesExpected != messagesReceived) {
            System.out.println(String.format("Message count failure; %d != %d",
                    messagesExpected, messagesReceived));
            for (String s : Files.readLines(kfiler2.getFiler().getFile(), Charsets.UTF_8)) {
                System.out.println("\t\t > " + s);
            }

        }
        // NB: ISL discovery messages will be generated .. multiple .. at present 9-11.
        Assert.assertTrue(messagesReceived > 8);
}

    protected long safeLinesCount(File filename) {
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
    }

    /**
     * BasicLinkHealthTest will exercise the basics of the Health check .. ie periodic disco
     * tests.
     */
    @Test
    public void BasicLinkHealthTest() throws IOException {
        System.out.println("==> Starting BasicLinkHealthTest");
    }

    /**
     * This method will send the messages we expect to see on the kafka topic.
     * At present we will respond to UP and DOWN messages.
     */

    public void sendSwitchUpMsg(String switchId){
        String msg = OFEMessageUtils.createSwitchDataMessage(switchId, OFEMessageUtils.SWITCH_UP);
    }

    public void sendSwitchDownMsg(String switchId){
        String msg = OFEMessageUtils.createSwitchDataMessage(switchId, OFEMessageUtils.SWITCH_DOWN);
    }
}
