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

package org.openkilda.wfm.topology.switchmanager.service.impl.fsmhandlers;

import static com.google.common.collect.Sets.newHashSet;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.openkilda.floodlight.api.request.SpeakerRequest;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.command.flow.RemoveFlowForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.DeleteMeterForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.switches.MeterInfoEntry;
import org.openkilda.messaging.info.switches.SwitchSyncResponse;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.wfm.topology.switchmanager.model.ValidateGroupsResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateLogicalPortsResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateMetersResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateRulesResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidationResult;
import org.openkilda.wfm.topology.switchmanager.service.CommandBuilder;
import org.openkilda.wfm.topology.switchmanager.service.SwitchManagerCarrier;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class SwitchSyncServiceImplTest {

    private static final SwitchId SWITCH_ID = new SwitchId(0x0000000000000001L);
    private static final String FLOW_ID = "flow_id";
    private static final String KEY = "KEY";
    private static final long EXCESS_COOKIE = new FlowSegmentCookie(FlowPathDirection.FORWARD, 1).getValue();

    @Mock
    private SwitchManagerCarrier carrier;

    @Mock
    private PersistenceManager persistenceManager;

    @Mock
    private CommandBuilder commandBuilder;

    private SwitchSyncServiceImpl service;

    private SwitchValidateRequest request;
    private FlowEntry flowEntry;
    private FlowSpeakerCommandData expectedFlow;

    private List<Long> missingRules;
    private Set<Long> excessRules;
    private List<Long> misconfiguredRules;
    private List<MeterInfoEntry> excessMeters;

    @Before
    public void setUp() {
        RepositoryFactory repositoryFactory = Mockito.mock(RepositoryFactory.class);

        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);

        service = new SwitchSyncServiceImpl(carrier, persistenceManager);
        service.commandBuilder = commandBuilder;

        request = SwitchValidateRequest.builder().switchId(SWITCH_ID).performSync(true).build();
        flowEntry = new FlowEntry(
                new FlowSegmentCookie(FlowPathDirection.FORWARD, 7).getValue(),
                0, 0, 0, 0, "", 0, 0, 0, 0, null, null, null);
        expectedFlow = FlowSpeakerCommandData.builder()
                .cookie(new Cookie(flowEntry.getCookie()))
                .build();

        missingRules = singletonList(flowEntry.getCookie());
        excessRules = emptySet();
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
    public void doNothingWhenFsmNotFound() {
        service.handleInstallCommandsResponse(KEY);

        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(commandBuilder);
    }

    @Test
    public void handleRuleSyncSuccess() {
        service.handleSwitchSync(KEY, request, makeValidationResult());

        verify(carrier).sendCommandToSpeaker(eq(KEY), any(SpeakerRequest.class));

        service.handleInstallCommandsResponse(KEY);

        verify(carrier).cancelTimeoutCallback(eq(KEY));
        verify(carrier).response(eq(KEY), any(InfoMessage.class));

        verifyNoMoreInteractions(commandBuilder);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void receiveRuleSyncTimeout() {
        service.handleSwitchSync(KEY, request, makeValidationResult());

        verify(carrier).sendCommandToSpeaker(eq(KEY), any(SpeakerRequest.class));

        service.handleTaskTimeout(KEY);

        verify(carrier).response(eq(KEY), any(ErrorMessage.class));

        verifyNoMoreInteractions(commandBuilder);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void receiveRuleSyncError() {
        service.handleSwitchSync(KEY, request, makeValidationResult());

        verify(carrier).sendCommandToSpeaker(eq(KEY), any(SpeakerRequest.class));

        ErrorMessage errorMessage = getErrorMessage();
        service.handleTaskError(KEY, errorMessage);

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

        service.handleSwitchSync(KEY, request, makeValidationResult());
        verify(carrier).sendCommandToSpeaker(eq(KEY), any(CommandData.class));

        ErrorMessage errorMessage = getErrorMessage();
        service.handleTaskError(KEY, errorMessage);

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
    public void handleSyncExcess() {
        request = SwitchValidateRequest.builder().switchId(SWITCH_ID).performSync(true).removeExcess(true).build();

        excessRules = singleton(EXCESS_COOKIE);
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

        verify(commandBuilder).buildCommandsToRemoveExcessRules(
                eq(SWITCH_ID), eq(singletonList(flowEntry)), eq(excessRules));
        verify(carrier).sendCommandToSpeaker(eq(KEY), any(RemoveFlowForSwitchManagerRequest.class));
        service.handleRemoveRulesResponse(KEY);

        verify(carrier).sendCommandToSpeaker(eq(KEY), any(DeleteMeterForSwitchManagerRequest.class));
        service.handleRemoveMetersResponse(KEY);

        verify(carrier, times(2)).sendCommandToSpeaker(eq(KEY), any(CommandData.class));
        verify(carrier).sendCommandToSpeaker(eq(KEY), any(SpeakerRequest.class));
        service.handleInstallCommandsResponse(KEY);

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

        service.handleSwitchSync(KEY, request, makeValidationResult());

        verify(carrier).sendCommandToSpeaker(eq(KEY), any(CommandData.class));
        service.handleRemoveMetersResponse(KEY);

        verify(carrier).cancelTimeoutCallback(eq(KEY));
        verify(carrier).response(eq(KEY), any(InfoMessage.class));

        verifyNoMoreInteractions(commandBuilder);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void handleSyncWhenNotProcessMeters() {
        request = SwitchValidateRequest.builder().switchId(SWITCH_ID).performSync(true).removeExcess(true).build();

        ValidationResult tempResult = makeValidationResult();
        service.handleSwitchSync(KEY, request, new ValidationResult(
                tempResult.getFlowEntries(), false, tempResult.getValidateRulesResult(), null,
                new ValidateGroupsResult(emptyList(), emptyList(), emptyList(), emptyList()),
                new ValidateLogicalPortsResult(emptyList(), emptyList(), emptyList(), emptyList()),
                singletonList(expectedFlow)));

        verify(carrier).sendCommandToSpeaker(eq(KEY), any(SpeakerRequest.class));
        service.handleInstallCommandsResponse(KEY);

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
                new ValidateLogicalPortsResult(emptyList(), emptyList(), emptyList(), emptyList()),
                singletonList(expectedFlow));
    }

    private ErrorMessage getErrorMessage() {
        return new ErrorMessage(new ErrorData(ErrorType.INTERNAL_ERROR, "message", "description"),
                System.currentTimeMillis(), KEY);
    }
}
