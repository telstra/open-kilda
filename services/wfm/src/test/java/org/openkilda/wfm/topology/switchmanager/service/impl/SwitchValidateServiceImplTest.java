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
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.meter.MeterEntry;
import org.openkilda.messaging.info.meter.SwitchMeterEntries;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.info.switches.SwitchValidationResponse;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.topology.switchmanager.SwitchManagerCarrier;
import org.openkilda.wfm.topology.switchmanager.model.ValidateMetersResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateRulesResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidationResult;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SwitchValidateServiceImplTest {

    private static SwitchId SWITCH_ID = new SwitchId(0x0000000000000001L);
    private static String KEY = "KEY";

    @Mock
    private PersistenceManager persistenceManager;

    @Mock
    private ValidationServiceImpl validationService;

    @Mock
    private SwitchManagerCarrier carrier;

    private SwitchValidateServiceImpl service;
    private SwitchValidateRequest request;

    private FlowEntry flowEntry;
    private MeterEntry meterEntry;

    @Before
    public void setUp() {
        RepositoryFactory repositoryFactory = Mockito.mock(RepositoryFactory.class);
        FlowPathRepository flowPathRepository = Mockito.mock(FlowPathRepository.class);

        when(repositoryFactory.createFlowPathRepository()).thenReturn(flowPathRepository);
        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);

        service = new SwitchValidateServiceImpl(carrier, persistenceManager);
        service.validationService = validationService;

        request = new SwitchValidateRequest(SWITCH_ID, false);
        flowEntry = new FlowEntry(-1L, 0, 0, 0, 0, "", 0, 0, 0, 0, null, null, null);
        meterEntry = new MeterEntry(32, 10000, 10500, "OF_13", new String[]{"KBPS", "BURST", "STATS"});

        when(validationService.validateRules(any(), any()))
                .thenReturn(new ValidateRulesResult(singletonList(flowEntry.getCookie()), emptyList(), emptyList()));
        when(validationService.validateMeters(any(), any(), anyLong(), anyDouble()))
                .thenReturn(new ValidateMetersResult(emptyList(), emptyList(), emptyList(), emptyList()));
    }

    @Test
    public void smokeHandleRequest() {
        handleRequestAndInitDataReceive();
    }

    @Test
    public void receiveOnlyRules() {
        handleRequestAndInitDataReceive();

        service.handleFlowEntriesResponse(KEY, new SwitchFlowEntries(SWITCH_ID, singletonList(flowEntry)));

        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(validationService);
    }

    @Test
    public void receiveTaskTimeout() {
        handleRequestAndInitDataReceive();

        service.handleFlowEntriesResponse(KEY, new SwitchFlowEntries(SWITCH_ID, singletonList(flowEntry)));
        service.handleTaskTimeout(KEY);

        verify(carrier).endProcessing(eq(KEY));
        verify(carrier).response(eq(KEY), any(ErrorMessage.class));
        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(validationService);
    }

    @Test
    public void receiveTaskError() {
        handleRequestAndInitDataReceive();

        service.handleFlowEntriesResponse(KEY, new SwitchFlowEntries(SWITCH_ID, singletonList(flowEntry)));
        ErrorMessage errorMessage = getErrorMessage();
        service.handleTaskError(KEY, errorMessage);

        verify(carrier).endProcessing(eq(KEY));
        verify(carrier).response(eq(KEY), eq(errorMessage));
        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(validationService);
    }

    @Test
    public void validationSuccess() {
        when(carrier.getFlowMeterMinBurstSizeInKbits()).thenReturn(1024L);
        when(carrier.getFlowMeterBurstCoefficient()).thenReturn(1D);

        handleRequestAndInitDataReceive();
        handleDataReceiveAndValidate();

        verify(carrier).endProcessing(eq(KEY));

        ArgumentCaptor<InfoMessage> responseCaptor = ArgumentCaptor.forClass(InfoMessage.class);
        verify(carrier).response(eq(KEY), responseCaptor.capture());
        SwitchValidationResponse response = (SwitchValidationResponse) responseCaptor.getValue().getData();
        assertEquals(singletonList(flowEntry.getCookie()), response.getRules().getMissing());

        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(validationService);
    }

    @Test
    public void exceptionWhileValidation() {
        handleRequestAndInitDataReceive();

        String errorMessage = "test error";
        when(validationService.validateMeters(any(), any(), anyLong(), anyDouble()))
                .thenThrow(new IllegalArgumentException(errorMessage));
        handleDataReceiveAndValidate();

        ArgumentCaptor<ErrorMessage> errorCaptor = ArgumentCaptor.forClass(ErrorMessage.class);
        verify(carrier).response(eq(KEY), errorCaptor.capture());
        assertEquals(errorMessage, errorCaptor.getValue().getData().getErrorMessage());

        verify(carrier).endProcessing(eq(KEY));
        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(validationService);
    }

    @Test
    public void doNothingWhenFsmNotFound() {
        service.handleFlowEntriesResponse(KEY, new SwitchFlowEntries(SWITCH_ID, singletonList(flowEntry)));

        verifyZeroInteractions(carrier);
        verifyZeroInteractions(validationService);
    }

    @Test
    public void validationPerformSync() {
        request = new SwitchValidateRequest(SWITCH_ID, true);

        handleRequestAndInitDataReceive();
        handleDataReceiveAndValidate();

        verify(carrier).runSwitchSync(eq(KEY), eq(request), any(ValidationResult.class));
        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(validationService);
    }


    private void handleRequestAndInitDataReceive() {
        service.handleSwitchValidateRequest(KEY, request);

        verify(carrier, times(2)).sendCommand(eq(KEY), any(CommandMessage.class));
        verifyNoMoreInteractions(carrier);
    }

    private void handleDataReceiveAndValidate() {
        service.handleFlowEntriesResponse(KEY, new SwitchFlowEntries(SWITCH_ID, singletonList(flowEntry)));
        service.handleMeterEntriesResponse(KEY, new SwitchMeterEntries(SWITCH_ID, singletonList(meterEntry)));

        verify(carrier).getFlowMeterBurstCoefficient();
        verify(carrier).getFlowMeterMinBurstSizeInKbits();

        verify(validationService).validateRules(eq(SWITCH_ID), any());
        verify(validationService).validateMeters(eq(SWITCH_ID), any(), anyLong(), anyDouble());
    }

    private ErrorMessage getErrorMessage() {
        return new ErrorMessage(new ErrorData(ErrorType.INTERNAL_ERROR, "message", "description"),
                System.currentTimeMillis(), KEY);
    }
}
