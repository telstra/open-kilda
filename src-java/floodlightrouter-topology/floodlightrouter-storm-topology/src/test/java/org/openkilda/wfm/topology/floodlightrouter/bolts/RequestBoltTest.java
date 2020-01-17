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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.floodlightrouter.Stream;
import org.openkilda.wfm.topology.floodlightrouter.service.SwitchMapping;

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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RunWith(MockitoJUnitRunner.class)
public class RequestBoltTest {

    private RequestBolt subject;

    private static final SwitchId switchAlpha = new SwitchId(1);
    private static final SwitchId switchBeta = new SwitchId(2);

    private static final String REGION_ONE = "1";
    private static final String REGION_TWO = "2";

    public static final String FIELD_ID_KEY = "key";
    public static final String FIELD_ID_MESSAGE = "message";
    private static final Fields STREAM_SPEAKER_FIELDS = new Fields(FIELD_ID_KEY, FIELD_ID_MESSAGE);

    @Mock
    private TopologyContext topologyContext;

    @Mock
    private OutputCollector outputCollector;

    private final Map<String, String> topologyConfig = Collections.emptyMap();

    @Before
    public void setUp() {
        Set<String> regions = new HashSet<>();
        regions.add(REGION_ONE);
        regions.add(REGION_TWO);
        subject = new RequestBolt(Stream.SPEAKER_DISCO, regions);
        when(topologyContext.getThisTaskId()).thenReturn(1);
        subject.prepare(topologyConfig, topologyContext, outputCollector);

    }

    @Test
    public void verifyStreamDefinition() {
        OutputFieldsDeclarer streamManager = mock(OutputFieldsDeclarer.class);
        subject.declareOutputFields(streamManager);

        verify(streamManager).declareStream(Stream.formatWithRegion(Stream.SPEAKER_DISCO, REGION_ONE),
                STREAM_SPEAKER_FIELDS);
        verify(streamManager).declareStream(Stream.formatWithRegion(Stream.SPEAKER_DISCO, REGION_TWO),
                STREAM_SPEAKER_FIELDS);
    }

    @Test
    public void verifyConsumerToSpeakerTupleConsistency() throws Exception {
        SwitchMapping switchMapping = new SwitchMapping(switchAlpha, REGION_ONE);
        Tuple notificationTuple = mock(Tuple.class);
        when(notificationTuple.getValueByField(FIELD_ID_MESSAGE)).thenReturn(switchMapping);
        when(notificationTuple.getSourceStreamId()).thenReturn(Stream.REGION_NOTIFICATION);
        subject.handleInput(notificationTuple);
        assertTrue(subject.switchTracker.getMapping().containsKey(switchAlpha));
        CommandMessage discoCommand = new CommandMessage(
                new DiscoverIslCommandData(switchAlpha, 1, 1L),
                3L, "discovery-confirmation");
        Tuple tuple = mock(Tuple.class);
        when(tuple.getStringByField(FIELD_ID_KEY)).thenReturn(switchAlpha.toString());
        when(tuple.getValueByField(FIELD_ID_MESSAGE)).thenReturn(discoCommand);
        subject.handleInput(tuple);
        ArgumentCaptor<Values> discoCommandValuesCaptor = ArgumentCaptor.forClass(Values.class);
        verify(outputCollector).emit(eq(Stream.formatWithRegion(Stream.SPEAKER_DISCO, REGION_ONE)),
                eq(tuple),
                discoCommandValuesCaptor.capture());

        assertEquals(switchAlpha.toString(), discoCommandValuesCaptor.getValue().get(0));
        assertEquals(discoCommand, discoCommandValuesCaptor.getValue().get(1));
    }

    @Test
    public void verifyConsumerToSpeakerTupleFails() throws Exception {
        SwitchMapping switchMapping = new SwitchMapping(switchBeta, REGION_ONE);
        Tuple notificationTuple = mock(Tuple.class);
        when(notificationTuple.getValueByField(FIELD_ID_MESSAGE)).thenReturn(switchMapping);
        when(notificationTuple.getSourceStreamId()).thenReturn(Stream.REGION_NOTIFICATION);
        subject.handleInput(notificationTuple);
        assertTrue(subject.switchTracker.getMapping().containsKey(switchBeta));
        assertFalse(subject.switchTracker.getMapping().containsKey(switchAlpha));
        CommandMessage discoCommand = new CommandMessage(
                new DiscoverIslCommandData(switchAlpha, 1, 1L),
                3L, "discovery-confirmation");
        Tuple tuple = mock(Tuple.class);
        when(tuple.getValueByField(FIELD_ID_MESSAGE)).thenReturn(discoCommand);
        subject.handleInput(tuple);

        verifyZeroInteractions(outputCollector);
    }
}
