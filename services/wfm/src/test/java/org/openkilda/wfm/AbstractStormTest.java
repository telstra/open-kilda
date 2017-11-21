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

import org.apache.storm.Config;
import org.apache.storm.testing.CompleteTopologyParam;
import org.apache.storm.testing.MkClusterParam;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;
import org.kohsuke.args4j.CmdLineException;
import org.openkilda.wfm.topology.TopologyConfig;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

/**
 * Created by carmine on 4/4/17.
 */
public class AbstractStormTest {
    private static final String CONFIG_NAME = "class-level-overlay.properties";

    protected static CompleteTopologyParam completeTopologyParam;
    protected static MkClusterParam clusterParam;

    @ClassRule
    public static TemporaryFolder fsData = new TemporaryFolder();

    @BeforeClass
    public static void setupOnce() throws Exception {
        System.out.println("------> Creating Sheep \uD83D\uDC11\n");

        clusterParam = new MkClusterParam();
        clusterParam.setSupervisors(1);
        Config daemonConfig = new Config();
        daemonConfig.put(Config.STORM_LOCAL_MODE_ZMQ, false);
        clusterParam.setDaemonConf(daemonConfig);
        makeConfigFile();

        Config conf = new Config();
        conf.setNumWorkers(1);

        completeTopologyParam = new CompleteTopologyParam();
        completeTopologyParam.setStormConf(conf);
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
