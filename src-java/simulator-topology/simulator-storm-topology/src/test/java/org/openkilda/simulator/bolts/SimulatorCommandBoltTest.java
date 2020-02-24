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

package org.openkilda.simulator.bolts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

public class SimulatorCommandBoltTest {
    SimulatorCommandBolt simulatorCommandBolt;
    ObjectMapper mapper;

    @Before
    public void setUp() throws Exception {
        simulatorCommandBolt = new SimulatorCommandBolt();
        mapper = new ObjectMapper();
    }

    @Test
    public void testLinkMessage() throws Exception {
        //        String dpid = "00:00:00:00:00:00:00:01";
        //        int latency = 10;
        //        int localPort = 5;
        //        String peerSwitch = "00:00:00:00:00:00:00:01";
        //        int peerPort = 8;
        //        Tuple tuple = mock(Tuple.class);
        //        LinkMessage linkMessage = new LinkMessage(latency, localPort, peerSwitch, peerPort);
        //        when(tuple.getStringByField(AbstractTopology.MESSAGE_FIELD))
        //        .thenReturn(mapper.writeValueAsString(linkMessage));
        //
        //        Map<String, Object> values = simulatorCommandBolt.doCommand(tuple);
        //        assertTrue(values.containsKey("stream"));
        //        assertTrue(values.containsKey("values"));
        //        assertThat(values.get("values"), instanceOf(List.class));
        //
        //        List<Values> v = (List<Values>) values.get("values");
        //        assertEquals(1, v.size());
        //        assertThat(v.get(0).get(1), instanceOf(LinkMessage.class));
        //        assertEquals(SimulatorCommands.DO_ADD_LINK, v.get(0).get(2));

    }

}
