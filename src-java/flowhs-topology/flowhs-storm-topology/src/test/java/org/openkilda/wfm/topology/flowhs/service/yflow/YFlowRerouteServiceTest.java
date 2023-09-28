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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.request.SpeakerRequest;
import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.InstallSpeakerCommandsRequest;
import org.openkilda.floodlight.api.response.SpeakerResponse;
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
import org.openkilda.wfm.topology.flowhs.service.AbstractYFlowTest;
import org.openkilda.wfm.topology.flowhs.service.FlowCreateService;
import org.openkilda.wfm.topology.flowhs.service.FlowDeleteService;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowRerouteHubCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowRerouteService;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ExtendWith(MockitoExtension.class)
public class YFlowRerouteServiceTest extends AbstractYFlowTest<SpeakerRequest> {
    private static final int METER_ALLOCATION_RETRIES_LIMIT = 3;

    @Mock
    private FlowGenericCarrier flowCreateHubCarrier;
    @Mock
    private FlowGenericCarrier flowDeleteHubCarrier;
    @Mock
    private FlowGenericCarrier yFlowCreateHubCarrier;
    @Mock
    private FlowRerouteHubCarrier flowRerouteHubCarrier;
    @Mock
    private YFlowRerouteHubCarrier yFlowRerouteHubCarrier;

    @BeforeEach
    public void init() {
        doAnswer(buildSpeakerRequestAnswer())
                .when(flowCreateHubCarrier).sendSpeakerRequest(any(SpeakerRequest.class));
        lenient().doAnswer(buildSpeakerRequestAnswer())
                .when(flowRerouteHubCarrier).sendSpeakerRequest(any(SpeakerRequest.class));
        doAnswer(buildSpeakerRequestAnswer())
                .when(yFlowCreateHubCarrier).sendSpeakerRequest(any(SpeakerRequest.class));
        lenient().doAnswer(buildSpeakerRequestAnswer())
                .when(yFlowRerouteHubCarrier).sendSpeakerRequest(any(SpeakerRequest.class));
    }

    @Test
    public void rerouteFlowWithTransitSwitches()
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

    /**
     * y-flow reroute test for one possible path exists for affinity group, and another already on the best path.
     *         /5\  -- possiblePath
     * '''''/-----2
     * 1--4
     * ''''\------3
     * There are two sub-paths: 1-4-2(with 1-4-5-2 as a possible path) and 1-4-3. 1 is share-point, 4 is y-point
     */
    @Test
    public void rerouteFlowWithOnePossiblePathExistForAffinityGroup()
            throws UnroutableFlowException, RecoverableException, DuplicateKeyException {
        // given
        YFlowRequest createYFlowRequest = createYFlow();
        YFlowRerouteRequest request = new YFlowRerouteRequest(createYFlowRequest.getYFlowId(), "reason");

        preparePathComputationForReroute("test_flow_1", buildFirstSubFlowPathPairWithNewSwitch5Transit());
        preparePathComputationForReroute("test_flow_2", buildSecondSubFlowPathPair());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_TRANSIT);

        // when
        processRerouteRequestAndSpeakerCommands(request);

        verifyNorthboundSuccessResponse(yFlowRerouteHubCarrier, YFlowRerouteResponse.class);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.UP);
        verifyAffinity(request.getYFlowId());
        verify(yFlowRerouteHubCarrier)
                .sendYFlowRerouteResultStatus(eq(createYFlowRequest.getYFlowId()), eq(null), anyString());
    }

    /**
     * y-flow reroute test for one possible path exists for not affinity group, and another already on the best path.
     * '''''/-----2
     * 1--4
     * ''''\------3
     *       \5/  -- possiblePath
     * There are two sub-paths: 1-4-2 and 1-4-3(with 1-4-5-3 as a possible path). 1 is share-point, 4 is y-point
     */
    @Test
    public void rerouteFlowWithOnePossiblePathExistForNotAffinityGroup()
            throws UnroutableFlowException, RecoverableException, DuplicateKeyException {
        // given
        YFlowRequest createYFlowRequest = createYFlow();
        YFlowRerouteRequest request = new YFlowRerouteRequest(createYFlowRequest.getYFlowId(), "reason");

        preparePathComputationForReroute("test_flow_1", buildFirstSubFlowPathPair());
        preparePathComputationForReroute("test_flow_2", buildSecondSubFlowPathPairWithNewSwitch5Transit());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_TRANSIT);

        // when
        processRerouteRequestAndSpeakerCommands(request);

        verifyNorthboundSuccessResponse(yFlowRerouteHubCarrier, YFlowRerouteResponse.class);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.UP);
        verifyAffinity(request.getYFlowId());
        verify(yFlowRerouteHubCarrier)
                .sendYFlowRerouteResultStatus(eq(createYFlowRequest.getYFlowId()), eq(null), anyString());
    }

    /**
     * y-flow reroute test for one possible path exists for affinity group, and another path broken.
     *         /5\  -- possiblePath
     * '''''/-----2
     * 1--4
     * ''''\--x  x--3
     * There are two sub-paths: 1-4-2(with 1-4-5-2 as a possible path) and 1-4-3, switch 3 unreachable,
     * 1 is share-point, 4 is y-point.
     */
    @Test
    public void rerouteFlowWithOnePossiblePathExistForAffinityGroupOtherPathBroken()
            throws UnroutableFlowException, RecoverableException, DuplicateKeyException {
        // given
        YFlowRequest createYFlowRequest = createYFlow();
        YFlowRerouteRequest request = new YFlowRerouteRequest(createYFlowRequest.getYFlowId(), "reason");

        preparePathComputationForReroute("test_flow_1", buildFirstSubFlowPathPairWithNewSwitch5Transit());
        when(pathComputer.getPath(buildFlowIdArgumentMatch("test_flow_2"), any(), anyBoolean()))
                .thenThrow(new UnroutableFlowException(injectedErrorMessage));
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_TRANSIT);

        // when
        processRerouteRequestAndSpeakerCommands(request);

        verifyNorthboundSuccessResponse(yFlowRerouteHubCarrier, YFlowRerouteResponse.class);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.DEGRADED, FlowStatus.UP, FlowStatus.DOWN);
        verifyAffinity(request.getYFlowId());
        verify(yFlowRerouteHubCarrier)
                .sendYFlowRerouteResultStatus(eq(createYFlowRequest.getYFlowId()), eq(null), anyString());
    }

    /**
     * y-flow reroute test for one possible path exists for not affinity group, and another path broken.
     * '''''/--x x--2
     * 1--4
     * ''''\------3
     *       \5/  -- possiblePath
     * There are two sub-paths: 1-4-2, switch 2 unreachable, and 1-4-3(with 1-4-5-3 as a possible path).
     * 1 is share-point, 4 is y-point.
     */
    @Test
    public void rerouteFlowWithOnePossiblePathExistForNotAffinityGroupOtherPathBroken()
            throws UnroutableFlowException, RecoverableException, DuplicateKeyException {
        // given
        YFlowRequest createYFlowRequest = createYFlow();
        YFlowRerouteRequest request = new YFlowRerouteRequest(createYFlowRequest.getYFlowId(), "reason");

        when(pathComputer.getPath(buildFlowIdArgumentMatch("test_flow_1"), any(), anyBoolean()))
                .thenThrow(new UnroutableFlowException(injectedErrorMessage));
        preparePathComputationForReroute("test_flow_2", buildSecondSubFlowPathPairWithNewSwitch5Transit());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_TRANSIT);

        // when
        processRerouteRequestAndSpeakerCommands(request);

        verifyNorthboundSuccessResponse(yFlowRerouteHubCarrier, YFlowRerouteResponse.class);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.DEGRADED, FlowStatus.UP, FlowStatus.DOWN);
        verifyAffinity(request.getYFlowId());
        verify(yFlowRerouteHubCarrier)
                .sendYFlowRerouteResultStatus(eq(createYFlowRequest.getYFlowId()), eq(null), anyString());
    }

    /**
     * y-flow reroute test for one path broken, another path already on the best path.
     * '''''/--x x--2
     * 1--4
     * ''''\------3
     * There are two sub-paths: 1-4-2, switch 2 unreachable, and 1-4-3.
     * 1 is share-point, 4 is y-point.
     */
    @Test
    public void rerouteFlowFirstSubFlowBrokenSecondAlreadyOnTheBestPath()
            throws UnroutableFlowException, RecoverableException, DuplicateKeyException {
        // given
        YFlowRequest createYFlowRequest = createYFlow();
        YFlowRerouteRequest request = new YFlowRerouteRequest(createYFlowRequest.getYFlowId(), "reason");

        when(pathComputer.getPath(buildFlowIdArgumentMatch("test_flow_1"), any(), anyBoolean()))
                .thenThrow(new UnroutableFlowException(injectedErrorMessage));
        preparePathComputationForReroute("test_flow_2", buildSecondSubFlowPathPair());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_TRANSIT);

        // when
        processRerouteRequestAndSpeakerCommands(request, FlowStatus.DEGRADED);

        verifyNorthboundErrorResponse(yFlowRerouteHubCarrier, ErrorType.NOT_FOUND);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.DEGRADED, FlowStatus.UP, FlowStatus.DOWN);
        verifyAffinity(request.getYFlowId());
        verify(yFlowRerouteHubCarrier)
                .sendYFlowRerouteResultStatus(eq(createYFlowRequest.getYFlowId()),
                        eq(new RerouteError("Failed to reroute all sub-flows of y-flow test_successful_yflow")),
                        anyString());
    }

    /**
     * y-flow reroute test for one path broken, another path already on the best path.
     * '''''/-----2
     * 1--4
     * ''''\--x  x--3
     * There are two sub-paths: 1-4-2, and 1-4-3, switch 3 unreachable,
     * 1 is share-point, 4 is y-point.
     */
    @Test
    public void rerouteFlowFirstSubFlowAlreadyOnTheBestPathSecondBroken()
            throws UnroutableFlowException, RecoverableException, DuplicateKeyException {
        // given
        YFlowRequest createYFlowRequest = createYFlow();
        YFlowRerouteRequest request = new YFlowRerouteRequest(createYFlowRequest.getYFlowId(), "reason");

        preparePathComputationForReroute("test_flow_1", buildFirstSubFlowPathPair());
        when(pathComputer.getPath(buildFlowIdArgumentMatch("test_flow_2"), any(), anyBoolean()))
                .thenThrow(new UnroutableFlowException(injectedErrorMessage));
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_TRANSIT);

        // when
        processRerouteRequestAndSpeakerCommands(request, FlowStatus.DEGRADED);

        verifyNorthboundErrorResponse(yFlowRerouteHubCarrier, ErrorType.NOT_FOUND);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.DEGRADED, FlowStatus.UP, FlowStatus.DOWN);
        verifyAffinity(request.getYFlowId());
        verify(yFlowRerouteHubCarrier)
                .sendYFlowRerouteResultStatus(eq(createYFlowRequest.getYFlowId()),
                        eq(new RerouteError("Failed to reroute all sub-flows of y-flow test_successful_yflow")),
                        anyString());
    }

    @Test
    public void updateFlowWithProtectedPath()
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
    public void successIfNoPathAvailableForFirstSubFlow()
            throws UnroutableFlowException, RecoverableException, DuplicateKeyException {
        // given
        YFlowRequest createYFlowRequest = createYFlow();
        YFlowRerouteRequest request = new YFlowRerouteRequest(createYFlowRequest.getYFlowId(), "reason");

        when(pathComputer.getPath(buildFlowIdArgumentMatch("test_flow_1"), any(), anyBoolean()))
                .thenThrow(new UnroutableFlowException(injectedErrorMessage));
        preparePathComputationForReroute("test_flow_2", buildSecondSubFlowPathPairWithNewTransit());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_NEW_TRANSIT);

        // when
        processRerouteRequestAndSpeakerCommands(request);

        verifyNorthboundSuccessResponse(yFlowRerouteHubCarrier, YFlowRerouteResponse.class);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.DEGRADED, FlowStatus.DOWN, FlowStatus.UP);
        verify(yFlowRerouteHubCarrier).sendYFlowRerouteResultStatus(eq(createYFlowRequest.getYFlowId()),
                eq(null),
                anyString());
    }

    @Test
    public void successIfNoPathAvailableForSecondSubFlow()
            throws UnroutableFlowException, RecoverableException, DuplicateKeyException {
        // given
        YFlowRequest createYFlowRequest = createYFlow();
        YFlowRerouteRequest request = new YFlowRerouteRequest(createYFlowRequest.getYFlowId(), "reason");

        preparePathComputationForReroute("test_flow_1", buildFirstSubFlowPathPairWithNewTransit());
        when(pathComputer.getPath(buildFlowIdArgumentMatch("test_flow_2"), any(), anyBoolean()))
                .thenThrow(new UnroutableFlowException(injectedErrorMessage));
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_NEW_TRANSIT);

        // when
        processRerouteRequestAndSpeakerCommands(request,
                FlowStatus.IN_PROGRESS, FlowStatus.IN_PROGRESS, FlowStatus.UP);

        verifyNorthboundSuccessResponse(yFlowRerouteHubCarrier, YFlowRerouteResponse.class);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.DEGRADED, FlowStatus.UP, FlowStatus.DOWN);
        verify(yFlowRerouteHubCarrier).sendYFlowRerouteResultStatus(eq(createYFlowRequest.getYFlowId()),
                eq(null),
                anyString());
    }

    @Test
    public void failIfNoResourcesAvailable()
            throws UnroutableFlowException, RecoverableException, ResourceAllocationException, DuplicateKeyException {
        // given
        YFlowRequest createYFlowRequest = createYFlow();
        YFlowRerouteRequest request = new YFlowRerouteRequest(createYFlowRequest.getYFlowId(), "reason");

        preparePathComputationForReroute("test_flow_1", buildFirstSubFlowPathPairWithNewTransit());
        preparePathComputationForReroute("test_flow_2", buildSecondSubFlowPathPairWithNewTransit());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_NEW_TRANSIT);
        lenient().doThrow(new ResourceAllocationException(injectedErrorMessage))
                .when(flowResourcesManager).allocateMeter(eq("test_successful_yflow"), eq(SWITCH_NEW_TRANSIT));

        // when
        processRerouteRequestAndSpeakerCommands(request);

        verifyYFlowStatus(request.getYFlowId(), FlowStatus.UP);
        verify(flowResourcesManager, times(METER_ALLOCATION_RETRIES_LIMIT + 1))
                .allocateMeter(eq("test_successful_yflow"), eq(SWITCH_NEW_TRANSIT));
        verify(yFlowRerouteHubCarrier).sendYFlowRerouteResultStatus(eq(createYFlowRequest.getYFlowId()),
                eq(new RerouteError("Failed to allocate y-flow resources. Unit-test injected failure")),
                anyString());
    }

    @Test
    public void rerouteFlowWithTransitSwitchesWithAffectedIsl()
            throws UnroutableFlowException, RecoverableException, DuplicateKeyException {
        // given
        YFlowRequest createYFlowRequest = createYFlow();
        YFlowRerouteRequest request = new YFlowRerouteRequest(createYFlowRequest.getYFlowId(), "reason");
        request.setAffectedIsls(Collections.singleton(new IslEndpoint(SWITCH_TRANSIT, 25)));

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
    public void rerouteFlowWithTransitSwitchesWithMainFlowAffectedIsl()
            throws UnroutableFlowException, RecoverableException, DuplicateKeyException {
        // given
        YFlowRequest createYFlowRequest = createYFlow();
        YFlowRerouteRequest request = new YFlowRerouteRequest(createYFlowRequest.getYFlowId(), "reason");
        request.setAffectedIsls(Collections.singleton(new IslEndpoint(SWITCH_TRANSIT, 26)));

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
    public void rerouteFlowWithTransitSwitchesWithSecondaryFlowAffectedIsl()
            throws UnroutableFlowException, RecoverableException, DuplicateKeyException {
        // given
        YFlowRequest createYFlowRequest = createYFlow();
        YFlowRerouteRequest request = new YFlowRerouteRequest(createYFlowRequest.getYFlowId(), "reason");
        request.setAffectedIsls(Collections.singleton(new IslEndpoint(SWITCH_TRANSIT, 27)));

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

    @Test
    public void failOnUnsuccessfulMeterInstallation()
            throws RecoverableException, UnroutableFlowException, DuplicateKeyException {
        // given
        YFlowRequest createYFlowRequest = createYFlow();
        YFlowRerouteRequest request = new YFlowRerouteRequest(createYFlowRequest.getYFlowId(), "reason");

        preparePathComputationForReroute("test_flow_1", buildFirstSubFlowPathPairWithNewTransit());
        preparePathComputationForReroute("test_flow_2", buildSecondSubFlowPathPairWithNewTransit());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_NEW_TRANSIT);

        YFlowRerouteService service = makeYFlowRerouteService(0);

        // when
        service.handleRequest(request.getYFlowId(), new CommandContext(), request);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.IN_PROGRESS, FlowStatus.IN_PROGRESS, FlowStatus.UP);
        // and
        handleSpeakerCommandsAndFailInstall(service, request.getYFlowId(), "test_successful_yflow");

        // then
        verifyNorthboundSuccessResponse(yFlowRerouteHubCarrier, YFlowRerouteResponse.class);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.UP);
        verifyAffinity(request.getYFlowId());
        verify(yFlowRerouteHubCarrier)
                .sendYFlowRerouteResultStatus(eq(createYFlowRequest.getYFlowId()),
                        eq(new RerouteError("Received error response(s) for 2 install commands")), anyString());
    }

    @Test
    public void failOnTimeoutDuringMeterInstallation()
            throws UnroutableFlowException, RecoverableException, DuplicateKeyException, UnknownKeyException {
        // given
        YFlowRequest createYFlowRequest = createYFlow();
        YFlowRerouteRequest request = new YFlowRerouteRequest(createYFlowRequest.getYFlowId(), "reason");

        preparePathComputationForReroute("test_flow_1", buildFirstSubFlowPathPairWithNewTransit());
        preparePathComputationForReroute("test_flow_2", buildSecondSubFlowPathPairWithNewTransit());
        prepareYPointComputation(SWITCH_SHARED, SWITCH_FIRST_EP, SWITCH_SECOND_EP, SWITCH_NEW_TRANSIT);

        YFlowRerouteService service = makeYFlowRerouteService(0);

        // when
        service.handleRequest(request.getYFlowId(), new CommandContext(), request);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.IN_PROGRESS);
        // and
        handleSpeakerCommandsAndTimeoutInstall(service, request.getYFlowId());

        // then
        verifyNorthboundSuccessResponse(yFlowRerouteHubCarrier, YFlowRerouteResponse.class);
        verifyYFlowStatus(request.getYFlowId(), FlowStatus.UP);
        verifyAffinity(request.getYFlowId());
        verify(yFlowRerouteHubCarrier)
                .sendYFlowRerouteResultStatus(eq(createYFlowRequest.getYFlowId()),
                        eq(new RerouteError("Timeout event has been received")), anyString());
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

    private void processRerouteRequestAndSpeakerCommands(YFlowRerouteRequest request)
            throws DuplicateKeyException {
        processRerouteRequestAndSpeakerCommands(request, FlowStatus.IN_PROGRESS);
    }

    private void processRerouteRequestAndSpeakerCommands(YFlowRerouteRequest request, FlowStatus yFlowStatus)
            throws DuplicateKeyException {
        FlowRerouteService flowRerouteService = makeFlowRerouteService(0);
        YFlowRerouteService service = makeYFlowRerouteService(flowRerouteService, 0);
        service.handleRequest(request.getYFlowId(), new CommandContext(), request);

        verifyYFlowStatus(request.getYFlowId(), yFlowStatus);

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

    private void handleAsyncResponse(YFlowRerouteService yFlowRerouteService,
                                     String yFlowFsmKey, SpeakerResponse commandResponse) {
        try {
            yFlowRerouteService.handleAsyncResponse(yFlowFsmKey, commandResponse);
        } catch (UnknownKeyException ex) {
            //skip
        }
    }


    protected YFlow verifyYFlowStatus(String yFlowId, FlowStatus expectedStatus,
                                      FlowStatus expectedFirstSubFlowStatus, FlowStatus expectedSecondSubFlowStatus) {
        YFlow flow = getYFlow(yFlowId);
        Assertions.assertEquals(expectedStatus, flow.getStatus());

        Set<FlowStatus> expectedSubFlowStatuses = Stream.of(expectedFirstSubFlowStatus, expectedSecondSubFlowStatus)
                .collect(Collectors.toSet());
        Set<FlowStatus> actualSubFlowStatuses = flow.getSubFlows().stream()
                .map(YSubFlow::getFlow)
                .map(Flow::getStatus)
                .collect(Collectors.toSet());

        Assertions.assertEquals(expectedSubFlowStatuses, actualSubFlowStatuses);

        return flow;
    }

    private void handleSpeakerCommandsAndFailInstall(YFlowRerouteService yFlowRerouteService, String yFlowFsmKey,
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
            handleAsyncResponse(yFlowRerouteService, yFlowFsmKey, commandResponse);
        });
    }

    private void handleSpeakerCommandsAndTimeoutInstall(YFlowRerouteService service, String yFlowFsmKey) {
        handleSpeakerRequests(request -> {
            SpeakerResponse commandResponse;
            if (request instanceof FlowSegmentRequest) {
                FlowSegmentRequest flowSegmentRequest = (FlowSegmentRequest) request;
                commandResponse = buildSuccessfulSpeakerResponse(flowSegmentRequest);
                handleAsyncResponse(service, yFlowFsmKey, commandResponse);
            } else {
                BaseSpeakerCommandsRequest speakerCommandsRequest = (BaseSpeakerCommandsRequest) request;
                if (speakerCommandsRequest instanceof InstallSpeakerCommandsRequest) {
                    try {
                        service.handleTimeout(yFlowFsmKey);
                    } catch (UnknownKeyException ex) {
                        //skip
                    }
                } else {
                    commandResponse = buildSuccessfulYFlowSpeakerResponse(speakerCommandsRequest);
                    handleAsyncResponse(service, yFlowFsmKey, commandResponse);
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
        when(pathComputer.getPath(buildFlowIdArgumentMatch(flowId))).thenReturn(pathPair);
        when(pathComputer.getPath(buildFlowIdArgumentMatch(flowId), anyCollection(), eq(true)))
                .thenReturn(pathPair2);
    }

    private void preparePathComputationForReroute(String flowId, GetPathsResult pathPair)
            throws RecoverableException, UnroutableFlowException {
        lenient().when(pathComputer.getPath(buildFlowIdArgumentMatch(flowId), any(), anyBoolean()))
                .thenReturn(pathPair);
    }

    private void preparePathComputationForReroute(String flowId, GetPathsResult pathPair, GetPathsResult pathPair2)
            throws RecoverableException, UnroutableFlowException {
        when(pathComputer.getPath(buildFlowIdArgumentMatch(flowId), anyCollection(), eq(false)))
                .thenReturn(pathPair);
        when(pathComputer.getPath(buildFlowIdArgumentMatch(flowId), anyCollection(), eq(true)))
                .thenReturn(pathPair2);
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
        lenient().when(pathComputer.getIntersectionPoint(any(),
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
                ruleManager, flowRerouteService, METER_ALLOCATION_RETRIES_LIMIT, retriesLimit);
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
