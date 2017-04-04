package org.bitbucket.openkilda.wfm;

import org.apache.storm.utils.Utils;
import org.junit.*;

/**
 * Created by carmine on 4/4/17.
 */
public class AbstractStormTest {

    static TestUtils.KafkaTestFixture server;
    static KafkaUtils kutils;

    @BeforeClass
    public static void setupOnce() throws Exception {
        server = new TestUtils.KafkaTestFixture();
        server.start();
        kutils = new KafkaUtils()
                .withZookeeperHost(TestUtils.zookeeperUrl)
                .withKafkaHosts(TestUtils.kafkaUrl);
    }

    @AfterClass
    public static void teardownOnce() throws Exception {
        System.out.println("------> Killing Sheep \uD83D\uDC11\n");
        Utils.sleep(2 * 1000);
        server.stop();
    }

}
