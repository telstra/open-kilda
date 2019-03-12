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
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.floodlightrouter.bolts.ReplyBolt;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.topology.TopologyBuilder;

import java.util.ArrayList;
import java.util.List;


/**
 * Floodlight topology.
 */
public class FloodlightRouterTopology extends AbstractTopology<FloodlightRouterTopologyConfig> {

    public FloodlightRouterTopology(LaunchEnvironment env) {
        super(env, FloodlightRouterTopologyConfig.class);
    }

    private void createKildaFlowSpout(TopologyBuilder builder, int parallelism, List<String> kildaFlowTopics) {
        KafkaSpout kildaFlowSpout = createKafkaSpout(kildaFlowTopics,
                ComponentType.KILDA_FLOW_KAFKA_SPOUT);
        builder.setSpout(ComponentType.KILDA_FLOW_KAFKA_SPOUT, kildaFlowSpout, parallelism);
    }

    private void createKildaFlowKafkaBolt(TopologyBuilder builder, int parallelism, KafkaTopicsConfig topicsConfig) {
        KafkaBolt kildaFlowKafkaBolt = createKafkaBolt(Stream.formatWithRegion(topicsConfig.getFlowTopic(),
                Stream.STORM_SUFFIX));
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
        KafkaBolt kildaPingKafkaBolt = createKafkaBolt(Stream.formatWithRegion(topicsConfig.getPingTopic(),
                Stream.STORM_SUFFIX));
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
        KafkaBolt kildaStatsKafkaBolt = createKafkaBolt(Stream.formatWithRegion(topicsConfig.getStatsTopic(),
                Stream.STORM_SUFFIX));
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
        KafkaBolt kildaSwitchManagerKafkaBolt = createKafkaBolt(
                Stream.formatWithRegion(topicsConfig.getTopoSwitchManagerTopic(), Stream.STORM_SUFFIX));
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
        KafkaBolt kildaNorthboundKafkaBolt = createKafkaBolt(
                Stream.formatWithRegion(topicsConfig.getNorthboundTopic(), Stream.STORM_SUFFIX));
        builder.setBolt(ComponentType.NORTHBOUND_REPLY_KAFKA_BOLT, kildaNorthboundKafkaBolt, parallelism)
                .shuffleGrouping(ComponentType.NORTHBOUND_REPLY_BOLT, Stream.NORTHBOUND_REPLY);
    }


    private void createKildaNorthboundReplyStream(TopologyBuilder builder, int parallelism,
                                                     KafkaTopicsConfig topicsConfig,
                                                     List<String> kildaSwitchManagerTopics) {
        createKildaNorthboundSpout(builder, parallelism, kildaSwitchManagerTopics);
        createKildaNorthboundKafkaBolt(builder, parallelism, topicsConfig);

        ReplyBolt replyBolt = new ReplyBolt(Stream.NORTHBOUND_REPLY);
        builder.setBolt(ComponentType.NORTHBOUND_REPLY_BOLT, replyBolt, parallelism)
                .shuffleGrouping(ComponentType.NORTHBOUND_REPLY_KAFKA_SPOUT);
    }

    private void createKildaTopoDiscoSpout(TopologyBuilder builder, int parallelism,
                                            List<String> kildaTopoDiscoTopics) {
        KafkaSpout kildaTopoDiscoSpout = createKafkaSpout(kildaTopoDiscoTopics,
                ComponentType.KILDA_TOPO_DISCO_KAFKA_SPOUT);
        builder.setSpout(ComponentType.KILDA_TOPO_DISCO_KAFKA_SPOUT, kildaTopoDiscoSpout, parallelism);
    }

    private void createKildaTopoDiscoKafkaBolt(TopologyBuilder builder, int parallelism,
                                                KafkaTopicsConfig topicsConfig) {
        KafkaBolt kildaTopoDiscoKafkaBolt = createKafkaBolt(
                Stream.formatWithRegion(topicsConfig.getTopoDiscoTopic(), Stream.STORM_SUFFIX));
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


    @Override
    public StormTopology createTopology() {
        logger.info("Creating FlowTopology - {}", topologyName);

        TopologyBuilder builder = new TopologyBuilder();
        Integer parallelism = topologyConfig.getParallelism();
        KafkaTopicsConfig topicsConfig = topologyConfig.getKafkaTopics();

        // Floodlight -- kilda.flow --> Router
        List<String> kildaFlowTopics = new ArrayList<>();
        kildaFlowTopics.add(topicsConfig.getFlowTopic());
        createKildaFlowReplyStream(builder, parallelism, topicsConfig, kildaFlowTopics);


        // Floodlight -- kilda.ping --> Router
        List<String> kildaPingTopics = new ArrayList<>();
        kildaPingTopics.add(topicsConfig.getPingTopic());
        createKildaPingReplyStream(builder, parallelism, topicsConfig, kildaPingTopics);

        // Floodlight -- kilda.stats --> Router
        List<String> kildaStatsTopics = new ArrayList<>();
        kildaStatsTopics.add(topicsConfig.getStatsTopic());
        createKildaStatsReplyStream(builder, parallelism, topicsConfig, kildaStatsTopics);

        // Floodlight -- kilda.topo.switch.manager --> Router
        List<String> kildaSwitchManagerTopics = new ArrayList<>();
        kildaSwitchManagerTopics.add(topicsConfig.getTopoSwitchManagerTopic());
        createKildaSwitchManagerReplyStream(builder, parallelism, topicsConfig, kildaSwitchManagerTopics);

        // Floodlight -- kilda.northbound --> Router
        List<String> kildaNorthboundTopics = new ArrayList<>();
        kildaNorthboundTopics.add(topicsConfig.getNorthboundTopic());
        createKildaNorthboundReplyStream(builder, parallelism, topicsConfig, kildaSwitchManagerTopics);

        // Floodlight -- kilda.topo.disco --> Router
        List<String> kildaTopoDiscoTopics = new ArrayList<>();
        kildaTopoDiscoTopics.add(topicsConfig.getTopoDiscoTopic());
        createKildaTopoDiscoReplyStream(builder, parallelism, topicsConfig, kildaTopoDiscoTopics);


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
