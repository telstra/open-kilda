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
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.messaging.Utils.MAPPER;

import org.apache.storm.Testing;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.testing.FixedTuple;
import org.apache.storm.testing.MockedSources;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Values;
import org.junit.Test;
import org.junit.Ignore;
import org.neo4j.graphdb.GraphDatabaseService;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.Datapoint;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.messaging.info.stats.FlowStatsReply;
import org.openkilda.messaging.info.stats.MeterConfigReply;
import org.openkilda.messaging.info.stats.MeterConfigStatsData;
import org.openkilda.messaging.info.stats.PortStatsData;
import org.openkilda.messaging.info.stats.PortStatsEntry;
import org.openkilda.messaging.info.stats.PortStatsReply;
import org.openkilda.wfm.StableAbstractStormTest;
import org.openkilda.wfm.topology.TestFlowGenMetricsBolt;
import org.openkilda.wfm.topology.TestingKafkaBolt;
import org.openkilda.wfm.topology.stats.metrics.FlowMetricGenBolt;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;

public class StatsTopologyTest extends StableAbstractStormTest {

    private static final long timestamp = System.currentTimeMillis();
    private static GraphDatabaseService graphDb;
    private static File dbFile;

    private final String switchId = "00:00:00:00:00:00:00:01";
    private final long cookie = 0x4000000000000001L;
    private final String flowId = "f253423454343";

    @Ignore
    @Test
    public void portStatsTest() throws Exception {
        final String switchId = "00:00:00:00:00:00:00:01";
        final List<PortStatsEntry> entries = IntStream.range(1, 53).boxed().map(port -> {
            int baseCount = port * 20;
            return new PortStatsEntry(port, baseCount, baseCount + 1, baseCount + 2, baseCount + 3,
                    baseCount + 4, baseCount + 5, baseCount + 6, baseCount + 7,
                    baseCount + 8, baseCount + 9, baseCount + 10, baseCount + 11);
        }).collect(toList());
        final List<PortStatsReply> replies = Collections.singletonList(new PortStatsReply(1, entries));
        InfoMessage message = new InfoMessage(new PortStatsData(switchId, replies), timestamp, CORRELATION_ID,
                Destination.WFM_STATS);

        //mock kafka spout
        MockedSources sources = new MockedSources();
        sources.addMockData(StatsComponentType.STATS_OFS_KAFKA_SPOUT.toString(),
                new Values(MAPPER.writeValueAsString(message)));
        completeTopologyParam.setMockedSources(sources);

        //execute topology
        Testing.withTrackedCluster(clusterParam, (cluster) ->  {
            StatsTopology topology = new TestingTargetTopology(new TestingKafkaBolt());
            StormTopology stormTopology = topology.createTopology();

            //verify results
            Map result = Testing.completeTopology(cluster, stormTopology, completeTopologyParam);
            ArrayList<FixedTuple> tuples =
                    (ArrayList<FixedTuple>) result.get(StatsComponentType.PORT_STATS_METRIC_GEN.name());
            assertThat(tuples.size(), is(728));
            tuples.stream()
                    .map(this::readFromJson)
                    .forEach(datapoint -> {
                        assertThat(datapoint.getTags().get("switchId"), is(switchId.replaceAll(":", "")));
                        assertThat(datapoint.getTime(), is(timestamp));
                        assertThat(datapoint.getMetric(), startsWith("pen.switch"));
                    });
        });
    }

    @Test
    public void meterConfigStatsTest() throws Exception {
        final String switchId = "00:00:00:00:00:00:00:01";
        final List<MeterConfigReply> stats = Collections.singletonList(new MeterConfigReply(2, Arrays.asList(1L, 2L, 3L)));
        InfoMessage message = new InfoMessage(new MeterConfigStatsData(switchId, stats), timestamp, CORRELATION_ID,
                Destination.WFM_STATS);

        //mock kafka spout
        MockedSources sources = new MockedSources();
        sources.addMockData(StatsComponentType.STATS_OFS_KAFKA_SPOUT.toString(),
                new Values(MAPPER.writeValueAsString(message)));
        completeTopologyParam.setMockedSources(sources);

        //execute topology
        Testing.withTrackedCluster(clusterParam, (cluster) ->  {
            StatsTopology topology = new TestingTargetTopology(new TestingKafkaBolt());
            StormTopology stormTopology = topology.createTopology();

            //verify results
            Map result = Testing.completeTopology(cluster, stormTopology, completeTopologyParam);
            ArrayList<FixedTuple> tuples =
                    (ArrayList<FixedTuple>) result.get(StatsComponentType.METER_CFG_STATS_METRIC_GEN.name());
            assertThat(tuples.size(), is(3));
            tuples.stream()
                    .map(this::readFromJson)
                    .forEach(datapoint -> {
                        assertThat(datapoint.getTags().get("switchid"), is(switchId.replaceAll(":", "")));
                        assertThat(datapoint.getTime(), is(timestamp));
                        assertThat(datapoint.getMetric(), is("pen.switch.meters"));
                    });
        });
    }

    @Test
    public void flowStatsTest() throws Exception {
        List<FlowStatsEntry> entries = Collections.singletonList(new FlowStatsEntry((short) 1, cookie, 1500L, 3000L));
        final List<FlowStatsReply> stats = Collections.singletonList(new FlowStatsReply(3, entries));
        InfoMessage message = new InfoMessage(new FlowStatsData(switchId, stats),
                timestamp, CORRELATION_ID, Destination.WFM_STATS);

        //mock kafka spout
        MockedSources sources = new MockedSources();
        sources.addMockData(StatsComponentType.STATS_OFS_KAFKA_SPOUT.toString(),
                new Values(MAPPER.writeValueAsString(message)));
        completeTopologyParam.setMockedSources(sources);

        //execute topology
        Testing.withTrackedCluster(clusterParam, (cluster) ->  {
            StatsTopology topology = new TestingTargetTopology(new TestingKafkaBolt());
            StormTopology stormTopology = topology.createTopology();

            Map result = Testing.completeTopology(cluster, stormTopology, completeTopologyParam);

            //verify results which were sent to Kafka bolt
            ArrayList<FixedTuple> tuples =
                    (ArrayList<FixedTuple>) result.get(StatsComponentType.FLOW_STATS_METRIC_GEN.name());
            assertThat(tuples.size(), is(6));
            tuples.stream()
                    .map(this::readFromJson)
                    .forEach(datapoint -> {
                        if (datapoint.getMetric().equals("pen.flow.packets")) {
                            assertThat(datapoint.getTags().get("direction"), is("forward"));
                        }
                        assertThat(datapoint.getTags().get("flowid"), is(flowId));
                        assertThat(datapoint.getTime(), is(timestamp));
                    });
        });
    }

    private Datapoint readFromJson(FixedTuple tuple) {
        try {
            return Utils.MAPPER.readValue(tuple.values.get(0).toString(), Datapoint.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * We should create child with these overridden methods because we don't want to use real kafka instance,
     */
    private class TestingTargetTopology extends StatsTopology {

        private KafkaBolt kafkaBolt;

        TestingTargetTopology(KafkaBolt kafkaBolt) throws Exception {
            super(makeLaunchEnvironment());

            this.kafkaBolt = kafkaBolt;
        }

        @Override
        protected void checkAndCreateTopic(String topic) {
        }

        @Override
        protected void createHealthCheckHandler(TopologyBuilder builder, String prefix) {
        }

        @Override
        public String makeTopologyName() {
            return StatsTopology.class.getSimpleName().toLowerCase();
        }

        @Override
        protected KafkaBolt createKafkaBolt(String topic) {
            return kafkaBolt;
        }

        @Override
        protected FlowMetricGenBolt createFlowMetricsGenBolt(String host, String username, String password) {
            return new TestFlowGenMetricsBolt(cookie, flowId, switchId, switchId);
        }
    }
}
