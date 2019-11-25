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

import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;
import static org.openkilda.model.SwitchProperties.DEFAULT_FLOW_ENCAPSULATION_TYPES;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse.ErrorCode;
import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.SwitchStatus;
import org.openkilda.model.TransitVlan;
import org.openkilda.pce.Path;
import org.openkilda.pce.Path.Segment;
import org.openkilda.pce.PathPair;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.history.FlowEventRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResources.PathResources;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.share.flow.resources.transitvlan.TransitVlanEncapsulation;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Optional;

@RunWith(MockitoJUnitRunner.class)
public class FlowRerouteServiceTest extends AbstractFlowTest {
    private static final int TRANSACTION_RETRIES_LIMIT = 3;
    private static final int PATH_ALLOCATION_RETRIES_LIMIT = 10;
    private static final int PATH_ALLOCATION_RETRY_DELAY = 0;
    private static final int SPEAKER_COMMAND_RETRIES_LIMIT = 0;
    private static final String FLOW_ID = "TEST_FLOW";
    private static final SwitchId SWITCH_1 = new SwitchId(1);
    private static final SwitchId SWITCH_2 = new SwitchId(2);
    private static final SwitchId SWITCH_3 = new SwitchId(3);
    private static final PathId OLD_FORWARD_FLOW_PATH = new PathId(FLOW_ID + "_forward_old");
    private static final PathId OLD_REVERSE_FLOW_PATH = new PathId(FLOW_ID + "_reverse_old");
    private static final PathId NEW_FORWARD_FLOW_PATH = new PathId(FLOW_ID + "_forward_new");
    private static final PathId NEW_REVERSE_FLOW_PATH = new PathId(FLOW_ID + "_reverse_new");
    private static final PathId OLD_PROTECTED_FORWARD_FLOW_PATH = new PathId(FLOW_ID + "_protected_forward_old");
    private static final PathId OLD_PROTECTED_REVERSE_FLOW_PATH = new PathId(FLOW_ID + "_protected_reverse_old");
    private static final PathId NEW_PROTECTED_FORWARD_FLOW_PATH = new PathId(FLOW_ID + "_protected_forward_new");
    private static final PathId NEW_PROTECTED_REVERSE_FLOW_PATH = new PathId(FLOW_ID + "_protected_reverse_new");

    @Mock
    private FlowRerouteHubCarrier carrier;
    @Mock
    private CommandContext commandContext;

    private FlowRerouteService rerouteService;

    @Before
    public void setUp() {
        RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
        when(repositoryFactory.createFlowRepository()).thenReturn(flowRepository);
        when(flowPathRepository.getUsedBandwidthBetweenEndpoints(any(), anyInt(), any(), anyInt())).thenReturn(0L);
        when(repositoryFactory.createFlowPathRepository()).thenReturn(flowPathRepository);
        when(repositoryFactory.createFeatureTogglesRepository()).thenReturn(featureTogglesRepository);

        when(repositoryFactory.createIslRepository()).thenReturn(islRepository);

        SwitchRepository switchRepository = mock(SwitchRepository.class);
        when(switchRepository.reload(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(switchRepository.findById(any(SwitchId.class))).thenAnswer(invocation ->
                Optional.of(Switch.builder()
                        .switchId(invocation.getArgument(0))
                        .status(SwitchStatus.ACTIVE)
                        .features(Sets.newHashSet(SwitchFeature.METERS))
                        .build()));
        when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);

        SwitchPropertiesRepository switchPropertiesRepository = mock(SwitchPropertiesRepository.class);
        when(switchPropertiesRepository.findBySwitchId(any(SwitchId.class))).thenAnswer((invocation) ->
                Optional.of(SwitchProperties.builder()
                        .multiTable(false)
                        .supportedTransitEncapsulation(DEFAULT_FLOW_ENCAPSULATION_TYPES)
                        .build()));
        when(repositoryFactory.createSwitchPropertiesRepository()).thenReturn(switchPropertiesRepository);

        FlowEventRepository flowEventRepository = mock(FlowEventRepository.class);
        when(flowEventRepository.existsByTaskId(any())).thenReturn(false);
        when(repositoryFactory.createFlowEventRepository()).thenReturn(flowEventRepository);

        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);

        doAnswer(getSpeakerCommandsAnswer()).when(carrier).sendSpeakerRequest(any());

        rerouteService = new FlowRerouteService(carrier, persistenceManager,
                pathComputer, flowResourcesManager, TRANSACTION_RETRIES_LIMIT,
                PATH_ALLOCATION_RETRIES_LIMIT, PATH_ALLOCATION_RETRY_DELAY, SPEAKER_COMMAND_RETRIES_LIMIT);
    }

    @Test
    public void shouldFailRerouteFlowIfNoPathAvailable()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenThrow(new UnroutableFlowException("No path found"));
        buildFlowResources();

        rerouteService.handleRequest("test_key", commandContext, FLOW_ID, null, false, null);

        assertEquals(FlowStatus.DOWN, flow.getStatus());
        assertEquals(OLD_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(OLD_REVERSE_FLOW_PATH, flow.getReversePathId());
        verify(pathComputer, times(1)).getPath(any(), any());
        verify(flowResourcesManager, never()).allocateFlowResources(any());
        verify(carrier, never()).sendSpeakerRequest(any());
        verify(carrier, times(1)).sendNorthboundResponse(any());
    }

    @Test
    public void shouldFailRerouteFlowIfRecoverableException()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenThrow(new RecoverableException("PCE error"));
        buildFlowResources();

        rerouteService.handleRequest("test_key", commandContext, FLOW_ID, null, false, null);

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(OLD_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(OLD_REVERSE_FLOW_PATH, flow.getReversePathId());
        verify(pathComputer, times(PATH_ALLOCATION_RETRIES_LIMIT + 1)).getPath(any(), any());
        verify(flowResourcesManager, never()).allocateFlowResources(any());
        verify(carrier, never()).sendSpeakerRequest(any());
        verify(carrier, times(1)).sendNorthboundResponse(any());
    }

    @Test
    public void shouldFailRerouteFlowIfMultipleOverprovisionBandwidth()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair(2, 3));
        buildFlowResources();

        when(islRepository.updateAvailableBandwidth(any(), anyInt(), any(), anyInt(), anyLong()))
                .thenThrow(ResourceAllocationException.class);

        rerouteService.handleRequest("test_key", commandContext, FLOW_ID, null, false, null);

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(OLD_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(OLD_REVERSE_FLOW_PATH, flow.getReversePathId());
        verify(pathComputer, times(PATH_ALLOCATION_RETRIES_LIMIT + 1)).getPath(any(), any());
        verify(islRepository, times(PATH_ALLOCATION_RETRIES_LIMIT + 1))
                .updateAvailableBandwidth(any(), anyInt(), any(), anyInt(), anyLong());
        verify(flowResourcesManager, times(PATH_ALLOCATION_RETRIES_LIMIT + 1)).allocateFlowResources(any());
        verify(carrier, never()).sendSpeakerRequest(any());
        verify(carrier, times(1)).sendNorthboundResponse(any());
    }

    @Test
    public void shouldFailRerouteFlowIfNoResourcesAvailable()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair(2, 3));
        when(flowResourcesManager.allocateFlowResources(any()))
                .thenThrow(new ResourceAllocationException("No resources"));

        rerouteService.handleRequest("test_key", commandContext, FLOW_ID, null, false, null);

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(OLD_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(OLD_REVERSE_FLOW_PATH, flow.getReversePathId());
        verify(pathComputer, times(PATH_ALLOCATION_RETRIES_LIMIT + 1)).getPath(any(), any());
        verify(flowResourcesManager, times(PATH_ALLOCATION_RETRIES_LIMIT + 1)).allocateFlowResources(any());
        verify(carrier, never()).sendSpeakerRequest(any());
        verify(carrier, times(1)).sendNorthboundResponse(any());
    }

    @Test
    public void shouldFailRerouteFlowOnResourcesAllocationConstraint()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair(2, 3));
        buildFlowResources();
        doThrow(new RuntimeException("Must fail")).when(flowPathRepository).lockInvolvedSwitches(any(), any());

        rerouteService.handleRequest("test_key", commandContext, FLOW_ID, null, false, null);

        verify(flowResourcesManager, times(0)).deallocatePathResources(any());
        verify(flowResourcesManager, times(0)).deallocatePathResources(any(), anyLong(), any(), any());
    }

    @Test
    public void shouldSkipRerouteIfNoNewPathFound()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any()))
                .thenReturn(build2SwitchPathPair(flow.getSrcPort(), flow.getDestPort()));
        buildFlowResources();

        rerouteService.handleRequest("test_key", commandContext, FLOW_ID, null, false, null);

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(OLD_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(OLD_REVERSE_FLOW_PATH, flow.getReversePathId());
        verify(carrier, never()).sendSpeakerRequest(any());
        verify(carrier, times(1)).sendNorthboundResponse(any());
    }

    @Test
    public void shouldFailRerouteOnUnsuccessfulInstallation()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair(2, 3));
        buildFlowResources();

        rerouteService.handleRequest("test_key", commandContext, FLOW_ID, null, false, null);

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        FlowSegmentRequest request;
        while ((request = requests.poll()) != null) {
            if (request.isInstallRequest()) {
                rerouteService.handleAsyncResponse("test_key", FlowErrorResponse.errorBuilder()
                        .messageContext(request.getMessageContext())
                        .errorCode(ErrorCode.UNKNOWN)
                        .description("Switch is unavailable")
                        .commandId(request.getCommandId())
                        .metadata(request.getMetadata())
                        .switchId(request.getSwitchId())
                        .build());
            } else {
                rerouteService.handleAsyncResponse("test_key", SpeakerFlowSegmentResponse.builder()
                        .messageContext(request.getMessageContext())
                        .commandId(request.getCommandId())
                        .metadata(request.getMetadata())
                        .switchId(request.getSwitchId())
                        .success(true)
                        .build());
            }
        }

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(OLD_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(OLD_REVERSE_FLOW_PATH, flow.getReversePathId());
    }

    @Test
    public void shouldFailRerouteOnTimeoutDuringInstallation()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair(2, 3));
        buildFlowResources();

        rerouteService.handleRequest("test_key", commandContext, FLOW_ID, null, false, null);

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        rerouteService.handleTimeout("test_key");

        FlowSegmentRequest request;
        while ((request = requests.poll()) != null) {
            if (request.isRemoveRequest()) {
                rerouteService.handleAsyncResponse("test_key", SpeakerFlowSegmentResponse.builder()
                        .messageContext(request.getMessageContext())
                        .commandId(request.getCommandId())
                        .metadata(request.getMetadata())
                        .switchId(request.getSwitchId())
                        .success(true)
                        .build());
            }
        }

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(OLD_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(OLD_REVERSE_FLOW_PATH, flow.getReversePathId());
    }

    @Test
    public void shouldFailRerouteOnUnsuccessfulValidation()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair(2, 3));
        buildFlowResources();

        rerouteService.handleRequest("test_key", commandContext, FLOW_ID, null, false, null);

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        FlowSegmentRequest request;
        while ((request = requests.poll()) != null) {
            if (request.isVerifyRequest()) {
                rerouteService.handleAsyncResponse("test_key", FlowErrorResponse.errorBuilder()
                        .errorCode(ErrorCode.UNKNOWN)
                        .description("Unknown rule")
                        .messageContext(request.getMessageContext())
                        .commandId(request.getCommandId())
                        .metadata(request.getMetadata())
                        .switchId(request.getSwitchId())
                        .build());
            } else {
                rerouteService.handleAsyncResponse("test_key", SpeakerFlowSegmentResponse.builder()
                        .messageContext(request.getMessageContext())
                        .commandId(request.getCommandId())
                        .metadata(request.getMetadata())
                        .switchId(request.getSwitchId())
                        .success(true)
                        .build());
            }
        }

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(OLD_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(OLD_REVERSE_FLOW_PATH, flow.getReversePathId());
    }

    @Test
    public void shouldFailRerouteOnTimeoutDuringValidation()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair(2, 3));
        buildFlowResources();

        rerouteService.handleRequest("test_key", commandContext, FLOW_ID, null, false, null);

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        FlowSegmentRequest request;
        while ((request = requests.poll()) != null) {
            if (request.isVerifyRequest()) {
                rerouteService.handleTimeout("test_key");
            } else {
                rerouteService.handleAsyncResponse("test_key", SpeakerFlowSegmentResponse.builder()
                        .messageContext(request.getMessageContext())
                        .commandId(request.getCommandId())
                        .metadata(request.getMetadata())
                        .switchId(request.getSwitchId())
                        .success(true)
                        .build());
            }
        }

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(OLD_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(OLD_REVERSE_FLOW_PATH, flow.getReversePathId());
    }

    @Test
    public void shouldFailRerouteOnSwapPathsError()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair(2, 3));
        buildFlowResources();

        rerouteService.handleRequest("test_key", commandContext, FLOW_ID, null, false, null);

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        doAnswer(invocation -> {
            // imitate transaction rollback
            Flow persistedFlow = invocation.getArgument(0);
            FlowPath oldForward = persistedFlow.getPaths().stream()
                    .filter(path -> path.getPathId().equals(OLD_FORWARD_FLOW_PATH))
                    .findAny().get();
            persistedFlow.setForwardPath(oldForward);
            FlowPath oldReverse = persistedFlow.getPaths().stream()
                    .filter(path -> path.getPathId().equals(OLD_REVERSE_FLOW_PATH))
                    .findAny().get();
            persistedFlow.setReversePath(oldReverse);

            throw new RuntimeException("A persistence error");
        }).when(flowRepository).createOrUpdate(argThat(
                hasProperty("forwardPathId", equalTo(NEW_FORWARD_FLOW_PATH))));

        handleRequestsWithSuccessResponses();

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(OLD_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(OLD_REVERSE_FLOW_PATH, flow.getReversePathId());
    }

    @Test
    public void shouldFailRerouteOnErrorDuringCompletingFlowPathInstallation()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair(2, 3));
        buildFlowResources();

        rerouteService.handleRequest("test_key", commandContext, FLOW_ID, null, false, null);

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        doAnswer(invocation -> {
            // imitate transaction rollback
            flow.getPath(invocation.getArgument(0)).ifPresent(
                    persistedFlowPath -> persistedFlowPath.setStatus(FlowPathStatus.IN_PROGRESS));

            throw new RuntimeException("A persistence error");
        }).when(flowPathRepository).updateStatus(eq(NEW_FORWARD_FLOW_PATH), eq(FlowPathStatus.ACTIVE));

        handleRequestsWithSuccessResponses();

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(OLD_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(OLD_REVERSE_FLOW_PATH, flow.getReversePathId());
    }

    @Test
    public void shouldCompleteRerouteOnErrorDuringCompletingFlowPathRemoval()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair(2, 3));
        buildFlowResources();

        rerouteService.handleRequest("test_key", commandContext, FLOW_ID, null, false, null);

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        doThrow(new RuntimeException("A persistence error"))
                .when(flowPathRepository).delete(argThat(
                hasProperty("pathId", equalTo(OLD_FORWARD_FLOW_PATH))));

        handleRequestsWithSuccessResponses();

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(NEW_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(NEW_REVERSE_FLOW_PATH, flow.getReversePathId());
    }

    @Test
    public void shouldCompleteRerouteOnErrorDuringResourceDeallocation()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair(2, 3));
        buildFlowResources();

        rerouteService.handleRequest("test_key", commandContext, FLOW_ID, null, false, null);

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        doThrow(new RuntimeException("A persistence error"))
                .when(flowResourcesManager).deallocatePathResources(argThat(
                hasProperty("forward",
                        hasProperty("pathId", equalTo(OLD_FORWARD_FLOW_PATH)))));

        handleRequestsWithSuccessResponses();

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(NEW_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(NEW_REVERSE_FLOW_PATH, flow.getReversePathId());
    }

    @Test
    public void shouldSuccessfullyRerouteFlow()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        flow.setStatus(FlowStatus.DOWN);

        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair(2, 3));
        buildFlowResources();

        rerouteService.handleRequest("test_key", commandContext, FLOW_ID, null, false, null);

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        handleRequestsWithSuccessResponses();

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(NEW_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(NEW_REVERSE_FLOW_PATH, flow.getReversePathId());
    }

    @Test
    public void shouldSuccessfullyRerouteFlowWithProtectedPaths()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = buildFlowWithProtectedPaths();
        flow.setStatus(FlowStatus.DOWN);

        when(pathComputer.getPath(any(), any()))
                .thenReturn(build2SwitchPathPair(2, 3))
                .thenReturn(build3SwitchPathPair(2, 3));
        buildFlowResources();

        rerouteService.handleRequest("test_key", commandContext, FLOW_ID, null, false, null);

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        handleRequestsWithSuccessResponses();

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(NEW_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(NEW_REVERSE_FLOW_PATH, flow.getReversePathId());
        assertEquals(NEW_PROTECTED_FORWARD_FLOW_PATH, flow.getProtectedForwardPathId());
        assertEquals(NEW_PROTECTED_REVERSE_FLOW_PATH, flow.getProtectedReversePathId());
    }

    @Test
    public void shouldCompleteRerouteFlowWithOverlappedProtectedPaths()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = buildFlowWithProtectedPaths();
        flow.setStatus(FlowStatus.DOWN);

        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair(2, 3));
        buildFlowResources();

        rerouteService.handleRequest("test_key", commandContext, FLOW_ID, null, false, null);

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        handleRequestsWithSuccessResponses();

        assertEquals(FlowStatus.DEGRADED, flow.getStatus());
        assertEquals(NEW_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(NEW_REVERSE_FLOW_PATH, flow.getReversePathId());
        assertEquals(OLD_PROTECTED_FORWARD_FLOW_PATH, flow.getProtectedForwardPathId());
        assertEquals(OLD_PROTECTED_REVERSE_FLOW_PATH, flow.getProtectedReversePathId());
    }

    @Test
    public void shouldSuccessfullyRerouteFlowWithoutOldPaths()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = buildFlowWithoutPaths(SWITCH_1, SWITCH_2);
        flow.setStatus(FlowStatus.DOWN);

        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair(2, 3));
        buildFlowResources();

        rerouteService.handleRequest("test_key", commandContext, FLOW_ID, null, false, null);

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        handleRequestsWithSuccessResponses();

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(NEW_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(NEW_REVERSE_FLOW_PATH, flow.getReversePathId());
    }

    private void handleRequestsWithSuccessResponses() {
        FlowSegmentRequest request;
        while ((request = requests.poll()) != null) {
            if (request.isVerifyRequest()) {
                rerouteService.handleAsyncResponse("test_key", buildResponseOnVerifyRequest(request));
            } else {
                rerouteService.handleAsyncResponse("test_key", SpeakerFlowSegmentResponse.builder()
                        .messageContext(request.getMessageContext())
                        .commandId(request.getCommandId())
                        .metadata(request.getMetadata())
                        .switchId(request.getSwitchId())
                        .success(true)
                        .build());
            }
        }
    }

    private PathPair build2SwitchPathPair(int srcPort, int destPort) {
        return PathPair.builder()
                .forward(Path.builder()
                        .srcSwitchId(SWITCH_1).destSwitchId(SWITCH_2)
                        .segments(Collections.singletonList(Segment.builder()
                                .srcSwitchId(SWITCH_1)
                                .srcPort(srcPort)
                                .destSwitchId(SWITCH_2)
                                .destPort(destPort)
                                .build()))
                        .build())
                .reverse(Path.builder()
                        .srcSwitchId(SWITCH_2).destSwitchId(SWITCH_1)
                        .segments(Collections.singletonList(Segment.builder()
                                .srcSwitchId(SWITCH_2)
                                .srcPort(destPort)
                                .destSwitchId(SWITCH_1)
                                .destPort(srcPort)
                                .build()))
                        .build())
                .build();
    }

    private PathPair build3SwitchPathPair(int srcPort, int destPort) {
        return PathPair.builder()
                .forward(Path.builder()
                        .srcSwitchId(SWITCH_1).destSwitchId(SWITCH_2)
                        .segments(asList(
                                Segment.builder()
                                        .srcSwitchId(SWITCH_1)
                                        .srcPort(srcPort)
                                        .destSwitchId(SWITCH_3)
                                        .destPort(3)
                                        .build(),
                                Segment.builder()
                                        .srcSwitchId(SWITCH_3)
                                        .srcPort(4)
                                        .destSwitchId(SWITCH_2)
                                        .destPort(destPort)
                                        .build()
                        )).build())
                .reverse(Path.builder()
                        .srcSwitchId(SWITCH_2).destSwitchId(SWITCH_1)
                        .segments(asList(
                                Segment.builder()
                                        .srcSwitchId(SWITCH_2)
                                        .srcPort(destPort)
                                        .destSwitchId(SWITCH_3)
                                        .destPort(4)
                                        .build(),
                                Segment.builder()
                                        .srcSwitchId(SWITCH_3)
                                        .srcPort(3)
                                        .destSwitchId(SWITCH_1)
                                        .destPort(srcPort)
                                        .build()
                        )).build())
                .build();
    }

    private Flow buildFlowWithProtectedPaths() {
        Flow flow = buildFlowWithoutPaths(SWITCH_1, SWITCH_2);
        flow.setAllocateProtectedPath(true);
        Switch src = flow.getSrcSwitch();
        Switch intermediate = Switch.builder().switchId(SWITCH_3).build();
        Switch dst = flow.getDestSwitch();

        FlowPath oldForwardPath = FlowPath.builder()
                .pathId(OLD_FORWARD_FLOW_PATH)
                .flow(flow)
                .cookie(Cookie.buildForwardCookie(2))
                .srcSwitch(src).destSwitch(dst)
                .status(FlowPathStatus.ACTIVE)
                .build();
        oldForwardPath.setSegments(Collections.singletonList(PathSegment.builder()
                .srcSwitch(src)
                .srcPort(1)
                .destSwitch(dst)
                .destPort(2)
                .build()));
        flow.setForwardPath(oldForwardPath);

        FlowPath oldReversePath = FlowPath.builder()
                .pathId(OLD_REVERSE_FLOW_PATH)
                .flow(flow)
                .cookie(Cookie.buildReverseCookie(2))
                .srcSwitch(dst).destSwitch(src)
                .status(FlowPathStatus.ACTIVE)
                .build();
        oldReversePath.setSegments(Collections.singletonList(PathSegment.builder()
                .srcSwitch(dst)
                .srcPort(2)
                .destSwitch(src)
                .destPort(1)
                .build()));
        flow.setReversePath(oldReversePath);

        FlowPath oldProtectedForwardPath = FlowPath.builder()
                .pathId(OLD_PROTECTED_FORWARD_FLOW_PATH)
                .flow(flow)
                .cookie(Cookie.buildForwardCookie(3))
                .srcSwitch(src).destSwitch(dst)
                .status(FlowPathStatus.ACTIVE)
                .build();
        oldProtectedForwardPath.setSegments(asList(
                PathSegment.builder()
                        .srcSwitch(src)
                        .srcPort(1)
                        .destSwitch(intermediate)
                        .destPort(3)
                        .build(),
                PathSegment.builder()
                        .srcSwitch(intermediate)
                        .srcPort(4)
                        .destSwitch(dst)
                        .destPort(2)
                        .build()
        ));
        flow.setProtectedForwardPath(oldProtectedForwardPath);

        FlowPath oldProtectedReversePath = FlowPath.builder()
                .pathId(OLD_PROTECTED_REVERSE_FLOW_PATH)
                .flow(flow)
                .cookie(Cookie.buildReverseCookie(3))
                .srcSwitch(dst).destSwitch(src)
                .status(FlowPathStatus.ACTIVE)
                .build();
        oldProtectedReversePath.setSegments(asList(
                PathSegment.builder()
                        .srcSwitch(dst)
                        .srcPort(2)
                        .destSwitch(intermediate)
                        .destPort(4)
                        .build(),
                PathSegment.builder()
                        .srcSwitch(intermediate)
                        .srcPort(3)
                        .destSwitch(src)
                        .destPort(1)
                        .build()
        ));
        flow.setProtectedReversePath(oldProtectedReversePath);

        return flow;
    }

    private Flow build2SwitchFlow() {
        Flow flow = buildFlowWithoutPaths(SWITCH_1, SWITCH_2);
        Switch src = flow.getSrcSwitch();
        Switch dst = flow.getDestSwitch();

        FlowPath oldForwardPath = FlowPath.builder()
                .pathId(OLD_FORWARD_FLOW_PATH)
                .flow(flow)
                .cookie(Cookie.buildForwardCookie(2))
                .srcSwitch(src).destSwitch(dst)
                .status(FlowPathStatus.ACTIVE)
                .build();
        oldForwardPath.setSegments(Collections.singletonList(PathSegment.builder()
                .srcSwitch(src)
                .srcPort(1)
                .destSwitch(dst)
                .destPort(2)
                .build()));
        flow.setForwardPath(oldForwardPath);

        FlowPath oldReversePath = FlowPath.builder()
                .pathId(OLD_REVERSE_FLOW_PATH)
                .flow(flow)
                .cookie(Cookie.buildReverseCookie(2))
                .srcSwitch(dst).destSwitch(src)
                .status(FlowPathStatus.ACTIVE)
                .build();
        oldReversePath.setSegments(Collections.singletonList(PathSegment.builder()
                .srcSwitch(dst)
                .srcPort(2)
                .destSwitch(src)
                .destPort(1)
                .build()));
        flow.setReversePath(oldReversePath);

        return flow;
    }


    private Flow buildFlowWithoutPaths(SwitchId srcSwitchId, SwitchId destSwitchId) {
        Switch src = Switch.builder().switchId(srcSwitchId).build();
        Switch dst = Switch.builder().switchId(destSwitchId).build();

        Flow flow = Flow.builder().flowId(FLOW_ID)
                .srcSwitch(src)
                .srcPort(1)
                .destSwitch(dst)
                .destPort(2)
                .status(FlowStatus.UP)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .build();

        when(flowRepository.findById(any())).thenReturn(Optional.of(flow));
        when(flowRepository.findById(any(), any())).thenReturn(Optional.of(flow));

        doAnswer(invocation -> {
            FlowStatus status = invocation.getArgument(1);
            flow.setStatus(status);
            return null;
        }).when(flowRepository).updateStatus(any(), any());

        doAnswer(invocation -> {
            PathId pathId = invocation.getArgument(0);
            return flow.getPath(pathId);
        }).when(flowPathRepository).findById(any());

        doAnswer(invocation -> {
            PathId pathId = invocation.getArgument(0);
            FlowPathStatus status = invocation.getArgument(1);
            flow.getPath(pathId).get().setStatus(status);
            return null;
        }).when(flowPathRepository).updateStatus(any(), any());

        return flow;
    }

    private FlowResources buildFlowResources() throws ResourceAllocationException {
        FlowResources flowResources = FlowResources.builder()
                .unmaskedCookie(11)
                .forward(PathResources.builder()
                        .pathId(NEW_FORWARD_FLOW_PATH)
                        .meterId(new MeterId(MeterId.MIN_FLOW_METER_ID + 11))
                        .build())
                .reverse(PathResources.builder()
                        .pathId(NEW_REVERSE_FLOW_PATH)
                        .meterId(new MeterId(MeterId.MIN_FLOW_METER_ID + 12))
                        .build())
                .build();

        FlowResources protectedFlowResources = FlowResources.builder()
                .unmaskedCookie(12)
                .forward(PathResources.builder()
                        .pathId(NEW_PROTECTED_FORWARD_FLOW_PATH)
                        .meterId(new MeterId(MeterId.MIN_FLOW_METER_ID + 21))
                        .build())
                .reverse(PathResources.builder()
                        .pathId(NEW_PROTECTED_REVERSE_FLOW_PATH)
                        .meterId(new MeterId(MeterId.MIN_FLOW_METER_ID + 22))
                        .build())
                .build();

        when(flowResourcesManager.allocateFlowResources(any()))
                .thenReturn(flowResources)
                .thenReturn(protectedFlowResources);

        when(flowResourcesManager.getEncapsulationResources(eq(NEW_FORWARD_FLOW_PATH), eq(NEW_REVERSE_FLOW_PATH),
                eq(FlowEncapsulationType.TRANSIT_VLAN)))
                .thenReturn(Optional.of(TransitVlanEncapsulation.builder().transitVlan(
                        TransitVlan.builder().flowId(FLOW_ID).pathId(NEW_FORWARD_FLOW_PATH).vlan(1001).build())
                        .build()));
        when(flowResourcesManager.getEncapsulationResources(eq(NEW_REVERSE_FLOW_PATH), eq(NEW_FORWARD_FLOW_PATH),
                eq(FlowEncapsulationType.TRANSIT_VLAN)))
                .thenReturn(Optional.of(TransitVlanEncapsulation.builder().transitVlan(
                        TransitVlan.builder().flowId(FLOW_ID).pathId(NEW_REVERSE_FLOW_PATH).vlan(1002).build())
                        .build()));

        when(flowResourcesManager.getEncapsulationResources(eq(OLD_FORWARD_FLOW_PATH), eq(OLD_REVERSE_FLOW_PATH),
                eq(FlowEncapsulationType.TRANSIT_VLAN)))
                .thenReturn(Optional.of(TransitVlanEncapsulation.builder().transitVlan(
                        TransitVlan.builder().flowId(FLOW_ID).pathId(OLD_FORWARD_FLOW_PATH).vlan(2001).build())
                        .build()));
        when(flowResourcesManager.getEncapsulationResources(eq(OLD_REVERSE_FLOW_PATH), eq(OLD_FORWARD_FLOW_PATH),
                eq(FlowEncapsulationType.TRANSIT_VLAN)))
                .thenReturn(Optional.of(TransitVlanEncapsulation.builder().transitVlan(
                        TransitVlan.builder().flowId(FLOW_ID).pathId(OLD_REVERSE_FLOW_PATH).vlan(2002).build())
                        .build()));

        when(flowResourcesManager.getEncapsulationResources(eq(NEW_PROTECTED_FORWARD_FLOW_PATH),
                eq(NEW_PROTECTED_REVERSE_FLOW_PATH),
                eq(FlowEncapsulationType.TRANSIT_VLAN)))
                .thenReturn(Optional.of(TransitVlanEncapsulation.builder().transitVlan(
                        TransitVlan.builder().flowId(FLOW_ID).pathId(NEW_PROTECTED_FORWARD_FLOW_PATH).vlan(3001)
                                .build())
                        .build()));
        when(flowResourcesManager.getEncapsulationResources(eq(NEW_PROTECTED_REVERSE_FLOW_PATH),
                eq(NEW_PROTECTED_FORWARD_FLOW_PATH),
                eq(FlowEncapsulationType.TRANSIT_VLAN)))
                .thenReturn(Optional.of(TransitVlanEncapsulation.builder().transitVlan(
                        TransitVlan.builder().flowId(FLOW_ID).pathId(NEW_PROTECTED_REVERSE_FLOW_PATH).vlan(3002)
                                .build())
                        .build()));

        when(flowResourcesManager.getEncapsulationResources(eq(OLD_PROTECTED_FORWARD_FLOW_PATH),
                eq(OLD_PROTECTED_REVERSE_FLOW_PATH),
                eq(FlowEncapsulationType.TRANSIT_VLAN)))
                .thenReturn(Optional.of(TransitVlanEncapsulation.builder().transitVlan(
                        TransitVlan.builder().flowId(FLOW_ID).pathId(OLD_PROTECTED_FORWARD_FLOW_PATH).vlan(4001)
                                .build())
                        .build()));
        when(flowResourcesManager.getEncapsulationResources(eq(OLD_PROTECTED_REVERSE_FLOW_PATH),
                eq(OLD_PROTECTED_FORWARD_FLOW_PATH),
                eq(FlowEncapsulationType.TRANSIT_VLAN)))
                .thenReturn(Optional.of(TransitVlanEncapsulation.builder().transitVlan(
                        TransitVlan.builder().flowId(FLOW_ID).pathId(OLD_PROTECTED_REVERSE_FLOW_PATH).vlan(4002)
                                .build())
                        .build()));

        return flowResources;
    }
}
