package org.bitbucket.openkilda.wfm;

import kafka.server.KafkaConfig;
import kafka.server.KafkaServerStartable;
import org.apache.curator.test.TestingServer;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import com.google.common.io.Files;
import org.apache.storm.kafka.BrokerHosts;
import org.apache.storm.kafka.ZkHosts;

/**
 * Utility classes to facilitate testing.
 *
 * Key Utilities:
 *
 */
public class TestUtils {

    public static class KafkaTestFixture {
        public TestingServer zk;
        public KafkaServerStartable kafka;


        public void start() throws Exception {
            this.start(serverProperties());
        }

        public void start(Properties props) throws Exception {
            Integer port = getZkPort(props);
            File tempDir = Files.createTempDir();
            props.put("log.dirs", tempDir.getAbsolutePath());

            //zk = new TestingServer(port); // this starts the zk server
            zk = new TestingServer(port, tempDir); // this starts the zk server
            //zk.start();
            System.out.println("Started ZooKeeper: ");
            System.out.println("--> Temp Directory: " + zk.getTempDirectory());

            KafkaConfig kafkaConfig = new KafkaConfig(props);
            kafka = new KafkaServerStartable(kafkaConfig);
            System.out.println("Started KAFKA: ");
            kafka.startup();
            //for (Object v : kafka.serverConfig().values().entrySet()) {
            //    System.out.println(v);
            //}
        }

        public void stop() throws IOException {
            kafka.shutdown();
            zk.stop();
            zk.close();
        }

        private int getZkPort(Properties properties) {
            String url = (String) properties.get("zookeeper.connect");
            String port = url.split(":")[1];
            return Integer.valueOf(port);
        }

        public BrokerHosts getBrokerHosts(){
            return new ZkHosts(zk.getConnectString());
        }
    }

    public static Properties serverProperties() {
        String tempDir = Files.createTempDir().getAbsolutePath();
        Properties props = new Properties();
        props.put("zookeeper.connect", "localhost:2182");
        props.put("broker.id", "1");
        props.put("delete.topic.enable","true");
        return props;
    }


}
