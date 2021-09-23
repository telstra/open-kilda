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

package org.openkilda.wfm.topology.flowhs.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse.ErrorCode;
import org.openkilda.messaging.command.yflow.YFlowRequest;
import org.openkilda.messaging.command.yflow.YFlowResponse;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.GetPathsResult;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class YFlowCreateServiceTest extends AbstractYFlowTest {
    private static final int METER_ALLOCATION_RETRIES_LIMIT = 3;

    @Mock
    private FlowCreateHubCarrier flowCreateHubCarrier;
    @Mock
    private FlowDeleteHubCarrier flowDeleteHubCarrier;
    @Mock
    private YFlowCreateHubCarrier yFlowCreateHubCarrier;

    @Before
    public void init() {
        doAnswer(getSpeakerCommandsAnswer())
                .when(flowCreateHubCarrier).sendSpeakerRequest(any(FlowSegmentRequest.class));
        doAnswer(getSpeakerCommandsAnswer())
                .when(flowDeleteHubCarrier).sendSpeakerRequest(any(FlowSegmentRequest.class));
        /*TODO: doAnswer(getSpeakerCommandsAnswer())
                .when(yFlowCreateHubCarrier).sendSpeakerRequest(any(FlowSegmentRequest.class));*/
    }

    @Test
    public void shouldCreateFlowWithTransitSwitches() throws UnroutableFlowException, RecoverableException {
        // given
        YFlowRequest request = makeYFlowRequest("test_successful_yflow", "test_flow_1", "test_flow_2")
                .build();
        preparePathComputation("test_flow_1", makeFirstSubFlowPathPair());
        preparePathComputation("test_flow_2", makeSecondSubFlowPathPair());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_TRANSIT);

        // when
        processRequestAndSpeakerCommands(request);

        verifyNorthboundSuccessResponse(yFlowCreateHubCarrier, YFlowResponse.class);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.UP);
    }

    @Test
    public void shouldCreateFlowWithProtectedPath() throws Exception {
        // given
        YFlowRequest request = makeYFlowRequest("test_successful_yflow", "test_flow_1", "test_flow_2")
                .allocateProtectedPath(true)
                .build();
        preparePathComputation("test_flow_1", makeFirstSubFlowPathPair(), makeFirstSubFlowProtectedPathPair());
        preparePathComputation("test_flow_2", makeSecondSubFlowPathPair(), makeSecondSubFlowProtectedPathPair());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_TRANSIT, SWITCH_TRANSIT);
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_ALT_TRANSIT,
                SWITCH_ALT_TRANSIT);

        // when
        processRequestAndSpeakerCommands(request);

        verifyNorthboundSuccessResponse(yFlowCreateHubCarrier, YFlowResponse.class);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.UP);
    }

    @Test
    public void shouldFailOnErrorDuringDraftYFlowCreation() throws RecoverableException, UnroutableFlowException {
        // given
        YFlowRequest request = makeYFlowRequest("test_successful_yflow", "test_flow_1", "test_flow_2")
                .build();
        YFlowRepository repository = setupYFlowRepositorySpy();
        doThrow(new RuntimeException(injectedErrorMessage))
                .when(repository)
                .add(ArgumentMatchers.argThat(argument -> argument.getYFlowId().equals(request.getYFlowId())));
        preparePathComputation("test_flow_1", makeFirstSubFlowPathPair());
        preparePathComputation("test_flow_2", makeSecondSubFlowPathPair());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_TRANSIT);

        // when
        processRequest(request);

        verifyNorthboundErrorResponse(yFlowCreateHubCarrier, ErrorType.INTERNAL_ERROR);
        verifyNoSpeakerInteraction(yFlowCreateHubCarrier);
        verifyYFlowIsAbsent(request.getYFlowId());
    }

    @Test
    public void shouldFailIfNoPathAvailable() throws RecoverableException, UnroutableFlowException {
        // given
        YFlowRequest request = makeYFlowRequest("test_successful_yflow", "test_flow_1", "test_flow_2")
                .build();
        when(pathComputer.getPath(makeFlowArgumentMatch("test_flow_1")))
                .thenThrow(new UnroutableFlowException(injectedErrorMessage));
        preparePathComputation("test_flow_2", makeSecondSubFlowPathPair());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_TRANSIT);

        // when
        processRequestAndSpeakerCommands(request);

        verifyNorthboundErrorResponse(yFlowCreateHubCarrier, ErrorType.NOT_FOUND);
        verifyNoSpeakerInteraction(yFlowCreateHubCarrier);
        verifyYFlowIsAbsent(request.getYFlowId());
    }

    @Test
    public void shouldFailIfNoResourcesAvailable()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        // given
        YFlowRequest request = makeYFlowRequest("test_successful_yflow", "test_flow_1", "test_flow_2")
                .build();
        preparePathComputation("test_flow_1", makeFirstSubFlowPathPair());
        preparePathComputation("test_flow_2", makeSecondSubFlowPathPair());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_TRANSIT);
        doThrow(new ResourceAllocationException(injectedErrorMessage))
                .when(flowResourcesManager).allocateMeter(eq("test_successful_yflow"), eq(SWITCH_TRANSIT));

        // when
        processRequestAndSpeakerCommands(request);

        verifyNoSpeakerInteraction(yFlowCreateHubCarrier);
        verify(flowResourcesManager, times(METER_ALLOCATION_RETRIES_LIMIT + 1))
                .allocateMeter(eq("test_successful_yflow"), eq(SWITCH_TRANSIT));
        verifyYFlowIsAbsent(request.getYFlowId());
    }

    @Ignore("TODO")
    @Test
    public void shouldFailOnUnsuccessfulMeterInstallation() throws RecoverableException, UnroutableFlowException {
        // given
        YFlowRequest request = makeYFlowRequest("test_successful_yflow", "test_flow_1", "test_flow_2")
                .build();
        preparePathComputation("test_flow_1", makeFirstSubFlowPathPair());
        preparePathComputation("test_flow_2", makeSecondSubFlowPathPair());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_TRANSIT);

        FlowCreateService flowCreateService = makeFlowCreateService(0);
        FlowDeleteService flowDeleteService = makeFlowDeleteService(0);
        YFlowCreateService service = makeYFlowCreateService(flowCreateService, flowDeleteService, 0);

        // when
        service.handleRequest(request.getYFlowId(), new CommandContext(), request);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.IN_PROGRESS);

        FlowSegmentRequest speakerRequest;
        while ((speakerRequest = requests.poll()) != null) {
            // For testing purpose we assume that key equals to flowId.
            String key = speakerRequest.getFlowId();
            if (speakerRequest.isInstallRequest() && key.equals("test_successful_yflow")) {
                service.handleAsyncResponse(key, FlowErrorResponse.errorBuilder()
                        .messageContext(speakerRequest.getMessageContext())
                        .errorCode(ErrorCode.UNKNOWN)
                        .description("Switch is unavailable")
                        .commandId(speakerRequest.getCommandId())
                        .metadata(speakerRequest.getMetadata())
                        .switchId(speakerRequest.getSwitchId())
                        .build());
            } else if (speakerRequest.isVerifyRequest()) {
                flowCreateService.handleAsyncResponse(key, buildResponseOnVerifyRequest(speakerRequest));
                flowDeleteService.handleAsyncResponse(key, buildResponseOnVerifyRequest(speakerRequest));
                service.handleAsyncResponse(key, buildResponseOnVerifyRequest(speakerRequest));
            } else {
                handleResponse(flowCreateService, key, speakerRequest);
                handleResponse(flowDeleteService, key, speakerRequest);
                handleResponse(service, key, speakerRequest);
            }
        }

        verifyNoNorthboundResponse(flowCreateHubCarrier);
        verifyNoSpeakerInteraction(yFlowCreateHubCarrier);
        verifyYFlowIsAbsent(request.getYFlowId());
    }

    @Test
    public void shouldFailOnTimeoutDuringMeterInstallation() throws RecoverableException, UnroutableFlowException {
        // given
        YFlowRequest request = makeYFlowRequest("test_successful_yflow", "test_flow_1", "test_flow_2")
                .build();
        preparePathComputation("test_flow_1", makeFirstSubFlowPathPair());
        preparePathComputation("test_flow_2", makeSecondSubFlowPathPair());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_TRANSIT);

        FlowCreateService flowCreateService = makeFlowCreateService(0);
        FlowDeleteService flowDeleteService = makeFlowDeleteService(0);
        YFlowCreateService service = makeYFlowCreateService(flowCreateService, flowDeleteService, 0);

        // when
        service.handleRequest(request.getYFlowId(), new CommandContext(), request);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.IN_PROGRESS);

        service.handleTimeout(request.getYFlowId());

        handleSpeakerRequests(flowCreateService, flowDeleteService, service);

        verifyNoNorthboundResponse(flowCreateHubCarrier);
        verifyNoSpeakerInteraction(yFlowCreateHubCarrier);
        verifyYFlowIsAbsent(request.getYFlowId());
    }

    @Ignore("TODO")
    @Test
    public void shouldFailOnUnsuccessfulValidation() throws RecoverableException, UnroutableFlowException {
        // given
        YFlowRequest request = makeYFlowRequest("test_successful_yflow", "test_flow_1", "test_flow_2")
                .build();
        preparePathComputation("test_flow_1", makeFirstSubFlowPathPair());
        preparePathComputation("test_flow_2", makeSecondSubFlowPathPair());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_TRANSIT);

        FlowCreateService flowCreateService = makeFlowCreateService(0);
        FlowDeleteService flowDeleteService = makeFlowDeleteService(0);
        YFlowCreateService service = makeYFlowCreateService(flowCreateService, flowDeleteService, 0);

        // when
        service.handleRequest(request.getYFlowId(), new CommandContext(), request);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.IN_PROGRESS);

        FlowSegmentRequest speakerRequest;
        while ((speakerRequest = requests.poll()) != null) {
            // For testing purpose we assume that key equals to flowId.
            String key = speakerRequest.getFlowId();
            if (speakerRequest.isVerifyRequest() && key.equals("test_successful_yflow")) {
                service.handleAsyncResponse(key, FlowErrorResponse.errorBuilder()
                        .messageContext(speakerRequest.getMessageContext())
                        .errorCode(ErrorCode.UNKNOWN)
                        .description("Switch is unavailable")
                        .commandId(speakerRequest.getCommandId())
                        .metadata(speakerRequest.getMetadata())
                        .switchId(speakerRequest.getSwitchId())
                        .build());
            } else if (speakerRequest.isVerifyRequest()) {
                flowCreateService.handleAsyncResponse(key, buildResponseOnVerifyRequest(speakerRequest));
                flowDeleteService.handleAsyncResponse(key, buildResponseOnVerifyRequest(speakerRequest));
                service.handleAsyncResponse(key, buildResponseOnVerifyRequest(speakerRequest));
            } else {
                handleResponse(flowCreateService, key, speakerRequest);
                handleResponse(flowDeleteService, key, speakerRequest);
                handleResponse(service, key, speakerRequest);
            }
        }

        verifyNoNorthboundResponse(flowCreateHubCarrier);
        verifyNoSpeakerInteraction(yFlowCreateHubCarrier);
        verifyYFlowIsAbsent(request.getYFlowId());
    }

    @Ignore("TODO")
    @Test
    public void shouldFailOnTimeoutDuringValidation() throws RecoverableException, UnroutableFlowException {
        // given
        YFlowRequest request = makeYFlowRequest("test_successful_yflow", "test_flow_1", "test_flow_2")
                .build();
        preparePathComputation("test_flow_1", makeFirstSubFlowPathPair());
        preparePathComputation("test_flow_2", makeSecondSubFlowPathPair());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_TRANSIT);

        FlowCreateService flowCreateService = makeFlowCreateService(0);
        FlowDeleteService flowDeleteService = makeFlowDeleteService(0);
        YFlowCreateService service = makeYFlowCreateService(flowCreateService, flowDeleteService, 0);

        // when
        service.handleRequest(request.getYFlowId(), new CommandContext(), request);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.IN_PROGRESS);

        FlowSegmentRequest speakerRequest;
        while ((speakerRequest = requests.poll()) != null) {
            // For testing purpose we assume that key equals to flowId.
            String key = speakerRequest.getFlowId();
            if (speakerRequest.isVerifyRequest() && key.equals("test_successful_yflow")) {
                service.handleTimeout(key);
            } else if (speakerRequest.isVerifyRequest()) {
                flowCreateService.handleAsyncResponse(key, buildResponseOnVerifyRequest(speakerRequest));
                flowDeleteService.handleAsyncResponse(key, buildResponseOnVerifyRequest(speakerRequest));
                service.handleAsyncResponse(key, buildResponseOnVerifyRequest(speakerRequest));
            } else {
                handleResponse(flowCreateService, key, speakerRequest);
                handleResponse(flowDeleteService, key, speakerRequest);
                handleResponse(service, key, speakerRequest);
            }
        }

        verifyNoNorthboundResponse(flowCreateHubCarrier);
        verifyNoSpeakerInteraction(yFlowCreateHubCarrier);
        verifyYFlowIsAbsent(request.getYFlowId());
    }

    private void processRequestAndSpeakerCommands(YFlowRequest yFlowRequest) {
        FlowCreateService flowCreateService = makeFlowCreateService(0);
        FlowDeleteService flowDeleteService = makeFlowDeleteService(0);
        YFlowCreateService service = makeYFlowCreateService(flowCreateService, flowDeleteService, 0);
        service.handleRequest(yFlowRequest.getYFlowId(), new CommandContext(), yFlowRequest);

        verifyYFlowStatus(yFlowRequest.getYFlowId(), FlowStatus.IN_PROGRESS);

        handleSpeakerRequests(flowCreateService, flowDeleteService, service);

        verifyNoNorthboundResponse(flowCreateHubCarrier);
    }

    private void processRequest(YFlowRequest yFlowRequest) {
        YFlowCreateService service = makeYFlowCreateService(0);
        service.handleRequest(yFlowRequest.getYFlowId(), new CommandContext(), yFlowRequest);
    }

    private void handleSpeakerRequests(FlowCreateService flowCreateService, FlowDeleteService flowDeleteService,
                                       YFlowCreateService service) {
        FlowSegmentRequest request;
        while ((request = requests.poll()) != null) {
            // For testing purpose we assume that key equals to flowId.
            String key = request.getFlowId();
            if (request.isVerifyRequest()) {
                flowCreateService.handleAsyncResponse(key, buildResponseOnVerifyRequest(request));
                flowDeleteService.handleAsyncResponse(key, buildResponseOnVerifyRequest(request));
                service.handleAsyncResponse(key, buildResponseOnVerifyRequest(request));
            } else {
                handleResponse(flowCreateService, key, request);
                handleResponse(flowDeleteService, key, request);
                handleResponse(service, key, request);
            }
        }
    }

    private void handleResponse(FlowCreateService service, String key, FlowSegmentRequest request) {
        service.handleAsyncResponse(key, SpeakerFlowSegmentResponse.builder()
                .messageContext(request.getMessageContext())
                .metadata(request.getMetadata())
                .commandId(request.getCommandId())
                .switchId(request.getSwitchId())
                .success(true)
                .build());
    }

    private void handleResponse(FlowDeleteService service, String key, FlowSegmentRequest request) {
        service.handleAsyncResponse(key, SpeakerFlowSegmentResponse.builder()
                .messageContext(request.getMessageContext())
                .metadata(request.getMetadata())
                .commandId(request.getCommandId())
                .switchId(request.getSwitchId())
                .success(true)
                .build());
    }

    private void handleResponse(YFlowCreateService service, String key, FlowSegmentRequest request) {
        service.handleAsyncResponse(key, SpeakerFlowSegmentResponse.builder()
                .messageContext(request.getMessageContext())
                .metadata(request.getMetadata())
                .commandId(request.getCommandId())
                .switchId(request.getSwitchId())
                .success(true)
                .build());
    }

    private void handleErrorResponse(YFlowCreateService service, String key, FlowSegmentRequest request,
                                     ErrorCode errorCode) {
        service.handleAsyncResponse(key, FlowErrorResponse.errorBuilder()
                .messageContext(request.getMessageContext())
                .metadata(request.getMetadata())
                .commandId(request.getCommandId())
                .switchId(request.getSwitchId())
                .errorCode(errorCode)
                .build());
    }

    private void preparePathComputation(String flowId, GetPathsResult pathPair)
            throws RecoverableException, UnroutableFlowException {
        when(pathComputer.getPath(makeFlowArgumentMatch(flowId))).thenReturn(pathPair);
    }

    private void preparePathComputation(String flowId, GetPathsResult pathPair, GetPathsResult pathPair2)
            throws RecoverableException, UnroutableFlowException {
        when(pathComputer.getPath(makeFlowArgumentMatch(flowId))).thenReturn(pathPair).thenReturn(pathPair2);
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
                flowCreateService, flowDeleteService, METER_ALLOCATION_RETRIES_LIMIT, retriesLimit);
    }
}
