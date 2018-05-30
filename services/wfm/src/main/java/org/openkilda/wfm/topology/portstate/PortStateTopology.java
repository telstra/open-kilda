package org.openkilda.wfm.topology.portstate;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.topology.TopologyBuilder;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.error.ConfigurationException;
import org.openkilda.wfm.error.NameCollisionException;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.portstate.bolt.ParsePortInfoBolt;
import org.openkilda.wfm.topology.portstate.bolt.TopoDiscoParseBolt;
import org.openkilda.wfm.topology.portstate.bolt.WfmStatsParseBolt;
import org.openkilda.wfm.topology.portstate.spout.SwitchPortsSpout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PortStateTopology extends AbstractTopology {
    private static final Logger logger = LoggerFactory.getLogger(PortStateTopology.class);

    public static final String TOPO_DISCO_SPOUT = "topo.disco.spout";
    public static final String WFM_STATS_SPOUT = "wfm.stats.spout";
    public static final int JANITOR_REFRESH = 600;
    public final String PARSE_PORT_INFO_BOLT_NAME = ParsePortInfoBolt.class.getSimpleName();
    public final String TOPO_DISCO_PARSE_BOLT_NAME = TopoDiscoParseBolt.class.getSimpleName();
    public final String SWITCH_PORTS_SPOUT_NAME = SwitchPortsSpout.class.getSimpleName();
    public final String WFM_STATS_PARSE_BOLT_NAME = WfmStatsParseBolt.class.getSimpleName();
    public final String SpeakerBoltName = "speaker.kafka.bolt";
    public final String OtsdbKafkaBoltName = "otsdb.kafka.bolt";

    protected PortStateTopology(LaunchEnvironment env) throws ConfigurationException {
        super(env);
    }

    @Override
    public StormTopology createTopology() throws NameCollisionException {
        final String clazzName = this.getClass().getSimpleName();
        logger.debug("Building Topology - {}", clazzName);

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
        String topoDiscoTopic = config.getKafkaTopoDiscoTopic();
        checkAndCreateTopic(topoDiscoTopic);
        logger.debug("connecting to topic {}", topoDiscoTopic);
        builder.setSpout(TOPO_DISCO_SPOUT, createKafkaSpout(topoDiscoTopic, clazzName+topoDiscoTopic));

        TopoDiscoParseBolt topoDiscoParseBolt = new TopoDiscoParseBolt();
        builder.setBolt(TOPO_DISCO_PARSE_BOLT_NAME, topoDiscoParseBolt, config.getParallelism())
                .shuffleGrouping(TOPO_DISCO_SPOUT);

        ParsePortInfoBolt parsePortInfoBolt = new ParsePortInfoBolt();
        builder.setBolt(PARSE_PORT_INFO_BOLT_NAME, parsePortInfoBolt, config.getParallelism())
                .shuffleGrouping(TOPO_DISCO_PARSE_BOLT_NAME, TopoDiscoParseBolt.TOPO_TO_PORT_INFO_STREAM)
                .shuffleGrouping(WFM_STATS_PARSE_BOLT_NAME, WfmStatsParseBolt.WFM_TO_PARSE_PORT_INFO_STREAM);

        final String openTsdbTopic = config.getKafkaOtsdbTopic();
        checkAndCreateTopic(openTsdbTopic);
        KafkaBolt openTsdbBolt = createKafkaBolt(openTsdbTopic);
        builder.setBolt(OtsdbKafkaBoltName, openTsdbBolt, config.getParallelism())
                .shuffleGrouping(PARSE_PORT_INFO_BOLT_NAME);

        // Setup spout and bolt for WFM_STATS_SPOUT line
        String wfmStatsTopic = config.getKafkaStatsTopic();
        checkAndCreateTopic(wfmStatsTopic);
        logger.debug("conencting to topic {}", wfmStatsTopic);
        builder.setSpout(WFM_STATS_SPOUT, createKafkaSpout(wfmStatsTopic, clazzName+wfmStatsTopic));
        
        WfmStatsParseBolt wfmStatsParseBolt = new WfmStatsParseBolt();
        builder.setBolt(WFM_STATS_PARSE_BOLT_NAME, wfmStatsParseBolt, config.getParallelism())
                .shuffleGrouping(WFM_STATS_SPOUT);

        // Setup spout and bolt for sending SwitchPortsCommand every frequency seconds
        SwitchPortsSpout switchPortsSpout = new SwitchPortsSpout(config, JANITOR_REFRESH);
        builder.setSpout(SWITCH_PORTS_SPOUT_NAME, switchPortsSpout);

        final String speakerTopic = config.getKafkaSpeakerTopic();
        checkAndCreateTopic(speakerTopic);
        KafkaBolt speakerBolt = createKafkaBolt(speakerTopic);
        builder.setBolt(SpeakerBoltName, speakerBolt, config.getParallelism())
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
