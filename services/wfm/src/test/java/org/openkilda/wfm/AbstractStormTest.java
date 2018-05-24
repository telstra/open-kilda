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

import org.apache.storm.testing.CompleteTopologyParam;
import org.apache.storm.testing.MkClusterParam;
import org.kohsuke.args4j.CmdLineException;
import org.openkilda.wfm.error.ConfigurationException;
import org.openkilda.wfm.topology.TestKafkaProducer;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;

import org.openkilda.wfm.topology.TopologyConfig;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

/**
 * Created by carmine on 4/4/17.
 */
public class AbstractStormTest {
    protected static String CONFIG_NAME = "class-level-overlay.properties";
    protected static String NEO4J_LISTEN_ADDRESS = "localhost:27600";

    protected static TestKafkaProducer kProducer;
    protected static LocalCluster cluster;
    protected static MkClusterParam clusterParam;
    protected static CompleteTopologyParam completeTopologyParam;
    static TestUtils.KafkaTestFixture server;

    @ClassRule
    public static TemporaryFolder fsData = new TemporaryFolder();

    protected static Properties kafkaProperties() throws ConfigurationException, CmdLineException {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, makeUnboundConfig().getKafkaHosts());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "test");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put("request.required.acks", "1");
        return properties;
    }

    protected static Properties kafkaProperties(final String groupId) throws ConfigurationException, CmdLineException {
        Properties properties = kafkaProperties();
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        return properties;
    }

    protected static Config stormConfig() {
        Config config = new Config();
        config.setDebug(false);
        config.setMaxTaskParallelism(1);
        config.setNumWorkers(1);
        return config;
    }

    @BeforeClass
    public static void setupOnce() throws Exception {
        System.out.println("------> Creating Sheep \uD83D\uDC11\n");

        makeConfigFile();

        server = new TestUtils.KafkaTestFixture(makeUnboundConfig());
        server.start();

        cluster = new LocalCluster();
        kProducer = new TestKafkaProducer(kafkaProperties());
    }

    @AfterClass
    public static void teardownOnce() throws Exception {
        System.out.println("------> Killing Sheep \uD83D\uDC11\n");
        kProducer.close();
        cluster.shutdown();
        server.stop();
    }

    protected static TopologyConfig makeUnboundConfig() throws ConfigurationException, CmdLineException {
        LaunchEnvironment env = makeLaunchEnvironment();
        return new TopologyConfig(env.makePropertiesReader());
    }

    protected static LaunchEnvironment makeLaunchEnvironment() throws CmdLineException, ConfigurationException {
        String args[] = makeLaunchArgs();
        return new LaunchEnvironment(args);
    }
    protected static LaunchEnvironment makeLaunchEnvironment(Properties overlay)
            throws CmdLineException, ConfigurationException, IOException {
        String extra = fsData.newFile().getName();
        makeConfigFile(overlay, extra);

        String args[] = makeLaunchArgs(extra);
        return new LaunchEnvironment(args);
    }

    protected static String[] makeLaunchArgs(String ...extraConfig) {
        String args[] = new String[extraConfig.length + 1];

        File root = fsData.getRoot();
        args[0] = new File(root, CONFIG_NAME).toString();
        for (int idx = 0; idx < extraConfig.length; idx += 1) {
            args[idx + 1] = new File(root, extraConfig[idx]).toString();
        }

        return args;
    }

    protected static void makeConfigFile() throws IOException {
        makeConfigFile(makeConfigOverlay(), CONFIG_NAME);
    }

    protected static void makeConfigFile(Properties overlay, String location) throws IOException {
        File path = new File(fsData.getRoot(), location);
        overlay.store(new FileWriter(path), null);
    }

    protected static Properties makeConfigOverlay() {
        return new Properties();
    }
}
