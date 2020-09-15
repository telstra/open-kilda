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

package org.openkilda.wfm.topology.snmp;

import org.openkilda.messaging.Message;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.spi.PersistenceProvider;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.error.NameCollisionException;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.snmp.bolts.SnmpTopologyBolt;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.topology.TopologyBuilder;

public class SnmpTopology extends AbstractTopology<SnmpTopologyConfig> {

    private static final String SPOUT_ID_SNMP_TOPOLOGY = "snmp-spout";
    private static final String BOLT_ID_SNMP_TOPOLOGY = "snmp-topology-bolt";

    public SnmpTopology(LaunchEnvironment env) {
        super(env, SnmpTopologyConfig.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StormTopology createTopology() throws NameCollisionException {
        logger.info("Creating SNMP topology - {}", topologyName);

        TopologyBuilder topologyBuilder = new TopologyBuilder();

        final Integer parallelism = topologyConfig.getNewParallelism();

        // this is the spout for network topology update
        KafkaSpout<String, Message> kafkaSpout = buildKafkaSpout(topologyConfig.getNetworkUpdateTopic(),
                SPOUT_ID_SNMP_TOPOLOGY);
        topologyBuilder.setSpout(SPOUT_ID_SNMP_TOPOLOGY, kafkaSpout, parallelism);

        PersistenceManager persistenceManager = PersistenceProvider.getInstance()
                .createPersistenceManager(configurationProvider);

        SnmpTopologyBolt snmpTopologyBolt = new SnmpTopologyBolt(persistenceManager, topologyConfig);
        topologyBuilder.setBolt(BOLT_ID_SNMP_TOPOLOGY, snmpTopologyBolt, parallelism)
                .shuffleGrouping(SPOUT_ID_SNMP_TOPOLOGY);

        return topologyBuilder.createTopology();
    }

    /**
     * Launches and sets up the workflow manager environment.
     *
     * @param args the command-line arguments.
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            new SnmpTopology(env).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }

}
