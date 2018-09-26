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

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.InstallOneSwitchFlow;
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
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.messaging.payload.flow.OutputVlanType;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.Neo4jFixture;
import org.openkilda.wfm.StableAbstractStormTest;
import org.openkilda.wfm.topology.TestingKafkaBolt;
import org.openkilda.wfm.topology.stats.bolts.CacheFilterBolt;

import org.apache.storm.Testing;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.testing.FixedTuple;
import org.apache.storm.testing.MockedSources;
import org.apache.storm.tuple.Values;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.IntStream;

public class StatsTopologyTest extends StableAbstractStormTest {

    private static final long timestamp = System.currentTimeMillis();

    private final SwitchId switchId = new SwitchId(1L);
    private final long cookie = 0x4000000000000001L;
    private final String flowId = "f253423454343";

    private static Neo4jFixture fakeNeo4jDb;

    private static LaunchEnvironment launchEnvironment;

    @BeforeClass
    public static void setupOnce() throws Exception {
        StableAbstractStormTest.setupOnce();
        fakeNeo4jDb = new Neo4jFixture(fsData.getRoot().toPath(), NEO4J_LISTEN_ADDRESS);
        fakeNeo4jDb.start();
        launchEnvironment = makeLaunchEnvironment();
        Properties configOverlay = new Properties();
        configOverlay.setProperty("neo4j.hosts", fakeNeo4jDb.getListenAddress());

        launchEnvironment.setupOverlay(configOverlay);
    }


    @AfterClass
    public static void teardownOnce() throws Exception {
        fakeNeo4jDb.stop();
    }

    @Ignore
    @Test
    public void portStatsTest() throws Exception {
        final SwitchId switchId = new SwitchId(1L);
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
        Testing.withTrackedCluster(clusterParam, (cluster) -> {
            StatsTopology topology = new TestingTargetTopology(launchEnvironment, new TestingKafkaBolt());
            StormTopology stormTopology = topology.createTopology();

            //verify results
            Map result = Testing.completeTopology(cluster, stormTopology, completeTopologyParam);
            ArrayList<FixedTuple> tuples =
                    (ArrayList<FixedTuple>) result.get(StatsComponentType.PORT_STATS_METRIC_GEN.name());
            assertThat(tuples.size(), is(728));
            tuples.stream()
                    .map(this::readFromJson)
                    .forEach(datapoint -> {
                        assertThat(datapoint.getTags().get("switchId"), is(switchId.toString().replaceAll(":", "")));
                        assertThat(datapoint.getTime(), is(timestamp));
                        assertThat(datapoint.getMetric(), startsWith("pen.switch"));
                    });
        });
    }

    @Test
    public void meterConfigStatsTest() throws Exception {
        final SwitchId switchId = new SwitchId(1L);
        final List<MeterConfigReply> stats =
                Collections.singletonList(new MeterConfigReply(2, Arrays.asList(1L, 2L, 3L)));
        InfoMessage message = new InfoMessage(new MeterConfigStatsData(switchId, stats), timestamp, CORRELATION_ID,
                Destination.WFM_STATS);

        //mock kafka spout
        MockedSources sources = new MockedSources();
        sources.addMockData(StatsComponentType.STATS_OFS_KAFKA_SPOUT.toString(),
                new Values(MAPPER.writeValueAsString(message)));
        sources.addMockData(StatsComponentType.STATS_KILDA_SPEAKER_SPOUT.name(),
                new Values(MAPPER.writeValueAsString(message))
        );

        completeTopologyParam.setMockedSources(sources);

        //execute topology
        Testing.withTrackedCluster(clusterParam, (cluster) -> {
            StatsTopology topology = new TestingTargetTopology(launchEnvironment, new TestingKafkaBolt());
            StormTopology stormTopology = topology.createTopology();

            //verify results
            Map result = Testing.completeTopology(cluster, stormTopology, completeTopologyParam);
            ArrayList<FixedTuple> tuples =
                    (ArrayList<FixedTuple>) result.get(StatsComponentType.METER_CFG_STATS_METRIC_GEN.name());
            assertThat(tuples.size(), is(3));
            tuples.stream()
                    .map(this::readFromJson)
                    .forEach(datapoint -> {
                        assertThat(datapoint.getTags().get("switchid"),
                                is(switchId.toOtsdFormat()));
                        assertThat(datapoint.getTime(), is(timestamp));
                        assertThat(datapoint.getMetric(), is("pen.switch.meters"));
                    });
        });
    }

    @Test
    public void flowStatsTest() throws Exception {
        //mock kafka spout
        MockedSources sources = new MockedSources();

        GraphDatabaseService graphDatabaseService = fakeNeo4jDb.getGraphDatabaseService();

        try (Transaction tx = graphDatabaseService.beginTx()) {
            Node node1 = graphDatabaseService.createNode(Label.label("switch"));
            node1.setProperty("name", switchId.toString());
            Relationship rel1 = node1.createRelationshipTo(node1, RelationshipType.withName("flow"));
            rel1.setProperty("flowid", flowId);
            rel1.setProperty("cookie", cookie);
            rel1.setProperty("meter_id", 2);
            rel1.setProperty("transit_vlan", 1);
            rel1.setProperty("src_switch", switchId.toString());
            rel1.setProperty("dst_switch", switchId.toString());
            rel1.setProperty("src_port", 1);
            rel1.setProperty("dst_port", 2);
            rel1.setProperty("src_vlan", 5);
            rel1.setProperty("dst_vlan", 5);
            rel1.setProperty("path", "\"{\"path\": [], \"latency_ns\": 0, \"timestamp\": 1522528031909}\"");
            rel1.setProperty("bandwidth", 200);
            rel1.setProperty("ignore_bandwidth", true);
            rel1.setProperty("description", "description");
            rel1.setProperty("last_updated", "last_updated");
            tx.success();
        }

        List<FlowStatsEntry> entries = Collections.singletonList(
                new FlowStatsEntry((short) 1, cookie, 1500L, 3000L));
        final List<FlowStatsReply> stats = Collections.singletonList(new FlowStatsReply(3, entries));
        InfoMessage message = new InfoMessage(new FlowStatsData(switchId, stats),
                timestamp, CORRELATION_ID, Destination.WFM_STATS);

        sources.addMockData(StatsComponentType.STATS_OFS_KAFKA_SPOUT.toString(),
                new Values(MAPPER.writeValueAsString(message)));

        sources.addMockData(StatsComponentType.STATS_KILDA_SPEAKER_SPOUT.name(),
                new Values("")
        );

        completeTopologyParam.setMockedSources(sources);

        //execute topology
        Testing.withTrackedCluster(clusterParam, (cluster) -> {
            StatsTopology topology = new TestingTargetTopology(launchEnvironment, new TestingKafkaBolt());
            StormTopology stormTopology = topology.createTopology();

            Map result = Testing.completeTopology(cluster, stormTopology, completeTopologyParam);

            //verify results which were sent to Kafka bolt
            ArrayList<FixedTuple> tuples =
                    (ArrayList<FixedTuple>) result.get(StatsComponentType.FLOW_STATS_METRIC_GEN.name());
            assertThat(tuples.size(), is(9));
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

    @Test
    public void cacheSyncSingleSwitchFlowAdd() throws Exception {
        final SwitchId switchId = new SwitchId(1L);
        final String flowId = "sync-test-add-ssf";
        final InstallOneSwitchFlow payload =
                new InstallOneSwitchFlow(
                        0L, flowId, 0xFFFF000000000001L, switchId, 8, 9, 127, 127,
                        OutputVlanType.PUSH, 1000L, 0L);
        final CommandMessage message = new CommandMessage(payload, timestamp, flowId, Destination.WFM_STATS);
        final String json = MAPPER.writeValueAsString(message);

        MockedSources sources = new MockedSources();
        sources.addMockData(StatsComponentType.STATS_OFS_KAFKA_SPOUT.name());
        sources.addMockData(StatsComponentType.STATS_KILDA_SPEAKER_SPOUT.name(), new Values(json));
        completeTopologyParam.setMockedSources(sources);

        Testing.withTrackedCluster(clusterParam, (cluster) -> {
            StatsTopology topologyManager = new TestingTargetTopology(launchEnvironment, new TestingKafkaBolt());
            StormTopology topology = topologyManager.createTopology();

            Map result = Testing.completeTopology(cluster, topology, completeTopologyParam);
            List<FixedTuple> cacheSyncStream = (List<FixedTuple>) result.get(
                    StatsComponentType.STATS_CACHE_FILTER_BOLT.name());

            final HashSet<MeasurePoint> expectedEvents = new HashSet<>();
            expectedEvents.add(MeasurePoint.INGRESS);
            expectedEvents.add(MeasurePoint.EGRESS);

            final HashSet<MeasurePoint> seenEvents = new HashSet<>();
            cacheSyncStream.stream()
                    .filter(item -> StatsStreamType.CACHE_UPDATE == StatsStreamType.valueOf(item.stream))
                    .forEach(item -> {
                        Assert.assertEquals(CacheFilterBolt.Commands.UPDATE, item.values.get(0));
                        Assert.assertEquals(flowId, item.values.get(1));
                        Assert.assertEquals(switchId, item.values.get(2));
                        MeasurePoint affectedPoint = (MeasurePoint) item.values.get(4);

                        seenEvents.add(affectedPoint);
                    });

            Assert.assertEquals(expectedEvents, seenEvents);
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
     * We should create child with these overridden methods because we don't want to use real kafka instance.
     */
    private class TestingTargetTopology extends StatsTopology {

        private KafkaBolt kafkaBolt;

        TestingTargetTopology(LaunchEnvironment launchEnvironment, KafkaBolt kafkaBolt)
                throws Exception {
            super(launchEnvironment);
            this.kafkaBolt = kafkaBolt;
        }

        @Override
        protected void checkAndCreateTopic(String topic) {
        }

        @Override
        public String getDefaultTopologyName() {
            return StatsTopology.class.getSimpleName().toLowerCase();
        }

        @Override
        protected KafkaBolt createKafkaBolt(String topic) {
            return kafkaBolt;
        }

    }
}
