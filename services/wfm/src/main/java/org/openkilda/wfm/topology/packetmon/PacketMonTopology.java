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

package org.openkilda.wfm.topology.packetmon;

import org.openkilda.messaging.ServiceType;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.error.NameCollisionException;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.packetmon.PacketMonTopologyConfig.PacketmonConfig;
import org.openkilda.wfm.topology.packetmon.bolts.BadFlowBolt;
import org.openkilda.wfm.topology.packetmon.bolts.FlowMonBolt;
import org.openkilda.wfm.topology.packetmon.bolts.ParseTsdbStatsBolt;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PacketMonTopology extends AbstractTopology<PacketMonTopologyConfig> {
    private static final Logger logger = LoggerFactory.getLogger(PacketMonTopology.class);
    private static final String PACKET_MON_TSDB_SPOUT_ID = "packetmon-stats-spout";
    private static final String PACKET_MON_PARSE_TSDB_BOLT_ID = "packetmon-parse-tsdb-bolt";
    private static final String PACKET_MON_FLOW_MON_BOLT_ID = "packetmon-flowmon-bolt";
    private static final String PACKET_MON_BAD_FLOW_BOLT_ID = "packetmon-badflow-bolt";
    private static final String PACKET_MON_TSDB_BOLT_ID = "packetmon-tsdb-bolt";

    public PacketMonTopology(LaunchEnvironment env) {
        super(env, PacketMonTopologyConfig.class);
    }

    /**
     * Topology to identify, alert and record anomalies on ISL's and within Flows based on comparing rx/tx packets
     * utilizing the pre-processed OpenTsdb data that can be found in the TSDB topic.  When an anomaly is found it
     * ios written back to the TSDB topic to help refine the algorithm.
     *
     * <p>Topology Map:
     * KafkaSpout --> ParseTsdbStatsBolt --> FlowMonBolt --> BadFlowBolt --> TsdbBolt (writes to Kafka tsdb Topic)
     *
     * @return StromTopology
     * @throws NameCollisionException When duplicate topology name
     */
    @Override
    public StormTopology createTopology() throws NameCollisionException {
        logger.info("Creating PacketMonTopology - {}", topologyName);

        String kafkaOtsdbTopic = topologyConfig.getKafkaOtsdbTopic();
        checkAndCreateTopic(kafkaOtsdbTopic);

        logger.debug("connecting to {} topic", kafkaOtsdbTopic);
        TopologyBuilder builder = new TopologyBuilder();
        builder.setSpout(PACKET_MON_TSDB_SPOUT_ID, createKafkaSpout(kafkaOtsdbTopic, PACKET_MON_TSDB_SPOUT_ID));

        /*
         * Receives tuples from the spout.
         */
        PacketmonConfig packetmonConfig = topologyConfig.getPacketmonTopologyConfig();
        ParseTsdbStatsBolt parseTsdbStatsBolt = new ParseTsdbStatsBolt();
        builder.setBolt(PACKET_MON_PARSE_TSDB_BOLT_ID, parseTsdbStatsBolt, packetmonConfig.getNumSpouts())
                .shuffleGrouping(PACKET_MON_TSDB_SPOUT_ID);

        /*
         * FlowMonBolt receives tuples from parseTsdbStatsBolt with fieldsGrouping based on simpleHash.
         */
        FlowMonBolt flowMonBolt = new FlowMonBolt(
                packetmonConfig.getFlowmonBoltCacheTimeout(),
                packetmonConfig.getWindowsize(),
                packetmonConfig.getMaxTimeDelta(),
                packetmonConfig.getMaxVariance()
        );
        builder.setBolt(PACKET_MON_FLOW_MON_BOLT_ID, flowMonBolt, packetmonConfig.getFlowMonBoltExecutors())
                .setNumTasks(packetmonConfig.getFlowMonBoltWorkers())
                .fieldsGrouping(PACKET_MON_PARSE_TSDB_BOLT_ID, ParseTsdbStatsBolt.FlowMon_Stream, new Fields("hash"));

        /*
         * BadFlowBolt receives tuples from flowMonBolt and sends them TSDB Topic with fieldsGrouping based on
         * flowid.
         */
        BadFlowBolt badFlowBolt = new BadFlowBolt(packetmonConfig.getBadFlowBoltCacheTimeout());
        builder.setBolt(PACKET_MON_BAD_FLOW_BOLT_ID, badFlowBolt, packetmonConfig.getBadFlowBoltExecutors())
                .setNumTasks(packetmonConfig.getBadFlowBoltWorkers())
                .fieldsGrouping(PACKET_MON_FLOW_MON_BOLT_ID, FlowMonBolt.BadFlowStream, new Fields("flowid"));

        /*
         * KafkaBolt writes to TSDB topic.
         */
        KafkaBolt openTsdbBolt = createKafkaBolt(kafkaOtsdbTopic);
        builder.setBolt(PACKET_MON_TSDB_BOLT_ID, openTsdbBolt, packetmonConfig.getTsdbBoltExecutors())
                .setNumTasks(packetmonConfig.getTsdbBoltWorkers())
                .shuffleGrouping(PACKET_MON_BAD_FLOW_BOLT_ID, BadFlowBolt.TsdbStream);

        createHealthCheckHandler(builder, ServiceType.PACKETMON_TOPOLOGY.getId());

        return builder.createTopology();
    }

    /**
     * Main run loop.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new PacketMonTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
