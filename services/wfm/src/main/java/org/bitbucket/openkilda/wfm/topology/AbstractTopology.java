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

package org.bitbucket.openkilda.wfm.topology;

import org.bitbucket.openkilda.messaging.Topic;
import org.bitbucket.openkilda.wfm.topology.utils.HealthCheckBolt;

import kafka.admin.AdminUtils;
import kafka.api.OffsetRequest;
import kafka.utils.ZKStringSerializer$;
import kafka.utils.ZkUtils;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.ZkConnection;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.storm.kafka.SpoutConfig;
import org.apache.storm.kafka.StringScheme;
import org.apache.storm.kafka.ZkHosts;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.kafka.bolt.selector.DefaultTopicSelector;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.spout.SchemeAsMultiScheme;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;
import org.codehaus.plexus.util.PropertyUtils;

import java.io.File;
import java.util.Properties;

/**
 * Represents abstract topology.
 */
public abstract class AbstractTopology implements Topology {
    /**
     * Message key.
     */
    public static final String MESSAGE_FIELD = "message";

    /**
     * Message field.
     */
    public static final Fields fieldMessage = new Fields(MESSAGE_FIELD);

    /**
     * Default zookeeper session timeout.
     */
    private static final int ZOOKEEPER_SESSION_TIMEOUT_MS = 5 * 1000;

    /**
     * Default zookeeper connection timeout.
     */
    private static final int ZOOKEEPER_CONNECTION_TIMEOUT_MS = 5 * 1000;

    /**
     * Default parallelism value.
     */
    private static final String DEFAULT_PARALLELISM = "1";

    /**
     * Default workers value.
     */
    private static final String DEFAULT_WORKERS = "1";

    /**
     * Kafka properties.
     */
    protected final Properties kafkaProperties = new Properties();

    /**
     * Topology properties.
     */
    protected final Properties topologyProperties = new Properties();

    /**
     * Zookeeper hosts.
     */
    protected final String zookeeperHosts;

    /**
     * Kafka hosts.
     */
    protected final String kafkaHosts;

    /**
     * Topology name.
     */
    protected final String topologyName;

    /**
     * Parallelism value.
     */
    protected final int parallelism;

    /**
     * Workers value.
     */
    protected final int workers;

    /**
     * Instance constructor. Loads topology specific properties from common configuration file. It uses topology name as
     * properties name prefix.
     */
    protected AbstractTopology(File file) {
        Properties properties = PropertyUtils.loadProperties(file);

        topologyName = getTopologyName();
        zookeeperHosts = properties.getProperty(PROPERTY_ZOOKEEPER);
        kafkaHosts = properties.getProperty(PROPERTY_KAFKA);

        // TODO: proper parallelism/workers configuration
        parallelism = Integer.parseInt(properties.getProperty(getTopologyPropertyName(PROPERTY_PARALLELISM), DEFAULT_PARALLELISM));
        workers = Integer.parseInt(properties.getProperty(getTopologyPropertyName(PROPERTY_WORKERS), DEFAULT_WORKERS));

        kafkaProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        kafkaProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        kafkaProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaHosts);
        kafkaProperties.put(ConsumerConfig.GROUP_ID_CONFIG, getTopologyName());
        kafkaProperties.put("request.required.acks", "1");

        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key);
            if (key.startsWith(topologyName)) {
                topologyProperties.put(key, value);
            }
        }
    }

    /**
     * Creates Kafka topic if it does not exist.
     *
     * @param topic Kafka topic
     */
    protected void checkAndCreateTopic(final String topic) {
        ZkClient zkClient = new ZkClient(zookeeperHosts, ZOOKEEPER_SESSION_TIMEOUT_MS,
                ZOOKEEPER_CONNECTION_TIMEOUT_MS, ZKStringSerializer$.MODULE$);
        ZkUtils zkUtils = new ZkUtils(zkClient, new ZkConnection(zookeeperHosts), false);
        if (!AdminUtils.topicExists(zkUtils, topic)) {
            AdminUtils.createTopic(zkUtils, topic, 1, 1,
                    AdminUtils.createTopic$default$5(), AdminUtils.createTopic$default$6());
        }
    }

    /**
     * Creates Kafka spout.
     *
     * @param topic Kafka topic
     * @return {@link KafkaSpout}
     */
    protected org.apache.storm.kafka.KafkaSpout createKafkaSpout(String topic, String spoutId) {
        String spoutID = topic + "." + spoutId;
        String zkRoot = "/" + topic; // used to store offset information.
        ZkHosts hosts = new ZkHosts(zookeeperHosts);
        SpoutConfig cfg = new SpoutConfig(hosts, topic, zkRoot, spoutID);
        cfg.startOffsetTime = OffsetRequest.EarliestTime();
        cfg.scheme = new SchemeAsMultiScheme(new StringScheme());
        cfg.bufferSizeBytes = 1024 * 1024 * 4;
        cfg.fetchSizeBytes = 1024 * 1024 * 4;
        return new org.apache.storm.kafka.KafkaSpout(cfg);
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

    /**
     * Creates health-check handler spout and bolts.
     *
     * @param builder topology builder
     * @param prefix  component id
     */
    protected void createHealthCheckHandler(TopologyBuilder builder, String prefix) {
        checkAndCreateTopic(Topic.HEALTH_CHECK.getId());
        org.apache.storm.kafka.KafkaSpout healthCheckKafkaSpout = createKafkaSpout(Topic.HEALTH_CHECK.getId(), prefix);
        builder.setSpout(prefix + "HealthCheckKafkaSpout", healthCheckKafkaSpout, 1);
        HealthCheckBolt healthCheckBolt = new HealthCheckBolt(prefix);
        builder.setBolt(prefix + "HealthCheckBolt", healthCheckBolt, 1)
                .shuffleGrouping(prefix + "HealthCheckKafkaSpout");
        KafkaBolt healthCheckKafkaBolt = createKafkaBolt(Topic.HEALTH_CHECK.getId());
        builder.setBolt(prefix + "HealthCheckKafkaBolt", healthCheckKafkaBolt, 1)
                .shuffleGrouping(prefix + "HealthCheckBolt", Topic.HEALTH_CHECK.getId());
    }
}
