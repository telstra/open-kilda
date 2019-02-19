/* Copyright 2018 Telstra Open Source
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
import org.openkilda.model.SwitchId;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.List;
import java.util.Map;

public class IslStatsBoltTest {
    private static final SwitchId SWITCH1_ID = new SwitchId("00:00:b0:d2:f5:b0:09:34");
    private static final String SWITCH1_ID_OTSD_FORMAT = SWITCH1_ID.toOtsdFormat();
    private static final int SWITCH1_PORT = 1;
    private static final int PATH1_SEQID = 1;
    private static final long PATH1_LATENCY = 10L;
    private static final PathNode NODE1 = new PathNode(SWITCH1_ID, SWITCH1_PORT, PATH1_SEQID, PATH1_LATENCY);

    private static final SwitchId SWITCH2_ID = new SwitchId("00:00:b0:d2:f5:00:5e:18");
    private static final String SWITCH2_ID_OTSD_FORMAT = SWITCH2_ID.toOtsdFormat();
    private static final int SWITCH2_PORT = 5;
    private static final int PATH2_SEQID = 2;
    private static final long PATH2_LATENCY = 15L;
    private static final PathNode NODE2 = new PathNode(SWITCH2_ID, SWITCH2_PORT, PATH2_SEQID, PATH2_LATENCY);

    private static final int LATENCY = 1000;
    private static final long SPEED = 400L;
    private static final IslChangeType STATE = IslChangeType.DISCOVERED;
    private static final long AVAILABLE_BANDWIDTH = 500L;
    private static final boolean UNDER_MAINTENANCE = false;
    private static final IslInfoData ISL_INFO_DATA = IslInfoData.builder()
            .latency(LATENCY)
            .source(NODE1)
            .destination(NODE2)
            .speed(SPEED)
            .state(STATE)
            .availableBandwidth(AVAILABLE_BANDWIDTH)
            .underMaintenance(UNDER_MAINTENANCE)
            .build();
    private static final long TIMESTAMP = 1507433872L;

    private IslStatsBolt statsBolt = new IslStatsBolt();

    private static final String CORRELATION_ID = "system";
    private static final Destination DESTINATION = null;
    private static final InfoMessage MESSAGE = new InfoMessage(ISL_INFO_DATA, TIMESTAMP, CORRELATION_ID, DESTINATION);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void buildTsdbTuple() throws Exception {
        List<Object> tsdbTuple = statsBolt.buildTsdbTuple(ISL_INFO_DATA, TIMESTAMP);
        assertThat(tsdbTuple.size(), is(1));

        Datapoint datapoint = Utils.MAPPER.readValue(tsdbTuple.get(0).toString(), Datapoint.class);
        assertEquals("pen.isl.latency", datapoint.getMetric());
        assertEquals((Long) TIMESTAMP, datapoint.getTime());
        assertEquals(LATENCY, datapoint.getValue());

        Map<String, String> pathNode = datapoint.getTags();
        assertEquals(SWITCH1_ID_OTSD_FORMAT, pathNode.get("src_switch"));
        assertEquals(SWITCH2_ID_OTSD_FORMAT, pathNode.get("dst_switch"));
        assertEquals(SWITCH1_PORT, Integer.parseInt(pathNode.get("src_port")));
        assertEquals(SWITCH2_PORT, Integer.parseInt(pathNode.get("dst_port")));
    }

    @Test
    public void getInfoData() throws Exception {
        Object data = statsBolt.getInfoData(MESSAGE);
        assertThat(data, instanceOf(InfoData.class));
    }

    @Test
    public void getIslInfoData() throws Exception {
        Object data = statsBolt.getIslInfoData(statsBolt.getInfoData(MESSAGE));
        assertThat(data, instanceOf(IslInfoData.class));
        assertEquals(ISL_INFO_DATA, data);

        thrown.expect(Exception.class);
        thrown.expectMessage(containsString("is not an IslInfoData"));
        PortInfoData portData = new PortInfoData();
        InfoMessage badMessage = new InfoMessage(portData, TIMESTAMP, CORRELATION_ID, null);
        statsBolt.getIslInfoData(statsBolt.getIslInfoData(badMessage.getData()));
    }
}
