package org.openkilda.simulator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.tuple.Fields;
import org.openkilda.simulator.bolts.CommandBolt;
import org.openkilda.simulator.bolts.SimulatorCommandBolt;
import org.openkilda.simulator.bolts.SwitchBolt;
import org.openkilda.wfm.topology.AbstractTopology;
import org.apache.storm.topology.TopologyBuilder;
import org.openkilda.wfm.topology.Topology;

import java.io.File;

public class SimulatorTopology extends AbstractTopology {
    private static final Logger logger = LogManager.getLogger(SimulatorTopology.class);
    private final String topoName = "simulatorTopology";
    private final int parallelism = 1;
    public static final String SIMULATOR_TOPIC = "kilda-simulator";
    public static  final String COMMAND_TOPIC = "kilda-test";
    public static final String SIMULATOR_SPOUT = "simulator-spout";
    public static final String COMMAND_SPOUT = "command-spout";
    public static final String COMMAND_BOLT_STREAM = "command_bolt_stream";
    public static final String COMMAND_BOLT = "command_bolt";
    public static final String SWITCH_BOLT = "switch_bolt";
    public static final String SWITCH_BOLT_STREAM = "switch_bolt_stream";
    public static final String KAFKA_BOLT = "kafka_bolt";
    public static final String KAFKA_BOLT_STREAM = "kafka_bolt_stream";
    public static final String SIMULATOR_COMMAND_BOLT = "simulator_command_bolt";
    public static final String SIMULATOR_COMMAND_STREAM = "simulator_command_stream";

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

        CommandBolt commandBolt = new CommandBolt();
        logger.debug("starting " + COMMAND_BOLT + " bolt");
        builder.setBolt(COMMAND_BOLT, commandBolt, parallelism)
                .shuffleGrouping(SIMULATOR_SPOUT)
                .shuffleGrouping(COMMAND_SPOUT);

        SimulatorCommandBolt simulatorCommandBolt = new SimulatorCommandBolt();
        logger.debug("starting " + SIMULATOR_COMMAND_BOLT + " bolt");
        builder.setBolt(SIMULATOR_COMMAND_BOLT, simulatorCommandBolt, parallelism)
                .shuffleGrouping(SIMULATOR_SPOUT);

        SwitchBolt switchBolt = new SwitchBolt();
        logger.debug("starting " + SWITCH_BOLT + " bolt");
        builder.setBolt(SWITCH_BOLT, switchBolt, 1)
                .fieldsGrouping(COMMAND_BOLT, COMMAND_BOLT_STREAM, new Fields("dpid"))
                .fieldsGrouping(SWITCH_BOLT, SWITCH_BOLT_STREAM, new Fields("dpid"))
                .fieldsGrouping(SIMULATOR_COMMAND_BOLT, SIMULATOR_COMMAND_STREAM, new Fields("dpid"));

        final String KILDA_TOPIC = "kilda-test";
        checkAndCreateTopic(KILDA_TOPIC);
        builder.setBolt(KAFKA_BOLT, createKafkaBolt(KILDA_TOPIC), parallelism)
                .shuffleGrouping(SWITCH_BOLT, KAFKA_BOLT_STREAM);

        return builder.createTopology();
    }
}
