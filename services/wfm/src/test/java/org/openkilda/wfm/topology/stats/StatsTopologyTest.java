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

package org.openkilda.wfm.topology.stats;

import static java.util.stream.Collectors.toList;
import static org.openkilda.messaging.Utils.CORRELATION_ID;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.messaging.info.stats.FlowStatsReply;
import org.openkilda.messaging.info.stats.MeterConfigReply;
import org.openkilda.messaging.info.stats.MeterConfigStatsData;
import org.openkilda.messaging.info.stats.PortStatsData;
import org.openkilda.messaging.info.stats.PortStatsEntry;
import org.openkilda.messaging.info.stats.PortStatsReply;
import org.openkilda.wfm.AbstractStormTest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.storm.Config;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.utils.Utils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

public class StatsTopologyTest extends AbstractStormTest {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String TOPIC = "kilda-test";
    private static final long timestamp = System.currentTimeMillis();

    @BeforeClass
    public static void setupOnce() throws Exception {
        AbstractStormTest.setupOnce();

        StatsTopology topology = new StatsTopology(makeLaunchEnvironment());
        StormTopology stormTopology = topology.createTopology();
        Config config = stormConfig();
        cluster.submitTopology(StatsTopologyTest.class.getSimpleName(), config, stormTopology);

        Utils.sleep(10000);
    }

    @AfterClass
    public static void teardownOnce() throws Exception {
        cluster.killTopology(StatsTopologyTest.class.getSimpleName());
        Utils.sleep(4 * 1000);
        AbstractStormTest.teardownOnce();
    }

    @Test
    public void portStatsTest() throws Exception {
        final List<PortStatsEntry> entries = IntStream.range(1, 53).boxed().map(port -> {
            int baseCount = port * 20;
            return new PortStatsEntry(port, baseCount, baseCount + 1, baseCount + 2, baseCount + 3,
                    baseCount + 4, baseCount + 5, baseCount + 6, baseCount + 7,
                    baseCount + 8, baseCount + 9, baseCount + 10, baseCount + 11);
        }).collect(toList());
        final List<PortStatsReply> replies = Collections.singletonList(new PortStatsReply(1, entries));
        InfoMessage message = new InfoMessage(new PortStatsData("00:00:00:00:00:00:00:01", replies),
                timestamp, CORRELATION_ID, Destination.WFM_STATS);
        kProducer.pushMessage(TOPIC, objectMapper.writeValueAsString(message));
    }

    @Test
    public void meterConfigStatsTest() throws Exception {
        final List<MeterConfigReply> stats = Collections.singletonList(new MeterConfigReply(2, Arrays.asList(1L, 2L, 3L)));
        InfoMessage message = new InfoMessage(new MeterConfigStatsData("00:00:00:00:00:00:00:01", stats),
                timestamp, CORRELATION_ID, Destination.WFM_STATS);
        kProducer.pushMessage(TOPIC, objectMapper.writeValueAsString(message));
    }

    @Test
    public void flowStatsTest() throws Exception {
        List<FlowStatsEntry> entries = Collections.singletonList(new FlowStatsEntry((short) 1, 0x1FFFFFFFFL, 1500L, 3000L));
        final List<FlowStatsReply> stats = Collections.singletonList(new FlowStatsReply(3, entries));
        InfoMessage message = new InfoMessage(new FlowStatsData("00:00:00:00:00:00:00:01", stats),
                timestamp, CORRELATION_ID, Destination.WFM_STATS);
        kProducer.pushMessage(TOPIC, objectMapper.writeValueAsString(message));
    }
}
