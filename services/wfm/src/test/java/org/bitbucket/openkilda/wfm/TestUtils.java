package org.bitbucket.openkilda.wfm;

import kafka.server.KafkaConfig;
import kafka.server.KafkaServerStartable;
import org.apache.curator.test.TestingServer;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import com.google.common.io.Files;
import org.apache.storm.Config;

/**
 * Utility classes to facilitate testing.
 *
 * Key Utilities:
 *
 */
public class TestUtils {

    public static final String zookeeperUrl = "localhost:2182";
    public static final String kafkaUrl = "localhost:9092";

    public static Properties serverProperties() {
        Properties props = new Properties();
        props.put("zookeeper.connect", zookeeperUrl);
        props.put("broker.id", "1");
        props.put("delete.topic.enable","true");
        return props;
    }

    public static Config stormConfig() {
        Config config = new Config();
        config.setDebug(false);
        config.setNumWorkers(1);
        return config;
    }

    public static class KafkaTestFixture {
        public TestingServer zk;
        public KafkaServerStartable kafka;
        public File tempDir  = Files.createTempDir();


        public void start() throws Exception {
            this.start(serverProperties());
        }

        public void start(Properties props) throws Exception {
            Integer port = getZkPort(props);

            zk = new TestingServer(port, tempDir); // this starts the zk server
            System.out.println("Started ZooKeeper: ");
            System.out.println("--> Temp Directory: " + zk.getTempDirectory());

            props.put("log.dirs", tempDir.getAbsolutePath());
            KafkaConfig kafkaConfig = new KafkaConfig(props);
            kafka = new KafkaServerStartable(kafkaConfig);
            kafka.startup();
            System.out.println("Started KAFKA: ");
        }

        public void stop() throws IOException {
            kafka.shutdown();
            zk.stop();
            zk.close();
            tempDir.delete();
        }

        private int getZkPort(Properties properties) {
            String url = (String) properties.get("zookeeper.connect");
            String port = url.split(":")[1];
            return Integer.valueOf(port);
        }

//        public BrokerHosts getBrokerHosts(){
//            return new ZkHosts(zk.getConnectString());
//        }
    }


}
