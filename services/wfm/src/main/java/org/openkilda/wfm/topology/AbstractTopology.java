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

import org.openkilda.messaging.Topic;
import org.openkilda.wfm.CtrlBoltRef;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.error.ConfigurationException;
import org.openkilda.wfm.error.NameCollisionException;
import org.openkilda.wfm.PropertiesReader;
import org.openkilda.wfm.error.StreamNameCollisionException;
import org.openkilda.wfm.ctrl.RouteBolt;
import org.openkilda.wfm.kafka.CustomNamedSubscription;
import org.openkilda.wfm.topology.utils.HealthCheckBolt;
import org.openkilda.wfm.topology.utils.KafkaRecordTranslator;

import org.apache.kafka.clients.consumer.ConsumerConfig;
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
import java.util.Properties;

/**
 * Represents abstract topology.
 */
public abstract class AbstractTopology implements Topology {
    private static final Logger logger = LoggerFactory.getLogger(AbstractTopology.class);

    protected final LaunchEnvironment env;
    protected final PropertiesReader propertiesReader;
    protected TopologyConfig config;
    protected final String topologyName;
    private final Properties kafkaProperties;

    public static final String SPOUT_ID_CTRL = "ctrl.in";
    public static final String BOLT_ID_CTRL_ROUTE = "ctrl.route";
    public static final String BOLT_ID_CTRL_OUTPUT = "ctrl.out";

    public static final String MESSAGE_FIELD = "message";
    public static final Fields fieldMessage = new Fields(MESSAGE_FIELD);

    protected AbstractTopology(LaunchEnvironment env) throws ConfigurationException {
        this.env = env;

        String builtinName = makeTopologyName();
        String name = env.getTopologyName();
        if (name == null) {
            name = builtinName;
        }
        topologyName = name;

        propertiesReader = env.makePropertiesReader(name, builtinName);
        config = new TopologyConfig(propertiesReader);
        kafkaProperties = makeKafkaProperties();
    }

    protected void setup() throws TException, NameCollisionException {
        if (config.getLocal()) {
            setupLocal();
        } else {
            setupRemote();
        }
    }

    private void setupRemote() throws TException, NameCollisionException {
        Config config = makeStormConfig();
        config.setDebug(false);

        logger.info("Submit Topology: {}", getTopologyName());
        StormSubmitter.submitTopology(getTopologyName(), config, createTopology());
    }

    private void setupLocal() throws NameCollisionException {
        Config config = makeStormConfig();
        config.setDebug(true);

        LocalCluster cluster = new LocalCluster();
        cluster.submitTopology(getTopologyName(), config, createTopology());

        logger.info("Start Topology: {} (local)", getTopologyName());
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

    private Properties makeKafkaProperties() {
        Properties kafka = new Properties();

        kafka.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        kafka.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        kafka.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getKafkaHosts());
        kafka.setProperty(ConsumerConfig.GROUP_ID_CONFIG, getTopologyName());
        kafka.setProperty("request.required.acks", "1");

        return kafka;
    }

    protected Config makeStormConfig() {
        Config stormConfig = new Config();

        stormConfig.setNumWorkers(config.getWorkers(topologyName));
        if (config.getLocal()) {
            stormConfig.setMaxTaskParallelism(config.getParallelism());
        }

        return stormConfig;
    }

    protected void localExecutionMainLoop() {
        logger.info("Sleep while local topology is executing");
        try {
            Thread.sleep(config.getLocalExecutionTime());
        } catch (InterruptedException e) {
            logger.warn("Execution process have been interrupted.");
        }
    }

    public String getTopologyName() {
        return topologyName;
    }

    public TopologyConfig getConfig() {
        return config;
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
                .withProducerProperties(kafkaProperties)
                .withTopicSelector(new DefaultTopicSelector(topic))
                .withTupleToKafkaMapper(new FieldNameBasedTupleToKafkaMapper<>());
    }

    protected void createCtrlBranch(TopologyBuilder builder, List<CtrlBoltRef> targets)
            throws StreamNameCollisionException {
        checkAndCreateTopic(config.getKafkaCtrlTopic());

        KafkaSpout kafkaSpout;
        kafkaSpout = createKafkaSpout(config.getKafkaCtrlTopic(), SPOUT_ID_CTRL);
        builder.setSpout(SPOUT_ID_CTRL, kafkaSpout);

        RouteBolt route = new RouteBolt(getTopologyName());
        builder.setBolt(BOLT_ID_CTRL_ROUTE, route)
                .shuffleGrouping(SPOUT_ID_CTRL);

        KafkaBolt kafkaBolt = createKafkaBolt(config.getKafkaCtrlTopic());
        BoltDeclarer outputSetup = builder.setBolt(BOLT_ID_CTRL_OUTPUT, kafkaBolt)
                .shuffleGrouping(BOLT_ID_CTRL_ROUTE, route.STREAM_ID_ERROR);

        for (CtrlBoltRef ref : targets) {
            String boltId = ref.getBoltId();
            ref.getDeclarer().allGrouping(BOLT_ID_CTRL_ROUTE, route.registerEndpoint(boltId));
            outputSetup.shuffleGrouping(boltId, ref.getBolt().getCtrlStreamId());
        }
    }

    /**
     * Creates health-check handler spout and bolts.
     *
     * @param builder topology builder
     * @param prefix  component id
     */
    protected void createHealthCheckHandler(TopologyBuilder builder, String prefix) {
        checkAndCreateTopic(Topic.HEALTH_CHECK);
        KafkaSpout healthCheckKafkaSpout = createKafkaSpout(Topic.HEALTH_CHECK, prefix);
        builder.setSpout(prefix + "HealthCheckKafkaSpout", healthCheckKafkaSpout, 1);
        HealthCheckBolt healthCheckBolt = new HealthCheckBolt(prefix);
        builder.setBolt(prefix + "HealthCheckBolt", healthCheckBolt, 1)
                .shuffleGrouping(prefix + "HealthCheckKafkaSpout");
        KafkaBolt healthCheckKafkaBolt = createKafkaBolt(Topic.HEALTH_CHECK);
        builder.setBolt(prefix + "HealthCheckKafkaBolt", healthCheckKafkaBolt, 1)
                .shuffleGrouping(prefix + "HealthCheckBolt", Topic.HEALTH_CHECK);
    }

    protected KafkaSpoutConfig.Builder<String, String> makeKafkaSpoutConfigBuilder(String spoutId, String topic) {
        return new KafkaSpoutConfig.Builder<>(
                config.getKafkaHosts(), StringDeserializer.class, StringDeserializer.class,
                new CustomNamedSubscription(topic))

                .setGroupId(makeKafkaGroupName(spoutId))
                .setRecordTranslator(new KafkaRecordTranslator<>())

                // NB: There is an issue with using the default of "earliest uncommitted message" -
                //      if we erase the topics, then the committed will be > the latest .. and so
                //      we won't process any messages.
                // NOW: we'll miss any messages generated while the topology is down.
                .setFirstPollOffsetStrategy(KafkaSpoutConfig.FirstPollOffsetStrategy.LATEST);
    }

    protected String makeKafkaGroupName(String spoutId) {
        return String.format("%s__%s", getTopologyName(), spoutId);
    }
}
