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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.yflow.SubFlowDto;
import org.openkilda.messaging.command.yflow.SubFlowSharedEndpointEncapsulation;
import org.openkilda.messaging.command.yflow.YFlowRequest;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.IslEndpoint;
import org.openkilda.model.KildaFeatureToggles;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
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
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;

import com.google.common.collect.ImmutableList;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.hamcrest.MockitoHamcrest;
import org.mockito.stubbing.Answer;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public abstract class AbstractYFlowTest extends InMemoryGraphBasedTest {
    protected static final SwitchId SWITCH_SHARED = new SwitchId(1);
    protected static final SwitchId SWITCH_FIRST_EP = new SwitchId(2);
    protected static final SwitchId SWITCH_SECOND_EP = new SwitchId(3);
    protected static final SwitchId SWITCH_TRANSIT = new SwitchId(4);
    protected static final SwitchId SWITCH_ALT_TRANSIT = new SwitchId(5);

    protected final IslDirectionalReference islSharedToFirst = new IslDirectionalReference(
            new IslEndpoint(SWITCH_SHARED, 24),
            new IslEndpoint(SWITCH_FIRST_EP, 24));
    protected final IslDirectionalReference islSharedToSecond = new IslDirectionalReference(
            new IslEndpoint(SWITCH_SHARED, 30),
            new IslEndpoint(SWITCH_SECOND_EP, 30));
    protected final IslDirectionalReference islSharedToTransit = new IslDirectionalReference(
            new IslEndpoint(SWITCH_SHARED, 25),
            new IslEndpoint(SWITCH_TRANSIT, 25));
    protected final IslDirectionalReference islTransitToFirst = new IslDirectionalReference(
            new IslEndpoint(SWITCH_TRANSIT, 26),
            new IslEndpoint(SWITCH_FIRST_EP, 26));
    protected final IslDirectionalReference islTransitToSecond = new IslDirectionalReference(
            new IslEndpoint(SWITCH_TRANSIT, 27),
            new IslEndpoint(SWITCH_SECOND_EP, 27));
    protected final IslDirectionalReference islSharedToAltTransit = new IslDirectionalReference(
            new IslEndpoint(SWITCH_SHARED, 23),
            new IslEndpoint(SWITCH_ALT_TRANSIT, 25));
    protected final IslDirectionalReference islAltTransitToFirst = new IslDirectionalReference(
            new IslEndpoint(SWITCH_ALT_TRANSIT, 26),
            new IslEndpoint(SWITCH_FIRST_EP, 25));
    protected final IslDirectionalReference islAltTransitToSecond = new IslDirectionalReference(
            new IslEndpoint(SWITCH_ALT_TRANSIT, 27),
            new IslEndpoint(SWITCH_SECOND_EP, 28));

    protected final FlowEndpoint firstSharedEndpoint = new FlowEndpoint(SWITCH_SHARED, 1, 101);
    protected final FlowEndpoint secondSharedEndpoint = new FlowEndpoint(SWITCH_SHARED, 1, 102);
    protected final FlowEndpoint firstEndpoint = new FlowEndpoint(SWITCH_FIRST_EP, 2, 103);
    protected final FlowEndpoint secondEndpoint = new FlowEndpoint(SWITCH_SECOND_EP, 3, 104);

    protected static PersistenceDummyEntityFactory dummyFactory;

    private FlowRepository flowRepositorySpy = null;
    private FlowPathRepository flowPathRepositorySpy = null;
    private IslRepository islRepositorySpy = null;
    private YFlowRepository yFlowRepositorySpy = null;

    protected FlowResourcesManager flowResourcesManager = null;

    protected final String dummyRequestKey = "test-key";
    protected final String injectedErrorMessage = "Unit-test injected failure";

    protected CommandContext commandContext = new CommandContext();

    @Mock
    SwitchPropertiesRepository switchPropertiesRepository;
    @Mock
    PathComputer pathComputer;

    final Queue<FlowSegmentRequest> requests = new ArrayDeque<>();
    final Map<SwitchId, Map<Cookie, FlowSegmentRequest>> installedSegments = new HashMap<>();

    @Before
    public void before() {
        dummyFactory = new PersistenceDummyEntityFactory(persistenceManager);

        FlowResourcesConfig resourceConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        flowResourcesManager = spy(new FlowResourcesManager(persistenceManager, resourceConfig));

        alterFeatureToggles(true, true, true, true);

        dummyFactory.makeSwitch(SWITCH_SHARED);
        dummyFactory.makeSwitch(SWITCH_FIRST_EP);
        dummyFactory.makeSwitch(SWITCH_SECOND_EP);
        dummyFactory.makeSwitch(SWITCH_TRANSIT);
        dummyFactory.makeSwitch(SWITCH_ALT_TRANSIT);
        for (IslDirectionalReference reference : new IslDirectionalReference[]{
                islSharedToFirst, islSharedToSecond, islSharedToTransit, islTransitToFirst, islTransitToSecond,
                islSharedToAltTransit, islAltTransitToFirst, islAltTransitToSecond}) {
            dummyFactory.makeIsl(reference.getSourceEndpoint(), reference.getDestEndpoint());
            dummyFactory.makeIsl(reference.getDestEndpoint(), reference.getSourceEndpoint());
        }
    }

    @After
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
        if (yFlowRepositorySpy != null) {
            reset(yFlowRepositorySpy);
        }
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

    Answer getSpeakerCommandsAnswer() {
        return invocation -> {
            FlowSegmentRequest request = invocation.getArgument(0);
            requests.offer(request);

            if (request.isInstallRequest()) {
                installedSegments.computeIfAbsent(request.getSwitchId(), ignore -> new HashMap<>())
                        .put(request.getCookie(), request);
            }

            return request;
        };
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

    protected YFlowRepository setupYFlowRepositorySpy() {
        if (yFlowRepositorySpy == null) {
            yFlowRepositorySpy = spy(persistenceManager.getRepositoryFactory().createYFlowRepository());
            when(repositoryFactory.createYFlowRepository()).thenReturn(yFlowRepositorySpy);
        }
        return yFlowRepositorySpy;
    }

    protected YFlow verifyYFlowStatus(String yFlowId, FlowStatus expectedStatus) {
        YFlow flow = fetchYFlow(yFlowId);
        assertEquals(expectedStatus, flow.getStatus());
        flow.getSubFlows().forEach(subFlow -> {
            assertEquals(expectedStatus, subFlow.getSubFlow().getStatus());
        });

        return flow;
    }

    protected void verifyYFlowIsAbsent(String yFlowId) {
        YFlowRepository repository = persistenceManager.getRepositoryFactory().createYFlowRepository();
        assertFalse(repository.findById(yFlowId).isPresent());
    }

    protected YFlow fetchYFlow(String yFlowId) {
        YFlowRepository repository = persistenceManager.getRepositoryFactory().createYFlowRepository();
        return repository.findById(yFlowId)
                .orElseThrow(() -> new AssertionError(String.format(
                        "Y-flow %s not found in persistent storage", yFlowId)));
    }

    protected void verifyNorthboundSuccessResponse(FlowGenericCarrier carrierMock, Class<?> expectedPayloadType) {
        verifyNorthboundSuccessResponse(carrierMock, expectedPayloadType, 1);
    }

    protected void verifyNorthboundSuccessResponse(FlowGenericCarrier carrierMock, Class<?> expectedPayloadType,
                                                   int times) {
        ArgumentCaptor<Message> responseCaptor = ArgumentCaptor.forClass(Message.class);
        verify(carrierMock, times(times)).sendNorthboundResponse(responseCaptor.capture());

        Message rawResponse = responseCaptor.getValue();
        Assert.assertNotNull(rawResponse);
        Assert.assertTrue(rawResponse instanceof InfoMessage);

        InfoData rawPayload = ((InfoMessage) rawResponse).getData();
        Assert.assertTrue(expectedPayloadType.isInstance(rawPayload));
    }

    protected void verifyNorthboundErrorResponse(FlowGenericCarrier carrier, ErrorType expectedErrorType) {
        ArgumentCaptor<Message> responseCaptor = ArgumentCaptor.forClass(Message.class);
        verify(carrier).sendNorthboundResponse(responseCaptor.capture());

        Message rawResponse = responseCaptor.getValue();
        Assert.assertNotNull(rawResponse);
        Assert.assertTrue(rawResponse instanceof ErrorMessage);
        ErrorMessage response = (ErrorMessage) rawResponse;

        Assert.assertSame(expectedErrorType, response.getData().getErrorType());
    }

    protected void verifyNoNorthboundResponse(FlowGenericCarrier carrier) {
        verify(carrier, never()).sendNorthboundResponse(any());
    }

    protected void verifyNoSpeakerInteraction(FlowGenericCarrier carrier) {
        verify(carrier, never()).sendSpeakerRequest(any());
    }

    protected void alterFeatureToggles(Boolean isCreateAllowed, Boolean isUpdateAllowed, Boolean isDeleteAllowed,
                                       Boolean isCudOnYFlowAllowed) {
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
        if (isCudOnYFlowAllowed != null) {
            toggles.setCudOnYFlowEnabled(isCudOnYFlowAllowed);
        }
    }

    protected YFlowRequest.YFlowRequestBuilder makeYFlowRequest(String yFlowId, String firstSubFlowId,
                                                                String secondSubFlowId) {
        List<SubFlowDto> subFlows = Arrays.asList(
                SubFlowDto.builder()
                        .flowId(firstSubFlowId)
                        .endpoint(firstEndpoint)
                        .sharedEndpoint(new SubFlowSharedEndpointEncapsulation(firstSharedEndpoint.getOuterVlanId(),
                                firstSharedEndpoint.getInnerVlanId()))
                        .build(),
                SubFlowDto.builder()
                        .flowId(secondSubFlowId)
                        .endpoint(secondEndpoint)
                        .sharedEndpoint(new SubFlowSharedEndpointEncapsulation(secondSharedEndpoint.getOuterVlanId(),
                                secondSharedEndpoint.getInnerVlanId()))
                        .build());
        return YFlowRequest.builder()
                .yFlowId(yFlowId)
                .maximumBandwidth(1000L)
                .sharedEndpoint(new FlowEndpoint(firstSharedEndpoint.getSwitchId(),
                        firstSharedEndpoint.getPortNumber()))
                .subFlows(subFlows);
    }

    protected GetPathsResult makeFirstSubFlowPathPair() {
        List<Segment> forwardSegments = ImmutableList.of(
                makePathSegment(islSharedToTransit),
                makePathSegment(islTransitToFirst));
        List<Segment> reverseSegments = ImmutableList.of(
                makePathSegment(islTransitToFirst.makeOpposite()),
                makePathSegment(islSharedToTransit.makeOpposite()));

        return GetPathsResult.builder()
                .forward(Path.builder()
                        .srcSwitchId(SWITCH_SHARED)
                        .destSwitchId(SWITCH_FIRST_EP)
                        .segments(forwardSegments)
                        .build())
                .reverse(Path.builder()
                        .srcSwitchId(SWITCH_FIRST_EP)
                        .destSwitchId(SWITCH_SHARED)
                        .segments(reverseSegments)
                        .build())
                .backUpPathComputationWayUsed(false)
                .build();
    }

    protected GetPathsResult makeSecondSubFlowPathPair() {
        List<Segment> forwardSegments = ImmutableList.of(
                makePathSegment(islSharedToTransit),
                makePathSegment(islTransitToSecond));
        List<Segment> reverseSegments = ImmutableList.of(
                makePathSegment(islTransitToSecond.makeOpposite()),
                makePathSegment(islSharedToTransit.makeOpposite()));

        return GetPathsResult.builder()
                .forward(Path.builder()
                        .srcSwitchId(SWITCH_SHARED)
                        .destSwitchId(SWITCH_SECOND_EP)
                        .segments(forwardSegments)
                        .build())
                .reverse(Path.builder()
                        .srcSwitchId(SWITCH_SECOND_EP)
                        .destSwitchId(SWITCH_SHARED)
                        .segments(reverseSegments)
                        .build())
                .backUpPathComputationWayUsed(false)
                .build();
    }

    protected GetPathsResult makeFirstSubFlowProtectedPathPair() {
        List<Segment> forwardSegments = ImmutableList.of(
                makePathSegment(islSharedToAltTransit),
                makePathSegment(islAltTransitToFirst));
        List<Segment> reverseSegments = ImmutableList.of(
                makePathSegment(islAltTransitToFirst.makeOpposite()),
                makePathSegment(islSharedToAltTransit.makeOpposite()));

        return GetPathsResult.builder()
                .forward(Path.builder()
                        .srcSwitchId(SWITCH_SHARED)
                        .destSwitchId(SWITCH_FIRST_EP)
                        .segments(forwardSegments)
                        .build())
                .reverse(Path.builder()
                        .srcSwitchId(SWITCH_FIRST_EP)
                        .destSwitchId(SWITCH_SHARED)
                        .segments(reverseSegments)
                        .build())
                .backUpPathComputationWayUsed(false)
                .build();
    }

    protected GetPathsResult makeSecondSubFlowProtectedPathPair() {
        List<Segment> forwardSegments = ImmutableList.of(
                makePathSegment(islSharedToAltTransit),
                makePathSegment(islAltTransitToSecond));
        List<Segment> reverseSegments = ImmutableList.of(
                makePathSegment(islAltTransitToSecond.makeOpposite()),
                makePathSegment(islSharedToAltTransit.makeOpposite()));

        return GetPathsResult.builder()
                .forward(Path.builder()
                        .srcSwitchId(SWITCH_SHARED)
                        .destSwitchId(SWITCH_SECOND_EP)
                        .segments(forwardSegments)
                        .build())
                .reverse(Path.builder()
                        .srcSwitchId(SWITCH_SECOND_EP)
                        .destSwitchId(SWITCH_SHARED)
                        .segments(reverseSegments)
                        .build())
                .backUpPathComputationWayUsed(false)
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

    protected Flow makeFlowArgumentMatch(String flowId) {
        return MockitoHamcrest.argThat(
                Matchers.hasProperty("flowId", is(flowId)));
    }
}
