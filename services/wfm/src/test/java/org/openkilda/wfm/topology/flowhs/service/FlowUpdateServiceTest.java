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
import org.openkilda.messaging.command.flow.FlowRequest;
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
public class FlowUpdateServiceTest extends AbstractFlowTest {
    private static final int TRANSACTION_RETRIES_LIMIT = 3;
    private static final int PATH_ALLOCATION_RETRIES_LIMIT = 10;
    private static final int PATH_ALLOCATION_RETRY_DELAY = 0;
    private static final int SPEAKER_COMMAND_RETRIES_LIMIT = 0;
    private static final String FLOW_ID = "TEST_FLOW";
    private static final SwitchId SWITCH_1 = new SwitchId(1);
    private static final SwitchId SWITCH_2 = new SwitchId(2);
    private static final SwitchId SWITCH_3 = new SwitchId(3);
    private static final SwitchId SWITCH_4 = new SwitchId(4);
    private static final PathId OLD_FORWARD_FLOW_PATH = new PathId(FLOW_ID + "_forward_old");
    private static final PathId OLD_REVERSE_FLOW_PATH = new PathId(FLOW_ID + "_reverse_old");
    private static final PathId NEW_FORWARD_FLOW_PATH = new PathId(FLOW_ID + "_forward_new");
    private static final PathId NEW_REVERSE_FLOW_PATH = new PathId(FLOW_ID + "_reverse_new");

    @Mock
    private FlowUpdateHubCarrier carrier;
    @Mock
    private CommandContext commandContext;

    private FlowUpdateService updateService;

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
        when(switchRepository.findById(any(SwitchId.class))).thenAnswer((invocation) ->
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

        updateService = new FlowUpdateService(carrier, persistenceManager,
                pathComputer, flowResourcesManager, TRANSACTION_RETRIES_LIMIT,
                PATH_ALLOCATION_RETRIES_LIMIT, PATH_ALLOCATION_RETRY_DELAY, SPEAKER_COMMAND_RETRIES_LIMIT);
    }

    @Test
    public void shouldFailUpdateFlowIfNoPathAvailable()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenThrow(new UnroutableFlowException("No path found"));
        buildFlowResources();

        FlowRequest request = FlowRequest.builder()
                .flowId(FLOW_ID)
                .bandwidth(1000L)
                .sourceSwitch(SWITCH_1)
                .sourcePort(11)
                .sourceVlan(101)
                .destinationSwitch(SWITCH_3)
                .destinationPort(12)
                .destinationVlan(102)
                .build();

        updateService.handleRequest("test_key", commandContext, request);

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(SWITCH_1, flow.getSrcSwitch().getSwitchId());
        assertEquals(1, flow.getSrcPort());
        assertEquals(SWITCH_2, flow.getDestSwitch().getSwitchId());
        assertEquals(2, flow.getDestPort());
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

        FlowRequest request = FlowRequest.builder()
                .flowId(FLOW_ID)
                .bandwidth(1000L)
                .sourceSwitch(SWITCH_1)
                .sourcePort(11)
                .sourceVlan(101)
                .destinationSwitch(SWITCH_3)
                .destinationPort(12)
                .destinationVlan(102)
                .build();

        updateService.handleRequest("test_key", commandContext, request);

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
        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair(SWITCH_1, 11, SWITCH_3, 12));
        buildFlowResources();

        when(islRepository.updateAvailableBandwidth(any(), anyInt(), any(), anyInt(), anyLong()))
                .thenThrow(ResourceAllocationException.class);

        FlowRequest request = FlowRequest.builder()
                .flowId(FLOW_ID)
                .bandwidth(1000L)
                .sourceSwitch(SWITCH_1)
                .sourcePort(11)
                .sourceVlan(101)
                .destinationSwitch(SWITCH_3)
                .destinationPort(12)
                .destinationVlan(102)
                .build();

        updateService.handleRequest("test_key", commandContext, request);

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
    public void shouldFailUpdateFlowIfNoResourcesAvailable()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair(SWITCH_1, 11, SWITCH_3, 12));
        when(flowResourcesManager.allocateFlowResources(any()))
                .thenThrow(new ResourceAllocationException("No resources"));

        FlowRequest request = FlowRequest.builder()
                .flowId(FLOW_ID)
                .bandwidth(1000L)
                .sourceSwitch(SWITCH_1)
                .sourcePort(11)
                .sourceVlan(101)
                .destinationSwitch(SWITCH_3)
                .destinationPort(12)
                .destinationVlan(102)
                .build();

        updateService.handleRequest("test_key", commandContext, request);

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(SWITCH_1, flow.getSrcSwitch().getSwitchId());
        assertEquals(1, flow.getSrcPort());
        assertEquals(SWITCH_2, flow.getDestSwitch().getSwitchId());
        assertEquals(2, flow.getDestPort());
        assertEquals(OLD_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(OLD_REVERSE_FLOW_PATH, flow.getReversePathId());
        verify(pathComputer, times(PATH_ALLOCATION_RETRIES_LIMIT + 1)).getPath(any(), any());
        verify(flowResourcesManager, times(PATH_ALLOCATION_RETRIES_LIMIT + 1)).allocateFlowResources(any());
        verify(carrier, never()).sendSpeakerRequest(any());
        verify(carrier, times(1)).sendNorthboundResponse(any());
    }

    @Test
    public void shouldFailUpdateFlowOnResourcesAllocationConstraint()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair(SWITCH_1, 11, SWITCH_3, 12));
        buildFlowResources();
        doThrow(new RuntimeException("Must fail")).when(flowPathRepository).lockInvolvedSwitches(any(), any());

        FlowRequest request = FlowRequest.builder()
                .flowId(FLOW_ID)
                .bandwidth(1000L)
                .sourceSwitch(SWITCH_1)
                .sourcePort(11)
                .sourceVlan(101)
                .destinationSwitch(SWITCH_3)
                .destinationPort(12)
                .destinationVlan(102)
                .build();

        updateService.handleRequest("test_key", commandContext, request);

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(SWITCH_1, flow.getSrcSwitch().getSwitchId());
        assertEquals(1, flow.getSrcPort());
        assertEquals(SWITCH_2, flow.getDestSwitch().getSwitchId());
        assertEquals(2, flow.getDestPort());
        assertEquals(OLD_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(OLD_REVERSE_FLOW_PATH, flow.getReversePathId());
        verify(flowResourcesManager, never()).deallocatePathResources(any());
        verify(flowResourcesManager, never()).deallocatePathResources(any(), anyLong(), any());
    }

    @Test
    public void shouldFailUpdateOnUnsuccessfulInstallation()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair(SWITCH_1, 11, SWITCH_3, 12));
        buildFlowResources();

        FlowRequest request = FlowRequest.builder()
                .flowId(FLOW_ID)
                .bandwidth(1000L)
                .sourceSwitch(SWITCH_1)
                .sourcePort(11)
                .sourceVlan(101)
                .destinationSwitch(SWITCH_3)
                .destinationPort(12)
                .destinationVlan(102)
                .build();

        updateService.handleRequest("test_key", commandContext, request);

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        FlowSegmentRequest flowRequest;
        while ((flowRequest = requests.poll()) != null) {
            if (flowRequest.isInstallRequest()) {
                updateService.handleAsyncResponse("test_key", FlowErrorResponse.errorBuilder()
                        .messageContext(flowRequest.getMessageContext())
                        .errorCode(ErrorCode.UNKNOWN)
                        .description("Switch is unavailable")
                        .commandId(flowRequest.getCommandId())
                        .metadata(flowRequest.getMetadata())
                        .switchId(flowRequest.getSwitchId())
                        .build());
            } else {
                updateService.handleAsyncResponse("test_key", SpeakerFlowSegmentResponse.builder()
                        .messageContext(flowRequest.getMessageContext())
                        .commandId(flowRequest.getCommandId())
                        .metadata(flowRequest.getMetadata())
                        .switchId(flowRequest.getSwitchId())
                        .success(true)
                        .build());
            }
        }

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(SWITCH_1, flow.getSrcSwitch().getSwitchId());
        assertEquals(SWITCH_2, flow.getDestSwitch().getSwitchId());
        assertEquals(OLD_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(OLD_REVERSE_FLOW_PATH, flow.getReversePathId());
    }

    @Test
    public void shouldFailUpdateOnTimeoutDuringInstallation()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair(SWITCH_1, 11, SWITCH_3, 12));
        buildFlowResources();

        FlowRequest request = FlowRequest.builder()
                .flowId(FLOW_ID)
                .bandwidth(1000L)
                .sourceSwitch(SWITCH_1)
                .sourcePort(11)
                .sourceVlan(101)
                .destinationSwitch(SWITCH_3)
                .destinationPort(12)
                .destinationVlan(102)
                .build();

        updateService.handleRequest("test_key", commandContext, request);

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        updateService.handleTimeout("test_key");

        FlowSegmentRequest flowRequest;
        while ((flowRequest = requests.poll()) != null) {
            if (flowRequest.isRemoveRequest()) {
                updateService.handleAsyncResponse("test_key", SpeakerFlowSegmentResponse.builder()
                        .messageContext(flowRequest.getMessageContext())
                        .commandId(flowRequest.getCommandId())
                        .metadata(flowRequest.getMetadata())
                        .switchId(flowRequest.getSwitchId())
                        .success(true)
                        .build());
            }
        }

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(SWITCH_1, flow.getSrcSwitch().getSwitchId());
        assertEquals(SWITCH_2, flow.getDestSwitch().getSwitchId());
        assertEquals(OLD_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(OLD_REVERSE_FLOW_PATH, flow.getReversePathId());
    }

    @Test
    public void shouldFailUpdateOnUnsuccessfulValidation()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair(SWITCH_1, 11, SWITCH_3, 12));
        buildFlowResources();

        FlowRequest request = FlowRequest.builder()
                .flowId(FLOW_ID)
                .bandwidth(1000L)
                .sourceSwitch(SWITCH_1)
                .sourcePort(11)
                .sourceVlan(101)
                .destinationSwitch(SWITCH_3)
                .destinationPort(12)
                .destinationVlan(102)
                .build();

        updateService.handleRequest("test_key", commandContext, request);

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        FlowSegmentRequest flowRequest;
        while ((flowRequest = requests.poll()) != null) {
            if (flowRequest.isVerifyRequest()) {
                updateService.handleAsyncResponse("test_key", FlowErrorResponse.errorBuilder()
                        .errorCode(ErrorCode.UNKNOWN)
                        .description("Unknown rule")
                        .messageContext(flowRequest.getMessageContext())
                        .commandId(flowRequest.getCommandId())
                        .metadata(flowRequest.getMetadata())
                        .switchId(flowRequest.getSwitchId())
                        .build());
            } else {
                updateService.handleAsyncResponse("test_key", SpeakerFlowSegmentResponse.builder()
                        .messageContext(flowRequest.getMessageContext())
                        .commandId(flowRequest.getCommandId())
                        .metadata(flowRequest.getMetadata())
                        .switchId(flowRequest.getSwitchId())
                        .success(true)
                        .build());
            }
        }

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(SWITCH_1, flow.getSrcSwitch().getSwitchId());
        assertEquals(SWITCH_2, flow.getDestSwitch().getSwitchId());
        assertEquals(OLD_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(OLD_REVERSE_FLOW_PATH, flow.getReversePathId());
    }

    @Test
    public void shouldFailUpdateOnTimeoutDuringValidation()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair(SWITCH_1, 11, SWITCH_3, 12));
        buildFlowResources();

        FlowRequest request = FlowRequest.builder()
                .flowId(FLOW_ID)
                .bandwidth(1000L)
                .sourceSwitch(SWITCH_1)
                .sourcePort(11)
                .sourceVlan(101)
                .destinationSwitch(SWITCH_3)
                .destinationPort(12)
                .destinationVlan(102)
                .build();

        updateService.handleRequest("test_key", commandContext, request);

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        FlowSegmentRequest flowRequest;
        while ((flowRequest = requests.poll()) != null) {
            if (flowRequest.isVerifyRequest()) {
                updateService.handleTimeout("test_key");
            } else {
                updateService.handleAsyncResponse("test_key", SpeakerFlowSegmentResponse.builder()
                        .messageContext(flowRequest.getMessageContext())
                        .commandId(flowRequest.getCommandId())
                        .metadata(flowRequest.getMetadata())
                        .switchId(flowRequest.getSwitchId())
                        .success(true)
                        .build());
            }
        }

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(SWITCH_1, flow.getSrcSwitch().getSwitchId());
        assertEquals(SWITCH_2, flow.getDestSwitch().getSwitchId());
        assertEquals(OLD_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(OLD_REVERSE_FLOW_PATH, flow.getReversePathId());
    }

    @Test
    public void shouldFailUpdateOnSwapPathsError()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair(SWITCH_1, 11, SWITCH_3, 12));
        buildFlowResources();

        FlowRequest request = FlowRequest.builder()
                .flowId(FLOW_ID)
                .bandwidth(1000L)
                .sourceSwitch(SWITCH_1)
                .sourcePort(11)
                .sourceVlan(101)
                .destinationSwitch(SWITCH_3)
                .destinationPort(12)
                .destinationVlan(102)
                .build();

        updateService.handleRequest("test_key", commandContext, request);

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

        FlowSegmentRequest flowRequest;
        while ((flowRequest = requests.poll()) != null) {
            if (flowRequest.isVerifyRequest()) {
                updateService.handleAsyncResponse("test_key", buildResponseOnVerifyRequest(flowRequest));
            } else {
                updateService.handleAsyncResponse("test_key", SpeakerFlowSegmentResponse.builder()
                        .messageContext(flowRequest.getMessageContext())
                        .commandId(flowRequest.getCommandId())
                        .metadata(flowRequest.getMetadata())
                        .switchId(flowRequest.getSwitchId())
                        .success(true)
                        .build());
            }
        }

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(SWITCH_1, flow.getSrcSwitch().getSwitchId());
        assertEquals(SWITCH_2, flow.getDestSwitch().getSwitchId());
        assertEquals(OLD_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(OLD_REVERSE_FLOW_PATH, flow.getReversePathId());
    }

    @Test
    public void shouldFailUpdateOnErrorDuringCompletingFlowPathInstallation()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair(SWITCH_1, 11, SWITCH_3, 12));
        buildFlowResources();

        FlowRequest request = FlowRequest.builder()
                .flowId(FLOW_ID)
                .bandwidth(1000L)
                .sourceSwitch(SWITCH_1)
                .sourcePort(11)
                .sourceVlan(101)
                .destinationSwitch(SWITCH_3)
                .destinationPort(12)
                .destinationVlan(102)
                .build();

        updateService.handleRequest("test_key", commandContext, request);

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        doAnswer(invocation -> {
            // imitate transaction rollback
            flow.getPath(invocation.getArgument(0)).ifPresent(
                    persistedFlowPath -> persistedFlowPath.setStatus(FlowPathStatus.IN_PROGRESS));

            throw new RuntimeException("A persistence error");
        }).when(flowPathRepository).updateStatus(eq(NEW_FORWARD_FLOW_PATH), eq(FlowPathStatus.ACTIVE));

        FlowSegmentRequest flowRequest;
        while ((flowRequest = requests.poll()) != null) {
            if (flowRequest.isVerifyRequest()) {
                updateService.handleAsyncResponse("test_key",
                        buildResponseOnVerifyRequest(flowRequest));
            } else {
                updateService.handleAsyncResponse("test_key", SpeakerFlowSegmentResponse.builder()
                        .messageContext(flowRequest.getMessageContext())
                        .commandId(flowRequest.getCommandId())
                        .metadata(flowRequest.getMetadata())
                        .switchId(flowRequest.getSwitchId())
                        .success(true)
                        .build());
            }
        }

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(SWITCH_1, flow.getSrcSwitch().getSwitchId());
        assertEquals(SWITCH_2, flow.getDestSwitch().getSwitchId());
        assertEquals(OLD_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(OLD_REVERSE_FLOW_PATH, flow.getReversePathId());
    }

    @Test
    public void shouldCompleteUpdateOnErrorDuringCompletingFlowPathRemoval()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair(SWITCH_1, 11, SWITCH_3, 12));
        buildFlowResources();

        FlowRequest request = FlowRequest.builder()
                .flowId(FLOW_ID)
                .bandwidth(1000L)
                .sourceSwitch(SWITCH_1)
                .sourcePort(11)
                .sourceVlan(101)
                .destinationSwitch(SWITCH_3)
                .destinationPort(12)
                .destinationVlan(102)
                .build();

        updateService.handleRequest("test_key", commandContext, request);

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        doThrow(new RuntimeException("A persistence error"))
                .when(flowPathRepository).delete(argThat(
                hasProperty("pathId", equalTo(OLD_FORWARD_FLOW_PATH))));

        FlowSegmentRequest flowRequest;
        while ((flowRequest = requests.poll()) != null) {
            if (flowRequest.isVerifyRequest()) {
                updateService.handleAsyncResponse("test_key",
                        buildResponseOnVerifyRequest(flowRequest));
            } else {
                updateService.handleAsyncResponse("test_key", SpeakerFlowSegmentResponse.builder()
                        .messageContext(flowRequest.getMessageContext())
                        .commandId(flowRequest.getCommandId())
                        .metadata(flowRequest.getMetadata())
                        .switchId(flowRequest.getSwitchId())
                        .success(true)
                        .build());
            }
        }

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(SWITCH_1, flow.getSrcSwitch().getSwitchId());
        assertEquals(SWITCH_3, flow.getDestSwitch().getSwitchId());
        assertEquals(NEW_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(NEW_REVERSE_FLOW_PATH, flow.getReversePathId());
    }

    @Test
    public void shouldCompleteUpdateOnErrorDuringResourceDeallocation()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair(SWITCH_1, 11, SWITCH_3, 12));
        buildFlowResources();

        FlowRequest request = FlowRequest.builder()
                .flowId(FLOW_ID)
                .bandwidth(1000L)
                .sourceSwitch(SWITCH_1)
                .sourcePort(11)
                .sourceVlan(101)
                .destinationSwitch(SWITCH_3)
                .destinationPort(12)
                .destinationVlan(102)
                .build();

        updateService.handleRequest("test_key", commandContext, request);

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        doThrow(new RuntimeException("A persistence error"))
                .when(flowResourcesManager).deallocatePathResources(argThat(
                hasProperty("forward",
                        hasProperty("pathId", equalTo(OLD_FORWARD_FLOW_PATH)))));

        FlowSegmentRequest flowRequest;
        while ((flowRequest = requests.poll()) != null) {
            if (flowRequest.isVerifyRequest()) {
                updateService.handleAsyncResponse("test_key",
                        buildResponseOnVerifyRequest(flowRequest));
            } else {
                updateService.handleAsyncResponse("test_key", SpeakerFlowSegmentResponse.builder()
                        .messageContext(flowRequest.getMessageContext())
                        .commandId(flowRequest.getCommandId())
                        .metadata(flowRequest.getMetadata())
                        .switchId(flowRequest.getSwitchId())
                        .success(true)
                        .build());
            }
        }

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(SWITCH_1, flow.getSrcSwitch().getSwitchId());
        assertEquals(SWITCH_3, flow.getDestSwitch().getSwitchId());
        assertEquals(NEW_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(NEW_REVERSE_FLOW_PATH, flow.getReversePathId());
    }

    @Test
    public void shouldSuccessfullyUpdateFlow()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        flow.setStatus(FlowStatus.DOWN);

        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair(SWITCH_3, 11, SWITCH_4, 12));
        buildFlowResources();

        FlowRequest request = FlowRequest.builder()
                .flowId(FLOW_ID)
                .bandwidth(1000L)
                .sourceSwitch(SWITCH_3)
                .sourcePort(11)
                .sourceVlan(101)
                .destinationSwitch(SWITCH_4)
                .destinationPort(12)
                .destinationVlan(102)
                .build();

        updateService.handleRequest("test_key", commandContext, request);

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        FlowSegmentRequest flowRequest;
        while ((flowRequest = requests.poll()) != null) {
            if (flowRequest.isVerifyRequest()) {
                updateService.handleAsyncResponse("test_key",
                        buildResponseOnVerifyRequest(flowRequest));
            } else {
                updateService.handleAsyncResponse("test_key", SpeakerFlowSegmentResponse.builder()
                        .messageContext(flowRequest.getMessageContext())
                        .commandId(flowRequest.getCommandId())
                        .metadata(flowRequest.getMetadata())
                        .switchId(flowRequest.getSwitchId())
                        .success(true)
                        .build());
            }
        }

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(SWITCH_3, flow.getSrcSwitch().getSwitchId());
        assertEquals(SWITCH_4, flow.getDestSwitch().getSwitchId());
        assertEquals(NEW_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(NEW_REVERSE_FLOW_PATH, flow.getReversePathId());
    }

    @Test
    public void shouldSuccessfullyUpdateFlowOnSameEndpoints()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        flow.setStatus(FlowStatus.DOWN);

        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair());
        buildFlowResources();

        FlowRequest request = FlowRequest.builder()
                .flowId(FLOW_ID)
                .bandwidth(flow.getBandwidth() + 1000L)
                .sourceSwitch(flow.getSrcSwitch().getSwitchId())
                .sourcePort(flow.getSrcPort())
                .sourceVlan(flow.getSrcVlan())
                .destinationSwitch(flow.getDestSwitch().getSwitchId())
                .destinationPort(flow.getDestPort())
                .destinationVlan(flow.getDestVlan())
                .build();

        updateService.handleRequest("test_key", commandContext, request);

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        FlowSegmentRequest flowRequest;
        while ((flowRequest = requests.poll()) != null) {
            if (flowRequest.isVerifyRequest()) {
                updateService.handleAsyncResponse("test_key",
                        buildResponseOnVerifyRequest(flowRequest));
            } else {
                updateService.handleAsyncResponse("test_key", SpeakerFlowSegmentResponse.builder()
                        .messageContext(flowRequest.getMessageContext())
                        .commandId(flowRequest.getCommandId())
                        .metadata(flowRequest.getMetadata())
                        .switchId(flowRequest.getSwitchId())
                        .success(true)
                        .build());
            }
        }

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(SWITCH_1, flow.getSrcSwitch().getSwitchId());
        assertEquals(SWITCH_2, flow.getDestSwitch().getSwitchId());
        assertEquals(NEW_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(NEW_REVERSE_FLOW_PATH, flow.getReversePathId());
    }

    private PathPair build2SwitchPathPair() {
        return build2SwitchPathPair(SWITCH_1, 1, SWITCH_2, 2);
    }

    private PathPair build2SwitchPathPair(SwitchId srcSwitch, int srcPort, SwitchId destSwitch, int destPort) {
        return PathPair.builder()
                .forward(Path.builder()
                        .srcSwitchId(srcSwitch).destSwitchId(destSwitch)
                        .segments(Collections.singletonList(Segment.builder()
                                .srcSwitchId(srcSwitch)
                                .srcPort(srcPort)
                                .destSwitchId(destSwitch)
                                .destPort(destPort)
                                .build()))
                        .build())
                .reverse(Path.builder()
                        .srcSwitchId(destSwitch).destSwitchId(srcSwitch)
                        .segments(Collections.singletonList(Segment.builder()
                                .srcSwitchId(destSwitch)
                                .srcPort(destPort)
                                .destSwitchId(srcSwitch)
                                .destPort(srcPort)
                                .build()))
                        .build())
                .build();
    }

    private Flow build2SwitchFlow() {
        Switch src = Switch.builder().switchId(SWITCH_1).build();
        Switch dst = Switch.builder().switchId(SWITCH_2).build();

        Flow flow = Flow.builder().flowId(FLOW_ID)
                .srcSwitch(src)
                .srcPort(1)
                .destSwitch(dst)
                .destPort(2)
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
                .unmaskedCookie(1)
                .forward(PathResources.builder()
                        .pathId(NEW_FORWARD_FLOW_PATH)
                        .meterId(new MeterId(MeterId.MIN_FLOW_METER_ID + 1))
                        .build())
                .reverse(PathResources.builder()
                        .pathId(NEW_REVERSE_FLOW_PATH)
                        .meterId(new MeterId(MeterId.MIN_FLOW_METER_ID + 2))
                        .build())
                .build();

        when(flowResourcesManager.allocateFlowResources(any())).thenReturn(flowResources);

        when(flowResourcesManager.getEncapsulationResources(eq(NEW_FORWARD_FLOW_PATH), eq(NEW_REVERSE_FLOW_PATH),
                eq(FlowEncapsulationType.TRANSIT_VLAN)))
                .thenReturn(Optional.of(TransitVlanEncapsulation.builder().transitVlan(
                        TransitVlan.builder().flowId(FLOW_ID).pathId(NEW_FORWARD_FLOW_PATH).vlan(101).build())
                        .build()));
        when(flowResourcesManager.getEncapsulationResources(eq(NEW_REVERSE_FLOW_PATH), eq(NEW_FORWARD_FLOW_PATH),
                eq(FlowEncapsulationType.TRANSIT_VLAN)))
                .thenReturn(Optional.of(TransitVlanEncapsulation.builder().transitVlan(
                        TransitVlan.builder().flowId(FLOW_ID).pathId(NEW_REVERSE_FLOW_PATH).vlan(102).build())
                        .build()));

        when(flowResourcesManager.getEncapsulationResources(eq(OLD_FORWARD_FLOW_PATH), eq(OLD_REVERSE_FLOW_PATH),
                eq(FlowEncapsulationType.TRANSIT_VLAN)))
                .thenReturn(Optional.of(TransitVlanEncapsulation.builder().transitVlan(
                        TransitVlan.builder().flowId(FLOW_ID).pathId(NEW_FORWARD_FLOW_PATH).vlan(201).build())
                        .build()));
        when(flowResourcesManager.getEncapsulationResources(eq(OLD_REVERSE_FLOW_PATH), eq(OLD_FORWARD_FLOW_PATH),
                eq(FlowEncapsulationType.TRANSIT_VLAN)))
                .thenReturn(Optional.of(TransitVlanEncapsulation.builder().transitVlan(
                        TransitVlan.builder().flowId(FLOW_ID).pathId(NEW_REVERSE_FLOW_PATH).vlan(202).build())
                        .build()));

        return flowResources;
    }
}
