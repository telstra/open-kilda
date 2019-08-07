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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.openkilda.floodlight.flow.request.GetInstalledRule;
import org.openkilda.floodlight.flow.request.InstallEgressRule;
import org.openkilda.floodlight.flow.request.InstallFlowRule;
import org.openkilda.floodlight.flow.request.InstallIngressRule;
import org.openkilda.floodlight.flow.request.RemoveRule;
import org.openkilda.floodlight.flow.request.SpeakerFlowRequest;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse.ErrorCode;
import org.openkilda.floodlight.flow.response.FlowResponse;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.flow.FlowRequest;
import org.openkilda.model.FeatureToggles;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.IslConfig;
import org.openkilda.model.KildaConfiguration;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeatures;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.model.TransitVlan;
import org.openkilda.pce.Path;
import org.openkilda.pce.Path.Segment;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathPair;
import org.openkilda.persistence.repositories.FeatureTogglesRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.KildaConfigurationRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchFeaturesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.config.IslCostConfig;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResources.PathResources;
import org.openkilda.wfm.share.flow.resources.transitvlan.TransitVlanEncapsulation;

import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class FlowCreateServiceTest extends AbstractFlowTest {

    private static final SwitchId SRC_SWITCH = new SwitchId(1L);
    private static final SwitchId TRANSIT_SWITCH = new SwitchId(2L);
    private static final SwitchId DST_SWITCH = new SwitchId(3L);
    private static final long COOKIE = 101L;

    private FlowCreateService target;

    @Mock
    private PathComputer pathComputer;
    @Mock
    private FlowCreateHubCarrier carrier;
    @Mock
    private IslCostConfig islCostConfig;

    @Captor
    private ArgumentCaptor<Flow> flowCaptor;

    @Before
    public void init() {
        RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);
        KildaConfigurationRepository configurationRepository = mock(KildaConfigurationRepository.class);
        when(configurationRepository.get()).thenReturn(KildaConfiguration.DEFAULTS);
        when(repositoryFactory.createKildaConfigurationRepository()).thenReturn(configurationRepository);

        FeatureTogglesRepository featureTogglesRepository = mock(FeatureTogglesRepository.class);
        when(featureTogglesRepository.find()).thenReturn(Optional.of(getFeatureToggles()));
        when(repositoryFactory.createFeatureTogglesRepository()).thenReturn(featureTogglesRepository);

        when(repositoryFactory.createFlowRepository()).thenReturn(flowRepository);
        when(repositoryFactory.createFlowPathRepository()).thenReturn(flowPathRepository);

        SwitchRepository switchRepository = mock(SwitchRepository.class);
        when(switchRepository.reload(any(Switch.class))).thenAnswer((invocation) -> invocation.getArgument(0));
        when(switchRepository.findById(any(SwitchId.class))).thenAnswer((invocation) ->
                Optional.of(Switch.builder()
                        .switchId(invocation.getArgument(0))
                        .status(SwitchStatus.ACTIVE)
                        .build()));
        when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);

        IslRepository islRepository = mock(IslRepository.class);
        when(repositoryFactory.createIslRepository()).thenReturn(islRepository);

        SwitchFeaturesRepository switchFeaturesRepository = mock(SwitchFeaturesRepository.class);
        when(switchFeaturesRepository.findBySwitchId(any(SwitchId.class)))
                .thenReturn(Optional.of(SwitchFeatures.builder().build()));

        doAnswer(invocation -> {
            FlowPath flowPath = invocation.getArgument(0);
            when(flowPathRepository.findById(flowPath.getPathId())).thenReturn(Optional.of(flowPath));
            return flowPath;
        }).when(flowPathRepository).createOrUpdate(any(FlowPath.class));

        doAnswer(getSpeakerCommandsAnswer()).when(carrier).sendSpeakerRequest(any(SpeakerFlowRequest.class));
        when(repositoryFactory.createSwitchFeaturesRepository()).thenReturn(switchFeaturesRepository);

        when(islCostConfig.getIslCostWhenPortDown()).thenReturn(10000);
        when(islCostConfig.getIslCostWhenUnderMaintenance()).thenReturn(10000);

        target = new FlowCreateService(carrier, persistenceManager, pathComputer, flowResourcesManager, 0, 0,
                islCostConfig);
    }

    @After
    public void reset() {
        Mockito.reset(persistenceManager, flowRepository, flowPathRepository, flowResourcesManager,
                pathComputer, carrier);
    }

    @Test
    public void shouldCreateFlowWithTransitSwitches() throws Exception {
        String key = "successful_flow_create";
        String flowId = "test_successful_flow_id";

        FlowRequest request = FlowRequest.builder()
                .flowId(flowId)
                .bandwidth(1000L)
                .sourceSwitch(SRC_SWITCH)
                .sourcePort(1)
                .sourceVlan(1)
                .destinationSwitch(DST_SWITCH)
                .destinationPort(3)
                .destinationVlan(3)
                .build();
        mockFlowCreationInDb(flowId);
        FlowResources flowResources = allocateResources(flowId);
        when(flowResourcesManager.allocateFlowResources(any(Flow.class))).thenReturn(flowResources);

        when(pathComputer.getPath(any(Flow.class), any(IslConfig.class))).thenReturn(getPath3Switches());

        target.handleRequest(key, new CommandContext(), request);

        verify(flowRepository).createOrUpdate(flowCaptor.capture());
        Flow createdFlow = flowCaptor.getValue();
        assertThat(createdFlow.getStatus(), is(FlowStatus.IN_PROGRESS));
        assertThat(createdFlow.getFlowId(), is(flowId));

        // verify response to northbound is sent
        verify(carrier).sendNorthboundResponse(any(Message.class));

        SpeakerFlowRequest flowRequest;
        while ((flowRequest = requests.poll()) != null) {
            if (flowRequest instanceof GetInstalledRule) {
                target.handleAsyncResponse(key, buildResponseOnGetInstalled((GetInstalledRule) flowRequest));
            } else {
                handleResponse(key, flowRequest);
            }
        }

        verify(flowRepository).updateStatus(eq(flowId), eq(FlowStatus.UP));
        verify(flowPathRepository).updateStatus(eq(flowResources.getForward().getPathId()), eq(FlowPathStatus.ACTIVE));
        verify(flowPathRepository).updateStatus(eq(flowResources.getReverse().getPathId()), eq(FlowPathStatus.ACTIVE));
    }

    @Test
    public void shouldCreateOneSwitchFlow() throws Exception {
        String key = "successful_flow_create";
        String flowId = "one_switch_flow";

        // "save" flow in repository if it is created

        FlowRequest flowRequest = FlowRequest.builder()
                .flowId(flowId)
                .bandwidth(1000L)
                .sourceSwitch(SRC_SWITCH)
                .sourcePort(1)
                .sourceVlan(1)
                .destinationSwitch(SRC_SWITCH)
                .destinationPort(2)
                .destinationVlan(2)
                .build();
        mockFlowCreationInDb(flowId);
        FlowResources flowResources = allocateResources(flowId);
        when(flowResourcesManager.allocateFlowResources(any(Flow.class))).thenReturn(flowResources);
        when(pathComputer.getPath(any(Flow.class), any(IslConfig.class))).thenReturn(getPathOneSwitch());

        target.handleRequest(key, new CommandContext(), flowRequest);

        verify(flowRepository).createOrUpdate(flowCaptor.capture());
        Flow createdFlow = flowCaptor.getValue();
        assertThat(createdFlow.getStatus(), is(FlowStatus.IN_PROGRESS));
        assertThat(createdFlow.getFlowId(), is(flowId));

        // verify response to northbound is sent
        verify(carrier).sendNorthboundResponse(any(Message.class));

        SpeakerFlowRequest command;
        while ((command = requests.poll()) != null) {
            if (command instanceof GetInstalledRule) {
                target.handleAsyncResponse(key, buildResponseOnGetInstalled((GetInstalledRule) command));
            } else {
                handleResponse(key, command);
            }
        }

        verify(flowRepository).updateStatus(eq(flowId), eq(FlowStatus.UP));
        verify(flowPathRepository).updateStatus(eq(flowResources.getForward().getPathId()), eq(FlowPathStatus.ACTIVE));
        verify(flowPathRepository).updateStatus(eq(flowResources.getReverse().getPathId()), eq(FlowPathStatus.ACTIVE));
    }

    @Test
    public void shouldRollbackIfEgressRuleNotInstalled() throws Exception {
        target = new FlowCreateService(carrier, persistenceManager, pathComputer, flowResourcesManager, 0, 0,
                islCostConfig);
        String key = "failed_flow_create";
        String flowId = "failed_flow_id";

        FlowRequest flowRequest = FlowRequest.builder()
                .flowId(flowId)
                .bandwidth(1000L)
                .sourceSwitch(SRC_SWITCH)
                .sourcePort(1)
                .sourceVlan(1)
                .destinationSwitch(DST_SWITCH)
                .destinationPort(3)
                .destinationVlan(3)
                .build();
        mockFlowCreationInDb(flowId);
        FlowResources flowResources = allocateResources(flowId);
        when(flowResourcesManager.allocateFlowResources(any(Flow.class))).thenReturn(flowResources);
        when(pathComputer.getPath(any(Flow.class), any(IslConfig.class))).thenReturn(getPath3Switches());
        target.handleRequest(key, new CommandContext(), flowRequest);

        verify(flowRepository).createOrUpdate(flowCaptor.capture());
        // verify flow with status IN PROGRESS has been created
        Flow createdFlow = flowCaptor.getValue();
        assertThat(createdFlow.getStatus(), is(FlowStatus.IN_PROGRESS));
        assertThat(createdFlow.getFlowId(), is(flowId));

        // verify response to northbound is sent
        verify(carrier).sendNorthboundResponse(any(Message.class));

        SpeakerFlowRequest command;
        int installCommands = 0;
        int deleteCommands = 0;
        while ((command = requests.poll()) != null) {
            if (command instanceof GetInstalledRule) {
                target.handleAsyncResponse(key, buildResponseOnGetInstalled((GetInstalledRule) command));
            } else if (command instanceof InstallFlowRule) {
                installCommands++;
                if (requests.size() > 1) {
                    handleResponse(key, command);
                } else {
                    handleErrorResponse(key, command);
                }
            } else if (command instanceof RemoveRule) {
                deleteCommands++;
                handleResponse(key, command);
            }
        }

        assertEquals("All installed rules should be deleted", installCommands, deleteCommands);
        verify(flowRepository).updateStatus(eq(flowId), eq(FlowStatus.DOWN));

        FlowPath forwardPath = flowPathRepository.findById(flowResources.getForward().getPathId()).get();
        FlowPath reversePath = flowPathRepository.findById(flowResources.getReverse().getPathId()).get();
        verify(flowPathRepository).delete(eq(forwardPath));
        verify(flowPathRepository).delete(eq(reversePath));
    }

    @Test
    public void shouldRollbackIfIngressRuleNotInstalled() throws Exception {
        target = new FlowCreateService(carrier, persistenceManager, pathComputer, flowResourcesManager, 0, 0,
                islCostConfig);
        String key = "failed_flow_create";
        String flowId = "failed_flow_id";

        FlowRequest flowRequest = FlowRequest.builder()
                .flowId(flowId)
                .bandwidth(1000L)
                .sourceSwitch(SRC_SWITCH)
                .sourcePort(1)
                .sourceVlan(1)
                .destinationSwitch(DST_SWITCH)
                .destinationPort(3)
                .destinationVlan(3)
                .build();
        FlowResources flowResources = allocateResources(flowId);
        when(flowResourcesManager.allocateFlowResources(any(Flow.class))).thenReturn(flowResources);
        mockFlowCreationInDb(flowId);
        when(pathComputer.getPath(any(Flow.class), any(IslConfig.class))).thenReturn(getPath3Switches());
        target.handleRequest(key, new CommandContext(), flowRequest);

        verify(flowRepository).createOrUpdate(flowCaptor.capture());
        // verify flow with status IN PROGRESS has been created
        Flow createdFlow = flowCaptor.getValue();
        assertThat(createdFlow.getStatus(), is(FlowStatus.IN_PROGRESS));
        assertThat(createdFlow.getFlowId(), is(flowId));

        // verify response to northbound is sent
        verify(carrier).sendNorthboundResponse(any(Message.class));

        SpeakerFlowRequest command;
        int installCommands = 0;
        int deleteCommands = 0;
        while ((command = requests.poll()) != null) {
            if (command instanceof GetInstalledRule) {
                target.handleAsyncResponse(key, buildResponseOnGetInstalled((GetInstalledRule) command));
            } else if (command instanceof InstallFlowRule) {
                installCommands++;
                if (requests.size() > 1 || command instanceof InstallEgressRule) {
                    handleResponse(key, command);
                } else {
                    handleErrorResponse(key, command);
                }
            } else if (command instanceof RemoveRule) {
                deleteCommands++;
                handleResponse(key, command);
            }
        }

        assertEquals("All installed rules should be deleted", installCommands, deleteCommands);
        verify(flowRepository).updateStatus(eq(flowId), eq(FlowStatus.DOWN));
        FlowPath forwardPath = flowPathRepository.findById(flowResources.getForward().getPathId()).get();
        FlowPath reversePath = flowPathRepository.findById(flowResources.getReverse().getPathId()).get();
        verify(flowPathRepository).delete(eq(forwardPath));
        verify(flowPathRepository).delete(eq(reversePath));
    }

    @Test
    public void shouldCreateFlowWithRetryNonIngressRuleIfSwitchIsUnavailable() throws Exception {
        int retriesLimit = 10;
        target = new FlowCreateService(carrier, persistenceManager, pathComputer, flowResourcesManager,
                0, retriesLimit, islCostConfig);
        String key = "retries_non_ingress_installation";
        String flowId = "failed_flow_id";

        FlowRequest flowRequest = FlowRequest.builder()
                .flowId(flowId)
                .bandwidth(1000L)
                .sourceSwitch(SRC_SWITCH)
                .sourcePort(1)
                .sourceVlan(1)
                .destinationSwitch(DST_SWITCH)
                .destinationPort(3)
                .destinationVlan(3)
                .build();
        FlowResources flowResources = allocateResources(flowId);
        when(flowResourcesManager.allocateFlowResources(any(Flow.class))).thenReturn(flowResources);
        mockFlowCreationInDb(flowId);
        when(pathComputer.getPath(any(Flow.class), any(IslConfig.class))).thenReturn(getPath3Switches());
        target.handleRequest(key, new CommandContext(), flowRequest);

        verify(flowRepository).createOrUpdate(flowCaptor.capture());
        // verify flow with status IN PROGRESS has been created
        Flow createdFlow = flowCaptor.getValue();
        assertThat(createdFlow.getStatus(), is(FlowStatus.IN_PROGRESS));
        assertThat(createdFlow.getFlowId(), is(flowId));

        // verify response to northbound is sent
        verify(carrier).sendNorthboundResponse(any(Message.class));

        int remainingRetries = retriesLimit;
        SpeakerFlowRequest command;
        while ((command = requests.poll()) != null) {
            if (command instanceof GetInstalledRule) {
                target.handleAsyncResponse(key, buildResponseOnGetInstalled((GetInstalledRule) command));
            } else {
                if (command instanceof InstallEgressRule && remainingRetries > 0) {
                    handleErrorResponse(key, command, ErrorCode.SWITCH_UNAVAILABLE);
                    remainingRetries--;
                } else {
                    handleResponse(key, command);
                }
            }
        }

        assertEquals(0, remainingRetries);
        verify(flowRepository).updateStatus(eq(flowId), eq(FlowStatus.UP));
        verify(flowPathRepository).updateStatus(
                eq(flowResources.getForward().getPathId()), eq(FlowPathStatus.ACTIVE));
        verify(flowPathRepository).updateStatus(
                eq(flowResources.getReverse().getPathId()), eq(FlowPathStatus.ACTIVE));
    }

    @Test
    public void shouldCreateFlowWithRetryIngressRuleIfSwitchIsUnavailable() throws Exception {
        int retriesLimit = 10;
        target = new FlowCreateService(carrier, persistenceManager, pathComputer, flowResourcesManager,
                0, retriesLimit, islCostConfig);
        String key = "retries_non_ingress_installation";
        String flowId = "failed_flow_id";

        FlowRequest flowRequest = FlowRequest.builder()
                .flowId(flowId)
                .bandwidth(1000L)
                .sourceSwitch(SRC_SWITCH)
                .sourcePort(1)
                .sourceVlan(1)
                .destinationSwitch(DST_SWITCH)
                .destinationPort(3)
                .destinationVlan(3)
                .build();
        FlowResources flowResources = allocateResources(flowId);
        when(flowResourcesManager.allocateFlowResources(any(Flow.class))).thenReturn(flowResources);
        mockFlowCreationInDb(flowId);
        when(pathComputer.getPath(any(Flow.class), any(IslConfig.class))).thenReturn(getPath3Switches());
        target.handleRequest(key, new CommandContext(), flowRequest);

        verify(flowRepository).createOrUpdate(flowCaptor.capture());
        // verify flow with status IN PROGRESS has been created
        Flow createdFlow = flowCaptor.getValue();
        assertThat(createdFlow.getStatus(), is(FlowStatus.IN_PROGRESS));
        assertThat(createdFlow.getFlowId(), is(flowId));

        // verify response to northbound is sent
        verify(carrier).sendNorthboundResponse(any(Message.class));

        int remainingRetries = retriesLimit;
        SpeakerFlowRequest command;
        while ((command = requests.poll()) != null) {
            if (command instanceof GetInstalledRule) {
                target.handleAsyncResponse(key, buildResponseOnGetInstalled((GetInstalledRule) command));
            } else {
                if (command instanceof InstallIngressRule && remainingRetries > 0) {
                    handleErrorResponse(key, command, ErrorCode.SWITCH_UNAVAILABLE);
                    remainingRetries--;
                } else {
                    handleResponse(key, command);
                }
            }
        }

        assertEquals(0, remainingRetries);
        verify(flowRepository).updateStatus(eq(flowId), eq(FlowStatus.UP));
        verify(flowPathRepository).updateStatus(
                eq(flowResources.getForward().getPathId()), eq(FlowPathStatus.ACTIVE));
        verify(flowPathRepository).updateStatus(
                eq(flowResources.getReverse().getPathId()), eq(FlowPathStatus.ACTIVE));
    }

    @Test
    public void shouldCreatePinnedFlow() throws Exception {
        String key = "successful_flow_create";
        String flowId = "test_successful_flow_id";

        FlowRequest request = FlowRequest.builder()
                .flowId(flowId)
                .bandwidth(1000L)
                .sourceSwitch(SRC_SWITCH)
                .sourcePort(1)
                .sourceVlan(1)
                .destinationSwitch(DST_SWITCH)
                .destinationPort(3)
                .destinationVlan(3)
                .pinned(true)
                .build();
        FlowResources flowResources = allocateResources(flowId);
        when(flowResourcesManager.allocateFlowResources(any(Flow.class))).thenReturn(flowResources);
        when(pathComputer.getPath(any(Flow.class), any(IslConfig.class))).thenReturn(getPath3Switches());
        mockFlowCreationInDb(flowId);

        target.handleRequest(key, new CommandContext(), request);

        verify(flowRepository).createOrUpdate(flowCaptor.capture());
        Flow createdFlow = flowCaptor.getValue();
        assertThat(createdFlow.getStatus(), is(FlowStatus.IN_PROGRESS));
        assertThat(createdFlow.getFlowId(), is(flowId));
        assertTrue(createdFlow.isPinned());

        // verify response to northbound is sent
        verify(carrier).sendNorthboundResponse(any(Message.class));

        SpeakerFlowRequest flowRequest;
        while ((flowRequest = requests.poll()) != null) {
            if (flowRequest instanceof GetInstalledRule) {
                target.handleAsyncResponse(key, buildResponseOnGetInstalled((GetInstalledRule) flowRequest));
            } else {
                handleResponse(key, flowRequest);
            }
        }

        verify(flowRepository).updateStatus(eq(flowId), eq(FlowStatus.UP));
        verify(flowPathRepository).updateStatus(eq(flowResources.getForward().getPathId()), eq(FlowPathStatus.ACTIVE));
        verify(flowPathRepository).updateStatus(eq(flowResources.getReverse().getPathId()), eq(FlowPathStatus.ACTIVE));
    }

    @Test
    public void shouldCreateFlowWithProtectedPath() throws Exception {
        String key = "successful_flow_create";
        String flowId = "test_successful_flow_id";

        FlowRequest request = FlowRequest.builder()
                .flowId(flowId)
                .bandwidth(1000L)
                .sourceSwitch(SRC_SWITCH)
                .sourcePort(1)
                .sourceVlan(1)
                .destinationSwitch(DST_SWITCH)
                .destinationPort(3)
                .destinationVlan(3)
                .allocateProtectedPath(true)
                .build();
        mockFlowCreationInDb(flowId);
        FlowResources mainResources = allocateResources(flowId);
        FlowResources protectedResources = allocateResources(flowId);
        when(flowResourcesManager.allocateFlowResources(any(Flow.class)))
                .thenReturn(mainResources)
                .thenReturn(protectedResources);
        when(pathComputer.getPath(any(Flow.class), any(IslConfig.class)))
                .thenReturn(getPath2Switches())
                .thenReturn(getPath3Switches());

        String groupId = UUID.randomUUID().toString();
        when(flowRepository.getOrCreateFlowGroupId(flowId)).thenReturn(Optional.of(groupId));

        target.handleRequest(key, new CommandContext(), request);

        verify(flowRepository, times(2)).createOrUpdate(flowCaptor.capture());
        Flow createdFlow = flowCaptor.getValue();
        assertThat(createdFlow.getStatus(), is(FlowStatus.IN_PROGRESS));
        assertThat(createdFlow.getFlowId(), is(flowId));
        assertTrue(createdFlow.isAllocateProtectedPath());

        // verify response to northbound is sent
        verify(carrier).sendNorthboundResponse(any(Message.class));

        SpeakerFlowRequest flowRequest;
        while ((flowRequest = requests.poll()) != null) {
            if (flowRequest instanceof GetInstalledRule) {
                target.handleAsyncResponse(key, buildResponseOnGetInstalled((GetInstalledRule) flowRequest));
            } else {
                handleResponse(key, flowRequest);
            }
        }

        verify(flowRepository).updateStatus(eq(flowId), eq(FlowStatus.UP));
        verify(flowPathRepository).updateStatus(eq(mainResources.getForward().getPathId()), eq(FlowPathStatus.ACTIVE));
        verify(flowPathRepository).updateStatus(eq(mainResources.getReverse().getPathId()), eq(FlowPathStatus.ACTIVE));
        verify(flowPathRepository).updateStatus(eq(protectedResources.getForward().getPathId()),
                eq(FlowPathStatus.ACTIVE));
        verify(flowPathRepository).updateStatus(eq(protectedResources.getReverse().getPathId()),
                eq(FlowPathStatus.ACTIVE));
    }

    private void mockFlowCreationInDb(String flowId) {
        // emulate flow existence in DB after saving it
        doAnswer((args) -> {
            Flow flow = args.getArgument(0);

            // once flow is created in DB it will be available for loading by flow id
            when(flowRepository.findById(eq(flowId))).thenReturn(Optional.of(flow));

            // flow path should also being created
            when(flowPathRepository.findById(flow.getForwardPathId()))
                    .thenReturn(Optional.ofNullable(flow.getForwardPath()));
            when(flowPathRepository.findById(flow.getReversePathId()))
                    .thenReturn(Optional.ofNullable(flow.getReversePath()));
            if (flow.isAllocateProtectedPath()) {
                when(flowPathRepository.findById(flow.getProtectedForwardPathId()))
                        .thenReturn(Optional.ofNullable(flow.getProtectedForwardPath()));
                when(flowPathRepository.findById(flow.getProtectedReversePathId()))
                        .thenReturn(Optional.ofNullable(flow.getProtectedReversePath()));
            }
            return null;
        }).when(flowRepository).createOrUpdate(any(Flow.class));
    }

    private FeatureToggles getFeatureToggles() {
        return FeatureToggles.builder()
                .createFlowEnabled(true)
                .build();
    }

    private PathPair getPathOneSwitch() {
        return PathPair.builder()
                .forward(Path.builder()
                        .srcSwitchId(SRC_SWITCH)
                        .destSwitchId(SRC_SWITCH)
                        .segments(Collections.emptyList())
                        .build())
                .reverse(Path.builder()
                        .srcSwitchId(SRC_SWITCH)
                        .destSwitchId(SRC_SWITCH)
                        .segments(Collections.emptyList())
                        .build())
                .build();
    }

    private PathPair getPath2Switches() {
        List<Segment> forwardSegments = ImmutableList.of(
                Segment.builder()
                        .srcSwitchId(SRC_SWITCH)
                        .srcPort(2)
                        .destSwitchId(DST_SWITCH)
                        .destPort(3)
                        .build());
        List<Segment> reverseSegments = ImmutableList.of(
                Segment.builder()
                        .srcSwitchId(DST_SWITCH)
                        .srcPort(3)
                        .destSwitchId(SRC_SWITCH)
                        .destPort(2)
                        .build()
        );

        return PathPair.builder()
                .forward(Path.builder()
                        .srcSwitchId(SRC_SWITCH)
                        .destSwitchId(DST_SWITCH)
                        .segments(forwardSegments)
                        .build())
                .reverse(Path.builder()
                        .srcSwitchId(DST_SWITCH)
                        .destSwitchId(SRC_SWITCH)
                        .segments(reverseSegments)
                        .build())
                .build();
    }

    private PathPair getPath3Switches() {
        List<Segment> forwardSegments = ImmutableList.of(
                Segment.builder()
                        .srcSwitchId(SRC_SWITCH)
                        .srcPort(2)
                        .destSwitchId(TRANSIT_SWITCH)
                        .destPort(2)
                        .build(),
                Segment.builder()
                        .srcSwitchId(TRANSIT_SWITCH)
                        .srcPort(3)
                        .destSwitchId(DST_SWITCH)
                        .destPort(3)
                        .build()
        );
        List<Segment> reverseSegments = ImmutableList.of(
                Segment.builder()
                        .srcSwitchId(DST_SWITCH)
                        .srcPort(3)
                        .destSwitchId(TRANSIT_SWITCH)
                        .destPort(3)
                        .build(),
                Segment.builder()
                        .srcSwitchId(TRANSIT_SWITCH)
                        .srcPort(2)
                        .destSwitchId(SRC_SWITCH)
                        .destPort(2)
                        .build()
        );

        return PathPair.builder()
                .forward(Path.builder()
                        .srcSwitchId(SRC_SWITCH)
                        .destSwitchId(DST_SWITCH)
                        .segments(forwardSegments)
                        .build())
                .reverse(Path.builder()
                        .srcSwitchId(DST_SWITCH)
                        .destSwitchId(SRC_SWITCH)
                        .segments(reverseSegments)
                        .build())
                .build();
    }

    private FlowResources allocateResources(String flowId) {
        PathId forwardPathId = new PathId(UUID.randomUUID().toString());
        PathId reversePathId = new PathId(UUID.randomUUID().toString());

        PathResources forwardResources = PathResources.builder()
                .pathId(forwardPathId)
                .meterId(new MeterId(32))
                .encapsulationResources(TransitVlanEncapsulation.builder()
                        .transitVlan(TransitVlan.builder()
                                .flowId(flowId)
                                .pathId(forwardPathId)
                                .vlan(201)
                                .build())
                        .build())
                .build();
        PathResources reverseResources = PathResources.builder()
                .pathId(reversePathId)
                .meterId(new MeterId(33))
                .encapsulationResources(TransitVlanEncapsulation.builder()
                        .transitVlan(TransitVlan.builder()
                                .pathId(reversePathId)
                                .flowId(flowId)
                                .vlan(202)
                                .build())
                        .build())
                .build();
        FlowResources flowResources = FlowResources.builder()
                .forward(forwardResources)
                .reverse(reverseResources)
                .unmaskedCookie(COOKIE)
                .build();

        when(flowResourcesManager.getEncapsulationResources(eq(forwardResources.getPathId()),
                eq(reverseResources.getPathId()), eq(FlowEncapsulationType.TRANSIT_VLAN)))
                .thenReturn(Optional.of(TransitVlanEncapsulation.builder().transitVlan(
                        getTransitVlans(flowResources, true)).build()));
        when(flowResourcesManager.getEncapsulationResources(eq(reverseResources.getPathId()),
                eq(forwardResources.getPathId()), eq(FlowEncapsulationType.TRANSIT_VLAN)))
                .thenReturn(Optional.of(TransitVlanEncapsulation.builder().transitVlan(
                        getTransitVlans(flowResources, false)).build()));

        return flowResources;
    }

    private void handleResponse(String key, SpeakerFlowRequest request) {
        target.handleAsyncResponse(key, FlowResponse.builder()
                .flowId(request.getFlowId())
                .commandId(request.getCommandId())
                .switchId(request.getSwitchId())
                .success(true)
                .build());
    }

    private void handleErrorResponse(String key, SpeakerFlowRequest request) {
        target.handleAsyncResponse(key, FlowErrorResponse.errorBuilder()
                .flowId(request.getFlowId())
                .commandId(request.getCommandId())
                .switchId(request.getSwitchId())
                .build());
    }

    private void handleErrorResponse(String key, SpeakerFlowRequest request, ErrorCode errorCode) {
        target.handleAsyncResponse(key, FlowErrorResponse.errorBuilder()
                .flowId(request.getFlowId())
                .commandId(request.getCommandId())
                .switchId(request.getSwitchId())
                .errorCode(errorCode)
                .build());
    }

    private TransitVlan getTransitVlans(FlowResources flowResources, boolean forward) {
        TransitVlanEncapsulation encap = forward
                ? (TransitVlanEncapsulation) flowResources.getForward().getEncapsulationResources()
                : (TransitVlanEncapsulation) flowResources.getReverse().getEncapsulationResources();

        return encap.getTransitVlan();
    }

}
