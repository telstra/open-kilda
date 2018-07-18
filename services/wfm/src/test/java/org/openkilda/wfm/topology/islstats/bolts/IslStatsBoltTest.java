package org.openkilda.wfm.topology.islstats.bolts;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.Datapoint;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.model.SwitchId;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class IslStatsBoltTest {
    private SwitchId switchId = new SwitchId("00:00:b0:d2:f5:b0:09:34");
    private String switch1IdOtsdFormat = switchId.toOtsdFormat();
    private int switch1Port = 1;
    private int path1Seqid = 1;
    private long path1Latency = 10L;
    private PathNode node1 = new PathNode(switchId, switch1Port, path1Seqid, path1Latency);

    private SwitchId switchId1 = new SwitchId("00:00:b0:d2:f5:00:5e:18");
    private String switch2IdOtsdFormat = switchId1.toOtsdFormat();
    private int switch2Port = 5;
    private int path2Seqid = 2;
    private long path2Latency = 15L;
    private PathNode node2 = new PathNode(switchId1, switch2Port, path2Seqid, path2Latency);

    private int latency = 1000;
    private List<PathNode> path = java.util.Arrays.asList(node1, node2);
    private long speed = 400L;
    private IslChangeType state = IslChangeType.DISCOVERED;
    private long availableBandwidth = 500L;
    private IslInfoData islInfoData = new IslInfoData(latency, path, speed, state, availableBandwidth);
    private long timestamp = 1507433872L;

    private IslStatsBolt statsBolt = new IslStatsBolt();

    private static String correlation = "system";
    private static Destination destination = null;
    private InfoMessage message = new InfoMessage(islInfoData, timestamp, correlation, destination);

    private static Logger logger = LoggerFactory.getLogger(IslStatsBolt.class);

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
        List<Object> tsdbTuple = statsBolt.buildTsdbTuple(islInfoData, timestamp);
        assertThat(tsdbTuple.size(), is(1));

        Datapoint datapoint = Utils.MAPPER.readValue(tsdbTuple.get(0).toString(), Datapoint.class);
        assertEquals("pen.isl.latency", datapoint.getMetric());
        assertEquals((Long) timestamp, datapoint.getTime());
        assertEquals(latency, datapoint.getValue());

        Map<String, String> pathNode = datapoint.getTags();
        assertEquals(switch1IdOtsdFormat, pathNode.get("src_switch"));
        assertEquals(switch2IdOtsdFormat, pathNode.get("dst_switch"));
        assertEquals(switch1Port, Integer.parseInt(pathNode.get("src_port")));
        assertEquals(switch2Port, Integer.parseInt(pathNode.get("dst_port")));
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
        InfoMessage badMessage = new InfoMessage(portData, timestamp, correlation, null);
        data = statsBolt.getIslInfoData(statsBolt.getIslInfoData(badMessage.getData()));
    }

}