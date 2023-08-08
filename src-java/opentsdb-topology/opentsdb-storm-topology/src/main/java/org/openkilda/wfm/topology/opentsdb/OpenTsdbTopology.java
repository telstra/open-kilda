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

package org.openkilda.wfm.topology.opentsdb;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.kafka.InfoDataDeserializer;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.share.zk.ZooKeeperSpout;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.opentsdb.OpenTsdbTopologyConfig.OpenTsdbConfig;
import org.openkilda.wfm.topology.opentsdb.bolts.DatapointParseBolt;
import org.openkilda.wfm.topology.opentsdb.bolts.OpenTsdbFilterBolt;
import org.openkilda.wfm.topology.utils.InfoDataTranslator;

import com.google.common.annotations.VisibleForTesting;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.kafka.spout.KafkaSpoutConfig;
import org.apache.storm.kafka.spout.KafkaSpoutConfig.FirstPollOffsetStrategy;
import org.apache.storm.opentsdb.bolt.OpenTsdbBolt;
import org.apache.storm.opentsdb.client.OpenTsdbClient;
import org.apache.storm.topology.BoltDeclarer;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Apache Storm topology for sending metrics into Open TSDB.
 */
public class OpenTsdbTopology extends AbstractTopology<OpenTsdbTopologyConfig> {
    private final Map<String, String> targets;

    public OpenTsdbTopology(LaunchEnvironment env) {
        super(env, "opentsdb-topology", OpenTsdbTopologyConfig.class);

        targets = collectTargets(String.format("%s.target.", OpenTsdbTopologyConfig.PREFIX), env.getProperties());
    }

    @VisibleForTesting
    static final String OTSDB_SPOUT_ID = "input";
    private static final String OTSDB_FILTER_BOLT_ID = OpenTsdbFilterBolt.class.getSimpleName();
    @VisibleForTesting
    static final String OTSDB_PARSE_BOLT_ID = DatapointParseBolt.class.getSimpleName();

    @Override
    public StormTopology createTopology() {
        logger.info("Creating OpenTsdbTopology - {}", topologyName);

        TopologyBuilder tb = new TopologyBuilder();

        ZooKeeperSpout zooKeeperSpout = new ZooKeeperSpout(getConfig().getBlueGreenMode(), getZkTopoName(),
                getZookeeperConfig());
        declareSpout(tb, zooKeeperSpout, ZooKeeperSpout.SPOUT_ID);

        if (targets.isEmpty()) {
            throw new IllegalStateException("There is no any OpenTSDB endpoint(target) defined into topology config");
        }

        int boltsToEnableCount = 0;
        List<String> boltsToEnable = new ArrayList<>();
        for (Map.Entry<String, String> entry : targets.entrySet()) {
            logger.info("Creating metric channel for target {} with URL {}", entry.getKey(), entry.getValue());
            String boltId = createDeliveryChannel(tb, entry.getKey(), entry.getValue());
            boltsToEnableCount += getBoltInstancesCount(boltId);
            boltsToEnable.add(boltId);
        }

        ZooKeeperBolt zooKeeperBolt = new ZooKeeperBolt(getConfig().getBlueGreenMode(), getZkTopoName(),
                getZookeeperConfig(), boltsToEnableCount);
        BoltDeclarer zkBoltDeclarer = declareBolt(tb, zooKeeperBolt, ZooKeeperBolt.BOLT_ID);
        for (String boltId : boltsToEnable) {
            zkBoltDeclarer.allGrouping(boltId, ZkStreams.ZK.toString());
        }

        return tb.createTopology();
    }

    private String createDeliveryChannel(TopologyBuilder topology, String targetName, String otsdbUrl) {
        String spoutId = addNameToId(OTSDB_SPOUT_ID, targetName);
        attachInput(topology, spoutId);

        String parseBoltId = addNameToId(OTSDB_PARSE_BOLT_ID, targetName);
        declareBolt(topology, new DatapointParseBolt(), parseBoltId)
                .shuffleGrouping(spoutId)
                .allGrouping(ZooKeeperSpout.SPOUT_ID);

        String filterBoltId = addNameToId(OTSDB_FILTER_BOLT_ID, targetName);
        declareBolt(topology, new OpenTsdbFilterBolt(), filterBoltId)
                .fieldsGrouping(parseBoltId, new Fields("hash"));

        attachOutput(topology, filterBoltId, targetName, otsdbUrl);

        return parseBoltId;
    }

    private void attachInput(TopologyBuilder topology, String spoutId) {
        String topic = topologyConfig.getKafkaTopics().getOtsdbTopic();

        //FIXME: We have to use the Message class for messaging (but current setup saves some space/traffic in stream).
        KafkaSpoutConfig<String, InfoData> config = makeKafkaSpoutConfig(
                Collections.singletonList(topic), spoutId, InfoDataDeserializer.class)
                .setRecordTranslator(new InfoDataTranslator())
                .setFirstPollOffsetStrategy(FirstPollOffsetStrategy.UNCOMMITTED_EARLIEST)
                .setTupleTrackingEnforced(true)
                .build();

        KafkaSpout<String, InfoData> kafkaSpout = new KafkaSpout<>(config);
        declareSpout(topology, kafkaSpout, spoutId);
    }

    private void attachOutput(TopologyBuilder topology, String sourceBoltId, String targetName, String otsdbUrl) {
        OpenTsdbConfig config = topologyConfig.getOpenTsdbConfig();

        OpenTsdbClient.Builder tsdbBuilder = OpenTsdbClient
                .newBuilder(otsdbUrl)
                .returnDetails();
        if (config.getClientChunkedRequestsEnabled()) {
            tsdbBuilder.enableChunkedEncoding();
        }

        OpenTsdbBolt openTsdbBolt = new OpenTsdbBolt(tsdbBuilder,
                Collections.singletonList(new StatsDatapointTupleMapper(OpenTsdbFilterBolt.FIELD_ID_DATAPOINT)));
        openTsdbBolt.withBatchSize(config.getBatchSize()).withFlushInterval(config.getFlushInterval());

        declareBolt(topology, openTsdbBolt, addNameToId("output", targetName))
                .shuffleGrouping(sourceBoltId);
    }

    @Override
    protected String getZkTopoName() {
        return "opentsdb";
    }

    private Map<String, String> collectTargets(String prefix, Properties rawTopologyConfig) {
        Map<String, String> targets = new HashMap<>();
        for (Map.Entry<Object, Object> entry : rawTopologyConfig.entrySet()) {
            String property = String.valueOf(entry.getKey());
            if (!property.startsWith(prefix)) {
                continue;
            }

            String name = property.substring(prefix.length());
            if (name.isEmpty()) {
                throw new IllegalArgumentException(String.format(
                        "The target's name is an empty string (property: \"%s\", prefix: \"%s\")", property, prefix));
            }

            String value = String.valueOf(entry.getValue());
            if (isValidUrl(value)) {
                targets.put(name, value);
            } else {
                logger.error("The target's URL is invalid (property: \"{}\", url: \"{}\")", property, value);
            }
        }
        return targets;
    }

    @VisibleForTesting
    protected static boolean isValidUrl(String url) {
        try {
            URL u = new URL(url);
            return u.getHost() != null && !u.getHost().isEmpty() && u.getPort() != -1;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    public static String addNameToId(String entityId, String name) {
        // TODO(surabujin): perhaps we need to limit the set of allowed characters in "name" or add some escaping
        return entityId + '.' + name;
    }

    /**
     * main.
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new OpenTsdbTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
