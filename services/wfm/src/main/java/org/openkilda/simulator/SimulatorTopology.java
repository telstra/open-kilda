package org.openkilda.simulator;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;
import org.openkilda.simulator.bolts.CommandBolt;
import org.openkilda.simulator.bolts.SimulatorCommandBolt;
import org.openkilda.simulator.bolts.SpeakerBolt;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.error.ConfigurationException;
import org.openkilda.wfm.topology.AbstractTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimulatorTopology extends AbstractTopology {
    private final String topoName = "simulatorTopology";
    private final int parallelism = 1;
    public static final String SIMULATOR_TOPIC = "kilda-simulator";
    public static  final String COMMAND_TOPIC = "kilda-test";
    public static final String SIMULATOR_SPOUT = "simulator-spout";
    public static final String COMMAND_SPOUT = "command-spout";
    private static final Logger logger = LoggerFactory.getLogger(SimulatorTopology.class);
    public static final String DEPLOY_TOPOLOGY_BOLT_STREAM = "deploy_topology_stream";
    public static final String COMMAND_BOLT_STREAM = "command_bolt_stream";
    public static final String COMMAND_BOLT = "command_bolt";
    public static final String SWITCH_BOLT = "switch_bolt";
    public static final String SWITCH_BOLT_STREAM = "switch_bolt_stream";
    public static final String KAFKA_BOLT = "kafka_bolt";
    public static final String KAFKA_BOLT_STREAM = "kafka_bolt_stream";
    public static final String SIMULATOR_COMMAND_BOLT = "simulator_command_bolt";
    public static final String SIMULATOR_COMMAND_STREAM = "simulator_command_stream";

    public SimulatorTopology(LaunchEnvironment env) throws ConfigurationException {
        super(env);
    }

    @Override
    public StormTopology createTopology() {
        final Integer parallelism = config.getParallelism();
        final String inputTopic = config.getKafkaSpeakerTopic();
        final String simulatorTopic = config.getKafkaSimulatorTopic();


        final TopologyBuilder builder = new TopologyBuilder();

        logger.debug("Building SimulatorTopology - {}", getTopologyName());

        checkAndCreateTopic(simulatorTopic);
        logger.debug("connecting to {} topic", simulatorTopic);
        builder.setSpout(SIMULATOR_SPOUT, createKafkaSpout(simulatorTopic, getTopologyName()));

        checkAndCreateTopic(inputTopic);
        logger.debug("connecting to {} topic", inputTopic);
        builder.setSpout(COMMAND_SPOUT, createKafkaSpout(inputTopic, COMMAND_SPOUT));

        CommandBolt commandBolt = new CommandBolt();
        logger.debug("starting " + COMMAND_BOLT + " bolt");
        builder.setBolt(COMMAND_BOLT, commandBolt, parallelism)
                .shuffleGrouping(SIMULATOR_SPOUT)
                .shuffleGrouping(COMMAND_SPOUT);

        SimulatorCommandBolt simulatorCommandBolt = new SimulatorCommandBolt();
        logger.debug("starting " + SIMULATOR_COMMAND_BOLT + " bolt");
        builder.setBolt(SIMULATOR_COMMAND_BOLT, simulatorCommandBolt, parallelism)
                .shuffleGrouping(SIMULATOR_SPOUT);

        SpeakerBolt speakerBolt = new SpeakerBolt();
        logger.debug("starting " + SWITCH_BOLT + " bolt");
        builder.setBolt(SWITCH_BOLT, speakerBolt, 1)
                .fieldsGrouping(COMMAND_BOLT, COMMAND_BOLT_STREAM, new Fields("dpid"))
                .fieldsGrouping(SWITCH_BOLT, SWITCH_BOLT_STREAM, new Fields("dpid"))
                .fieldsGrouping(SIMULATOR_COMMAND_BOLT, SIMULATOR_COMMAND_STREAM, new Fields("dpid"));

        // TODO(dbogun): check is it must be output topic
        checkAndCreateTopic(inputTopic);
        builder.setBolt(KAFKA_BOLT, createKafkaBolt(inputTopic), parallelism)
                .shuffleGrouping(SWITCH_BOLT, KAFKA_BOLT_STREAM);

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
