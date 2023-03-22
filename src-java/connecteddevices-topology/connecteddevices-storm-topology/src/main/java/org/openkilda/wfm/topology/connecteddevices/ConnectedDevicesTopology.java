/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.connecteddevices;

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.share.zk.ZooKeeperSpout;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.connecteddevices.bolts.PacketBolt;
import org.openkilda.wfm.topology.connecteddevices.bolts.RouterBolt;
import org.openkilda.wfm.topology.utils.KafkaRecordTranslator;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;

public class ConnectedDevicesTopology extends AbstractTopology<ConnectedDevicesTopologyConfig> {
    public static final String CONNECTED_DEVICES_SPOUT_ID = "connected-devices-spout";
    public static final String ROUTER_BOLT_ID = "router-bolt";
    public static final String PACKET_BOLT_ID = "packet-bolt";

    public ConnectedDevicesTopology(LaunchEnvironment env) {
        super(env, "connecteddevices-topology", ConnectedDevicesTopologyConfig.class);
    }

    /**
     * Creating topology.
     */
    public StormTopology createTopology() {
        TopologyBuilder builder = new TopologyBuilder();
        PersistenceManager persistenceManager = new PersistenceManager(configurationProvider);

        createZkSpout(builder);

        createSpout(builder);
        createRouterBolt(builder, persistenceManager);
        createPacketBolt(builder, persistenceManager);

        createZkBolt(builder);

        return builder.createTopology();
    }

    private void createZkSpout(TopologyBuilder builder) {
        ZooKeeperSpout zooKeeperSpout = new ZooKeeperSpout(getConfig().getBlueGreenMode(), getZkTopoName(),
                getZookeeperConfig());
        declareSpout(builder, zooKeeperSpout, ZooKeeperSpout.SPOUT_ID);
    }

    private void createRouterBolt(TopologyBuilder builder, PersistenceManager persistenceManager) {
        RouterBolt routerBolt = new RouterBolt(persistenceManager, ZooKeeperSpout.SPOUT_ID);
        declareBolt(builder, routerBolt, ROUTER_BOLT_ID)
                .shuffleGrouping(CONNECTED_DEVICES_SPOUT_ID)
                .allGrouping(ZooKeeperSpout.SPOUT_ID);
    }

    private void createPacketBolt(TopologyBuilder builder, PersistenceManager persistenceManager) {
        PacketBolt packetBolt = new PacketBolt(persistenceManager);
        declareBolt(builder, packetBolt, PACKET_BOLT_ID)
                .fieldsGrouping(ROUTER_BOLT_ID, RouterBolt.PACKET_STREAM_ID,
                        new Fields(KafkaRecordTranslator.FIELD_ID_KEY));
    }

    private void createSpout(TopologyBuilder builder) {
        declareKafkaSpout(builder, topologyConfig.getKafkaTopoConnectedDevicesTopic(), CONNECTED_DEVICES_SPOUT_ID);
    }

    private void createZkBolt(TopologyBuilder builder) {
        ZooKeeperBolt zooKeeperBolt = new ZooKeeperBolt(getConfig().getBlueGreenMode(), getZkTopoName(),
                getZookeeperConfig(), getBoltInstancesCount(ROUTER_BOLT_ID));
        declareBolt(builder, zooKeeperBolt, ZooKeeperBolt.BOLT_ID)
                .allGrouping(ROUTER_BOLT_ID, ZkStreams.ZK.toString());
    }

    @Override
    protected String getZkTopoName() {
        return "connecteddevices";
    }

    /**
     * Main method to run topology.
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new ConnectedDevicesTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
