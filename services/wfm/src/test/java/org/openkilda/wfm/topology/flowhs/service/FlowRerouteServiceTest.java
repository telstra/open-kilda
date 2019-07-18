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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

import org.openkilda.floodlight.flow.request.GetInstalledRule;
import org.openkilda.floodlight.flow.request.InstallEgressRule;
import org.openkilda.floodlight.flow.request.InstallFlowRule;
import org.openkilda.floodlight.flow.request.InstallIngressRule;
import org.openkilda.floodlight.flow.request.InstallTransitRule;
import org.openkilda.floodlight.flow.request.RemoveRule;
import org.openkilda.floodlight.flow.request.SpeakerFlowRequest;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse.ErrorCode;
import org.openkilda.floodlight.flow.response.FlowResponse;
import org.openkilda.floodlight.flow.response.FlowRuleResponse;
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
import org.openkilda.model.SwitchFeatures;
import org.openkilda.model.SwitchId;
import org.openkilda.model.TransitVlan;
import org.openkilda.pce.Path;
import org.openkilda.pce.Path.Segment;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathPair;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionCallback;
import org.openkilda.persistence.TransactionCallbackWithoutResult;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FeatureTogglesRepository;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchFeaturesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.history.FlowEventRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResources.PathResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.share.flow.resources.transitvlan.TransitVlanEncapsulation;

import lombok.SneakyThrows;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;

@RunWith(MockitoJUnitRunner.class)
public class FlowRerouteServiceTest {
    private static final String FLOW_ID = "TEST_FLOW";
    private static final SwitchId SWITCH_1 = new SwitchId(1);
    private static final SwitchId SWITCH_2 = new SwitchId(2);
    private static final PathId OLD_FORWARD_FLOW_PATH = new PathId(FLOW_ID + "_forward_old");
    private static final PathId OLD_REVERSE_FLOW_PATH = new PathId(FLOW_ID + "_reverse_old");
    private static final PathId NEW_FORWARD_FLOW_PATH = new PathId(FLOW_ID + "_forward_new");
    private static final PathId NEW_REVERSE_FLOW_PATH = new PathId(FLOW_ID + "_reverse_new");

    @Mock
    private PersistenceManager persistenceManager;
    @Mock
    private FlowRepository flowRepository;
    @Mock
    private FlowPathRepository flowPathRepository;
    @Mock
    private PathComputer pathComputer;
    @Mock
    private FlowResourcesManager flowResourcesManager;
    @Mock
    private FlowRerouteHubCarrier carrier;
    @Mock
    private CommandContext commandContext;

    private Queue<SpeakerFlowRequest> requests = new ArrayDeque<>();
    private Map<SwitchId, Map<Cookie, InstallFlowRule>> installedRules = new HashMap<>();

    @Before
    public void setUp() {
        RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
        when(repositoryFactory.createFlowRepository()).thenReturn(flowRepository);

        when(flowPathRepository.getUsedBandwidthBetweenEndpoints(any(), anyInt(), any(), anyInt())).thenReturn(0L);

        when(repositoryFactory.createFlowPathRepository()).thenReturn(flowPathRepository);

        IslRepository islRepository = mock(IslRepository.class);
        when(repositoryFactory.createIslRepository()).thenReturn(islRepository);

        SwitchRepository switchRepository = mock(SwitchRepository.class);
        when(switchRepository.reload(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);

        FeatureTogglesRepository featureTogglesRepository = mock(FeatureTogglesRepository.class);
        when(repositoryFactory.createFeatureTogglesRepository()).thenReturn(featureTogglesRepository);

        SwitchFeaturesRepository featuresRepository = mock(SwitchFeaturesRepository.class);
        when(featuresRepository.findBySwitchId(any(SwitchId.class)))
                .thenReturn(Optional.of(SwitchFeatures.builder().build()));
        when(repositoryFactory.createSwitchFeaturesRepository()).thenReturn(featuresRepository);

        FlowEventRepository flowEventRepository = mock(FlowEventRepository.class);
        when(flowEventRepository.existsByTaskId(any())).thenReturn(false);
        when(repositoryFactory.createFlowEventRepository()).thenReturn(flowEventRepository);

        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);
        when(persistenceManager.getTransactionManager()).thenReturn(new TransactionManager() {
            @SneakyThrows
            @Override
            public <T, E extends Throwable> T doInTransaction(TransactionCallback<T, E> action) throws E {
                return action.doInTransaction();
            }

            @Override
            public <T, E extends Throwable> T doInTransaction(RetryPolicy retryPolicy, TransactionCallback<T, E> action)
                    throws E {
                return Failsafe.with(retryPolicy).get(action::doInTransaction);
            }

            @SneakyThrows
            @Override
            public <E extends Throwable> void doInTransaction(TransactionCallbackWithoutResult<E> action) throws E {
                action.doInTransaction();
            }

            @Override
            public <E extends Throwable> void doInTransaction(RetryPolicy retryPolicy,
                                                              TransactionCallbackWithoutResult<E> action) throws E {
                Failsafe.with(retryPolicy).run(action::doInTransaction);
            }
        });

        doAnswer(invocation -> {
            SpeakerFlowRequest request = invocation.getArgument(0);
            requests.offer(request);

            if (request instanceof InstallFlowRule) {
                Map<Cookie, InstallFlowRule> switchRules =
                        installedRules.getOrDefault(request.getSwitchId(), new HashMap<>());
                switchRules.put(((InstallFlowRule) request).getCookie(), ((InstallFlowRule) request));
                installedRules.put(request.getSwitchId(), switchRules);
            }
            return request;
        }).when(carrier).sendSpeakerRequest(any());
    }

    @Test
    public void shouldFailRerouteFlowIfNoPathAvailable()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenThrow(new UnroutableFlowException("No path found"));
        buildFlowResources();

        FlowRerouteService rerouteService = new FlowRerouteService(carrier, persistenceManager,
                pathComputer, flowResourcesManager);

        rerouteService.handleRequest("test_key", commandContext, FLOW_ID, null);

        assertEquals(FlowStatus.DOWN, flow.getStatus());
        assertEquals(OLD_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(OLD_REVERSE_FLOW_PATH, flow.getReversePathId());
        verify(pathComputer, times(1)).getPath(any(), any());
        verify(flowResourcesManager, never()).allocateFlowResources(any());
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

        FlowRerouteService rerouteService = new FlowRerouteService(carrier, persistenceManager,
                pathComputer, flowResourcesManager);

        rerouteService.handleRequest("test_key", commandContext, FLOW_ID, null);

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(OLD_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(OLD_REVERSE_FLOW_PATH, flow.getReversePathId());
        verify(pathComputer, times(4)).getPath(any(), any());
        verify(flowResourcesManager, times(4)).allocateFlowResources(any());
        verify(carrier, never()).sendSpeakerRequest(any());
        verify(carrier, times(1)).sendNorthboundResponse(any());
    }

    @Test
    public void shouldSkipRerouteIfNoNewPathFound()
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        Flow flow = build2SwitchFlow();
        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair());
        buildFlowResources();

        FlowRerouteService rerouteService = new FlowRerouteService(carrier, persistenceManager,
                pathComputer, flowResourcesManager);

        rerouteService.handleRequest("test_key", commandContext, FLOW_ID, null);

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

        FlowRerouteService rerouteService = new FlowRerouteService(carrier, persistenceManager,
                pathComputer, flowResourcesManager);

        rerouteService.handleRequest("test_key", commandContext, FLOW_ID, null);

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        SpeakerFlowRequest flowRequest;
        while ((flowRequest = requests.poll()) != null) {
            if (flowRequest instanceof InstallFlowRule) {
                rerouteService.handleAsyncResponse("test_key", FlowErrorResponse.errorBuilder()
                        .errorCode(ErrorCode.UNKNOWN)
                        .description("Switch is unavailable")
                        .commandId(flowRequest.getCommandId())
                        .flowId(flowRequest.getFlowId())
                        .switchId(flowRequest.getSwitchId())
                        .build());
            } else {
                rerouteService.handleAsyncResponse("test_key", FlowResponse.builder()
                        .commandId(flowRequest.getCommandId())
                        .flowId(flowRequest.getFlowId())
                        .switchId(flowRequest.getSwitchId())
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

        FlowRerouteService rerouteService = new FlowRerouteService(carrier, persistenceManager,
                pathComputer, flowResourcesManager);

        rerouteService.handleRequest("test_key", commandContext, FLOW_ID, null);

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        rerouteService.handleTimeout("test_key");

        SpeakerFlowRequest flowRequest;
        while ((flowRequest = requests.poll()) != null) {
            if (flowRequest instanceof RemoveRule) {
                rerouteService.handleAsyncResponse("test_key", FlowResponse.builder()
                        .commandId(flowRequest.getCommandId())
                        .flowId(flowRequest.getFlowId())
                        .switchId(flowRequest.getSwitchId())
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

        FlowRerouteService rerouteService = new FlowRerouteService(carrier, persistenceManager,
                pathComputer, flowResourcesManager);

        rerouteService.handleRequest("test_key", commandContext, FLOW_ID, null);

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        SpeakerFlowRequest flowRequest;
        while ((flowRequest = requests.poll()) != null) {
            if (flowRequest instanceof GetInstalledRule) {
                rerouteService.handleAsyncResponse("test_key", FlowErrorResponse.errorBuilder()
                        .errorCode(ErrorCode.UNKNOWN)
                        .description("Unknown rule")
                        .messageContext(flowRequest.getMessageContext())
                        .commandId(flowRequest.getCommandId())
                        .flowId(flowRequest.getFlowId())
                        .switchId(flowRequest.getSwitchId())
                        .build());
            } else {
                rerouteService.handleAsyncResponse("test_key", FlowResponse.builder()
                        .commandId(flowRequest.getCommandId())
                        .flowId(flowRequest.getFlowId())
                        .switchId(flowRequest.getSwitchId())
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

        FlowRerouteService rerouteService = new FlowRerouteService(carrier, persistenceManager,
                pathComputer, flowResourcesManager);

        rerouteService.handleRequest("test_key", commandContext, FLOW_ID, null);

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        SpeakerFlowRequest flowRequest;
        while ((flowRequest = requests.poll()) != null) {
            if (flowRequest instanceof GetInstalledRule) {
                rerouteService.handleTimeout("test_key");
            } else {
                rerouteService.handleAsyncResponse("test_key", FlowResponse.builder()
                        .commandId(flowRequest.getCommandId())
                        .flowId(flowRequest.getFlowId())
                        .switchId(flowRequest.getSwitchId())
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

        FlowRerouteService rerouteService = new FlowRerouteService(carrier, persistenceManager,
                pathComputer, flowResourcesManager);

        rerouteService.handleRequest("test_key", commandContext, FLOW_ID, null);

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

        SpeakerFlowRequest flowRequest;
        while ((flowRequest = requests.poll()) != null) {
            if (flowRequest instanceof GetInstalledRule) {
                rerouteService.handleAsyncResponse("test_key",
                        buildResponseOnGetInstalled((GetInstalledRule) flowRequest));
            } else {
                rerouteService.handleAsyncResponse("test_key", FlowResponse.builder()
                        .commandId(flowRequest.getCommandId())
                        .flowId(flowRequest.getFlowId())
                        .switchId(flowRequest.getSwitchId())
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

        FlowRerouteService rerouteService = new FlowRerouteService(carrier, persistenceManager,
                pathComputer, flowResourcesManager);

        rerouteService.handleRequest("test_key", commandContext, FLOW_ID, null);

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        doAnswer(invocation -> {
            // imitate transaction rollback
            FlowPath persistedFlowPath = invocation.getArgument(0);
            persistedFlowPath.setStatus(FlowPathStatus.IN_PROGRESS);

            throw new RuntimeException("A persistence error");
        }).when(flowPathRepository).updateStatus(eq(NEW_FORWARD_FLOW_PATH), eq(FlowPathStatus.ACTIVE));

        SpeakerFlowRequest flowRequest;
        while ((flowRequest = requests.poll()) != null) {
            if (flowRequest instanceof GetInstalledRule) {
                rerouteService.handleAsyncResponse("test_key",
                        buildResponseOnGetInstalled((GetInstalledRule) flowRequest));
            } else {
                rerouteService.handleAsyncResponse("test_key", FlowResponse.builder()
                        .commandId(flowRequest.getCommandId())
                        .flowId(flowRequest.getFlowId())
                        .switchId(flowRequest.getSwitchId())
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

        FlowRerouteService rerouteService = new FlowRerouteService(carrier, persistenceManager,
                pathComputer, flowResourcesManager);

        rerouteService.handleRequest("test_key", commandContext, FLOW_ID, null);

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        doThrow(new RuntimeException("A persistence error"))
                .when(flowPathRepository).delete(argThat(
                hasProperty("pathId", equalTo(OLD_FORWARD_FLOW_PATH))));

        SpeakerFlowRequest flowRequest;
        while ((flowRequest = requests.poll()) != null) {
            if (flowRequest instanceof GetInstalledRule) {
                rerouteService.handleAsyncResponse("test_key",
                        buildResponseOnGetInstalled((GetInstalledRule) flowRequest));
            } else {
                rerouteService.handleAsyncResponse("test_key", FlowResponse.builder()
                        .commandId(flowRequest.getCommandId())
                        .flowId(flowRequest.getFlowId())
                        .switchId(flowRequest.getSwitchId())
                        .success(true)
                        .build());
            }
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

        FlowRerouteService rerouteService = new FlowRerouteService(carrier, persistenceManager,
                pathComputer, flowResourcesManager);

        rerouteService.handleRequest("test_key", commandContext, FLOW_ID, null);

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        doThrow(new RuntimeException("A persistence error"))
                .when(flowResourcesManager).deallocatePathResources(argThat(
                hasProperty("forward",
                        hasProperty("pathId", equalTo(OLD_FORWARD_FLOW_PATH)))));

        SpeakerFlowRequest flowRequest;
        while ((flowRequest = requests.poll()) != null) {
            if (flowRequest instanceof GetInstalledRule) {
                rerouteService.handleAsyncResponse("test_key",
                        buildResponseOnGetInstalled((GetInstalledRule) flowRequest));
            } else {
                rerouteService.handleAsyncResponse("test_key", FlowResponse.builder()
                        .commandId(flowRequest.getCommandId())
                        .flowId(flowRequest.getFlowId())
                        .switchId(flowRequest.getSwitchId())
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

        when(pathComputer.getPath(any(), any())).thenReturn(build2SwitchPathPair(2, 3));
        buildFlowResources();

        FlowRerouteService rerouteService = new FlowRerouteService(carrier, persistenceManager,
                pathComputer, flowResourcesManager);

        rerouteService.handleRequest("test_key", commandContext, FLOW_ID, null);

        assertEquals(FlowStatus.IN_PROGRESS, flow.getStatus());
        verify(carrier, times(1)).sendNorthboundResponse(any());

        SpeakerFlowRequest flowRequest;
        while ((flowRequest = requests.poll()) != null) {
            if (flowRequest instanceof GetInstalledRule) {
                rerouteService.handleAsyncResponse("test_key",
                        buildResponseOnGetInstalled((GetInstalledRule) flowRequest));
            } else {
                rerouteService.handleAsyncResponse("test_key", FlowResponse.builder()
                        .commandId(flowRequest.getCommandId())
                        .flowId(flowRequest.getFlowId())
                        .switchId(flowRequest.getSwitchId())
                        .success(true)
                        .build());
            }
        }

        assertEquals(FlowStatus.UP, flow.getStatus());
        assertEquals(NEW_FORWARD_FLOW_PATH, flow.getForwardPathId());
        assertEquals(NEW_REVERSE_FLOW_PATH, flow.getReversePathId());
    }

    private PathPair build2SwitchPathPair() {
        return build2SwitchPathPair(1, 2);
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

    private Flow build2SwitchFlow() {
        Switch src = Switch.builder().switchId(SWITCH_1).build();
        Switch dst = Switch.builder().switchId(SWITCH_2).build();

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

    private FlowResponse buildResponseOnGetInstalled(GetInstalledRule request) {
        Cookie cookie = request.getCookie();

        InstallFlowRule rule = Optional.ofNullable(installedRules.get(request.getSwitchId()))
                .map(switchRules -> switchRules.get(cookie))
                .orElse(null);

        FlowRuleResponse.FlowRuleResponseBuilder builder = FlowRuleResponse.flowRuleResponseBuilder()
                .commandId(request.getCommandId())
                .flowId(request.getFlowId())
                .switchId(request.getSwitchId())
                .cookie(rule.getCookie())
                .inPort(rule.getInputPort())
                .outPort(rule.getOutputPort());
        if (rule instanceof InstallEgressRule) {
            builder.inVlan(((InstallEgressRule) rule).getTransitEncapsulationId());
            builder.outVlan(((InstallEgressRule) rule).getOutputVlanId());
        } else if (rule instanceof InstallTransitRule) {
            builder.inVlan(((InstallTransitRule) rule).getTransitEncapsulationId());
            builder.outVlan(((InstallTransitRule) rule).getTransitEncapsulationId());
        } else if (rule instanceof InstallIngressRule) {
            InstallIngressRule ingressRule = (InstallIngressRule) rule;
            builder.inVlan(ingressRule.getInputVlanId())
                    .meterId(ingressRule.getMeterId());
        }

        return builder.build();
    }
}
