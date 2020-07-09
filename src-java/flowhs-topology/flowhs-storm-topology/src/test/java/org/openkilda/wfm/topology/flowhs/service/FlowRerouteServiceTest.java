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

package org.openkilda.wfm.topology.flowhs.service;

import static java.util.stream.Collectors.toSet;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse.ErrorCode;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.flow.FlowRerouteResponse;
import org.openkilda.messaging.info.reroute.RerouteResultInfoData;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.IslEndpoint;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.PathPair;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.topology.flowhs.model.FlowRerouteFact;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@RunWith(MockitoJUnitRunner.class)
public class FlowRerouteServiceTest extends AbstractFlowTest {
    private static final int TRANSACTION_RETRIES_LIMIT = 3;
    private static final int PATH_ALLOCATION_RETRIES_LIMIT = 10;
    private static final int PATH_ALLOCATION_RETRY_DELAY = 0;
    private static final int SPEAKER_COMMAND_RETRIES_LIMIT = 0;

    @Mock
    private FlowRerouteHubCarrier carrier;

    private String currentRequestKey = dummyRequestKey;

    @Before
    public void setUp() {
        doAnswer(getSpeakerCommandsAnswer()).when(carrier).sendSpeakerRequest(any());

        // must be done before first service create attempt, because repository objects are cached inside FSM actions
        setupFlowRepositorySpy();
        setupFlowPathRepositorySpy();
        setupIslRepositorySpy();
    }

    @Test
    public void shouldFailRerouteFlowIfNoPathAvailable() throws RecoverableException, UnroutableFlowException {
        Flow origin = makeFlow();
        preparePathComputation(origin.getFlowId(), new UnroutableFlowException(injectedErrorMessage));

        FlowRerouteFact request = new FlowRerouteFact(
                dummyRequestKey, commandContext, origin.getFlowId(), null, false, false, false, null);
        testExpectedFailure(request, origin, FlowStatus.DOWN, ErrorType.NOT_FOUND);
        verify(pathComputer, times(11))
                .getPath(makeFlowArgumentMatch(origin.getFlowId()), any());
    }

    @Test
    public void shouldFailRerouteFlowIfRecoverableException() throws RecoverableException, UnroutableFlowException {
        Flow origin = makeFlow();
        preparePathComputation(origin.getFlowId(), new RecoverableException(injectedErrorMessage));

        FlowRerouteFact request = new FlowRerouteFact(
                dummyRequestKey, commandContext, origin.getFlowId(), null, false, false, false, null);
        testExpectedFailure(request, origin, FlowStatus.UP, ErrorType.INTERNAL_ERROR);
        verify(pathComputer, times(PATH_ALLOCATION_RETRIES_LIMIT + 1))
                .getPath(makeFlowArgumentMatch(origin.getFlowId()), any());
    }

    @Test
    public void shouldFailRerouteFlowIfMultipleOverprovisionBandwidth()
            throws RecoverableException, UnroutableFlowException {
        Flow origin = makeFlow();
        preparePathComputation(origin.getFlowId(), make3SwitchesPathPair());

        IslRepository repository = setupIslRepositorySpy();
        doThrow(ResourceAllocationException.class)
                .when(repository).updateAvailableBandwidth(any(), anyInt(), any(), anyInt(), anyLong());

        FlowRerouteFact request = new FlowRerouteFact(
                dummyRequestKey, commandContext, origin.getFlowId(), null, false, false, false, null);
        testExpectedFailure(request, origin, FlowStatus.UP, ErrorType.INTERNAL_ERROR);

        verify(repository, times(PATH_ALLOCATION_RETRIES_LIMIT + 1))
                .updateAvailableBandwidth(any(), anyInt(), any(), anyInt(), anyLong());
    }

    @Test
    public void shouldFailRerouteFlowIfNoResourcesAvailable()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow origin = makeFlow();
        preparePathComputation(origin.getFlowId(), make3SwitchesPathPair());

        doThrow(new ResourceAllocationException(injectedErrorMessage))
                .when(flowResourcesManager).allocateFlowResources(makeFlowArgumentMatch(origin.getFlowId()));

        FlowRerouteFact request = new FlowRerouteFact(
                dummyRequestKey, commandContext, origin.getFlowId(), null, false, false, false, null);
        testExpectedFailure(request, origin, FlowStatus.UP, ErrorType.INTERNAL_ERROR);

        verify(flowResourcesManager, times(PATH_ALLOCATION_RETRIES_LIMIT + 1))
                .allocateFlowResources(makeFlowArgumentMatch(origin.getFlowId()));
    }

    @Test
    public void shouldFailRerouteFlowOnResourcesAllocationConstraint()
            throws RecoverableException, UnroutableFlowException {
        Flow origin = makeFlow();
        preparePathComputation(origin.getFlowId(), make3SwitchesPathPair());

        FlowPathRepository repository = setupFlowPathRepositorySpy();
        doThrow(new RuntimeException(injectedErrorMessage))
                .when(repository)
                .createOrUpdate(any(FlowPath.class));

        FlowRerouteFact request = new FlowRerouteFact(
                dummyRequestKey, commandContext, origin.getFlowId(), null, false, false, false, null);
        testExpectedFailure(request, origin, FlowStatus.UP, ErrorType.INTERNAL_ERROR);
    }

    private void testExpectedFailure(
            FlowRerouteFact request, Flow origin, FlowStatus expectedFlowStatus, ErrorType expectedError) {
        makeService().handleRequest(request);

        verifyNoSpeakerInteraction(carrier);
        verifyNorthboundErrorResponse(carrier, expectedError);

        Flow result = verifyFlowStatus(origin.getFlowId(), expectedFlowStatus);
        verifyNoPathReplace(origin, result);
    }

    @Test
    public void shouldSkipRerouteIfNoNewPathFound() throws RecoverableException, UnroutableFlowException {
        Flow origin = makeFlow();
        preparePathComputation(origin.getFlowId(), make2SwitchesPathPair());

        makeService().handleRequest(new FlowRerouteFact(
                dummyRequestKey, commandContext, origin.getFlowId(), null, false, false, false, null));

        verifyNoSpeakerInteraction(carrier);
        verifyNorthboundSuccessResponse(carrier);

        Flow result = verifyFlowStatus(origin.getFlowId(), FlowStatus.UP);
        verifyNoPathReplace(origin, result);
    }

    @Test
    public void shouldFailRerouteOnUnsuccessfulInstallation() throws RecoverableException, UnroutableFlowException {
        Flow origin = makeFlow();
        PathPair newPathPair = make3SwitchesPathPair();
        preparePathComputation(origin.getFlowId(), newPathPair);

        FlowRerouteService service = makeService();
        service.handleRequest(new FlowRerouteFact(
                currentRequestKey, commandContext, origin.getFlowId(), null, false, false, false, null));

        verifyFlowStatus(origin.getFlowId(), FlowStatus.IN_PROGRESS);
        verifyNorthboundSuccessResponse(carrier);

        FlowSegmentRequest speakerRequest;
        while ((speakerRequest = requests.poll()) != null) {
            if (speakerRequest.isInstallRequest()) {
                service.handleAsyncResponse(currentRequestKey, FlowErrorResponse.errorBuilder()
                        .messageContext(speakerRequest.getMessageContext())
                        .errorCode(ErrorCode.UNKNOWN)
                        .description(injectedErrorMessage)
                        .commandId(speakerRequest.getCommandId())
                        .metadata(speakerRequest.getMetadata())
                        .switchId(speakerRequest.getSwitchId())
                        .build());
            } else {
                produceAsyncResponse(service, speakerRequest);
            }
        }

        Flow result = verifyFlowStatus(origin.getFlowId(), FlowStatus.UP);
        verifyNoPathReplace(origin, result);
        verify(carrier).sendRerouteResultStatus(eq(origin.getFlowId()),
                argThat(hasProperty("message", equalTo("Failed to install rules"))),
                any(String.class));
    }

    @Test
    public void shouldFailRerouteOnTimeoutDuringInstallation() throws RecoverableException, UnroutableFlowException {
        Flow origin = makeFlow();
        PathPair newPathPair = make3SwitchesPathPair();
        preparePathComputation(origin.getFlowId(), newPathPair);

        FlowRerouteService service = makeService();
        service.handleRequest(new FlowRerouteFact(
                currentRequestKey, commandContext, origin.getFlowId(), null, false, false, false, null));

        verifyFlowStatus(origin.getFlowId(), FlowStatus.IN_PROGRESS);
        verifyNorthboundSuccessResponse(carrier);

        service.handleTimeout(currentRequestKey);

        FlowSegmentRequest speakerRequest;
        while ((speakerRequest = requests.poll()) != null) {
            produceAsyncResponse(service, speakerRequest);
        }

        Flow result = verifyFlowStatus(origin.getFlowId(), FlowStatus.UP);
        verifyNoPathReplace(origin, result);
        verify(carrier).sendRerouteResultStatus(eq(origin.getFlowId()),
                argThat(hasProperty("message", equalTo("Failed to install rules"))),
                any(String.class));
    }

    @Test
    public void shouldFailRerouteOnUnsuccessfulValidation() throws RecoverableException, UnroutableFlowException {
        Flow origin = makeFlow();
        PathPair newPathPair = make3SwitchesPathPair();
        preparePathComputation(origin.getFlowId(), newPathPair);

        FlowRerouteService service = makeService();
        service.handleRequest(new FlowRerouteFact(
                currentRequestKey, commandContext, origin.getFlowId(), null, false, false, false, null));

        verifyFlowStatus(origin.getFlowId(), FlowStatus.IN_PROGRESS);
        verifyNorthboundSuccessResponse(carrier);

        FlowSegmentRequest speakerRequest;
        while ((speakerRequest = requests.poll()) != null) {
            if (speakerRequest.isVerifyRequest()) {
                service.handleAsyncResponse(currentRequestKey, FlowErrorResponse.errorBuilder()
                        .errorCode(ErrorCode.UNKNOWN)
                        .description(injectedErrorMessage)
                        .messageContext(speakerRequest.getMessageContext())
                        .commandId(speakerRequest.getCommandId())
                        .metadata(speakerRequest.getMetadata())
                        .switchId(speakerRequest.getSwitchId())
                        .build());
            } else {
                produceAsyncResponse(service, speakerRequest);
            }
        }

        Flow result = verifyFlowStatus(origin.getFlowId(), FlowStatus.UP);
        verifyNoPathReplace(origin, result);
        verify(carrier).sendRerouteResultStatus(eq(origin.getFlowId()),
                argThat(hasProperty("message", equalTo("Failed to validate rules"))),
                any(String.class));
    }

    @Test
    public void shouldFailRerouteOnTimeoutDuringValidation() throws RecoverableException, UnroutableFlowException {
        Flow origin = makeFlow();
        PathPair newPathPair = make3SwitchesPathPair();
        preparePathComputation(origin.getFlowId(), newPathPair);

        FlowRerouteService service = makeService();
        service.handleRequest(new FlowRerouteFact(
                currentRequestKey, commandContext, origin.getFlowId(), null, false, false, false, null));

        verifyFlowStatus(origin.getFlowId(), FlowStatus.IN_PROGRESS);
        verifyNorthboundSuccessResponse(carrier);

        FlowSegmentRequest speakerRequest;
        while ((speakerRequest = requests.poll()) != null) {
            if (speakerRequest.isVerifyRequest()) {
                service.handleTimeout(currentRequestKey);
            } else {
                produceAsyncResponse(service, speakerRequest);
            }
        }

        Flow result = verifyFlowStatus(origin.getFlowId(), FlowStatus.UP);
        verifyNoPathReplace(origin, result);
        verify(carrier).sendRerouteResultStatus(eq(origin.getFlowId()),
                argThat(hasProperty("message", equalTo("Failed to validate rules"))),
                any(String.class));
    }

    @Test
    public void shouldFailRerouteOnSwapPathsError() throws RecoverableException, UnroutableFlowException {
        Flow origin = makeFlow();
        preparePathComputation(origin.getFlowId(), make3SwitchesPathPair());

        FlowRepository repository = setupFlowRepositorySpy();
        doThrow(new RuntimeException(injectedErrorMessage))
                .when(repository).createOrUpdate(
                argThat(hasProperty("forwardPathId", Matchers.not(equalTo(origin.getForwardPathId())))));

        FlowRerouteService service = makeService();
        service.handleRequest(new FlowRerouteFact(
                currentRequestKey, commandContext, origin.getFlowId(), null, false, false, false, null));

        verifyFlowStatus(origin.getFlowId(), FlowStatus.IN_PROGRESS);
        verifyNorthboundSuccessResponse(carrier);

        FlowSegmentRequest speakerRequest;
        while ((speakerRequest = requests.poll()) != null) {
            if (speakerRequest.isVerifyRequest()) {
                service.handleAsyncResponse(currentRequestKey, buildResponseOnVerifyRequest(speakerRequest));
            } else {
                produceAsyncResponse(service, speakerRequest);
            }
        }

        Flow result = verifyFlowStatus(origin.getFlowId(), FlowStatus.UP);
        verifyNoPathReplace(origin, result);
    }

    @Test
    public void shouldFailRerouteOnErrorDuringCompletingFlowPathInstallation()
            throws RecoverableException, UnroutableFlowException {
        Flow origin = makeFlow();
        preparePathComputation(origin.getFlowId(), make3SwitchesPathPair());

        FlowPathRepository repository = setupFlowPathRepositorySpy();
        Set<PathId> originalPaths = origin.getPaths().stream()
                .map(FlowPath::getPathId)
                .collect(toSet());
        doThrow(new RuntimeException(injectedErrorMessage))
                .when(repository)
                .updateStatus(
                        ArgumentMatchers.argThat(argument -> !originalPaths.contains(argument)),
                        eq(FlowPathStatus.ACTIVE));

        FlowRerouteService service = makeService();
        service.handleRequest(new FlowRerouteFact(
                currentRequestKey, commandContext, origin.getFlowId(), null, false, false, false, null));

        verifyFlowStatus(origin.getFlowId(), FlowStatus.IN_PROGRESS);
        verifyNorthboundSuccessResponse(carrier);

        FlowSegmentRequest speakerRequest;
        while ((speakerRequest = requests.poll()) != null) {
            if (speakerRequest.isVerifyRequest()) {
                service.handleAsyncResponse(currentRequestKey, buildResponseOnVerifyRequest(speakerRequest));
            } else {
                produceAsyncResponse(service, speakerRequest);
            }
        }

        Flow result = verifyFlowStatus(origin.getFlowId(), FlowStatus.UP);
        verifyNoPathReplace(origin, result);
    }

    @Test
    public void shouldCompleteRerouteOnErrorDuringCompletingFlowPathRemoval()
            throws RecoverableException, UnroutableFlowException {
        Flow origin = makeFlow();
        preparePathComputation(origin.getFlowId(), make3SwitchesPathPair());

        FlowPathRepository repository = setupFlowPathRepositorySpy();
        doThrow(new RuntimeException(injectedErrorMessage))
                .when(repository)
                .delete(argThat(hasProperty("pathId", equalTo(origin.getForwardPathId()))));

        FlowRerouteService service = makeService();
        service.handleRequest(new FlowRerouteFact(
                currentRequestKey, commandContext, origin.getFlowId(), null, false, false, false, null));

        verifyFlowStatus(origin.getFlowId(), FlowStatus.IN_PROGRESS);
        verifyNorthboundSuccessResponse(carrier);

        FlowSegmentRequest speakerRequest;
        while ((speakerRequest = requests.poll()) != null) {
            produceAsyncResponse(service, speakerRequest);
        }

        Flow result = verifyFlowStatus(origin.getFlowId(), FlowStatus.UP);
        verifyPathReplace(origin, result);
    }

    @Test
    public void shouldCompleteRerouteOnErrorDuringResourceDeallocation()
            throws RecoverableException, UnroutableFlowException {
        Flow origin = makeFlow();
        preparePathComputation(origin.getFlowId(), make3SwitchesPathPair());

        FlowRerouteService service = makeService();
        service.handleRequest(new FlowRerouteFact(
                currentRequestKey, commandContext, origin.getFlowId(), null, false, false, false, null));

        verifyFlowStatus(origin.getFlowId(), FlowStatus.IN_PROGRESS);
        verifyNorthboundSuccessResponse(carrier);

        doThrow(new RuntimeException(injectedErrorMessage))
                .when(flowResourcesManager).deallocatePathResources(argThat(
                hasProperty("forward",
                        hasProperty("pathId", equalTo(origin.getForwardPathId())))));

        FlowSegmentRequest speakerRequest;
        while ((speakerRequest = requests.poll()) != null) {
            if (speakerRequest.isVerifyRequest()) {
                service.handleAsyncResponse(currentRequestKey, buildResponseOnVerifyRequest(speakerRequest));
            } else {
                produceAsyncResponse(service, speakerRequest);
            }
        }

        Flow result = verifyFlowStatus(origin.getFlowId(), FlowStatus.UP);
        verifyPathReplace(origin, result);
    }

    @Test
    public void shouldSuccessfullyRerouteFlow() throws RecoverableException, UnroutableFlowException {
        Flow origin = makeFlow();
        origin.setStatus(FlowStatus.DOWN);
        flushFlowChanges(origin);

        preparePathComputation(origin.getFlowId(), make3SwitchesPathPair());

        FlowRerouteService service = makeService();
        service.handleRequest(new FlowRerouteFact(
                currentRequestKey, commandContext, origin.getFlowId(), null, false, false, false, null));

        verifyFlowStatus(origin.getFlowId(), FlowStatus.IN_PROGRESS);
        verifyNorthboundSuccessResponse(carrier);

        FlowSegmentRequest speakerRequest;
        while ((speakerRequest = requests.poll()) != null) {
            produceAsyncResponse(service, speakerRequest);
        }

        Flow result = verifyFlowStatus(origin.getFlowId(), FlowStatus.UP);
        verifyPathReplace(origin, result);
        RerouteResultInfoData expected = RerouteResultInfoData.builder()
                .flowId(origin.getFlowId())
                .success(true)
                .build();
        verify(carrier).sendRerouteResultStatus(eq(origin.getFlowId()), isNull(), any(String.class));
    }

    @Test
    public void shouldSuccessfullyHandleOverlappingRequests() throws RecoverableException, UnroutableFlowException {
        Flow origin = makeFlow();
        origin.setStatus(FlowStatus.DOWN);
        flushFlowChanges(origin);

        when(pathComputer.getPath(makeFlowArgumentMatch(origin.getFlowId()), any()))
                .thenReturn(make2SwitchAltPathPair())
                .thenReturn(make3SwitchesPathPair());

        FlowRerouteService service = makeService();

        service.handleRequest(new FlowRerouteFact(
                currentRequestKey, commandContext, origin.getFlowId(), null, false, false, false, null));

        verifyFlowStatus(origin.getFlowId(), FlowStatus.IN_PROGRESS);

        String overlappingKey = dummyRequestKey + "2";
        service.handleRequest(new FlowRerouteFact(
                overlappingKey, commandContext, origin.getFlowId(), null, false, false, false, null));
        verifyFlowStatus(origin.getFlowId(), FlowStatus.IN_PROGRESS);

        FlowSegmentRequest request;
        while ((request = requests.poll()) != null) {
            produceAsyncResponse(service, request);
        }

        Flow result = verifyFlowStatus(origin.getFlowId(), FlowStatus.UP);
        verifyPathReplace(origin, result);

        FlowPath forwardPath = result.getForwardPath();
        Assert.assertNotNull(forwardPath);
        Assert.assertEquals(1, forwardPath.getSegments().size());  // second request is dropped

        verify(carrier).sendRerouteResultStatus(eq(origin.getFlowId()),
                argThat(hasProperty("message", equalTo("Reroute is in progress"))),
                any(String.class));
    }

    @Test
    public void shouldMakeFlowDownOnTimeoutIfEffectivelyDown() throws RecoverableException, UnroutableFlowException {
        Flow origin = makeFlow();
        preparePathComputation(origin.getFlowId(), make3SwitchesPathPair());

        FlowRerouteService service = makeService();
        service.handleRequest(new FlowRerouteFact(
                currentRequestKey, commandContext, origin.getFlowId(), null, false, false, true, null));

        verifyFlowStatus(origin.getFlowId(), FlowStatus.IN_PROGRESS);
        verifyNorthboundSuccessResponse(carrier);

        service.handleTimeout(currentRequestKey);

        FlowSegmentRequest speakerRequest;
        while ((speakerRequest = requests.poll()) != null) {
            produceAsyncResponse(service, speakerRequest);
        }

        Flow result = verifyFlowStatus(origin.getFlowId(), FlowStatus.DOWN);
        verifyNoPathReplace(origin, result);
    }

    @Test
    public void shouldIgnoreEffectivelyDownStateIfSamePaths() throws RecoverableException, UnroutableFlowException {
        Flow origin = makeFlow();
        preparePathComputation(origin.getFlowId(), make2SwitchesPathPair());

        FlowRerouteService service = makeService();
        service.handleRequest(new FlowRerouteFact(
                currentRequestKey, commandContext, origin.getFlowId(), null, false, false, true, null));

        FlowSegmentRequest speakerRequest;
        while ((speakerRequest = requests.poll()) != null) {
            produceAsyncResponse(service, speakerRequest);
        }

        Flow result = verifyFlowStatus(origin.getFlowId(), FlowStatus.UP);
        verifyNoPathReplace(origin, result);
    }

    @Test
    public void shouldProcessRerouteForValidRequest() throws RecoverableException, UnroutableFlowException {
        Flow origin = makeFlow();
        origin.setTargetPathComputationStrategy(PathComputationStrategy.LATENCY);
        FlowRepository flowRepository = setupFlowRepositorySpy();
        flowRepository.createOrUpdate(origin);
        preparePathComputation(origin.getFlowId(), make3SwitchesPathPair());

        FlowRerouteService service = makeService();
        IslEndpoint affectedEndpoint = extractIslEndpoint(origin);
        service.handleRequest(new FlowRerouteFact(
                currentRequestKey, commandContext, origin.getFlowId(), Collections.singleton(affectedEndpoint),
                false, false, true, null));
        verifyFlowStatus(origin.getFlowId(), FlowStatus.IN_PROGRESS);

        FlowSegmentRequest speakerRequest;
        while ((speakerRequest = requests.poll()) != null) {
            produceAsyncResponse(service, speakerRequest);
        }

        Flow result = verifyFlowStatus(origin.getFlowId(), FlowStatus.UP);
        verifyPathReplace(origin, result);
        assertEquals(PathComputationStrategy.LATENCY, result.getPathComputationStrategy());
        assertNull(result.getTargetPathComputationStrategy());
    }

    @Test
    public void shouldSkipRerouteOnOutdatedRequest() {
        Flow origin = makeFlow();

        FlowRerouteService service = makeService();
        IslEndpoint affectedEndpoint = extractIslEndpoint(origin);
        IslEndpoint notAffectedEndpoint = new IslEndpoint(
                affectedEndpoint.getSwitchId(), affectedEndpoint.getPortNumber() + 1);
        service.handleRequest(new FlowRerouteFact(
                currentRequestKey, commandContext, origin.getFlowId(), Collections.singleton(notAffectedEndpoint),
                false, false, true, null));

        Flow result = verifyFlowStatus(origin.getFlowId(), FlowStatus.UP);
        verifyNoPathReplace(origin, result);
    }

    protected void produceAsyncResponse(FlowRerouteService service, FlowSegmentRequest speakerRequest) {
        service.handleAsyncResponse(currentRequestKey, buildSpeakerResponse(speakerRequest));
    }

    private IslEndpoint extractIslEndpoint(Flow flow) {
        FlowPath forwardPath = flow.getForwardPath();
        assertNotNull(forwardPath);
        List<PathSegment> forwardSegments = forwardPath.getSegments();
        assertFalse(forwardSegments.isEmpty());
        PathSegment firstSegment = forwardSegments.get(0);

        return new IslEndpoint(firstSegment.getSrcSwitch().getSwitchId(), firstSegment.getSrcPort());
    }

    private void preparePathComputation(String flowId, Throwable error)
            throws RecoverableException, UnroutableFlowException {
        doThrow(error).when(pathComputer)
                .getPath(makeFlowArgumentMatch(flowId), any());
    }

    private void preparePathComputation(String flowId, PathPair pathPair)
            throws RecoverableException, UnroutableFlowException {
        when(pathComputer.getPath(makeFlowArgumentMatch(flowId), any())).thenReturn(pathPair);
    }

    @Override
    protected void verifyNorthboundSuccessResponse(FlowGenericCarrier carrierMock) {
        verifyNorthboundSuccessResponse(carrierMock, FlowRerouteResponse.class);
    }

    private FlowRerouteService makeService() {
        return new FlowRerouteService(
                carrier, persistenceManager, pathComputer, flowResourcesManager, TRANSACTION_RETRIES_LIMIT,
                PATH_ALLOCATION_RETRIES_LIMIT, PATH_ALLOCATION_RETRY_DELAY, SPEAKER_COMMAND_RETRIES_LIMIT);
    }

    private Set<SwitchId> getSwitches(PathPair pathPair) {
        return pathPair.getForward().getSegments().stream()
                .flatMap(segment -> Stream.of(segment.getSrcSwitchId(), segment.getDestSwitchId()))
                .collect(toSet());
    }
}
