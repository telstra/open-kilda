/* Copyright 2021 Telstra Open Source
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

package org.openkilda.floodlight.command.rulemanager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;
import org.openkilda.floodlight.service.session.Session;
import org.openkilda.floodlight.service.session.SessionService;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;

import com.google.common.util.concurrent.SettableFuture;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.MockitoJUnitRunner;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFFlowStatsRequest;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RunWith(MockitoJUnitRunner.class)
public class OfBatchExecutorTest {

    private static final MessageContext MESSAGE_CONTEXT = new MessageContext("correlation-id");
    private static final SwitchId SWITCH_ID = new SwitchId("1");

    IOFSwitch sw = mock(IOFSwitch.class);
    KafkaUtilityService kafkaUtilityService = mock(KafkaUtilityService.class);
    IKafkaProducerService kafkaProducerService = mock(IKafkaProducerService.class);
    SessionService sessionService = mock(SessionService.class);
    IOFSwitchService switchService = mock(IOFSwitchService.class);

    private final OfBatchHolder holder = new OfBatchHolder(switchService, MESSAGE_CONTEXT,
            UUID.randomUUID(), SWITCH_ID);
    private final OfBatchExecutor executor = OfBatchExecutor.builder()
            .iofSwitch(sw)
            .kafkaUtilityService(kafkaUtilityService)
            .kafkaProducerService(kafkaProducerService)
            .sessionService(sessionService)
            .messageContext(MESSAGE_CONTEXT)
            .holder(holder)
            .switchFeatures(Collections.emptySet())
            .kafkaKey("kafka-key")
            .replyTo("flow-hs")
            .build();

    @Test
    public void shouldSendSuccessResponse() {
        when(switchService.getSwitch(DatapathId.of(SWITCH_ID.toLong()))).thenReturn(sw);
        when(sw.getOFFactory()).thenReturn(new OFFactoryVer13());
        when(sw.getId()).thenReturn(DatapathId.of(SWITCH_ID.toLong()));
        Session session = mock(Session.class);
        when(sessionService.open(MESSAGE_CONTEXT, sw)).thenReturn(session);
        when(session.write(any(OFMessage.class))).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        OFFlowStatsReply reply = mock(OFFlowStatsReply.class);
        when(reply.getEntries()).thenReturn(Collections.emptyList());
        SettableFuture<List<OFFlowStatsReply>> future = SettableFuture.create();
        future.set(Collections.singletonList(reply));
        when(sw.writeStatsRequest(any(OFFlowStatsRequest.class))).thenReturn(future);
        KafkaChannel kafkaChannel = mock(KafkaChannel.class);
        when(kafkaChannel.getSpeakerSwitchManagerResponseTopic()).thenReturn("kafka-topic");
        when(kafkaUtilityService.getKafkaChannel()).thenReturn(kafkaChannel);

        holder.addDeleteFlow(FlowSpeakerData.builder()
                .switchId(SWITCH_ID)
                .cookie(new Cookie(1))
                .priority(2)
                .table(OfTable.INPUT)
                .instructions(Instructions.builder().build())
                .build(), SWITCH_ID);

        executor.executeBatch();

        ArgumentCaptor<SpeakerCommandResponse> captor = ArgumentCaptor.forClass(SpeakerCommandResponse.class);
        verify(kafkaProducerService).sendMessageAndTrack(any(String.class), any(String.class),
                captor.capture());
        assertTrue(captor.getValue().isSuccess());

        verifyNoMoreInteractions(kafkaProducerService);
    }

    @Test
    public void shouldSendFailedResponse() {
        when(switchService.getSwitch(DatapathId.of(SWITCH_ID.toLong()))).thenReturn(sw);
        when(sw.getOFFactory()).thenReturn(new OFFactoryVer13());
        Session session = mock(Session.class);
        when(sessionService.open(MESSAGE_CONTEXT, sw)).thenReturn(session);
        CompletableFuture<Optional<OFMessage>> completableFuture = new CompletableFuture<>();
        completableFuture.completeExceptionally(new Exception("test exception"));
        when(session.write(any(OFMessage.class))).thenReturn(completableFuture);
        OFFlowStatsReply reply = mock(OFFlowStatsReply.class);
        SettableFuture<List<OFFlowStatsReply>> future = SettableFuture.create();
        future.set(Collections.singletonList(reply));
        KafkaChannel kafkaChannel = mock(KafkaChannel.class);
        when(kafkaChannel.getSpeakerSwitchManagerResponseTopic()).thenReturn("kafka-topic");
        when(kafkaUtilityService.getKafkaChannel()).thenReturn(kafkaChannel);

        holder.addDeleteFlow(FlowSpeakerData.builder()
                .switchId(SWITCH_ID)
                .cookie(new Cookie(1))
                .priority(2)
                .table(OfTable.INPUT)
                .instructions(Instructions.builder().build())
                .build(), SWITCH_ID);

        executor.executeBatch();

        ArgumentCaptor<SpeakerCommandResponse> captor = ArgumentCaptor.forClass(SpeakerCommandResponse.class);
        verify(kafkaProducerService).sendMessageAndTrack(any(String.class), any(String.class),
                captor.capture());
        assertFalse(captor.getValue().isSuccess());

        verifyNoMoreInteractions(kafkaProducerService);
    }
}
