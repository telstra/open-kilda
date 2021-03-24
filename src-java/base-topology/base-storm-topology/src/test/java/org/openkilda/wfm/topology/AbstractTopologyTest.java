/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.openkilda.wfm.AbstractStormTest;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.error.ConfigurationException;

import com.sabre.oss.conf4j.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;
import org.apache.storm.generated.StormTopology;
import org.junit.Test;
import org.kohsuke.args4j.CmdLineException;

import java.io.IOException;
import java.util.Properties;

@Slf4j
public class AbstractTopologyTest extends AbstractStormTest {

    public static final String TOPOLOGY_NAME = "test_topology";
    public static final String TOPOLOGY_PARALLELISM_PROPERTY = "topology_parallelism";
    public static final String FIRST_BOLT_NAME = "first_bolt";
    public static final String FIRST_BOLT_NAME_PROPERTY = "first_bolt_name";
    public static final String FIRST_BOLT_PARALLELISM_PROPERTY = "first_bolt_parallelism";
    public static final String FIRST_BOLT_NUM_TASKS_PROPERTY = "first_bolt_num_tasks";
    public static final String SECOND_BOLT_NAME = "second_bolt";
    public static final String SECOND_BOLT_NAME_PROPERTY = "second_bolt_name";
    public static final String SECOND_BOLT_PARALLELISM_PROPERTY = "second_bolt_parallelism";
    public static final String UNKNOWN_BOLT_NAME = "unknown_bolt";
    public static final String UNKNOWN_TOPOLOGY_NAME = "unknown_topology";
    public static final Integer TOPOLOGY_PARALLELISM = 4;
    public static final Integer FIRST_BOLT_PARALLELISM = 2;
    public static final Integer FIRST_BOLT_NUM_TASKS = 3;
    public static final Integer SECOND_BOLT_PARALLELISM = 8;


    @Test
    public void getKnownBoltParallelismTest() throws CmdLineException, ConfigurationException, IOException {
        TestTopology topology = new TestTopology(getLaunchEnvironment(), TOPOLOGY_NAME);
        assertEquals(FIRST_BOLT_PARALLELISM, topology.getBoltParallelism(FIRST_BOLT_NAME));
    }

    @Test
    public void getUnknownBoltParallelismTest() throws CmdLineException, ConfigurationException, IOException {
        TestTopology topology = new TestTopology(getLaunchEnvironment(), TOPOLOGY_NAME);
        // For unknown bolts we should get topology parallelism
        assertEquals(TOPOLOGY_PARALLELISM, topology.getBoltParallelism(UNKNOWN_BOLT_NAME));
    }

    @Test
    public void getBoltParallelismWithoutConfigTest() throws CmdLineException, ConfigurationException, IOException {
        // There is no config in /resources folder for unknown topology. So null parallelism must be returned
        TestTopology topology = new TestTopology(getLaunchEnvironment(), UNKNOWN_TOPOLOGY_NAME);
        assertNull(topology.getBoltParallelism(FIRST_BOLT_NAME));
    }

    @Test
    public void getKnownBoltNumTasksTest() throws CmdLineException, ConfigurationException, IOException {
        TestTopology topology = new TestTopology(getLaunchEnvironment(), TOPOLOGY_NAME);
        assertEquals(FIRST_BOLT_NUM_TASKS, topology.getBoltNumTasks(FIRST_BOLT_NAME));
    }

    @Test
    public void getUnknownBoltNumTasksTest() throws CmdLineException, ConfigurationException, IOException {
        TestTopology topology = new TestTopology(getLaunchEnvironment(), TOPOLOGY_NAME);
        // For unknown bolts we just return null
        assertNull(topology.getBoltNumTasks(UNKNOWN_BOLT_NAME));
    }

    @Test
    public void getBoltNumTasksWithoutConfigTest() throws CmdLineException, ConfigurationException, IOException {
        // There is no config in /resources folder for unknown topology. So null num tasks must be returned
        TestTopology topology = new TestTopology(getLaunchEnvironment(), UNKNOWN_TOPOLOGY_NAME);
        assertNull(topology.getBoltParallelism(FIRST_BOLT_NAME));
    }

    @Test
    public void getKnownBoltInstancesCountTest() throws CmdLineException, ConfigurationException, IOException {
        TestTopology topology = new TestTopology(getLaunchEnvironment(), TOPOLOGY_NAME);
        assertEquals(FIRST_BOLT_NUM_TASKS.intValue(), topology.getBoltInstancesCount(FIRST_BOLT_NAME));
    }

    @Test
    public void getKnownBoltInstancesCountWithoutCountInConfigTest()
            throws CmdLineException, ConfigurationException, IOException {
        TestTopology topology = new TestTopology(getLaunchEnvironment(), TOPOLOGY_NAME);
        assertEquals(SECOND_BOLT_PARALLELISM.intValue(), topology.getBoltInstancesCount(SECOND_BOLT_NAME));
    }

    @Test
    public void getUnknownBoltInstancesCountTest() throws CmdLineException, ConfigurationException, IOException {
        TestTopology topology = new TestTopology(getLaunchEnvironment(), TOPOLOGY_NAME);
        assertEquals(TOPOLOGY_PARALLELISM.intValue(), topology.getBoltInstancesCount(UNKNOWN_BOLT_NAME));
    }

    @Test
    public void getBoltInstancesCountWithoutConfigTest() throws CmdLineException, ConfigurationException, IOException {
        TestTopology topology = new TestTopology(getLaunchEnvironment(), UNKNOWN_TOPOLOGY_NAME);
        assertEquals(1, topology.getBoltInstancesCount(FIRST_BOLT_NAME));
    }

    @Test
    public void getBoltInstancesCountSeveralBoltsTest() throws CmdLineException, ConfigurationException, IOException {
        TestTopology topology = new TestTopology(getLaunchEnvironment(), TOPOLOGY_NAME);
        assertEquals(FIRST_BOLT_NUM_TASKS + SECOND_BOLT_PARALLELISM + TOPOLOGY_PARALLELISM,
                topology.getBoltInstancesCount(FIRST_BOLT_NAME, SECOND_BOLT_NAME, UNKNOWN_BOLT_NAME));
    }

    @Test
    public void getBoltInstancesCountInvalidNumTasksTest()
            throws CmdLineException, ConfigurationException, IOException {
        // bolt parallelism must be <= num tasks. If it doesn't storm sets bolt parallelism equal to num tasks.
        // So even if user sets parallelism greater than num tasks, getBoltInstancesCount() must return num tasks.
        int parallelism = 10;
        int numTasks = 5;
        TestTopology topology = new TestTopology(getLaunchEnvironment(parallelism, numTasks), TOPOLOGY_NAME);
        assertEquals(numTasks, topology.getBoltInstancesCount(FIRST_BOLT_NAME));
    }

    private LaunchEnvironment getLaunchEnvironment() throws IOException, CmdLineException, ConfigurationException {
        return getLaunchEnvironment(FIRST_BOLT_PARALLELISM, FIRST_BOLT_NUM_TASKS);
    }

    /**
     * Uses /resources/test_topology.yaml config file to create launch environment.
     */
    private LaunchEnvironment getLaunchEnvironment(int firstBoltParallelism, int firstBoltNumTasks)
            throws IOException, CmdLineException, ConfigurationException {
        makeConfigFile();
        Properties properties = new Properties();
        properties.put(FIRST_BOLT_NAME_PROPERTY, FIRST_BOLT_NAME);
        properties.put(TOPOLOGY_PARALLELISM_PROPERTY, String.valueOf(TOPOLOGY_PARALLELISM));
        properties.put(FIRST_BOLT_PARALLELISM_PROPERTY, String.valueOf(firstBoltParallelism));
        properties.put(FIRST_BOLT_NUM_TASKS_PROPERTY, String.valueOf(firstBoltNumTasks));
        properties.put(SECOND_BOLT_NAME_PROPERTY, SECOND_BOLT_NAME);
        properties.put(SECOND_BOLT_PARALLELISM_PROPERTY, String.valueOf(SECOND_BOLT_PARALLELISM));

        LaunchEnvironment env = makeLaunchEnvironment();
        env.setupOverlay(properties);
        return env;
    }

    @Configuration
    public interface TestConfig extends AbstractTopologyConfig {
    }

    private static class TestTopology extends AbstractTopology<TestConfig> {
        public TestTopology(LaunchEnvironment env, String topologyDefinitionName) {
            super(env, topologyDefinitionName, TestConfig.class);
        }

        @Override
        public StormTopology createTopology() {
            return null;
        }

        @Override
        protected String getZkTopoName() {
            return null;
        }
    }
}
