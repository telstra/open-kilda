package org.bitbucket.openkilda.wfm;

import kafka.api.OffsetRequest;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.storm.kafka.BrokerHosts;
import org.apache.storm.kafka.KafkaSpout;
import org.apache.storm.kafka.SpoutConfig;
import org.apache.storm.kafka.StringScheme;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.kafka.bolt.selector.DefaultTopicSelector;
import org.apache.storm.spout.SchemeAsMultiScheme;
import org.apache.storm.topology.TopologyBuilder;

import java.util.Properties;

/**
 */
public class OFEventSplitterTopology {

    String ofSplitterBoltID = "of.splitter.bolt";
    String topic = "kilda-speaker"; // + System.currentTimeMillis();
    String topoName = "OpenFlowEvents-Topology";

    TopologyBuilder builder;
    BrokerHosts hosts;

    public OFEventSplitterTopology(){
        this(new TopologyBuilder());
    }

    public OFEventSplitterTopology(TopologyBuilder builder){
        this.builder = builder;
    }

    public OFEventSplitterTopology setBrokerHosts(BrokerHosts hosts){
        this.hosts = hosts;
        return this;
    }

    /**
     * Build the Topology .. ie add it to the TopologyBuilder
     */
    public TopologyBuilder build(){
        builder.setSpout("kafka-spout", createKafkaSpout(topic));
        builder.setBolt(ofSplitterBoltID,
                new OFEventSplitterBolt(), 3)
                .shuffleGrouping("kafka-spout");

        /*
         * Setup the KafkaBolt, which will listen to the appropriate Stream from "SplitterBolt"
         */
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        primeKafkaTopic(topic+".INFO");
        KafkaBolt<String, String> bolt = new KafkaBolt<String, String>()
                .withProducerProperties(props)
                .withTopicSelector(new DefaultTopicSelector(topic+".INFO"))
                .withTupleToKafkaMapper(new FieldNameBasedTupleToKafkaMapper());
        builder.setBolt("forwardToKafka", bolt, 8)
                .shuffleGrouping(ofSplitterBoltID, OFEventSplitterBolt.INFO);
        builder.setBolt("echo_to_confused",
                new OFEventSplitterBolt(), 3)
                .shuffleGrouping(ofSplitterBoltID, OFEventSplitterBolt.OTHER);

        String infoSplitterBoltID = "info.splitter.bolt";
        builder.setBolt(infoSplitterBoltID, new InfoEventSplitterBolt(),3).shuffleGrouping
                (ofSplitterBoltID, OFEventSplitterBolt.INFO);


        // Create the output from the InfoSplitter to Kafka
        // TODO: Can convert part of this to a test .. see if the right messages land in right topic
        /*
         * Next steps
         *  1) ... validate the messages are showing up in the right place.
         *  2) ... hook up to kilda, with logging output, see if messages show up.
         */
        for (String stream : InfoEventSplitterBolt.outputStreams){
            KafkaBolt<String, String> splitter_bolt = new KafkaBolt<String, String>()
                    .withProducerProperties(props)
                    .withTopicSelector(new DefaultTopicSelector(stream))
                    .withTupleToKafkaMapper(new FieldNameBasedTupleToKafkaMapper());
            builder.setBolt(stream+"-KafkaWriter", splitter_bolt, 8)
                    .shuffleGrouping(infoSplitterBoltID, stream);

            // TODO: only set this up if the DEBUG flag is on.
            // NB: However, if we do that, it'll eliminate the ability to *dynamically* adjust log
            // Create Kafka Spout / Logger pairs   (facilitate debugging)
            primeKafkaTopic(stream);
            String spout = stream+"-KafkaSpout";
            builder.setSpout(spout,createKafkaSpout(stream));
            builder.setBolt(stream+"-KafkaReader", new LoggerBolt(), 3).shuffleGrouping(spout);
        }
        return builder;
    }

    KafkaProducer<String,String> kProducer = KafkaUtils.createStringsProducer();
    private void primeKafkaTopic(String topic){
        kProducer.send(new ProducerRecord<>(topic, "no_op", "{'type': 'NO_OP'}"));
    }


    private KafkaSpout createKafkaSpout(String topic){
        String spoutID = topic + "_" + System.currentTimeMillis();
        SpoutConfig kafkaSpoutConfig = new SpoutConfig (hosts, topic, "/" + topic, spoutID);
        kafkaSpoutConfig.bufferSizeBytes = 1024 * 1024 * 4;
        kafkaSpoutConfig.fetchSizeBytes = 1024 * 1024 * 4;
        kafkaSpoutConfig.startOffsetTime = OffsetRequest.EarliestTime();  // start later
        kafkaSpoutConfig.scheme = new SchemeAsMultiScheme(new StringScheme());
        return new KafkaSpout(kafkaSpoutConfig);
    }

    //Entry point for the topology
    public static void main(String[] args) throws Exception {



    }

}
