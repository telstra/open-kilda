package org.openkilda.simulator;

import org.openkilda.simulator.bolts.CommandBolt;
import org.openkilda.simulator.bolts.DeployTopologyBolt;
import org.openkilda.simulator.bolts.SwitchBolt;
import org.openkilda.wfm.ConfigurationException;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.topology.AbstractTopology;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.topology.TopologyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimulatorTopology extends AbstractTopology {
    private static final Logger logger = LoggerFactory.getLogger(SimulatorTopology.class);

    public static final String DEPLOY_TOPOLOGY_BOLT_STREAM = "deploy_topology_stream";
    public static final String COMMAND_BOLT_STREAM = "command_bolt_stream";

    public SimulatorTopology(LaunchEnvironment env) throws ConfigurationException {
        super(env);
    }

    @Override
    public StormTopology createTopology() {
        final Integer parallelism = config.getParallelism();
        final String inputTopic = config.getKafkaInputTopic();
        final String simulatorTopic = config.getKafkaSimulatorTopic();
        final String simulatorSpout = "simulator-spout";
        final String commandSpout = "command-spout";

        final TopologyBuilder builder = new TopologyBuilder();

        logger.debug("Building SimulatorTopology - {}", getTopologyName());

        checkAndCreateTopic(simulatorTopic);
        logger.debug("connecting to {} topic", simulatorTopic);
        builder.setSpout(simulatorSpout, createKafkaSpout(simulatorTopic, getTopologyName()));

        checkAndCreateTopic(inputTopic);
        logger.debug("connecting to {} topic", inputTopic);
        builder.setSpout(commandSpout, createKafkaSpout(inputTopic, commandSpout));

        final String commandBoltName = CommandBolt.class.getSimpleName();
        CommandBolt commandBolt = new CommandBolt();
        logger.debug("starting " + commandBoltName + " bolt");
        builder.setBolt(commandBoltName, commandBolt, parallelism)
                .shuffleGrouping(simulatorSpout)
                .shuffleGrouping(commandSpout);

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

        // TODO(dbogun): check is it must be output topic
        final String kafkaBoltName = inputTopic + "Bolt";
        checkAndCreateTopic(inputTopic);
        builder.setBolt(kafkaBoltName, createKafkaBolt(inputTopic), parallelism)
                .shuffleGrouping(switchBoltName);

        return builder.createTopology();
    }

    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new SimulatorTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
