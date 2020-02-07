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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
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
import org.openkilda.model.IslEndpoint;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
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
import org.openkilda.wfm.share.flow.resources.EncapsulationResources;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResources.PathResources;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.share.flow.resources.transitvlan.TransitVlanEncapsulation;
import org.openkilda.wfm.topology.flowhs.model.FlowRerouteFact;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Mock
    private FlowRerouteHubCarrier carrier;
    @Mock
    private CommandContext commandContext;
    @Mock
    private FlowEventRepository flowEventRepository;

    private FlowRerouteService rerouteService;

    private String currentRequestKey;

    private Map<SwitchId, Switch> swMap = new HashMap<>();

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
        when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);

        SwitchPropertiesRepository switchPropertiesRepository = mock(SwitchPropertiesRepository.class);
        when(switchPropertiesRepository.findBySwitchId(any(SwitchId.class))).thenAnswer((invocation) ->
                Optional.of(SwitchProperties.builder()
                        .multiTable(false)
                        .supportedTransitEncapsulation(DEFAULT_FLOW_ENCAPSULATION_TYPES)
                        .build()));
        when(repositoryFactory.createSwitchPropertiesRepository()).thenReturn(switchPropertiesRepository);

        when(flowEventRepository.existsByTaskId(any())).thenReturn(false);
        when(repositoryFactory.createFlowEventRepository()).thenReturn(flowEventRepository);

        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);

        doAnswer(getSpeakerCommandsAnswer()).when(carrier).sendSpeakerRequest(any());

        doAnswer(invocation -> {
            FlowRerouteFact retry = invocation.getArgument(0);
            currentRequestKey = retry.getKey();
            rerouteService.handlePostponedRequest(retry);
            return null;
        }).when(carrier).injectPostponedRequest(any());

        rerouteService = new FlowRerouteService(carrier, persistenceManager,
                pathComputer, flowResourcesManager, TRANSACTION_RETRIES_LIMIT,
                PATH_ALLOCATION_RETRIES_LIMIT, PATH_ALLOCATION_RETRY_DELAY, SPEAKER_COMMAND_RETRIES_LIMIT);

        currentRequestKey = "test-key";

        for (SwitchId id : new SwitchId[] {SWITCH_1, SWITCH_2, SWITCH_3}) {
            Switch entry = makeSwitch(id);
            swMap.put(id, entry);
            when(switchRepository.findById(eq(id))).thenReturn(Optional.of(entry));
        }
    }

    @Test
    public void shouldFailRerouteFlowIfNoPathAvailable()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenThrow(new UnroutableFlowException("No path found"));
        buildFlowResources();

        rerouteService.handleRequest(new FlowRerouteFact(
                "test_key", commandContext, FLOW_ID, null, false, false, null));

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

        rerouteService.handleRequest(new FlowRerouteFact(
                "test_key", commandContext, FLOW_ID, null, false, false, null));

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

        rerouteService.handleRequest(new FlowRerouteFact(
                "test_key", commandContext, FLOW_ID, null, false, false, null));

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

        rerouteService.handleRequest(new FlowRerouteFact(
                "test_key", commandContext, FLOW_ID, null, false, false, null));

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

        rerouteService.handleRequest(new FlowRerouteFact(
                "test_key", commandContext, FLOW_ID, null, false, false, null));

        verify(flowResourcesManager, times(0)).deallocatePathResources(any());
        verify(flowResourcesManager, times(0)).deallocatePathResources(any(), anyLong(), any());
    }

    @Test
    public void shouldSkipRerouteIfNoNewPathFound()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair());
        buildFlowResources();

        rerouteService.handleRequest(new FlowRerouteFact(
                "test_key", commandContext, FLOW_ID, null, false, false, null));

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

        rerouteService.handleRequest(new FlowRerouteFact(
                "test_key", commandContext, FLOW_ID, null, false, false, null));

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

        rerouteService.handleRequest(new FlowRerouteFact(
                "test_key", commandContext, FLOW_ID, null, false, false, null));

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        rerouteService.handleTimeout("test_key");

        FlowSegmentRequest request;
        while ((request = requests.poll()) != null) {
            produceAsyncResponse("test_key", request);
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

        rerouteService.handleRequest(new FlowRerouteFact(
                "test_key", commandContext, FLOW_ID, null, false, false, null));

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

        rerouteService.handleRequest(new FlowRerouteFact(
                "test_key", commandContext, FLOW_ID, null, false, false, null));

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

        rerouteService.handleRequest(new FlowRerouteFact(
                "test_key", commandContext, FLOW_ID, null, false, false, null));

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

        rerouteService.handleRequest(new FlowRerouteFact(
                "test_key", commandContext, FLOW_ID, null, false, false, null));

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        doAnswer(invocation -> {
            // imitate transaction rollback
            flow.getPath(invocation.getArgument(0)).ifPresent(
                    persistedFlowPath -> persistedFlowPath.setStatus(FlowPathStatus.IN_PROGRESS));

            throw new RuntimeException("A persistence error");
        }).when(flowPathRepository).updateStatus(eq(NEW_FORWARD_FLOW_PATH), eq(FlowPathStatus.ACTIVE));

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

        rerouteService.handleRequest(new FlowRerouteFact(
                "test_key", commandContext, FLOW_ID, null, false, false, null));

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        doThrow(new RuntimeException("A persistence error"))
                .when(flowPathRepository).delete(argThat(
                hasProperty("pathId", equalTo(OLD_FORWARD_FLOW_PATH))));

        FlowSegmentRequest request;
        while ((request = requests.poll()) != null) {
            produceAsyncResponse("test_key", request);
        }

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

        rerouteService.handleRequest(new FlowRerouteFact(
                "test_key", commandContext, FLOW_ID, null, false, false, null));

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        doThrow(new RuntimeException("A persistence error"))
                .when(flowResourcesManager).deallocatePathResources(argThat(
                hasProperty("forward",
                        hasProperty("pathId", equalTo(OLD_FORWARD_FLOW_PATH)))));

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

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(NEW_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(NEW_REVERSE_FLOW_PATH, flow.getReversePathId());
    }

    @Test
    public void shouldSuccessfullyRerouteFlow()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        flow.setStatus(FlowStatus.DOWN);

        when(pathComputer.getPath(any(), any()))
                .thenReturn(build2SwitchPathPair(2, 3))
                .thenReturn(build3SwitchPathPair());
        buildFlowResources();

        rerouteService.handleRequest(new FlowRerouteFact(
                "test_key", commandContext, FLOW_ID, null, false, false, null));

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        FlowSegmentRequest request;
        while ((request = requests.poll()) != null) {
            produceAsyncResponse("test_key", request);
        }

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(NEW_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(NEW_REVERSE_FLOW_PATH, flow.getReversePathId());
    }

    @Test
    public void shouldSuccessfullyHandleOverlappingRequests()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        flow.setStatus(FlowStatus.DOWN);

        when(pathComputer.getPath(any(), any()))
                .thenReturn(build2SwitchPathPair(2, 3))
                .thenReturn(build3SwitchPathPair());

        FlowResources resourcesOrigin = makeFlowResources(
                flow.getFlowId(), flow.getForwardPathId(), flow.getReversePathId());

        PathId pathForwardFirst = new PathId(flow.getFlowId() + "_forward_first");
        PathId pathReverseFirst = new PathId(flow.getFlowId() + "_reverse_first");
        FlowResources resourcesFirst = makeFlowResources(
                flow.getFlowId(), pathForwardFirst, pathReverseFirst, resourcesOrigin);

        PathId pathForwardSecond = new PathId(flow.getFlowId() + "_forward_second");
        PathId pathReverseSecond = new PathId(flow.getFlowId() + "_reverse_second");
        FlowResources resourcesSecond = makeFlowResources(
                flow.getFlowId(), pathForwardSecond, pathReverseSecond, resourcesFirst);

        when(flowResourcesManager.allocateFlowResources(any()))
                .thenReturn(resourcesFirst)
                .thenReturn(resourcesSecond);

        rerouteService.handleRequest(new FlowRerouteFact(
                currentRequestKey, commandContext, flow.getFlowId(), null, false, false, null));
        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());

        rerouteService.handleRequest(new FlowRerouteFact(
                "test_key2", commandContext, flow.getFlowId(), null, false, false, null));
        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());

        FlowSegmentRequest request;
        while ((request = requests.poll()) != null) {
            produceAsyncResponse(currentRequestKey, request);
        }

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(pathForwardSecond, flow.getForwardPathId());
        assertEquals(pathReverseSecond, flow.getReversePathId());
    }

    @Test
    public void shouldFixFlowStatusOnRequestConflict() {
        Flow flow = build2SwitchFlow();
        flow.setStatus(FlowStatus.UP);

        reset(flowEventRepository);
        when(flowEventRepository.existsByTaskId(any()))
                .thenReturn(true)
                .thenReturn(true);

        rerouteService.handleRequest(new FlowRerouteFact(
                currentRequestKey, commandContext, flow.getFlowId(), null, false, false, null));
        FlowSegmentRequest request;
        while ((request = requests.poll()) != null) {
            produceAsyncResponse(currentRequestKey, request);
        }
        assertEquals(FlowStatus.UP, flow.getStatus());

        rerouteService.handleRequest(new FlowRerouteFact(
                currentRequestKey, commandContext, flow.getFlowId(), null, false, true, null));
        while ((request = requests.poll()) != null) {
            produceAsyncResponse(currentRequestKey, request);
        }
        assertEquals(FlowStatus.DOWN, flow.getStatus());
    }

    @Test
    public void shouldMakeFlowDontOnTimeoutIfEffectivelyDown()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair(2, 3));
        buildFlowResources();

        rerouteService.handleRequest(new FlowRerouteFact(
                currentRequestKey, commandContext, FLOW_ID, null, false, true, null));

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        rerouteService.handleTimeout(currentRequestKey);

        FlowSegmentRequest request;
        while ((request = requests.poll()) != null) {
            produceAsyncResponse(currentRequestKey, request);
        }

        assertEquals(FlowStatus.DOWN, flow.getStatus());
        assertEquals(OLD_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(OLD_REVERSE_FLOW_PATH, flow.getReversePathId());
    }

    @Test
    public void shouldIgnoreEffectivelyDownStateIfSamePaths() throws RecoverableException, UnroutableFlowException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair());

        rerouteService.handleRequest(new FlowRerouteFact(
                currentRequestKey, commandContext, FLOW_ID, null, false, true, null));

        FlowSegmentRequest request;
        while ((request = requests.poll()) != null) {
            produceAsyncResponse(currentRequestKey, request);
        }

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(OLD_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(OLD_REVERSE_FLOW_PATH, flow.getReversePathId());
    }

    @Test
    public void shouldProcessRerouteForValidRequest()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair(2, 3));
        buildFlowResources();

        IslEndpoint affectedEndpoint = extractIslEndpoint(flow);
        rerouteService.handleRequest(new FlowRerouteFact(
                currentRequestKey, commandContext, FLOW_ID, Collections.singleton(affectedEndpoint), false, true,
                null));
        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());

        FlowSegmentRequest request;
        while ((request = requests.poll()) != null) {
            produceAsyncResponse(currentRequestKey, request);
        }

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(NEW_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(NEW_REVERSE_FLOW_PATH, flow.getReversePathId());
    }

    @Test
    public void shouldSkipRerouteOnOutdatedRequest() {
        Flow flow = build2SwitchFlow();

        IslEndpoint affectedEndpoint = extractIslEndpoint(flow);
        IslEndpoint notAffectedEndpoint = new IslEndpoint(
                affectedEndpoint.getSwitchId(), affectedEndpoint.getPortNumber() + 1);
        rerouteService.handleRequest(new FlowRerouteFact(
                currentRequestKey, commandContext, FLOW_ID, Collections.singleton(notAffectedEndpoint), false, true,
                null));

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(OLD_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(OLD_REVERSE_FLOW_PATH, flow.getReversePathId());
    }

    protected void produceAsyncResponse(String key, FlowSegmentRequest flowRequest) {
        rerouteService.handleAsyncResponse(key, buildSpeakerResponse(flowRequest));
    }

    private Flow build2SwitchFlow() {
        Switch src = swMap.get(SWITCH_1);
        Switch dst = swMap.get(SWITCH_2);

        Flow flow = Flow.builder().flowId(FLOW_ID)
                .srcSwitch(src).destSwitch(dst)
                .status(FlowStatus.UP)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .build();

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

        when(flowRepository.findById(eq(flow.getFlowId()))).thenReturn(Optional.of(flow));
        when(flowRepository.findById(eq(flow.getFlowId()), any())).thenReturn(Optional.of(flow));

        doAnswer(invocation -> {
            FlowStatus status = invocation.getArgument(1);
            flow.setStatus(status);
            return null;
        }).when(flowRepository).updateStatus(eq(flow.getFlowId()), any());
        doAnswer(invocation -> {
            FlowStatus status = invocation.getArgument(1);
            if (FlowStatus.IN_PROGRESS != flow.getStatus()) {
                flow.setStatus(status);
            }
            return null;
        }).when(flowRepository).updateStatusSafe(eq(flow.getFlowId()), any());

        doAnswer(invocation -> {
            PathId pathId = invocation.getArgument(0);
            return flow.getPath(pathId);
        }).when(flowPathRepository).findById(any());

        doAnswer(invocation -> {
            PathId pathId = invocation.getArgument(0);
            FlowPathStatus status = invocation.getArgument(1);
            flow.getPath(pathId).ifPresent(entity -> entity.setStatus(status));
            return null;
        }).when(flowPathRepository).updateStatus(any(), any());

        return flow;
    }

    private PathPair build2SwitchPathPair() {
        return build2SwitchPathPair(1, 2);
    }

    private PathPair build2SwitchPathPair(int srcPort, int destPort) {
        List<Segment> forwardSegments = Collections.singletonList(
                Segment.builder()
                        .srcSwitchId(SWITCH_1).srcPort(srcPort).destSwitchId(SWITCH_2).destPort(destPort).build());
        List<Segment> reverseSegments = Collections.singletonList(
                Segment.builder()
                        .srcSwitchId(SWITCH_2).srcPort(destPort).destSwitchId(SWITCH_1).destPort(srcPort).build());
        return buildPcePathPair(forwardSegments, reverseSegments);
    }

    private PathPair build3SwitchPathPair() {
        return build3SwitchPathPair(3, 4);
    }

    private PathPair build3SwitchPathPair(int srcPort, int destPort) {
        List<Segment> forwardSegments = new ArrayList<>();
        forwardSegments.add(
                Segment.builder()
                        .srcSwitchId(SWITCH_1).srcPort(srcPort).destPort(destPort).destSwitchId(SWITCH_3).build());
        forwardSegments.add(
                Segment.builder()
                        .srcSwitchId(SWITCH_3).srcPort(srcPort).destPort(destPort).destSwitchId(SWITCH_2).build());

        List<Segment> reverseSegments = new ArrayList<>();
        reverseSegments.add(
                Segment.builder()
                        .srcSwitchId(SWITCH_2).srcPort(destPort).destPort(srcPort).destSwitchId(SWITCH_3).build());
        reverseSegments.add(
                Segment.builder()
                        .srcSwitchId(SWITCH_3).srcPort(destPort).destPort(srcPort).destSwitchId(SWITCH_1).build());

        return buildPcePathPair(forwardSegments, reverseSegments);
    }

    private PathPair buildPcePathPair(List<Segment> forwardSegments, List<Segment> reverseSegments) {
        return PathPair.builder()
                .forward(Path.builder()
                        .srcSwitchId(SWITCH_1).destSwitchId(SWITCH_2)
                        .segments(forwardSegments)
                        .build())
                .reverse(Path.builder()
                        .srcSwitchId(SWITCH_2).destSwitchId(SWITCH_1)
                        .segments(reverseSegments)
                        .build())
                .build();
    }

    private FlowResources buildFlowResources() throws ResourceAllocationException {
        FlowResources original = makeFlowResources(FLOW_ID, OLD_FORWARD_FLOW_PATH, OLD_REVERSE_FLOW_PATH);
        FlowResources target = makeFlowResources(FLOW_ID, NEW_FORWARD_FLOW_PATH, NEW_REVERSE_FLOW_PATH, original);

        when(flowResourcesManager.allocateFlowResources(any())).thenReturn(target);

        return target;
    }

    private Switch makeSwitch(SwitchId id) {
        return Switch.builder().switchId(id).build();
    }

    private FlowResources makeFlowResources(String flowId, PathId forward, PathId reverse) {
        return makeFlowResources(flowId, forward, reverse, null);
    }

    private FlowResources makeFlowResources(
            String flowId, PathId forward, PathId reverse, FlowResources lastAllocated) {
        FlowResources resources = FlowResources.builder()
                .unmaskedCookie(extractLastAllocatedCookie(lastAllocated).getValue())
                .forward(PathResources.builder()
                                 .pathId(forward)
                                 .meterId(allocateForwardMeterId(lastAllocated))
                                 .encapsulationResources(
                                         makeTransitVlanResources(flowId, forward,
                                                                  allocateForwardTransitVlanId(lastAllocated)))
                                 .build())
                .reverse(PathResources.builder()
                                 .pathId(reverse)
                                 .meterId(allocateReverseMeterId(lastAllocated))
                                 .encapsulationResources(
                                         makeTransitVlanResources(flowId, reverse,
                                                                  allocateReverseTransitVlanId(lastAllocated)))
                                 .build())
                .build();

        when(flowResourcesManager.getEncapsulationResources(
                eq(forward), eq(reverse), eq(FlowEncapsulationType.TRANSIT_VLAN)))
                .thenReturn(Optional.of(resources.getForward().getEncapsulationResources()));
        when(flowResourcesManager.getEncapsulationResources(
                eq(reverse), eq(forward), eq(FlowEncapsulationType.TRANSIT_VLAN)))
                .thenReturn(Optional.of(resources.getReverse().getEncapsulationResources()));

        return resources;
    }

    private EncapsulationResources makeTransitVlanResources(String flowId, PathId pathId, int vlanId) {
        TransitVlan entity = TransitVlan.builder()
                .flowId(flowId).pathId(pathId)
                .vlan(vlanId)
                .build();
        return TransitVlanEncapsulation.builder()
                .transitVlan(entity)
                .build();
    }

    private MeterId allocateForwardMeterId(FlowResources lastAllocated) {
        return new MeterId(extractLastAllocatedMeterId(lastAllocated).getValue() + 1);
    }

    private MeterId allocateReverseMeterId(FlowResources lastAllocated) {
        return new MeterId(extractLastAllocatedMeterId(lastAllocated).getValue() + 2);
    }

    private int allocateForwardTransitVlanId(FlowResources lastAllocated) {
        return extractLastAllocatedVlanId(lastAllocated) + 1;
    }

    private int allocateReverseTransitVlanId(FlowResources lastAllocated) {
        return extractLastAllocatedVlanId(lastAllocated) + 2;
    }

    private Cookie extractLastAllocatedCookie(FlowResources resources) {
        if (resources == null) {
            return new Cookie(1);
        }
        return new Cookie(resources.getUnmaskedCookie());
    }

    private MeterId extractLastAllocatedMeterId(FlowResources resources) {
        if (resources == null) {
            return new MeterId(MeterId.MIN_FLOW_METER_ID);
        }
        return resources.getForward().getMeterId();
    }

    private int extractLastAllocatedVlanId(FlowResources resources) {
        if (resources == null) {
            return 100;
        }
        return resources.getForward().getEncapsulationResources().getTransitEncapsulationId();
    }

    private IslEndpoint extractIslEndpoint(Flow flow) {
        FlowPath forwardPath = flow.getForwardPath();
        assertNotNull(forwardPath);
        List<PathSegment> forwardSegments = forwardPath.getSegments();
        assertFalse(forwardSegments.isEmpty());
        PathSegment firstSegment = forwardSegments.get(0);

        return new IslEndpoint(firstSegment.getSrcSwitch().getSwitchId(), firstSegment.getSrcPort());
    }
}
