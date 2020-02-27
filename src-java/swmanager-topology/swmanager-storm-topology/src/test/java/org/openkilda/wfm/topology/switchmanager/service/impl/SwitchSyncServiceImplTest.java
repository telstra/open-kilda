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

package org.openkilda.wfm.topology.switchmanager.service.impl;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.floodlight.api.request.factory.IngressFlowSegmentRequestFactory;
import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.command.flow.RemoveFlowForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.switches.MeterInfoEntry;
import org.openkilda.messaging.info.switches.SwitchSyncResponse;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.switchmanager.model.SwitchValidationContext;
import org.openkilda.wfm.topology.switchmanager.model.ValidateMetersResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateRulesResult;
import org.openkilda.wfm.topology.switchmanager.service.CommandBuilder;
import org.openkilda.wfm.topology.switchmanager.service.SwitchManagerCarrier;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.stream.Collectors;

@RunWith(MockitoJUnitRunner.class)
public class SwitchSyncServiceImplTest {

    private static SwitchId SWITCH_ID = new SwitchId(0x0000000000000001L);
    private static SwitchId INGRESS_SWITCH_ID = new SwitchId(0x0000000000000002L);
    private static SwitchId EGRESS_SWITCH_ID = new SwitchId(0x0000000000000002L);
    private static String FLOW_ID = "flow_id";
    private static String KEY = "KEY";
    private static long EXCESS_COOKIE = Cookie.buildForwardCookie(1).getValue();

    @Mock
    private SwitchManagerCarrier carrier;

    @Mock
    private CommandBuilder commandBuilder;

    private SwitchSyncServiceImpl service;

    private SwitchValidateRequest request;
    private FlowEntry flowEntry;

    private List<Long> missingRules;
    private List<Long> excessRules;
    private List<Long> misconfiguredRules;
    private List<MeterInfoEntry> excessMeters;

    private final Map<Long, FlowSegmentRequestFactory> flowSegmentRequestFactories = new HashMap<>();

    private final Queue<FlowSegmentRequest> speakerRequestsQueue = new ArrayDeque<>();

    @Before
    public void setUp() {
        service = new SwitchSyncServiceImpl(carrier, commandBuilder);

        request = SwitchValidateRequest.builder().switchId(SWITCH_ID).performSync(true).build();
        flowEntry = new FlowEntry(
                Cookie.buildForwardCookie(7).getValue(), 0, 0, 0, 0, "", 0, 0, 0, 0, null, null, null);

        missingRules = singletonList(flowEntry.getCookie());
        excessRules = emptyList();
        misconfiguredRules = emptyList();
        excessMeters = emptyList();

        doAnswer(invocation -> speakerRequestsQueue.offer(invocation.getArgument(1)))
                .when(carrier).sendCommandToSpeaker(any(String.class), any(FlowSegmentRequest.class));

        flowSegmentRequestFactories.put(flowEntry.getCookie(), IngressFlowSegmentRequestFactory.builder()
                .messageContext(new MessageContext())
                .metadata(new FlowSegmentMetadata(FLOW_ID, new Cookie(flowEntry.getCookie()), false))
                .endpoint(new FlowEndpoint(SWITCH_ID, 1, 50))
                .meterConfig(new MeterConfig(new MeterId(100), 10L))
                .egressSwitchId(EGRESS_SWITCH_ID)
                .islPort(2)
                .encapsulation(new FlowTransitEncapsulation(60, FlowEncapsulationType.TRANSIT_VLAN))
                .build());
    }

    @Test
    public void handleNothingRulesToSync() {
        missingRules = emptyList();

        processRequest(makeValidationContext());

        verify(carrier).response(eq(KEY), any(InfoMessage.class));
        verify(carrier).cancelTimeoutCallback(eq(KEY));

        verifyNoMoreInteractions(commandBuilder);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void handleMissingFlowSegmentRequest() {
        final Cookie segmentCookie = new Cookie(flowEntry.getCookie());

        FlowSegmentRequestFactory segmentRequestFactory = Mockito.mock(FlowSegmentRequestFactory.class);
        when(segmentRequestFactory.getSwitchId()).thenReturn(SWITCH_ID);
        when(segmentRequestFactory.getCookie()).thenReturn(segmentCookie);
        when(segmentRequestFactory.getFlowId()).thenReturn(FLOW_ID);
        when(segmentRequestFactory.makeInstallRequest(any()))
                .thenReturn(Optional.empty());

        service.handleSwitchSync(KEY, request, makeValidationContext().toBuilder()
                .expectedFlowSegments(Collections.singletonList(segmentRequestFactory))
                .build());

        ArgumentCaptor<ErrorMessage> errorCaptor = ArgumentCaptor.forClass(ErrorMessage.class);
        verify(carrier).cancelTimeoutCallback(eq(KEY));
        verify(carrier).response(eq(KEY), errorCaptor.capture());
        assertEquals(
                String.format(
                        "Unable to create install request for missing OF flow (sw=%s, cookie=%s, flowId=%s)",
                        SWITCH_ID, segmentCookie, FLOW_ID),
                errorCaptor.getValue().getData().getErrorMessage());

        verifyNoMoreInteractions(commandBuilder);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void doNothingWhenFsmNotFound() {
        service.handleSpeakerResponse(KEY, null);

        verifyZeroInteractions(carrier);
        verifyZeroInteractions(commandBuilder);
    }

    @Test
    public void handleRuleSyncSuccess() {
        service.handleSwitchSync(KEY, request, makeValidationContext());
        verify(carrier).sendCommandToSpeaker(eq(KEY), any(FlowSegmentRequest.class));
        verifyNoMoreInteractions(carrier);

        produceSpeakerResponses();

        verify(carrier).cancelTimeoutCallback(eq(KEY));
        verify(carrier).response(eq(KEY), any(InfoMessage.class));

        verifyNoMoreInteractions(commandBuilder);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void receiveRuleSyncTimeout() {
        service.handleSwitchSync(KEY, request, makeValidationContext());
        verify(carrier).sendCommandToSpeaker(eq(KEY), any(FlowSegmentRequest.class));

        service.handleTaskTimeout(KEY);

        verify(carrier).response(eq(KEY), any(ErrorMessage.class));

        verifyNoMoreInteractions(commandBuilder);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void receiveRuleSyncError() {
        service.handleSwitchSync(KEY, request, makeValidationContext());
        verify(carrier).sendCommandToSpeaker(eq(KEY), any(FlowSegmentRequest.class));
        verifyNoMoreInteractions(carrier);

        while (!speakerRequestsQueue.isEmpty()) {
            FlowSegmentRequest segmentRequest = speakerRequestsQueue.poll();
            service.handleSpeakerResponse(
                    KEY, makeFlowSegmentResponse(segmentRequest).success(false).build());
        }

        verify(carrier).cancelTimeoutCallback(eq(KEY));
        verify(carrier).response(eq(KEY), any(ErrorMessage.class));

        verifyNoMoreInteractions(commandBuilder);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void receiveMetersSyncError() {
        request = SwitchValidateRequest.builder().switchId(SWITCH_ID).performSync(true).removeExcess(true).build();
        missingRules = emptyList();
        excessMeters = singletonList(
                new MeterInfoEntry(EXCESS_COOKIE, EXCESS_COOKIE, FLOW_ID, 0L, 0L, new String[]{}, null, null));

        service.handleSwitchSync(KEY, request, makeValidationContext());
        verify(carrier).sendCommandToSpeaker(eq(KEY), any(CommandData.class));

        ErrorMessage errorMessage = getErrorMessage();
        service.handleTaskError(KEY, errorMessage);

        verify(carrier).cancelTimeoutCallback(eq(KEY));
        verify(carrier).response(eq(KEY), any(ErrorMessage.class));

        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void handleNothingToSyncWithExcess() {
        request = SwitchValidateRequest.builder().switchId(SWITCH_ID).performSync(true).removeExcess(true).build();
        missingRules = emptyList();

        processRequest(makeValidationContext());

        verify(carrier).cancelTimeoutCallback(eq(KEY));
        verify(carrier).response(eq(KEY), any(InfoMessage.class));

        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void handleSyncExcess() {
        request = SwitchValidateRequest.builder().switchId(SWITCH_ID).performSync(true).removeExcess(true).build();

        excessRules = singletonList(EXCESS_COOKIE);
        excessMeters = singletonList(
                new MeterInfoEntry(EXCESS_COOKIE, EXCESS_COOKIE, FLOW_ID, 0L, 0L, new String[]{}, null, null));

        RemoveFlow removeFlow = RemoveFlow.builder()
                .transactionId(UUID.randomUUID())
                .flowId(FLOW_ID)
                .cookie(EXCESS_COOKIE)
                .switchId(SWITCH_ID)
                .meterId(EXCESS_COOKIE)
                .build();
        when(commandBuilder.buildCommandsToRemoveExcessRules(eq(SWITCH_ID), any(), any()))
                .thenReturn(singletonList(removeFlow));

        service.handleSwitchSync(KEY, request, makeValidationContext());

        verify(commandBuilder).buildCommandsToRemoveExcessRules(
                eq(SWITCH_ID), eq(singletonList(flowEntry)), eq(excessRules));
        verify(carrier).sendCommandToSpeaker(eq(KEY), any(FlowSegmentRequest.class));
        verify(carrier).sendCommandToSpeaker(eq(KEY), any(RemoveFlowForSwitchManagerRequest.class));
        verifyNoMoreInteractions(carrier);
        reset(carrier);

        produceSpeakerResponses();
        service.handleRemoveRulesResponse(KEY);
        service.handleRemoveMetersResponse(KEY);

        verify(carrier).sendCommandToSpeaker(eq(KEY), any(CommandData.class));

        verify(carrier).cancelTimeoutCallback(eq(KEY));
        verify(carrier).response(eq(KEY), any(InfoMessage.class));

        verifyNoMoreInteractions(commandBuilder);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void handleSyncOnlyExcessMeters() {
        request = SwitchValidateRequest.builder().switchId(SWITCH_ID).performSync(true).removeExcess(true).build();
        missingRules = emptyList();
        excessMeters = singletonList(
                new MeterInfoEntry(EXCESS_COOKIE, EXCESS_COOKIE, FLOW_ID, 0L, 0L, new String[]{}, null, null));

        processRequest(makeValidationContext());

        verify(carrier).sendCommandToSpeaker(eq(KEY), any(CommandData.class));
        service.handleRemoveMetersResponse(KEY);

        verify(carrier).cancelTimeoutCallback(eq(KEY));
        verify(carrier).response(eq(KEY), any(InfoMessage.class));

        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void handleSyncWhenNotProcessMeters() {
        request = SwitchValidateRequest.builder().switchId(SWITCH_ID).performSync(true).removeExcess(true).build();

        processRequest(makeValidationContext().toBuilder()
                .metersValidationReport(null)
                .build());

        verify(carrier).sendCommandToSpeaker(eq(KEY), any(FlowSegmentRequest.class));
        verify(carrier).cancelTimeoutCallback(eq(KEY));

        ArgumentCaptor<InfoMessage> responseCaptor = ArgumentCaptor.forClass(InfoMessage.class);
        verify(carrier).response(eq(KEY), responseCaptor.capture());
        assertNull(((SwitchSyncResponse) responseCaptor.getValue().getData()).getMeters());

        verifyNoMoreInteractions(carrier);
    }

    private void processRequest(SwitchValidationContext validationContext) {
        service.handleSwitchSync(KEY, request, validationContext);
        produceSpeakerResponses();
    }

    private void produceSpeakerResponses() {
        while (! speakerRequestsQueue.isEmpty()) {
            FlowSegmentRequest request = speakerRequestsQueue.poll();

            SpeakerFlowSegmentResponse response = makeFlowSegmentResponse(request).build();
            service.handleSpeakerResponse(KEY, response);
        }
    }

    private SwitchValidationContext makeValidationContext() {
        List<FlowSegmentRequestFactory> expectedFlowSegments = missingRules.stream()
                .map(flowSegmentRequestFactories::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return SwitchValidationContext.builder(request.getSwitchId())
                .actualOfFlows(Collections.singletonList(flowEntry))
                .expectedFlowSegments(expectedFlowSegments)
                .ofFlowsValidationReport(new ValidateRulesResult(
                        missingRules, singletonList(flowEntry.getCookie()), excessRules, misconfiguredRules))
                .metersValidationReport(new ValidateMetersResult(emptyList(), emptyList(), emptyList(), excessMeters))
                .build();
    }

    private SpeakerFlowSegmentResponse.SpeakerFlowSegmentResponseBuilder makeFlowSegmentResponse(
            FlowSegmentRequest request) {
        return SpeakerFlowSegmentResponse.builder()
                .commandId(request.getCommandId())
                .messageContext(request.getMessageContext())
                .metadata(request.getMetadata())
                .switchId(request.getSwitchId())
                .success(true);
    }

    private ErrorMessage getErrorMessage() {
        return new ErrorMessage(new ErrorData(ErrorType.INTERNAL_ERROR, "message", "description"),
                System.currentTimeMillis(), KEY);
    }
}
