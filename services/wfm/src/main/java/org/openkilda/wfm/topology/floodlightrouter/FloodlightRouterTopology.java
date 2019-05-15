/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.floodlightrouter;

import org.openkilda.config.KafkaTopicsConfig;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.spi.PersistenceProvider;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.floodlightrouter.bolts.DiscoveryBolt;
import org.openkilda.wfm.topology.floodlightrouter.bolts.ReplyBolt;
import org.openkilda.wfm.topology.floodlightrouter.bolts.RequestBolt;
import org.openkilda.wfm.topology.floodlightrouter.bolts.SpeakerRequestBolt;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.topology.TopologyBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


/**
 * Floodlight topology.
 */
public class FloodlightRouterTopology extends AbstractTopology<FloodlightRouterTopologyConfig> {
    private final PersistenceManager persistenceManager;

    public FloodlightRouterTopology(LaunchEnvironment env) {
        super(env, FloodlightRouterTopologyConfig.class);

        persistenceManager = PersistenceProvider.getInstance().createPersistenceManager(configurationProvider);
    }

    private void createKildaFlowSpout(TopologyBuilder builder, int parallelism, List<String> kildaFlowTopics) {
        KafkaSpout kildaFlowSpout = createKafkaSpout(kildaFlowTopics,
                ComponentType.KILDA_FLOW_KAFKA_SPOUT);
        builder.setSpout(ComponentType.KILDA_FLOW_KAFKA_SPOUT, kildaFlowSpout, parallelism);
    }

    private void createKildaFlowKafkaBolt(TopologyBuilder builder, int parallelism, KafkaTopicsConfig topicsConfig) {
        KafkaBolt kildaFlowKafkaBolt = createKafkaBolt(topicsConfig.getFlowTopic());
        builder.setBolt(ComponentType.KILDA_FLOW_KAFKA_BOLT, kildaFlowKafkaBolt, parallelism)
                .shuffleGrouping(ComponentType.KILDA_FLOW_REPLY_BOLT, Stream.KILDA_FLOW);
    }

    private void createKildaFlowReplyStream(TopologyBuilder builder, int parallelism, KafkaTopicsConfig topicsConfig,
                                            List<String> kildaFlowTopics) {
        createKildaFlowSpout(builder, parallelism, kildaFlowTopics);
        createKildaFlowKafkaBolt(builder, parallelism, topicsConfig);

        ReplyBolt replyBolt = new ReplyBolt(Stream.KILDA_FLOW);
        builder.setBolt(ComponentType.KILDA_FLOW_REPLY_BOLT, replyBolt, parallelism)
                .shuffleGrouping(ComponentType.KILDA_FLOW_KAFKA_SPOUT);

    }

    private void createKildaPingSpout(TopologyBuilder builder, int parallelism, List<String> kildaPingTopics) {
        KafkaSpout kildaPingSpout = createKafkaSpout(kildaPingTopics,
                ComponentType.KILDA_PING_KAFKA_SPOUT);
        builder.setSpout(ComponentType.KILDA_PING_KAFKA_SPOUT, kildaPingSpout, parallelism);
    }

    private void createKildaPingKafkaBolt(TopologyBuilder builder, int parallelism, KafkaTopicsConfig topicsConfig) {
        KafkaBolt kildaPingKafkaBolt = createKafkaBolt(topicsConfig.getPingTopic());
        builder.setBolt(ComponentType.KILDA_PING_KAFKA_BOLT, kildaPingKafkaBolt, parallelism)
                .shuffleGrouping(ComponentType.KILDA_PING_REPLY_BOLT, Stream.KILDA_PING);
    }


    private void createKildaPingReplyStream(TopologyBuilder builder, int parallelism, KafkaTopicsConfig topicsConfig,
                                            List<String> kildaPingTopics) {
        createKildaPingSpout(builder, parallelism, kildaPingTopics);
        createKildaPingKafkaBolt(builder, parallelism, topicsConfig);

        ReplyBolt replyBolt = new ReplyBolt(Stream.KILDA_PING);
        builder.setBolt(ComponentType.KILDA_PING_REPLY_BOLT, replyBolt, parallelism)
                .shuffleGrouping(ComponentType.KILDA_PING_KAFKA_SPOUT);

    }

    private void createKildaStatsSpout(TopologyBuilder builder, int parallelism, List<String> kildaStatsTopics) {
        KafkaSpout kildaStatsSpout = createKafkaSpout(kildaStatsTopics,
                ComponentType.KILDA_STATS_KAFKA_SPOUT);
        builder.setSpout(ComponentType.KILDA_STATS_KAFKA_SPOUT, kildaStatsSpout, parallelism);
    }

    private void createKildaStatsKafkaBolt(TopologyBuilder builder, int parallelism, KafkaTopicsConfig topicsConfig) {
        KafkaBolt kildaStatsKafkaBolt = createKafkaBolt(topicsConfig.getStatsTopic());
        builder.setBolt(ComponentType.KILDA_STATS_KAFKA_BOLT, kildaStatsKafkaBolt, parallelism)
                .shuffleGrouping(ComponentType.KILDA_STATS_REPLY_BOLT, Stream.KILDA_STATS);
    }


    private void createKildaStatsReplyStream(TopologyBuilder builder, int parallelism, KafkaTopicsConfig topicsConfig,
                                            List<String> kildaStatsTopics) {
        createKildaStatsSpout(builder, parallelism, kildaStatsTopics);
        createKildaStatsKafkaBolt(builder, parallelism, topicsConfig);

        ReplyBolt replyBolt = new ReplyBolt(Stream.KILDA_STATS);
        builder.setBolt(ComponentType.KILDA_STATS_REPLY_BOLT, replyBolt, parallelism)
                .shuffleGrouping(ComponentType.KILDA_STATS_KAFKA_SPOUT);

    }

    private void createKildaSwitchManagerSpout(TopologyBuilder builder, int parallelism,
                                               List<String> kildaSwitchManagerTopics) {
        KafkaSpout kildaSwitchManagerSpout = createKafkaSpout(kildaSwitchManagerTopics,
                ComponentType.KILDA_SWITCH_MANAGER_KAFKA_SPOUT);
        builder.setSpout(ComponentType.KILDA_SWITCH_MANAGER_KAFKA_SPOUT, kildaSwitchManagerSpout, parallelism);
    }

    private void createKildaSwitchManagerKafkaBolt(TopologyBuilder builder, int parallelism,
                                                   KafkaTopicsConfig topicsConfig) {
        KafkaBolt kildaSwitchManagerKafkaBolt = createKafkaBolt(topicsConfig.getTopoSwitchManagerTopic());
        builder.setBolt(ComponentType.KILDA_SWITCH_MANAGER_KAFKA_BOLT, kildaSwitchManagerKafkaBolt, parallelism)
                .shuffleGrouping(ComponentType.KILDA_SWITCH_MANAGER_REPLY_BOLT, Stream.KILDA_SWITCH_MANAGER);
    }


    private void createKildaSwitchManagerReplyStream(TopologyBuilder builder, int parallelism,
                                                     KafkaTopicsConfig topicsConfig,
                                                     List<String> kildaSwitchManagerTopics) {
        createKildaSwitchManagerSpout(builder, parallelism, kildaSwitchManagerTopics);
        createKildaSwitchManagerKafkaBolt(builder, parallelism, topicsConfig);

        ReplyBolt replyBolt = new ReplyBolt(Stream.KILDA_SWITCH_MANAGER);
        builder.setBolt(ComponentType.KILDA_SWITCH_MANAGER_REPLY_BOLT, replyBolt, parallelism)
                .shuffleGrouping(ComponentType.KILDA_SWITCH_MANAGER_KAFKA_SPOUT);
    }

    private void createKildaNorthboundSpout(TopologyBuilder builder, int parallelism,
                                               List<String> kildaNorthboundTopics) {
        KafkaSpout kildaNorthboundSpout = createKafkaSpout(kildaNorthboundTopics,
                ComponentType.NORTHBOUND_REPLY_KAFKA_SPOUT);
        builder.setSpout(ComponentType.NORTHBOUND_REPLY_KAFKA_SPOUT, kildaNorthboundSpout, parallelism);
    }

    private void createKildaNorthboundKafkaBolt(TopologyBuilder builder, int parallelism,
                                                   KafkaTopicsConfig topicsConfig) {
        KafkaBolt kildaNorthboundKafkaBolt = createKafkaBolt(topicsConfig.getNorthboundTopic());
        builder.setBolt(ComponentType.NORTHBOUND_REPLY_KAFKA_BOLT, kildaNorthboundKafkaBolt, parallelism)
                .shuffleGrouping(ComponentType.SPEAKER_REQUEST_BOLT, Stream.NORTHBOUND_REPLY)
                .shuffleGrouping(ComponentType.NORTHBOUND_REPLY_BOLT, Stream.NORTHBOUND_REPLY);
    }


    private void createKildaNorthboundReplyStream(TopologyBuilder builder, int parallelism,
                                                     KafkaTopicsConfig topicsConfig,
                                                     List<String> kildaNorthboundTopics) {
        createKildaNorthboundSpout(builder, parallelism, kildaNorthboundTopics);
        createKildaNorthboundKafkaBolt(builder, parallelism, topicsConfig);

        ReplyBolt replyBolt = new ReplyBolt(Stream.NORTHBOUND_REPLY);
        builder.setBolt(ComponentType.NORTHBOUND_REPLY_BOLT, replyBolt, parallelism)
                .shuffleGrouping(ComponentType.NORTHBOUND_REPLY_KAFKA_SPOUT);
    }

    private void createKildaNbWorkerSpout(TopologyBuilder builder, int parallelism,
                                            List<String> kildaNbWorkerTopics) {
        KafkaSpout kildaNbWorkerSpout = createKafkaSpout(kildaNbWorkerTopics,
                ComponentType.KILDA_NB_WORKER_KAFKA_SPOUT);
        builder.setSpout(ComponentType.KILDA_NB_WORKER_KAFKA_SPOUT, kildaNbWorkerSpout, parallelism);
    }

    private void createKildaNbWorkerKafkaBolt(TopologyBuilder builder, int parallelism,
                                                KafkaTopicsConfig topicsConfig) {
        KafkaBolt kildaNbWorkerKafkaBolt = createKafkaBolt(topicsConfig.getTopoNbTopic());
        builder.setBolt(ComponentType.KILDA_NB_WORKER_KAFKA_BOLT, kildaNbWorkerKafkaBolt, parallelism)
                .shuffleGrouping(ComponentType.SPEAKER_REQUEST_BOLT, Stream.NB_WORKER)
                .shuffleGrouping(ComponentType.KILDA_NB_WORKER_REPLY_BOLT, Stream.NB_WORKER);
    }


    private void createKildaNbWorkerReplyStream(TopologyBuilder builder, int parallelism,
                                                  KafkaTopicsConfig topicsConfig,
                                                  List<String> kildaNbWorkerTopics) {
        createKildaNbWorkerSpout(builder, parallelism, kildaNbWorkerTopics);
        createKildaNbWorkerKafkaBolt(builder, parallelism, topicsConfig);

        ReplyBolt replyBolt = new ReplyBolt(Stream.NB_WORKER);
        builder.setBolt(ComponentType.KILDA_NB_WORKER_REPLY_BOLT, replyBolt, parallelism)
                .shuffleGrouping(ComponentType.KILDA_NB_WORKER_KAFKA_SPOUT);
    }



    private void createSpeakerFlowRequestStream(TopologyBuilder builder, int parallelism,
                                                KafkaTopicsConfig topicsConfig) {
        KafkaSpout speakerFlowKafkaSpout = createKafkaSpout(topicsConfig.getSpeakerFlowTopic(),
                ComponentType.SPEAKER_FLOW_KAFKA_SPOUT);
        builder.setSpout(ComponentType.SPEAKER_FLOW_KAFKA_SPOUT, speakerFlowKafkaSpout);

        for (String region: topologyConfig.getFloodlightRegions()) {
            KafkaBolt speakerFlowKafkaBolt = createKafkaBolt(
                    Stream.formatWithRegion(topicsConfig.getSpeakerFlowRegionTopic(), region));
            builder.setBolt(Stream.formatWithRegion(ComponentType.SPEAKER_FLOW_KAFKA_BOLT, region),
                    speakerFlowKafkaBolt, parallelism)
                    .shuffleGrouping(ComponentType.SPEAKER_FLOW_REQUEST_BOLT,
                            Stream.formatWithRegion(Stream.SPEAKER_FLOW, region));
        }

        RequestBolt speakerFlowRequestBolt = new RequestBolt(Stream.SPEAKER_FLOW,
                topologyConfig.getFloodlightRegions());
        builder.setBolt(ComponentType.SPEAKER_FLOW_REQUEST_BOLT, speakerFlowRequestBolt, parallelism)
                .shuffleGrouping(ComponentType.SPEAKER_FLOW_KAFKA_SPOUT)
                .shuffleGrouping(ComponentType.KILDA_TOPO_DISCO_BOLT, Stream.REGION_NOTIFICATION);
    }

    private void createSpeakerFlowPingRequestStream(TopologyBuilder builder, int parallelism,
                                                KafkaTopicsConfig topicsConfig) {
        KafkaSpout speakerPingKafkaSpout = createKafkaSpout(topicsConfig.getSpeakerFlowPingTopic(),
                ComponentType.SPEAKER_PING_KAFKA_SPOUT);
        builder.setSpout(ComponentType.SPEAKER_PING_KAFKA_SPOUT, speakerPingKafkaSpout);

        for (String region: topologyConfig.getFloodlightRegions()) {
            KafkaBolt speakerPingKafkaBolt = createKafkaBolt(
                    Stream.formatWithRegion(topicsConfig.getSpeakerFlowPingRegionTopic(), region));
            builder.setBolt(Stream.formatWithRegion(ComponentType.SPEAKER_PING_KAFKA_BOLT, region),
                    speakerPingKafkaBolt, parallelism)
                    .shuffleGrouping(ComponentType.SPEAKER_PING_REQUEST_BOLT,
                            Stream.formatWithRegion(Stream.SPEAKER_PING, region));
        }

        RequestBolt speakerPingRequestBolt = new RequestBolt(Stream.SPEAKER_PING,
                topologyConfig.getFloodlightRegions());
        builder.setBolt(ComponentType.SPEAKER_PING_REQUEST_BOLT, speakerPingRequestBolt, parallelism)
                .shuffleGrouping(ComponentType.SPEAKER_PING_KAFKA_SPOUT)
                .shuffleGrouping(ComponentType.KILDA_TOPO_DISCO_BOLT, Stream.REGION_NOTIFICATION);
    }

    private void createSpeakerRequestStream(TopologyBuilder builder, int parallelism,
                                                    KafkaTopicsConfig topicsConfig) {
        KafkaSpout speakerKafkaSpout = createKafkaSpout(topicsConfig.getSpeakerTopic(),
                ComponentType.SPEAKER_KAFKA_SPOUT);
        builder.setSpout(ComponentType.SPEAKER_KAFKA_SPOUT, speakerKafkaSpout);

        for (String region: topologyConfig.getFloodlightRegions()) {
            KafkaBolt speakerKafkaBolt = createKafkaBolt(
                    Stream.formatWithRegion(topicsConfig.getSpeakerRegionTopic(), region));
            builder.setBolt(Stream.formatWithRegion(ComponentType.SPEAKER_KAFKA_BOLT, region),
                    speakerKafkaBolt, parallelism)
                    .shuffleGrouping(ComponentType.SPEAKER_REQUEST_BOLT,
                            Stream.formatWithRegion(Stream.SPEAKER, region));
        }

        SpeakerRequestBolt speakerRequestBolt = new SpeakerRequestBolt(Stream.SPEAKER,
                topologyConfig.getFloodlightRegions());
        builder.setBolt(ComponentType.SPEAKER_REQUEST_BOLT, speakerRequestBolt, parallelism)
                .shuffleGrouping(ComponentType.SPEAKER_KAFKA_SPOUT)
                .shuffleGrouping(ComponentType.KILDA_TOPO_DISCO_BOLT, Stream.REGION_NOTIFICATION);
    }


    private void createKildaTopoDiscoSpout(TopologyBuilder builder, int parallelism,
                                           List<String> kildaTopoDiscoTopics) {
        KafkaSpout kildaTopoDiscoSpout = createKafkaSpout(kildaTopoDiscoTopics,
                ComponentType.KILDA_TOPO_DISCO_KAFKA_SPOUT);
        builder.setSpout(ComponentType.KILDA_TOPO_DISCO_KAFKA_SPOUT, kildaTopoDiscoSpout, parallelism);
    }

    private void createKildaTopoDiscoKafkaBolt(TopologyBuilder builder, int parallelism,
                                               KafkaTopicsConfig topicsConfig) {
        KafkaBolt kildaTopoDiscoKafkaBolt = createKafkaBolt(topicsConfig.getTopoDiscoTopic());
        builder.setBolt(ComponentType.KILDA_TOPO_DISCO_KAFKA_BOLT, kildaTopoDiscoKafkaBolt, parallelism)
                .shuffleGrouping(ComponentType.KILDA_TOPO_DISCO_BOLT, Stream.KILDA_TOPO_DISCO);
    }


    private void createKildaTopoDiscoReplyStream(TopologyBuilder builder, int parallelism,
                                                 KafkaTopicsConfig topicsConfig,
                                                 List<String> kildaTopoDiscoTopics) {
        createKildaTopoDiscoSpout(builder, parallelism, kildaTopoDiscoTopics);
        createKildaTopoDiscoKafkaBolt(builder, parallelism, topicsConfig);

        ReplyBolt replyBolt = new ReplyBolt(Stream.KILDA_TOPO_DISCO);
        builder.setBolt(ComponentType.KILDA_TOPO_DISCO_BOLT, replyBolt, parallelism)
                .shuffleGrouping(ComponentType.KILDA_TOPO_DISCO_KAFKA_SPOUT);
    }

    private void createSpeakerDiscoSpout(TopologyBuilder builder, int parallelism,
                                         String kildaTopoDiscoTopic) {
        KafkaSpout speakerDiscoSpout = createKafkaSpout(kildaTopoDiscoTopic,
                ComponentType.SPEAKER_DISCO_KAFKA_SPOUT);
        builder.setSpout(ComponentType.SPEAKER_DISCO_KAFKA_SPOUT, speakerDiscoSpout, parallelism);
    }

    private void createSpeakerDiscoKafkaBolt(TopologyBuilder builder, int parallelism,
                                               KafkaTopicsConfig topicsConfig) {
        for (String region: topologyConfig.getFloodlightRegions()) {
            KafkaBolt speakerDiscoKafkaBolt = createKafkaBolt(
                    Stream.formatWithRegion(topicsConfig.getSpeakerDiscoRegionTopic(), region));
            builder.setBolt(Stream.formatWithRegion(ComponentType.SPEAKER_DISCO_KAFKA_BOLT, region),
                    speakerDiscoKafkaBolt, parallelism)
                    .shuffleGrouping(ComponentType.KILDA_TOPO_DISCO_BOLT,
                            Stream.formatWithRegion(Stream.SPEAKER_DISCO, region));
        }
    }

    private void createDiscoveryPipelines(TopologyBuilder builder, int parallelism, KafkaTopicsConfig topicsConfig) {

        List<String> kildaTopoDiscoTopics = new ArrayList<>();
        for (String region : topologyConfig.getFloodlightRegions()) {
            kildaTopoDiscoTopics.add(Stream.formatWithRegion(topicsConfig.getTopoDiscoRegionTopic(), region));

        }
        createKildaTopoDiscoSpout(builder, parallelism, kildaTopoDiscoTopics);
        createKildaTopoDiscoKafkaBolt(builder, parallelism, topicsConfig);
        createSpeakerDiscoSpout(builder, parallelism, topicsConfig.getSpeakerDiscoTopic());
        createSpeakerDiscoKafkaBolt(builder, parallelism, topicsConfig);
        DiscoveryBolt discoveryBolt = new DiscoveryBolt(
                persistenceManager,
                topologyConfig.getFloodlightRegions(), topologyConfig.getFloodlightAliveTimeout(),
                topologyConfig.getFloodlightAliveInterval(), topologyConfig.getFloodlightDumpInterval());
        builder.setBolt(ComponentType.KILDA_TOPO_DISCO_BOLT, discoveryBolt, parallelism)
                .shuffleGrouping(ComponentType.KILDA_TOPO_DISCO_KAFKA_SPOUT)
                .shuffleGrouping(ComponentType.SPEAKER_DISCO_KAFKA_SPOUT);
    }


    @Override
    public StormTopology createTopology() {
        logger.info("Creating FlowTopology - {}", topologyName);

        TopologyBuilder builder = new TopologyBuilder();
        Integer parallelism = topologyConfig.getParallelism();
        KafkaTopicsConfig topicsConfig = topologyConfig.getKafkaTopics();
        Set<String> regions = topologyConfig.getFloodlightRegions();
        // Floodlight -- kilda.flow --> Router
        List<String> kildaFlowTopics = new ArrayList<>();
        for (String region: regions) {
            kildaFlowTopics.add(Stream.formatWithRegion(topicsConfig.getFlowRegionTopic(), region));
        }
        createKildaFlowReplyStream(builder, parallelism, topicsConfig, kildaFlowTopics);


        // Floodlight -- kilda.ping --> Router
        List<String> kildaPingTopics = new ArrayList<>();
        for (String region: regions) {
            kildaPingTopics.add(Stream.formatWithRegion(topicsConfig.getPingRegionTopic(), region));
        }
        createKildaPingReplyStream(builder, parallelism, topicsConfig, kildaPingTopics);

        // Floodlight -- kilda.stats --> Router
        List<String> kildaStatsTopics = new ArrayList<>();
        for (String region: regions) {
            kildaStatsTopics.add(Stream.formatWithRegion(topicsConfig.getStatsRegionTopic(), region));
        }
        createKildaStatsReplyStream(builder, parallelism, topicsConfig, kildaStatsTopics);

        // Floodlight -- kilda.topo.switch.manager --> Router
        List<String> kildaSwitchManagerTopics = new ArrayList<>();
        for (String region: regions) {
            kildaSwitchManagerTopics.add(
                    Stream.formatWithRegion(topicsConfig.getTopoSwitchManagerRegionTopic(), region));
        }
        createKildaSwitchManagerReplyStream(builder, parallelism, topicsConfig, kildaSwitchManagerTopics);

        // Floodlight -- kilda.northbound --> Router
        List<String> kildaNorthboundTopics = new ArrayList<>();
        for (String region: regions) {
            kildaNorthboundTopics.add(Stream.formatWithRegion(topicsConfig.getNorthboundRegionTopic(), region));
        }
        createKildaNorthboundReplyStream(builder, parallelism, topicsConfig, kildaNorthboundTopics);

        // Floodlight -- kilda.topo.nb --> Router
        List<String> kildaNbWorkerTopics = new ArrayList<>();
        for (String region: regions) {
            kildaNbWorkerTopics.add(Stream.formatWithRegion(topicsConfig.getTopoNbRegionTopic(), region));
        }
        createKildaNbWorkerReplyStream(builder, parallelism, topicsConfig, kildaNbWorkerTopics);

        // Part3 Request to Floodlights
        // Storm -- kilda.speaker.flow --> Floodlight
        createSpeakerFlowRequestStream(builder, parallelism, topicsConfig);

        // Storm -- kilda.speaker.flow.ping --> Floodlight
        createSpeakerFlowPingRequestStream(builder, parallelism, topicsConfig);

        // Storm -- kilda.speaker --> Floodlight
        createSpeakerRequestStream(builder, parallelism, topicsConfig);

        // Storm -- kilda.speaker.disco --> Floodlight
        // Storm <-- kilda.topo.disco -- Floodlight
        createDiscoveryPipelines(builder, parallelism, topicsConfig);

        return builder.createTopology();
    }

    /**
     * Topology entry point.
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new FloodlightRouterTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
