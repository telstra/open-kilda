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

import org.apache.logging.log4j.Level;
import org.apache.storm.Config;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.topology.TopologyBuilder;

import java.io.File;

/**
 * A topology that will listen to a kafka topic and log the messages at the configured level.
 * <p>
 * NB: This will only be able to log if the configured log level is below what is passed in.
 * Consequently, the default log level is INFO, which is the default configured setting.
 * In other words, the default loglevel is INFO. If KafkaLoggerTopology is configured with
 * DEBUG, then the messages won't appear.
 * <p>
 * The alternative is to adjust the loglevel of KafkaLoggerTopology to that of what is
 * configured, but that doesn't feel right. Let's see how this works in operations.
 * <p>
 * Example Call:
 * (1) storm jar target/WorkflowManager-1.0-SNAPSHOT-jar-with-dependencies.jar \
 * org.openkilda.wfm.KafkaLoggerTopology logger-5 kilda.speaker INFO fred
 * (2) ==> using a FQDN zookeeper <==
 * storm jar target/WorkflowManager-1.0-SNAPSHOT-jar-with-dependencies.jar \
 * org.openkilda.wfm.KafkaLoggerTopology logger-5 kilda.speaker INFO fred \
 * zookeeper.pendev:2181
 * (3) ==> using a localhost zookeeper <==
 * storm jar target/WorkflowManager-1.0-SNAPSHOT-jar-with-dependencies.jar \
 * org.openkilda.wfm.KafkaLoggerTopology logger-5 kilda.speaker INFO fred localhost:2181
 */
public class KafkaLoggerTopology extends AbstractTopology {
    private String topoName;
    private String topic;
    private Level level;
    private String watermark;

    // assigned after createTopology() is called
    public static LoggerBolt logger;

    public KafkaLoggerTopology(File file, String topic, Level level, String watermark) {
        super(file);
        this.topic = topic;
        this.level = level;
        this.watermark = watermark;
        this.topoName = String.format("%s_%s_%d", topologyName, topic, System.currentTimeMillis());
    }

    public static void main(String[] args) throws Exception {
        // process command line
        String topic = (args != null && args.length > 1) ? args[1] : "kilda.speaker";
        Level level = (args != null && args.length > 2) ? Level.valueOf(args[2]) : Level.INFO;
        String watermark = (args != null && args.length > 3) ? args[3] : "";
        boolean debug = (level == Level.DEBUG || level == Level.TRACE || level == Level.ALL);

        Config conf = new Config();
        conf.setDebug(debug);
        conf.setNumWorkers(1);
        File file = new File(KafkaLoggerTopology.class.getResource(Topology.TOPOLOGY_PROPERTIES).getFile());
        KafkaLoggerTopology kafkaLoggerTopology = new KafkaLoggerTopology(file, topic, level, watermark);
        StormSubmitter.submitTopology(kafkaLoggerTopology.topoName, conf, kafkaLoggerTopology.createTopology());
    }

    @Override
    public StormTopology createTopology() {
        TopologyBuilder builder = new TopologyBuilder();

        String spoutId = "KafkaSpout-" + topic;
        int parallelism = 1;

        builder.setSpout(spoutId, createKafkaSpout(topic, topoName), parallelism);
        logger = new LoggerBolt().withLevel(level).withWatermark(watermark);

        builder.setBolt("Logger", logger, parallelism)
                .shuffleGrouping(spoutId);

        return builder.createTopology();
    }
}
