package org.bitbucket.openkilda.wfm.topology;

import static org.codehaus.plexus.util.PropertyUtils.loadProperties;

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
import org.apache.storm.tuple.Fields;

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
     * Default zookeeper hosts.
     */
    private static final String DEFAULT_ZOOKEEPER = "zookeeper.pendev:2181";

    /**
     * Default kafka hosts.
     */
    private static final String DEFAULT_KAFKA = "kafka.pendev:9092";

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
     * Instance constructor.
     * Loads topology specific properties from common configuration file.
     * It uses topology name as properties name prefix.
     */
    protected AbstractTopology() {
        Properties properties = loadProperties(getClass().getClassLoader().getResourceAsStream(TOPOLOGY_PROPERTIES));

        topologyName = getTopologyName();
        zookeeperHosts = properties.getProperty(PROPERTY_ZOOKEEPER, DEFAULT_ZOOKEEPER);
        kafkaHosts = properties.getProperty(PROPERTY_KAFKA, DEFAULT_KAFKA);

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
    protected org.apache.storm.kafka.KafkaSpout createKafkaSpout(String topic) {
        String spoutID = topic + "_" + System.currentTimeMillis();
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
}
