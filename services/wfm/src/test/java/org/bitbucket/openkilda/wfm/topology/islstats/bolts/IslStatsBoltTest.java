package org.bitbucket.openkilda.wfm.topology.islstats.bolts;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.containsString;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.info.InfoData;
import org.bitbucket.openkilda.messaging.info.InfoMessage;
import org.bitbucket.openkilda.messaging.info.event.IslChangeType;
import org.bitbucket.openkilda.messaging.info.event.IslInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathNode;
import org.bitbucket.openkilda.messaging.info.event.PortInfoData;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class IslStatsBoltTest {
    private String SWITCH1_ID = "SW0000B0D2F5B00934";
    private int SWITCH1_PORT = 1;
    private int PATH1_SEQID = 1;
    private long PATH1_LATENCY = 10;
    private PathNode NODE1 = new PathNode(SWITCH1_ID, SWITCH1_PORT, PATH1_SEQID, PATH1_LATENCY);

    private String SWITCH2_ID = "SW0000B0D2F5005E18";
    private int SWITCH2_PORT = 5;
    private int PATH2_SEQID = 2;
    private long PATH2_LATENCY = 15;
    private PathNode NODE2 = new PathNode(SWITCH2_ID, SWITCH2_PORT, PATH2_SEQID, PATH2_LATENCY);

    private long LATENCY = 1000;
    private List<PathNode> PATH = java.util.Arrays.asList(NODE1, NODE2);
    private long SPEED = 400;
    private IslChangeType STATE = IslChangeType.DISCOVERED;
    private long AVAILABLE_BANDWIDTH = 500;
    private IslInfoData islInfoData = new IslInfoData(LATENCY, PATH, SPEED, STATE, AVAILABLE_BANDWIDTH);
    private long TIMESTAMP = 1507433872;

    private IslStatsBolt statsBolt = new IslStatsBolt();

    private static String CORRELATION = "system";
    private static Destination DESTINATION = null;
    private InfoMessage message = new InfoMessage(islInfoData, TIMESTAMP,CORRELATION, DESTINATION);

    private static Logger logger = LogManager.getLogger(IslStatsBolt.class);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setupOnce() throws Exception {

    }

    @AfterClass
    public static void teardownOnce() throws Exception {
    }

    @Test
    public void buildTsdbTuple() throws Exception {
        List<Object> tsdbTuple = statsBolt.buildTsdbTuple(islInfoData, TIMESTAMP);
        assertEquals(tsdbTuple.get(0), "pen.isl.latency");
        assertEquals(tsdbTuple.get(1), TIMESTAMP);
        assertEquals(tsdbTuple.get(2), LATENCY);

        logger.debug(tsdbTuple.get(3));
        Map<String, String> pathNode = (HashMap<String, String>) tsdbTuple.get(3);
        assertEquals(pathNode.get("src_switch"), SWITCH1_ID);
        assertEquals(pathNode.get("dst_switch"), SWITCH2_ID);
        assertEquals(Integer.parseInt(pathNode.get("src_port")), SWITCH1_PORT);
        assertEquals(Integer.parseInt(pathNode.get("dst_port")), SWITCH2_PORT);
    }

    @Test
    public void getJson() throws Exception {
    }

    @Test
    public void getMessage() throws Exception {
    }

    @Test
    public void getInfoData() throws Exception {
        Object data = statsBolt.getInfoData(message);
        assertThat(data, instanceOf(InfoData.class));
    }

    @Test
    public void getIslInfoData() throws Exception {
        Object data = statsBolt.getIslInfoData(statsBolt.getInfoData(message));
        assertThat(data, instanceOf(IslInfoData.class));
        assertEquals(data, islInfoData);

        thrown.expect(Exception.class);
        thrown.expectMessage(containsString("is not an IslInfoData"));
        PortInfoData portData = new PortInfoData();
        InfoMessage badMessage = new InfoMessage(portData,TIMESTAMP, CORRELATION, null);
        data = statsBolt.getIslInfoData(statsBolt.getIslInfoData(badMessage.getData()));
    }

}