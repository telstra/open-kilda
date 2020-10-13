/* Copyright 2020 Telstra Open Source
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

package org.openkilda.server42.control.topology;

import org.openkilda.messaging.Message;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.spi.PersistenceProvider;
import org.openkilda.server42.control.topology.storm.ComponentId;
import org.openkilda.server42.control.topology.storm.bolt.TickBolt;
import org.openkilda.server42.control.topology.storm.bolt.flow.FlowHandler;
import org.openkilda.server42.control.topology.storm.bolt.router.Router;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.topology.AbstractTopology;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;

public class ControlTopology extends AbstractTopology<ControlTopologyConfig> {
    private final PersistenceManager persistenceManager;

    public ControlTopology(LaunchEnvironment env) {
        super(env, ControlTopologyConfig.class);

        persistenceManager = PersistenceProvider.getInstance().getPersistenceManager(configurationProvider);
    }

    /**
     * Topology uploader.
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new ControlTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }

    /**
     * Topology factory.
     */
    @Override
    public StormTopology createTopology() {

        TopologyBuilder topology = new TopologyBuilder();

        inputFlowHs(topology, topologyConfig.getNewParallelism());
        inputNbWorker(topology, topologyConfig.getNewParallelism());
        //TODO: LCM stuff
        //inputControl(topology, topologyConfig.getNewParallelism());

        router(topology, topologyConfig.getNewParallelism());

        flowHandler(topology, topologyConfig.getNewParallelism());

        outputSpeaker(topology, topologyConfig.getNewParallelism());

        lcm(topology, topologyConfig);

        return topology.createTopology();
    }

    private void inputFlowHs(TopologyBuilder topology, int scaleFactor) {
        KafkaSpout<String, Message> spout = buildKafkaSpout(
                topologyConfig.getKafkaTopics().getFlowHsServer42StormNotifyTopic(),
                ComponentId.INPUT_FLOW_HS.toString());
        topology.setSpout(ComponentId.INPUT_FLOW_HS.toString(), spout, scaleFactor);
    }

    private void inputNbWorker(TopologyBuilder topology, int scaleFactor) {
        KafkaSpout<String, Message> spout = buildKafkaSpout(
                topologyConfig.getKafkaTopics().getNbWorkerServer42StormNotifyTopic(),
                ComponentId.INPUT_NB.toString());
        topology.setSpout(ComponentId.INPUT_NB.toString(), spout, scaleFactor);
    }

    private void inputControl(TopologyBuilder topology, int scaleFactor) {
        KafkaSpout<String, Message> spout = buildKafkaSpout(
                topologyConfig.getKafkaTopics().getServer42ControlCommandsReplyTopic(),
                ComponentId.INPUT_SERVER42_CONTROL.toString());
        topology.setSpout(ComponentId.INPUT_SERVER42_CONTROL.toString(), spout, scaleFactor);
    }

    private void router(TopologyBuilder topology, int scaleFactor) {
        Router bolt = new Router(persistenceManager);
        topology.setBolt(Router.BOLT_ID, bolt, scaleFactor)
                .shuffleGrouping(ComponentId.INPUT_FLOW_HS.toString())
                .shuffleGrouping(ComponentId.INPUT_NB.toString())
                .shuffleGrouping(ComponentId.TICK_BOLT.toString());
    }

    private void flowHandler(TopologyBuilder topology, int scaleFactor) {
        FlowHandler bolt = new FlowHandler(persistenceManager);
        Fields grouping = new Fields(Router.FIELD_ID_SWITCH_ID);
        topology.setBolt(FlowHandler.BOLT_ID, bolt, scaleFactor)
                .fieldsGrouping(Router.BOLT_ID, Router.STREAM_FLOW_ID, grouping);

    }

    private void outputSpeaker(TopologyBuilder topology, int scaleFactor) {

        KafkaBolt output = buildKafkaBoltWithRawObject(topologyConfig.getKafkaTopics().getServer42StormCommandsTopic());
        topology.setBolt(ComponentId.OUTPUT_SERVER42_CONTROL.toString(), output, scaleFactor)
                .shuffleGrouping(FlowHandler.BOLT_ID, FlowHandler.STREAM_CONTROL_COMMANDS_ID);
    }

    private void lcm(TopologyBuilder topology, ControlTopologyConfig topologyConfig) {
        topology.setBolt(TickBolt.BOLT_ID, new TickBolt(topologyConfig.getFlowRttSyncIntervalSeconds()));
    }
}
