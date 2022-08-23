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

package org.openkilda.wfm.topology.flowhs.service.yflow;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.request.SpeakerRequest;
import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.InstallSpeakerCommandsRequest;
import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.messaging.command.yflow.FlowPartialUpdateEndpoint;
import org.openkilda.messaging.command.yflow.SubFlowPartialUpdateDto;
import org.openkilda.messaging.command.yflow.YFlowPartialUpdateRequest;
import org.openkilda.messaging.command.yflow.YFlowRequest;
import org.openkilda.messaging.command.yflow.YFlowResponse;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
import org.openkilda.model.YSubFlow;
import org.openkilda.pce.GetPathsResult;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.exception.UnknownKeyException;
import org.openkilda.wfm.topology.flowhs.service.AbstractYFlowTest;
import org.openkilda.wfm.topology.flowhs.service.FlowCreateService;
import org.openkilda.wfm.topology.flowhs.service.FlowDeleteService;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowUpdateHubCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowUpdateService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RunWith(MockitoJUnitRunner.class)
public class YFlowUpdateServiceTest extends AbstractYFlowTest<SpeakerRequest> {
    private static final int METER_ALLOCATION_RETRIES_LIMIT = 3;

    @Mock
    private FlowGenericCarrier flowCreateHubCarrier;
    @Mock
    private FlowGenericCarrier flowDeleteHubCarrier;
    @Mock
    private FlowGenericCarrier yFlowCreateHubCarrier;
    @Mock
    private FlowUpdateHubCarrier flowUpdateHubCarrier;
    @Mock
    private FlowGenericCarrier yFlowUpdateHubCarrier;

    @Before
    public void init() {
        doAnswer(buildSpeakerRequestAnswer())
                .when(flowCreateHubCarrier).sendSpeakerRequest(any(SpeakerRequest.class));
        doAnswer(buildSpeakerRequestAnswer())
                .when(flowUpdateHubCarrier).sendSpeakerRequest(any(SpeakerRequest.class));
        doAnswer(buildSpeakerRequestAnswer())
                .when(yFlowCreateHubCarrier).sendSpeakerRequest(any(SpeakerRequest.class));
        doAnswer(buildSpeakerRequestAnswer())
                .when(yFlowUpdateHubCarrier).sendSpeakerRequest(any(SpeakerRequest.class));
    }

    @Test
    public void shouldUpdateFlowWithTransitSwitches()
            throws UnroutableFlowException, RecoverableException, DuplicateKeyException {
        // given
        YFlowRequest request = createYFlow();
        request.setMaximumBandwidth(2000L);
        request.getSubFlows().get(0).setEndpoint(newFirstEndpoint);
        request.getSubFlows().get(1).setEndpoint(newSecondEndpoint);
        preparePathComputationForUpdate("test_flow_1", buildNewFirstSubFlowPathPair());
        preparePathComputationForUpdate("test_flow_2", buildNewSecondSubFlowPathPair());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_NEW_FIRST_EP, SWITCH_NEW_SECOND_EP, SWITCH_TRANSIT);

        // when
        processUpdateRequestAndSpeakerCommands(request);

        verifyNorthboundSuccessResponse(yFlowUpdateHubCarrier, YFlowResponse.class);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.UP);
        verifyAffinity(request.getYFlowId());
        YFlow flow = getYFlow(request.getYFlowId());
        assertEquals(2000L, flow.getMaximumBandwidth());
        Set<SwitchId> expectedEndpointSwitchIds = Stream.of(SWITCH_NEW_FIRST_EP, SWITCH_NEW_SECOND_EP)
                .collect(Collectors.toSet());
        Set<SwitchId> actualEndpointSwitchIds = flow.getSubFlows().stream()
                .map(YSubFlow::getEndpointSwitchId)
                .collect(Collectors.toSet());
        assertEquals(expectedEndpointSwitchIds, actualEndpointSwitchIds);
    }

    @Test
    public void shouldUpdateFlowWithProtectedPath()
            throws UnroutableFlowException, RecoverableException, DuplicateKeyException {
        // given
        YFlowRequest request = createYFlowWithProtectedPath();
        request.setMaximumBandwidth(2000L);
        request.getSubFlows().get(0).setEndpoint(newFirstEndpoint);
        request.getSubFlows().get(1).setEndpoint(newSecondEndpoint);

        preparePathComputationForUpdate("test_flow_1",
                buildNewFirstSubFlowPathPair(), buildNewFirstSubFlowProtectedPathPair());
        preparePathComputationForUpdate("test_flow_2",
                buildNewSecondSubFlowPathPair(), buildNewSecondSubFlowProtectedPathPair());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_NEW_FIRST_EP, SWITCH_NEW_SECOND_EP,
                SWITCH_TRANSIT, SWITCH_TRANSIT);
        prepareYPointComputation(SWITCH_SHARED, SWITCH_NEW_FIRST_EP, SWITCH_NEW_SECOND_EP,
                SWITCH_NEW_ALT_TRANSIT, SWITCH_NEW_ALT_TRANSIT);

        // when
        processUpdateRequestAndSpeakerCommands(request);

        verifyNorthboundSuccessResponse(yFlowUpdateHubCarrier, YFlowResponse.class);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.UP);
        verifyAffinity(request.getYFlowId());
        YFlow flow = getYFlow(request.getYFlowId());
        assertEquals(2000L, flow.getMaximumBandwidth());
        Set<SwitchId> expectedEndpointSwitchIds = Stream.of(SWITCH_NEW_FIRST_EP, SWITCH_NEW_SECOND_EP)
                .collect(Collectors.toSet());
        Set<SwitchId> actualEndpointSwitchIds = flow.getSubFlows().stream()
                .map(YSubFlow::getEndpointSwitchId)
                .collect(Collectors.toSet());
        assertEquals(expectedEndpointSwitchIds, actualEndpointSwitchIds);
    }

    @Test
    public void shouldFailIfNoPathAvailableForFirstSubFlow()
            throws UnroutableFlowException, RecoverableException, DuplicateKeyException {
        // given
        YFlowRequest request = createYFlow();
        request.setMaximumBandwidth(2000L);
        request.getSubFlows().get(0).setEndpoint(newFirstEndpoint);
        request.getSubFlows().get(1).setEndpoint(newSecondEndpoint);

        when(pathComputer.getPath(buildFlowIdArgumentMatch("test_flow_1"), any(), anyBoolean()))
                .thenThrow(new UnroutableFlowException(injectedErrorMessage));
        preparePathComputationForUpdate("test_flow_2", buildNewSecondSubFlowPathPair(), buildSecondSubFlowPathPair());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_NEW_FIRST_EP, SWITCH_NEW_SECOND_EP, SWITCH_TRANSIT);

        // when
        processUpdateRequestAndSpeakerCommands(request);

        verifyNorthboundErrorResponse(yFlowUpdateHubCarrier, ErrorType.NOT_FOUND);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.UP);

        YFlow flow = getYFlow(request.getYFlowId());
        assertEquals(1000L, flow.getMaximumBandwidth());
        Set<SwitchId> expectedEndpointSwitchIds = Stream.of(SWITCH_FIRST_EP, SWITCH_SECOND_EP)
                .collect(Collectors.toSet());
        Set<SwitchId> actualEndpointSwitchIds = flow.getSubFlows().stream()
                .map(YSubFlow::getEndpointSwitchId)
                .collect(Collectors.toSet());
        assertEquals(expectedEndpointSwitchIds, actualEndpointSwitchIds);
    }

    @Test
    public void shouldFailIfNoPathAvailableForSecondSubFlow()
            throws UnroutableFlowException, RecoverableException, DuplicateKeyException {
        // given
        YFlowRequest request = createYFlow();
        request.setMaximumBandwidth(2000L);
        request.getSubFlows().get(0).setEndpoint(newFirstEndpoint);
        request.getSubFlows().get(1).setEndpoint(newSecondEndpoint);

        preparePathComputationForUpdate("test_flow_1", buildNewFirstSubFlowPathPair(), buildFirstSubFlowPathPair());
        when(pathComputer.getPath(buildFlowIdArgumentMatch("test_flow_2"), any(), anyBoolean()))
                .thenThrow(new UnroutableFlowException(injectedErrorMessage));
        prepareYPointComputation(SWITCH_SHARED, SWITCH_NEW_FIRST_EP, SWITCH_NEW_SECOND_EP, SWITCH_TRANSIT);

        // when
        processUpdateRequestAndSpeakerCommands(request, FlowStatus.IN_PROGRESS, FlowStatus.UP, FlowStatus.IN_PROGRESS);

        verifyNorthboundErrorResponse(yFlowUpdateHubCarrier, ErrorType.NOT_FOUND);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.UP);

        YFlow flow = getYFlow(request.getYFlowId());
        assertEquals(1000L, flow.getMaximumBandwidth());
        Set<SwitchId> expectedEndpointSwitchIds = Stream.of(SWITCH_FIRST_EP, SWITCH_SECOND_EP)
                .collect(Collectors.toSet());
        Set<SwitchId> actualEndpointSwitchIds = flow.getSubFlows().stream()
                .map(YSubFlow::getEndpointSwitchId)
                .collect(Collectors.toSet());
        assertEquals(expectedEndpointSwitchIds, actualEndpointSwitchIds);
    }

    @Test
    public void shouldFailIfNoResourcesAvailable()
            throws UnroutableFlowException, RecoverableException, ResourceAllocationException, DuplicateKeyException {
        // given
        YFlowRequest request = createYFlow();
        request.setMaximumBandwidth(2000L);
        request.getSubFlows().get(0).setEndpoint(newFirstEndpoint);
        request.getSubFlows().get(1).setEndpoint(newSecondEndpoint);

        preparePathComputationForUpdate("test_flow_1", buildNewFirstSubFlowPathPair(), buildFirstSubFlowPathPair());
        preparePathComputationForUpdate("test_flow_2", buildNewSecondSubFlowPathPair(), buildSecondSubFlowPathPair());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_NEW_FIRST_EP, SWITCH_NEW_SECOND_EP, SWITCH_TRANSIT);
        doThrow(new ResourceAllocationException(injectedErrorMessage))
                .when(flowResourcesManager).allocateMeter(eq("test_successful_yflow"), eq(SWITCH_TRANSIT));

        // when
        processUpdateRequestAndSpeakerCommands(request);

        verifyYFlowStatus(request.getYFlowId(), FlowStatus.UP);
        verify(flowResourcesManager, times(METER_ALLOCATION_RETRIES_LIMIT + 2)) // +1 from YFlowCreateFsm
                .allocateMeter(eq("test_successful_yflow"), eq(SWITCH_TRANSIT));

        YFlow flow = getYFlow(request.getYFlowId());
        assertEquals(1000L, flow.getMaximumBandwidth());
        Set<SwitchId> expectedEndpointSwitchIds = Stream.of(SWITCH_FIRST_EP, SWITCH_SECOND_EP)
                .collect(Collectors.toSet());
        Set<SwitchId> actualEndpointSwitchIds = flow.getSubFlows().stream()
                .map(YSubFlow::getEndpointSwitchId)
                .collect(Collectors.toSet());
        assertEquals(expectedEndpointSwitchIds, actualEndpointSwitchIds);
    }

    @Test
    public void shouldFailOnUnsuccessfulMeterInstallation()
            throws RecoverableException, UnroutableFlowException, DuplicateKeyException {
        // given
        YFlowRequest request = createYFlow();
        request.setMaximumBandwidth(2000L);
        request.getSubFlows().get(0).setEndpoint(newFirstEndpoint);
        request.getSubFlows().get(1).setEndpoint(newSecondEndpoint);

        preparePathComputationForUpdate("test_flow_1", buildNewFirstSubFlowPathPair(), buildFirstSubFlowPathPair());
        preparePathComputationForUpdate("test_flow_2", buildNewSecondSubFlowPathPair(), buildSecondSubFlowPathPair());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_NEW_FIRST_EP, SWITCH_NEW_SECOND_EP, SWITCH_TRANSIT);

        YFlowUpdateService service = makeYFlowUpdateService(0);

        // when
        service.handleRequest(request.getYFlowId(), new CommandContext(), request);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.IN_PROGRESS, FlowStatus.IN_PROGRESS, FlowStatus.UP);
        // and
        handleSpeakerCommandsAndFailInstall(service, request.getYFlowId(), "test_successful_yflow");

        // then
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.UP);
        YFlow flow = getYFlow(request.getYFlowId());
        assertEquals(1000L, flow.getMaximumBandwidth());
        Set<SwitchId> expectedEndpointSwitchIds = Stream.of(SWITCH_FIRST_EP, SWITCH_SECOND_EP)
                .collect(Collectors.toSet());
        Set<SwitchId> actualEndpointSwitchIds = flow.getSubFlows().stream()
                .map(YSubFlow::getEndpointSwitchId)
                .collect(Collectors.toSet());
        assertEquals(expectedEndpointSwitchIds, actualEndpointSwitchIds);
    }

    @Test
    public void shouldFailOnTimeoutDuringMeterInstallation()
            throws UnroutableFlowException, RecoverableException, DuplicateKeyException, UnknownKeyException {
        // given
        YFlowRequest request = createYFlow();
        request.setMaximumBandwidth(2000L);
        request.getSubFlows().get(0).setEndpoint(newFirstEndpoint);
        request.getSubFlows().get(1).setEndpoint(newSecondEndpoint);

        preparePathComputationForUpdate("test_flow_1", buildNewFirstSubFlowPathPair(), buildFirstSubFlowPathPair());
        preparePathComputationForUpdate("test_flow_2", buildNewSecondSubFlowPathPair(), buildSecondSubFlowPathPair());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_NEW_FIRST_EP, SWITCH_NEW_SECOND_EP, SWITCH_TRANSIT);

        YFlowUpdateService service = makeYFlowUpdateService(0);

        // when
        service.handleRequest(request.getYFlowId(), new CommandContext(), request);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.IN_PROGRESS);
        // and
        handleSpeakerCommandsAndTimeoutInstall(service, request.getYFlowId());

        // then
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.UP);
        YFlow flow = getYFlow(request.getYFlowId());
        assertEquals(1000L, flow.getMaximumBandwidth());
        Set<SwitchId> expectedEndpointSwitchIds = Stream.of(SWITCH_FIRST_EP, SWITCH_SECOND_EP)
                .collect(Collectors.toSet());
        Set<SwitchId> actualEndpointSwitchIds = flow.getSubFlows().stream()
                .map(YSubFlow::getEndpointSwitchId)
                .collect(Collectors.toSet());
        assertEquals(expectedEndpointSwitchIds, actualEndpointSwitchIds);
    }

    @Test
    public void shouldPatchFlowWithTransitSwitches()
            throws UnroutableFlowException, RecoverableException, DuplicateKeyException {
        // given
        createYFlow();
        List<SubFlowPartialUpdateDto> subFlowPartialUpdateDtos = new ArrayList<>();
        subFlowPartialUpdateDtos.add(SubFlowPartialUpdateDto.builder()
                .flowId("test_flow_1")
                .endpoint(FlowPartialUpdateEndpoint.builder()
                        .switchId(SWITCH_NEW_FIRST_EP).portNumber(2).vlanId(103).build())
                .build());
        subFlowPartialUpdateDtos.add(SubFlowPartialUpdateDto.builder()
                .flowId("test_flow_2")
                .endpoint(FlowPartialUpdateEndpoint.builder()
                        .switchId(SWITCH_NEW_SECOND_EP).portNumber(3).vlanId(104).build())
                .build());

        YFlowPartialUpdateRequest request = YFlowPartialUpdateRequest.builder()
                .yFlowId("test_successful_yflow")
                .maximumBandwidth(2000L)
                .subFlows(subFlowPartialUpdateDtos)
                .build();

        preparePathComputationForUpdate("test_flow_1", buildNewFirstSubFlowPathPair());
        preparePathComputationForUpdate("test_flow_2", buildNewSecondSubFlowPathPair());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_NEW_FIRST_EP, SWITCH_NEW_SECOND_EP, SWITCH_TRANSIT);

        // when
        processUpdateRequestAndSpeakerCommands(request);

        verifyNorthboundSuccessResponse(yFlowUpdateHubCarrier, YFlowResponse.class);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.UP);
        YFlow flow = getYFlow(request.getYFlowId());
        assertEquals(2000L, flow.getMaximumBandwidth());
        Set<SwitchId> expectedEndpointSwitchIds = Stream.of(SWITCH_NEW_FIRST_EP, SWITCH_NEW_SECOND_EP)
                .collect(Collectors.toSet());
        Set<SwitchId> actualEndpointSwitchIds = flow.getSubFlows().stream()
                .map(YSubFlow::getEndpointSwitchId)
                .collect(Collectors.toSet());
        assertEquals(expectedEndpointSwitchIds, actualEndpointSwitchIds);
    }

    private YFlowRequest createYFlow() throws UnroutableFlowException, RecoverableException, DuplicateKeyException {
        YFlowRequest request = buildYFlowRequest("test_successful_yflow", "test_flow_1", "test_flow_2")
                .build();
        preparePathComputationForCreate("test_flow_1", buildFirstSubFlowPathPair());
        preparePathComputationForCreate("test_flow_2", buildSecondSubFlowPathPair());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_TRANSIT);

        processCreateRequestAndSpeakerCommands(request);

        verifyNorthboundSuccessResponse(yFlowCreateHubCarrier, YFlowResponse.class);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.UP);
        verifyAffinity(request.getYFlowId());
        return request;
    }

    private YFlowRequest createYFlowWithProtectedPath()
            throws UnroutableFlowException, RecoverableException, DuplicateKeyException {
        YFlowRequest request = buildYFlowRequest("test_successful_yflow", "test_flow_1", "test_flow_2")
                .allocateProtectedPath(true)
                .build();
        preparePathComputationForCreate("test_flow_1",
                buildFirstSubFlowPathPair(), buildFirstSubFlowProtectedPathPair());
        preparePathComputationForCreate("test_flow_2",
                buildSecondSubFlowPathPair(), buildSecondSubFlowProtectedPathPair());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_TRANSIT, SWITCH_TRANSIT);
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_ALT_TRANSIT,
                SWITCH_ALT_TRANSIT);

        processCreateRequestAndSpeakerCommands(request);

        verifyNorthboundSuccessResponse(yFlowCreateHubCarrier, YFlowResponse.class);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.UP);

        return request;
    }

    private void processCreateRequestAndSpeakerCommands(YFlowRequest yFlowRequest) throws DuplicateKeyException {
        YFlowCreateService service = makeYFlowCreateService(0);
        service.handleRequest(yFlowRequest.getYFlowId(), new CommandContext(), yFlowRequest);

        verifyYFlowStatus(yFlowRequest.getYFlowId(), FlowStatus.IN_PROGRESS);

        handleSpeakerRequests(speakerRequest -> {
            SpeakerResponse commandResponse;
            if (speakerRequest instanceof FlowSegmentRequest) {
                FlowSegmentRequest flowSegmentRequest = (FlowSegmentRequest) speakerRequest;
                commandResponse = buildSuccessfulSpeakerResponse(flowSegmentRequest);
            } else {
                BaseSpeakerCommandsRequest speakerCommandsRequest = (BaseSpeakerCommandsRequest) speakerRequest;
                commandResponse = buildSuccessfulYFlowSpeakerResponse(speakerCommandsRequest);
            }
            handleAsyncResponse(service, yFlowRequest.getYFlowId(), commandResponse);
        });
    }

    private void processUpdateRequestAndSpeakerCommands(YFlowRequest yFlowRequest) throws DuplicateKeyException {
        YFlowUpdateService service = makeYFlowUpdateService(0);
        service.handleRequest(yFlowRequest.getYFlowId(), new CommandContext(), yFlowRequest);

        verifyYFlowStatus(yFlowRequest.getYFlowId(), FlowStatus.IN_PROGRESS);

        handleSpeakerRequests(speakerRequest -> {
            SpeakerResponse commandResponse;
            if (speakerRequest instanceof FlowSegmentRequest) {
                FlowSegmentRequest flowSegmentRequest = (FlowSegmentRequest) speakerRequest;
                commandResponse = buildSuccessfulSpeakerResponse(flowSegmentRequest);
            } else {
                BaseSpeakerCommandsRequest speakerCommandsRequest = (BaseSpeakerCommandsRequest) speakerRequest;
                commandResponse = buildSuccessfulYFlowSpeakerResponse(speakerCommandsRequest);
            }
            handleAsyncResponse(service, yFlowRequest.getYFlowId(), commandResponse);
        });
    }

    private void processUpdateRequestAndSpeakerCommands(YFlowPartialUpdateRequest request)
            throws DuplicateKeyException {
        YFlowUpdateService service = makeYFlowUpdateService(0);
        service.handlePartialUpdateRequest(request.getYFlowId(), new CommandContext(),
                request);

        verifyYFlowStatus(request.getYFlowId(), FlowStatus.IN_PROGRESS);

        handleSpeakerRequests(speakerRequest -> {
            SpeakerResponse commandResponse;
            if (speakerRequest instanceof FlowSegmentRequest) {
                FlowSegmentRequest flowSegmentRequest = (FlowSegmentRequest) speakerRequest;
                commandResponse = buildSuccessfulSpeakerResponse(flowSegmentRequest);
            } else {
                BaseSpeakerCommandsRequest speakerCommandsRequest = (BaseSpeakerCommandsRequest) speakerRequest;
                commandResponse = buildSuccessfulYFlowSpeakerResponse(speakerCommandsRequest);
            }
            handleAsyncResponse(service, request.getYFlowId(), commandResponse);
        });
    }

    private void processUpdateRequestAndSpeakerCommands(YFlowRequest request, FlowStatus expectedStatus,
                                                        FlowStatus expectedFirstSubFlowStatus,
                                                        FlowStatus expectedSecondSubFlowStatus)
            throws DuplicateKeyException {
        FlowUpdateService flowUpdateService = makeFlowUpdateService(0);
        YFlowUpdateService service = makeYFlowUpdateService(flowUpdateService, 0);
        service.handleRequest(request.getYFlowId(), new CommandContext(), request);

        verifyYFlowStatus(request.getYFlowId(), expectedStatus,
                expectedFirstSubFlowStatus, expectedSecondSubFlowStatus);

        handleSpeakerRequests(speakerRequest -> {
            SpeakerResponse commandResponse;
            if (speakerRequest instanceof FlowSegmentRequest) {
                FlowSegmentRequest flowSegmentRequest = (FlowSegmentRequest) speakerRequest;
                commandResponse = buildSuccessfulSpeakerResponse(flowSegmentRequest);
            } else {
                BaseSpeakerCommandsRequest speakerCommandsRequest = (BaseSpeakerCommandsRequest) speakerRequest;
                commandResponse = buildSuccessfulYFlowSpeakerResponse(speakerCommandsRequest);
            }
            handleAsyncResponse(service, request.getYFlowId(), commandResponse);
        });
    }

    private void handleAsyncResponse(YFlowCreateService yFlowCreateService,
                                     String yFlowFsmKey, SpeakerResponse commandResponse) {
        try {
            yFlowCreateService.handleAsyncResponse(yFlowFsmKey, commandResponse);
        } catch (UnknownKeyException ex) {
            //skip
        }
    }

    private void handleAsyncResponse(YFlowUpdateService yFlowUpdateService,
                                     String yFlowFsmKey, SpeakerResponse commandResponse) {
        try {
            yFlowUpdateService.handleAsyncResponse(yFlowFsmKey, commandResponse);
        } catch (UnknownKeyException ex) {
            //skip
        }
    }

    protected YFlow verifyYFlowStatus(String yFlowId, FlowStatus expectedStatus,
                                      FlowStatus expectedFirstSubFlowStatus, FlowStatus expectedSecondSubFlowStatus) {
        YFlow flow = getYFlow(yFlowId);
        assertEquals(expectedStatus, flow.getStatus());

        Set<FlowStatus> expectedSubFlowStatuses = Stream.of(expectedFirstSubFlowStatus, expectedSecondSubFlowStatus)
                .collect(Collectors.toSet());
        Set<FlowStatus> actualSubFlowStatuses = flow.getSubFlows().stream()
                .map(YSubFlow::getFlow)
                .map(Flow::getStatus)
                .collect(Collectors.toSet());

        assertEquals(expectedSubFlowStatuses, actualSubFlowStatuses);

        return flow;
    }

    private void handleTimeout(YFlowUpdateService yFlowUpdateService, String yFlowFsmKey) {
        try {
            yFlowUpdateService.handleTimeout(yFlowFsmKey);
        } catch (UnknownKeyException ex) {
            //skip
        }
    }

    private void handleSpeakerCommandsAndFailInstall(YFlowUpdateService yFlowUpdateService, String yFlowFsmKey,
                                                     String commandFlowIdToFail) {
        handleSpeakerRequests(request -> {
            SpeakerResponse commandResponse;
            if (request instanceof FlowSegmentRequest) {
                FlowSegmentRequest flowSegmentRequest = (FlowSegmentRequest) request;
                commandResponse = flowSegmentRequest.isInstallRequest()
                        && flowSegmentRequest.getMetadata().getFlowId().equals(commandFlowIdToFail)
                        ? buildErrorSpeakerResponse(flowSegmentRequest)
                        : buildSuccessfulSpeakerResponse(flowSegmentRequest);
            } else {
                BaseSpeakerCommandsRequest speakerCommandsRequest = (BaseSpeakerCommandsRequest) request;
                commandResponse = request instanceof InstallSpeakerCommandsRequest
                        ? buildErrorYFlowSpeakerResponse(speakerCommandsRequest)
                        : buildSuccessfulYFlowSpeakerResponse(speakerCommandsRequest);
            }
            handleAsyncResponse(yFlowUpdateService, yFlowFsmKey, commandResponse);
        });
    }

    private void handleSpeakerCommandsAndTimeoutInstall(YFlowUpdateService yFlowUpdateService, String yFlowFsmKey) {
        handleSpeakerRequests(request -> {
            SpeakerResponse commandResponse;
            if (request instanceof FlowSegmentRequest) {
                FlowSegmentRequest flowSegmentRequest = (FlowSegmentRequest) request;
                commandResponse = buildSuccessfulSpeakerResponse(flowSegmentRequest);
                yFlowUpdateService.handleAsyncResponse(yFlowFsmKey, commandResponse);
            } else {
                BaseSpeakerCommandsRequest speakerCommandsRequest = (BaseSpeakerCommandsRequest) request;
                if (speakerCommandsRequest instanceof InstallSpeakerCommandsRequest) {
                    handleTimeout(yFlowUpdateService, yFlowFsmKey);
                } else {
                    commandResponse = buildSuccessfulYFlowSpeakerResponse(speakerCommandsRequest);
                    yFlowUpdateService.handleAsyncResponse(yFlowFsmKey, commandResponse);
                }
            }
        });
    }

    private void preparePathComputationForCreate(String flowId, GetPathsResult pathPair)
            throws RecoverableException, UnroutableFlowException {
        when(pathComputer.getPath(buildFlowIdArgumentMatch(flowId))).thenReturn(pathPair);
    }

    private void preparePathComputationForCreate(String flowId, GetPathsResult pathPair, GetPathsResult pathPair2)
            throws RecoverableException, UnroutableFlowException {
        when(pathComputer.getPath(buildFlowIdArgumentMatch(flowId))).thenReturn(pathPair).thenReturn(pathPair2);
    }

    private void preparePathComputationForUpdate(String flowId, GetPathsResult pathPair)
            throws RecoverableException, UnroutableFlowException {
        when(pathComputer.getPath(buildFlowIdArgumentMatch(flowId), any(), anyBoolean())).thenReturn(pathPair);
    }

    private void preparePathComputationForUpdate(String flowId, GetPathsResult pathPair, GetPathsResult pathPair2)
            throws RecoverableException, UnroutableFlowException {
        when(pathComputer.getPath(buildFlowIdArgumentMatch(flowId), any(), anyBoolean()))
                .thenReturn(pathPair).thenReturn(pathPair2);
    }

    private void prepareYPointComputation(SwitchId sharedEndpoint, SwitchId first, SwitchId second, SwitchId yPoint) {
        prepareYPointComputation(sharedEndpoint, first, second, null, yPoint);
    }

    private void prepareYPointComputation(SwitchId sharedEndpoint, SwitchId first, SwitchId second, SwitchId transit,
                                          SwitchId yPoint) {
        ArgumentMatcher<FlowPath> pathArgumentMatcher = argument ->
                argument != null
                        // match both forward and reverse paths
                        && (argument.getSrcSwitchId().equals(first)
                        && argument.getDestSwitchId().equals(sharedEndpoint)
                        || argument.getSrcSwitchId().equals(sharedEndpoint)
                        && argument.getDestSwitchId().equals(first)
                        || argument.getSrcSwitchId().equals(second)
                        && argument.getDestSwitchId().equals(sharedEndpoint)
                        || argument.getSrcSwitchId().equals(sharedEndpoint)
                        && argument.getDestSwitchId().equals(second))
                        // if transit switch matching is requested
                        && argument.getSegments().stream()
                        .anyMatch(pathSegment -> transit == null
                                || pathSegment.getSrcSwitchId().equals(transit)
                                || pathSegment.getDestSwitchId().equals(transit));
        when(pathComputer.getIntersectionPoint(any(),
                ArgumentMatchers.argThat(pathArgumentMatcher),
                ArgumentMatchers.argThat(pathArgumentMatcher)))
                .thenReturn(yPoint);
    }

    private FlowUpdateService makeFlowUpdateService(int retriesLimit) {
        return new FlowUpdateService(flowUpdateHubCarrier, persistenceManager,
                pathComputer, flowResourcesManager, 3, 0, 3, retriesLimit);
    }

    private YFlowUpdateService makeYFlowUpdateService(int retriesLimit) {
        return makeYFlowUpdateService(makeFlowUpdateService(retriesLimit), retriesLimit);
    }

    private YFlowUpdateService makeYFlowUpdateService(FlowUpdateService flowUpdateService,
                                                      int retriesLimit) {
        return new YFlowUpdateService(yFlowUpdateHubCarrier, persistenceManager, pathComputer, flowResourcesManager,
                ruleManager, flowUpdateService, METER_ALLOCATION_RETRIES_LIMIT, retriesLimit, "", "");
    }

    private FlowCreateService makeFlowCreateService(int retriesLimit) {
        return new FlowCreateService(flowCreateHubCarrier, persistenceManager,
                pathComputer, flowResourcesManager, 0, 3, 0, retriesLimit);
    }

    private FlowDeleteService makeFlowDeleteService(int retriesLimit) {
        return new FlowDeleteService(flowDeleteHubCarrier, persistenceManager,
                flowResourcesManager, retriesLimit);
    }

    private YFlowCreateService makeYFlowCreateService(int retriesLimit) {
        return makeYFlowCreateService(makeFlowCreateService(retriesLimit), makeFlowDeleteService(retriesLimit),
                retriesLimit);
    }

    private YFlowCreateService makeYFlowCreateService(FlowCreateService flowCreateService,
                                                      FlowDeleteService flowDeleteService,
                                                      int retriesLimit) {
        return new YFlowCreateService(yFlowCreateHubCarrier, persistenceManager, pathComputer, flowResourcesManager,
                ruleManager, flowCreateService, flowDeleteService, METER_ALLOCATION_RETRIES_LIMIT, retriesLimit, "",
                "");
    }
}
