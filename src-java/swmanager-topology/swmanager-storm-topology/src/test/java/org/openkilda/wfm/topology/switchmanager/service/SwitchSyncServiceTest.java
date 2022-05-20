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
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.openkilda.floodlight.api.request.rulemanager.FlowCommand;
import org.openkilda.floodlight.api.request.rulemanager.MeterCommand;
import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.MessageCookie;
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.grpc.DeleteLogicalPortResponse;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.switches.MeterInfoEntry;
import org.openkilda.messaging.info.switches.SwitchSyncResponse;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.wfm.error.MessageDispatchException;
import org.openkilda.wfm.error.UnexpectedInputException;
import org.openkilda.wfm.topology.switchmanager.bolt.SwitchManagerHub.OfCommandAction;
import org.openkilda.wfm.topology.switchmanager.model.ValidateGroupsResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateLogicalPortsResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateMetersResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateRulesResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidationResult;
import org.openkilda.wfm.topology.switchmanager.service.configs.SwitchSyncConfig;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class SwitchSyncServiceTest {

    private static final int OF_COMMANDS_BATCH_SIZE = 500;
    private static SwitchId SWITCH_ID = new SwitchId(0x0000000000000001L);
    private static String FLOW_ID = "flow_id";
    private static String KEY = "KEY";
    private static long EXCESS_COOKIE = new FlowSegmentCookie(FlowPathDirection.FORWARD, 1).getValue();

    @Mock
    private SwitchManagerCarrier carrier;

    @Mock
    private CommandBuilder commandBuilder;

    @Captor
    private ArgumentCaptor<List<OfCommand>> captor;

    private SwitchSyncService service;

    private SwitchValidateRequest request;
    private FlowEntry flowEntry;

    private List<Long> missingRules;
    private List<Long> excessRules;
    private List<Long> misconfiguredRules;
    private List<MeterInfoEntry> excessMeters;

    private List<SpeakerData> expectedEntries;
    private List<FlowSpeakerData> actualFlows;
    private List<MeterSpeakerData> actualMeters;
    private List<GroupSpeakerData> actualGroups;

    @Before
    public void setUp() {
        service = new SwitchSyncService(carrier, commandBuilder, new SwitchSyncConfig(OF_COMMANDS_BATCH_SIZE));

        request = SwitchValidateRequest.builder().switchId(SWITCH_ID).performSync(true).build();
        flowEntry = new FlowEntry(
                new FlowSegmentCookie(FlowPathDirection.FORWARD, 7).getValue(),
                0, 0, 0, 0, "", 0, 0, 0, 0, null, null, null);

        missingRules = singletonList(flowEntry.getCookie());
        excessRules = emptyList();
        misconfiguredRules = emptyList();
        excessMeters = emptyList();

        expectedEntries = singletonList(FlowSpeakerData.builder().cookie(new Cookie(flowEntry.getCookie())).build());
        actualFlows = emptyList();
        actualMeters = emptyList();
        actualGroups = emptyList();
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

    @Test(expected = MessageDispatchException.class)
    public void reportErrorInCaseOfMissingHandler() throws UnexpectedInputException, MessageDispatchException {
        service.dispatchWorkerMessage(new DeleteLogicalPortResponse("", 2, true), new MessageCookie("dummy"));
    }

    @Test
    public void handleRuleSyncSuccess() throws UnexpectedInputException, MessageDispatchException {
        service.handleSwitchSync(KEY, request, makeValidationResult());

        verify(carrier).sendOfCommandsToSpeaker(eq(KEY), captor.capture(), eq(OfCommandAction.INSTALL), eq(SWITCH_ID));
        assertEquals(1, captor.getValue().size());
        assertTrue(captor.getValue().get(0) instanceof FlowCommand);
        FlowCommand flowCommand = (FlowCommand) captor.getValue().get(0);
        assertEquals(flowEntry.getCookie(), flowCommand.getData().getCookie().getValue());

        service.dispatchWorkerMessage(buildSpeakerCommandResponse(), new MessageCookie(KEY));

        verify(carrier).cancelTimeoutCallback(eq(KEY));
        verify(carrier).response(eq(KEY), any(InfoMessage.class));

        verifyNoInteractions(commandBuilder);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void receiveRuleSyncTimeout() throws MessageDispatchException {
        service.handleSwitchSync(KEY, request, makeValidationResult());

        verify(carrier).sendOfCommandsToSpeaker(eq(KEY), any(List.class), eq(OfCommandAction.INSTALL), eq(SWITCH_ID));

        service.timeout(new MessageCookie(KEY));

        verify(carrier).response(eq(KEY), any(ErrorMessage.class));

        verifyNoMoreInteractions(commandBuilder);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void receiveRuleSyncError() throws MessageDispatchException {
        service.handleSwitchSync(KEY, request, makeValidationResult());

        verify(carrier).sendOfCommandsToSpeaker(eq(KEY), any(List.class), eq(OfCommandAction.INSTALL), eq(SWITCH_ID));

        ErrorMessage errorMessage = getErrorMessage();
        service.dispatchErrorMessage(errorMessage.getData(), new MessageCookie(KEY));

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
        actualMeters = singletonList(MeterSpeakerData.builder()
                .meterId(new MeterId(EXCESS_COOKIE))
                .build());

        service.handleSwitchSync(KEY, request, makeValidationResult());
        verify(carrier).sendOfCommandsToSpeaker(eq(KEY), any(List.class), eq(OfCommandAction.DELETE), eq(SWITCH_ID));

        service.dispatchErrorMessage(getErrorMessage().getData(), new MessageCookie(KEY));

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
        actualFlows = singletonList(FlowSpeakerData.builder()
                .cookie(new Cookie(EXCESS_COOKIE))
                .build());

        excessMeters = singletonList(
                new MeterInfoEntry(EXCESS_COOKIE, EXCESS_COOKIE, FLOW_ID, 0L, 0L, new String[]{}, null, null));
        actualMeters = singletonList(MeterSpeakerData.builder()
                .meterId(new MeterId(EXCESS_COOKIE))
                .build());

        service.handleSwitchSync(KEY, request, makeValidationResult());

        verify(carrier).sendOfCommandsToSpeaker(eq(KEY), captor.capture(), eq(OfCommandAction.DELETE), eq(SWITCH_ID));
        assertEquals(2, captor.getValue().size());
        FlowCommand flowCommand = captor.getValue().stream()
                .filter(command -> command instanceof FlowCommand)
                .map(command -> (FlowCommand) command)
                .findFirst().orElseThrow(() -> new IllegalStateException("Flow command not found"));
        assertEquals(EXCESS_COOKIE, flowCommand.getData().getCookie().getValue());
        MeterCommand meterCommand = captor.getValue().stream()
                .filter(command -> command instanceof MeterCommand)
                .map(command -> (MeterCommand) command)
                .findFirst().orElseThrow(() -> new IllegalStateException("Meter command not found"));
        assertEquals(EXCESS_COOKIE, meterCommand.getData().getMeterId().getValue());
        service.dispatchWorkerMessage(buildSpeakerCommandResponse(), new MessageCookie(KEY));

        verify(carrier).sendOfCommandsToSpeaker(eq(KEY), any(List.class), eq(OfCommandAction.INSTALL), eq(SWITCH_ID));
        service.dispatchWorkerMessage(buildSpeakerCommandResponse(), new MessageCookie(KEY));

        verify(carrier).cancelTimeoutCallback(eq(KEY));
        verify(carrier).response(eq(KEY), any(InfoMessage.class));

        verifyNoInteractions(commandBuilder);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void handleSyncOnlyExcessMeters() throws UnexpectedInputException, MessageDispatchException {
        request = SwitchValidateRequest.builder().switchId(SWITCH_ID).performSync(true).removeExcess(true).build();
        missingRules = emptyList();
        excessMeters = singletonList(
                new MeterInfoEntry(EXCESS_COOKIE, EXCESS_COOKIE, FLOW_ID, 0L, 0L, new String[]{}, null, null));
        actualMeters = singletonList(MeterSpeakerData.builder()
                .meterId(new MeterId(EXCESS_COOKIE))
                .build());

        service.handleSwitchSync(KEY, request, makeValidationResult());

        verify(carrier).sendOfCommandsToSpeaker(eq(KEY), captor.capture(), eq(OfCommandAction.DELETE), eq(SWITCH_ID));
        service.dispatchWorkerMessage(buildSpeakerCommandResponse(), new MessageCookie(KEY));

        verify(carrier).cancelTimeoutCallback(eq(KEY));
        verify(carrier).response(eq(KEY), any(InfoMessage.class));

        verifyNoInteractions(commandBuilder);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void handleSyncWhenNotProcessMeters() {
        request = SwitchValidateRequest.builder().switchId(SWITCH_ID).performSync(true).removeExcess(true).build();

        ValidationResult tempResult = makeValidationResult();
        service.handleSwitchSync(KEY, request, new ValidationResult(
                tempResult.getFlowEntries(), false,
                emptyList(), emptyList(), emptyList(), emptyList(),
                tempResult.getValidateRulesResult(), null,
                new ValidateGroupsResult(emptyList(), emptyList(), emptyList(), emptyList()),
                ValidateLogicalPortsResult.newEmpty()));

        verify(carrier).cancelTimeoutCallback(eq(KEY));
        ArgumentCaptor<InfoMessage> responseCaptor = ArgumentCaptor.forClass(InfoMessage.class);
        verify(carrier).response(eq(KEY), responseCaptor.capture());
        assertNull(((SwitchSyncResponse) responseCaptor.getValue().getData()).getMeters());

        verifyNoInteractions(commandBuilder);
        verifyNoMoreInteractions(carrier);
    }

    private ValidationResult makeValidationResult() {
        return new ValidationResult(singletonList(flowEntry),
                true,
                expectedEntries,
                actualFlows,
                actualMeters,
                actualGroups,
                new ValidateRulesResult(newHashSet(missingRules), newHashSet(flowEntry.getCookie()),
                        newHashSet(excessRules), newHashSet(misconfiguredRules)),
                new ValidateMetersResult(emptyList(), emptyList(), emptyList(), excessMeters),
                new ValidateGroupsResult(emptyList(), emptyList(), emptyList(), emptyList()),
                ValidateLogicalPortsResult.newEmpty());
    }

    private ErrorMessage getErrorMessage() {
        return new ErrorMessage(new ErrorData(ErrorType.INTERNAL_ERROR, "message", "description"),
                System.currentTimeMillis(), KEY);
    }

    private SpeakerCommandResponse buildSpeakerCommandResponse() {
        return new SpeakerCommandResponse(new MessageContext(), UUID.randomUUID(), SWITCH_ID, true, emptyMap());
    }
}
