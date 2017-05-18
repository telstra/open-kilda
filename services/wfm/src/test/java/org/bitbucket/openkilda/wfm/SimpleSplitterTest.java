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
        cluster.submitTopology(splitter.defaultTopoName, TestUtils.stormConfig(), splitter
                .createTopology());

        // Dumping the Kafka Topic to file so that I can test the results.
        KafkaFilerTopology kfiler = new KafkaFilerTopology();
        cluster.submitTopology("filer-1", TestUtils.stormConfig(),
                kfiler.createTopology(InfoEventSplitterBolt.I_SWITCH_UPDOWN,
                        server.tempDir.getAbsolutePath(), TestUtils.zookeeperUrl));

        Utils.sleep(4 * 1000);
        SendMessages(splitter.topic);
        Utils.sleep(8 * 1000);

        // 3 messages .. in I_SWITCH_UPDOWN  .. since we send 3 of those type of messages
        long messagesExpected = 3;
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
        String added = "ADDED";
        String active = OFEMessageUtils.SWITCH_UP;

        kProducer.send(new ProducerRecord<>(topic, "payload",
                OFEMessageUtils.createSwitchInfoMessage("sw1", added)));
        kProducer.send(new ProducerRecord<>(topic, "payload",
                OFEMessageUtils.createSwitchInfoMessage("sw2", added)));
        kProducer.send(new ProducerRecord<>(topic, "payload",
                OFEMessageUtils.createSwitchInfoMessage("sw3", added)));

        Utils.sleep(1 * 1000);

        kProducer.send(new ProducerRecord<>(topic, "payload",
                OFEMessageUtils.createSwitchInfoMessage("sw1", active)));
        kProducer.send(new ProducerRecord<>(topic, "payload",
                OFEMessageUtils.createSwitchInfoMessage("sw2", active)));
        kProducer.send(new ProducerRecord<>(topic, "payload",
                OFEMessageUtils.createSwitchInfoMessage("sw3", active)));


    }

}
