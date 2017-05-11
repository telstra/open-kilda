package org.bitbucket.openkilda.wfm;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.utils.Utils;
import org.junit.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

/**
 * OFEventWfmTest tests the critical aspects of OFEventWFMTopology
 */
public class OFEventWfmTest extends AbstractStormTest  {

    // Leaving these here as a tickler if needed.
    @Before
    public void setupEach() {}
    @After
    public void teardownEach() {}

    public static Config stormConfig() {
        Config config = new Config();
        config.setDebug(false);
        config.setNumWorkers(1);
        return config;
    }

    @Test
    public void BasicSwitchEventTest() throws IOException {
        System.out.println("==> Starting BasicSwitchEventTest");

        Properties kprops = new Properties();
        kprops.put("bootstrap.servers", TestUtils.kafkaUrl);

        kutils.createTopics(new String[] {
                InfoEventSplitterBolt.I_SWITCH_UPDOWN,
                InfoEventSplitterBolt.I_PORT_UPDOWN,
                InfoEventSplitterBolt.I_ISL_UPDOWN,
                OFELinkBolt.DEFAULT_DISCO_TOPIC,
                OFEventWFMTopology.DEFAULT_KAFKA_OUTPUT
        }, 1, 1);



        LocalCluster cluster = new LocalCluster();
        OFEventWFMTopology topo = new OFEventWFMTopology().withKafkaProps(kprops);
        topo.kutils = kutils;

        cluster.submitTopology(topo.topoName, stormConfig(), topo.createTopology());

        KafkaFilerTopology kfiler1 = new KafkaFilerTopology();
        cluster.submitTopology("filer-1", stormConfig(),
                kfiler1.createTopology(topo.kafkaOutputTopic,
                        server.tempDir.getAbsolutePath(), TestUtils.zookeeperUrl));

        KafkaFilerTopology kfiler2 = new KafkaFilerTopology();
        cluster.submitTopology("filer-2", stormConfig(),
                kfiler2.createTopology(OFELinkBolt.DEFAULT_DISCO_TOPIC,
                        server.tempDir.getAbsolutePath(), TestUtils.zookeeperUrl));

        Utils.sleep(4 * 1000);

        String sw1_up = OFEMessageUtils.createSwitchDataMessage(
                OFEMessageUtils.SWITCH_UP,"sw1");
        String sw2_up = OFEMessageUtils.createSwitchDataMessage(
                OFEMessageUtils.SWITCH_UP,"sw2");
        String sw1p1_up = OFEMessageUtils.createPortDataMessage(
                OFEMessageUtils.PORT_UP,"sw1", "1");
        String sw2p2_up = OFEMessageUtils.createPortDataMessage(
                OFEMessageUtils.PORT_UP,"sw2", "2");
        String sw2p2_down = OFEMessageUtils.createPortDataMessage(
                OFEMessageUtils.PORT_DOWN,"sw2", "2");
        String switch_topic = InfoEventSplitterBolt.I_SWITCH_UPDOWN;
        String port_topic = InfoEventSplitterBolt.I_PORT_UPDOWN;

        // send sw1 and sw2 up
        kProducer.send(new ProducerRecord<>(switch_topic, "payload", sw1_up));
        kProducer.send(new ProducerRecord<>(switch_topic, "payload", sw2_up));

        Utils.sleep(1 * 1000);

        // sent sw1/port1 up ... sw2/port2 up
        kProducer.send(new ProducerRecord<>(port_topic, "payload", sw1p1_up));
        kProducer.send(new ProducerRecord<>(port_topic, "payload", sw2p2_up));

        Utils.sleep(1 * 1000);

        // send duplicates ... NB: at present, dupes aren't detected until we do FieldGrouping
        // probably should send duplicates in another test
        kProducer.send(new ProducerRecord<>(switch_topic, "payload", sw1_up));
        kProducer.send(new ProducerRecord<>(switch_topic, "payload", sw2_up));
        kProducer.send(new ProducerRecord<>(port_topic, "payload", sw1p1_up));
        kProducer.send(new ProducerRecord<>(port_topic, "payload", sw2p2_up));

        Utils.sleep(1 * 1000);

        long messagesExpected = 8; // at present, everything is passed through, no filter.
        long messagesReceived = safeLinesCount(kfiler1.filer.getFile());
        Assert.assertEquals(messagesExpected,messagesReceived);

        cluster.killTopology("filer-1");

        Utils.sleep(1 * 1000);

        // sending this now just for fun .. we'll more formally test that the ISL state is correct.
        kProducer.send(new ProducerRecord<>(port_topic, "payload", sw2p2_down));

        Utils.sleep(2 * 1000);

        // There are 4 port up messages (2 duplicates), so we should see 4 discovery packets sents
        messagesExpected = 4; // at present, everything is passed through, no filter.
        messagesReceived = safeLinesCount(kfiler2.filer.getFile());
        Assert.assertEquals(messagesExpected,messagesReceived);

        Utils.sleep( 2 * 1000);
        cluster.killTopology(topo.topoName);

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

    public void primeKafkaTopic() {

    }
}
