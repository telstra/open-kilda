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

package org.openkilda.wfm.topology.opentsdb.bolt;

import static java.util.Collections.singletonMap;
import static org.apache.storm.Constants.SYSTEM_COMPONENT_ID;
import static org.apache.storm.Constants.SYSTEM_TICK_STREAM_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.info.Datapoint;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.wfm.topology.opentsdb.bolts.OpenTSDBFilterBolt;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.tuple.Tuple;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RunWith(MockitoJUnitRunner.class)
public class OpenTSDBFilterBoltTest {

    private static final String METRIC = "METRIC";
    private static final long TIMESTAMP = System.currentTimeMillis();
    private static final Integer VALUE = 123;

    @InjectMocks
    private OpenTSDBFilterBolt target = new OpenTSDBFilterBolt();

    @Mock
    private OutputCollector outputCollector;

    @Mock
    private Tuple tuple;

    @Captor
    private ArgumentCaptor<List<Object>> argumentCaptor;

    @Before
    public void init() {
        Mockito.reset(outputCollector, tuple);

        when(outputCollector.emit(anyList())).thenReturn(Collections.emptyList());
    }

    @Test
    public void shouldEmitMessage() throws Exception {
        mockTuple();

        target.prepare(Collections.emptyMap(), null, outputCollector);
        target.execute(tuple);

        verify(outputCollector).emit(argumentCaptor.capture());
        verify(outputCollector).ack(any(Tuple.class));
        List<Object> captured = argumentCaptor.getValue();
        assertNotNull(captured);
        assertThat(captured.size(), is(4));
        assertEquals(METRIC, captured.get(0));
        assertEquals(TIMESTAMP, captured.get(1));
        assertEquals(VALUE, captured.get(2));
        assertTrue(((Map) captured.get(3)).isEmpty());
    }

    @Test
    public void shouldEmitMessageOnlyOnce() throws Exception {
        mockTuple();

        target.prepare(Collections.emptyMap(), null, outputCollector);
        target.execute(tuple);
        target.execute(tuple);

        verify(outputCollector, times(1)).emit(argumentCaptor.capture());
        verify(outputCollector, times(2)).ack(any(Tuple.class));
    }

    @Test
    public void shouldEmitMessageOnlyOnceBecauseOfInterval() throws Exception {
        mockTuple();

        target.prepare(Collections.emptyMap(), null, outputCollector);
        target.execute(tuple);

        mockTuple(TIMESTAMP + TimeUnit.MINUTES.toMillis(10) - 1);
        target.execute(tuple);

        verify(outputCollector, times(1)).emit(argumentCaptor.capture());
        verify(outputCollector, times(2)).ack(any(Tuple.class));
    }

    @Test
    public void shouldEmitBothMessagesBecauseOfInterval() throws Exception {
        mockTuple();

        target.prepare(Collections.emptyMap(), null, outputCollector);
        target.execute(tuple);

        mockTuple(TIMESTAMP + TimeUnit.MINUTES.toMillis(10) + 1);
        target.execute(tuple);

        verify(outputCollector, times(2)).emit(argumentCaptor.capture());
        verify(outputCollector, times(2)).ack(any(Tuple.class));
    }

    @Test
    public void shouldEmitBothMessagesIfHashcodeConflicts() throws Exception {
        // given
        target.prepare(Collections.emptyMap(), null, outputCollector);

        Datapoint infoData1 = new Datapoint("1", TIMESTAMP, singletonMap("key",  "a"), VALUE);
        Datapoint infoData2 = new Datapoint("2", TIMESTAMP, singletonMap("key",  "\u0040"), VALUE);
        assertEquals(infoData1.simpleHashCode(), infoData2.simpleHashCode());

        // when
        when(tuple.contains(eq("datapoint"))).thenReturn(true);
        when(tuple.getValueByField(eq("datapoint"))).thenReturn(infoData1);
        target.execute(tuple);

        when(tuple.getValueByField(eq("datapoint"))).thenReturn(infoData2);
        target.execute(tuple);

        // then
        verify(outputCollector, times(2)).emit(argumentCaptor.capture());
        verify(outputCollector, times(2)).ack(any(Tuple.class));
    }

    @Test
    public void shouldEmitAfterTickCleanup() throws Exception {
        // given
        target.prepare(Collections.emptyMap(), null, outputCollector);

        when(tuple.contains(eq("datapoint"))).thenReturn(true);

        final long now = System.currentTimeMillis();
        final long timestamp = now - TimeUnit.MINUTES.toMillis(10) - 1;

        // when
        Datapoint infoData1 = new Datapoint("1", timestamp, singletonMap("key", "a"), VALUE);
        when(tuple.getValueByField(eq("datapoint"))).thenReturn(infoData1);
        target.execute(tuple);

        Datapoint infoData2 = new Datapoint("2", timestamp, singletonMap("key", "b"), VALUE);
        when(tuple.getValueByField(eq("datapoint"))).thenReturn(infoData2);
        target.execute(tuple);

        Tuple tickTuple = mock(Tuple.class);
        when(tickTuple.getSourceComponent()).thenReturn(SYSTEM_COMPONENT_ID);
        when(tickTuple.getSourceStreamId()).thenReturn(SYSTEM_TICK_STREAM_ID);
        target.execute(tickTuple);

        Datapoint infoData3 = new Datapoint("1", now, singletonMap("key", "a"), VALUE);
        when(tuple.getValueByField(eq("datapoint"))).thenReturn(infoData3);
        target.execute(tuple);

        // then
        verify(outputCollector, times(3)).emit(argumentCaptor.capture());
        verify(outputCollector, times(4)).ack(any(Tuple.class));
    }

    private void mockTuple(long timestamp) throws Exception {
        InfoData infoData = new Datapoint(METRIC, timestamp, Collections.emptyMap(), VALUE);
        when(tuple.contains(eq("datapoint"))).thenReturn(true);
        when(tuple.getValueByField(eq("datapoint"))).thenReturn(infoData);
    }

    private void mockTuple() throws Exception {
        mockTuple(TIMESTAMP);
    }
}
