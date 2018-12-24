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

    private String formatWithRegion(String param, String region) {
        return String.format("%s_%s", param, region);
    }

    @Override
    public StormTopology createTopology() {
        logger.info("Creating FlowTopology - {}", topologyName);

        TopologyBuilder builder = new TopologyBuilder();
        Integer parallelism = topologyConfig.getParallelism();
        KafkaTopicsConfig topicsConfig = topologyConfig.getKafkaTopics();

        KafkaSpout speakerFlowSpout = createKafkaSpout(topicsConfig.getSpeakerFlowTopic(),
                ComponentType.ROUTER_SPEAKER_FLOW_KAFKA_SPOUT);
        builder.setSpout(ComponentType.ROUTER_SPEAKER_FLOW_KAFKA_SPOUT, speakerFlowSpout, parallelism);
        // OFEventTopology -- router --> Router
        KafkaSpout routerSpout = createKafkaSpout(topicsConfig.getSpeakerTopic(),
                ComponentType.ROUTER_SPEAKER_KAFKA_SPOUT);

        builder.setSpout(ComponentType.ROUTER_SPEAKER_KAFKA_SPOUT, routerSpout, parallelism);

        KafkaSpout speakerDiscoKafkaSpout = createKafkaSpout(topicsConfig.getSpeakerDiscoTopic(),
                ComponentType.SPEAKER_DISCO_KAFKA_SPOUT);
        builder.setSpout(ComponentType.SPEAKER_DISCO_KAFKA_SPOUT, speakerDiscoKafkaSpout, parallelism);

        // speaker.flow.ping -- > RouterBolt
        KafkaSpout speakerPingKafkaSout = createKafkaSpout(topicsConfig.getSpeakerFlowPingTopic(),
                ComponentType.SPEAKER_PING_KAFKA_SPOUT);
        builder.setSpout(ComponentType.SPEAKER_PING_KAFKA_SPOUT, speakerPingKafkaSout, parallelism);

        Set<String> floodlights = topologyConfig.getFloodlightRegions();

        // Main Router
        RouterBolt routerBolt = new RouterBolt(floodlights, topologyConfig.getFloodligthAliveTimeout(),
                topologyConfig.getFloodligthRequestTimeout(), topologyConfig.getMessageBlacklistTimeout());
        builder.setBolt(ComponentType.ROUTER_BOLT, routerBolt, parallelism)
                .shuffleGrouping(ComponentType.ROUTER_SPEAKER_KAFKA_SPOUT)
                .shuffleGrouping(ComponentType.ROUTER_TOPO_DISCO_SPOUT)
                .shuffleGrouping(ComponentType.SPEAKER_DISCO_KAFKA_SPOUT)
                .shuffleGrouping(ComponentType.ROUTER_SPEAKER_FLOW_KAFKA_SPOUT)
                .shuffleGrouping(ComponentType.KILDA_FLOW_KAFKA_SPOUT)
                .shuffleGrouping(ComponentType.SPEAKER_PING_KAFKA_SPOUT);
        List<String> speakerTopoDiscoTopics = new ArrayList<>();
        List<String> kildaFlowTopics = new ArrayList<>();
        for (String region: floodlights) {
            // Router -- speaker --> Floodlight
            String speakerRegionTopic = formatWithRegion(topicsConfig.getSpeakerTopic(), region);
            String speakerRegionStream = formatWithRegion(Stream.SPEAKER, region);
            KafkaBolt speakerKafkaBolt = createKafkaBolt(speakerRegionTopic);

            builder.setBolt(formatWithRegion(ComponentType.SPEAKER_KAFKA_BOLT, region), speakerKafkaBolt, parallelism)
                  .shuffleGrouping(ComponentType.ROUTER_BOLT, speakerRegionStream);

            // Router -- speaker.disco --> Floodlight
            String speakerDiscoRegionTopic = formatWithRegion(topicsConfig.getSpeakerDiscoTopic(), region);
            String speakerDiscoRegionStream = formatWithRegion(Stream.SPEAKER_DISCO, region);
            KafkaBolt speakerDiscoKafkaBolt = createKafkaBolt(speakerDiscoRegionTopic);
            builder.setBolt(formatWithRegion(ComponentType.SPEAKER_DISCO_KAFKA_BOLT, region), speakerDiscoKafkaBolt,
                    parallelism).shuffleGrouping(ComponentType.ROUTER_BOLT, speakerDiscoRegionStream);


            // Router -- speaker.flow --> Floodlight
            String speakerFlowRegionTopic = formatWithRegion(topicsConfig.getSpeakerFlowTopic(), region);
            String speakerFlowRegionStream = formatWithRegion(Stream.SPEAKER_FLOW, region);
            KafkaBolt speakerFlowKafkaBolt = createKafkaBolt(speakerFlowRegionTopic);
            builder.setBolt(formatWithRegion(ComponentType.SPEAKER_FLOW_KAFKA_BOLT, region), speakerFlowKafkaBolt,
                    parallelism).shuffleGrouping(ComponentType.ROUTER_BOLT, speakerFlowRegionStream);

            // Router -- speaker.flow.ping --> Floodlight
            String speakerPingRegionTopic = formatWithRegion(topicsConfig.getSpeakerFlowPingTopic(), region);
            String speakerPingRegionStream = formatWithRegion(Stream.SPEAKER_PING, region);
            KafkaBolt speakerPingKafkaBolt = createKafkaBolt(speakerPingRegionTopic);
            builder.setBolt(formatWithRegion(ComponentType.SPEAKER_PING_KAFKA_BOLT, region), speakerPingKafkaBolt,
                    parallelism).shuffleGrouping(ComponentType.ROUTER_BOLT, speakerPingRegionStream);


            // Topics for topo.disco spout
            speakerTopoDiscoTopics.add(formatWithRegion(topicsConfig.getTopoDiscoTopic(), region));

            // Topics for kilda.flow spout
            kildaFlowTopics.add(formatWithRegion(topicsConfig.getFlowTopic(), region));
        }

        // Floodlight -- kilda.flow --> Router
        KafkaSpout kildaFlowSpout = createKafkaSpout(kildaFlowTopics,
                ComponentType.KILDA_FLOW_KAFKA_SPOUT);
        builder.setSpout(ComponentType.KILDA_FLOW_KAFKA_SPOUT, kildaFlowSpout, parallelism);

        // Floodlight -- topo.disco --> Router
        KafkaSpout speakerTopoDiscoSpout = createKafkaSpout(speakerTopoDiscoTopics,
                ComponentType.ROUTER_TOPO_DISCO_SPOUT);
        builder.setSpout(ComponentType.ROUTER_TOPO_DISCO_SPOUT, speakerTopoDiscoSpout, parallelism);

        // Router -- router.disco --> OFEventTopology
        KafkaBolt topoDiscoKafkaBolt = createKafkaBolt(topicsConfig.getTopoDiscoTopic());
        builder.setBolt(ComponentType.TOPO_DISCO_KAFKA_BOLT, topoDiscoKafkaBolt, parallelism)
                .shuffleGrouping(ComponentType.ROUTER_BOLT, Stream.TOPO_DISCO);

        // Router -- kilda.flow --> FlowTopology
        KafkaBolt kildaFlowKafkaBolt = createKafkaBolt(topicsConfig.getFlowTopic());
        builder.setBolt(ComponentType.KILDA_FLOW_KAFKA_BOLT, kildaFlowKafkaBolt, parallelism)
                .shuffleGrouping(ComponentType.ROUTER_BOLT, Stream.KILDA_FLOW);

        // Router --kilda.northbound --> Northbound
        KafkaBolt kildaNorthboundReplyBolt = createKafkaBolt(topicsConfig.getNorthboundTopic());
        builder.setBolt(ComponentType.NORTHBOND_REPLY_KAFKA_BOLT, kildaNorthboundReplyBolt, parallelism)
                .shuffleGrouping(ComponentType.ROUTER_BOLT, Stream.NORTHBOUND_REPLY);

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
