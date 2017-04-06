package org.bitbucket.openkilda.wfm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.kafka.bolt.selector.DefaultTopicSelector;
import org.apache.storm.task.IBolt;
import org.apache.storm.topology.BoltDeclarer;
import org.apache.storm.topology.IComponent;
import org.apache.storm.topology.IStatefulBolt;
import org.apache.storm.topology.TopologyBuilder;

import java.util.Properties;

/**
 * OFEventWFMTopology creates the topology to manage these key aspects of OFEvents:
 *
 * (1) Switch UP/DOWN
 * (2) Port UP/DOWN
 * (3) Link UP/DOWN (ISL) - and health manager
 */
public class OFEventWFMTopology {
    /*
     * Progress Tracker - Phase 1: Simple message flow, a little bit of state, re-wire spkr/tpe
     * (1) √ Switch UP - Simple pass through
     * (2) ◊ Switch Down - Simple pass through (LinkBolt will stop Link Discovery / Health)
     * (3) √ Port UP - Simple Pass through (will be picked up by Link bolts, Discovery started)
     * (4) ◊ Port DOWN - Simple Pass through (LinkBolt will stop Link Discovery / Health)
     * (5) ◊ Link UP - this will be a response from the Discovery packet.
     *          ◊ Put message on kilda.wfm.topo.updown
     * (6) ◊ Link DOWN - this will be a response from the Discovery packet
     *          ◊ Put message on kilda.wfm.topo.updown
     * (7) ◊ Re-wire Speaker (use kilda.speaker) and TPE (use kilda.wfm.topo.updown)
     * (8) ◊ Add simple pass through for verification (w/ speaker) & validation (w/ TPE)
     */

    public String topoName = "WFM_OFEvents";
    public static final String DEFAULT_KAFKA_OUTPUT = "kilda.wfm.topo.updown";
    //public static final String DEFAULT_KAFKA_OUTPUT = "kilda-test";
    public String kafkaOutputTopic = DEFAULT_KAFKA_OUTPUT;
    private static Logger logger = LogManager.getLogger(OFEventWFMTopology.class);


    public KafkaUtils kutils = new KafkaUtils();
    public Properties kafkaProps = kutils.createStringsKafkaProps();
    public int parallelism = 3;
    /** This is the primary input topics */
    public String[] topics = {
            InfoEventSplitterBolt.I_SWITCH_UPDOWN,
            InfoEventSplitterBolt.I_PORT_UPDOWN,
            InfoEventSplitterBolt.I_ISL_UPDOWN
    };
    // The order of bolts should match topics, and Link should be last .. logic below relies on it
    public IStatefulBolt[] bolts = {
            new OFESwitchBolt().withOutputStreamId(kafkaOutputTopic),
            new OFEPortBolt().withOutputStreamId(kafkaOutputTopic),
            new OFELinkBolt().withOutputStreamId(kafkaOutputTopic)
    };

    public OFEventWFMTopology(){}

    public OFEventWFMTopology withKafkaProps(Properties kprops){
        kafkaProps.putAll(kprops);
        return this;
    }


    public StormTopology createTopology() {
        logger.debug("Building Topology - " + this.getClass().getSimpleName());

        TopologyBuilder builder = new TopologyBuilder();

        BoltDeclarer kbolt = builder.setBolt(kafkaOutputTopic+"-kafkabolt",
                kutils.createKafkaBolt(kafkaOutputTopic), parallelism);
        // tbolt will save the setBolt() results; will be useed to add switch/port to link
        BoltDeclarer[] tbolt = new BoltDeclarer[bolts.length];

        for (int i = 0; i < topics.length; i++) {
            String topic = topics[i];
            String spoutName = topic+"-spout";
            String boltName = topic+"-bolt";
            builder.setSpout(spoutName, kutils.createKafkaSpout(topic));
            // NB: with shuffleGrouping, we can't maintain state .. would need to parse first
            //      just to pull out switchID.
            tbolt[i] = builder.setBolt(boltName, bolts[i], parallelism).shuffleGrouping(spoutName);
            kbolt = kbolt.shuffleGrouping(boltName,kafkaOutputTopic);
        }
        // now hookup switch and port to the link bolt so that it can take appropriate action
        tbolt[2].shuffleGrouping(topics[0]+"-bolt",kafkaOutputTopic)
                .shuffleGrouping(topics[1]+"-bolt",kafkaOutputTopic);
        // finally, one more bolt, to write the ISL Discovery requests
        String discoTopic = ((OFELinkBolt) bolts[2]).islDiscoTopic;
        builder.setBolt("ISL_Discovery-kafkabolt",
                kutils.createKafkaBolt(discoTopic), parallelism)
                .shuffleGrouping(topics[2]+"-bolt",discoTopic);

        return builder.createTopology();
    }

    public static void main(String[] args) throws Exception {

        OFEventWFMTopology kildaTopology = new OFEventWFMTopology();
        StormTopology topo = kildaTopology.createTopology();
        String name = (args != null && args.length > 0) ?
             args[0] : kildaTopology.topoName;

        Config conf = new Config();
        conf.setDebug(false);

        //If there are arguments, we are running on a cluster; otherwise, we are running locally
        if (args != null && args.length > 0) {
            conf.setNumWorkers(kildaTopology.parallelism);
            StormSubmitter.submitTopology(name, conf, topo);
        }
        else {
            conf.setMaxTaskParallelism(kildaTopology.parallelism);

            LocalCluster cluster = new LocalCluster();
            cluster.submitTopology(name, conf,topo);

            Thread.sleep(10 * 1000);
            cluster.shutdown();
        }
    }









    /*
     * Progress Tracker - Phase 2: Speaker / TPE Integration; Cache Coherency Checks; Flapping
     * (1) ◊ - Interact with Speaker (network element is / isn't there)
     * (2) ◊ - Interact with TPE (graph element is / isn't there)
     * (3) ◊ - Validate the Topology periodically - switches, ports, links
     *          - health checks should validate the known universe; what about missing stuff?
     * (4) ◊ - See if flapping happens .. define window and if there are greater than 4 up/downs?
     * (5) ◊ -
     * (6) ◊ -
     * (7) ◊ -
     * (8) ◊ -
     * (9) ◊ -
     */

    /*
     * Progress Tracker - Phase 3: ?
     * (1) ◊ -
     * (2) ◊ -
     * (3) ◊ -
     * (4) ◊ -
     * (5) ◊ -
     * (6) ◊ -
     * (7) ◊ -
     * (8) ◊ -
     * (9) ◊ -
     */

}
