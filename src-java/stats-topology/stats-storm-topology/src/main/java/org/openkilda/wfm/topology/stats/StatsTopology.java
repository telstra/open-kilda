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

package org.openkilda.wfm.topology.stats;

import static org.openkilda.wfm.topology.stats.StatsTopology.ComponentId.FLOW_STATS_METRIC_GEN_BOLT;
import static org.openkilda.wfm.topology.stats.StatsTopology.ComponentId.METER_CFG_STATS_METRIC_GEN_BOLT;
import static org.openkilda.wfm.topology.stats.StatsTopology.ComponentId.METER_STATS_METRIC_GEN_BOLT;
import static org.openkilda.wfm.topology.stats.StatsTopology.ComponentId.PACKET_IN_OUT_STATS_METRIC_GEN_BOLT;
import static org.openkilda.wfm.topology.stats.StatsTopology.ComponentId.PORT_STATS_METRIC_GEN_BOLT;
import static org.openkilda.wfm.topology.stats.StatsTopology.ComponentId.SERVER42_STATS_FLOW_RTT_METRIC_GEN;
import static org.openkilda.wfm.topology.stats.StatsTopology.ComponentId.SERVER42_STATS_FLOW_RTT_SPOUT;
import static org.openkilda.wfm.topology.stats.StatsTopology.ComponentId.STATS_FLOW_CACHE_BOLT;
import static org.openkilda.wfm.topology.stats.StatsTopology.ComponentId.STATS_FLOW_NOTIFY_SPOUT;
import static org.openkilda.wfm.topology.stats.StatsTopology.ComponentId.STATS_GRPC_SPEAKER_BOLT;
import static org.openkilda.wfm.topology.stats.StatsTopology.ComponentId.STATS_KILDA_SPEAKER_BOLT;
import static org.openkilda.wfm.topology.stats.StatsTopology.ComponentId.STATS_OFS_KAFKA_SPOUT;
import static org.openkilda.wfm.topology.stats.StatsTopology.ComponentId.STATS_OFS_ROUTER_BOLT;
import static org.openkilda.wfm.topology.stats.StatsTopology.ComponentId.STATS_OPENTSDB_BOLT;
import static org.openkilda.wfm.topology.stats.StatsTopology.ComponentId.STATS_REQUESTER_BOLT;
import static org.openkilda.wfm.topology.stats.StatsTopology.ComponentId.SYSTEM_RULE_STATS_METRIC_GEN_BOLT;
import static org.openkilda.wfm.topology.stats.StatsTopology.ComponentId.TABLE_STATS_METRIC_GEN_BOLT;
import static org.openkilda.wfm.topology.stats.StatsTopology.ComponentId.TICK_BOLT;

import org.openkilda.messaging.Message;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.spi.PersistenceProvider;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.share.zk.ZooKeeperSpout;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.stats.bolts.FlowCacheBolt;
import org.openkilda.wfm.topology.stats.bolts.SpeakerStatsRouterBolt;
import org.openkilda.wfm.topology.stats.bolts.StatsRequesterBolt;
import org.openkilda.wfm.topology.stats.bolts.TickBolt;
import org.openkilda.wfm.topology.stats.bolts.metrics.FlowMetricGenBolt;
import org.openkilda.wfm.topology.stats.bolts.metrics.FlowRttMetricGenBolt;
import org.openkilda.wfm.topology.stats.bolts.metrics.MeterConfigMetricGenBolt;
import org.openkilda.wfm.topology.stats.bolts.metrics.MeterStatsMetricGenBolt;
import org.openkilda.wfm.topology.stats.bolts.metrics.PacketInOutMetricGenBolt;
import org.openkilda.wfm.topology.stats.bolts.metrics.PortMetricGenBolt;
import org.openkilda.wfm.topology.stats.bolts.metrics.SystemRuleMetricGenBolt;
import org.openkilda.wfm.topology.stats.bolts.metrics.TableStatsMetricGenBolt;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.kafka.spout.KafkaSpoutConfig;
import org.apache.storm.topology.TopologyBuilder;

public class StatsTopology extends AbstractTopology<StatsTopologyConfig> {
    public static final String STATS_FIELD = "stats";

    public StatsTopology(LaunchEnvironment env) {
        super(env, "stats-topology", StatsTopologyConfig.class);
    }

    @Override
    public StormTopology createTopology() {
        TopologyBuilder tb = new TopologyBuilder();

        zooKeeperSpout(tb);

        PersistenceManager persistenceManager = PersistenceProvider.loadAndMakeDefault(configurationProvider);
        statsRequesterBolts(tb, persistenceManager);

        incomingOfStatsSpoutAndRouterBolt(tb);
        incomingServer42StatsSpoutAndBolt(tb);

        flowNotifySpoutAndCacheBolt(tb, persistenceManager);

        outgoingStatsBolts(tb);
        outgoingStatsWithCacheBolts(tb);
        openTsdbBolt(tb);

        zooKeeperBolt(tb);

        return tb.createTopology();
    }

    private void statsRequesterBolts(TopologyBuilder topologyBuilder, PersistenceManager persistenceManager) {
        declareBolt(topologyBuilder,
                new TickBolt(topologyConfig.getStatisticsRequestInterval()), TICK_BOLT.name());

        declareBolt(topologyBuilder,
                new StatsRequesterBolt(persistenceManager, ZooKeeperSpout.SPOUT_ID), STATS_REQUESTER_BOLT.name())
                .shuffleGrouping(TICK_BOLT.name())
                .allGrouping(ZooKeeperSpout.SPOUT_ID);

        declareBolt(topologyBuilder,
                buildKafkaBolt(topologyConfig.getSpeakerTopic()), STATS_KILDA_SPEAKER_BOLT.name())
                .shuffleGrouping(STATS_REQUESTER_BOLT.name(), StatsRequesterBolt.STATS_REQUEST_STREAM);
        declareBolt(topologyBuilder,
                buildKafkaBolt(topologyConfig.getGrpcSpeakerTopic()), STATS_GRPC_SPEAKER_BOLT.name())
                .shuffleGrouping(STATS_REQUESTER_BOLT.name(), StatsRequesterBolt.GRPC_REQUEST_STREAM);
    }

    private void incomingOfStatsSpoutAndRouterBolt(TopologyBuilder topologyBuilder) {
        declareKafkaSpout(topologyBuilder, topologyConfig.getKafkaStatsTopic(), STATS_OFS_KAFKA_SPOUT.name());

        declareBolt(topologyBuilder,
                new SpeakerStatsRouterBolt(ZooKeeperSpout.SPOUT_ID), STATS_OFS_ROUTER_BOLT.name())
                .shuffleGrouping(STATS_OFS_KAFKA_SPOUT.name())
                .allGrouping(ZooKeeperSpout.SPOUT_ID);
    }

    private void incomingServer42StatsSpoutAndBolt(TopologyBuilder topologyBuilder) {
        KafkaSpoutConfig<String, Message> config =
                getKafkaSpoutConfigBuilder(topologyConfig.getServer42StatsFlowRttTopic(),
                        SERVER42_STATS_FLOW_RTT_SPOUT.name())
                        .setFirstPollOffsetStrategy(KafkaSpoutConfig.FirstPollOffsetStrategy.UNCOMMITTED_EARLIEST)
                        .build();
        logger.info("Setup kafka spout: id={}, group={}, subscriptions={}",
                SERVER42_STATS_FLOW_RTT_SPOUT, config.getConsumerGroupId(), config.getSubscription().getTopicsString());
        declareSpout(topologyBuilder, new KafkaSpout<>(config), SERVER42_STATS_FLOW_RTT_SPOUT.name());

        declareBolt(topologyBuilder,
                new FlowRttMetricGenBolt(topologyConfig.getMetricPrefix(), ZooKeeperSpout.SPOUT_ID),
                SERVER42_STATS_FLOW_RTT_METRIC_GEN.name())
                .shuffleGrouping(SERVER42_STATS_FLOW_RTT_SPOUT.name())
                .allGrouping(ZooKeeperSpout.SPOUT_ID);
    }

    private void flowNotifySpoutAndCacheBolt(TopologyBuilder topologyBuilder, PersistenceManager persistenceManager) {
        declareKafkaSpout(topologyBuilder, topologyConfig.getFlowStatsNotifyTopic(),
                STATS_FLOW_NOTIFY_SPOUT.name());

        declareBolt(topologyBuilder,
                new FlowCacheBolt(persistenceManager, ZooKeeperSpout.SPOUT_ID), STATS_FLOW_CACHE_BOLT.name())
                .shuffleGrouping(STATS_FLOW_NOTIFY_SPOUT.name())
                .fieldsGrouping(STATS_OFS_ROUTER_BOLT.name(), SpeakerStatsRouterBolt.TO_CACHE_STREAM,
                        SpeakerStatsRouterBolt.STATS_FIELDS)
                .allGrouping(ZooKeeperSpout.SPOUT_ID);
    }

    private void outgoingStatsBolts(TopologyBuilder topologyBuilder) {
        declareBolt(topologyBuilder,
                new PortMetricGenBolt(topologyConfig.getMetricPrefix()), PORT_STATS_METRIC_GEN_BOLT.name())
                .fieldsGrouping(STATS_OFS_ROUTER_BOLT.name(), SpeakerStatsRouterBolt.PORT_STATS_STREAM,
                        SpeakerStatsRouterBolt.STATS_WITH_MESSAGE_FIELDS);
        declareBolt(topologyBuilder,
                new MeterConfigMetricGenBolt(topologyConfig.getMetricPrefix()), METER_CFG_STATS_METRIC_GEN_BOLT.name())
                .fieldsGrouping(STATS_OFS_ROUTER_BOLT.name(), SpeakerStatsRouterBolt.METER_CFG_STATS_STREAM,
                        SpeakerStatsRouterBolt.STATS_WITH_MESSAGE_FIELDS);
        declareBolt(topologyBuilder,
                new SystemRuleMetricGenBolt(topologyConfig.getMetricPrefix()), SYSTEM_RULE_STATS_METRIC_GEN_BOLT.name())
                .fieldsGrouping(STATS_OFS_ROUTER_BOLT.name(), SpeakerStatsRouterBolt.SYSTEM_RULES_STATS_STREAM,
                        SpeakerStatsRouterBolt.STATS_FIELDS);
        declareBolt(topologyBuilder,
                new TableStatsMetricGenBolt(topologyConfig.getMetricPrefix()), TABLE_STATS_METRIC_GEN_BOLT.name())
                .fieldsGrouping(STATS_OFS_ROUTER_BOLT.name(), SpeakerStatsRouterBolt.TABLE_STATS_STREAM,
                        SpeakerStatsRouterBolt.STATS_FIELDS);
        declareBolt(topologyBuilder,
                new PacketInOutMetricGenBolt(topologyConfig.getMetricPrefix()),
                PACKET_IN_OUT_STATS_METRIC_GEN_BOLT.name())
                .fieldsGrouping(STATS_OFS_ROUTER_BOLT.name(), SpeakerStatsRouterBolt.PACKET_IN_OUT_STATS_STREAM,
                        SpeakerStatsRouterBolt.STATS_FIELDS);
    }

    private void outgoingStatsWithCacheBolts(TopologyBuilder topologyBuilder) {
        declareBolt(topologyBuilder,
                new FlowMetricGenBolt(topologyConfig.getMetricPrefix()), FLOW_STATS_METRIC_GEN_BOLT.name())
                .fieldsGrouping(STATS_FLOW_CACHE_BOLT.name(), FlowCacheBolt.FLOW_STATS_STREAM,
                        FlowCacheBolt.STATS_WITH_CACHED_FIELDS);
        declareBolt(topologyBuilder,
                new MeterStatsMetricGenBolt(topologyConfig.getMetricPrefix()), METER_STATS_METRIC_GEN_BOLT.name())
                .fieldsGrouping(STATS_FLOW_CACHE_BOLT.name(), FlowCacheBolt.METER_STATS_STREAM,
                        FlowCacheBolt.STATS_WITH_CACHED_FIELDS);
    }

    private void openTsdbBolt(TopologyBuilder topologyBuilder) {
        declareBolt(topologyBuilder,
                createKafkaBolt(topologyConfig.getKafkaOtsdbTopic()), STATS_OPENTSDB_BOLT.name())
                .shuffleGrouping(PORT_STATS_METRIC_GEN_BOLT.name())
                .shuffleGrouping(METER_STATS_METRIC_GEN_BOLT.name())
                .shuffleGrouping(METER_CFG_STATS_METRIC_GEN_BOLT.name())
                .shuffleGrouping(FLOW_STATS_METRIC_GEN_BOLT.name())
                .shuffleGrouping(TABLE_STATS_METRIC_GEN_BOLT.name())
                .shuffleGrouping(SYSTEM_RULE_STATS_METRIC_GEN_BOLT.name())
                .shuffleGrouping(PACKET_IN_OUT_STATS_METRIC_GEN_BOLT.name())
                .shuffleGrouping(SERVER42_STATS_FLOW_RTT_METRIC_GEN.name());
    }

    private void zooKeeperSpout(TopologyBuilder topologyBuilder) {
        ZooKeeperSpout zooKeeperSpout = new ZooKeeperSpout(getConfig().getBlueGreenMode(), getZkTopoName(),
                getZookeeperConfig());
        declareSpout(topologyBuilder, zooKeeperSpout, ZooKeeperSpout.SPOUT_ID);
    }

    private void zooKeeperBolt(TopologyBuilder topologyBuilder) {
        ZooKeeperBolt zooKeeperBolt = new ZooKeeperBolt(getConfig().getBlueGreenMode(), getZkTopoName(),
                getZookeeperConfig(), getBoltInstancesCount(STATS_REQUESTER_BOLT.name(),
                STATS_OFS_ROUTER_BOLT.name(), SERVER42_STATS_FLOW_RTT_METRIC_GEN.name(), STATS_FLOW_CACHE_BOLT.name()));
        declareBolt(topologyBuilder, zooKeeperBolt, ZooKeeperBolt.BOLT_ID)
                .allGrouping(STATS_REQUESTER_BOLT.name(), StatsRequesterBolt.ZOOKEEPER_STREAM)
                .allGrouping(STATS_OFS_ROUTER_BOLT.name(), SpeakerStatsRouterBolt.ZOOKEEPER_STREAM)
                .allGrouping(SERVER42_STATS_FLOW_RTT_METRIC_GEN.name(), FlowRttMetricGenBolt.ZOOKEEPER_STREAM)
                .allGrouping(STATS_FLOW_CACHE_BOLT.name(), FlowCacheBolt.ZOOKEEPER_STREAM);
    }

    @Override
    protected String getZkTopoName() {
        return "stats";
    }

    public enum ComponentId {
        TICK_BOLT,
        STATS_REQUESTER_BOLT,
        STATS_KILDA_SPEAKER_BOLT,
        STATS_GRPC_SPEAKER_BOLT,

        STATS_OFS_KAFKA_SPOUT,
        STATS_OFS_ROUTER_BOLT,

        SERVER42_STATS_FLOW_RTT_SPOUT,
        SERVER42_STATS_FLOW_RTT_METRIC_GEN,

        PORT_STATS_METRIC_GEN_BOLT,
        METER_STATS_METRIC_GEN_BOLT,
        METER_CFG_STATS_METRIC_GEN_BOLT,
        SYSTEM_RULE_STATS_METRIC_GEN_BOLT,
        FLOW_STATS_METRIC_GEN_BOLT,
        TABLE_STATS_METRIC_GEN_BOLT,
        PACKET_IN_OUT_STATS_METRIC_GEN_BOLT,

        STATS_FLOW_CACHE_BOLT,
        STATS_FLOW_NOTIFY_SPOUT,

        STATS_OPENTSDB_BOLT
    }

    /**
     * The topology deployer entry-point.
     *
     * @param args the command-line arguments.
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new StatsTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
