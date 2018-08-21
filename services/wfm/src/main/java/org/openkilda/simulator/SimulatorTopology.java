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

package org.openkilda.simulator;

import org.openkilda.simulator.bolts.CommandBolt;
import org.openkilda.simulator.bolts.SimulatorCommandBolt;
import org.openkilda.simulator.bolts.SpeakerBolt;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.topology.AbstractTopology;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimulatorTopology extends AbstractTopology<SimulatorTopologyConfig> {
    private final String topoName = "simulatorTopology";
    private final int parallelism = 1;

    public static final String SIMULATOR_SPOUT = "simulator-spout";
    public static final String COMMAND_SPOUT = "command-spout";
    public static final String DEPLOY_TOPOLOGY_BOLT_STREAM = "deploy_topology_stream";
    public static final String COMMAND_BOLT_STREAM = "command_bolt_stream";
    public static final String COMMAND_BOLT = "command_bolt";
    public static final String SWITCH_BOLT = "switch_bolt";
    public static final String SWITCH_BOLT_STREAM = "switch_bolt_stream";
    public static final String KAFKA_BOLT = "kafka_bolt";
    public static final String KAFKA_BOLT_STREAM = "kafka_bolt_stream";
    public static final String SIMULATOR_COMMAND_BOLT = "simulator_command_bolt";
    public static final String SIMULATOR_COMMAND_STREAM = "simulator_command_stream";

    private static final Logger logger = LoggerFactory.getLogger(SimulatorTopology.class);

    public SimulatorTopology(LaunchEnvironment env) {
        super(env, SimulatorTopologyConfig.class);
    }

    @Override
    public StormTopology createTopology() {
        final int parallelism = topologyConfig.getParallelism();
        final String inputTopic = topologyConfig.getKafkaSpeakerTopic();
        final String simulatorTopic = topologyConfig.getKafkaSimulatorTopic();

        final TopologyBuilder builder = new TopologyBuilder();

        logger.debug("Building SimulatorTopology - {}", topologyName);

        checkAndCreateTopic(simulatorTopic);
        logger.debug("connecting to {} topic", simulatorTopic);
        builder.setSpout(SIMULATOR_SPOUT, createKafkaSpout(simulatorTopic, SIMULATOR_SPOUT));

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
