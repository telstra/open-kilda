package org.bitbucket.openkilda.wfm;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import org.apache.storm.Config;
import org.apache.storm.utils.Utils;
import org.junit.*;

import org.apache.storm.LocalCluster;

import java.io.IOException;
import java.util.Properties;


/**
 * Basic Splitter Tests.
 * <p>
 * The Splitter listens to a kafka queue and splits them into other queues.
 */
public class SimpleSplitterTest extends AbstractStormTest {

    @Before
    public void setupEach() {
    }

    @After
    public void teardownEach() {
    }

    public static Config stormConfig() {
        Config config = new Config();
        config.setDebug(false);
        config.setNumWorkers(1);
        return config;
    }

    @Test
    public void KafkaSplitterTest() throws IOException {

        /*
         * Need to ensure everything is pointing to the right testing URLS
         */
        Properties kprops = new Properties();
        kprops.put("bootstrap.servers", TestUtils.kafkaUrl);

        OFEventSplitterTopology splitter = new OFEventSplitterTopology().withKafkaProps(kprops);
        splitter.kutils = kutils;

        LocalCluster cluster = new LocalCluster();
        cluster.submitTopology(splitter.defaultTopoName, stormConfig(), splitter.createTopology());

        KafkaFilerTopology kfiler = new KafkaFilerTopology();
        cluster.submitTopology("filer-1", stormConfig(),
                kfiler.createTopology(InfoEventSplitterBolt.I_SWITCH_UPDOWN,
                        server.tempDir.getAbsolutePath(), TestUtils.zookeeperUrl));

        Utils.sleep(4 * 1000);
        SendMessages(splitter.topic);
        Utils.sleep(8 * 1000);

        long messagesExpected = 4; // 3 from below, and 1 no-op
        long messagesReceived = Files.readLines(kfiler.filer.getFile(), Charsets.UTF_8).size();
        Assert.assertEquals(messagesExpected,messagesReceived);

        Utils.sleep( 2 * 1000);
        cluster.killTopology(splitter.defaultTopoName);

// This code block was preferred but didn't work - ie interrogate Kafka and get number of
// messages sent. Unfortunately, the code returned 0 each time.  So, plan B was to dump to file.
//        KafkaLoggerTopology klogger = new KafkaLoggerTopology();
//        cluster.submitTopology("logger-3", stormConfig(),
//                klogger.createTopology(InfoEventSplitterBolt.I_SWITCH_UPDOWN, Level.DEBUG,
//                        InfoEventSplitterBolt.I_SWITCH_UPDOWN,TestUtils.zookeeperUrl));
//        List<String> messages = kutils
//                .getMessagesFromTopic(InfoEventSplitterBolt.I_SWITCH_UPDOWN);
//        long messagesReceived = messages.size();

    }



    // =~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=
    // TESTING Area
    // =~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=

    public static void SendMessages(String topic){
        System.out.println("==> sending records");

        KafkaProducer<String,String> kProducer = kutils.createStringsProducer();

        kProducer.send(new ProducerRecord<>(topic, "data", createSwitchInfoMessage("sw1",
                "ADDED")));
        kProducer.send(new ProducerRecord<>(topic, "data", createSwitchInfoMessage("sw2",
                "ADDED")));
        kProducer.send(new ProducerRecord<>(topic, "data", createSwitchInfoMessage("sw3",
                "ADDED")));

        Utils.sleep(1 * 1000);

        kProducer.send(new ProducerRecord<>(topic, "data", createSwitchInfoMessage("sw1",
                "ACTIVATED")));
        kProducer.send(new ProducerRecord<>(topic, "data", createSwitchInfoMessage("sw2",
                "ACTIVATED")));
        kProducer.send(new ProducerRecord<>(topic, "data", createSwitchInfoMessage("sw3",
                "ACTIVATED")));

        //kProducer.close();

    }

    /**
     * @param state - ACTIVATED | ADDED | CHANGE | DEACTIVATED | REMOVED
     */
    public static String createSwitchInfoMessage (String switchID, String state) {
        return createInfoMessage(true,switchID,null,state);
    }

    /**
     * @param state - ADD | OTHER_UPDATE | DELETE | UP | DOWN
     */
    public static String createPortInfoMessage (String switchID, String portID, String state) {
        return createInfoMessage(false,switchID,portID,state);
    }

    /**
     * TODO: this handles switch / port messages, but not ISL. Add it.
     * Example OpenFlow Messages:
            {
            "type": "INFO",
            "timestamp": 1489980143,
            "data": {
                "message_type": "switch",
                "switch_id": "0x0000000000000001",
                "state": "ACTIVATED | ADDED | CHANGE | DEACTIVATED | REMOVED"
                }
            }

        {
             "type": "INFO",
             "timestamp": 1489980143,
             "data": {
                "message_type": "port",
                "switch_id": "0x0000000000000001",
                "state": "UP | DOWN | .. "
                "port_no": LONG
                "max_capacity": LONG
             }
        }

     {"type": "INFO", "data": {"message_type": "switch", "switch_id": "0x0000000000000001", "state": "ACTIVATED"}}

     * @param isSwitch - it is either a switch or port at this stage.
     */
    public static String createInfoMessage (boolean isSwitch, String switchID, String portID, String
            state) {
        StringBuffer sb = new StringBuffer("{'type': 'INFO', ");
        sb.append("'timestamp': ").append(System.currentTimeMillis()).append(", ");
        sb.append("'data': {'message_type': '").append(isSwitch?"switch":"port").append("', ");
        sb.append("'switch_id': '").append(switchID).append("', ");
        if (!isSwitch) {
            sb.append(", 'port_no': ").append(portID).append("', ");
        }
        sb.append("'state': '").append(state).append("'}}");
        return sb.toString().replace("'","\"");
    }
}
