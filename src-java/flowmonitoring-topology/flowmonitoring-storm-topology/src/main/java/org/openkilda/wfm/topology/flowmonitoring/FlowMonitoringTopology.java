/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.flowmonitoring;

import static org.openkilda.wfm.topology.flowmonitoring.FlowMonitoringTopology.Stream.ACTION_STREAM_ID;
import static org.openkilda.wfm.topology.flowmonitoring.FlowMonitoringTopology.Stream.FLOW_UPDATE_STREAM_ID;
import static org.openkilda.wfm.topology.flowmonitoring.bolt.FlowCacheBolt.FLOW_ID_FIELD;

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.spi.PersistenceProvider;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.share.zk.ZooKeeperSpout;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.flowmonitoring.bolt.ActionBolt;
import org.openkilda.wfm.topology.flowmonitoring.bolt.FlowCacheBolt;
import org.openkilda.wfm.topology.flowmonitoring.bolt.IslCacheBolt;
import org.openkilda.wfm.topology.flowmonitoring.bolt.RerouteEncoder;
import org.openkilda.wfm.topology.flowmonitoring.bolt.TickBolt;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;

import java.time.Duration;

public class FlowMonitoringTopology extends AbstractTopology<FlowMonitoringTopologyConfig> {

    private static final Fields FLOW_ID_FIELDS = new Fields(FLOW_ID_FIELD);

    public FlowMonitoringTopology(LaunchEnvironment env) {
        super(env, "flowmonitoring-topology", FlowMonitoringTopologyConfig.class);
    }

    @Override
    public StormTopology createTopology() {
        TopologyBuilder tb = new TopologyBuilder();

        flowSpout(tb);
        flowLatencySpout(tb);
        islSpout(tb);
        islLatencySpout(tb);

        zooKeeperSpout(tb);

        tickBolt(tb);

        PersistenceManager persistenceManager =
                PersistenceProvider.getInstance().getPersistenceManager(configurationProvider);

        flowCacheBolt(tb, persistenceManager);
        islCacheBolt(tb, persistenceManager);

        actionBolt(tb, persistenceManager);
        outputReroute(tb);

        zooKeeperBolt(tb);

        return tb.createTopology();
    }

    private void flowSpout(TopologyBuilder topologyBuilder) {
        declareKafkaSpout(topologyBuilder, getConfig().getKafkaFlowHsToFlowMonitoringTopic(),
                ComponentId.FLOW_SPOUT.name());
    }

    private void flowLatencySpout(TopologyBuilder topologyBuilder) {
        declareKafkaSpout(topologyBuilder, getConfig().getServer42StatsFlowRttTopic(),
                ComponentId.FLOW_LATENCY_SPOUT.name());
    }

    private void islSpout(TopologyBuilder topologyBuilder) {
        declareKafkaSpout(topologyBuilder, getConfig().getNetworkFlowMonitoringNotifyTopic(),
                ComponentId.ISL_SPOUT.name());
    }

    private void islLatencySpout(TopologyBuilder topologyBuilder) {
        declareKafkaSpout(topologyBuilder, getConfig().getTopoIslLatencyTopic(), ComponentId.ISL_LATENCY_SPOUT.name());
    }

    private void tickBolt(TopologyBuilder topologyBuilder) {
        declareBolt(topologyBuilder, new TickBolt(topologyConfig.getFlowSlaCheckIntervalSeconds()),
                ComponentId.TICK_BOLT.name());
    }

    private void flowCacheBolt(TopologyBuilder topologyBuilder, PersistenceManager persistenceManager) {
        FlowCacheBolt flowCacheBolt = new FlowCacheBolt(persistenceManager,
                getConfig().getFlowRttStatsExpirationSeconds(), ZooKeeperSpout.SPOUT_ID);
        declareBolt(topologyBuilder, flowCacheBolt, ComponentId.FLOW_CACHE_BOLT.name())
                .allGrouping(ComponentId.FLOW_SPOUT.name())
                .allGrouping(ComponentId.FLOW_LATENCY_SPOUT.name())
                .allGrouping(ComponentId.TICK_BOLT.name())
                .allGrouping(ZooKeeperSpout.SPOUT_ID);
    }

    private void islCacheBolt(TopologyBuilder topologyBuilder, PersistenceManager persistenceManager) {
        IslCacheBolt islCacheBolt = new IslCacheBolt(persistenceManager, getConfig().getIslRttLatencyExpiration(),
                ZooKeeperSpout.SPOUT_ID);
        declareBolt(topologyBuilder, islCacheBolt, ComponentId.ISL_CACHE_BOLT.name())
                .allGrouping(ComponentId.ISL_LATENCY_SPOUT.name())
                .allGrouping(ComponentId.ISL_SPOUT.name())
                .allGrouping(ComponentId.FLOW_CACHE_BOLT.name())
                .allGrouping(ZooKeeperSpout.SPOUT_ID);
    }

    private void actionBolt(TopologyBuilder topologyBuilder, PersistenceManager persistenceManager) {
        declareBolt(topologyBuilder, new ActionBolt(persistenceManager,
                        Duration.ofSeconds(getConfig().getFlowLatencySlaTimeoutSeconds()),
                        getConfig().getFlowLatencySlaThreshold()),
                ComponentId.ACTION_BOLT.name())
                .fieldsGrouping(ComponentId.FLOW_CACHE_BOLT.name(), ACTION_STREAM_ID.name(), FLOW_ID_FIELDS)
                .fieldsGrouping(ComponentId.FLOW_CACHE_BOLT.name(), FLOW_UPDATE_STREAM_ID.name(), FLOW_ID_FIELDS)
                .fieldsGrouping(ComponentId.ISL_CACHE_BOLT.name(), ACTION_STREAM_ID.name(), FLOW_ID_FIELDS);
    }

    private void outputReroute(TopologyBuilder topology) {
        RerouteEncoder bolt = new RerouteEncoder();
        declareBolt(topology, bolt, RerouteEncoder.BOLT_ID)
                .shuffleGrouping(ComponentId.ACTION_BOLT.name());

        KafkaBolt output = buildKafkaBolt(getConfig().getKafkaTopoRerouteTopic());
        declareBolt(topology, output, ComponentId.REROUTE_BOLT.name())
                .shuffleGrouping(RerouteEncoder.BOLT_ID);
    }

    private void zooKeeperSpout(TopologyBuilder topology) {
        ZooKeeperSpout zooKeeperSpout = new ZooKeeperSpout(getConfig().getBlueGreenMode(), getZkTopoName(),
                getZookeeperConfig());
        declareSpout(topology, zooKeeperSpout, ZooKeeperSpout.SPOUT_ID);
    }

    private void zooKeeperBolt(TopologyBuilder topology) {
        ZooKeeperBolt zooKeeperBolt = new ZooKeeperBolt(getConfig().getBlueGreenMode(), getZkTopoName(),
                getZookeeperConfig(),
                getBoltInstancesCount(ComponentId.ISL_CACHE_BOLT.name(), ComponentId.FLOW_CACHE_BOLT.name()));
        declareBolt(topology, zooKeeperBolt, ZooKeeperBolt.BOLT_ID)
                .allGrouping(ComponentId.ISL_CACHE_BOLT.name(), ZkStreams.ZK.toString())
                .allGrouping(ComponentId.FLOW_CACHE_BOLT.name(), ZkStreams.ZK.toString());
    }

    @Override
    protected String getZkTopoName() {
        return "flowmonitoring";
    }

    public enum ComponentId {
        FLOW_SPOUT("flow.spout"),
        FLOW_LATENCY_SPOUT("flow.latency.spout"),
        ISL_SPOUT("isl.spout"),
        ISL_LATENCY_SPOUT("isl.latency.spout"),

        FLOW_CACHE_BOLT("flow.cache.bolt"),
        ISL_CACHE_BOLT("isl.cache.bolt"),
        ACTION_BOLT("action.bolt"),

        REROUTE_ENCODER("reroute.encoder"),
        REROUTE_BOLT("reroute.bolt"),

        TICK_BOLT("tick.bolt");

        private final String value;

        ComponentId(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }

    }

    public enum Stream {
        ACTION_STREAM_ID,
        FLOW_UPDATE_STREAM_ID
    }

    /**
     * Launches and sets up the topology.
     *
     * @param args the command-line arguments.
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            new FlowMonitoringTopology(env).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
