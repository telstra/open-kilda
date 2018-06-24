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

package org.openkilda.wfm.topology.utils;

import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.topology.AbstractTopology;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.topology.TopologyBuilder;

import java.io.File;

/**
 * Take a kafka topic and dump it to file.
 */
public class KafkaFilerTopology extends AbstractTopology<KafkaFilerTopologyConfig> {
    private String topic;
    /**
     * assigned after createTopology() is called
     */
    private FilerBolt filer;

    public KafkaFilerTopology(LaunchEnvironment env) {
        super(env, KafkaFilerTopologyConfig.class);

        topic = topologyConfig.getKafkaSpeakerTopic();
    }

    public KafkaFilerTopology(LaunchEnvironment env, String topic) {
        super(env, KafkaFilerTopologyConfig.class);

        this.topic = topic;
    }

    @Override
    public StormTopology createTopology() {
        final String directory = topologyConfig.getFilterDirectory();
        final String name = String.format("%s_%s_%s_%d", getTopologyName(), topic, directory, System.currentTimeMillis());

        String spoutId = "KafkaSpout-" + topic;
        int parallelism = 1;

        TopologyBuilder builder = new TopologyBuilder();
        builder.setSpout(spoutId, createKafkaSpout(topic, name), parallelism);
        filer = new FilerBolt().withFileName("utils-" + topic + ".log");
        if (directory.length() != 0)
            filer.withDir(new File(directory));

        builder.setBolt("utils", filer, parallelism)
                .shuffleGrouping(spoutId);
        return builder.createTopology();
    }

    public FilerBolt getFiler() {
        return filer;
    }

    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new KafkaFilerTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
