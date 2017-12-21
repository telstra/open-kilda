/* Copyright 2017 Telstra Open Source
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

package org.openkilda.wfm.topology.event;

import org.openkilda.messaging.ServiceType;
import org.openkilda.wfm.ConfigurationException;
import org.openkilda.wfm.CtrlBoltRef;
import org.openkilda.wfm.StreamNameCollisionException;
import org.openkilda.wfm.ctrl.ICtrlBolt;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.topology.splitter.InfoEventSplitterBolt;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.topology.BoltDeclarer;
import org.apache.storm.topology.IStatefulBolt;
import org.apache.storm.topology.TopologyBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * OFEventWFMTopology creates the topology to manage these key aspects of OFEvents:
 * <p>
 * (1) Switch UP/DOWN
 * (2) Port UP/DOWN
 * (3) Link UP/DOWN (ISL) - and health manager
 */
public class OFEventWFMTopology extends AbstractTopology {
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

    private static Logger logger = LoggerFactory.getLogger(OFEventWFMTopology.class);

    public static final String SPOUT_ID_INPUT = "input";
    public static final String BOLT_ID_OUTPUT = "out";

    /**
     * This is the primary input topics
     */
    private String[] topics = {
            InfoEventSplitterBolt.I_SWITCH_UPDOWN,
            InfoEventSplitterBolt.I_PORT_UPDOWN,
            InfoEventSplitterBolt.I_ISL_UPDOWN
    };

    public OFEventWFMTopology(LaunchEnvironment env) throws ConfigurationException {
        super(env);
    }

    public StormTopology createTopology() throws StreamNameCollisionException {
        logger.debug("Building Topology - " + this.getClass().getSimpleName());

        initKafka();

        String kafkaTopoDiscoTopic = config.getKafkaTopoDiscoTopic();
        TopologyBuilder builder = new TopologyBuilder();
        List<CtrlBoltRef> ctrlTargets = new ArrayList<>();

        builder.setSpout(SPOUT_ID_INPUT, createKafkaSpout(kafkaTopoDiscoTopic, SPOUT_ID_INPUT));

        BoltDeclarer kbolt = builder.setBolt(BOLT_ID_OUTPUT,
                createKafkaBolt(kafkaTopoDiscoTopic), config.getParallelism());

        // The order of bolts should match topics, and Link should be last .. logic below relies on it
        IStatefulBolt[] bolts = {
                new OFESwitchBolt().withOutputStreamId(kafkaTopoDiscoTopic),
                new OFEPortBolt().withOutputStreamId(kafkaTopoDiscoTopic),
                new OFELinkBolt(config)
        };

        // tbolt will save the setBolt() results; will be useed to add switch/port to link
        BoltDeclarer[] tbolt = new BoltDeclarer[bolts.length];

        for (int i = 0; i < topics.length; i++) {
            String topic = topics[i];
            String spoutName = topic + "-spout";
            String boltName = topic + "-bolt";

            builder.setSpout(spoutName, createKafkaSpout(topic, getTopologyName()));

            // NB: with shuffleGrouping, we can't maintain state .. would need to parse first
            //      just to pull out switchID.
            tbolt[i] = builder.setBolt(boltName, bolts[i], config.getParallelism()).shuffleGrouping(spoutName);
            kbolt.shuffleGrouping(boltName, kafkaTopoDiscoTopic);

            ctrlTargets.add(new CtrlBoltRef(boltName, (ICtrlBolt) bolts[i], tbolt[i]));
        }

        // now hookup switch and port to the link bolt so that it can take appropriate action
        /*
          FIXME(surabujin): use "correct" stream id. Now it works only because stream id matches kafka topic name, but
          topic name can be changed via config
        */
        tbolt[2].shuffleGrouping(topics[0] + "-bolt", kafkaTopoDiscoTopic)
                .shuffleGrouping(topics[1] + "-bolt", kafkaTopoDiscoTopic)
                .allGrouping(SPOUT_ID_INPUT);

        // finally, one more bolt, to write the ISL Discovery requests
        String discoTopic = config.getKafkaTopoDiscoTopic();
        builder.setBolt("ISL_Discovery-kafkabolt", createKafkaBolt(discoTopic), config.getParallelism())
                .shuffleGrouping(topics[2] + "-bolt", discoTopic);

        createCtrlBranch(builder, ctrlTargets);
        createHealthCheckHandler(builder, ServiceType.WFM_TOPOLOGY.getId());

        return builder.createTopology();
    }

    /*
     * Progress Tracker - Phase 2: Speaker / TPE Integration; Cache Coherency Checks; Flapping
     * (1) ◊ - Interact with Speaker (network element is / isn't there)
     * (2) ◊ - Interact with TPE (graph element is / isn't there)
     * (3) ◊ - Validate the Topology periodically - switches, ports, links
     *          - health checks should validate the known universe; what about missing stuff?
     * (4) ◊ - See if flapping happens .. define window and if there are greater than 4 up/downs?
     */

    private void initKafka() {
        checkAndCreateTopic(config.getKafkaTopoDiscoTopic());
        for (String topic : topics) {
            checkAndCreateTopic(topic);
        }
    }

    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new OFEventWFMTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
