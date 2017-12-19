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

package org.openkilda.wfm.topology.splitter;

import org.openkilda.messaging.ServiceType;
import org.openkilda.wfm.ConfigurationException;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.LaunchEnvironment;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.topology.TopologyBuilder;

/**
 * This topology does the following:
 * 1) Listen to the kilda open flow speaker topic and split it into info / command / other.
 * - It does that by listening to kafka via a kafka spout (createKafkaSpout) and adding
 * a bolt to that (OFEventSplitterBolt). The bolt is the thing that creates 3 storm streams.
 * 2) Create bolts to listen to each of those streams
 * - The bolts are KafkaBolts, tied to the streams emitted in #1.
 * 3) At present, we only have another splitter for the INFO channel, and the same strategy is
 * followed, with the exception that there isn't a need to listen to the INFO kafka topic,
 * since we already have the stream within storm.
 */
public class OFEventSplitterTopology extends AbstractTopology {
    private static Logger logger = LoggerFactory.getLogger(OFEventSplitterTopology.class);

    public OFEventSplitterTopology(LaunchEnvironment env) throws ConfigurationException {
        super(env);
    }

    /**
     * Build the Topology .. ie add it to the TopologyBuilder
     */
    public StormTopology createTopology() {
        logger.debug("Building Topology - {}", getTopologyName());

        String topic = config.getKafkaTopoDiscoTopic();
        Integer parallelism = config.getParallelism();

        /*
         * Setup the initial kafka spout and bolts to write the streams to kafka.
         */
        String primarySpout = topic + "-spout";
        String primaryBolt = topic + "-bolt";
        checkAndCreateTopic(topic); // in case the topic doesn't exist yet

        TopologyBuilder builder = new TopologyBuilder();
        builder.setSpout(primarySpout, createKafkaSpout(topic, getTopologyName()));
        builder.setBolt(primaryBolt, new OFEventSplitterBolt(), parallelism)
                .shuffleGrouping(primarySpout);

        /*
         * Setup the next level of spouts / bolts .. take the streams emitted and push to kafka.
         * At present, only INFO has further processing, so it has an additional bolt to create
         * more streams.  The others just need to write the stream their stream to a kafka topic.
         */
        for (String channel : OFEventSplitterBolt.CHANNELS) {
            checkAndCreateTopic(channel);
            builder.setBolt(channel + "-kafkabolt", createKafkaBolt(channel), parallelism)
                    .shuffleGrouping(primaryBolt, channel);

        }
        // NB: This is the extra bolt for INFO to generate more streams. If the others end up with
        //     their own splitter bolt, then we can move this logic into the for loop above.
        String infoSplitterBoltID = OFEventSplitterBolt.INFO + "-bolt";
        builder.setBolt(infoSplitterBoltID, new InfoEventSplitterBolt(), 3)
                .shuffleGrouping(primaryBolt, OFEventSplitterBolt.INFO);

        // Create the output from the InfoSplitter to Kafka
        // TODO: Can convert part of this to a test .. see if the right messages land in right topic
        for (String stream : InfoEventSplitterBolt.outputStreams) {
            checkAndCreateTopic(stream);
            builder.setBolt(stream + "-kafkabolt", createKafkaBolt(stream), parallelism)
                    .shuffleGrouping(infoSplitterBoltID, stream);
        }

        createHealthCheckHandler(builder, ServiceType.SPLITTER_TOPOLOGY.getId());

        return builder.createTopology();
    }

    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new OFEventSplitterTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
