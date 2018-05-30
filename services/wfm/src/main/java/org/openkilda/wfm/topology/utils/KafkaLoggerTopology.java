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

import org.openkilda.wfm.error.ConfigurationException;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.LaunchEnvironment;

import org.slf4j.event.Level;
import org.apache.storm.Config;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.topology.TopologyBuilder;

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
    public KafkaLoggerTopology(LaunchEnvironment env) throws ConfigurationException {
        super(env);
    }

    @Override
    public StormTopology createTopology() {
        final String topic = config.getKafkaSpeakerTopic();
        final String name = String.format("%s_%s_%d", getTopologyName(), topic, System.currentTimeMillis());
        final Integer parallelism = config.getParallelism();

        TopologyBuilder builder = new TopologyBuilder();

        String spoutId = "KafkaSpout-" + topic;
        builder.setSpout(spoutId, createKafkaSpout(topic, name), parallelism);
        LoggerBolt logger = new LoggerBolt()
                .withLevel(config.getLoggerLevel())
                .withWatermark(config.getLoggerWatermark());

        builder.setBolt("Logger", logger, parallelism)
                .shuffleGrouping(spoutId);

        return builder.createTopology();
    }

    @Override
    protected Config makeStormConfig() {
        Config config = super.makeStormConfig();
        Level level = this.config.getLoggerLevel();

        config.setDebug(level == Level.DEBUG || level == Level.TRACE);

        return config;
    }

    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new KafkaLoggerTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
