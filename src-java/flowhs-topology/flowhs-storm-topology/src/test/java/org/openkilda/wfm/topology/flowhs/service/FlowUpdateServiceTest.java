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

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse.ErrorCode;
import org.openkilda.messaging.command.flow.FlowRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathId;
import org.openkilda.pce.PathPair;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Set;
import java.util.stream.Collectors;

@RunWith(MockitoJUnitRunner.class)
public class FlowUpdateServiceTest extends AbstractFlowTest {
    private static final int TRANSACTION_RETRIES_LIMIT = 3;
    private static final int PATH_ALLOCATION_RETRIES_LIMIT = 10;
    private static final int PATH_ALLOCATION_RETRY_DELAY = 0;
    private static final int SPEAKER_COMMAND_RETRIES_LIMIT = 0;

    @Mock
    private FlowUpdateHubCarrier carrier;

    @Before
    public void setUp() {
        doAnswer(getSpeakerCommandsAnswer()).when(carrier).sendSpeakerRequest(any());

        // must be done before first service create attempt, because repository objects are cached inside FSM actions
        setupFlowRepositorySpy();
        setupFlowPathRepositorySpy();
        setupIslRepositorySpy();
    }

    @Test
    public void shouldFailUpdateFlowIfNoPathAvailable() throws RecoverableException, UnroutableFlowException {
        Flow origin = makeFlow();
        when(pathComputer.getPath(makeFlowArgumentMatch(origin.getFlowId()), any()))
                .thenThrow(new UnroutableFlowException(injectedErrorMessage));

        FlowRequest request = makeRequest()
                .flowId(origin.getFlowId())
                .bandwidth(origin.getBandwidth() + 1)
                .build();

        Flow result = testExpectedFailure(request, origin, ErrorType.NOT_FOUND);
        Assert.assertEquals(origin.getBandwidth(), result.getBandwidth());

        verify(pathComputer, times(11)).getPath(makeFlowArgumentMatch(origin.getFlowId()), any());
    }

    @Test
    public void shouldFailRerouteFlowIfRecoverableException() throws RecoverableException, UnroutableFlowException {
        Flow origin = makeFlow();
        when(pathComputer.getPath(makeFlowArgumentMatch(origin.getFlowId()), any()))
                .thenThrow(new RecoverableException(injectedErrorMessage));

        FlowRequest request = makeRequest()
                .flowId(origin.getFlowId())
                .bandwidth(origin.getBandwidth() + 1)
                .build();
        testExpectedFailure(request, origin, ErrorType.INTERNAL_ERROR);

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

        FlowRequest request = makeRequest()
                .flowId(origin.getFlowId())
                .build();
        testExpectedFailure(request, origin, ErrorType.INTERNAL_ERROR);

        verify(pathComputer, times(PATH_ALLOCATION_RETRIES_LIMIT + 1))
                .getPath(makeFlowArgumentMatch(origin.getFlowId()), any());
        verify(repository, times(PATH_ALLOCATION_RETRIES_LIMIT + 1))
                .updateAvailableBandwidth(any(), anyInt(), any(), anyInt(), anyLong());
    }

    @Test
    public void shouldFailUpdateFlowIfNoResourcesAvailable()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow origin = makeFlow();
        preparePathComputation(origin.getFlowId(), make3SwitchesPathPair());

        doThrow(new ResourceAllocationException(injectedErrorMessage))
                .when(flowResourcesManager).allocateFlowResources(makeFlowArgumentMatch(origin.getFlowId()));

        FlowRequest request = makeRequest()
                .flowId(origin.getFlowId())
                .build();

        testExpectedFailure(request, origin, ErrorType.INTERNAL_ERROR);

        verify(pathComputer, times(PATH_ALLOCATION_RETRIES_LIMIT + 1))
                .getPath(makeFlowArgumentMatch(origin.getFlowId()), any());
        verify(flowResourcesManager, times(PATH_ALLOCATION_RETRIES_LIMIT + 1))
                .allocateFlowResources(makeFlowArgumentMatch(origin.getFlowId()));
    }

    @Test
    public void shouldFailUpdateFlowOnResourcesAllocationConstraint()
            throws RecoverableException, UnroutableFlowException {
        Flow origin = makeFlow();
        preparePathComputation(origin.getFlowId(), make3SwitchesPathPair());

        FlowPathRepository repository = setupFlowPathRepositorySpy();
        doThrow(new RuntimeException(injectedErrorMessage))
                .when(repository).createOrUpdate(any(FlowPath.class));

        FlowRequest request = makeRequest()
                .flowId(origin.getFlowId())
                .build();
        testExpectedFailure(request, origin, ErrorType.INTERNAL_ERROR);
    }

    private Flow testExpectedFailure(FlowRequest request, Flow origin, ErrorType expectedError) {
        makeService().handleRequest(dummyRequestKey, commandContext, request);

        verifyNoSpeakerInteraction(carrier);
        verifyNorthboundErrorResponse(carrier, expectedError);

        Flow result = verifyFlowStatus(origin.getFlowId(), FlowStatus.UP);
        verifyNoPathReplace(origin, result);

        return result;
    }

    @Test
    public void shouldFailUpdateOnUnsuccessfulInstallation() throws RecoverableException, UnroutableFlowException {
        Flow origin = makeFlow();
        preparePathComputation(origin.getFlowId(), make3SwitchesPathPair());

        FlowRequest request = makeRequest()
                .flowId(origin.getFlowId())
                .build();

        FlowUpdateService service = makeService();
        service.handleRequest(dummyRequestKey, commandContext, request);

        verifyFlowStatus(origin.getFlowId(), FlowStatus.IN_PROGRESS);
        verifyNorthboundSuccessResponse(carrier);

        FlowSegmentRequest speakerRequest;
        while ((speakerRequest = requests.poll()) != null) {
            if (speakerRequest.isInstallRequest()) {
                service.handleAsyncResponse(dummyRequestKey, FlowErrorResponse.errorBuilder()
                        .messageContext(speakerRequest.getMessageContext())
                        .errorCode(ErrorCode.UNKNOWN)
                        .description("Switch is unavailable")
                        .commandId(speakerRequest.getCommandId())
                        .metadata(speakerRequest.getMetadata())
                        .switchId(speakerRequest.getSwitchId())
                        .build());
            } else {
                service.handleAsyncResponse(dummyRequestKey, SpeakerFlowSegmentResponse.builder()
                        .messageContext(speakerRequest.getMessageContext())
                        .commandId(speakerRequest.getCommandId())
                        .metadata(speakerRequest.getMetadata())
                        .switchId(speakerRequest.getSwitchId())
                        .success(true)
                        .build());
            }
        }

        Flow result = verifyFlowStatus(origin.getFlowId(), FlowStatus.UP);
        verifyNoPathReplace(origin, result);
    }

    @Test
    public void shouldFailUpdateOnTimeoutDuringInstallation() throws RecoverableException, UnroutableFlowException {
        Flow origin = makeFlow();
        preparePathComputation(origin.getFlowId(), make3SwitchesPathPair());

        FlowRequest request = makeRequest()
                .flowId(origin.getFlowId())
                .build();

        FlowUpdateService service = makeService();
        service.handleRequest(dummyRequestKey, commandContext, request);

        verifyFlowStatus(origin.getFlowId(), FlowStatus.IN_PROGRESS);
        verifyNorthboundSuccessResponse(carrier);

        service.handleTimeout(dummyRequestKey);

        FlowSegmentRequest speakerRequest;
        while ((speakerRequest = requests.poll()) != null) {
            if (speakerRequest.isRemoveRequest()) {
                service.handleAsyncResponse(dummyRequestKey, SpeakerFlowSegmentResponse.builder()
                        .messageContext(speakerRequest.getMessageContext())
                        .commandId(speakerRequest.getCommandId())
                        .metadata(speakerRequest.getMetadata())
                        .switchId(speakerRequest.getSwitchId())
                        .success(true)
                        .build());
            }
        }

        Flow result = verifyFlowStatus(origin.getFlowId(), FlowStatus.UP);
        verifyNoPathReplace(origin, result);
    }

    @Test
    public void shouldFailUpdateOnUnsuccessfulValidation() throws RecoverableException, UnroutableFlowException {
        Flow origin = makeFlow();
        preparePathComputation(origin.getFlowId(), make3SwitchesPathPair());

        FlowRequest request = makeRequest()
                .flowId(origin.getFlowId())
                .build();

        FlowUpdateService service = makeService();
        service.handleRequest(dummyRequestKey, commandContext, request);

        verifyFlowStatus(origin.getFlowId(), FlowStatus.IN_PROGRESS);
        verifyNorthboundSuccessResponse(carrier);

        FlowSegmentRequest speakerRequest;
        while ((speakerRequest = requests.poll()) != null) {
            if (speakerRequest.isVerifyRequest()) {
                service.handleAsyncResponse(dummyRequestKey, FlowErrorResponse.errorBuilder()
                        .errorCode(ErrorCode.UNKNOWN)
                        .description(injectedErrorMessage)
                        .messageContext(speakerRequest.getMessageContext())
                        .commandId(speakerRequest.getCommandId())
                        .metadata(speakerRequest.getMetadata())
                        .switchId(speakerRequest.getSwitchId())
                        .build());
            } else {
                service.handleAsyncResponse(dummyRequestKey, SpeakerFlowSegmentResponse.builder()
                        .messageContext(speakerRequest.getMessageContext())
                        .commandId(speakerRequest.getCommandId())
                        .metadata(speakerRequest.getMetadata())
                        .switchId(speakerRequest.getSwitchId())
                        .success(true)
                        .build());
            }
        }

        Flow result = verifyFlowStatus(origin.getFlowId(), FlowStatus.UP);
        verifyNoPathReplace(origin, result);
    }

    @Test
    public void shouldFailUpdateOnTimeoutDuringValidation() throws RecoverableException, UnroutableFlowException {
        Flow origin = makeFlow();
        preparePathComputation(origin.getFlowId(), make3SwitchesPathPair());

        FlowRequest request = makeRequest()
                .flowId(origin.getFlowId())
                .build();

        FlowUpdateService service = makeService();
        service.handleRequest(dummyRequestKey, commandContext, request);

        verifyFlowStatus(origin.getFlowId(), FlowStatus.IN_PROGRESS);
        verifyNorthboundSuccessResponse(carrier);

        FlowSegmentRequest speakerRequest;
        while ((speakerRequest = requests.poll()) != null) {
            if (speakerRequest.isVerifyRequest()) {
                service.handleTimeout(dummyRequestKey);
            } else {
                service.handleAsyncResponse(dummyRequestKey, SpeakerFlowSegmentResponse.builder()
                        .messageContext(speakerRequest.getMessageContext())
                        .commandId(speakerRequest.getCommandId())
                        .metadata(speakerRequest.getMetadata())
                        .switchId(speakerRequest.getSwitchId())
                        .success(true)
                        .build());
            }
        }

        Flow result = verifyFlowStatus(origin.getFlowId(), FlowStatus.UP);
        verifyNoPathReplace(origin, result);
    }

    @Test
    public void shouldFailUpdateOnSwapPathsError() throws RecoverableException, UnroutableFlowException {
        Flow origin = makeFlow();
        preparePathComputation(origin.getFlowId(), make3SwitchesPathPair());

        FlowRequest request = makeRequest()
                .flowId(origin.getFlowId())
                .build();

        FlowRepository repository = setupFlowRepositorySpy();
        doThrow(new RuntimeException(injectedErrorMessage))
                .when(repository)
                .createOrUpdate(argThat(hasProperty(
                        "forwardPathId", Matchers.not(equalTo(origin.getForwardPathId())))));

        FlowUpdateService service = makeService();
        service.handleRequest(dummyRequestKey, commandContext, request);

        verifyFlowStatus(origin.getFlowId(), FlowStatus.IN_PROGRESS);
        verifyNorthboundSuccessResponse(carrier);

        FlowSegmentRequest speakerRequest;
        while ((speakerRequest = requests.poll()) != null) {
            if (speakerRequest.isVerifyRequest()) {
                service.handleAsyncResponse(dummyRequestKey, buildResponseOnVerifyRequest(speakerRequest));
            } else {
                service.handleAsyncResponse(dummyRequestKey, SpeakerFlowSegmentResponse.builder()
                        .messageContext(speakerRequest.getMessageContext())
                        .commandId(speakerRequest.getCommandId())
                        .metadata(speakerRequest.getMetadata())
                        .switchId(speakerRequest.getSwitchId())
                        .success(true)
                        .build());
            }
        }

        Flow result = verifyFlowStatus(origin.getFlowId(), FlowStatus.UP);
        verifyNoPathReplace(origin, result);
    }

    @Test
    public void shouldFailUpdateOnErrorDuringCompletingFlowPathInstallation()
            throws RecoverableException, UnroutableFlowException {
        Flow origin = makeFlow();
        preparePathComputation(origin.getFlowId(), make3SwitchesPathPair());

        FlowRequest request = makeRequest()
                .flowId(origin.getFlowId())
                .build();

        FlowPathRepository repository = setupFlowPathRepositorySpy();
        Set<PathId> originalPaths = origin.getPaths().stream()
                .map(FlowPath::getPathId)
                .collect(Collectors.toSet());
        doThrow(new RuntimeException(injectedErrorMessage))
                .when(repository)
                .updateStatus(
                        ArgumentMatchers.argThat(argument -> ! originalPaths.contains(argument)),
                        eq(FlowPathStatus.ACTIVE));

        FlowUpdateService service = makeService();
        service.handleRequest(dummyRequestKey, commandContext, request);

        verifyFlowStatus(origin.getFlowId(), FlowStatus.IN_PROGRESS);
        verifyNorthboundSuccessResponse(carrier);

        FlowSegmentRequest speakerRequest;
        while ((speakerRequest = requests.poll()) != null) {
            if (speakerRequest.isVerifyRequest()) {
                service.handleAsyncResponse(dummyRequestKey, buildResponseOnVerifyRequest(speakerRequest));
            } else {
                service.handleAsyncResponse(dummyRequestKey, SpeakerFlowSegmentResponse.builder()
                        .messageContext(speakerRequest.getMessageContext())
                        .commandId(speakerRequest.getCommandId())
                        .metadata(speakerRequest.getMetadata())
                        .switchId(speakerRequest.getSwitchId())
                        .success(true)
                        .build());
            }
        }

        Flow result = verifyFlowStatus(origin.getFlowId(), FlowStatus.UP);
        verifyNoPathReplace(origin, result);
    }

    @Test
    public void shouldCompleteUpdateOnErrorDuringCompletingFlowPathRemoval()
            throws RecoverableException, UnroutableFlowException {
        Flow origin = makeFlow();
        preparePathComputation(origin.getFlowId(), make3SwitchesPathPair());

        FlowRequest request = makeRequest()
                .flowId(origin.getFlowId())
                .build();

        FlowPathRepository repository = setupFlowPathRepositorySpy();
        doThrow(new RuntimeException(injectedErrorMessage))
                .when(repository)
                .delete(argThat(hasProperty("pathId", equalTo(origin.getForwardPathId()))));

        FlowUpdateService service = makeService();
        service.handleRequest(dummyRequestKey, commandContext, request);

        verifyFlowStatus(origin.getFlowId(), FlowStatus.IN_PROGRESS);
        verifyNorthboundSuccessResponse(carrier);

        FlowSegmentRequest speakerRequest;
        while ((speakerRequest = requests.poll()) != null) {
            if (speakerRequest.isVerifyRequest()) {
                service.handleAsyncResponse(dummyRequestKey, buildResponseOnVerifyRequest(speakerRequest));
            } else {
                service.handleAsyncResponse(dummyRequestKey, SpeakerFlowSegmentResponse.builder()
                        .messageContext(speakerRequest.getMessageContext())
                        .commandId(speakerRequest.getCommandId())
                        .metadata(speakerRequest.getMetadata())
                        .switchId(speakerRequest.getSwitchId())
                        .success(true)
                        .build());
            }
        }

        Flow result = verifyFlowStatus(origin.getFlowId(), FlowStatus.UP);
        verifyPathReplace(origin, result);
    }

    @Test
    public void shouldCompleteUpdateOnErrorDuringResourceDeallocation()
            throws RecoverableException, UnroutableFlowException {
        Flow origin = makeFlow();
        preparePathComputation(origin.getFlowId(), make3SwitchesPathPair());

        FlowRequest request = makeRequest()
                .flowId(origin.getFlowId())
                .build();

        FlowUpdateService service = makeService();
        service.handleRequest(dummyRequestKey, commandContext, request);

        verifyFlowStatus(origin.getFlowId(), FlowStatus.IN_PROGRESS);
        verifyNorthboundSuccessResponse(carrier);

        doThrow(new RuntimeException(injectedErrorMessage))
                .when(flowResourcesManager)
                .deallocatePathResources(argThat(
                        hasProperty("forward",
                                hasProperty("pathId", equalTo(origin.getForwardPathId())))));

        FlowSegmentRequest speakerRequest;
        while ((speakerRequest = requests.poll()) != null) {
            if (speakerRequest.isVerifyRequest()) {
                service.handleAsyncResponse(dummyRequestKey,
                        buildResponseOnVerifyRequest(speakerRequest));
            } else {
                service.handleAsyncResponse(dummyRequestKey, SpeakerFlowSegmentResponse.builder()
                        .messageContext(speakerRequest.getMessageContext())
                        .commandId(speakerRequest.getCommandId())
                        .metadata(speakerRequest.getMetadata())
                        .switchId(speakerRequest.getSwitchId())
                        .success(true)
                        .build());
            }
        }

        Flow result = verifyFlowStatus(origin.getFlowId(), FlowStatus.UP);
        verifyPathReplace(origin, result);
    }

    @Test
    public void shouldSuccessfullyUpdateFlow() throws RecoverableException, UnroutableFlowException {
        Flow origin = makeFlow();
        origin.setStatus(FlowStatus.DOWN); // TODO(surabujin): why for we forcing initial DOWN state here?
        flushFlowChanges(origin);

        preparePathComputation(origin.getFlowId(), make3SwitchesPathPair());

        FlowRequest request = makeRequest()
                .flowId(origin.getFlowId())
                .build();
        testExpectedSuccess(request, origin);
    }

    @Test
    public void shouldSuccessfullyUpdateFlowOnSameEndpoints()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow origin = makeFlow();
        origin.setStatus(FlowStatus.DOWN); // TODO(surabujin): why for we forcing initial DOWN state here?
        flushFlowChanges(origin);

        preparePathComputation(origin.getFlowId(), make2SwitchesPathPair());  // same path to current one

        FlowRequest request = makeRequest()
                .flowId(origin.getFlowId())
                .bandwidth(origin.getBandwidth() + 1000L)
                .build();
        testExpectedSuccess(request, origin);
    }

    private void testExpectedSuccess(FlowRequest request, Flow origin) {
        FlowUpdateService service = makeService();
        service.handleRequest(dummyRequestKey, commandContext, request);

        verifyFlowStatus(origin.getFlowId(), FlowStatus.IN_PROGRESS);
        verifyNorthboundSuccessResponse(carrier);

        FlowSegmentRequest speakerRequest;
        while ((speakerRequest = requests.poll()) != null) {
            if (speakerRequest.isVerifyRequest()) {
                service.handleAsyncResponse(dummyRequestKey, buildResponseOnVerifyRequest(speakerRequest));
            } else {
                service.handleAsyncResponse(dummyRequestKey, SpeakerFlowSegmentResponse.builder()
                        .messageContext(speakerRequest.getMessageContext())
                        .commandId(speakerRequest.getCommandId())
                        .metadata(speakerRequest.getMetadata())
                        .switchId(speakerRequest.getSwitchId())
                        .success(true)
                        .build());
            }
        }

        Flow result = verifyFlowStatus(origin.getFlowId(), FlowStatus.UP);
        verifyPathReplace(origin, result);
    }

    private void preparePathComputation(String flowId, PathPair pathPair)
            throws RecoverableException, UnroutableFlowException {
        when(pathComputer.getPath(makeFlowArgumentMatch(flowId), any())).thenReturn(pathPair);
    }

    private FlowUpdateService makeService() {
        return new FlowUpdateService(carrier, persistenceManager,
                pathComputer, flowResourcesManager, TRANSACTION_RETRIES_LIMIT,
                PATH_ALLOCATION_RETRIES_LIMIT, PATH_ALLOCATION_RETRY_DELAY, SPEAKER_COMMAND_RETRIES_LIMIT);
    }
}
