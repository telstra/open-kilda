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
import org.openkilda.messaging.Topic;
import org.openkilda.wfm.CtrlBoltRef;
import org.openkilda.wfm.error.ConfigurationException;
import org.openkilda.wfm.error.StreamNameCollisionException;
import org.openkilda.wfm.ctrl.ICtrlBolt;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.LaunchEnvironment;

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
     * (6) ◊ Link DOWN - this will be a response from the Discovery packet
     * (7) ◊ Add simple pass through for verification (w/ speaker) & validation (w/ TPE)
     */

    private static Logger logger = LoggerFactory.getLogger(OFEventWFMTopology.class);

    /** Externalize the ID so that the Test classes can leverage it */
    public static final String SPOUT_ID_INPUT = Topic.TOPO_DISCO+"-spout";
    public static final String BOLT_ID = Topic.TOPO_DISCO+"-bolt";

//    public static final String SPOUT_ID_INPUT = "input";
//    public static final String BOLT_ID_OUTPUT = "out";

    public OFEventWFMTopology(LaunchEnvironment env) throws ConfigurationException {
        super(env);
    }

    /**
     * The best place to look for detailed design information regarding this topologies
     * interactions is to look at docs/design/usecase/network-discovery.md
     *
     * At a high level, it receives input from the speaker, and sends output to the
     * topology engine.
     *
     * @return
     * @throws StreamNameCollisionException
     */
    public StormTopology createTopology() throws StreamNameCollisionException {
        logger.debug("Building Topology - " + this.getClass().getSimpleName());

        String kafkaTopoDiscoTopic = config.getKafkaTopoDiscoTopic();
        String kafkaTopoEngTopic = config.getKafkaTopoEngTopic();
        String kafkaSpeakerTopic = config.getKafkaSpeakerTopic();

        checkAndCreateTopic(kafkaTopoDiscoTopic);
        checkAndCreateTopic(kafkaTopoEngTopic);

        TopologyBuilder builder = new TopologyBuilder();
        List<CtrlBoltRef> ctrlTargets = new ArrayList<>();

        String spoutName = SPOUT_ID_INPUT;
        String boltName = BOLT_ID;

        builder.setSpout(spoutName, createKafkaSpout(kafkaTopoDiscoTopic, spoutName));

        IStatefulBolt bolt = new OFELinkBolt(config);

        // TODO: resolve the comments below; are there any state issues?
        // NB: with shuffleGrouping, we can't maintain state .. would need to parse first
        //      just to pull out switchID.
        // (crimi) - not sure I agree here .. state can be maintained, albeit distributed.
        //
        BoltDeclarer bd = builder.setBolt(boltName, bolt, config.getParallelism())
                .shuffleGrouping(spoutName);

        builder.setBolt(kafkaTopoEngTopic, createKafkaBolt(kafkaTopoEngTopic),
                config.getParallelism()).shuffleGrouping(boltName, kafkaTopoEngTopic);
        builder.setBolt(kafkaSpeakerTopic, createKafkaBolt(kafkaSpeakerTopic),
                config.getParallelism()).shuffleGrouping(boltName, kafkaSpeakerTopic);

        // TODO: verify this ctrlTarget after refactoring.
        ctrlTargets.add(new CtrlBoltRef(boltName, (ICtrlBolt) bolt, bd));
        createCtrlBranch(builder, ctrlTargets);
        // TODO: verify WFM_TOPOLOGY health check
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

    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new OFEventWFMTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
