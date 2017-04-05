package org.bitbucket.openkilda.wfm;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import kafka.admin.AdminUtils;
import kafka.admin.RackAwareMode;
import kafka.admin.RackAwareMode.Disabled$;
import kafka.utils.ZKStringSerializer$;
import kafka.utils.ZkUtils;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.ZkConnection;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.utils.Utils;
import org.junit.*;

import java.io.IOException;
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
                InfoEventSplitterBolt.I_ISL_UPDOWN
        }, 1, 1);

        OFEventWFMTopology topo = new OFEventWFMTopology().withKafkaProps(kprops);
        topo.kutils = kutils;

        LocalCluster cluster = new LocalCluster();
        cluster.submitTopology(topo.topoName, stormConfig(), topo.createTopology());

//        KafkaFilerTopology kfiler = new KafkaFilerTopology();
//        cluster.submitTopology("filer-1", stormConfig(),
//                kfiler.createTopology(InfoEventSplitterBolt.I_SWITCH_UPDOWN,
//                        server.tempDir.getAbsolutePath(), TestUtils.zookeeperUrl));

        Utils.sleep(4 * 1000);

        String sw1_up = OFEMessageUtils.createSwitchDataMessage(OFEMessageUtils.SWITCH_UP, "sw1");
        String sw2_up = OFEMessageUtils.createSwitchDataMessage(OFEMessageUtils.SWITCH_UP, "sw2");
        String topic = InfoEventSplitterBolt.I_SWITCH_UPDOWN;

        // send sw1 and sw2 up
        kProducer.send(new ProducerRecord<>(topic, "data", sw1_up));
        kProducer.send(new ProducerRecord<>(topic, "data", sw2_up));

        Utils.sleep(2 * 1000);

        // resend sw1
        kProducer.send(new ProducerRecord<>(topic, "data", sw1_up));

        Utils.sleep(2 * 1000);

//        long messagesExpected = 4; // 3 from below, and 1 no-op
//        long messagesReceived = Files.readLines(kfiler.filer.getFile(), Charsets.UTF_8).size();
//        Assert.assertEquals(messagesExpected,messagesReceived);

        Utils.sleep( 2 * 1000);
        cluster.killTopology(topo.topoName);

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
