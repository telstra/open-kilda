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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.InstallIngressFlow;
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.OutputVlanType;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.wfm.topology.switchmanager.SwitchManagerCarrier;
import org.openkilda.wfm.topology.switchmanager.model.ValidateMetersResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateRulesResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidationResult;
import org.openkilda.wfm.topology.switchmanager.service.CommandBuilder;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class SwitchSyncServiceImplTest {

    private static SwitchId SWITCH_ID = new SwitchId(0x0000000000000001L);
    private static SwitchId INGRESS_SWITCH_ID = new SwitchId(0x0000000000000002L);
    private static String KEY = "KEY";

    @Mock
    private SwitchManagerCarrier carrier;

    @Mock
    private PersistenceManager persistenceManager;

    @Mock
    private CommandBuilder commandBuilder;

    private SwitchSyncServiceImpl service;

    private SwitchValidateRequest request;
    private FlowEntry flowEntry;
    private List<Long> missingRules;

    @Before
    public void setUp() {
        RepositoryFactory repositoryFactory = Mockito.mock(RepositoryFactory.class);
        FlowRepository flowRepository = Mockito.mock(FlowRepository.class);
        FlowPathRepository flowPathRepository = Mockito.mock(FlowPathRepository.class);
        TransitVlanRepository transitVlanRepository = Mockito.mock(TransitVlanRepository.class);

        when(repositoryFactory.createFlowPathRepository()).thenReturn(flowPathRepository);
        when(repositoryFactory.createFlowRepository()).thenReturn(flowRepository);
        when(repositoryFactory.createTransitVlanRepository()).thenReturn(transitVlanRepository);
        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);

        service = new SwitchSyncServiceImpl(carrier, persistenceManager);
        service.commandBuilder = commandBuilder;

        request = new SwitchValidateRequest(SWITCH_ID, true);
        flowEntry = new FlowEntry(-1L, 0, 0, 0, 0, "", 0, 0, 0, 0, null, null, null);

        InstallIngressFlow installingRule = new InstallIngressFlow(UUID.randomUUID(), "flow", flowEntry.getCookie(),
                SWITCH_ID, 1, 2, 50, 60,
                FlowEncapsulationType.TRANSIT_VLAN, OutputVlanType.POP, 10L, 100L, INGRESS_SWITCH_ID);
        when(commandBuilder.buildCommandsToSyncRules(eq(SWITCH_ID), any()))
                .thenReturn(singletonList(installingRule));

        missingRules = singletonList(flowEntry.getCookie());
    }

    @Test
    public void handleNothingToSync() {
        missingRules = emptyList();
        when(commandBuilder.buildCommandsToSyncRules(eq(SWITCH_ID), any())).thenReturn(emptyList());

        service.handleSwitchSync(KEY, request, makeValidationResult());

        verify(commandBuilder).buildCommandsToSyncRules(eq(SWITCH_ID), eq(emptyList()));
        verify(carrier).endProcessing(eq(KEY));
        verify(carrier).response(eq(KEY), any(InfoMessage.class));

        verifyNoMoreInteractions(commandBuilder);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void handleCommandBuilderException() {
        String errorMessage = "test error";
        when(commandBuilder.buildCommandsToSyncRules(eq(SWITCH_ID), any()))
                .thenThrow(new IllegalArgumentException(errorMessage));

        service.handleSwitchSync(KEY, request, makeValidationResult());

        verify(commandBuilder).buildCommandsToSyncRules(eq(SWITCH_ID), eq(missingRules));
        verify(carrier).endProcessing(eq(KEY));
        ArgumentCaptor<ErrorMessage> errorCaptor = ArgumentCaptor.forClass(ErrorMessage.class);
        verify(carrier).response(eq(KEY), errorCaptor.capture());
        assertEquals(errorMessage, errorCaptor.getValue().getData().getErrorMessage());

        verifyNoMoreInteractions(commandBuilder);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void doNothingWhenFsmNotFound() {
        service.handleInstallRulesResponse(KEY);

        verifyZeroInteractions(carrier);
        verifyZeroInteractions(commandBuilder);
    }

    @Test
    public void handleRuleSyncSuccess() {
        service.handleSwitchSync(KEY, request, makeValidationResult());

        verify(commandBuilder).buildCommandsToSyncRules(eq(SWITCH_ID), eq(missingRules));
        verify(carrier).sendCommand(eq(KEY), any(CommandMessage.class));

        service.handleInstallRulesResponse(KEY);

        verify(carrier).endProcessing(eq(KEY));
        verify(carrier).response(eq(KEY), any(InfoMessage.class));

        verifyNoMoreInteractions(commandBuilder);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void receiveRuleSyncTimeout() {
        service.handleSwitchSync(KEY, request, makeValidationResult());

        verify(commandBuilder).buildCommandsToSyncRules(eq(SWITCH_ID), eq(missingRules));
        verify(carrier).sendCommand(eq(KEY), any(CommandMessage.class));

        service.handleTaskTimeout(KEY);

        verify(carrier).endProcessing(eq(KEY));
        verify(carrier).response(eq(KEY), any(ErrorMessage.class));

        verifyNoMoreInteractions(commandBuilder);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void receiveRuleSyncError() {
        service.handleSwitchSync(KEY, request, makeValidationResult());

        verify(commandBuilder).buildCommandsToSyncRules(eq(SWITCH_ID), eq(missingRules));
        verify(carrier).sendCommand(eq(KEY), any(CommandMessage.class));

        ErrorMessage errorMessage = getErrorMessage();
        service.handleTaskError(KEY, errorMessage);

        verify(carrier).endProcessing(eq(KEY));
        verify(carrier).response(eq(KEY), eq(errorMessage));

        verifyNoMoreInteractions(commandBuilder);
        verifyNoMoreInteractions(carrier);
    }

    private ValidationResult makeValidationResult() {
        return new ValidationResult(singletonList(flowEntry),
                new ValidateRulesResult(missingRules, singletonList(flowEntry.getCookie()), emptyList()),
                new ValidateMetersResult(emptyList(), emptyList(), emptyList(), emptyList()));
    }

    private ErrorMessage getErrorMessage() {
        return new ErrorMessage(new ErrorData(ErrorType.INTERNAL_ERROR, "message", "description"),
                System.currentTimeMillis(), KEY);
    }
}
