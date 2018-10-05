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
import org.openkilda.wfm.topology.event.bolt.ComponentId;
import org.openkilda.wfm.topology.event.bolt.FlMonitor;
import org.openkilda.wfm.topology.event.bolt.MonotonicTick;
import org.openkilda.wfm.topology.event.bolt.OfeLinkBolt;
import org.openkilda.wfm.topology.event.bolt.SpeakerDecoder;
import org.openkilda.wfm.topology.ping.bolt.SpeakerEncoder;

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

        final List<CtrlBoltRef> ctrlTargets = new ArrayList<>();
        TopologyBuilder builder = new TopologyBuilder();

        // monotonic tick
        builder.setBolt(MonotonicTick.BOLT_ID, new MonotonicTick());

        // speaker spout
        builder.setSpout(ComponentId.SPEAKER_SPOUT.toString(),
                createKafkaSpout(kafkaTopoDiscoTopic, ComponentId.SPEAKER_SPOUT.toString()));
        // speaker decoder
        builder.setBolt(SpeakerDecoder.BOLT_ID, new SpeakerDecoder())
                .shuffleGrouping(ComponentId.SPEAKER_SPOUT.toString());

        // speaker monitor
        builder.setBolt(FlMonitor.BOLT_ID, new FlMonitor(topologyConfig))
                .shuffleGrouping(MonotonicTick.BOLT_ID)
                .shuffleGrouping(SpeakerDecoder.BOLT_ID);

        // ISL discovery
        OfeLinkBolt ofeLinkBolt = new OfeLinkBolt(topologyConfig);
        BoltDeclarer boltDeclarer = builder.setBolt(OfeLinkBolt.BOLT_ID, ofeLinkBolt, topologyConfig.getParallelism())
                .allGrouping(MonotonicTick.BOLT_ID)
                .shuffleGrouping(FlMonitor.BOLT_ID)
                .shuffleGrouping(FlMonitor.BOLT_ID, FlMonitor.STREAM_SYNC_ID);
        ctrlTargets.add(new CtrlBoltRef(OfeLinkBolt.BOLT_ID, ofeLinkBolt, boltDeclarer));

        PersistenceManager persistenceManager =  PersistenceProvider.getInstance()
                .createPersistenceManager(configurationProvider);

        NetworkTopologyBolt networkTopologyBolt = new NetworkTopologyBolt(persistenceManager,
                topologyConfig.getIslCostWhenPortDown());
        builder.setBolt(NETWORK_TOPOLOGY_BOLT_ID, networkTopologyBolt, topologyConfig.getParallelism())
                .shuffleGrouping(DISCO_BOLT_ID, OfeLinkBolt.NETWORK_TOPOLOGY_CHANGE_STREAM);

        builder.setBolt(REROUTE_BOLT_ID,
                createKafkaBolt(topologyConfig.getKafkaTopoRerouteTopic()), topologyConfig.getParallelism())
                .shuffleGrouping(NETWORK_TOPOLOGY_BOLT_ID, NetworkTopologyBolt.REROUTE_STREAM);

        // speaker encoder
        builder.setBolt(SpeakerEncoder.BOLT_ID, new SpeakerEncoder())
                .shuffleGrouping(FlMonitor.BOLT_ID, FlMonitor.STREAM_SPEAKER_ID)
                .shuffleGrouping(OfeLinkBolt.BOLT_ID, OfeLinkBolt.SPEAKER_DISCO_STREAM);

        // kafka exporters
        builder.setBolt(SPEAKER_DISCO_BOLT_ID, createKafkaBolt(topologyConfig.getKafkaSpeakerDiscoTopic()),
                topologyConfig.getParallelism())
                .shuffleGrouping(SpeakerEncoder.BOLT_ID);

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
