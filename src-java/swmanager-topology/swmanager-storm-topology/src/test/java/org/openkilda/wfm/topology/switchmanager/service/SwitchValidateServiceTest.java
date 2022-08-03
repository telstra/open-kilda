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

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.openkilda.model.SwitchFeature.LAG;

import org.openkilda.messaging.MessageCookie;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.grpc.DumpLogicalPortsRequest;
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowDumpResponse;
import org.openkilda.messaging.info.group.GroupDumpResponse;
import org.openkilda.messaging.info.meter.MeterDumpResponse;
import org.openkilda.messaging.info.meter.SwitchMeterUnsupported;
import org.openkilda.messaging.info.switches.v2.RuleInfoEntryV2;
import org.openkilda.messaging.info.switches.v2.SwitchValidationResponseV2;
import org.openkilda.model.IpSocketAddress;
import org.openkilda.model.MeterId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.MeterFlag;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.OfMetadata;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.wfm.error.MessageDispatchException;
import org.openkilda.wfm.error.UnexpectedInputException;
import org.openkilda.wfm.topology.switchmanager.error.SpeakerFailureException;
import org.openkilda.wfm.topology.switchmanager.model.SwitchEntities;
import org.openkilda.wfm.topology.switchmanager.model.ValidationResult;
import org.openkilda.wfm.topology.switchmanager.model.v2.ValidateMetersResultV2;
import org.openkilda.wfm.topology.switchmanager.model.v2.ValidateRulesResultV2;
import org.openkilda.wfm.topology.switchmanager.service.impl.ValidationServiceImpl;

import com.google.common.collect.Sets;
import org.apache.storm.shade.com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Optional;

@RunWith(MockitoJUnitRunner.class)
public class SwitchValidateServiceTest {
    private static final SwitchId SWITCH_ID = new SwitchId(0x0000000000000001L);
    private static final SwitchId SWITCH_ID_MISSING = new SwitchId(0x0000000000000002L);
    private static final SwitchId LAG_SWITCH_ID = new SwitchId(0x0000000000000003L);
    private static final Switch SWITCH_1 = Switch.builder().switchId(SWITCH_ID).build();
    private static final Switch SWITCH_2 = Switch.builder()
            .switchId(LAG_SWITCH_ID)
            .features(Sets.newHashSet(LAG))
            .socketAddress(new IpSocketAddress("127.127.127.127", 9999))
            .build();
    private static final String KEY = "KEY";

    @Mock
    private PersistenceManager persistenceManager;

    @Mock
    private ValidationServiceImpl validationService;

    @Mock
    private SwitchManagerCarrier carrier;

    private SwitchValidateService service;
    private SwitchValidateRequest request;

    private FlowSpeakerData flowSpeakerData;
    private MeterSpeakerData meterSpeakerData;

    @Before
    public void setUp() {
        RepositoryFactory repositoryFactory = Mockito.mock(RepositoryFactory.class);
        SwitchRepository switchRepository = Mockito.mock(SwitchRepository.class);
        when(switchRepository.findById(eq(SWITCH_ID))).thenReturn(Optional.of(SWITCH_1));
        when(switchRepository.findById(eq(LAG_SWITCH_ID))).thenReturn(Optional.of(SWITCH_2));
        when(switchRepository.findById(eq(SWITCH_ID_MISSING))).thenReturn(Optional.empty());
        when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);
        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);

        service = new SwitchValidateService(carrier, persistenceManager, validationService);

        request = SwitchValidateRequest.builder().switchId(SWITCH_ID).processMeters(true).build();
        flowSpeakerData = FlowSpeakerData.builder()
                .ofVersion(OfVersion.OF_13)
                .cookie(new Cookie(1L))
                .table(OfTable.INPUT)
                .priority(10)
                .match(emptySet())
                .instructions(Instructions.builder().goToMeter(new MeterId(888)).goToTable(OfTable.EGRESS)
                        .writeMetadata(new OfMetadata(1, 1)).build())
                .flags(emptySet())
                .build();
        meterSpeakerData = MeterSpeakerData.builder()
                .meterId(new MeterId(32))
                .rate(10000)
                .burst(10500)
                .ofVersion(OfVersion.OF_13)
                .flags(Sets.newHashSet(MeterFlag.KBPS, MeterFlag.BURST, MeterFlag.STATS))
                .build();

        RuleInfoEntryV2 ruleEntry = RuleInfoEntryV2.builder()
                .cookie(flowSpeakerData.getCookie().getValue())
                .build();

        when(validationService.validateRules(any(), any(), any()))
                .thenReturn(new ValidateRulesResultV2(false, Sets.newHashSet(ruleEntry), emptySet(),
                        emptySet(), emptySet()));
        when(validationService.validateMeters(any(), any(), any()))
                .thenReturn(new ValidateMetersResultV2(false, emptyList(), emptyList(), emptyList(),
                        emptyList()));
    }

    @Test
    public void smokeHandleRequest() {
        handleRequestAndInitDataReceive();
    }

    @Test
    public void receiveOnlyRules() throws UnexpectedInputException, MessageDispatchException {
        handleRequestAndInitDataReceive();

        service.dispatchWorkerMessage(
                new FlowDumpResponse(singletonList(flowSpeakerData)), new MessageCookie(KEY));

        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(validationService);
    }

    @Test
    public void receiveTaskTimeout() throws UnexpectedInputException, MessageDispatchException {
        handleRequestAndInitDataReceive();

        service.dispatchWorkerMessage(
                new FlowDumpResponse(singletonList(flowSpeakerData)), new MessageCookie(KEY));
        ArgumentCaptor<MessageCookie> argument = ArgumentCaptor.forClass(MessageCookie.class);
        verify(carrier, times(3))
                .sendCommandToSpeaker(any(CommandData.class), argument.capture());
        MessageCookie cookie = argument.getValue();
        service.timeout(cookie);

        verify(carrier).cancelTimeoutCallback(eq(KEY));
        verify(carrier).errorResponse(eq(KEY), eq(ErrorType.OPERATION_TIMED_OUT), any(String.class), any(String.class));

        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(validationService);
    }

    @Test
    public void receiveTaskError() throws UnexpectedInputException, MessageDispatchException {
        handleRequestAndInitDataReceive();

        service.dispatchWorkerMessage(
                new FlowDumpResponse(singletonList(flowSpeakerData)), new MessageCookie(KEY));
        ArgumentCaptor<MessageCookie> argument = ArgumentCaptor.forClass(MessageCookie.class);
        verify(carrier, times(3))
                .sendCommandToSpeaker(any(CommandData.class), argument.capture());
        ErrorMessage errorMessage = getErrorMessage();
        service.dispatchErrorMessage(errorMessage.getData(), argument.getValue());

        verify(carrier).cancelTimeoutCallback(eq(KEY));
        verify(carrier).errorResponse(eq(KEY), eq(errorMessage.getData().getErrorType()), any(String.class),
                any(String.class));

        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(validationService);
    }

    @Test
    public void validationSuccess() throws UnexpectedInputException, MessageDispatchException {
        handleRequestAndInitDataReceive();
        handleDataReceiveAndValidate();

        verify(carrier).cancelTimeoutCallback(eq(KEY));
        ArgumentCaptor<InfoMessage> responseCaptor = ArgumentCaptor.forClass(InfoMessage.class);
        verify(carrier).response(eq(KEY), responseCaptor.capture());
        SwitchValidationResponseV2 response = (SwitchValidationResponseV2) responseCaptor.getValue().getData();
        assertEquals(flowSpeakerData.getCookie().getValue(),
                Lists.newArrayList(response.getRules().getMissing()).get(0).getCookie().longValue());

        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(validationService);
    }

    @Test
    public void validationWithoutMetersSuccess() throws UnexpectedInputException, MessageDispatchException {
        request = SwitchValidateRequest.builder().switchId(SWITCH_ID).build();

        service.handleSwitchValidateRequest(KEY, request);
        verify(carrier, times(2))
                .sendCommandToSpeaker(any(CommandData.class), any(MessageCookie.class));
        verify(carrier, times(1))
                .runHeavyOperation(eq(SWITCH_ID), any(MessageCookie.class));

        service.dispatchWorkerMessage(
                new FlowDumpResponse(singletonList(flowSpeakerData)), new MessageCookie(KEY));
        service.dispatchWorkerMessage(new GroupDumpResponse(SWITCH_ID, emptyList()), new MessageCookie(KEY));
        service.dispatchWorkerMessage(new SwitchEntities(new ArrayList<>()), new MessageCookie(KEY));
        verify(validationService).validateRules(eq(SWITCH_ID), any(), any());
        verify(validationService).validateGroups(eq(SWITCH_ID), any(), any());
        verify(carrier).cancelTimeoutCallback(eq(KEY));
        ArgumentCaptor<InfoMessage> responseCaptor = ArgumentCaptor.forClass(InfoMessage.class);
        verify(carrier).response(eq(KEY), responseCaptor.capture());

        SwitchValidationResponseV2 response = (SwitchValidationResponseV2) responseCaptor.getValue().getData();
        assertEquals(flowSpeakerData.getCookie().getValue(),
                Lists.newArrayList(response.getRules().getMissing()).get(0).getCookie().longValue());
        assertNull(response.getMeters());

        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(validationService);
    }

    @Test
    public void validationSuccessWithUnsupportedMeters() throws UnexpectedInputException, MessageDispatchException {
        handleRequestAndInitDataReceive();
        service.dispatchWorkerMessage(
                new FlowDumpResponse(singletonList(flowSpeakerData)), new MessageCookie(KEY));
        service.dispatchWorkerMessage(new SwitchMeterUnsupported(SWITCH_ID), new MessageCookie(KEY));
        service.dispatchWorkerMessage(new GroupDumpResponse(SWITCH_ID, emptyList()), new MessageCookie(KEY));
        service.dispatchWorkerMessage(new SwitchEntities(new ArrayList<>()), new MessageCookie(KEY));

        verify(validationService).validateRules(eq(SWITCH_ID), any(), any());
        verify(validationService).validateGroups(eq(SWITCH_ID), any(), any());
        verify(carrier).cancelTimeoutCallback(eq(KEY));
        ArgumentCaptor<InfoMessage> responseCaptor = ArgumentCaptor.forClass(InfoMessage.class);
        verify(carrier).response(eq(KEY), responseCaptor.capture());

        SwitchValidationResponseV2 response = (SwitchValidationResponseV2) responseCaptor.getValue().getData();
        assertEquals(flowSpeakerData.getCookie().getValue(),
                Lists.newArrayList(response.getRules().getMissing()).get(0).getCookie().longValue());
        assertNull(response.getMeters());

        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(validationService);
    }

    @Test
    public void validationSuccessWithUnavailableGrpc() throws UnexpectedInputException, MessageDispatchException {
        request = SwitchValidateRequest.builder().switchId(LAG_SWITCH_ID).processMeters(true).build();
        service.handleSwitchValidateRequest(KEY, request);

        verify(carrier, times(4))
                .sendCommandToSpeaker(any(CommandData.class), argThat(cookie -> KEY.equals(cookie.getValue())));
        verify(carrier, times(1))
                .runHeavyOperation(eq(LAG_SWITCH_ID), argThat(cookie -> KEY.equals(cookie.getValue())));

        ArgumentCaptor<MessageCookie> cookieCaptor = ArgumentCaptor.forClass(MessageCookie.class);
        verify(carrier, times(1))
                .sendCommandToSpeaker(any(DumpLogicalPortsRequest.class), cookieCaptor.capture());
        verifyNoMoreInteractions(carrier);

        service.dispatchWorkerMessage(
                new FlowDumpResponse(singletonList(flowSpeakerData)), new MessageCookie(KEY));
        service.dispatchWorkerMessage(new GroupDumpResponse(LAG_SWITCH_ID, emptyList()), new MessageCookie(KEY));
        service.dispatchWorkerMessage(new SwitchEntities(new ArrayList<>()), new MessageCookie(KEY));
        service.dispatchWorkerMessage(new MeterDumpResponse(LAG_SWITCH_ID, emptyList()), new MessageCookie(KEY));
        service.dispatchErrorMessage(getErrorMessage().getData(), cookieCaptor.getValue());

        verify(validationService).validateRules(eq(LAG_SWITCH_ID), any(), any());
        verify(validationService).validateGroups(eq(LAG_SWITCH_ID), any(), any());
        verify(validationService).validateMeters(eq(LAG_SWITCH_ID), any(), any());

        verify(carrier).cancelTimeoutCallback(eq(KEY));

        ArgumentCaptor<InfoMessage> responseCaptor = ArgumentCaptor.forClass(InfoMessage.class);
        verify(carrier).response(eq(KEY), responseCaptor.capture());
        SwitchValidationResponseV2 response = (SwitchValidationResponseV2) responseCaptor.getValue().getData();

        assertEquals(flowSpeakerData.getCookie().getValue(),
                Lists.newArrayList(response.getRules().getMissing()).get(0).getCookie().longValue());
        assertEquals(SpeakerFailureException.makeMessage(getErrorMessage().getData()),
                response.getLogicalPorts().getError());

        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(validationService);

    }

    @Test
    public void exceptionWhileValidation() throws UnexpectedInputException, MessageDispatchException {
        handleRequestAndInitDataReceive();

        String errorMessage = "test error";
        when(validationService.validateGroups(any(), any(), any()))
                .thenThrow(new IllegalArgumentException(errorMessage));
        handleDataReceiveAndValidate();

        verify(carrier).cancelTimeoutCallback(eq(KEY));
        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(carrier).errorResponse(eq(KEY), eq(ErrorType.INTERNAL_ERROR), errorCaptor.capture(), any(String.class));
        assertEquals(errorMessage, errorCaptor.getValue());

        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(validationService);
    }

    @Test(expected = MessageDispatchException.class)
    public void doNothingWhenFsmNotFound() throws UnexpectedInputException, MessageDispatchException {
        service.dispatchWorkerMessage(
                new FlowDumpResponse(singletonList(flowSpeakerData)), new MessageCookie(KEY));
    }

    @Test
    public void validationPerformSync() throws UnexpectedInputException, MessageDispatchException {
        request = SwitchValidateRequest.builder().switchId(SWITCH_ID).performSync(true).processMeters(true).build();

        handleRequestAndInitDataReceive();
        handleDataReceiveAndValidate();

        verify(carrier).runSwitchSync(eq(KEY), eq(request), any(ValidationResult.class));
        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(validationService);
    }

    @Test
    public void errorResponseOnSwitchNotFound() {
        request = SwitchValidateRequest
                .builder().switchId(SWITCH_ID_MISSING).performSync(true).processMeters(true).build();
        service.handleSwitchValidateRequest(KEY, request);

        verify(carrier).cancelTimeoutCallback(eq(KEY));
        verify(carrier).errorResponse(
                eq(KEY), eq(ErrorType.NOT_FOUND), eq(String.format("Switch '%s' not found", request.getSwitchId())),
                any(String.class));

        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(validationService);
    }

    private void handleRequestAndInitDataReceive() {
        service.handleSwitchValidateRequest(KEY, request);

        verify(carrier, times(3))
                .sendCommandToSpeaker(any(CommandData.class), argThat(cookie -> KEY.equals(cookie.getValue())));
        verify(carrier, times(1))
                .runHeavyOperation(eq(SWITCH_ID), argThat(cookie -> KEY.equals(cookie.getValue())));
        verifyNoMoreInteractions(carrier);
    }

    private void handleDataReceiveAndValidate() throws UnexpectedInputException, MessageDispatchException {
        service.dispatchWorkerMessage(
                new FlowDumpResponse(singletonList(flowSpeakerData)), new MessageCookie(KEY));
        service.dispatchWorkerMessage(
                new MeterDumpResponse(SWITCH_ID, singletonList(meterSpeakerData)), new MessageCookie(KEY));
        service.dispatchWorkerMessage(new GroupDumpResponse(SWITCH_ID, emptyList()), new MessageCookie(KEY));
        service.dispatchWorkerMessage(new SwitchEntities(new ArrayList<>()), new MessageCookie(KEY));

        verify(validationService).validateRules(eq(SWITCH_ID), any(), any());
        verify(validationService).validateMeters(eq(SWITCH_ID), any(), any());
        verify(validationService).validateGroups(eq(SWITCH_ID), any(), any());
    }

    private ErrorMessage getErrorMessage() {
        return new ErrorMessage(new ErrorData(ErrorType.INTERNAL_ERROR, "message", "description"),
                System.currentTimeMillis(), KEY);
    }
}
