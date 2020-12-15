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

package org.openkilda.wfm.topology.portstate;

import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.portstate.bolt.ParsePortInfoBolt;
import org.openkilda.wfm.topology.portstate.bolt.TopoDiscoParseBolt;
import org.openkilda.wfm.topology.portstate.bolt.WfmStatsParseBolt;
import org.openkilda.wfm.topology.portstate.spout.SwitchPortsSpout;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.topology.TopologyBuilder;

public class PortStateTopology extends AbstractTopology<PortStateTopologyConfig> {

    public static final String TOPO_DISCO_SPOUT = "topo.disco.spout";
    private static final String WFM_STATS_SPOUT = "wfm.stats.spout";
    private static final int JANITOR_REFRESH = 600;
    private static final String PARSE_PORT_INFO_BOLT_NAME = ParsePortInfoBolt.class.getSimpleName();
    private static final String TOPO_DISCO_PARSE_BOLT_NAME = TopoDiscoParseBolt.class.getSimpleName();
    private static final String SWITCH_PORTS_SPOUT_NAME = SwitchPortsSpout.class.getSimpleName();
    private static final String WFM_STATS_PARSE_BOLT_NAME = WfmStatsParseBolt.class.getSimpleName();
    private static final String SPEAKER_KAFKA_BOLT_NAME = "speaker.kafka.bolt";
    private static final String OTSDB_KAFKA_BOLT_NAME = "otsdb.kafka.bolt";

    protected PortStateTopology(LaunchEnvironment env) {
        super(env, "portstate-topology", PortStateTopologyConfig.class);
    }

    @Override
    public StormTopology createTopology() {
        logger.info("Creating PortStateTopology - {}", topologyName);

        TopologyBuilder builder = new TopologyBuilder();

        /*
         *  Topology:
         *
         *  TOPO_DISCO_SPOUT ---> TopoDiscoParseBolt ---> ParsePortInfoBolt ---> OtsdbKafkaBolt(kilda.otsdb topic)
         *                                                         ^
         *                                                         |
         *  WFM_STATS_SPOUT ---> WfmStatsParseBolt -----------------
         *
         *
         *  SwitchPortsSpout ---> SpeakerKafkaBolt(kilda.speaker topic)
         *
         */

        // Setup spout and bolt for TOPO_DISCO_SPOUT line
        String topoDiscoTopic = topologyConfig.getKafkaTopoDiscoTopic();
        logger.debug("connecting to {} topic", topoDiscoTopic);
        declareKafkaSpout(builder, topoDiscoTopic, TOPO_DISCO_SPOUT);

        TopoDiscoParseBolt topoDiscoParseBolt = new TopoDiscoParseBolt();
        declareBolt(builder, topoDiscoParseBolt, TOPO_DISCO_PARSE_BOLT_NAME)
                .shuffleGrouping(TOPO_DISCO_SPOUT);

        ParsePortInfoBolt parsePortInfoBolt = new ParsePortInfoBolt(topologyConfig.getMetricPrefix());
        declareBolt(builder, parsePortInfoBolt, PARSE_PORT_INFO_BOLT_NAME)
                .shuffleGrouping(TOPO_DISCO_PARSE_BOLT_NAME, TopoDiscoParseBolt.TOPO_TO_PORT_INFO_STREAM)
                .shuffleGrouping(WFM_STATS_PARSE_BOLT_NAME, WfmStatsParseBolt.WFM_TO_PARSE_PORT_INFO_STREAM);

        String openTsdbTopic = topologyConfig.getKafkaOtsdbTopic();
        KafkaBolt openTsdbBolt = createKafkaBolt(openTsdbTopic);
        declareBolt(builder, openTsdbBolt, OTSDB_KAFKA_BOLT_NAME)
                .shuffleGrouping(PARSE_PORT_INFO_BOLT_NAME);

        // Setup spout and bolt for WFM_STATS_SPOUT line
        String wfmStatsTopic = topologyConfig.getKafkaStatsTopic();
        logger.debug("connecting to {} topic", wfmStatsTopic);
        declareKafkaSpout(builder, wfmStatsTopic, WFM_STATS_SPOUT);

        WfmStatsParseBolt wfmStatsParseBolt = new WfmStatsParseBolt();
        declareBolt(builder, wfmStatsParseBolt, WFM_STATS_PARSE_BOLT_NAME)
                .shuffleGrouping(WFM_STATS_SPOUT);

        // Setup spout and bolt for sending SwitchPortsCommand every frequency seconds
        SwitchPortsSpout switchPortsSpout = new SwitchPortsSpout(JANITOR_REFRESH);
        declareSpout(builder, switchPortsSpout, SWITCH_PORTS_SPOUT_NAME);

        String speakerTopic = topologyConfig.getKafkaSpeakerTopic();
        KafkaBolt speakerBolt = createKafkaBolt(speakerTopic);
        declareBolt(builder, speakerBolt, SPEAKER_KAFKA_BOLT_NAME)
                .shuffleGrouping(SWITCH_PORTS_SPOUT_NAME);

        return builder.createTopology();
    }

    /**
     * Entry point for local run of the topology.
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new PortStateTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
