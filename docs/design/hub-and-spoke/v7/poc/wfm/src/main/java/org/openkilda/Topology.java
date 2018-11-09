package org.openkilda;


import static org.openkilda.Constants.BOLT_COORDINATOR;
import static org.openkilda.Constants.BOLT_HUB;
import static org.openkilda.Constants.BOLT_TO_FL;
import static org.openkilda.Constants.BOLT_TO_NB;
import static org.openkilda.Constants.BOLT_WORKER;
import static org.openkilda.Constants.KAFKA_SERVER;
import static org.openkilda.Constants.SPOUT_COORDINATOR;
import static org.openkilda.Constants.SPOUT_HUB;
import static org.openkilda.Constants.SPOUT_WORKER;
import static org.openkilda.Constants.STREAM_HUB_BOLT_TO_NB;
import static org.openkilda.Constants.STREAM_HUB_BOLT_TO_WORKER_BOLT;
import static org.openkilda.Constants.STREAM_TO_BOLT_COORDINATOR;
import static org.openkilda.Constants.STREAM_WORKER_BOLT_TO_FL;
import static org.openkilda.Constants.STREAM_WORKER_BOLT_TO_HUB_BOLT;
import static org.openkilda.Constants.TOPIC_FL_TO_WORKER;
import static org.openkilda.Constants.TOPIC_HUB_TO_NB;
import static org.openkilda.Constants.TOPIC_NB_TO_HUB;
import static org.openkilda.Constants.TOPIC_WORKER_TO_FL;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.kafka.bolt.selector.DefaultTopicSelector;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.kafka.spout.KafkaSpoutConfig;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;
import org.apache.storm.utils.Utils;

import java.util.Properties;


public class Topology {

    public static void main(String[] args) throws Exception {
        TopologyBuilder builder = new TopologyBuilder();

        builder.setSpout(SPOUT_COORDINATOR, new CoordinatorSpout());
        builder.setBolt(BOLT_COORDINATOR, new CoordinatorBolt(), 10).
                allGrouping(SPOUT_COORDINATOR).
                fieldsGrouping(BOLT_WORKER, STREAM_TO_BOLT_COORDINATOR, new Fields("key")).
                fieldsGrouping(BOLT_HUB, STREAM_TO_BOLT_COORDINATOR, new Fields("key"));

        builder.setSpout(SPOUT_HUB, createKafkaSpout(SPOUT_HUB, TOPIC_NB_TO_HUB));
        builder.setBolt(BOLT_HUB, new HubBolt(), 10)
                .directGrouping(BOLT_WORKER, STREAM_WORKER_BOLT_TO_HUB_BOLT)
                .fieldsGrouping(SPOUT_HUB, new Fields("key"))
                .directGrouping(BOLT_COORDINATOR);

        builder.setSpout(SPOUT_WORKER, createKafkaSpout(SPOUT_WORKER, TOPIC_FL_TO_WORKER));
        builder.setBolt(BOLT_WORKER, new WorkerBolt(), 20).
                fieldsGrouping(BOLT_HUB, STREAM_HUB_BOLT_TO_WORKER_BOLT, new Fields("key")).
                fieldsGrouping(SPOUT_WORKER, new Fields("key")).
                directGrouping(BOLT_COORDINATOR);

        builder.setBolt(BOLT_TO_FL, createKafkaBolt(TOPIC_WORKER_TO_FL), 1).
                shuffleGrouping(BOLT_WORKER, STREAM_WORKER_BOLT_TO_FL);
        builder.setBolt(BOLT_TO_NB, createKafkaBolt(TOPIC_HUB_TO_NB), 1).
                shuffleGrouping(BOLT_HUB, STREAM_HUB_BOLT_TO_NB);

        Config conf = new Config();
        conf.setDebug(false);


        if (args != null && args.length > 0) {
            conf.setNumWorkers(1);
            StormSubmitter.submitTopologyWithProgressBar(args[0], conf, builder.createTopology());
        } else {
            LocalCluster cluster = new LocalCluster();
            StormTopology topology = builder.createTopology();
            cluster.submitTopology("test", conf, topology);
            Utils.sleep(600000);
            cluster.killTopology("test");
            cluster.shutdown();
        }
    }

    private static KafkaSpout<String, String> createKafkaSpout(String groupId, String... topics) {
        return new KafkaSpout<>(KafkaSpoutConfig.builder(KAFKA_SERVER, topics)
                .setGroupId(groupId)
                .build());
    }

    private static KafkaBolt createKafkaBolt(final String topic) {
        return new KafkaBolt<String, String>()
                .withProducerProperties(getKafkaProducerProperties())
                .withTopicSelector(new DefaultTopicSelector(topic))
                .withTupleToKafkaMapper(new StormToKafkaTranslator());
    }

    private static Properties getKafkaProducerProperties() {
        Properties kafka = new Properties();

        kafka.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringSerializer");
        kafka.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringSerializer");
        kafka.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_SERVER);
        kafka.setProperty("request.required.acks", "1");

        return kafka;
    }
}