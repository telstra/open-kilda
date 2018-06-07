package org.openkilda.wfm.topology.portstate;

import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.error.NameCollisionException;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.portstate.bolt.ParsePortInfoBolt;
import org.openkilda.wfm.topology.portstate.bolt.TopoDiscoParseBolt;
import org.openkilda.wfm.topology.portstate.bolt.WfmStatsParseBolt;
import org.openkilda.wfm.topology.portstate.spout.SwitchPortsSpout;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.topology.TopologyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PortStateTopology extends AbstractTopology<PortStateTopologyConfig> {
    private static final Logger logger = LoggerFactory.getLogger(PortStateTopology.class);

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
        super(env, PortStateTopologyConfig.class);
    }

    @Override
    public StormTopology createTopology() throws NameCollisionException {
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
        checkAndCreateTopic(topoDiscoTopic);
        logger.debug("connecting to {} topic", topoDiscoTopic);
        builder.setSpout(TOPO_DISCO_SPOUT, createKafkaSpout(topoDiscoTopic, TOPO_DISCO_SPOUT));

        TopoDiscoParseBolt topoDiscoParseBolt = new TopoDiscoParseBolt();
        builder.setBolt(TOPO_DISCO_PARSE_BOLT_NAME, topoDiscoParseBolt, topologyConfig.getParallelism())
                .shuffleGrouping(TOPO_DISCO_SPOUT);

        ParsePortInfoBolt parsePortInfoBolt = new ParsePortInfoBolt();
        builder.setBolt(PARSE_PORT_INFO_BOLT_NAME, parsePortInfoBolt, topologyConfig.getParallelism())
                .shuffleGrouping(TOPO_DISCO_PARSE_BOLT_NAME, TopoDiscoParseBolt.TOPO_TO_PORT_INFO_STREAM)
                .shuffleGrouping(WFM_STATS_PARSE_BOLT_NAME, WfmStatsParseBolt.WFM_TO_PARSE_PORT_INFO_STREAM);

        String openTsdbTopic = topologyConfig.getKafkaOtsdbTopic();
        checkAndCreateTopic(openTsdbTopic);
        KafkaBolt openTsdbBolt = createKafkaBolt(openTsdbTopic);
        builder.setBolt(OTSDB_KAFKA_BOLT_NAME, openTsdbBolt, topologyConfig.getParallelism())
                .shuffleGrouping(PARSE_PORT_INFO_BOLT_NAME);

        // Setup spout and bolt for WFM_STATS_SPOUT line
        String wfmStatsTopic = topologyConfig.getKafkaStatsTopic();
        checkAndCreateTopic(wfmStatsTopic);
        logger.debug("connecting to {} topic", wfmStatsTopic);
        builder.setSpout(WFM_STATS_SPOUT, createKafkaSpout(wfmStatsTopic, WFM_STATS_SPOUT));
        
        WfmStatsParseBolt wfmStatsParseBolt = new WfmStatsParseBolt();
        builder.setBolt(WFM_STATS_PARSE_BOLT_NAME, wfmStatsParseBolt, topologyConfig.getParallelism())
                .shuffleGrouping(WFM_STATS_SPOUT);

        // Setup spout and bolt for sending SwitchPortsCommand every frequency seconds
        SwitchPortsSpout switchPortsSpout = new SwitchPortsSpout(topologyConfig, JANITOR_REFRESH);
        builder.setSpout(SWITCH_PORTS_SPOUT_NAME, switchPortsSpout);

        String speakerTopic = topologyConfig.getKafkaSpeakerTopic();
        checkAndCreateTopic(speakerTopic);
        KafkaBolt speakerBolt = createKafkaBolt(speakerTopic);
        builder.setBolt(SPEAKER_KAFKA_BOLT_NAME, speakerBolt, topologyConfig.getParallelism())
                .shuffleGrouping(SWITCH_PORTS_SPOUT_NAME);

        return builder.createTopology();
    }

    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new PortStateTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
