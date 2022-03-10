/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.switchmanager.service;

import static com.google.common.collect.Sets.newHashSet;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.MessageCookie;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.flow.InstallFlowForSwitchManagerRequest;
import org.openkilda.messaging.command.flow.InstallIngressFlow;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.command.flow.RemoveFlowForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.DeleterMeterForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowInstallResponse;
import org.openkilda.messaging.info.flow.FlowRemoveResponse;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.switches.DeleteMeterResponse;
import org.openkilda.messaging.info.switches.MeterInfoEntry;
import org.openkilda.messaging.info.switches.SwitchSyncResponse;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.OutputVlanType;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.wfm.error.MessageDispatchException;
import org.openkilda.wfm.error.UnexpectedInputException;
import org.openkilda.wfm.topology.switchmanager.model.ValidateGroupsResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateLogicalPortsResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateMetersResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateRulesResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidationResult;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class SwitchSyncServiceTest {

    private static SwitchId SWITCH_ID = new SwitchId(0x0000000000000001L);
    private static SwitchId INGRESS_SWITCH_ID = new SwitchId(0x0000000000000002L);
    private static SwitchId EGRESS_SWITCH_ID = new SwitchId(0x0000000000000002L);
    private static String FLOW_ID = "flow_id";
    private static String KEY = "KEY";
    private static long EXCESS_COOKIE = new FlowSegmentCookie(FlowPathDirection.FORWARD, 1).getValue();

    @Mock
    private SwitchManagerCarrier carrier;

    @Mock
    private CommandBuilder commandBuilder;

    private SwitchSyncService service;

    private SwitchValidateRequest request;
    private FlowEntry flowEntry;

    private List<Long> missingRules;
    private List<Long> excessRules;
    private List<Long> misconfiguredRules;
    private List<MeterInfoEntry> excessMeters;

    @Before
    public void setUp() {
        service = new SwitchSyncService(carrier, commandBuilder);

        request = SwitchValidateRequest.builder().switchId(SWITCH_ID).performSync(true).build();
        flowEntry = new FlowEntry(
                new FlowSegmentCookie(FlowPathDirection.FORWARD, 7).getValue(),
                0, 0, 0, 0, "", 0, 0, 0, 0, null, null, null);

        InstallIngressFlow installingRule = new InstallIngressFlow(UUID.randomUUID(), FLOW_ID, flowEntry.getCookie(),
                SWITCH_ID, 1, 2, 50, 0,
                60, FlowEncapsulationType.TRANSIT_VLAN, OutputVlanType.POP, 10L,
                100L, EGRESS_SWITCH_ID, false, false, false, null);
        when(commandBuilder.buildCommandsToSyncMissingRules(eq(SWITCH_ID), any()))
                .thenReturn(singletonList(installingRule));

        missingRules = singletonList(flowEntry.getCookie());
        excessRules = emptyList();
        misconfiguredRules = emptyList();
        excessMeters = emptyList();
    }

    @Test
    public void handleNothingRulesToSync() {
        missingRules = emptyList();

        service.handleSwitchSync(KEY, request, makeValidationResult());

        verify(carrier).response(eq(KEY), any(InfoMessage.class));
        verify(carrier).cancelTimeoutCallback(eq(KEY));

        verifyNoMoreInteractions(commandBuilder);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void handleCommandBuilderMissingRulesException() {
        String errorMessage = "test error";
        when(commandBuilder.buildCommandsToSyncMissingRules(eq(SWITCH_ID), any()))
                .thenThrow(new IllegalArgumentException(errorMessage));

        service.handleSwitchSync(KEY, request, makeValidationResult());

        verify(commandBuilder).buildCommandsToSyncMissingRules(eq(SWITCH_ID), eq(missingRules));
        ArgumentCaptor<ErrorMessage> errorCaptor = ArgumentCaptor.forClass(ErrorMessage.class);
        verify(carrier).cancelTimeoutCallback(eq(KEY));
        verify(carrier).response(eq(KEY), errorCaptor.capture());
        assertEquals(errorMessage, errorCaptor.getValue().getData().getErrorMessage());

        verifyNoMoreInteractions(commandBuilder);
        verifyNoMoreInteractions(carrier);
    }

    @Test(expected = MessageDispatchException.class)
    public void reportErrorInCaseOfMissingHandler() throws UnexpectedInputException, MessageDispatchException {
        service.dispatchWorkerMessage(new FlowInstallResponse(), new MessageCookie("dummy"));
    }

    @Test
    public void handleRuleSyncSuccess() throws UnexpectedInputException, MessageDispatchException {
        service.handleSwitchSync(KEY, request, makeValidationResult());

        verify(commandBuilder).buildCommandsToSyncMissingRules(eq(SWITCH_ID), eq(missingRules));
        verify(carrier).sendCommandToSpeaker(eq(KEY), any(CommandData.class));

        service.dispatchWorkerMessage(new FlowInstallResponse(), new MessageCookie(KEY));

        verify(carrier).cancelTimeoutCallback(eq(KEY));
        verify(carrier).response(eq(KEY), any(InfoMessage.class));

        verifyNoMoreInteractions(commandBuilder);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void receiveRuleSyncTimeout() throws MessageDispatchException {
        service.handleSwitchSync(KEY, request, makeValidationResult());

        verify(commandBuilder).buildCommandsToSyncMissingRules(eq(SWITCH_ID), eq(missingRules));
        verify(carrier).sendCommandToSpeaker(eq(KEY), any(CommandData.class));

        service.timeout(new MessageCookie(KEY));

        verify(carrier).response(eq(KEY), any(ErrorMessage.class));

        verifyNoMoreInteractions(commandBuilder);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void receiveRuleSyncError() throws MessageDispatchException {
        service.handleSwitchSync(KEY, request, makeValidationResult());

        verify(commandBuilder).buildCommandsToSyncMissingRules(eq(SWITCH_ID), eq(missingRules));
        verify(carrier).sendCommandToSpeaker(eq(KEY), any(InstallFlowForSwitchManagerRequest.class));

        ErrorMessage errorMessage = getErrorMessage();
        service.dispatchWorkerMessage(errorMessage.getData(), new MessageCookie(KEY));

        verify(carrier).cancelTimeoutCallback(eq(KEY));
        verify(carrier).response(eq(KEY), any(ErrorMessage.class));

        verifyNoMoreInteractions(commandBuilder);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void receiveMetersSyncError() throws MessageDispatchException {
        request = SwitchValidateRequest.builder().switchId(SWITCH_ID).performSync(true).removeExcess(true).build();
        missingRules = emptyList();
        excessMeters = singletonList(
                new MeterInfoEntry(EXCESS_COOKIE, EXCESS_COOKIE, FLOW_ID, 0L, 0L, new String[]{}, null, null));

        service.handleSwitchSync(KEY, request, makeValidationResult());
        verify(carrier).sendCommandToSpeaker(eq(KEY), any(CommandData.class));

        service.dispatchWorkerMessage(getErrorMessage().getData(), new MessageCookie(KEY));

        verify(carrier).cancelTimeoutCallback(eq(KEY));
        verify(carrier).response(eq(KEY), any(ErrorMessage.class));

        verifyNoMoreInteractions(commandBuilder);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void handleNothingToSyncWithExcess() {
        request = SwitchValidateRequest.builder().switchId(SWITCH_ID).performSync(true).removeExcess(true).build();
        missingRules = emptyList();

        service.handleSwitchSync(KEY, request, makeValidationResult());

        verify(carrier).cancelTimeoutCallback(eq(KEY));
        verify(carrier).response(eq(KEY), any(InfoMessage.class));

        verifyNoMoreInteractions(commandBuilder);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void handleSyncExcess() throws UnexpectedInputException, MessageDispatchException {
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

        service.handleSwitchSync(KEY, request, makeValidationResult());

        verify(commandBuilder).buildCommandsToSyncMissingRules(eq(SWITCH_ID), eq(missingRules));
        verify(commandBuilder).buildCommandsToRemoveExcessRules(
                eq(SWITCH_ID), eq(singletonList(flowEntry)), eq(excessRules));
        verify(carrier).sendCommandToSpeaker(eq(KEY), any(DeleterMeterForSwitchManagerRequest.class));

        service.dispatchWorkerMessage(new DeleteMeterResponse(true), new MessageCookie(KEY));
        verify(carrier).sendCommandToSpeaker(eq(KEY), any(InstallFlowForSwitchManagerRequest.class));
        verify(carrier).sendCommandToSpeaker(eq(KEY), any(RemoveFlowForSwitchManagerRequest.class));

        service.dispatchWorkerMessage(new FlowInstallResponse(), new MessageCookie(KEY));
        service.dispatchWorkerMessage(new FlowRemoveResponse(), new MessageCookie(KEY));

        verify(carrier, times(3)).sendCommandToSpeaker(eq(KEY), any(CommandData.class));

        verify(carrier).cancelTimeoutCallback(eq(KEY));
        verify(carrier).response(eq(KEY), any(InfoMessage.class));

        verifyNoMoreInteractions(commandBuilder);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void handleSyncOnlyExcessMeters() throws UnexpectedInputException, MessageDispatchException {
        request = SwitchValidateRequest.builder().switchId(SWITCH_ID).performSync(true).removeExcess(true).build();
        missingRules = emptyList();
        excessMeters = singletonList(
                new MeterInfoEntry(EXCESS_COOKIE, EXCESS_COOKIE, FLOW_ID, 0L, 0L, new String[]{}, null, null));

        service.handleSwitchSync(KEY, request, makeValidationResult());

        verify(carrier).sendCommandToSpeaker(eq(KEY), any(CommandData.class));
        service.dispatchWorkerMessage(new DeleteMeterResponse(true), new MessageCookie(KEY));

        verify(carrier).cancelTimeoutCallback(eq(KEY));
        verify(carrier).response(eq(KEY), any(InfoMessage.class));

        verifyNoMoreInteractions(commandBuilder);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void handleSyncWhenNotProcessMeters() throws UnexpectedInputException, MessageDispatchException {
        request = SwitchValidateRequest.builder().switchId(SWITCH_ID).performSync(true).removeExcess(true).build();

        ValidationResult tempResult = makeValidationResult();
        service.handleSwitchSync(KEY, request, new ValidationResult(
                tempResult.getFlowEntries(), false, tempResult.getValidateRulesResult(), null,
                new ValidateGroupsResult(emptyList(), emptyList(), emptyList(), emptyList()),
                new ValidateLogicalPortsResult(emptyList(), emptyList(), emptyList(), emptyList())));

        verify(commandBuilder).buildCommandsToSyncMissingRules(eq(SWITCH_ID), eq(missingRules));
        verify(carrier).sendCommandToSpeaker(eq(KEY), any(CommandData.class));

        service.dispatchWorkerMessage(new FlowInstallResponse(), new MessageCookie(KEY));

        verify(carrier).cancelTimeoutCallback(eq(KEY));
        ArgumentCaptor<InfoMessage> responseCaptor = ArgumentCaptor.forClass(InfoMessage.class);
        verify(carrier).response(eq(KEY), responseCaptor.capture());
        assertNull(((SwitchSyncResponse) responseCaptor.getValue().getData()).getMeters());

        verifyNoMoreInteractions(commandBuilder);
        verifyNoMoreInteractions(carrier);
    }

    private ValidationResult makeValidationResult() {
        return new ValidationResult(singletonList(flowEntry),
                true,
                new ValidateRulesResult(newHashSet(missingRules), newHashSet(flowEntry.getCookie()),
                        newHashSet(excessRules), newHashSet(misconfiguredRules)),
                new ValidateMetersResult(emptyList(), emptyList(), emptyList(), excessMeters),
                new ValidateGroupsResult(emptyList(), emptyList(), emptyList(), emptyList()),
                new ValidateLogicalPortsResult(emptyList(), emptyList(), emptyList(), emptyList()));
    }

    private ErrorMessage getErrorMessage() {
        return new ErrorMessage(new ErrorData(ErrorType.INTERNAL_ERROR, "message", "description"),
                System.currentTimeMillis(), KEY);
    }
}
