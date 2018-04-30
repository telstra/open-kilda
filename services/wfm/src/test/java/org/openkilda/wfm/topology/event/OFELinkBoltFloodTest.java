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

package org.openkilda.wfm.topology.event;


import static org.junit.Assert.assertEquals;
import static org.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.storm.Config;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.utils.Utils;
import org.junit.AfterClass;
import org.junit.Ignore;
import org.junit.Test;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.NetworkInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;
import org.openkilda.wfm.AbstractStormTest;
import org.openkilda.wfm.topology.TestKafkaConsumer;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

/** That test is part of warming mechanism.
 * When we reload OFELinkBolt, FL can spam in our topic with thousands of messages. And we need to
 * sure that our dump state message can be processed because we use one topic for communication
 * with FL.
 */
@Ignore // TODO(nmarchenko): move that test to perf and unignore
public class OFELinkBoltFloodTest extends AbstractStormTest {

    private static OFEventWFMTopology topology;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private TestKafkaConsumer teConsumer;

    @AfterClass
    public static void teardownOnce() throws Exception {
        cluster.killTopology(OFELinkBoltFloodTest.class.getSimpleName());
        Utils.sleep(4 * 1000);
        AbstractStormTest.teardownOnce();
    }

    @Test(timeout=5000*60)
    public void warmBoltOnHighLoadedTopic() throws Exception {
        topology = new OFEventWFMTopology(makeLaunchEnvironment());

        teConsumer = new TestKafkaConsumer(
                topology.getConfig().getKafkaTopoEngTopic(),
                kafkaProperties(UUID.nameUUIDFromBytes(Destination.TOPOLOGY_ENGINE.toString().getBytes()).toString())
        );
        teConsumer.start();

        // Size of messages in topic before bolt start
        final int floodSize = 100000;

        SwitchInfoData data = new SwitchInfoData("switchId", SwitchState.ADDED, "address",
                "hostname", "description", "controller");
        InfoMessage message = new InfoMessage(data, System.currentTimeMillis(), UUID.randomUUID().toString());

        // Floooding
        sendMessages(message, topology.getConfig().getKafkaTopoDiscoTopic(), floodSize);


        StormTopology stormTopology = topology.createTopology();
        Config config = stormConfig();
        cluster.submitTopology(OFELinkBoltFloodTest.class.getSimpleName(), config, stormTopology);

        NetworkInfoData dump = new NetworkInfoData(
                "test", Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptySet());

        InfoMessage info = new InfoMessage(dump, 0, DEFAULT_CORRELATION_ID, Destination.WFM);

        String request = objectMapper.writeValueAsString(info);
        // Send DumpMessage to topic with offset floodSize+1.
        kProducer.pushMessage(topology.getConfig().getKafkaTopoDiscoTopic(), request);

        // Wait all messages
        int pooled = 0;
        while (pooled < floodSize)
        {
            if (teConsumer.pollMessage() != null)
                ++pooled;
        }
        assertEquals(floodSize, pooled);
    }

    private static void sendMessages(Object object, String topic, int count) throws IOException {
        String request = objectMapper.writeValueAsString(object);
        for (int i = 0; i < count; ++i) {
            kProducer.pushMessageAsync(topic, request);
        }
        kProducer.flush();
    }
}
