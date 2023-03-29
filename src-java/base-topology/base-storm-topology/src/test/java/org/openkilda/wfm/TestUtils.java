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

import org.openkilda.wfm.config.KafkaConfig;
import org.openkilda.wfm.config.ZookeeperConfig;

import com.google.common.io.Files;
import kafka.server.KafkaServer;
import org.apache.curator.test.TestingServer;
import org.apache.kafka.common.utils.Time;
import scala.Option;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

/**
 * Utility classes to facilitate testing.
 *
 * <p>Key Utilities:</p>
 */
public final class TestUtils {

    private TestUtils() {}

    private static Properties serverProperties(ZookeeperConfig zookeeperConfig, KafkaConfig kafkaConfig) {
        Properties props = new Properties();
        props.setProperty("zookeeper.connect", zookeeperConfig.getConnectString());
        props.setProperty("broker.id", "1");
        props.setProperty("delete.topic.enable", "true");
        props.setProperty("listeners", kafkaConfig.getListeners());
        props.setProperty("advertised.listeners", kafkaConfig.getAdvertisedListeners());
        props.setProperty("offsets.topic.replication.factor", "1");
        return props;
    }

    public static class KafkaTestFixture {
        public TestingServer zk;
        public KafkaServer kafka;
        public File tempDir = Files.createTempDir();
        private ZookeeperConfig zooKeeperConfig;
        private KafkaConfig kafkaConfig;

        public KafkaTestFixture(ZookeeperConfig zooKeeperConfig, KafkaConfig kafkaConfig) {
            this.zooKeeperConfig = zooKeeperConfig;
            this.kafkaConfig = kafkaConfig;
        }

        public void start() throws Exception {
            Properties props = serverProperties(zooKeeperConfig, kafkaConfig);
            this.start(props);
        }

        private void start(Properties props) throws Exception {
            Integer port = getZkPort(props);

            zk = new TestingServer(port, tempDir); // this starts the zk server
            System.out.println("Started ZooKeeper: ");
            System.out.println("--> Temp Directory: " + zk.getTempDirectory());

            props.put("log.dirs", tempDir.getAbsolutePath());
            kafka.server.KafkaConfig kafkaConfig = new kafka.server.KafkaConfig(props);
            kafka = new KafkaServer(kafkaConfig, Time.SYSTEM, Option.empty(), false);
            kafka.startup();
            System.out.println("Started KAFKA: ");
        }

        /**
         * Stop kafka and zookeeper.
         *
         * @throws IOException IO exception
         */
        public void stop() throws IOException {
            kafka.shutdown();
            zk.stop();
            zk.close();
            tempDir.delete();
        }

        private int getZkPort(Properties properties) {
            String url = (String) properties.get("zookeeper.connect");
            String port = url.split(":")[1].split("/")[0];
            return Integer.valueOf(port);
        }
    }
}
