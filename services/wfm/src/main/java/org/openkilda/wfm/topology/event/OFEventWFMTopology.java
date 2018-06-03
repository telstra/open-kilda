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
import org.openkilda.wfm.CtrlBoltRef;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.ctrl.ICtrlBolt;
import org.openkilda.wfm.error.StreamNameCollisionException;
import org.openkilda.wfm.topology.AbstractTopology;

import com.google.common.annotations.VisibleForTesting;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.topology.BoltDeclarer;
import org.apache.storm.topology.IStatefulBolt;
import org.apache.storm.topology.TopologyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * OFEventWFMTopology creates the topology to manage these key aspects of OFEvents:
 * <p>
 * (1) Switch UP/DOWN
 * (2) Port UP/DOWN
 * (3) Link UP/DOWN (ISL) - and health manager
 */
public class OFEventWFMTopology extends AbstractTopology<OFEventWfmTopologyConfig> {
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

    public OFEventWFMTopology(LaunchEnvironment env) {
        super(env, OFEventWfmTopologyConfig.class);
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

        String kafkaTopoDiscoTopic = topologyConfig.getKafkaTopoDiscoTopic();
        String kafkaTopoEngTopic = topologyConfig.getKafkaTopoEngTopic();
        String kafkaSpeakerTopic = topologyConfig.getKafkaSpeakerTopic();

        checkAndCreateTopic(kafkaTopoDiscoTopic);
        checkAndCreateTopic(kafkaTopoEngTopic);

        TopologyBuilder builder = new TopologyBuilder();
        List<CtrlBoltRef> ctrlTargets = new ArrayList<>();

        String spoutName = getSpoutIdInput();
        String boltName = kafkaTopoDiscoTopic + "-bolt";

        builder.setSpout(spoutName, createKafkaSpout(kafkaTopoDiscoTopic, spoutName));

        IStatefulBolt bolt = new OFELinkBolt(topologyConfig);

        // TODO: resolve the comments below; are there any state issues?
        // NB: with shuffleGrouping, we can't maintain state .. would need to parse first
        //      just to pull out switchID.
        // (crimi) - not sure I agree here .. state can be maintained, albeit distributed.
        //
        BoltDeclarer bd = builder.setBolt(boltName, bolt, topologyConfig.getParallelism())
                .shuffleGrouping(spoutName);

        builder.setBolt(kafkaTopoEngTopic, createKafkaBolt(kafkaTopoEngTopic),
                topologyConfig.getParallelism()).shuffleGrouping(boltName, kafkaTopoEngTopic);
        builder.setBolt(kafkaSpeakerTopic, createKafkaBolt(kafkaSpeakerTopic),
                topologyConfig.getParallelism()).shuffleGrouping(boltName, kafkaSpeakerTopic);

        // TODO: verify this ctrlTarget after refactoring.
        ctrlTargets.add(new CtrlBoltRef(boltName, (ICtrlBolt) bolt, bd));
        createCtrlBranch(builder, ctrlTargets);
        // TODO: verify WFM_TOPOLOGY health check
        createHealthCheckHandler(builder, ServiceType.WFM_TOPOLOGY.getId());

        return builder.createTopology();
    }

    /** Externalize the ID so that the Test classes can leverage it. */
    @VisibleForTesting
    public String getSpoutIdInput() {
        return topologyConfig.getKafkaTopoDiscoTopic() + "-spout";
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
