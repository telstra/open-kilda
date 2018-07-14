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

package org.openkilda.wfm.topology.packetmon.bolts;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.mock;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;

import org.openkilda.messaging.info.Datapoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.tuple.Tuple;
import org.easymock.Capture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ParseTsdbStatsBoltTest {

    private static final String FlowPackets = "pen.flow.packets";
    private static final String FlowPacketsIngress = "pen.flow.ingress.packets";
    private static final String SwitchStats = "pen.switch.tx-packets";
    private static final List<String> goodMetrics = Stream.of(FlowPackets, FlowPacketsIngress)
            .collect(Collectors.toList());

    private Tuple tuple;
    private ParseTsdbStatsBolt parseTsdbStatsBolt;
    private Datapoint datapoint;
    private OutputCollector collector;
    private Map map;
    private TopologyContext context;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void setUp() throws Exception {
        // Mock objects
        tuple = mock(Tuple.class);
        collector = createStrictMock(OutputCollector.class);
        context = createStrictMock(TopologyContext.class);

        parseTsdbStatsBolt = new ParseTsdbStatsBolt();
        map = new HashMap();
    }

    @After
    public void tearDown() throws Exception {
    }

    private Datapoint buildDatapoint(String metric) {
        datapoint = new Datapoint();

        datapoint.setMetric(metric);
        Map<String, String> tags = new HashMap<>();
        tags.put("direction", "forward");
        tags.put("flowid", "12345");
        datapoint.setTags(tags);
        datapoint.setValue(9999);
        datapoint.setTime(1529971181L);
        return datapoint;
    }

    @Test
    public void flowMonStreamPositiveTest() throws Exception {
        // test each of the good metrics
        for (String metric : goodMetrics) {
            // given
            datapoint = buildDatapoint(metric);
            expect(tuple.getString(0)).andStubReturn(objectMapper.writeValueAsString(datapoint));
            expect(tuple.getFields()).andStubReturn(ParseTsdbStatsBolt.fields);
            Capture<String> emitStreamId = Capture.newInstance();
            Capture<List<Object>> emitValues = Capture.newInstance();
            expect(collector.emit(capture(emitStreamId), capture(emitValues))).andReturn(new ArrayList<>());
            collector.ack(tuple);
            replay(tuple, collector, context);

            // when
            parseTsdbStatsBolt.prepare(map, context, collector);
            parseTsdbStatsBolt.execute(tuple);

            // then
            assertEquals(ParseTsdbStatsBolt.FlowMon_Stream, emitStreamId.getValue());
            assertEquals(2, emitValues.getValue().size());
            assertEquals(datapoint, emitValues.getValue().get(1));
            verify(collector);

            reset(tuple, collector, context);
        }
    }

    @Test
    public void flowMonStreamNegativeTest() throws Exception {
        // given - no emit as Strict test this will error of collector.emit is called
        datapoint = buildDatapoint(SwitchStats);
        expect(tuple.getString(0)).andStubReturn(objectMapper.writeValueAsString(datapoint));
        expect(tuple.getFields()).andStubReturn(ParseTsdbStatsBolt.fields);
        collector.ack(tuple);
        replay(tuple, collector, context);

        // when
        parseTsdbStatsBolt.prepare(map, context, collector);
        parseTsdbStatsBolt.execute(tuple);

        // then
        verify(collector);
    }
}
