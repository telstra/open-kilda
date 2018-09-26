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

package org.openkilda.wfm.topology;

import static java.lang.String.format;

import org.openkilda.config.KafkaConfig;
import org.openkilda.config.naming.KafkaNamingStrategy;
import org.openkilda.wfm.CtrlBoltRef;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.config.naming.TopologyNamingStrategy;
import org.openkilda.wfm.config.provider.ConfigurationProvider;
import org.openkilda.wfm.ctrl.RouteBolt;
import org.openkilda.wfm.error.ConfigurationException;
import org.openkilda.wfm.error.NameCollisionException;
import org.openkilda.wfm.error.StreamNameCollisionException;
import org.openkilda.wfm.kafka.CustomNamedSubscription;
import org.openkilda.wfm.topology.utils.KafkaRecordTranslator;

import com.google.common.annotations.VisibleForTesting;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.kafka.bolt.selector.DefaultTopicSelector;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.kafka.spout.KafkaSpoutConfig;
import org.apache.storm.thrift.TException;
import org.apache.storm.topology.BoltDeclarer;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;
import org.kohsuke.args4j.CmdLineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Represents abstract topology.
 */
public abstract class AbstractTopology<T extends AbstractTopologyConfig> implements Topology {
    private static final Logger logger = LoggerFactory.getLogger(AbstractTopology.class);

    public static final String SPOUT_ID_CTRL = "ctrl.in";
    public static final String BOLT_ID_CTRL_ROUTE = "ctrl.route";
    public static final String BOLT_ID_CTRL_OUTPUT = "ctrl.out";

    public static final String MESSAGE_FIELD = "message";
    public static final Fields fieldMessage = new Fields(MESSAGE_FIELD);

    protected final String topologyName;

    protected final KafkaNamingStrategy kafkaNamingStrategy;
    protected final TopologyNamingStrategy topoNamingStrategy;
    protected final ConfigurationProvider configurationProvider;

    protected final T topologyConfig;
    private final KafkaConfig kafkaConfig;

    protected AbstractTopology(LaunchEnvironment env, Class<T> topologyConfigClass) {
        kafkaNamingStrategy = env.getKafkaNamingStrategy();
        topoNamingStrategy = env.getTopologyNamingStrategy();

        String defaultTopologyName = getDefaultTopologyName();
        // Use the default topology name with naming strategy applied only if no specific name provided via CLI.
        topologyName = Optional.ofNullable(env.getTopologyName())
                .orElse(topoNamingStrategy.stormTopologyName(defaultTopologyName));

        configurationProvider = env.getConfigurationProvider(topologyName,
                TOPOLOGY_PROPERTIES_DEFAULTS_PREFIX + defaultTopologyName);

        topologyConfig = configurationProvider.getConfiguration(topologyConfigClass);
        kafkaConfig = configurationProvider.getConfiguration(KafkaConfig.class);

        logger.debug("Topology built {}: kafka={}, parallelism={}, workers={}",
                topologyName, kafkaConfig.getHosts(), topologyConfig.getParallelism(),
                topologyConfig.getWorkers());
    }

    protected String getDefaultTopologyName() {
        return getClass().getSimpleName().toLowerCase();
    }

    protected void setup() throws TException, NameCollisionException {
        if (topologyConfig.getUseLocalCluster()) {
            setupLocal();
        } else {
            setupRemote();
        }
    }

    private void setupRemote() throws TException, NameCollisionException {
        Config config = makeStormConfig();
        config.setDebug(false);

        logger.info("Submit Topology: {}", topologyName);
        StormSubmitter.submitTopology(topologyName, config, createTopology());
    }

    private void setupLocal() throws NameCollisionException {
        Config config = makeStormConfig();
        config.setDebug(true);

        LocalCluster cluster = new LocalCluster();
        cluster.submitTopology(topologyName, config, createTopology());

        logger.info("Start Topology: {} (local)", topologyName);
        localExecutionMainLoop();

        cluster.shutdown();
    }

    protected static int handleLaunchException(Exception error) {
        int errorCode;

        try {
            throw error;
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            System.err.println();
            System.err.println("Allowed options and arguments:");
            e.getParser().printUsage(System.err);
            errorCode = 2;
        } catch (ConfigurationException e) {
            System.err.println(e.getMessage());
            errorCode = 3;
        } catch (TException e) {
            logger.error("Unable to complete topology setup: {}", e.getMessage());
            errorCode = 4;
        } catch (Exception e) {
            logger.error("Unhandled exception", e);
            errorCode = 1;
        }

        return errorCode;
    }

    private Properties getKafkaProducerProperties() {
        Properties kafka = new Properties();

        kafka.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringSerializer");
        kafka.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringSerializer");
        kafka.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.getHosts());
        kafka.setProperty("request.required.acks", "1");

        return kafka;
    }

    protected Config makeStormConfig() {
        Config stormConfig = new Config();

        stormConfig.setNumWorkers(topologyConfig.getWorkers());
        if (topologyConfig.getUseLocalCluster()) {
            stormConfig.setMaxTaskParallelism(topologyConfig.getParallelism());
        }

        return stormConfig;
    }

    protected void localExecutionMainLoop() {
        logger.info("Sleep while local topology is executing");
        try {
            Thread.sleep(topologyConfig.getLocalExecutionTime());
        } catch (InterruptedException e) {
            logger.warn("Execution process have been interrupted.");
        }
    }

    @Override
    public final String getTopologyName() {
        return topologyName;
    }

    @VisibleForTesting
    public final T getConfig() {
        return topologyConfig;
    }

    /**
     * Creates Kafka topic if it does not exist.
     *
     * @param topic Kafka topic
     */
    protected void checkAndCreateTopic(final String topic) {
        // FIXME(nmarchenko): do we need this? need check
    }

    /**
     * Creates Kafka spout.
     *
     * @param topic Kafka topic
     * @return {@link KafkaSpout}
     */
    protected KafkaSpout<String, String> createKafkaSpout(String topic, String spoutId) {
        KafkaSpoutConfig<String, String> config = makeKafkaSpoutConfigBuilder(spoutId, topic)
                .build();

        return new KafkaSpout<>(config);
    }

    /**
     * Creates Kafka bolt.
     *
     * @param topic Kafka topic
     * @return {@link KafkaBolt}
     */
    protected KafkaBolt createKafkaBolt(final String topic) {
        return new KafkaBolt<String, String>()
                .withProducerProperties(getKafkaProducerProperties())
                .withTopicSelector(new DefaultTopicSelector(topic))
                .withTupleToKafkaMapper(new FieldNameBasedTupleToKafkaMapper<>());
    }

    protected void createCtrlBranch(TopologyBuilder builder, List<CtrlBoltRef> targets)
            throws StreamNameCollisionException {
        String ctrlTopic = topologyConfig.getKafkaCtrlTopic();

        checkAndCreateTopic(ctrlTopic);

        KafkaSpout kafkaSpout;
        kafkaSpout = createKafkaSpout(ctrlTopic, SPOUT_ID_CTRL);
        builder.setSpout(SPOUT_ID_CTRL, kafkaSpout);

        RouteBolt route = new RouteBolt(topologyName);
        builder.setBolt(BOLT_ID_CTRL_ROUTE, route)
                .shuffleGrouping(SPOUT_ID_CTRL);

        KafkaBolt kafkaBolt = createKafkaBolt(ctrlTopic);
        BoltDeclarer outputSetup = builder.setBolt(BOLT_ID_CTRL_OUTPUT, kafkaBolt)
                .shuffleGrouping(BOLT_ID_CTRL_ROUTE, route.STREAM_ID_ERROR);

        for (CtrlBoltRef ref : targets) {
            String boltId = ref.getBoltId();
            ref.getDeclarer().allGrouping(BOLT_ID_CTRL_ROUTE, route.registerEndpoint(boltId));
            outputSetup.shuffleGrouping(boltId, ref.getBolt().getCtrlStreamId());
        }
    }

    protected KafkaSpoutConfig.Builder<String, String> makeKafkaSpoutConfigBuilder(String spoutId, String topic) {
        return new KafkaSpoutConfig.Builder<>(
                kafkaConfig.getHosts(), StringDeserializer.class, StringDeserializer.class,
                new CustomNamedSubscription(topic))

                .setGroupId(makeKafkaGroupName(spoutId))
                .setRecordTranslator(new KafkaRecordTranslator<>())

                // NB: There is an issue with using the default of "earliest uncommitted message" -
                //      if we erase the topics, then the committed will be > the latest .. and so
                //      we won't process any messages.
                // NOW: we'll miss any messages generated while the topology is down.
                .setFirstPollOffsetStrategy(KafkaSpoutConfig.FirstPollOffsetStrategy.LATEST);
    }

    private String makeKafkaGroupName(String spoutId) {
        return kafkaNamingStrategy.kafkaConsumerGroupName(format("%s__%s", topologyName, spoutId));
    }
}
