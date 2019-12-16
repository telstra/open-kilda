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

import static org.openkilda.wfm.AbstractBolt.FIELD_ID_CONTEXT;
import static org.openkilda.wfm.topology.stats.StatsComponentType.FLOW_STATS_METRIC_GEN;
import static org.openkilda.wfm.topology.stats.StatsComponentType.METER_CFG_STATS_METRIC_GEN;
import static org.openkilda.wfm.topology.stats.StatsComponentType.METER_STATS_METRIC_GEN;
import static org.openkilda.wfm.topology.stats.StatsComponentType.PACKET_IN_OUT_STATS_METRIC_GEN;
import static org.openkilda.wfm.topology.stats.StatsComponentType.PORT_STATS_METRIC_GEN;
import static org.openkilda.wfm.topology.stats.StatsComponentType.SERVER42_STATS_FLOW_RTT_METRIC_GEN;
import static org.openkilda.wfm.topology.stats.StatsComponentType.SERVER42_STATS_FLOW_RTT_SPOUT;
import static org.openkilda.wfm.topology.stats.StatsComponentType.STATS_CACHE_BOLT;
import static org.openkilda.wfm.topology.stats.StatsComponentType.STATS_CACHE_FILTER_BOLT;
import static org.openkilda.wfm.topology.stats.StatsComponentType.STATS_GRPC_SPEAKER_BOLT;
import static org.openkilda.wfm.topology.stats.StatsComponentType.STATS_KILDA_SPEAKER_BOLT;
import static org.openkilda.wfm.topology.stats.StatsComponentType.STATS_KILDA_SPEAKER_SPOUT;
import static org.openkilda.wfm.topology.stats.StatsComponentType.STATS_REQUESTER_BOLT;
import static org.openkilda.wfm.topology.stats.StatsComponentType.SYSTEM_RULE_STATS_METRIC_GEN;
import static org.openkilda.wfm.topology.stats.StatsComponentType.TABLE_STATS_METRIC_GEN;
import static org.openkilda.wfm.topology.stats.StatsComponentType.TICK_BOLT;
import static org.openkilda.wfm.topology.stats.StatsStreamType.CACHE_UPDATE;
import static org.openkilda.wfm.topology.stats.StatsStreamType.GRPC_REQUEST;
import static org.openkilda.wfm.topology.stats.StatsStreamType.STATS_REQUEST;
import static org.openkilda.wfm.topology.stats.bolts.CacheBolt.statsWithCacheFields;

import org.openkilda.config.KafkaTopicsConfig;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.spi.PersistenceProvider;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.stats.bolts.CacheBolt;
import org.openkilda.wfm.topology.stats.bolts.CacheFilterBolt;
import org.openkilda.wfm.topology.stats.bolts.SpeakerBolt;
import org.openkilda.wfm.topology.stats.bolts.SpeakerRequestDecoderBolt;
import org.openkilda.wfm.topology.stats.bolts.StatsRequesterBolt;
import org.openkilda.wfm.topology.stats.bolts.TickBolt;
import org.openkilda.wfm.topology.stats.metrics.FlowMetricGenBolt;
import org.openkilda.wfm.topology.stats.metrics.FlowRttMetricGenBolt;
import org.openkilda.wfm.topology.stats.metrics.MeterConfigMetricGenBolt;
import org.openkilda.wfm.topology.stats.metrics.MeterStatsMetricGenBolt;
import org.openkilda.wfm.topology.stats.metrics.PacketInOutMetricGenBolt;
import org.openkilda.wfm.topology.stats.metrics.PortMetricGenBolt;
import org.openkilda.wfm.topology.stats.metrics.SystemRuleMetricGenBolt;
import org.openkilda.wfm.topology.stats.metrics.TableStatsMetricGenBolt;
import org.openkilda.wfm.topology.utils.JsonKafkaTranslator;

import com.google.common.collect.ImmutableList;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.kafka.spout.KafkaSpoutConfig;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;

public class StatsTopology extends AbstractTopology<StatsTopologyConfig> {
    public static final String STATS_FIELD = "stats";
    public static final Fields statsFields = new Fields(STATS_FIELD, FIELD_ID_CONTEXT);

    public StatsTopology(LaunchEnvironment env) {
        super(env, "stats-topology", StatsTopologyConfig.class);
    }

    /**
     * main.
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new StatsTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }

    @Override
    public StormTopology createTopology() {
        logger.info("Creating StatsTopology - {}", topologyName);

        TopologyBuilder builder = new TopologyBuilder();

        final String kafkaSpoutId = StatsComponentType.STATS_OFS_KAFKA_SPOUT.toString();
        declareKafkaSpout(builder, topologyConfig.getKafkaStatsTopic(), kafkaSpoutId);

        SpeakerBolt speakerBolt = new SpeakerBolt();
        final String statsOfsBolt = StatsComponentType.STATS_OFS_BOLT.toString();
        declareBolt(builder, speakerBolt, statsOfsBolt)
                .shuffleGrouping(kafkaSpoutId);

        inputSpeakerRequests(builder);
        cacheSyncFilter(builder);

        // Cache bolt get data from the database on start
        PersistenceManager persistenceManager =
                PersistenceProvider.getInstance().getPersistenceManager(configurationProvider);
        declareBolt(builder, new CacheBolt(persistenceManager), STATS_CACHE_BOLT.name())
                .allGrouping(STATS_CACHE_FILTER_BOLT.name(), CACHE_UPDATE.name())
                .fieldsGrouping(statsOfsBolt, StatsStreamType.CACHE_DATA.toString(), statsFields);

        declareBolt(builder,
                new PortMetricGenBolt(topologyConfig.getMetricPrefix()), PORT_STATS_METRIC_GEN.name())
                .fieldsGrouping(statsOfsBolt, StatsStreamType.PORT_STATS.toString(), fieldMessage);
        declareBolt(builder,
                new MeterConfigMetricGenBolt(topologyConfig.getMetricPrefix()), METER_CFG_STATS_METRIC_GEN.name())
                .fieldsGrouping(statsOfsBolt, StatsStreamType.METER_CONFIG_STATS.toString(), fieldMessage);
        declareBolt(builder,
                new SystemRuleMetricGenBolt(topologyConfig.getMetricPrefix()), SYSTEM_RULE_STATS_METRIC_GEN.name())
                .fieldsGrouping(statsOfsBolt, StatsStreamType.SYSTEM_RULE_STATS.toString(), statsFields);
        declareBolt(builder,
                new TableStatsMetricGenBolt(topologyConfig.getMetricPrefix()), TABLE_STATS_METRIC_GEN.name())
                .fieldsGrouping(statsOfsBolt, StatsStreamType.TABLE_STATS.toString(), statsFields);
        declareBolt(builder,
                new PacketInOutMetricGenBolt(topologyConfig.getMetricPrefix()), PACKET_IN_OUT_STATS_METRIC_GEN.name())
                .fieldsGrouping(statsOfsBolt, StatsStreamType.PACKET_IN_OUT_STATS.toString(), statsFields);

        logger.debug("starting flow_stats_metric_gen");
        declareBolt(builder,
                new FlowMetricGenBolt(topologyConfig.getMetricPrefix()), FLOW_STATS_METRIC_GEN.name())
                .fieldsGrouping(STATS_CACHE_BOLT.name(), StatsStreamType.FLOW_STATS.toString(), statsWithCacheFields);
        declareBolt(builder,
                new MeterStatsMetricGenBolt(topologyConfig.getMetricPrefix()), METER_STATS_METRIC_GEN.name())
                .fieldsGrouping(STATS_CACHE_BOLT.name(), StatsStreamType.METER_STATS.toString(), statsWithCacheFields);

        declareBolt(builder,
                new TickBolt(topologyConfig.getStatisticsRequestInterval()), TICK_BOLT.name());

        declareBolt(builder, new StatsRequesterBolt(persistenceManager), STATS_REQUESTER_BOLT.name())
                .shuffleGrouping(TICK_BOLT.name());

        declareBolt(builder, buildKafkaBolt(topologyConfig.getSpeakerTopic()), STATS_KILDA_SPEAKER_BOLT.name())
                .shuffleGrouping(STATS_REQUESTER_BOLT.name(), STATS_REQUEST.name());
        declareBolt(builder, buildKafkaBolt(topologyConfig.getGrpcSpeakerTopic()), STATS_GRPC_SPEAKER_BOLT.name())
                .shuffleGrouping(STATS_REQUESTER_BOLT.name(), GRPC_REQUEST.name());

        declareKafkaSpout(builder, topologyConfig.getServer42StatsFlowRttTopic(),
                SERVER42_STATS_FLOW_RTT_SPOUT.name());

        declareBolt(builder,
                new FlowRttMetricGenBolt(topologyConfig.getMetricPrefix()), SERVER42_STATS_FLOW_RTT_METRIC_GEN.name())
                .shuffleGrouping(SERVER42_STATS_FLOW_RTT_SPOUT.name());

        String openTsdbTopic = topologyConfig.getKafkaOtsdbTopic();
        declareBolt(builder, createKafkaBolt(openTsdbTopic), "stats-opentsdb")
                .shuffleGrouping(PORT_STATS_METRIC_GEN.name())
                .shuffleGrouping(METER_STATS_METRIC_GEN.name())
                .shuffleGrouping(METER_CFG_STATS_METRIC_GEN.name())
                .shuffleGrouping(FLOW_STATS_METRIC_GEN.name())
                .shuffleGrouping(TABLE_STATS_METRIC_GEN.name())
                .shuffleGrouping(SYSTEM_RULE_STATS_METRIC_GEN.name())
                .shuffleGrouping(PACKET_IN_OUT_STATS_METRIC_GEN.name())
                .shuffleGrouping(SERVER42_STATS_FLOW_RTT_METRIC_GEN.name());

        return builder.createTopology();
    }

    /**
     * Capture and decode speaker requests (kilda.speaker.flow).
     */
    private void inputSpeakerRequests(TopologyBuilder topology) {
        String id = STATS_KILDA_SPEAKER_SPOUT.name();
        KafkaTopicsConfig topics = topologyConfig.getKafkaTopics();
        KafkaSpoutConfig<String, String> config = makeKafkaSpoutConfig(
                    ImmutableList.of(topics.getSpeakerFlowHsTopic()),
                    id, StringDeserializer.class)
                .setRecordTranslator(new JsonKafkaTranslator())
                .build();
        declareSpout(topology, new KafkaSpout<>(config), id);

        SpeakerRequestDecoderBolt decoder = new SpeakerRequestDecoderBolt();
        declareBolt(topology, decoder, SpeakerRequestDecoderBolt.BOLT_ID)
                .shuffleGrouping(id);
    }

    /**
     * CacheFilterBolt catch data from kilda.speaker spout and tried to find InstallEgressFlow
     * or InstallOneSwitchFlow and throw tuple to CacheBolt.
     */
    private void cacheSyncFilter(TopologyBuilder topology) {
        declareBolt(topology, new CacheFilterBolt(), STATS_CACHE_FILTER_BOLT.name())
                .shuffleGrouping(SpeakerRequestDecoderBolt.BOLT_ID, SpeakerRequestDecoderBolt.STREAM_GENERIC_ID)
                .shuffleGrouping(SpeakerRequestDecoderBolt.BOLT_ID, SpeakerRequestDecoderBolt.STREAM_HUB_AND_SPOKE_ID);
    }
}
