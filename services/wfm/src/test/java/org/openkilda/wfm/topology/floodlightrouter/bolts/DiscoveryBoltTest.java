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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.DiscoPacketSendingConfirmation;
import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FeatureTogglesRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.floodlightrouter.ComponentType;
import org.openkilda.wfm.topology.floodlightrouter.Stream;
import org.openkilda.wfm.topology.floodlightrouter.service.RouterService;
import org.openkilda.wfm.topology.utils.MessageTranslator;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Value;
import org.apache.storm.state.InMemoryKeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.TupleImpl;
import org.apache.storm.tuple.Values;
import org.apache.storm.utils.Utils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RunWith(MockitoJUnitRunner.class)
public class DiscoveryBoltTest {
    private static final long ALIVE_INTERVAL = 1L;
    private static final long ALIVE_TIMEOUT = 5L;
    private static final long DUMP_INTERVAL = 60L;
    
    private static final String REGION_ONE = "1";
    private static final String REGION_TWO = "2";

    private static final SwitchId switchAlpha = new SwitchId(1);

    public static final String FIELD_ID_KEY = "key";
    public static final String FIELD_ID_MESSAGE = "message";
    public static final String FIELD_ID_CONTEXT = "context";
    private static final Fields STREAM_SPEAKER_FIELDS = new Fields(FIELD_ID_KEY, FIELD_ID_MESSAGE);
    private static final Fields STREAM_CONSUMER_FIELDS = STREAM_SPEAKER_FIELDS;
    private static final Fields STREAM_REGION_NOTIFICATION_FIELDS = new Fields(FIELD_ID_MESSAGE, FIELD_ID_CONTEXT);

    private static final InMemoryKeyValueState<String, RouterService> subjectStateStorage
            = new InMemoryKeyValueState<>();
    private static final Map<String, Integer> componentNameToTaskId = new HashMap<>();
    private static final Map<Integer, String> taskIdToComponentName = new HashMap<>();
    private static final Map<StreamDescriptor, Fields> streamFields = new HashMap<>();

    static {
        int idx = 0;
        componentNameToTaskId.put(ComponentType.KILDA_TOPO_DISCO_KAFKA_SPOUT, idx++);
        componentNameToTaskId.put(ComponentType.SPEAKER_DISCO_KAFKA_SPOUT, idx++);

        componentNameToTaskId.forEach((key, value) -> taskIdToComponentName.put(value, key));

        Fields kafkaSpoutFields = MessageTranslator.STREAM_FIELDS;
        streamFields.put(new StreamDescriptor(ComponentType.KILDA_TOPO_DISCO_KAFKA_SPOUT, Utils.DEFAULT_STREAM_ID),
                         kafkaSpoutFields);
        streamFields.put(new StreamDescriptor(ComponentType.SPEAKER_DISCO_KAFKA_SPOUT, Utils.DEFAULT_STREAM_ID),
                         kafkaSpoutFields);
    }

    @Mock
    private PersistenceManager persistenceManager;

    @Mock
    private FeatureTogglesRepository featureTogglesRepository;

    @Mock
    private TopologyContext topologyContext;

    @Mock
    private OutputCollector outputCollector;

    private final Map<String, String> topologyConfig = Collections.emptyMap();

    private DiscoveryBolt subject;

    @Before
    public void setUp() {
        Set<String> regions = new HashSet<>();
        regions.add(REGION_ONE);
        regions.add(REGION_TWO);

        RepositoryFactory repositoryFactory = Mockito.mock(RepositoryFactory.class);
        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);
        when(repositoryFactory.createFeatureTogglesRepository()).thenReturn(featureTogglesRepository);

        doAnswer(invocation -> taskIdToComponentName.get(invocation.getArgument(0)))
                .when(topologyContext).getComponentId(any(Integer.class));
        doAnswer(invocation -> streamFields.get(new StreamDescriptor(invocation.getArgument(0),
                                                                     invocation.getArgument(1))))
                .when(topologyContext).getComponentOutputFields(any(String.class), any(String.class));

        subject = new DiscoveryBolt(persistenceManager, regions,
                                    ALIVE_TIMEOUT, ALIVE_INTERVAL, DUMP_INTERVAL);
        subject.prepare(topologyConfig, topologyContext, outputCollector);
    }

    @Test
    public void verifyStreamDefinition() {
        OutputFieldsDeclarer streamManager = Mockito.mock(OutputFieldsDeclarer.class);
        subject.declareOutputFields(streamManager);

        verify(streamManager).declareStream(Stream.formatWithRegion(Stream.SPEAKER_DISCO, REGION_ONE),
                                            STREAM_SPEAKER_FIELDS);
        verify(streamManager).declareStream(Stream.formatWithRegion(Stream.SPEAKER_DISCO, REGION_TWO),
                                            STREAM_SPEAKER_FIELDS);
        verify(streamManager).declareStream(Stream.KILDA_TOPO_DISCO, STREAM_CONSUMER_FIELDS);
        verify(streamManager).declareStream(Stream.REGION_NOTIFICATION, STREAM_REGION_NOTIFICATION_FIELDS);
    }

    @Test
    public void verifyConsumerToSpeakerTupleFormat() {
        injectSwitch();

        CommandMessage discoveryRequest = new CommandMessage(
                new DiscoverIslCommandData(switchAlpha, 1, 1L), 2, "discovery-request");
        Tuple discoveryRequestTuple = makeTuple(
                makeConsumerTuple("key", discoveryRequest),
                ComponentType.SPEAKER_DISCO_KAFKA_SPOUT, Utils.DEFAULT_STREAM_ID);

        subject.doWork(discoveryRequestTuple);

        verify(outputCollector).emit(eq(Stream.formatWithRegion(Stream.SPEAKER_DISCO, REGION_ONE)),
                                     eq(discoveryRequestTuple),
                                     argThat(this::verifyConsumerTupleFormat));

        verify(outputCollector).ack(any(Tuple.class));
        verifyNoMoreInteractions(outputCollector);

    }

    @Test
    public void verifySpeakerToConsumerTupleFormat() {
        injectSwitch();

        InfoMessage discoveryConfirmation = new InfoMessage(
                new DiscoPacketSendingConfirmation(new NetworkEndpoint(switchAlpha, 1), 1L),
                3L, "discovery-confirmation", REGION_ONE);
        Tuple discoveryConfirmationTuple = makeTuple(
                makeSpeakerTuple("key", discoveryConfirmation),
                ComponentType.KILDA_TOPO_DISCO_KAFKA_SPOUT, Utils.DEFAULT_STREAM_ID);

        subject.doWork(discoveryConfirmationTuple);

        verify(outputCollector).emit(eq(Stream.KILDA_TOPO_DISCO), eq(discoveryConfirmationTuple),
                                     argThat(this::verifyConsumerTupleFormat));
        verify(outputCollector).ack(any(Tuple.class));
        verifyNoMoreInteractions(outputCollector);

    }

    @Test
    public void verifyConsumerToSpeakerKeyPropagation() throws JsonProcessingException {
        injectSwitch();

        CommandMessage discoveryRequest = new CommandMessage(
                new DiscoverIslCommandData(switchAlpha, 1, 2L), 4L, "discovery-request");
        String requestKey = "discovery-request";
        Tuple discoveryRequestTuple2 = makeTuple(
                makeConsumerTuple(requestKey, discoveryRequest),
                ComponentType.SPEAKER_DISCO_KAFKA_SPOUT, Utils.DEFAULT_STREAM_ID);

        subject.doWork(discoveryRequestTuple2);

        verify(outputCollector).emit(eq(Stream.formatWithRegion(Stream.SPEAKER_DISCO, REGION_ONE)),
                                     eq(discoveryRequestTuple2),
                                     argThat(values -> verifyConsumerTupleFormat(values, requestKey)));

        verify(outputCollector).ack(any(Tuple.class));
        verifyNoMoreInteractions(outputCollector);
    }

    @Test
    public void verifySpeakerToConsumerKeyPropagation() {
        injectSwitch();

        InfoMessage discoveryConfirmation = new InfoMessage(
                new DiscoPacketSendingConfirmation(new NetworkEndpoint(switchAlpha, 1), 1L),
                3L, "discovery-confirmation", REGION_ONE);
        String responseKey = "discovery-confirmation";
        Tuple discoveryConfirmationTuple = makeTuple(
                makeSpeakerTuple(responseKey, discoveryConfirmation),
                ComponentType.KILDA_TOPO_DISCO_KAFKA_SPOUT, Utils.DEFAULT_STREAM_ID);

        subject.doWork(discoveryConfirmationTuple);

        verify(outputCollector).emit(eq(Stream.KILDA_TOPO_DISCO), eq(discoveryConfirmationTuple),
                                     argThat(values -> verifyConsumerTupleFormat(values, responseKey)));
        verify(outputCollector).ack(any(Tuple.class));
        verifyNoMoreInteractions(outputCollector);
    }

    private void injectSwitch() {
        // populate switch-to-region map
        InfoMessage switchInfo = new InfoMessage(new SwitchInfoData(switchAlpha, SwitchChangeType.ACTIVATED),
                                                 1, "init-sw-map", REGION_ONE);
        subject.doWork(makeTuple(makeSpeakerTuple(null, switchInfo),
                                 ComponentType.KILDA_TOPO_DISCO_KAFKA_SPOUT, Utils.DEFAULT_STREAM_ID));
        reset(outputCollector);
    }

    private boolean verifySpeakerTupleFormat(List<Object> payload) {
        return verifySpeakerTupleFormat(payload, null);
    }

    private boolean verifySpeakerTupleFormat(List<Object> payload, String expectKey) {
        return verifyConsumerTupleFormat(payload, expectKey);
    }

    private boolean verifyConsumerTupleFormat(List<Object> payload) {
        return verifyConsumerTupleFormat(payload, null);
    }

    private boolean verifyConsumerTupleFormat(List<Object> payload, String expectKey) {
        return payload.size() == 2 && isNullOrString(payload.get(0), expectKey) && isNullOrMessage(payload.get(1));
    }

    private boolean isNullOrString(Object value, String expect) {
        if (expect != null) {
            return expect.equals(value);
        }
        return value == null || value instanceof String;
    }

    private boolean isNullOrMessage(Object value) {
        return value == null || value instanceof Message;
    }

    private Tuple makeTuple(Values payload, String component, String stream) {
        Integer taskId = componentNameToTaskId.get(component);
        return new TupleImpl(topologyContext, payload, taskId, stream);
    }

    private Values makeConsumerTuple(String key, Message payload) {
        return makeSpeakerTuple(key, payload);
    }

    private Values makeSpeakerTuple(String key, Message payload) {
        return new Values(key, payload, new CommandContext());
    }

    @Value
    private static class StreamDescriptor {
        private String componentName;
        private String streamName;
    }
}
