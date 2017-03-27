package org.bitbucket.openkilda.wfm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kafka.api.OffsetRequest;
import org.I0Itec.zkclient.ZkClient;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.storm.kafka.*;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.kafka.bolt.selector.DefaultTopicSelector;
import org.apache.storm.spout.SchemeAsMultiScheme;
import org.bitbucket.openkilda.wfm.TestUtils;

import org.apache.storm.Config;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.apache.storm.utils.Utils;
import org.junit.*;

import org.apache.storm.LocalCluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;

/**
 * Basic Splitter Tests.
 * <p>
 * The Splitter listens to a kafka queue and splits them into other queues.
 */
public class SimpleSplitterTest {

    private static Logger logger = LoggerFactory.getLogger(SimpleSplitterTest.class);

    public static final String topic = "kilda-speaker"; // + System.currentTimeMillis();
    public static final String topoName = "fred";

    private static TestUtils.KafkaTestFixture server;

    @BeforeClass
    public static void setupOnce() throws Exception {
        server = new TestUtils.KafkaTestFixture();
        server.start();
    }

    @AfterClass
    public static void teardownOnce() throws Exception {
        System.out.println("------> Killing Sheep \uD83D\uDC11\n");
        server.stop();
    }

    @Before
    public void setupEach() throws Exception {
    }

    @After
    public void teardownEach() throws Exception {
    }

    private Config stormConfig() {
        Config config = new Config();
        config.setDebug(true);
        config.setNumWorkers(3);
        return config;
    }

    private KafkaSpout createKafkaSpout(String topic, BrokerHosts hosts){
        String spoutID = topic + "_" + System.currentTimeMillis();
        SpoutConfig kafkaSpoutConfig = new SpoutConfig (hosts, topic, "/" + topic, spoutID);
        kafkaSpoutConfig.bufferSizeBytes = 1024 * 1024 * 4;
        kafkaSpoutConfig.fetchSizeBytes = 1024 * 1024 * 4;
        kafkaSpoutConfig.startOffsetTime = OffsetRequest.EarliestTime();  // start later
        kafkaSpoutConfig.scheme = new SchemeAsMultiScheme(new StringScheme());
        return new KafkaSpout(kafkaSpoutConfig);
    }


    @Test
    public void no_op() {

        String zkConnString = server.zk.getConnectString();
        BrokerHosts hosts = new ZkHosts(zkConnString);
        String ofSplitterBoltID = "of.splitter.bolt";

        TopologyBuilder builder = new TopologyBuilder();
        builder.setSpout("kafka-spout", createKafkaSpout(topic, hosts));
        builder.setBolt(ofSplitterBoltID,
                new OFSplitterBolt(), 3)
                .shuffleGrouping("kafka-spout");

        /*
         * Setup the KafkaBolt, which will listen to the appropriate Stream from "SplitterBolt"
         */
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        //props.put("acks", "1");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        primeKafkaTopic(topic+".INFO");
        KafkaBolt<String, String> bolt = new KafkaBolt<String, String>()
                .withProducerProperties(props)
                .withTopicSelector(new DefaultTopicSelector(topic+".INFO"))
                .withTupleToKafkaMapper(new FieldNameBasedTupleToKafkaMapper());
//        bolt.setFireAndForget(true);
//        bolt.setAsync(true);
        builder.setBolt("forwardToKafka", bolt, 8)
                .shuffleGrouping(ofSplitterBoltID, OFSplitterBolt.INFO);
        builder.setBolt("echo_to_confused",
                new OFSplitterBolt(), 3)
                .shuffleGrouping(ofSplitterBoltID, OFSplitterBolt.OTHER);

        String infoSplitterBoltID = "info.splitter.bolt";
        builder.setBolt(infoSplitterBoltID, new InfoSplitterBolt(),3).shuffleGrouping
                (ofSplitterBoltID, OFSplitterBolt.INFO);


        // Create the output from the InfoSplitter to Kafka
        // TODO: Can convert part of this to a test .. see if the right messages land in right topic
        /*
         * Next steps
         *  1) ... validate the messages are showing up in the right place.
         *  2) ... hook up to kilda, with logging output, see if messages show up.
         */
        for (String stream : InfoSplitterBolt.outputStreams){
            KafkaBolt<String, String> splitter_bolt = new KafkaBolt<String, String>()
                    .withProducerProperties(props)
                    .withTopicSelector(new DefaultTopicSelector(stream))
                    .withTupleToKafkaMapper(new FieldNameBasedTupleToKafkaMapper());
//            splitter_bolt.setAsync(true);
//            splitter_bolt.setFireAndForget(true);
            builder.setBolt(stream+"-KafkaWriter", splitter_bolt, 8)
                    .shuffleGrouping(infoSplitterBoltID, stream);

            // TODO: only set this up if the DEBUG flag is on.
            // NB: However, if we do that, it'll eliminate the ability to *dynamically* adjust log
            // Create Kafka Spout / Logger pairs   (facilitate debugging)
            primeKafkaTopic(stream);
            String spout = stream+"-KafkaSpout";
            builder.setSpout(spout,createKafkaSpout(stream,hosts));
            builder.setBolt(stream+"-KafkaReader", new LoggerBolt(), 3).shuffleGrouping(spout);
        }


        LocalCluster cluster = new LocalCluster();
        cluster.submitTopology(SimpleSplitterTest.topoName, stormConfig(), builder.createTopology());

        // Send data into kafka ..
        SendMessages();

        Utils.sleep(240 * 1000);
        Assert.assertTrue(true);
        //cluster.killTopology(SimpleSplitterTest.topoName);

    }


    public static class LoggerBolt extends BaseRichBolt {
        private OutputCollector _collector;

        private static Logger logger = LoggerFactory.getLogger(LoggerBolt.class);

        @Override
        public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
            _collector = collector;
        }

        @Override
        public void execute(Tuple tuple) {
            logger.debug("\nfields: {}\nvalues: {}", tuple.getFields(), tuple.getValues());
            _collector.ack(tuple);
        }

        @Override
        public void declareOutputFields(OutputFieldsDeclarer declarer) {}
    }




    /**
     * OFSplitterBolt - split the OpenFlow messages (ie INFO / COMMAND)
     */
    public static class OFSplitterBolt extends BaseRichBolt {
        OutputCollector _collector;

        public static final String INFO = "INFO";
        public static final String COMMAND = "COMMAND";
        public static final String OTHER = "OTHER";

        @Override
        public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
            _collector = collector;
        }

        @Override
        public void execute(Tuple tuple) {
            String json = tuple.getString(0);
            ObjectMapper mapper = new ObjectMapper();
            Map<String,?> root;
            try {
                root = mapper.readValue(json, Map.class);
                String type = (String) root.get("type");
                Map<String,?> data = (Map<String,?>) root.get("data");
                // TODO: data should be converted back to json string .. or use json serializer
                Values dataVal = new Values("data", mapper.writeValueAsString(data));
                switch (type) {
                    case INFO:
                        _collector.emit(INFO,tuple,dataVal);
                        break;
                    case COMMAND:
                        _collector.emit(COMMAND,tuple,dataVal);
                        break;
                    default:
                        // NB: we'll push the original message onto the CONFUSED channel
                        _collector.emit(OTHER,tuple, new Values(tuple.getString(0)));
                        System.out.println("WARNING: Unknown Message Type: " + type);
                }
            } catch (IOException e) {
                System.out.println("ERROR: Exception during json parsing: " + e.getMessage());
                e.printStackTrace();
                return;
            }

            // Regardless of whether we have errors, we don't want to reprocess for now, so send ack
            _collector.ack(tuple);
        }

        @Override
        public void declareOutputFields(OutputFieldsDeclarer declarer) {
            declarer.declareStream(INFO, new Fields("key","message"));
            declarer.declareStream(COMMAND, new Fields("key","message"));
            declarer.declareStream(OTHER, new Fields("key","message"));
        }
    }


    /**
     * InfoSplitterBolt - split the INFO message types
     * - All SWITCH / PORT / ISL messages will be sent to their respective queues
     * - UP / DOWN for each of SWITCH/PORT/ISL will be sent to a their respective queues
     * - OTHER will be used for each are if there isn't a match (INFO.OTHER,SWITCH.OTHER, etc.)
     */
    public static class InfoSplitterBolt extends BaseRichBolt {

        private static Logger logger = LoggerFactory.getLogger(InfoSplitterBolt.class);

        OutputCollector _collector;

        public static final String INFO_SWITCH = "INFO.SWITCH";
        public static final String INFO_PORT = "INFO.PORT";
        public static final String INFO_ISL = "INFO.ISL";
        public static final String SWITCH_UPDOWN = "SWITCH.UPDOWN";
        public static final String PORT_UPDOWN = "PORT.UPDOWN";
        public static final String ISL_UPDOWN = "ISL.UPDOWN";
        public static final String SWITCH_OTHER = "SWITCH.OTHER";
        public static final String PORT_OTHER = "PORT.OTHER";
        public static final String ISL_OTHER = "ISL.OTHER";
        public static final String INFO_OTHER = "INFO.OTHER";

        public static final String[] outputStreams = {
                INFO_SWITCH, INFO_PORT, INFO_ISL,
                SWITCH_UPDOWN, PORT_UPDOWN, ISL_UPDOWN,
                SWITCH_OTHER, PORT_OTHER, ISL_OTHER
        };

        @Override
        public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
            _collector = collector;
        }

        /**
         * The data field holds the "message_type" and "state" fields.
         * @param root the "data" field of an "INFO" message
         */
        private void splitInfoMessage(Map<String,?> root, Tuple tuple) throws JsonProcessingException {
            Values dataVal = new Values("data", new ObjectMapper().writeValueAsString(root));
            String key = ((String) root.get("message_type")).toUpperCase();
            String state = (String) root.get("state");
            switch (key) {
                case "SWITCH":
                    _collector.emit(INFO_SWITCH,tuple,dataVal);
                    logger.debug("EMIT {} : {}", INFO_SWITCH, dataVal);
                    if (state.equals("ACTIVATED") || state.equals("DEACTIVATED")){
                        _collector.emit(SWITCH_UPDOWN,tuple,dataVal);
                        logger.debug("EMIT {} : {}", SWITCH_UPDOWN, dataVal);
                    } else {
                        _collector.emit(SWITCH_OTHER,tuple,dataVal);
                        logger.debug("EMIT {} : {}", SWITCH_OTHER, dataVal);
                    }
                    break;
                case "PORT":
                    _collector.emit(INFO_PORT,tuple,dataVal);
                    if (state.equals("UP") || state.equals("DOWN")){
                        _collector.emit(PORT_UPDOWN,tuple,dataVal);
                    } else {
                        _collector.emit(PORT_OTHER,tuple,dataVal);
                    }
                    break;
                case "ISL":
                    _collector.emit(INFO_ISL,tuple,dataVal);
                    if (state.equals("UP") || state.equals("DOWN")){
                        _collector.emit(ISL_UPDOWN,tuple,dataVal);
                    } else {
                        _collector.emit(ISL_OTHER,tuple,dataVal);
                    }
                    break;
                default:
                    // NB: we'll push the original message onto the CONFUSED channel
                    _collector.emit(INFO_OTHER,tuple,dataVal);
                    logger.warn("Unknown INFO Message Type: {}\nJSON:{}", key, root);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public void execute(Tuple tuple) {
//            logger.trace("tuple.getFields() = " + tuple.getFields());
//            logger.trace("tuple.getValues() = " + tuple.getValues());
            String json = tuple.getString(1);

            Map<String,?> root = null;
            try {
                root = (Map<String,?>) new ObjectMapper().readValue(json, Map.class);
                splitInfoMessage(root,tuple);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                // Regardless of whether we have errors, we don't want to reprocess for now, so send ack
                _collector.ack(tuple);

            }
        }

        @Override
        public void declareOutputFields(OutputFieldsDeclarer declarer) {
            Fields f = new Fields("key","message");
            for (String name : outputStreams) {
                logger.trace("Declaring Stream: " + name);
                declarer.declareStream(name, f);
            }
        }
    }

    /* ClearMessages became unnecessary once we provided (and change) temporary directory */
//    public void ClearMessages(){
//        String conn = server.zk.getConnectString();
//        System.out.println("Deleting Topic: " + topic + " at " + conn);
//        ZkClient zkc = new ZkClient(conn);
//        ZkUtils zku = new ZkUtils(zkc,null,false);
//        TopicCommand.TopicCommandOptions tco =
//                new TopicCommand.TopicCommandOptions(new String[]{
//                "--zookeeper", conn, "--delete", "--topic", topic});
//        //TopicCommand.deleteTopic(zku,tco);
//        zkc.close();
//    }

    // =~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=
    // TESTING Area
    // =~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=

    private static final Properties kprops = new Properties();
    private static Boolean kafkaInitialized = false;
    private static KafkaProducer<String, String> kProducer;

    private static final void _prepKafka(){
        synchronized (kafkaInitialized){
            if (!kafkaInitialized){
                kprops.put("bootstrap.servers", "localhost:9092");
                kprops.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
                kprops.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
//                kprops.put("acks", acks);
//                kprops.put("retries", retries);
//                kprops.put("batch.size", batchSize);
//                kprops.put("linger.ms", linger);
//                kprops.put("buffer.memory", memory);
                kProducer = new KafkaProducer<>(kprops);
                kafkaInitialized = true;
            }
        }
    }

    private void primeKafkaTopic(String topic){
        _prepKafka();
        kProducer.send(new ProducerRecord<>(topic, "no_op", "{'type': 'NO_OP'}"));
    }

    private void SendMessages(){
        System.out.println("==> sending records");

        _prepKafka();

        kProducer.send(new ProducerRecord<>(topic, "data", createSwitchInfoMessage("sw1",
                "ADDED")));
        kProducer.send(new ProducerRecord<>(topic, "data", createSwitchInfoMessage("sw2",
                "ADDED")));
        kProducer.send(new ProducerRecord<>(topic, "data", createSwitchInfoMessage("sw3",
                "ADDED")));
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            /* consume the exception .. ie noop */
        }
        kProducer.send(new ProducerRecord<>(topic, "data", createSwitchInfoMessage("sw1",
                "ACTIVATED")));
        kProducer.send(new ProducerRecord<>(topic, "data", createSwitchInfoMessage("sw2",
                "ACTIVATED")));
        kProducer.send(new ProducerRecord<>(topic, "data", createSwitchInfoMessage("sw3",
                "ACTIVATED")));

        //kProducer.close();

    }

    /**
     * @param state - ACTIVATED | ADDED | CHANGE | DEACTIVATED | REMOVED
     */
    public String createSwitchInfoMessage (String switchID, String state) {
        return createInfoMessage(true,switchID,null,state);
    }

    /**
     * @param state - ADD | OTHER_UPDATE | DELETE | UP | DOWN
     */
    public String createPortInfoMessage (String switchID, String portID, String state) {
        return createInfoMessage(false,switchID,portID,state);
    }

    /**
     * TODO: this handles switch / port messages, but not ISL. Add it.
     * Example OpenFlow Messages:
            {
            "type": "INFO",
            "timestamp": 1489980143,
            "data": {
                "message_type": "switch",
                "switch_id": "0x0000000000000001",
                "state": "ACTIVATED | ADDED | CHANGE | DEACTIVATED | REMOVED"
                }
            }

        {
             "type": "INFO",
             "timestamp": 1489980143,
             "data": {
                "message_type": "port",
                "switch_id": "0x0000000000000001",
                "state": "UP | DOWN | .. "
                "port_no": LONG
                "max_capacity": LONG
             }
        }

     * @param isSwitch - it is either a switch or port at this stage.
     */
    private String createInfoMessage (boolean isSwitch, String switchID, String portID, String
            state) {
        StringBuffer sb = new StringBuffer("{'type': 'INFO', ");
        sb.append("'timestamp': ").append(System.currentTimeMillis()).append(", ");
        sb.append("'data': {'message_type': '").append(isSwitch?"switch":"port").append("', ");
        sb.append("'switch_id': '").append(switchID).append("', ");
        if (!isSwitch) {
            sb.append(", 'port_no': ").append(portID).append("', ");
        }
        sb.append("'state': '").append(state).append("'}}");
        return sb.toString().replace("'","\"");
    }
}
