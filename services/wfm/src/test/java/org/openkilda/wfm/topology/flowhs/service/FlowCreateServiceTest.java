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

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.openkilda.floodlight.flow.request.InstallEgressRule;
import org.openkilda.floodlight.flow.request.InstallIngressRule;
import org.openkilda.floodlight.flow.request.InstallTransitRule;
import org.openkilda.floodlight.flow.request.RemoveRule;
import org.openkilda.floodlight.flow.request.SpeakerFlowRequest;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse.ErrorCode;
import org.openkilda.floodlight.flow.response.FlowResponse;
import org.openkilda.floodlight.flow.response.FlowRuleResponse;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.flow.FlowRequest;
import org.openkilda.model.FeatureToggles;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
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
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionCallbackWithoutResult;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FeatureTogglesRepository;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.KildaConfigurationRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchFeaturesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResources.PathResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.share.flow.resources.transitvlan.TransitVlanEncapsulation;

import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class FlowCreateServiceTest {

    private static final SwitchId SRC_SWITCH = new SwitchId(1L);
    private static final SwitchId TRANSIT_SWITCH = new SwitchId(2L);
    private static final SwitchId DST_SWITCH = new SwitchId(3L);
    private static final long COOKIE = 101L;
    private final Map<UUID, FlowResponse> rulePerCommandId = new HashMap<>();

    private FlowCreateService target;

    @Mock
    private PersistenceManager persistenceManager;
    @Mock
    private TransactionManager transactionManager;
    @Mock
    private RepositoryFactory repositoryFactory;
    @Mock
    private FlowRepository flowRepository;
    @Mock
    private FlowPathRepository flowPathRepository;
    @Mock
    private FlowResourcesManager flowResourcesManager;
    @Mock
    private PathComputer pathComputer;
    @Mock
    private FlowCreateHubCarrier carrier;

    @Captor
    private ArgumentCaptor<SpeakerFlowRequest> speakerRequestCaptor;
    @Captor
    private ArgumentCaptor<Flow> flowCaptor;

    @Before
    public void init() {
        doAnswer(invocation -> {
            ((TransactionCallbackWithoutResult) invocation.getArgument(0)).doInTransaction();
            return null;
        }).when(transactionManager).doInTransaction(any(TransactionCallbackWithoutResult.class));

        when(persistenceManager.getTransactionManager()).thenReturn(transactionManager);
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
        when(repositoryFactory.createSwitchFeaturesRepository()).thenReturn(switchFeaturesRepository);
        target = new FlowCreateService(carrier, persistenceManager, pathComputer, flowResourcesManager, 0, 0);
    }

    @After
    public void reset() {
        Mockito.reset(persistenceManager, transactionManager, repositoryFactory, flowRepository, flowPathRepository,
                flowResourcesManager, pathComputer, carrier);
    }

    @Test
    public void shouldCreateFlowWithTransitSwitches() throws Exception {
        String key = "successful_flow_create";
        String flowId = "test_successful_flow_id";

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
        mockCreateFlowInDb(flowId);
        FlowResources flowResources = allocateResources(flowId);
        when(pathComputer.getPath(any(Flow.class))).thenReturn(getPath3Switches());

        target.handleRequest(key, new CommandContext(), flowRequest);

        verify(flowRepository).createOrUpdate(flowCaptor.capture());
        Flow createdFlow = flowCaptor.getValue();
        assertThat(createdFlow.getStatus(), is(FlowStatus.IN_PROGRESS));
        assertThat(createdFlow.getFlowId(), is(flowId));

        // verify response to northbound is sent
        verify(carrier).sendNorthboundResponse(any(Message.class));
        // verify installation of 2 transit and 2 egress rules is sent
        verify(carrier, times(4)).sendSpeakerRequest(speakerRequestCaptor.capture());
        Mockito.reset(carrier, flowRepository);
        //simulate flow existence in DB
        when(flowRepository.findById(eq(flowId))).thenReturn(Optional.of(createdFlow));

        for (SpeakerFlowRequest request : speakerRequestCaptor.getAllValues()) {
            rulePerCommandId.put(request.getCommandId(), getFlowRule(request));
            handleResponse(key, request);
        }

        // verify loading requests of 2 transit and 2 egress rules
        verify(carrier, times(4)).sendSpeakerRequest(speakerRequestCaptor.capture());
        Mockito.reset(carrier);
        for (SpeakerFlowRequest request : speakerRequestCaptor.getAllValues().subList(4, 8)) {
            target.handleAsyncResponse(key, rulePerCommandId.get(request.getCommandId()));
        }

        // verify sending install ingress rule commands
        verify(carrier, times(2)).sendSpeakerRequest(speakerRequestCaptor.capture());
        Mockito.reset(carrier);
        for (SpeakerFlowRequest request : speakerRequestCaptor.getAllValues().subList(8, 10)) {
            rulePerCommandId.put(request.getCommandId(), getFlowRule(request));
            handleResponse(key, request);
        }

        // verify loading requests of 2 ingress rules
        verify(carrier, times(2)).sendSpeakerRequest(speakerRequestCaptor.capture());
        Mockito.reset(carrier);
        for (SpeakerFlowRequest request : speakerRequestCaptor.getAllValues().subList(10, 12)) {
            target.handleAsyncResponse(key, rulePerCommandId.get(request.getCommandId()));
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
        mockCreateFlowInDb(flowId);
        FlowResources flowResources = allocateResources(flowId);
        when(pathComputer.getPath(any(Flow.class))).thenReturn(getPathOneSwitch());

        target.handleRequest(key, new CommandContext(), flowRequest);

        verify(flowRepository).createOrUpdate(flowCaptor.capture());
        Flow createdFlow = flowCaptor.getValue();
        assertThat(createdFlow.getStatus(), is(FlowStatus.IN_PROGRESS));
        assertThat(createdFlow.getFlowId(), is(flowId));

        // verify response to northbound is sent
        verify(carrier).sendNorthboundResponse(any(Message.class));

        // verify sending install ingress rule commands
        verify(carrier, times(2)).sendSpeakerRequest(speakerRequestCaptor.capture());
        Mockito.reset(carrier, flowRepository);

        //simulate flow existence in DB
        when(flowRepository.findById(eq(flowId))).thenReturn(Optional.of(createdFlow));

        for (SpeakerFlowRequest request : speakerRequestCaptor.getAllValues().subList(0, 2)) {
            rulePerCommandId.put(request.getCommandId(), getFlowRule(request));
            handleResponse(key, request);
        }

        // verify loading requests of 2 ingress rules
        verify(carrier, times(2)).sendSpeakerRequest(speakerRequestCaptor.capture());
        Mockito.reset(carrier);
        for (SpeakerFlowRequest request : speakerRequestCaptor.getAllValues().subList(2, 4)) {
            target.handleAsyncResponse(key, rulePerCommandId.get(request.getCommandId()));
        }

        verify(flowRepository).updateStatus(eq(flowId), eq(FlowStatus.UP));
        verify(flowPathRepository).updateStatus(eq(flowResources.getForward().getPathId()), eq(FlowPathStatus.ACTIVE));
        verify(flowPathRepository).updateStatus(eq(flowResources.getReverse().getPathId()), eq(FlowPathStatus.ACTIVE));
    }

    @Test
    public void shouldRollbackIfOneRuleNotInstalled() throws Exception {
        target = new FlowCreateService(carrier, persistenceManager, pathComputer, flowResourcesManager, 0, 0);
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
        mockCreateFlowInDb(flowId);
        when(pathComputer.getPath(any(Flow.class))).thenReturn(getPath3Switches());
        target.handleRequest(key, new CommandContext(), flowRequest);

        verify(flowRepository).createOrUpdate(flowCaptor.capture());
        // verify flow with status IN PROGRESS has been created
        Flow createdFlow = flowCaptor.getValue();
        assertThat(createdFlow.getStatus(), is(FlowStatus.IN_PROGRESS));
        assertThat(createdFlow.getFlowId(), is(flowId));

        // verify response to northbound is sent
        verify(carrier).sendNorthboundResponse(any(Message.class));
        // verify installation of 2 transit and 2 egress rules is sent
        verify(carrier, times(4)).sendSpeakerRequest(speakerRequestCaptor.capture());
        Mockito.reset(carrier, flowRepository);

        //simulate flow existence in DB
        when(flowRepository.findById(eq(flowId))).thenReturn(Optional.of(createdFlow));

        for (int index = 0; index < speakerRequestCaptor.getAllValues().size(); index++) {
            SpeakerFlowRequest request = speakerRequestCaptor.getAllValues().get(index);

            if (index == speakerRequestCaptor.getAllValues().size() - 1) {
                handleErrorResponse(key, request);
            } else {
                rulePerCommandId.put(request.getCommandId(), getFlowRule(request));
                handleResponse(key, request);
            }
        }
        // verify deletion rules commands were sent
        verify(carrier, times(4)).sendSpeakerRequest(speakerRequestCaptor.capture());
        for (SpeakerFlowRequest request : speakerRequestCaptor.getAllValues().subList(4, 8)) {
            assertThat(request, instanceOf(RemoveRule.class));
            handleResponse(key, request);
        }

        verify(flowRepository).updateStatus(eq(flowId), eq(FlowStatus.DOWN));
        verify(flowPathRepository).updateStatus(
                eq(flowResources.getForward().getPathId()), eq(FlowPathStatus.INACTIVE));
        verify(flowPathRepository).updateStatus(
                eq(flowResources.getReverse().getPathId()), eq(FlowPathStatus.INACTIVE));
    }

    @Test
    @Ignore
    public void shouldCreateFlowWithRetryNonIngressFlowInstallation() throws Exception {
        target = new FlowCreateService(carrier, persistenceManager, pathComputer, flowResourcesManager, 0, 1);
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
        mockCreateFlowInDb(flowId);
        when(pathComputer.getPath(any(Flow.class))).thenReturn(getPath3Switches());
        target.handleRequest(key, new CommandContext(), flowRequest);

        verify(flowRepository).createOrUpdate(flowCaptor.capture());
        // verify flow with status IN PROGRESS has been created
        Flow createdFlow = flowCaptor.getValue();
        assertThat(createdFlow.getStatus(), is(FlowStatus.IN_PROGRESS));
        assertThat(createdFlow.getFlowId(), is(flowId));

        // verify response to northbound is sent
        verify(carrier).sendNorthboundResponse(any(Message.class));
        // verify installation of 2 transit and 2 egress rules is sent
        verify(carrier, times(4)).sendSpeakerRequest(speakerRequestCaptor.capture());
        Mockito.reset(carrier, flowRepository);

        //simulate flow existence in DB
        when(flowRepository.findById(eq(flowId))).thenReturn(Optional.of(createdFlow));

        for (int index = 0; index < speakerRequestCaptor.getAllValues().size(); index++) {
            SpeakerFlowRequest request = speakerRequestCaptor.getAllValues().get(index);

            if (index == speakerRequestCaptor.getAllValues().size() - 1) {
                FlowResponse response = FlowErrorResponse.errorBuilder()
                        .flowId(request.getFlowId())
                        .commandId(request.getCommandId())
                        .switchId(request.getSwitchId())
                        .errorCode(ErrorCode.SWITCH_UNAVAILABLE)
                        .build();
                target.handleAsyncResponse(key, response);
            } else {
                rulePerCommandId.put(request.getCommandId(), getFlowRule(request));
                handleResponse(key, request);
            }
        }

        // verify command was re-sent
        verify(carrier, times(1)).sendSpeakerRequest(speakerRequestCaptor.capture());
        Mockito.reset(carrier);
        SpeakerFlowRequest retriedCommand = speakerRequestCaptor.getAllValues().get(4);
        handleResponse(key, retriedCommand);

        // verify loading requests of 2 transit and 2 egress rules
        verify(carrier, times(4)).sendSpeakerRequest(speakerRequestCaptor.capture());
        Mockito.reset(carrier);
        for (SpeakerFlowRequest request : speakerRequestCaptor.getAllValues().subList(5, 9)) {
            target.handleAsyncResponse(key, rulePerCommandId.get(request.getCommandId()));
        }

        // verify sending install ingress rule commands
        verify(carrier, times(2)).sendSpeakerRequest(speakerRequestCaptor.capture());
        Mockito.reset(carrier);
        for (SpeakerFlowRequest request : speakerRequestCaptor.getAllValues().subList(9, 11)) {
            rulePerCommandId.put(request.getCommandId(), getFlowRule(request));
            handleResponse(key, request);
        }

        // verify loading requests of 2 ingress rules
        verify(carrier, times(2)).sendSpeakerRequest(speakerRequestCaptor.capture());
        Mockito.reset(carrier);
        for (SpeakerFlowRequest request : speakerRequestCaptor.getAllValues().subList(11, 13)) {
            rulePerCommandId.put(request.getCommandId(), getFlowRule(request));
            handleResponse(key, request);
        }

        verify(flowRepository).updateStatus(eq(flowId), eq(FlowStatus.DOWN));
        verify(flowPathRepository).updateStatus(
                eq(flowResources.getForward().getPathId()), eq(FlowPathStatus.INACTIVE));
        verify(flowPathRepository).updateStatus(
                eq(flowResources.getReverse().getPathId()), eq(FlowPathStatus.INACTIVE));
    }

    @Test
    public void shouldCreateFlowWithRetryIngressFlowInstallation() {

    }

    @Test
    public void shouldCreatePinnedFlow() {

    }

    @Test
    public void shouldCreateFlowWithProtectedPath() {

    }

    @Test
    public void shouldCreateFlowWithDiversePath() {

    }

    private void mockCreateFlowInDb(String flowId) {
        // emulate flow existence in DB after saving it
        doAnswer((args) -> {
            Flow flow = args.getArgument(0);

            // once flow is created in DB it will be available for loading by flow id
            when(flowRepository.findById(eq(flowId))).thenReturn(Optional.of(flow));
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

    private FlowResources allocateResources(String flowId) throws ResourceAllocationException {
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

        when(flowResourcesManager.allocateFlowResources(any(Flow.class))).thenReturn(flowResources);
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

    private TransitVlan getTransitVlans(FlowResources flowResources, boolean forward) {
        TransitVlanEncapsulation encap = forward
                ? (TransitVlanEncapsulation) flowResources.getForward().getEncapsulationResources()
                : (TransitVlanEncapsulation) flowResources.getReverse().getEncapsulationResources();

        return encap.getTransitVlan();
    }

    private FlowResponse getFlowRule(SpeakerFlowRequest request) {
        FlowResponse response;
        if (request instanceof InstallEgressRule) {
            InstallEgressRule command = (InstallEgressRule) request;
            response = FlowRuleResponse.flowRuleResponseBuilder()
                    .switchId(command.getSwitchId())
                    .commandId(request.getCommandId())
                    .cookie(command.getCookie())
                    .inVlan(command.getTransitEncapsulationId())
                    .outVlan(command.getOutputVlanId())
                    .inPort(command.getInputPort())
                    .outPort(command.getOutputPort())
                    .build();
        } else if (request instanceof InstallTransitRule) {
            InstallTransitRule command = (InstallTransitRule) request;
            response = FlowRuleResponse.flowRuleResponseBuilder()
                    .switchId(command.getSwitchId())
                    .commandId(request.getCommandId())
                    .cookie(command.getCookie())
                    .inVlan(command.getTransitEncapsulationId())
                    .outVlan(command.getTransitEncapsulationId())
                    .inPort(command.getInputPort())
                    .outPort(command.getOutputPort())
                    .build();
        } else if (request instanceof InstallIngressRule) {
            InstallIngressRule command = (InstallIngressRule) request;
            response = FlowRuleResponse.flowRuleResponseBuilder()
                    .switchId(command.getSwitchId())
                    .commandId(request.getCommandId())
                    .cookie(command.getCookie())
                    .inVlan(command.getInputVlanId())
                    .meterId(command.getMeterId())
                    .inPort(command.getInputPort())
                    .outPort(command.getOutputPort())
                    .build();
        } else {
            throw new IllegalStateException(String.format("Unexpected flow request was sent: %s", request));
        }

        return response;
    }
}
