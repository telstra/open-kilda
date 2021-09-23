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
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.exception.UnknownKeyException;

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
    public void shouldCreateFlowWithTransitSwitches()
            throws UnroutableFlowException, RecoverableException, DuplicateKeyException {
        // given
        YFlowRequest request = buildYFlowRequest("test_successful_yflow", "test_flow_1", "test_flow_2")
                .build();
        preparePathComputation("test_flow_1", buildFirstSubFlowPathPair());
        preparePathComputation("test_flow_2", buildSecondSubFlowPathPair());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_TRANSIT);

        // when
        processRequestAndSpeakerCommands(request);
        // then
        verifyNorthboundSuccessResponse(yFlowCreateHubCarrier, YFlowResponse.class);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.UP);
    }

    @Test
    public void shouldCreateFlowWithProtectedPath() throws Exception {
        // given
        YFlowRequest request = buildYFlowRequest("test_successful_yflow", "test_flow_1", "test_flow_2")
                .allocateProtectedPath(true)
                .build();
        preparePathComputation("test_flow_1", buildFirstSubFlowPathPair(), buildFirstSubFlowProtectedPathPair());
        preparePathComputation("test_flow_2", buildSecondSubFlowPathPair(), buildSecondSubFlowProtectedPathPair());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_TRANSIT, SWITCH_TRANSIT);
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_ALT_TRANSIT,
                SWITCH_ALT_TRANSIT);

        // when
        processRequestAndSpeakerCommands(request);
        // then
        verifyNorthboundSuccessResponse(yFlowCreateHubCarrier, YFlowResponse.class);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.UP);
    }

    @Test
    public void shouldFailOnErrorDuringDraftYFlowCreation()
            throws RecoverableException, UnroutableFlowException, DuplicateKeyException {
        // given
        YFlowRequest request = buildYFlowRequest("test_successful_yflow", "test_flow_1", "test_flow_2")
                .build();
        YFlowRepository repository = setupYFlowRepositorySpy();
        doThrow(new RuntimeException(injectedErrorMessage))
                .when(repository)
                .add(ArgumentMatchers.argThat(argument -> argument.getYFlowId().equals(request.getYFlowId())));
        preparePathComputation("test_flow_1", buildFirstSubFlowPathPair());
        preparePathComputation("test_flow_2", buildSecondSubFlowPathPair());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_TRANSIT);

        // when
        processRequest(request);
        // then
        verifyNorthboundErrorResponse(yFlowCreateHubCarrier, ErrorType.INTERNAL_ERROR);
        verifyNoSpeakerInteraction(yFlowCreateHubCarrier);
        verifyYFlowIsAbsent(request.getYFlowId());
    }

    @Test
    public void shouldFailIfNoPathAvailable()
            throws RecoverableException, UnroutableFlowException, DuplicateKeyException {
        // given
        YFlowRequest request = buildYFlowRequest("test_successful_yflow", "test_flow_1", "test_flow_2")
                .build();
        when(pathComputer.getPath(buildFlowIdArgumentMatch("test_flow_1")))
                .thenThrow(new UnroutableFlowException(injectedErrorMessage));
        preparePathComputation("test_flow_2", buildSecondSubFlowPathPair());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_TRANSIT);

        // when
        processRequestAndSpeakerCommands(request);
        // then
        verifyNorthboundErrorResponse(yFlowCreateHubCarrier, ErrorType.NOT_FOUND);
        verifyNoSpeakerInteraction(yFlowCreateHubCarrier);
        verifyYFlowIsAbsent(request.getYFlowId());
    }

    @Test
    public void shouldFailIfNoResourcesAvailable()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException, DuplicateKeyException {
        // given
        YFlowRequest request = buildYFlowRequest("test_successful_yflow", "test_flow_1", "test_flow_2")
                .build();
        preparePathComputation("test_flow_1", buildFirstSubFlowPathPair());
        preparePathComputation("test_flow_2", buildSecondSubFlowPathPair());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_TRANSIT);
        doThrow(new ResourceAllocationException(injectedErrorMessage))
                .when(flowResourcesManager).allocateMeter(eq("test_successful_yflow"), eq(SWITCH_TRANSIT));

        // when
        processRequestAndSpeakerCommands(request);
        // then
        verifyNoSpeakerInteraction(yFlowCreateHubCarrier);
        verify(flowResourcesManager, times(METER_ALLOCATION_RETRIES_LIMIT + 1))
                .allocateMeter(eq("test_successful_yflow"), eq(SWITCH_TRANSIT));
        verifyYFlowIsAbsent(request.getYFlowId());
    }

    @Ignore("TODO: implement meter installation")
    @Test
    public void shouldFailOnUnsuccessfulMeterInstallation()
            throws RecoverableException, UnroutableFlowException, DuplicateKeyException {
        // given
        YFlowRequest request = buildYFlowRequest("test_successful_yflow", "test_flow_1", "test_flow_2")
                .build();
        preparePathComputation("test_flow_1", buildFirstSubFlowPathPair());
        preparePathComputation("test_flow_2", buildSecondSubFlowPathPair());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_TRANSIT);

        YFlowCreateService service = makeYFlowCreateService(0);

        // when
        service.handleRequest(request.getYFlowId(), new CommandContext(), request);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.IN_PROGRESS);
        // and
        handleSpeakerCommandsAndFailInstall(service, request.getYFlowId(), "test_successful_yflow");

        // then
        verifyNoNorthboundResponse(flowCreateHubCarrier);
        verifyNoNorthboundResponse(flowDeleteHubCarrier);
        verifyNoSpeakerInteraction(yFlowCreateHubCarrier);
        verifyYFlowIsAbsent(request.getYFlowId());
    }

    @Ignore("TODO: implement meter installation")
    @Test
    public void shouldFailOnTimeoutDuringMeterInstallation()
            throws RecoverableException, UnroutableFlowException, DuplicateKeyException {
        // given
        YFlowRequest request = buildYFlowRequest("test_successful_yflow", "test_flow_1", "test_flow_2")
                .build();
        preparePathComputation("test_flow_1", buildFirstSubFlowPathPair());
        preparePathComputation("test_flow_2", buildSecondSubFlowPathPair());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_TRANSIT);

        YFlowCreateService service = makeYFlowCreateService(0);

        // when
        service.handleRequest(request.getYFlowId(), new CommandContext(), request);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.IN_PROGRESS);
        // and
        handleSpeakerCommandsAndTimeoutInstall(service, request.getYFlowId(), "test_successful_yflow");

        // then
        verifyNoNorthboundResponse(flowCreateHubCarrier);
        verifyNoNorthboundResponse(flowDeleteHubCarrier);
        verifyNoSpeakerInteraction(yFlowCreateHubCarrier);
        verifyYFlowIsAbsent(request.getYFlowId());
    }

    @Ignore("TODO: implement meter validation")
    @Test
    public void shouldFailOnUnsuccessfulValidation()
            throws RecoverableException, UnroutableFlowException, DuplicateKeyException {
        // given
        YFlowRequest request = buildYFlowRequest("test_successful_yflow", "test_flow_1", "test_flow_2")
                .build();
        preparePathComputation("test_flow_1", buildFirstSubFlowPathPair());
        preparePathComputation("test_flow_2", buildSecondSubFlowPathPair());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_TRANSIT);

        YFlowCreateService service = makeYFlowCreateService(0);

        // when
        service.handleRequest(request.getYFlowId(), new CommandContext(), request);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.IN_PROGRESS);
        // and
        handleSpeakerCommandsAndFailVerify(service, request.getYFlowId(), "test_successful_yflow");

        // then
        verifyNoNorthboundResponse(flowCreateHubCarrier);
        verifyNoNorthboundResponse(flowDeleteHubCarrier);
        verifyNoSpeakerInteraction(yFlowCreateHubCarrier);
        verifyYFlowIsAbsent(request.getYFlowId());
    }

    @Ignore("TODO: implement meter validation")
    @Test
    public void shouldFailOnTimeoutDuringMeterValidation()
            throws RecoverableException, UnroutableFlowException, DuplicateKeyException {
        // given
        YFlowRequest request = buildYFlowRequest("test_successful_yflow", "test_flow_1", "test_flow_2")
                .build();
        preparePathComputation("test_flow_1", buildFirstSubFlowPathPair());
        preparePathComputation("test_flow_2", buildSecondSubFlowPathPair());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_TRANSIT);

        YFlowCreateService service = makeYFlowCreateService(0);

        // when
        service.handleRequest(request.getYFlowId(), new CommandContext(), request);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.IN_PROGRESS);
        // and
        handleSpeakerCommandsAndTimeoutVerify(service, request.getYFlowId(), "test_successful_yflow");

        // then
        verifyNoNorthboundResponse(flowCreateHubCarrier);
        verifyNoNorthboundResponse(flowDeleteHubCarrier);
        verifyNoSpeakerInteraction(yFlowCreateHubCarrier);
        verifyYFlowIsAbsent(request.getYFlowId());
    }

    private void processRequestAndSpeakerCommands(YFlowRequest yFlowRequest) throws DuplicateKeyException {
        YFlowCreateService service = makeYFlowCreateService(0);
        service.handleRequest(yFlowRequest.getYFlowId(), new CommandContext(), yFlowRequest);

        verifyYFlowStatus(yFlowRequest.getYFlowId(), FlowStatus.IN_PROGRESS);

        handleSpeakerCommands(speakerRequest -> {
            SpeakerFlowSegmentResponse commandResponse = buildSuccessfulSpeakerResponse(speakerRequest);
            handleAsyncResponse(service, yFlowRequest.getYFlowId(), commandResponse);
        });

        // FlowCreate & FlowDelete service / FSM mustn't emit anything to NB
        verifyNoNorthboundResponse(flowCreateHubCarrier);
        verifyNoNorthboundResponse(flowDeleteHubCarrier);
    }

    private void handleAsyncResponse(YFlowCreateService yFlowCreateService,
                                     String key, SpeakerFlowSegmentResponse commandResponse) {
        try {
            yFlowCreateService.handleAsyncResponse(key, commandResponse);
        } catch (UnknownKeyException ex) {
            //skip
        }
    }

    private void processRequest(YFlowRequest yFlowRequest) throws DuplicateKeyException {
        YFlowCreateService service = makeYFlowCreateService(0);
        service.handleRequest(yFlowRequest.getYFlowId(), new CommandContext(), yFlowRequest);
    }

    private void handleSpeakerCommandsAndFailInstall(YFlowCreateService yFlowCreateService, String key,
                                                     String commandKeyToFail) {
        handleSpeakerCommands(request -> {
            SpeakerFlowSegmentResponse commandResponse = request.isInstallRequest()
                    && request.getMetadata().getFlowId().equals(commandKeyToFail)
                    ? buildErrorSpeakerResponse(request) : buildSuccessfulSpeakerResponse(request);
            handleAsyncResponse(yFlowCreateService, key, commandResponse);
        });
    }

    private void handleSpeakerCommandsAndFailVerify(YFlowCreateService yFlowCreateService, String key,
                                                    String commandKeyToFail) {
        handleSpeakerCommands(request -> {
            SpeakerFlowSegmentResponse commandResponse = request.isVerifyRequest() && key.equals(commandKeyToFail)
                    ? buildErrorSpeakerResponse(request) : buildSuccessfulSpeakerResponse(request);
            handleAsyncResponse(yFlowCreateService, key, commandResponse);
        });
    }

    private void handleSpeakerCommandsAndTimeoutInstall(YFlowCreateService yFlowCreateService, String key,
                                                        String commandKeyToFail) {
        handleSpeakerCommands(request -> {
            if (request.isInstallRequest() && key.equals(commandKeyToFail)) {
                handleTimeout(yFlowCreateService, key);
            }
            SpeakerFlowSegmentResponse commandResponse = buildSuccessfulSpeakerResponse(request);
            yFlowCreateService.handleAsyncResponse(key, commandResponse);
        });
    }

    private void handleTimeout(YFlowCreateService yFlowCreateService, String key) {
        try {
            yFlowCreateService.handleTimeout(key);
        } catch (UnknownKeyException ex) {
            //skip
        }
    }

    private void handleSpeakerCommandsAndTimeoutVerify(YFlowCreateService yFlowCreateService, String key,
                                                       String commandKeyToFail) {
        handleSpeakerCommands(request -> {
            if (request.isVerifyRequest() && key.equals(commandKeyToFail)) {
                handleTimeout(yFlowCreateService, key);
            }
            SpeakerFlowSegmentResponse commandResponse = buildSuccessfulSpeakerResponse(request);
            yFlowCreateService.handleAsyncResponse(key, commandResponse);
        });
    }

    private void preparePathComputation(String flowId, GetPathsResult pathPair)
            throws RecoverableException, UnroutableFlowException {
        when(pathComputer.getPath(buildFlowIdArgumentMatch(flowId))).thenReturn(pathPair);
    }

    private void preparePathComputation(String flowId, GetPathsResult pathPair, GetPathsResult pathPair2)
            throws RecoverableException, UnroutableFlowException {
        when(pathComputer.getPath(buildFlowIdArgumentMatch(flowId))).thenReturn(pathPair).thenReturn(pathPair2);
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
                flowCreateService, flowDeleteService, METER_ALLOCATION_RETRIES_LIMIT, retriesLimit, "", "");
    }
}
