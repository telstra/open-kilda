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

import org.junit.Ignore;
import org.kohsuke.args4j.CmdLineException;
import org.openkilda.wfm.topology.Topology;
import org.openkilda.wfm.topology.splitter.InfoEventSplitterBolt;
import org.openkilda.wfm.topology.splitter.OFEventSplitterTopology;
import org.openkilda.wfm.topology.utils.KafkaFilerTopology;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.apache.storm.utils.Utils;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

/**
 * Basic Splitter Tests.
 * <p>
 * The Splitter listens to a kafka queue and splits them into other queues.
 */
public class SimpleSplitterTest extends AbstractStormTest {
    @Test
    public void KafkaSplitterTest() throws IOException, ConfigurationException, CmdLineException {
        /*
         * Need to ensure everything is pointing to the right testing URLS
         */
        OFEventSplitterTopology splitter = new OFEventSplitterTopology(makeLaunchEnvironment());
        cluster.submitTopology(splitter.makeTopologyName(), TestUtils.stormConfig(), splitter.createTopology());

        // Dumping the Kafka Topic to file so that I can test the results.
        KafkaFilerTopology kfiler = new KafkaFilerTopology(
                makeLaunchEnvironment(), splitter.getConfig().getKafkaInputTopic());
        cluster.submitTopology("utils-1", TestUtils.stormConfig(), kfiler.createTopology());

        Utils.sleep(4 * 1000);
        SendMessages(splitter.getConfig().getKafkaInputTopic());
        Utils.sleep(8 * 1000);

        // 3 messages .. in I_SWITCH_UPDOWN  .. since we send 3 of those type of messages
        // and 3 messages for ADDED state
        long messagesExpected = 6;
        long messagesReceived = Files.readLines(kfiler.getFiler().getFile(), Charsets.UTF_8).size();
        Assert.assertEquals(messagesExpected, messagesReceived);

        cluster.killTopology(splitter.makeTopologyName());
        cluster.killTopology("utils-1");
        Utils.sleep(4 * 1000);
    }

    // =~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=
    // TESTING Area
    // =~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=

    public static void SendMessages(String topic) {
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
