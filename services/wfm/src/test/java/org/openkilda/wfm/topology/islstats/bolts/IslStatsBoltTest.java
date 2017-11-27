package org.openkilda.wfm.topology.islstats.bolts;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.Datapoint;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.event.PortInfoData;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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

    private int LATENCY = 1000;
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

    private final String goodJson = "{\"type\":\"INFO\",\"destination\":\"TOPOLOGY_ENGINE\",\"payload\":{\"message_type\":\"isl\",\"id\":\"00:00:00:00:00:00:00:06_1\",\"latency_ns\":19,\"path\":[{\"switch_id\":\"00:00:00:00:00:00:00:06\",\"port_no\":1,\"seq_id\":0,\"segment_latency\":19},{\"switch_id\":\"00:00:00:00:00:00:00:05\",\"port_no\":2,\"seq_id\":1}],\"speed\":10000000,\"available_bandwidth\":9000000,\"state\":\"DISCOVERED\"},\"timestamp\":1507651077860,\"correlation_id\":\"system-request\"}";
    private final String badJson = "{\"destination\":\"WFM_STATS\",\"payload\":{\"message_type\":\"flow_stats\",\"switch_id\":\"00:00:00:00:00:00:00:03\",\"stats\":[{\"xid\":1852,\"entries\":[]}]},\"timestamp\":1507651267714,\"correlation_id\":\"system-request\"}";

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
        assertThat(tsdbTuple.size(), is(1));

        Datapoint datapoint = Utils.MAPPER.readValue(tsdbTuple.get(0).toString(), Datapoint.class);
        assertEquals("pen.isl.latency", datapoint.getMetric());
        assertEquals((Long) TIMESTAMP, datapoint.getTimestamp());
        assertEquals(LATENCY, datapoint.getValue());

        Map<String, String> pathNode = datapoint.getTags();
        assertEquals(SWITCH1_ID, pathNode.get("src_switch"));
        assertEquals(SWITCH2_ID, pathNode.get("dst_switch"));
        assertEquals(SWITCH1_PORT, Integer.parseInt(pathNode.get("src_port")));
        assertEquals(SWITCH2_PORT, Integer.parseInt(pathNode.get("dst_port")));
    }

    @Test
    public void getMessage() throws Exception {
        Object data = statsBolt.getMessage(goodJson);
        assertThat(data, instanceOf(Message.class));

        thrown.expect(IOException.class);
        data = statsBolt.getMessage(badJson);
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