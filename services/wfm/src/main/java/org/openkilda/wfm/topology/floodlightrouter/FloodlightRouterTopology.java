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
import org.openkilda.wfm.topology.floodlightrouter.bolts.RouterBolt;

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

    public FloodlightRouterTopology(LaunchEnvironment env) {
        super(env, FloodlightRouterTopologyConfig.class);
    }

    private void createSpeakerFlowSpout(TopologyBuilder builder, int parallelism, KafkaTopicsConfig topicsConfig) {
        KafkaSpout speakerFlowSpout = createKafkaSpout(topicsConfig.getSpeakerFlowTopic(),
                ComponentType.ROUTER_SPEAKER_FLOW_KAFKA_SPOUT);
        builder.setSpout(ComponentType.ROUTER_SPEAKER_FLOW_KAFKA_SPOUT, speakerFlowSpout, parallelism);
    }

    private void createSpeakerSpout(TopologyBuilder builder, int parallelism, KafkaTopicsConfig topicsConfig) {
        // OFEventTopology -- router --> Router
        KafkaSpout routerSpout = createKafkaSpout(topicsConfig.getSpeakerTopic(),
                ComponentType.ROUTER_SPEAKER_KAFKA_SPOUT);
        builder.setSpout(ComponentType.ROUTER_SPEAKER_KAFKA_SPOUT, routerSpout, parallelism);
    }

    private void createSpeakerDiscoSpout(TopologyBuilder builder, int parallelism, KafkaTopicsConfig topicsConfig) {
        KafkaSpout speakerDiscoKafkaSpout = createKafkaSpout(topicsConfig.getSpeakerDiscoTopic(),
                ComponentType.SPEAKER_DISCO_KAFKA_SPOUT);
        builder.setSpout(ComponentType.SPEAKER_DISCO_KAFKA_SPOUT, speakerDiscoKafkaSpout, parallelism);
    }

    private void createSpeakerPingSpout(TopologyBuilder builder, int parallelism, KafkaTopicsConfig topicsConfig) {
        // speaker.flow.ping -- > RouterBolt
        KafkaSpout speakerPingKafkaSout = createKafkaSpout(topicsConfig.getSpeakerFlowPingTopic(),
                ComponentType.SPEAKER_PING_KAFKA_SPOUT);
        builder.setSpout(ComponentType.SPEAKER_PING_KAFKA_SPOUT, speakerPingKafkaSout, parallelism);
    }

    private void createRouterBolt(TopologyBuilder builder) {
        // Main Router
        RouterBolt routerBolt = new RouterBolt(topologyConfig.getFloodlightRegions(),
                topologyConfig.getFloodligthAliveTimeout(), topologyConfig.getFloodligthRequestTimeout(),
                topologyConfig.getMessageBlacklistTimeout());
        builder.setBolt(ComponentType.ROUTER_BOLT, routerBolt, topologyConfig.getParallelism())
                .shuffleGrouping(ComponentType.ROUTER_SPEAKER_KAFKA_SPOUT)
                .shuffleGrouping(ComponentType.ROUTER_TOPO_DISCO_SPOUT)
                .shuffleGrouping(ComponentType.SPEAKER_DISCO_KAFKA_SPOUT)
                .shuffleGrouping(ComponentType.ROUTER_SPEAKER_FLOW_KAFKA_SPOUT)
                .shuffleGrouping(ComponentType.KILDA_FLOW_KAFKA_SPOUT)
                .shuffleGrouping(ComponentType.SPEAKER_PING_KAFKA_SPOUT);

    }

    private void createSpeakerKafkaBolt(TopologyBuilder builder, String region, KafkaTopicsConfig topicsConfig,
                                        int parallelism) {
        String speakerRegionTopic = Stream.formatWithRegion(topicsConfig.getSpeakerTopic(), region);
        String speakerRegionStream = Stream.formatWithRegion(Stream.SPEAKER, region);
        KafkaBolt speakerKafkaBolt = createKafkaBolt(speakerRegionTopic);

        builder.setBolt(Stream.formatWithRegion(ComponentType.SPEAKER_KAFKA_BOLT, region),
                        speakerKafkaBolt, parallelism)
                .shuffleGrouping(ComponentType.ROUTER_BOLT, speakerRegionStream);
    }


    private void createSpeakerDiscoKafkaBolt(TopologyBuilder builder, String region, KafkaTopicsConfig topicsConfig,
                                        int parallelism) {
        String speakerDiscoRegionTopic = Stream.formatWithRegion(topicsConfig.getSpeakerDiscoTopic(), region);
        String speakerDiscoRegionStream = Stream.formatWithRegion(Stream.SPEAKER_DISCO, region);
        KafkaBolt speakerDiscoKafkaBolt = createKafkaBolt(speakerDiscoRegionTopic);
        builder.setBolt(Stream.formatWithRegion(ComponentType.SPEAKER_DISCO_KAFKA_BOLT, region), speakerDiscoKafkaBolt,
                parallelism).shuffleGrouping(ComponentType.ROUTER_BOLT, speakerDiscoRegionStream);
    }

    private void createSpeakerFlowKafkaBolt(TopologyBuilder builder, String region, KafkaTopicsConfig topicsConfig,
                                             int parallelism) {
        String speakerFlowRegionTopic = Stream.formatWithRegion(topicsConfig.getSpeakerFlowTopic(), region);
        String speakerFlowRegionStream = Stream.formatWithRegion(Stream.SPEAKER_FLOW, region);
        KafkaBolt speakerFlowKafkaBolt = createKafkaBolt(speakerFlowRegionTopic);
        builder.setBolt(Stream.formatWithRegion(ComponentType.SPEAKER_FLOW_KAFKA_BOLT, region), speakerFlowKafkaBolt,
                parallelism).shuffleGrouping(ComponentType.ROUTER_BOLT, speakerFlowRegionStream);
    }

    private void createSpeakerPingKafkaBolt(TopologyBuilder builder, String region, KafkaTopicsConfig topicsConfig,
                                            int parallelism) {
        String speakerPingRegionTopic = Stream.formatWithRegion(topicsConfig.getSpeakerFlowPingTopic(), region);
        String speakerPingRegionStream = Stream.formatWithRegion(Stream.SPEAKER_PING, region);
        KafkaBolt speakerPingKafkaBolt = createKafkaBolt(speakerPingRegionTopic);
        builder.setBolt(Stream.formatWithRegion(ComponentType.SPEAKER_PING_KAFKA_BOLT, region), speakerPingKafkaBolt,
                parallelism).shuffleGrouping(ComponentType.ROUTER_BOLT, speakerPingRegionStream);
    }

    private void createKildaFlowSpout(TopologyBuilder builder, int parallelism, List<String> kildaFlowTopics) {
        KafkaSpout kildaFlowSpout = createKafkaSpout(kildaFlowTopics,
                ComponentType.KILDA_FLOW_KAFKA_SPOUT);
        builder.setSpout(ComponentType.KILDA_FLOW_KAFKA_SPOUT, kildaFlowSpout, parallelism);
    }

    private void createKildaTopoDiscoSpout(TopologyBuilder builder, int parallelism,
                                           List<String> speakerTopoDiscoTopics) {
        KafkaSpout speakerTopoDiscoSpout = createKafkaSpout(speakerTopoDiscoTopics,
                ComponentType.ROUTER_TOPO_DISCO_SPOUT);
        builder.setSpout(ComponentType.ROUTER_TOPO_DISCO_SPOUT, speakerTopoDiscoSpout, parallelism);
    }

    private void createRouterDiscoKafkaBolt(TopologyBuilder builder, int parallelism, KafkaTopicsConfig topicsConfig) {
        KafkaBolt topoDiscoKafkaBolt = createKafkaBolt(topicsConfig.getTopoDiscoTopic());
        builder.setBolt(ComponentType.TOPO_DISCO_KAFKA_BOLT, topoDiscoKafkaBolt, parallelism)
                .shuffleGrouping(ComponentType.ROUTER_BOLT, Stream.TOPO_DISCO);
    }

    private void createKildaFlowKafkaBolt(TopologyBuilder builder, int parallelism, KafkaTopicsConfig topicsConfig) {
        KafkaBolt kildaFlowKafkaBolt = createKafkaBolt(topicsConfig.getFlowTopic());
        builder.setBolt(ComponentType.KILDA_FLOW_KAFKA_BOLT, kildaFlowKafkaBolt, parallelism)
                .shuffleGrouping(ComponentType.ROUTER_BOLT, Stream.KILDA_FLOW);
    }

    private void createNorthBoundReplyKafkaBolt(TopologyBuilder builder, int parallelism,
                                                KafkaTopicsConfig topicsConfig) {
        KafkaBolt kildaNorthboundReplyBolt = createKafkaBolt(topicsConfig.getNorthboundTopic());
        builder.setBolt(ComponentType.NORTHBOND_REPLY_KAFKA_BOLT, kildaNorthboundReplyBolt, parallelism)
                .shuffleGrouping(ComponentType.ROUTER_BOLT, Stream.NORTHBOUND_REPLY);
    }

    @Override
    public StormTopology createTopology() {
        logger.info("Creating FlowTopology - {}", topologyName);

        TopologyBuilder builder = new TopologyBuilder();
        int parallelism = topologyConfig.getParallelism();
        KafkaTopicsConfig topicsConfig = topologyConfig.getKafkaTopics();

        createSpeakerFlowSpout(builder, parallelism, topicsConfig);
        createSpeakerSpout(builder, parallelism, topicsConfig);
        createSpeakerDiscoSpout(builder, parallelism, topicsConfig);
        createSpeakerPingSpout(builder, parallelism, topicsConfig);

        createRouterBolt(builder);

        List<String> speakerTopoDiscoTopics = new ArrayList<>();
        List<String> kildaFlowTopics = new ArrayList<>();
        Set<String> floodlights = topologyConfig.getFloodlightRegions();
        for (String region: floodlights) {
            // Router -- speaker --> Floodlight
            createSpeakerKafkaBolt(builder, region, topicsConfig, parallelism);

            // Router -- speaker.disco --> Floodlight
            createSpeakerDiscoKafkaBolt(builder, region, topicsConfig, parallelism);


            // Router -- speaker.flow --> Floodlight
            createSpeakerFlowKafkaBolt(builder, region, topicsConfig, parallelism);

            // Router -- speaker.flow.ping --> Floodlight
            createSpeakerPingKafkaBolt(builder, region, topicsConfig, parallelism);

            // Topics for topo.disco spout
            speakerTopoDiscoTopics.add(Stream.formatWithRegion(topicsConfig.getTopoDiscoTopic(), region));
            // Topics for kilda.flow spout
            kildaFlowTopics.add(Stream.formatWithRegion(topicsConfig.getFlowTopic(), region));
        }

        // Floodlight -- kilda.flow --> Router
        createKildaFlowSpout(builder, parallelism, kildaFlowTopics);

        // Floodlight -- topo.disco --> Router
        createKildaTopoDiscoSpout(builder, parallelism, speakerTopoDiscoTopics);

        // Router -- router.disco --> OFEventTopology
        createRouterDiscoKafkaBolt(builder, parallelism, topicsConfig);

        // Router -- kilda.flow --> FlowTopology
        createKildaFlowKafkaBolt(builder, parallelism, topicsConfig);

        // Router --kilda.northbound --> Northbound
        createNorthBoundReplyKafkaBolt(builder, parallelism, topicsConfig);

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
