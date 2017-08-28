package org.bitbucket.openkilda.wfm;

import org.bitbucket.openkilda.wfm.topology.splitter.InfoEventSplitterBolt;
import org.bitbucket.openkilda.wfm.topology.splitter.OFEventSplitterTopology;
import org.bitbucket.openkilda.wfm.topology.utils.KafkaFilerTopology;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.apache.storm.utils.Utils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

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

    @Test
    public void KafkaSplitterTest() throws IOException {

        /*
         * Need to ensure everything is pointing to the right testing URLS
         */

        OFEventSplitterTopology splitter = new OFEventSplitterTopology(kutils);

        cluster.submitTopology(splitter.defaultTopoName, TestUtils.stormConfig(), splitter
                .createTopology());

        // Dumping the Kafka Topic to file so that I can test the results.
        KafkaFilerTopology kfiler = new KafkaFilerTopology();
        cluster.submitTopology("utils-1", TestUtils.stormConfig(),
                kfiler.createTopology(InfoEventSplitterBolt.I_SWITCH_UPDOWN,
                        server.tempDir.getAbsolutePath(), TestUtils.zookeeperUrl));

        Utils.sleep(4 * 1000);
        SendMessages(splitter.topic);
        Utils.sleep(8 * 1000);

        // 3 messages .. in I_SWITCH_UPDOWN  .. since we send 3 of those type of messages
        // and 3 messages for ADDED state
        long messagesExpected = 6;
        long messagesReceived = Files.readLines(kfiler.getFiler().getFile(), Charsets.UTF_8).size();
        Assert.assertEquals(messagesExpected,messagesReceived);

        Utils.sleep( 2 * 1000);

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

        kProducer.pushMessage(topic, OFEMessageUtils.createSwitchInfoMessage("sw1", added));
        kProducer.pushMessage(topic, OFEMessageUtils.createSwitchInfoMessage("sw2", added));
        kProducer.pushMessage(topic, OFEMessageUtils.createSwitchInfoMessage("sw3", added));

        Utils.sleep(1 * 1000);

        kProducer.pushMessage(topic, OFEMessageUtils.createSwitchInfoMessage("sw1", active));
        kProducer.pushMessage(topic, OFEMessageUtils.createSwitchInfoMessage("sw2", active));
        kProducer.pushMessage(topic, OFEMessageUtils.createSwitchInfoMessage("sw3", active));
    }
}
