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

import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.Topology;

import org.apache.storm.Config;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.topology.TopologyBuilder;

import java.io.File;

/**
 * Take a kafka topic and dump it to file.
 */
public class KafkaFilerTopology extends AbstractTopology {
    private String topoName;
    private final String topic;
    private final String dir;
    /**
     * assigned after createTopology() is called
     */
    private FilerBolt filer;

    public KafkaFilerTopology(File file, String topic, String dir) {
        super(file);
        this.topic = topic;
        this.dir = dir;
        this.topoName = String.format("%s_%s_%s_%d", topologyName, topic, dir, System.currentTimeMillis());
    }

    public static void main(String[] args) throws Exception {
        // process command line
        String topic = (args != null && args.length > 1) ? args[1] : "kilda.speaker";
        String dir = (args != null && args.length > 2) ? args[2] : "";

        Config conf = new Config();
        conf.setDebug(false);
        conf.setNumWorkers(1);
        File file = new File(KafkaFilerTopology.class.getResource(Topology.TOPOLOGY_PROPERTIES).getFile());
        KafkaFilerTopology kafkaFilerTopology = new KafkaFilerTopology(file, topic, dir);
        StormSubmitter.submitTopology(kafkaFilerTopology.topoName, conf, kafkaFilerTopology.createTopology());
    }

    @Override
    public StormTopology createTopology() {
        TopologyBuilder builder = new TopologyBuilder();

        String spoutId = "KafkaSpout-" + topic;
        int parallelism = 1;

        builder.setSpout(spoutId, createKafkaSpout(topic, topoName), parallelism);
        filer = new FilerBolt().withFileName("utils-" + topic + ".log");
        if (dir != null && dir.length() > 0)
            filer.withDir(new File(dir));

        builder.setBolt("utils", filer, parallelism)
                .shuffleGrouping(spoutId);
        return builder.createTopology();
    }

    public FilerBolt getFiler() {
        return filer;
    }
}
