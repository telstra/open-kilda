/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.isllatency;

import static org.apache.storm.utils.Utils.sleep;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.openkilda.messaging.info.Datapoint;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.AbstractStormTest;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.topology.TestKafkaConsumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.storm.Config;
import org.apache.storm.generated.StormTopology;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Properties;
import java.util.UUID;

public class IslLatencyTopologyTest extends AbstractStormTest {

    private static final long timestamp = System.currentTimeMillis();
    private static final int POLL_TIMEOUT = 1000;
    private static final String POLL_DATAPOINT_ASSERT_MESSAGE = "Could not poll any datapoint";
    private static final String METRIC_PREFIX = "kilda.";
    private static final int PORT_1 = 1;
    private static final int PORT_2 = 2;
    private static final int LATENCY = 123;
    private static final SwitchId SWITCH_ID_1 = new SwitchId(1L);
    private static final SwitchId SWITCH_ID_2 = new SwitchId(2L);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static IslLatencyTopologyConfig islLatencyTopologyConfig;
    private static TestKafkaConsumer otsdbConsumer;

    @BeforeClass
    public static void setupOnce() throws Exception {
        AbstractStormTest.startZooKafkaAndStorm();

        LaunchEnvironment launchEnvironment = makeLaunchEnvironment();
        Properties configOverlay = new Properties();
        configOverlay.setProperty("opentsdb.metric.prefix", METRIC_PREFIX);

        launchEnvironment.setupOverlay(configOverlay);

        IslLatencyTopology islLatencyTopology = new IslLatencyTopology(launchEnvironment);
        islLatencyTopologyConfig = islLatencyTopology.getConfig();

        StormTopology stormTopology = islLatencyTopology.createTopology();
        Config config = stormConfig();

        cluster.submitTopology(IslLatencyTopologyTest.class.getSimpleName(), config, stormTopology);

        otsdbConsumer = new TestKafkaConsumer(islLatencyTopologyConfig.getKafkaOtsdbTopic(),
                kafkaProperties(UUID.randomUUID().toString()));
        otsdbConsumer.start();

        sleep(TOPOLOGY_START_TIMEOUT);
    }

    @AfterClass
    public static void teardownOnce() throws Exception {
        otsdbConsumer.wakeup();
        otsdbConsumer.join();
        AbstractStormTest.stopZooKafkaAndStorm();
    }

    @Before
    public void setup() {
        otsdbConsumer.clear();
    }

    @Test
    public void latencyMetricTest() throws Exception {
        IslInfoData islInfoData = IslInfoData.builder()
                .source(new PathNode(SWITCH_ID_1, PORT_1, 0))
                .destination(new PathNode(SWITCH_ID_2, PORT_2, 0))
                .latency(LATENCY)
                .build();
        InfoMessage infoMessage = new InfoMessage(islInfoData, timestamp, UUID.randomUUID().toString(), null, null);
        String request = objectMapper.writeValueAsString(infoMessage);
        kProducer.pushMessage(islLatencyTopologyConfig.getKafkaTopoDiscoTopic(), request);

        Datapoint datapoint = pollDataPoint();

        assertThat(datapoint.getTags().get("src_switch"), is(SWITCH_ID_1.toOtsdFormat()));
        assertThat(datapoint.getTags().get("src_port"), is(String.valueOf(PORT_1)));
        assertThat(datapoint.getTags().get("dst_switch"), is(SWITCH_ID_2.toOtsdFormat()));
        assertThat(datapoint.getTags().get("dst_port"), is(String.valueOf(PORT_2)));
        assertThat(datapoint.getTime(), is(timestamp));
        assertThat(datapoint.getMetric(), is(METRIC_PREFIX + "isl.latency"));
    }

    private Datapoint pollDataPoint() {
        ConsumerRecord<String, String> record = null;
        try {
            record = otsdbConsumer.pollMessage(POLL_TIMEOUT);
            if (record == null) {
                throw new AssertionError(POLL_DATAPOINT_ASSERT_MESSAGE);
            }
            return objectMapper.readValue(record.value(), Datapoint.class);
        } catch (InterruptedException e) {
            throw new AssertionError(POLL_DATAPOINT_ASSERT_MESSAGE);
        } catch (IOException e) {
            throw new AssertionError(String.format("Could not parse datapoint object: '%s'", record.value()));
        }
    }
}
