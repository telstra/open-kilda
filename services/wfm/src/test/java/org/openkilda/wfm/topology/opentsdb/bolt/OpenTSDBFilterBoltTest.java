package org.openkilda.wfm.topology.opentsdb.bolt;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.Datapoint;
import org.openkilda.wfm.topology.opentsdb.bolts.OpenTSDBFilterBolt;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class OpenTSDBFilterBoltTest {

    private static final String METRIC = "METRIC";
    private static final String CORRELATION_ID = "correlation_id";
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
        Mockito.reset(outputCollector);

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
        verify(outputCollector).ack(any(Tuple.class));
    }

    @Test
    public void shouldEmitMessageOnlyOnceBecauseOfInterval() throws Exception {
        mockTuple();

        target.prepare(Collections.emptyMap(), null, outputCollector);
        target.execute(tuple);

        mockTuple(TIMESTAMP + 59999);
        target.execute(tuple);

        verify(outputCollector, times(1)).emit(argumentCaptor.capture());
        verify(outputCollector).ack(any(Tuple.class));
    }

    private void mockTuple(long timestamp) throws Exception {
        InfoData infoData = new Datapoint(METRIC, timestamp, Collections.emptyMap(), VALUE);
        InfoMessage infoMessage = new InfoMessage(infoData, timestamp, CORRELATION_ID);
        when(tuple.getString(eq(0))).thenReturn(Utils.MAPPER.writeValueAsString(infoMessage));
    }

    private void mockTuple() throws Exception {
        mockTuple(TIMESTAMP);
    }
}
