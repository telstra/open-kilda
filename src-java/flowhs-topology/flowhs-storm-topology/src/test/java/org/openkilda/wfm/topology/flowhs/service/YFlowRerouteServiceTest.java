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

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.messaging.command.yflow.YFlowRequest;
import org.openkilda.messaging.command.yflow.YFlowRerouteRequest;
import org.openkilda.messaging.command.yflow.YFlowRerouteResponse;
import org.openkilda.messaging.command.yflow.YFlowResponse;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.reroute.error.RerouteError;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.IslEndpoint;
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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RunWith(MockitoJUnitRunner.class)
public class YFlowRerouteServiceTest extends AbstractYFlowTest {
    private static final int METER_ALLOCATION_RETRIES_LIMIT = 3;

    @Mock
    private FlowCreateHubCarrier flowCreateHubCarrier;
    @Mock
    private FlowDeleteHubCarrier flowDeleteHubCarrier;
    @Mock
    private YFlowCreateHubCarrier yFlowCreateHubCarrier;
    @Mock
    private FlowRerouteHubCarrier flowRerouteHubCarrier;
    @Mock
    private YFlowRerouteHubCarrier yFlowRerouteHubCarrier;

    @Before
    public void init() {
        doAnswer(getSpeakerCommandsAnswer())
                .when(flowCreateHubCarrier).sendSpeakerRequest(any(FlowSegmentRequest.class));
        doAnswer(getSpeakerCommandsAnswer())
                .when(flowRerouteHubCarrier).sendSpeakerRequest(any(FlowSegmentRequest.class));
        /*TODO: doAnswer(getSpeakerCommandsAnswer())
                .when(yFlowUpdateHubCarrier).sendSpeakerRequest(any(FlowSegmentRequest.class));*/
    }

    @Test
    public void shouldRerouteFlowWithTransitSwitches()
            throws UnroutableFlowException, RecoverableException, DuplicateKeyException {
        // given
        YFlowRequest createYFlowRequest = createYFlow();
        YFlowRerouteRequest request = new YFlowRerouteRequest(createYFlowRequest.getYFlowId(), "reason");

        preparePathComputationForReroute("test_flow_1", buildFirstSubFlowPathPairWithNewTransit());
        preparePathComputationForReroute("test_flow_2", buildSecondSubFlowPathPairWithNewTransit());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_NEW_TRANSIT);

        // when
        processRerouteRequestAndSpeakerCommands(request);

        verifyNorthboundSuccessResponse(yFlowRerouteHubCarrier, YFlowRerouteResponse.class);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.UP);
        verifyAffinity(request.getYFlowId());
        verify(yFlowRerouteHubCarrier)
                .sendYFlowRerouteResultStatus(eq(createYFlowRequest.getYFlowId()), eq(null), anyString());
    }

    @Test
    public void shouldUpdateFlowWithProtectedPath()
            throws UnroutableFlowException, RecoverableException, DuplicateKeyException {
        // given
        YFlowRequest createYFlowRequest = createYFlowWithProtectedPath();
        YFlowRerouteRequest request = new YFlowRerouteRequest(createYFlowRequest.getYFlowId(), "reason");

        preparePathComputationForReroute("test_flow_1",
                buildFirstSubFlowPathPair(), buildFirstSubFlowPathPairWithNewTransit());
        preparePathComputationForReroute("test_flow_2",
                buildSecondSubFlowPathPair(), buildSecondSubFlowPathPairWithNewTransit());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP,
                SWITCH_TRANSIT, SWITCH_TRANSIT);
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP,
                SWITCH_NEW_TRANSIT, SWITCH_NEW_TRANSIT);

        // when
        processRerouteRequestAndSpeakerCommands(request);

        verifyNorthboundSuccessResponse(yFlowRerouteHubCarrier, YFlowRerouteResponse.class);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.UP);
        verifyAffinity(request.getYFlowId());
        verify(yFlowRerouteHubCarrier)
                .sendYFlowRerouteResultStatus(eq(createYFlowRequest.getYFlowId()), eq(null), anyString());
    }

    @Test
    public void shouldFailIfNoPathAvailableForFirstSubFlow()
            throws UnroutableFlowException, RecoverableException, DuplicateKeyException {
        // given
        YFlowRequest createYFlowRequest = createYFlow();
        YFlowRerouteRequest request = new YFlowRerouteRequest(createYFlowRequest.getYFlowId(), "reason");

        when(pathComputer.getPath(buildFlowIdArgumentMatch("test_flow_1"), any()))
                .thenThrow(new UnroutableFlowException(injectedErrorMessage));
        preparePathComputationForReroute("test_flow_2", buildSecondSubFlowPathPairWithNewTransit());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_NEW_TRANSIT);

        // when
        processRerouteRequest(request);

        verifyNorthboundErrorResponse(yFlowRerouteHubCarrier, ErrorType.NOT_FOUND);
        verifyNoSpeakerInteraction(yFlowRerouteHubCarrier);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.DEGRADED, FlowStatus.DOWN, FlowStatus.UP);
        verify(yFlowRerouteHubCarrier).sendYFlowRerouteResultStatus(eq(createYFlowRequest.getYFlowId()),
                eq(new RerouteError("Failed to reroute sub-flows [test_flow_1] of y-flow test_successful_yflow")),
                anyString());
    }

    @Test
    public void shouldFailIfNoPathAvailableForSecondSubFlow()
            throws UnroutableFlowException, RecoverableException, DuplicateKeyException {
        // given
        YFlowRequest createYFlowRequest = createYFlow();
        YFlowRerouteRequest request = new YFlowRerouteRequest(createYFlowRequest.getYFlowId(), "reason");

        preparePathComputationForReroute("test_flow_1", buildFirstSubFlowPathPairWithNewTransit());
        when(pathComputer.getPath(buildFlowIdArgumentMatch("test_flow_2"), any()))
                .thenThrow(new UnroutableFlowException(injectedErrorMessage));
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_NEW_TRANSIT);

        // when
        processRerouteRequestAndSpeakerCommands(request,
                FlowStatus.IN_PROGRESS, FlowStatus.IN_PROGRESS, FlowStatus.DOWN);

        verifyNorthboundErrorResponse(yFlowRerouteHubCarrier, ErrorType.NOT_FOUND);
        verifyNoSpeakerInteraction(yFlowRerouteHubCarrier);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.DEGRADED, FlowStatus.UP, FlowStatus.DOWN);
        verify(yFlowRerouteHubCarrier).sendYFlowRerouteResultStatus(eq(createYFlowRequest.getYFlowId()),
                eq(new RerouteError("Failed to reroute sub-flows [test_flow_2] of y-flow test_successful_yflow")),
                anyString());
    }

    @Test
    public void shouldFailIfNoResourcesAvailable()
            throws UnroutableFlowException, RecoverableException, ResourceAllocationException, DuplicateKeyException {
        // given
        YFlowRequest createYFlowRequest = createYFlow();
        YFlowRerouteRequest request = new YFlowRerouteRequest(createYFlowRequest.getYFlowId(), "reason");

        preparePathComputationForReroute("test_flow_1", buildFirstSubFlowPathPairWithNewTransit());
        preparePathComputationForReroute("test_flow_2", buildSecondSubFlowPathPairWithNewTransit());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_NEW_TRANSIT);
        doThrow(new ResourceAllocationException(injectedErrorMessage))
                .when(flowResourcesManager).allocateMeter(eq("test_successful_yflow"), eq(SWITCH_NEW_TRANSIT));

        // when
        processRerouteRequestAndSpeakerCommands(request);

        verifyNoSpeakerInteraction(yFlowRerouteHubCarrier);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.UP);
        verify(flowResourcesManager, times(METER_ALLOCATION_RETRIES_LIMIT + 1))
                .allocateMeter(eq("test_successful_yflow"), eq(SWITCH_NEW_TRANSIT));
        verify(yFlowRerouteHubCarrier).sendYFlowRerouteResultStatus(eq(createYFlowRequest.getYFlowId()),
                eq(new RerouteError("Failed to allocate y-flow resources. Unit-test injected failure")),
                anyString());
    }

    @Test
    public void shouldRerouteFlowWithTransitSwitchesWithAffectedIsl()
            throws UnroutableFlowException, RecoverableException, DuplicateKeyException {
        // given
        YFlowRequest createYFlowRequest = createYFlow();
        YFlowRerouteRequest request = new YFlowRerouteRequest(createYFlowRequest.getYFlowId(), "reason");
        request.setAffectedIsl(Collections.singleton(new IslEndpoint(SWITCH_TRANSIT, 25)));

        preparePathComputationForReroute("test_flow_1", buildFirstSubFlowPathPairWithNewTransit());
        preparePathComputationForReroute("test_flow_2", buildSecondSubFlowPathPairWithNewTransit());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_NEW_TRANSIT);

        // when
        processRerouteRequestAndSpeakerCommands(request);

        verifyNorthboundSuccessResponse(yFlowRerouteHubCarrier, YFlowRerouteResponse.class);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.UP);
        verifyAffinity(request.getYFlowId());
        verify(yFlowRerouteHubCarrier)
                .sendYFlowRerouteResultStatus(eq(createYFlowRequest.getYFlowId()), eq(null), anyString());
    }

    @Test
    public void shouldRerouteFlowWithTransitSwitchesWithMainFlowAffectedIsl()
            throws UnroutableFlowException, RecoverableException, DuplicateKeyException {
        // given
        YFlowRequest createYFlowRequest = createYFlow();
        YFlowRerouteRequest request = new YFlowRerouteRequest(createYFlowRequest.getYFlowId(), "reason");
        request.setAffectedIsl(Collections.singleton(new IslEndpoint(SWITCH_TRANSIT, 26)));

        preparePathComputationForReroute("test_flow_1", buildFirstSubFlowPathPairWithNewTransit());
        preparePathComputationForReroute("test_flow_2", buildSecondSubFlowPathPairWithNewTransit());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_NEW_TRANSIT);

        // when
        processRerouteRequestAndSpeakerCommands(request);

        verifyNorthboundSuccessResponse(yFlowRerouteHubCarrier, YFlowRerouteResponse.class);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.UP);
        verifyAffinity(request.getYFlowId());
        verify(yFlowRerouteHubCarrier)
                .sendYFlowRerouteResultStatus(eq(createYFlowRequest.getYFlowId()), eq(null), anyString());
    }

    @Test
    public void shouldRerouteFlowWithTransitSwitchesWithSecondaryFlowAffectedIsl()
            throws UnroutableFlowException, RecoverableException, DuplicateKeyException {
        // given
        YFlowRequest createYFlowRequest = createYFlow();
        YFlowRerouteRequest request = new YFlowRerouteRequest(createYFlowRequest.getYFlowId(), "reason");
        request.setAffectedIsl(Collections.singleton(new IslEndpoint(SWITCH_TRANSIT, 27)));

        preparePathComputationForReroute("test_flow_2", buildSecondSubFlowPathPairWithNewTransit());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_SHARED);

        // when
        processRerouteRequestAndSpeakerCommands(request, FlowStatus.IN_PROGRESS, FlowStatus.UP, FlowStatus.IN_PROGRESS);

        verifyNorthboundSuccessResponse(yFlowRerouteHubCarrier, YFlowRerouteResponse.class);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.UP);
        verifyAffinity(request.getYFlowId());
        verify(yFlowRerouteHubCarrier)
                .sendYFlowRerouteResultStatus(eq(createYFlowRequest.getYFlowId()), eq(null), anyString());
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

        handleSpeakerCommands(speakerRequest -> {
            SpeakerFlowSegmentResponse commandResponse = buildSuccessfulSpeakerResponse(speakerRequest);
            handleAsyncResponse(service, yFlowRequest.getYFlowId(), commandResponse);
        });

        // FlowCreate & FlowDelete service / FSM mustn't emit anything to NB
        verifyNoNorthboundResponse(flowCreateHubCarrier);
        verifyNoNorthboundResponse(flowDeleteHubCarrier);
    }

    private void processRerouteRequest(YFlowRerouteRequest yFlowRerouteRequest) throws DuplicateKeyException {
        YFlowRerouteService service = makeYFlowRerouteService(0);
        service.handleRequest(yFlowRerouteRequest.getYFlowId(), new CommandContext(), yFlowRerouteRequest);
    }

    private void processRerouteRequestAndSpeakerCommands(YFlowRerouteRequest request)
            throws DuplicateKeyException {
        FlowRerouteService flowRerouteService = makeFlowRerouteService(0);
        YFlowRerouteService service = makeYFlowRerouteService(flowRerouteService, 0);
        service.handleRequest(request.getYFlowId(), new CommandContext(), request);

        verifyYFlowStatus(request.getYFlowId(), FlowStatus.IN_PROGRESS);

        handleSpeakerCommands(speakerRequest -> {
            SpeakerFlowSegmentResponse commandResponse = buildSuccessfulSpeakerResponse(speakerRequest);
            handleAsyncResponse(service, request.getYFlowId(), commandResponse);
        });

        verifyNoNorthboundResponse(flowRerouteHubCarrier);
    }

    private void processRerouteRequestAndSpeakerCommands(YFlowRerouteRequest request,
                                                         FlowStatus expectedStatus,
                                                         FlowStatus expectedFirstSubFlowStatus,
                                                         FlowStatus expectedSecondSubFlowStatus)
            throws DuplicateKeyException {
        FlowRerouteService flowRerouteService = makeFlowRerouteService(0);
        YFlowRerouteService service = makeYFlowRerouteService(flowRerouteService, 0);
        service.handleRequest(request.getYFlowId(), new CommandContext(), request);

        verifyYFlowStatus(request.getYFlowId(), expectedStatus,
                expectedFirstSubFlowStatus, expectedSecondSubFlowStatus);

        handleSpeakerCommandsAndTimeoutInstall(service, request.getYFlowId());

        verifyNoNorthboundResponse(flowRerouteHubCarrier);
    }

    private void handleAsyncResponse(YFlowCreateService yFlowCreateService,
                                     String key, SpeakerFlowSegmentResponse commandResponse) {
        try {
            yFlowCreateService.handleAsyncResponse(key, commandResponse);
        } catch (UnknownKeyException ex) {
            //skip
        }
    }

    private void handleAsyncResponse(YFlowRerouteService yFlowRerouteService,
                                     String key, SpeakerFlowSegmentResponse commandResponse) {
        try {
            yFlowRerouteService.handleAsyncResponse(key, commandResponse);
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

    private void handleSpeakerCommandsAndTimeoutInstall(YFlowRerouteService service, String key) {
        handleSpeakerCommands(request -> {
            SpeakerFlowSegmentResponse commandResponse = buildSuccessfulSpeakerResponse(request);
            handleAsyncResponse(service, key, commandResponse);
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

    private void preparePathComputationForReroute(String flowId, GetPathsResult pathPair)
            throws RecoverableException, UnroutableFlowException {
        when(pathComputer.getPath(buildFlowIdArgumentMatch(flowId), any())).thenReturn(pathPair);
    }

    private void preparePathComputationForReroute(String flowId, GetPathsResult pathPair, GetPathsResult pathPair2)
            throws RecoverableException, UnroutableFlowException {
        when(pathComputer.getPath(buildFlowIdArgumentMatch(flowId), any())).thenReturn(pathPair).thenReturn(pathPair2);
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

    private FlowRerouteService makeFlowRerouteService(int retriesLimit) {
        return new FlowRerouteService(flowRerouteHubCarrier, persistenceManager,
                pathComputer, flowResourcesManager, 3, 0, 3, retriesLimit);
    }

    private YFlowRerouteService makeYFlowRerouteService(int retriesLimit) {
        return makeYFlowRerouteService(makeFlowRerouteService(retriesLimit), retriesLimit);
    }

    private YFlowRerouteService makeYFlowRerouteService(FlowRerouteService flowRerouteService,
                                                        int retriesLimit) {
        return new YFlowRerouteService(yFlowRerouteHubCarrier, persistenceManager, pathComputer, flowResourcesManager,
                flowRerouteService, METER_ALLOCATION_RETRIES_LIMIT, retriesLimit);
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
