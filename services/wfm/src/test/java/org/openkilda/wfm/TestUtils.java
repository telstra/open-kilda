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

package org.openkilda.wfm;

import org.openkilda.wfm.error.ConfigurationException;
import org.openkilda.wfm.topology.TopologyConfig;

import com.google.common.io.Files;
import kafka.server.KafkaConfig;
import kafka.server.KafkaServerStartable;
import org.apache.curator.test.TestingServer;
import org.apache.storm.Config;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

/**
 * Utility classes to facilitate testing.
 * <p>
 * Key Utilities:
 */
public class TestUtils {
    public static Properties serverProperties(TopologyConfig config) {
        Properties props = new Properties();
        props.setProperty("zookeeper.connect", config.getZookeeperHosts());
        props.setProperty("broker.id", "1");
        props.setProperty("delete.topic.enable", "true");
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
        public File tempDir = Files.createTempDir();
        private TopologyConfig config;

        public KafkaTestFixture(TopologyConfig config) throws ConfigurationException {
            this.config = config;
        }

        public void start() throws Exception {
            Properties props = serverProperties(config);
            this.start(props);
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
    }
}
