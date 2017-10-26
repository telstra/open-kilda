package org.openkilda.simulator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.StormTopology;
import org.openkilda.simulator.bolts.CommandBolt;
import org.openkilda.simulator.bolts.DeployTopologyBolt;
import org.openkilda.simulator.bolts.SwitchBolt;
import org.openkilda.wfm.topology.AbstractTopology;
import org.apache.storm.topology.TopologyBuilder;
import org.openkilda.wfm.topology.Topology;

import java.io.File;

public class SimulatorTopology extends AbstractTopology {
    private static final Logger logger = LogManager.getLogger(SimulatorTopology.class);
    private final String topoName = "simulatorTopology";
    private final int parallelism = 1;
    private final String SIMULATOR_TOPIC = "kilda-simulator";
    private final String COMMAND_TOPIC = "kilda-test";
    public static final String SIMULATOR_SPOUT = "simulator-spout";
    public static final String COMMAND_SPOUT = "command-spout";
    public static final String DEPLOY_TOPOLOGY_BOLT_STREAM = "deploy_topology_stream";
    public static final String COMMAND_BOLT_STREAM = "command_bolt_stream";

    public SimulatorTopology(File file) {
        super(file);
    }

    public static void main(String[] args) throws Exception {

        //If there are arguments, we are running on a cluster; otherwise, we are running locally
        if (args != null && args.length > 0) {
            File file = new File(args[1]);
            Config conf = new Config();
            conf.setDebug(false);
            SimulatorTopology simulatorTopology = new SimulatorTopology(file);
            StormTopology topo = simulatorTopology.createTopology();

            conf.setNumWorkers(simulatorTopology.parallelism);
            StormSubmitter.submitTopology(args[0], conf, topo);
        } else {
            logger.info("starting islStatsTopo in local mode");
            File file = new File(SimulatorTopology.class.getResource(Topology.TOPOLOGY_PROPERTIES).getFile());
            Config conf = new Config();
            conf.setDebug(false);
            SimulatorTopology simulatorTopology = new SimulatorTopology(file);
            StormTopology topo = simulatorTopology.createTopology();

            conf.setMaxTaskParallelism(3);

            LocalCluster cluster = new LocalCluster();
            cluster.submitTopology(simulatorTopology.topologyName, conf, topo);

            while(true) {
                Thread.sleep(60000);
            }
//            cluster.shutdown();
        }
    }


    @Override
    public StormTopology createTopology() {
        final String className = this.getClass().getSimpleName();
        logger.debug("Building SimulatorTopology - " + className);
        TopologyBuilder builder = new TopologyBuilder();

        checkAndCreateTopic(SIMULATOR_TOPIC);
        logger.debug("connecting to " + SIMULATOR_TOPIC + " topic");
        builder.setSpout(SIMULATOR_SPOUT, createKafkaSpout(SIMULATOR_TOPIC, className));

        checkAndCreateTopic(COMMAND_TOPIC);
        logger.debug("connecting to " + COMMAND_TOPIC + " topic");
        builder.setSpout(COMMAND_SPOUT, createKafkaSpout(COMMAND_TOPIC, COMMAND_SPOUT));

        final String commandBoltName = CommandBolt.class.getSimpleName();
        CommandBolt commandBolt = new CommandBolt();
        logger.debug("starting " + commandBoltName + " bolt");
        builder.setBolt(commandBoltName, commandBolt, parallelism)
                .shuffleGrouping(SIMULATOR_SPOUT)
                .shuffleGrouping(COMMAND_SPOUT);

        final String deployTopologyBoltName = DeployTopologyBolt.class.getSimpleName();
        DeployTopologyBolt deployTopologyBolt = new DeployTopologyBolt();
        logger.debug("starting " + deployTopologyBoltName + " bolt");
        builder.setBolt(deployTopologyBoltName, deployTopologyBolt, parallelism)
                .shuffleGrouping(commandBoltName, DEPLOY_TOPOLOGY_BOLT_STREAM);

        final String switchBoltName = SwitchBolt.class.getSimpleName();
        SwitchBolt switchBolt = new SwitchBolt();
        logger.debug("starting " + switchBoltName + " bolt");
        builder.setBolt(switchBoltName, switchBolt, parallelism)
                .shuffleGrouping(deployTopologyBoltName)
                .shuffleGrouping(commandBoltName, COMMAND_BOLT_STREAM);
//                .fieldsGrouping(deployTopologyBoltName, new Fields("dpid"));

        final String KILDA_TOPIC = "kilda-test";
        final String kafkaBoltName = KILDA_TOPIC + "Bolt";
        final String KAFKA_BOLT_STREAM = kafkaBoltName + "Stream";
        checkAndCreateTopic(KILDA_TOPIC);
        builder.setBolt(kafkaBoltName, createKafkaBolt(KILDA_TOPIC), parallelism)
                .shuffleGrouping(switchBoltName);

        return builder.createTopology();
    }
}
