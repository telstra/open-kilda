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

import static java.util.Collections.singleton;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.FlowCommand;
import org.openkilda.floodlight.api.request.rulemanager.GroupCommand;
import org.openkilda.floodlight.api.request.rulemanager.MeterCommand;
import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.flow.FlowRequest;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.IslEndpoint;
import org.openkilda.model.KildaFeatureToggles;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
import org.openkilda.model.YFlow.SharedEndpoint;
import org.openkilda.model.YSubFlow;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.pce.GetPathsResult;
import org.openkilda.pce.Path;
import org.openkilda.pce.Path.Segment;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.dummy.IslDirectionalReference;
import org.openkilda.persistence.dummy.PersistenceDummyEntityFactory;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;

import com.google.common.collect.ImmutableList;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.hamcrest.MockitoHamcrest;
import org.mockito.stubbing.Answer;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Collectors;

public abstract class AbstractFlowTest<T> extends InMemoryGraphBasedTest {
    protected static final SwitchId SWITCH_SOURCE = new SwitchId(1);
    protected static final SwitchId SWITCH_DEST = new SwitchId(2);
    protected static final SwitchId SWITCH_TRANSIT = new SwitchId(3L);

    protected final IslDirectionalReference islSourceDest = new IslDirectionalReference(
            new IslEndpoint(SWITCH_SOURCE, 24),
            new IslEndpoint(SWITCH_DEST, 24));
    protected final IslDirectionalReference islSourceDestAlt = new IslDirectionalReference(
            new IslEndpoint(SWITCH_SOURCE, 30),
            new IslEndpoint(SWITCH_DEST, 30));
    protected final IslDirectionalReference islSourceTransit = new IslDirectionalReference(
            new IslEndpoint(SWITCH_SOURCE, 25),
            new IslEndpoint(SWITCH_TRANSIT, 25));
    protected final IslDirectionalReference islTransitDest = new IslDirectionalReference(
            new IslEndpoint(SWITCH_TRANSIT, 26),
            new IslEndpoint(SWITCH_DEST, 26));

    protected final FlowEndpoint flowSource = new FlowEndpoint(SWITCH_SOURCE, 1, 101);
    protected final FlowEndpoint flowDestination = new FlowEndpoint(SWITCH_DEST, 2, 102);
    protected final FlowEndpoint singleSwitchFlowEndpoint = new FlowEndpoint(SWITCH_SOURCE, 3, 103);

    protected static PersistenceDummyEntityFactory dummyFactory;

    private FlowRepository flowRepositorySpy = null;
    private FlowPathRepository flowPathRepositorySpy = null;
    private IslRepository islRepositorySpy = null;

    protected FlowResourcesManager flowResourcesManager = null;

    protected final String dummyRequestKey = "test-key";
    protected final String injectedErrorMessage = "Unit-test injected failure";

    protected CommandContext commandContext = new CommandContext();

    @Mock
    SwitchPropertiesRepository switchPropertiesRepository;
    @Mock
    PathComputer pathComputer;

    protected final Queue<T> requests = new ArrayDeque<>();
    final Map<SwitchId, Map<Cookie, FlowSegmentRequest>> installedSegments = new HashMap<>();

    @BeforeEach
    public void before() {
        dummyFactory = new PersistenceDummyEntityFactory(persistenceManager);

        FlowResourcesConfig resourceConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        flowResourcesManager = spy(new FlowResourcesManager(persistenceManager, resourceConfig));

        alterFeatureToggles(true, true, true);

        dummyFactory.makeSwitch(SWITCH_SOURCE);
        dummyFactory.makeSwitch(SWITCH_DEST);
        dummyFactory.makeSwitch(SWITCH_TRANSIT);
        for (IslDirectionalReference reference : new IslDirectionalReference[]{
                islSourceDest, islSourceDestAlt, islSourceTransit, islTransitDest}) {
            dummyFactory.makeIsl(reference.getSourceEndpoint(), reference.getDestEndpoint());
            dummyFactory.makeIsl(reference.getDestEndpoint(), reference.getSourceEndpoint());
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (flowRepositorySpy != null) {
            reset(flowRepositorySpy);
        }
        if (flowPathRepositorySpy != null) {
            reset(flowPathRepositorySpy);
        }
        if (islRepositorySpy != null) {
            reset(islRepositorySpy);
        }
    }

    protected Answer<T> buildSpeakerRequestAnswer() {
        return invocation -> {
            T request = invocation.getArgument(0);
            requests.offer(request);

            if (request instanceof FlowSegmentRequest) {
                FlowSegmentRequest flowSegmentRequest = (FlowSegmentRequest) request;
                if (flowSegmentRequest.isInstallRequest()) {
                    installedSegments.computeIfAbsent(flowSegmentRequest.getSwitchId(), ignore -> new HashMap<>())
                            .put(flowSegmentRequest.getCookie(), flowSegmentRequest);
                }
            }

            return request;
        };
    }

    protected SpeakerFlowSegmentResponse buildSpeakerResponse(FlowSegmentRequest flowRequest) {
        return SpeakerFlowSegmentResponse.builder()
                .messageContext(flowRequest.getMessageContext())
                .commandId(flowRequest.getCommandId())
                .metadata(flowRequest.getMetadata())
                .switchId(flowRequest.getSwitchId())
                .success(true)
                .build();
    }

    protected SpeakerCommandResponse buildSuccessfulYFlowSpeakerResponse(BaseSpeakerCommandsRequest request) {
        return SpeakerCommandResponse.builder()
                .messageContext(request.getMessageContext())
                .commandId(request.getCommandId())
                .switchId(request.getSwitchId())
                .success(true)
                .failedCommandIds(new HashMap<>())
                .build();
    }

    protected SpeakerCommandResponse buildErrorYFlowSpeakerResponse(BaseSpeakerCommandsRequest request) {
        return SpeakerCommandResponse.builder()
                .messageContext(request.getMessageContext())
                .commandId(request.getCommandId())
                .switchId(request.getSwitchId())
                .success(false)
                .failedCommandIds(request.getCommands().stream().map(command -> {
                    if (command instanceof FlowCommand) {
                        return ((FlowCommand) command).getData();
                    }
                    if (command instanceof MeterCommand) {
                        return ((MeterCommand) command).getData();
                    }
                    return ((GroupCommand) command).getData();
                }).collect(Collectors.toMap(SpeakerData::getUuid, error -> "Switch is unavailable")))
                .build();
    }

    SpeakerFlowSegmentResponse buildResponseOnVerifyRequest(FlowSegmentRequest request) {
        return SpeakerFlowSegmentResponse.builder()
                .commandId(request.getCommandId())
                .metadata(request.getMetadata())
                .messageContext(request.getMessageContext())
                .switchId(request.getSwitchId())
                .success(true)
                .build();
    }

    protected Flow fetchFlow(String flowId) {
        FlowRepository repository = persistenceManager.getRepositoryFactory().createFlowRepository();
        return repository.findById(flowId)
                .orElseThrow(() -> new AssertionError(String.format(
                        "Flow %s not found in persistent storage", flowId)));
    }

    protected FlowRepository setupFlowRepositorySpy() {
        if (flowRepositorySpy == null) {
            flowRepositorySpy = spy(persistenceManager.getRepositoryFactory().createFlowRepository());
            when(repositoryFactory.createFlowRepository()).thenReturn(flowRepositorySpy);
        }
        return flowRepositorySpy;
    }

    protected FlowPathRepository setupFlowPathRepositorySpy() {
        if (flowPathRepositorySpy == null) {
            flowPathRepositorySpy = spy(persistenceManager.getRepositoryFactory().createFlowPathRepository());
            when(repositoryFactory.createFlowPathRepository()).thenReturn(flowPathRepositorySpy);
        }
        return flowPathRepositorySpy;
    }

    protected IslRepository setupIslRepositorySpy() {
        if (islRepositorySpy == null) {
            islRepositorySpy = spy(persistenceManager.getRepositoryFactory().createIslRepository());
            when(repositoryFactory.createIslRepository()).thenReturn(islRepositorySpy);
        }
        return islRepositorySpy;
    }

    protected Flow verifyFlowStatus(String flowId, FlowStatus expectedStatus) {
        Flow flow = fetchFlow(flowId);
        Assertions.assertEquals(expectedStatus, flow.getStatus());
        return flow;
    }

    protected void verifyFlowPathStatus(FlowPath path, FlowPathStatus expectedStatus, String name) {
        Assertions.assertNotNull(path, String.format("%s flow path not defined (is null)", name));
        Assertions.assertSame(expectedStatus, path.getStatus(), String.format("%s flow path status is invalid", name));
    }

    protected void verifyNorthboundSuccessResponse(FlowGenericCarrier carrierMock) {
        verifyNorthboundSuccessResponse(carrierMock, FlowResponse.class);
    }

    protected void verifyNorthboundSuccessResponse(FlowGenericCarrier carrierMock, Class<?> expectedPayloadType) {
        ArgumentCaptor<Message> responseCaptor = ArgumentCaptor.forClass(Message.class);
        verify(carrierMock).sendNorthboundResponse(responseCaptor.capture());

        Message rawResponse = responseCaptor.getValue();
        Assertions.assertNotNull(rawResponse);
        Assertions.assertTrue(rawResponse instanceof InfoMessage);

        InfoData rawPayload = ((InfoMessage) rawResponse).getData();
        Assertions.assertTrue(expectedPayloadType.isInstance(rawPayload));
    }

    protected void verifyNorthboundErrorResponse(FlowGenericCarrier carrier, ErrorType expectedErrorType) {
        ArgumentCaptor<Message> responseCaptor = ArgumentCaptor.forClass(Message.class);
        verify(carrier).sendNorthboundResponse(responseCaptor.capture());

        Message rawResponse = responseCaptor.getValue();
        Assertions.assertNotNull(rawResponse);
        Assertions.assertTrue(rawResponse instanceof ErrorMessage);
        ErrorMessage response = (ErrorMessage) rawResponse;

        Assertions.assertSame(expectedErrorType, response.getData().getErrorType());
    }

    protected void verifyNoSpeakerInteraction(FlowGenericCarrier carrier) {
        verify(carrier, never()).sendSpeakerRequest(any());
    }

    protected void verifyNoPathReplace(Flow origin, Flow result) {
        Assertions.assertEquals(origin.getForwardPathId(), result.getForwardPathId());
        Assertions.assertEquals(origin.getReversePathId(), result.getReversePathId());
    }

    protected void verifyPathReplace(Flow origin, Flow result) {
        Assertions.assertNotEquals(origin.getForwardPathId(), result.getForwardPathId());
        Assertions.assertNotEquals(origin.getReversePathId(), result.getReversePathId());
    }

    protected void alterFeatureToggles(Boolean isCreateAllowed, Boolean isUpdateAllowed, Boolean isDeleteAllowed) {
        KildaFeatureTogglesRepository repository = persistenceManager
                .getRepositoryFactory().createFeatureTogglesRepository();

        KildaFeatureToggles toggles = repository.find()
                .orElseGet(() -> {
                    KildaFeatureToggles newToggles = KildaFeatureToggles.builder().build();
                    repository.add(newToggles);
                    return newToggles;
                });

        if (isCreateAllowed != null) {
            toggles.setCreateFlowEnabled(isCreateAllowed);
        }
        if (isUpdateAllowed != null) {
            toggles.setUpdateFlowEnabled(isUpdateAllowed);
        }
        if (isDeleteAllowed != null) {
            toggles.setDeleteFlowEnabled(isDeleteAllowed);
        }
    }

    protected FlowRequest.FlowRequestBuilder makeRequest() {
        return FlowRequest.builder()
                .bandwidth(1000L)
                .source(new FlowEndpoint(
                        flowSource.getSwitchId(), flowSource.getPortNumber(), flowSource.getOuterVlanId()))
                .destination(new FlowEndpoint(
                        flowDestination.getSwitchId(), flowDestination.getPortNumber(),
                        flowDestination.getOuterVlanId()));
    }

    protected GetPathsResult makeOneSwitchPathPair() {
        return GetPathsResult.builder()
                .forward(Path.builder()
                        .srcSwitchId(SWITCH_SOURCE)
                        .destSwitchId(SWITCH_SOURCE)
                        .segments(Collections.emptyList())
                        .build())
                .reverse(Path.builder()
                        .srcSwitchId(SWITCH_SOURCE)
                        .destSwitchId(SWITCH_SOURCE)
                        .segments(Collections.emptyList())
                        .build())
                .build();
    }

    protected GetPathsResult make2SwitchesPathPair() {
        return makeSourceDestPathPair(islSourceDest);
    }

    protected GetPathsResult make2SwitchAltPathPair() {
        return makeSourceDestPathPair(islSourceDestAlt);
    }

    private GetPathsResult makeSourceDestPathPair(IslDirectionalReference islReference) {
        List<Segment> forwardSegments = ImmutableList.of(
                makePathSegment(islReference));
        List<Segment> reverseSegments = ImmutableList.of(
                makePathSegment(islReference.makeOpposite()));

        return GetPathsResult.builder()
                .forward(Path.builder()
                        .srcSwitchId(SWITCH_SOURCE)
                        .destSwitchId(SWITCH_DEST)
                        .segments(forwardSegments)
                        .build())
                .reverse(Path.builder()
                        .srcSwitchId(SWITCH_DEST)
                        .destSwitchId(SWITCH_SOURCE)
                        .segments(reverseSegments)
                        .build())
                .backUpPathComputationWayUsed(false)
                .build();
    }

    protected GetPathsResult make3SwitchesPathPair() {
        return make3SwitchesPathPair(false);
    }

    protected GetPathsResult make3SwitchesPathPair(boolean backUpPathComputationWayUsed) {
        List<Segment> forwardSegments = ImmutableList.of(
                makePathSegment(islSourceTransit),
                makePathSegment(islTransitDest));
        List<Segment> reverseSegments = ImmutableList.of(
                makePathSegment(islTransitDest.makeOpposite()),
                makePathSegment(islSourceTransit.makeOpposite()));

        return GetPathsResult.builder()
                .forward(Path.builder()
                        .srcSwitchId(SWITCH_SOURCE)
                        .destSwitchId(SWITCH_DEST)
                        .segments(forwardSegments)
                        .build())
                .reverse(Path.builder()
                        .srcSwitchId(SWITCH_DEST)
                        .destSwitchId(SWITCH_SOURCE)
                        .segments(reverseSegments)
                        .build())
                .backUpPathComputationWayUsed(backUpPathComputationWayUsed)
                .build();
    }

    private Segment makePathSegment(IslDirectionalReference reference) {
        IslEndpoint source = reference.getSourceEndpoint();
        IslEndpoint dest = reference.getDestEndpoint();
        return Segment.builder()
                .srcSwitchId(source.getSwitchId())
                .srcPort(source.getPortNumber())
                .destSwitchId(dest.getSwitchId())
                .destPort(dest.getPortNumber())
                .build();
    }

    protected Flow makeFlow() {
        return makeFlow(flowSource, flowDestination);
    }

    private Flow makeFlow(FlowEndpoint source, FlowEndpoint dest) {
        return dummyFactory.makeFlow(source, dest, islSourceDest);
    }

    protected Flow makeFlowArgumentMatch(String flowId) {
        return MockitoHamcrest.argThat(
                Matchers.hasProperty("flowId", is(flowId)));
    }

    protected void createTestYFlowForSubFlow(Flow subFlow) {
        YFlow yFlow = YFlow.builder()
                .yFlowId("test_y_flow")
                .sharedEndpoint(new SharedEndpoint(subFlow.getSrcSwitchId(),
                        subFlow.getSrcPort()))
                .status(FlowStatus.UP)
                .build();
        yFlow.setSubFlows(singleton(YSubFlow.builder()
                .sharedEndpointVlan(subFlow.getSrcVlan())
                .sharedEndpointInnerVlan(subFlow.getSrcInnerVlan())
                .endpointSwitchId(subFlow.getDestSwitchId())
                .endpointPort(subFlow.getDestPort())
                .endpointVlan(subFlow.getDestVlan())
                .endpointInnerVlan(subFlow.getDestInnerVlan())
                .flow(subFlow)
                .yFlow(yFlow)
                .build()));
        YFlowRepository yFlowRepository = persistenceManager.getRepositoryFactory().createYFlowRepository();
        yFlowRepository.add(yFlow);
    }
}
