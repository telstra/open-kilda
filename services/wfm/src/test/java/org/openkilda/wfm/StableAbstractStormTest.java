package org.openkilda.wfm;


import org.apache.storm.Config;
import org.apache.storm.testing.CompleteTopologyParam;
import org.apache.storm.testing.MkClusterParam;
import org.junit.AfterClass;
import org.junit.BeforeClass;

//todo: should be removed when all tests will be free from kafka usages
public class StableAbstractStormTest extends AbstractStormTest {
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


    @AfterClass
    public static void teardownOnce() throws Exception {
    }

}
