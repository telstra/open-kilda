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

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.spi.PersistenceProvider;
import org.openkilda.wfm.CtrlBoltRef;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.error.StreamNameCollisionException;
import org.openkilda.wfm.topology.AbstractTopology;

import com.google.common.annotations.VisibleForTesting;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.topology.BoltDeclarer;
import org.apache.storm.topology.TopologyBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * OFEventWFMTopology creates the topology to manage these key aspects of OFEvents.
 * <p/>
 * (1) Switch UP/DOWN
 * (2) Port UP/DOWN
 * (3) Link UP/DOWN (ISL) - and health manager
 */
public class OfEventWfmTopology extends AbstractTopology<OFEventWfmTopologyConfig> {
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

    @VisibleForTesting
    public static final String DISCO_SPOUT_ID = "disco-spout";
    private static final String DISCO_BOLT_ID = OfeLinkBolt.class.getSimpleName();
    private static final String SPEAKER_BOLT_ID = "speaker-bolt";
    private static final String SPEAKER_DISCO_BOLT_ID = "speaker.disco-bolt";
    private static final String NETWORK_TOPOLOGY_BOLT_ID = "topology-bolt";
    private static final String REROUTE_BOLT_ID = "reroute-bolt";

    public OfEventWfmTopology(LaunchEnvironment env) {
        super(env, OFEventWfmTopologyConfig.class);
    }

    /**
     * The best place to look for detailed design information regarding this topologies
     * interactions is to look at docs/design/usecase/network-discovery.md
     * <p/>
     * At a high level, it receives input from the speaker, and sends output to the
     * topology engine.
     */
    public StormTopology createTopology() throws StreamNameCollisionException {
        logger.info("Building OfEventWfmTopology - {}", topologyName);

        String kafkaTopoDiscoTopic = topologyConfig.getKafkaTopoDiscoTopic();

        TopologyBuilder builder = new TopologyBuilder();

        builder.setSpout(DISCO_SPOUT_ID, createKafkaSpout(kafkaTopoDiscoTopic, DISCO_SPOUT_ID));

        // TODO: resolve the comments below; are there any state issues?
        // NB: with shuffleGrouping, we can't maintain state .. would need to parse first
        //      just to pull out switchID.
        // (crimi) - not sure I agree here .. state can be maintained, albeit distributed.
        //
        builder.setBolt(SPEAKER_BOLT_ID, createKafkaBolt(topologyConfig.getKafkaSpeakerTopic()),
                topologyConfig.getParallelism()).shuffleGrouping(DISCO_BOLT_ID, OfeLinkBolt.SPEAKER_STREAM);
        builder.setBolt(SPEAKER_DISCO_BOLT_ID, createKafkaBolt(topologyConfig.getKafkaSpeakerDiscoTopic()),
                topologyConfig.getParallelism()).shuffleGrouping(DISCO_BOLT_ID, OfeLinkBolt.SPEAKER_DISCO_STREAM);

        OfeLinkBolt ofeLinkBolt = new OfeLinkBolt(topologyConfig);
        BoltDeclarer bd = builder.setBolt(DISCO_BOLT_ID, ofeLinkBolt, topologyConfig.getParallelism())
                .shuffleGrouping(DISCO_SPOUT_ID);

        PersistenceManager persistenceManager =  PersistenceProvider.getInstance()
                .createPersistenceManager(configurationProvider);

        NetworkTopologyBolt networkTopologyBolt = new NetworkTopologyBolt(persistenceManager,
                topologyConfig.getIslCostWhenPortDown());
        builder.setBolt(NETWORK_TOPOLOGY_BOLT_ID, networkTopologyBolt, topologyConfig.getParallelism())
                .shuffleGrouping(DISCO_BOLT_ID, OfeLinkBolt.NETWORK_TOPOLOGY_CHANGE_STREAM);

        builder.setBolt(REROUTE_BOLT_ID,
                createKafkaBolt(topologyConfig.getKafkaTopoRerouteTopic()), topologyConfig.getParallelism())
                .shuffleGrouping(NETWORK_TOPOLOGY_BOLT_ID, NetworkTopologyBolt.REROUTE_STREAM);

        List<CtrlBoltRef> ctrlTargets = new ArrayList<>();
        // TODO: verify this ctrlTarget after refactoring.
        ctrlTargets.add(new CtrlBoltRef(DISCO_BOLT_ID, ofeLinkBolt, bd));
        createCtrlBranch(builder, ctrlTargets);

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

    /**
     * Main function.
     * @param args args.
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new OfEventWfmTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
