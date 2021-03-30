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

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.spi.PersistenceProvider;
import org.openkilda.server42.control.topology.storm.ComponentId;
import org.openkilda.server42.control.topology.storm.bolt.TickBolt;
import org.openkilda.server42.control.topology.storm.bolt.flow.FlowHandler;
import org.openkilda.server42.control.topology.storm.bolt.router.Router;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.share.zk.ZooKeeperSpout;
import org.openkilda.wfm.topology.AbstractTopology;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;

public class ControlTopology extends AbstractTopology<ControlTopologyConfig> {
    private final PersistenceManager persistenceManager;

    public ControlTopology(LaunchEnvironment env) {
        super(env, "control-topology", ControlTopologyConfig.class);

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

        zooKeeperSpout(topology);

        inputFlowHs(topology);
        inputNbWorker(topology);
        //TODO: LCM stuff
        //inputControl(topology, topologyConfig.getNewParallelism());

        zooKeeperBolt(topology);

        router(topology);

        flowHandler(topology);

        outputSpeaker(topology);

        lcm(topology, topologyConfig);

        return topology.createTopology();
    }

    private void zooKeeperSpout(TopologyBuilder topology) {
        ZooKeeperSpout zooKeeperSpout = new ZooKeeperSpout(getConfig().getBlueGreenMode(), getZkTopoName(),
                getZookeeperConfig());
        declareSpout(topology, zooKeeperSpout, ZooKeeperSpout.SPOUT_ID);
    }

    private void zooKeeperBolt(TopologyBuilder topology) {
        ZooKeeperBolt zooKeeperBolt = new ZooKeeperBolt(getConfig().getBlueGreenMode(), getZkTopoName(),
                getZookeeperConfig(), getBoltInstancesCount(Router.BOLT_ID));
        declareBolt(topology, zooKeeperBolt, ZooKeeperBolt.BOLT_ID)
                .allGrouping(Router.BOLT_ID, ZkStreams.ZK.toString());
    }

    private void inputFlowHs(TopologyBuilder topology) {
        declareKafkaSpout(topology,
                topologyConfig.getKafkaTopics().getFlowHsServer42StormNotifyTopic(),
                ComponentId.INPUT_FLOW_HS.toString());
    }

    private void inputNbWorker(TopologyBuilder topology) {
        declareKafkaSpout(topology,
                topologyConfig.getKafkaTopics().getNbWorkerServer42StormNotifyTopic(),
                ComponentId.INPUT_NB.toString());
    }

    private void inputControl(TopologyBuilder topology) {
        declareKafkaSpout(topology,
                topologyConfig.getKafkaTopics().getServer42ControlCommandsReplyTopic(),
                ComponentId.INPUT_SERVER42_CONTROL.toString());
    }

    private void router(TopologyBuilder topology) {
        Router bolt = new Router(persistenceManager, ZooKeeperSpout.SPOUT_ID);
        declareBolt(topology, bolt, Router.BOLT_ID)
                .shuffleGrouping(ComponentId.INPUT_FLOW_HS.toString())
                .shuffleGrouping(ComponentId.INPUT_NB.toString())
                .shuffleGrouping(ComponentId.TICK_BOLT.toString())
                .allGrouping(ZooKeeperSpout.SPOUT_ID);
    }

    private void flowHandler(TopologyBuilder topology) {
        FlowHandler bolt = new FlowHandler(persistenceManager);
        Fields grouping = new Fields(Router.FIELD_ID_SWITCH_ID);
        declareBolt(topology, bolt, FlowHandler.BOLT_ID)
                .fieldsGrouping(Router.BOLT_ID, Router.STREAM_FLOW_ID, grouping);

    }

    private void outputSpeaker(TopologyBuilder topology) {
        KafkaBolt output = buildKafkaBoltWithRawObject(topologyConfig.getKafkaTopics().getServer42StormCommandsTopic());
        declareBolt(topology, output, ComponentId.OUTPUT_SERVER42_CONTROL.toString())
                .shuffleGrouping(FlowHandler.BOLT_ID, FlowHandler.STREAM_CONTROL_COMMANDS_ID);
    }

    private void lcm(TopologyBuilder topology, ControlTopologyConfig topologyConfig) {
        TickBolt tickBolt = new TickBolt(topologyConfig.getFlowRttSyncIntervalSeconds());
        declareBolt(topology, tickBolt, TickBolt.BOLT_ID);
    }

    @Override
    protected String getZkTopoName() {
        return "server42-control";
    }
}
