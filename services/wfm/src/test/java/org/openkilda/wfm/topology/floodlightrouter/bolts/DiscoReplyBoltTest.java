/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.floodlightrouter.bolts;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.DiscoPacketSendingConfirmation;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.floodlightrouter.Stream;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class DiscoReplyBoltTest {

    private DiscoReplyBolt subject;

    private static final SwitchId switchAlpha = new SwitchId(1);

    private static final String REGION_ONE = "1";

    public static final String FIELD_ID_KEY = "key";
    public static final String FIELD_ID_MESSAGE = "message";
    private static final Fields STREAM_CONSUMER_FIELDS = new Fields(FIELD_ID_KEY, FIELD_ID_MESSAGE);

    @Mock
    private TopologyContext topologyContext;

    @Mock
    private OutputCollector outputCollector;

    private final Map<String, String> topologyConfig = Collections.emptyMap();

    @Before
    public void setUp() {
        subject = new DiscoReplyBolt(Stream.KILDA_TOPO_DISCO);
        when(topologyContext.getThisTaskId()).thenReturn(1);
        subject.prepare(topologyConfig, topologyContext, outputCollector);

    }

    @Test
    public void verifyStreamDefinition() {
        OutputFieldsDeclarer streamManager = mock(OutputFieldsDeclarer.class);
        subject.declareOutputFields(streamManager);

        verify(streamManager).declareStream(Stream.KILDA_TOPO_DISCO, STREAM_CONSUMER_FIELDS);
        verify(streamManager).declareStream(Stream.DISCO_REPLY, STREAM_CONSUMER_FIELDS);
    }

    @Test
    public void verifySpeakerToConsumerTupleConsistency() throws Exception {
        InfoMessage discoveryConfirmation = new InfoMessage(
                new DiscoPacketSendingConfirmation(new NetworkEndpoint(switchAlpha, 1), 1L),
                3L, "discovery-confirmation", REGION_ONE);
        Tuple tuple = mock(Tuple.class);
        when(tuple.getStringByField(FIELD_ID_KEY)).thenReturn(switchAlpha.toString());
        when(tuple.getValueByField(FIELD_ID_MESSAGE)).thenReturn(discoveryConfirmation);
        subject.handleInput(tuple);
        ArgumentCaptor<Values> discoReplyValuesCaptor = ArgumentCaptor.forClass(Values.class);

        verify(outputCollector).emit(eq(Stream.DISCO_REPLY),
                discoReplyValuesCaptor.capture());

        assertEquals(switchAlpha.toString(), discoReplyValuesCaptor.getValue().get(0));
        assertEquals(discoveryConfirmation, discoReplyValuesCaptor.getValue().get(1));

        ArgumentCaptor<Values> topoDiscoCaptor = ArgumentCaptor.forClass(Values.class);
        verify(outputCollector).emit(eq(Stream.KILDA_TOPO_DISCO),
                eq(tuple),
                topoDiscoCaptor.capture());
        assertEquals(switchAlpha.toString(), topoDiscoCaptor.getValue().get(0));
        assertEquals(discoveryConfirmation, topoDiscoCaptor.getValue().get(1));


    }
}
