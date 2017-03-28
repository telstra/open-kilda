package org.bitbucket.openkilda.wfm;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.storm.kafka.*;

import org.apache.storm.Config;
import org.apache.storm.utils.Utils;
import org.junit.*;

import org.apache.storm.LocalCluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Basic Splitter Tests.
 * <p>
 * The Splitter listens to a kafka queue and splits them into other queues.
 */
public class SimpleSplitterTest {

    private static Logger logger = LoggerFactory.getLogger(SimpleSplitterTest.class);
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


    @Test
    public void no_op() {

        OFEventSplitterTopology splitter = new OFEventSplitterTopology();
        splitter.setBrokerHosts(server.getBrokerHosts());
        splitter.build();
        LocalCluster cluster = new LocalCluster();
        cluster.submitTopology(splitter.topoName, stormConfig()
                , splitter.builder.createTopology());

        // Send data into kafka ..
        SendMessages(splitter.topic);

        Utils.sleep(15 * 1000);
        Assert.assertTrue(true);
        cluster.killTopology(splitter.topoName);

    }



    // =~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=
    // TESTING Area
    // =~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=~=

    private void SendMessages(String topic){
        System.out.println("==> sending records");

        KafkaProducer<String,String> kProducer = KafkaUtils.createStringsProducer();

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
