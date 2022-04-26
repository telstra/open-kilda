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

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.FlowCommand;
import org.openkilda.floodlight.api.request.rulemanager.GroupCommand;
import org.openkilda.floodlight.api.request.rulemanager.MeterCommand;
import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse.ErrorCode;
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
import org.openkilda.model.FlowMeter;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.IslEndpoint;
import org.openkilda.model.KildaFeatureToggles;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
import org.openkilda.model.YFlow.SharedEndpoint;
import org.openkilda.model.YSubFlow;
import org.openkilda.pce.GetPathsResult;
import org.openkilda.pce.Path;
import org.openkilda.pce.Path.Segment;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.dummy.IslDirectionalReference;
import org.openkilda.persistence.dummy.PersistenceDummyEntityFactory;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.RuleManagerImpl;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.service.common.NorthboundResponseCarrier;

import com.google.common.collect.ImmutableList;
import lombok.SneakyThrows;
import net.jodah.failsafe.function.CheckedConsumer;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.hamcrest.MockitoHamcrest;
import org.mockito.stubbing.Answer;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractYFlowTest<T> extends InMemoryGraphBasedTest {
    protected static final SwitchId SWITCH_SHARED = new SwitchId(1);
    protected static final SwitchId SWITCH_FIRST_EP = new SwitchId(2);
    protected static final SwitchId SWITCH_SECOND_EP = new SwitchId(3);
    protected static final SwitchId SWITCH_TRANSIT = new SwitchId(4);
    protected static final SwitchId SWITCH_ALT_TRANSIT = new SwitchId(5);
    protected static final SwitchId SWITCH_NEW_FIRST_EP = new SwitchId(6);
    protected static final SwitchId SWITCH_NEW_SECOND_EP = new SwitchId(7);
    protected static final SwitchId SWITCH_NEW_ALT_TRANSIT = new SwitchId(8);
    protected static final SwitchId SWITCH_NEW_TRANSIT = new SwitchId(9);

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
    protected final IslDirectionalReference islTransitToNewFirst = new IslDirectionalReference(
            new IslEndpoint(SWITCH_TRANSIT, 31),
            new IslEndpoint(SWITCH_NEW_FIRST_EP, 31));
    protected final IslDirectionalReference islTransitToNewSecond = new IslDirectionalReference(
            new IslEndpoint(SWITCH_TRANSIT, 32),
            new IslEndpoint(SWITCH_NEW_SECOND_EP, 32));
    protected final IslDirectionalReference islSharedToNewAltTransit = new IslDirectionalReference(
            new IslEndpoint(SWITCH_SHARED, 22),
            new IslEndpoint(SWITCH_NEW_ALT_TRANSIT, 25));
    protected final IslDirectionalReference islNewAltTransitToFirst = new IslDirectionalReference(
            new IslEndpoint(SWITCH_NEW_ALT_TRANSIT, 26),
            new IslEndpoint(SWITCH_NEW_FIRST_EP, 33));
    protected final IslDirectionalReference islNewAltTransitToSecond = new IslDirectionalReference(
            new IslEndpoint(SWITCH_NEW_ALT_TRANSIT, 27),
            new IslEndpoint(SWITCH_NEW_SECOND_EP, 34));
    protected final IslDirectionalReference islSharedToNewTransit = new IslDirectionalReference(
            new IslEndpoint(SWITCH_SHARED, 52),
            new IslEndpoint(SWITCH_NEW_TRANSIT, 52));
    protected final IslDirectionalReference islNewTransitToFirst = new IslDirectionalReference(
            new IslEndpoint(SWITCH_NEW_TRANSIT, 62),
            new IslEndpoint(SWITCH_FIRST_EP, 62));
    protected final IslDirectionalReference islNewTransitToSecond = new IslDirectionalReference(
            new IslEndpoint(SWITCH_NEW_TRANSIT, 72),
            new IslEndpoint(SWITCH_SECOND_EP, 72));

    protected final FlowEndpoint firstSharedEndpoint = new FlowEndpoint(SWITCH_SHARED, 1, 101);
    protected final FlowEndpoint secondSharedEndpoint = new FlowEndpoint(SWITCH_SHARED, 1, 102);
    protected final FlowEndpoint firstEndpoint = new FlowEndpoint(SWITCH_FIRST_EP, 2, 103);
    protected final FlowEndpoint secondEndpoint = new FlowEndpoint(SWITCH_SECOND_EP, 3, 104);
    protected final FlowEndpoint newFirstEndpoint = new FlowEndpoint(SWITCH_NEW_FIRST_EP, 2, 103);
    protected final FlowEndpoint newSecondEndpoint = new FlowEndpoint(SWITCH_NEW_SECOND_EP, 3, 104);

    protected static PersistenceDummyEntityFactory dummyFactory;

    private YFlowRepository yFlowRepositorySpy = null;

    protected FlowResourcesManager flowResourcesManager = null;
    protected RuleManager ruleManager = null;

    protected final String injectedErrorMessage = "Unit-test injected failure";

    @Mock
    protected PathComputer pathComputer;

    protected final Queue<T> requests = new ArrayDeque<>();

    @Before
    public void before() {
        dummyFactory = new PersistenceDummyEntityFactory(persistenceManager);

        FlowResourcesConfig resourceConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        flowResourcesManager = spy(new FlowResourcesManager(persistenceManager, resourceConfig));

        RuleManagerConfig ruleManagerConfig = configurationProvider.getConfiguration(RuleManagerConfig.class);
        ruleManager = spy(new RuleManagerImpl(ruleManagerConfig));

        alterFeatureToggles(true, true, true, true);

        dummyFactory.makeSwitch(SWITCH_SHARED);
        dummyFactory.makeSwitch(SWITCH_FIRST_EP);
        dummyFactory.makeSwitch(SWITCH_SECOND_EP);
        dummyFactory.makeSwitch(SWITCH_TRANSIT);
        dummyFactory.makeSwitch(SWITCH_NEW_TRANSIT);
        dummyFactory.makeSwitch(SWITCH_ALT_TRANSIT);
        dummyFactory.makeSwitch(SWITCH_NEW_FIRST_EP);
        dummyFactory.makeSwitch(SWITCH_NEW_SECOND_EP);
        for (IslDirectionalReference reference : new IslDirectionalReference[]{
                islSharedToFirst, islSharedToSecond, islSharedToTransit, islTransitToFirst, islTransitToSecond,
                islSharedToAltTransit, islAltTransitToFirst, islAltTransitToSecond, islTransitToNewFirst,
                islTransitToNewSecond, islSharedToNewAltTransit, islNewAltTransitToFirst, islNewAltTransitToSecond,
                islSharedToNewTransit, islNewTransitToFirst, islNewTransitToSecond}) {
            dummyFactory.makeIsl(reference.getSourceEndpoint(), reference.getDestEndpoint());
            dummyFactory.makeIsl(reference.getDestEndpoint(), reference.getSourceEndpoint());
        }
    }

    @After
    public void tearDown() throws Exception {
        if (yFlowRepositorySpy != null) {
            reset(yFlowRepositorySpy);
        }
    }

    protected Answer<T> buildSpeakerRequestAnswer() {
        return invocation -> {
            T request = invocation.getArgument(0);
            requests.offer(request);
            return request;
        };
    }

    @SneakyThrows
    protected void handleSpeakerRequests(CheckedConsumer<T> speakerRequestConsumer) {
        T request;
        while ((request = requests.poll()) != null) {
            // For testing purpose we assume that key equals to flowId.
            speakerRequestConsumer.accept(request);
        }
    }

    protected SpeakerFlowSegmentResponse buildSuccessfulSpeakerResponse(FlowSegmentRequest flowRequest) {
        return SpeakerFlowSegmentResponse.builder()
                .messageContext(flowRequest.getMessageContext())
                .commandId(flowRequest.getCommandId())
                .metadata(flowRequest.getMetadata())
                .switchId(flowRequest.getSwitchId())
                .success(true)
                .build();
    }

    protected SpeakerFlowSegmentResponse buildErrorSpeakerResponse(FlowSegmentRequest request) {
        return FlowErrorResponse.errorBuilder()
                .messageContext(request.getMessageContext())
                .metadata(request.getMetadata())
                .commandId(request.getCommandId())
                .switchId(request.getSwitchId())
                .errorCode(ErrorCode.UNKNOWN)
                .description("Switch is unavailable")
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

    protected YFlowRepository setupYFlowRepositorySpy() {
        if (yFlowRepositorySpy == null) {
            yFlowRepositorySpy = spy(persistenceManager.getRepositoryFactory().createYFlowRepository());
            when(repositoryFactory.createYFlowRepository()).thenReturn(yFlowRepositorySpy);
        }
        return yFlowRepositorySpy;
    }

    protected YFlow verifyYFlowStatus(String yFlowId, FlowStatus expectedStatus) {
        YFlow flow = getYFlow(yFlowId);
        assertEquals(expectedStatus, flow.getStatus());
        return flow;
    }

    protected void verifyYFlowAndSubFlowStatus(String yFlowId, FlowStatus expectedStatus) {
        YFlow flow = getYFlow(yFlowId);
        assertEquals(expectedStatus, flow.getStatus());
        flow.getSubFlows().forEach(subFlow -> {
            assertEquals(expectedStatus, subFlow.getFlow().getStatus());
        });
    }

    protected void verifyAffinity(String yFlowId) {
        YFlow flow = getYFlow(yFlowId);
        Set<String> affinityGroups = flow.getSubFlows().stream()
                .map(YSubFlow::getFlow)
                .map(Flow::getAffinityGroupId)
                .collect(Collectors.toSet());
        assertEquals(1, affinityGroups.size());

        String affinityGroupId = affinityGroups.iterator().next();
        Set<String> subFlowIds = flow.getSubFlows().stream()
                .map(YSubFlow::getSubFlowId)
                .collect(Collectors.toSet());
        assertTrue(subFlowIds.contains(affinityGroupId));
    }

    protected void verifyYFlowIsAbsent(String yFlowId) {
        YFlowRepository repository = persistenceManager.getRepositoryFactory().createYFlowRepository();
        assertFalse(repository.findById(yFlowId).isPresent());
    }

    protected YFlow getYFlow(String yFlowId) {
        YFlowRepository repository = persistenceManager.getRepositoryFactory().createYFlowRepository();
        return repository.findById(yFlowId)
                .orElseThrow(() -> new AssertionError(format(
                        "Y-flow %s not found in persistent storage", yFlowId)));
    }

    protected void verifyNorthboundSuccessResponse(NorthboundResponseCarrier carrierMock,
                                                   Class<?> expectedPayloadType) {
        verifyNorthboundSuccessResponse(carrierMock, expectedPayloadType, 1);
    }

    protected void verifyNorthboundSuccessResponse(NorthboundResponseCarrier carrierMock, Class<?> expectedPayloadType,
                                                   int times) {
        ArgumentCaptor<Message> responseCaptor = ArgumentCaptor.forClass(Message.class);
        verify(carrierMock, times(times)).sendNorthboundResponse(responseCaptor.capture());

        Message rawResponse = responseCaptor.getValue();
        Assert.assertNotNull(rawResponse);
        Assert.assertTrue(rawResponse instanceof InfoMessage);

        InfoData rawPayload = ((InfoMessage) rawResponse).getData();
        Assert.assertTrue(expectedPayloadType.isInstance(rawPayload));
    }

    protected void verifyNorthboundErrorResponse(NorthboundResponseCarrier carrier, ErrorType expectedErrorType) {
        ArgumentCaptor<Message> responseCaptor = ArgumentCaptor.forClass(Message.class);
        verify(carrier).sendNorthboundResponse(responseCaptor.capture());

        Message rawResponse = responseCaptor.getValue();
        Assert.assertNotNull(rawResponse);
        Assert.assertTrue(rawResponse instanceof ErrorMessage);
        ErrorMessage response = (ErrorMessage) rawResponse;

        Assert.assertSame(expectedErrorType, response.getData().getErrorType());
    }

    protected void verifyNoNorthboundResponse(NorthboundResponseCarrier carrier) {
        verify(carrier, never()).sendNorthboundResponse(any());
    }

    protected void verifyNoSpeakerInteraction(FlowGenericCarrier carrier) {
        verify(carrier, never()).sendSpeakerRequest(any());
    }

    protected void alterFeatureToggles(Boolean isCreateAllowed, Boolean isUpdateAllowed, Boolean isDeleteAllowed,
                                       Boolean isModifyYFlowEnabled) {
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
        if (isModifyYFlowEnabled != null) {
            toggles.setModifyYFlowEnabled(isModifyYFlowEnabled);
        }
    }

    protected YFlowRequest.YFlowRequestBuilder buildYFlowRequest(String yFlowId, String firstSubFlowId,
                                                                 String secondSubFlowId) {
        List<SubFlowDto> subFlows = asList(
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

    protected GetPathsResult buildFirstSubFlowPathPair() {
        List<Segment> forwardSegments = ImmutableList.of(
                buildPathSegment(islSharedToTransit),
                buildPathSegment(islTransitToFirst));
        List<Segment> reverseSegments = ImmutableList.of(
                buildPathSegment(islTransitToFirst.makeOpposite()),
                buildPathSegment(islSharedToTransit.makeOpposite()));

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

    protected GetPathsResult buildSecondSubFlowPathPair() {
        List<Segment> forwardSegments = ImmutableList.of(
                buildPathSegment(islSharedToTransit),
                buildPathSegment(islTransitToSecond));
        List<Segment> reverseSegments = ImmutableList.of(
                buildPathSegment(islTransitToSecond.makeOpposite()),
                buildPathSegment(islSharedToTransit.makeOpposite()));

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

    protected GetPathsResult buildFirstSubFlowProtectedPathPair() {
        List<Segment> forwardSegments = ImmutableList.of(
                buildPathSegment(islSharedToAltTransit),
                buildPathSegment(islAltTransitToFirst));
        List<Segment> reverseSegments = ImmutableList.of(
                buildPathSegment(islAltTransitToFirst.makeOpposite()),
                buildPathSegment(islSharedToAltTransit.makeOpposite()));

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

    protected GetPathsResult buildSecondSubFlowProtectedPathPair() {
        List<Segment> forwardSegments = ImmutableList.of(
                buildPathSegment(islSharedToAltTransit),
                buildPathSegment(islAltTransitToSecond));
        List<Segment> reverseSegments = ImmutableList.of(
                buildPathSegment(islAltTransitToSecond.makeOpposite()),
                buildPathSegment(islSharedToAltTransit.makeOpposite()));

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

    protected GetPathsResult buildNewFirstSubFlowPathPair() {
        List<Segment> forwardSegments = ImmutableList.of(
                buildPathSegment(islSharedToTransit),
                buildPathSegment(islTransitToNewFirst));
        List<Segment> reverseSegments = ImmutableList.of(
                buildPathSegment(islTransitToNewFirst.makeOpposite()),
                buildPathSegment(islSharedToTransit.makeOpposite()));

        return GetPathsResult.builder()
                .forward(Path.builder()
                        .srcSwitchId(SWITCH_SHARED)
                        .destSwitchId(SWITCH_NEW_FIRST_EP)
                        .segments(forwardSegments)
                        .build())
                .reverse(Path.builder()
                        .srcSwitchId(SWITCH_NEW_FIRST_EP)
                        .destSwitchId(SWITCH_SHARED)
                        .segments(reverseSegments)
                        .build())
                .backUpPathComputationWayUsed(false)
                .build();
    }

    protected GetPathsResult buildNewSecondSubFlowPathPair() {
        List<Segment> forwardSegments = ImmutableList.of(
                buildPathSegment(islSharedToTransit),
                buildPathSegment(islTransitToNewSecond));
        List<Segment> reverseSegments = ImmutableList.of(
                buildPathSegment(islTransitToNewSecond.makeOpposite()),
                buildPathSegment(islSharedToTransit.makeOpposite()));

        return GetPathsResult.builder()
                .forward(Path.builder()
                        .srcSwitchId(SWITCH_SHARED)
                        .destSwitchId(SWITCH_NEW_SECOND_EP)
                        .segments(forwardSegments)
                        .build())
                .reverse(Path.builder()
                        .srcSwitchId(SWITCH_NEW_SECOND_EP)
                        .destSwitchId(SWITCH_SHARED)
                        .segments(reverseSegments)
                        .build())
                .backUpPathComputationWayUsed(false)
                .build();
    }

    protected GetPathsResult buildNewFirstSubFlowProtectedPathPair() {
        List<Segment> forwardSegments = ImmutableList.of(
                buildPathSegment(islSharedToNewAltTransit),
                buildPathSegment(islNewAltTransitToFirst));
        List<Segment> reverseSegments = ImmutableList.of(
                buildPathSegment(islNewAltTransitToFirst.makeOpposite()),
                buildPathSegment(islSharedToNewAltTransit.makeOpposite()));

        return GetPathsResult.builder()
                .forward(Path.builder()
                        .srcSwitchId(SWITCH_SHARED)
                        .destSwitchId(SWITCH_NEW_FIRST_EP)
                        .segments(forwardSegments)
                        .build())
                .reverse(Path.builder()
                        .srcSwitchId(SWITCH_NEW_FIRST_EP)
                        .destSwitchId(SWITCH_SHARED)
                        .segments(reverseSegments)
                        .build())
                .backUpPathComputationWayUsed(false)
                .build();
    }

    protected GetPathsResult buildNewSecondSubFlowProtectedPathPair() {
        List<Segment> forwardSegments = ImmutableList.of(
                buildPathSegment(islSharedToNewAltTransit),
                buildPathSegment(islNewAltTransitToSecond));
        List<Segment> reverseSegments = ImmutableList.of(
                buildPathSegment(islNewAltTransitToSecond.makeOpposite()),
                buildPathSegment(islSharedToNewAltTransit.makeOpposite()));

        return GetPathsResult.builder()
                .forward(Path.builder()
                        .srcSwitchId(SWITCH_SHARED)
                        .destSwitchId(SWITCH_NEW_SECOND_EP)
                        .segments(forwardSegments)
                        .build())
                .reverse(Path.builder()
                        .srcSwitchId(SWITCH_NEW_SECOND_EP)
                        .destSwitchId(SWITCH_SHARED)
                        .segments(reverseSegments)
                        .build())
                .backUpPathComputationWayUsed(false)
                .build();
    }

    protected GetPathsResult buildFirstSubFlowPathPairWithNewTransit() {
        List<Segment> forwardSegments = ImmutableList.of(
                buildPathSegment(islSharedToNewTransit),
                buildPathSegment(islNewTransitToFirst));
        List<Segment> reverseSegments = ImmutableList.of(
                buildPathSegment(islNewTransitToFirst.makeOpposite()),
                buildPathSegment(islSharedToNewTransit.makeOpposite()));

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

    protected GetPathsResult buildSecondSubFlowPathPairWithNewTransit() {
        List<Segment> forwardSegments = ImmutableList.of(
                buildPathSegment(islSharedToNewTransit),
                buildPathSegment(islNewTransitToSecond));
        List<Segment> reverseSegments = ImmutableList.of(
                buildPathSegment(islNewTransitToSecond.makeOpposite()),
                buildPathSegment(islSharedToNewTransit.makeOpposite()));

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

    private Segment buildPathSegment(IslDirectionalReference reference) {
        IslEndpoint source = reference.getSourceEndpoint();
        IslEndpoint dest = reference.getDestEndpoint();
        return Segment.builder()
                .srcSwitchId(source.getSwitchId())
                .srcPort(source.getPortNumber())
                .destSwitchId(dest.getSwitchId())
                .destPort(dest.getPortNumber())
                .build();
    }

    protected Flow buildFlowIdArgumentMatch(String flowId) {
        return MockitoHamcrest.argThat(
                Matchers.hasProperty("flowId", is(flowId)));
    }

    protected YFlow createYFlowViaTransit(String yFlowId) {
        // Create sub-flows
        Flow firstFlow =
                dummyFactory.makeMainAffinityFlow(firstSharedEndpoint, firstEndpoint,
                        islSharedToTransit, islTransitToFirst);
        Flow secondFlow =
                dummyFactory.makeFlow(secondSharedEndpoint, secondEndpoint, firstFlow.getAffinityGroupId(),
                        islSharedToTransit, islTransitToSecond);

        SwitchId yPoint = SWITCH_TRANSIT;
        FlowMeter yPointMeter = dummyFactory.makeFlowMeter(yPoint, yFlowId, null);
        FlowMeter sharedEndpointMeter = dummyFactory.makeFlowMeter(firstSharedEndpoint.getSwitchId(), yFlowId, null);

        YFlow yFlow = YFlow.builder()
                .yFlowId(yFlowId)
                .sharedEndpoint(new SharedEndpoint(firstSharedEndpoint.getSwitchId(),
                        firstSharedEndpoint.getPortNumber()))
                .sharedEndpointMeterId(sharedEndpointMeter.getMeterId())
                .yPoint(yPoint)
                .meterId(yPointMeter.getMeterId())
                .status(FlowStatus.UP)
                .build();
        yFlow.setSubFlows(Stream.of(firstFlow, secondFlow)
                .map(flow -> YSubFlow.builder()
                        .sharedEndpointVlan(flow.getSrcVlan())
                        .sharedEndpointInnerVlan(flow.getSrcInnerVlan())
                        .endpointSwitchId(flow.getDestSwitchId())
                        .endpointPort(flow.getDestPort())
                        .endpointVlan(flow.getDestVlan())
                        .endpointInnerVlan(flow.getDestInnerVlan())
                        .flow(flow)
                        .yFlow(yFlow)
                        .build())
                .collect(Collectors.toSet()));

        YFlowRepository yFlowRepository = persistenceManager.getRepositoryFactory().createYFlowRepository();
        yFlowRepository.add(yFlow);
        return yFlow;
    }

    protected YFlow createYFlowWithProtected(String yFlowId) {
        dummyFactory.getFlowDefaults().setAllocateProtectedPath(true);
        // Create sub-flows
        String firstFlowId = format("%s_1", yFlowId);
        Flow firstFlow = dummyFactory.makeFlowWithProtectedPath(firstFlowId,
                firstSharedEndpoint, firstEndpoint,
                firstFlowId,
                asList(islSharedToTransit, islTransitToFirst),
                asList(islSharedToAltTransit, islAltTransitToFirst));

        String secondFlowId = format("%s_2", yFlowId);
        Flow secondFlow = dummyFactory.makeFlowWithProtectedPath(secondFlowId,
                secondSharedEndpoint, secondEndpoint,
                firstFlow.getAffinityGroupId(),
                asList(islSharedToTransit, islTransitToSecond),
                asList(islSharedToAltTransit, islAltTransitToSecond));

        SwitchId yPoint = SWITCH_TRANSIT;
        SwitchId protectedPathYPoint = SWITCH_ALT_TRANSIT;
        FlowMeter yPointMeter = dummyFactory.makeFlowMeter(yPoint, yFlowId, null);
        FlowMeter protectedPathYPointMeter = dummyFactory.makeFlowMeter(protectedPathYPoint, yFlowId, null);
        FlowMeter sharedEndpointMeter = dummyFactory.makeFlowMeter(firstSharedEndpoint.getSwitchId(), yFlowId, null);

        YFlow yFlow = YFlow.builder()
                .yFlowId(yFlowId)
                .sharedEndpoint(new SharedEndpoint(firstSharedEndpoint.getSwitchId(),
                        firstSharedEndpoint.getPortNumber()))
                .sharedEndpointMeterId(sharedEndpointMeter.getMeterId())
                .yPoint(yPoint)
                .meterId(yPointMeter.getMeterId())
                .protectedPathYPoint(protectedPathYPoint)
                .protectedPathMeterId(protectedPathYPointMeter.getMeterId())
                .allocateProtectedPath(true)
                .status(FlowStatus.UP)
                .build();
        yFlow.setSubFlows(Stream.of(firstFlow, secondFlow)
                .map(flow -> YSubFlow.builder()
                        .sharedEndpointVlan(flow.getSrcVlan())
                        .sharedEndpointInnerVlan(flow.getSrcInnerVlan())
                        .endpointSwitchId(flow.getDestSwitchId())
                        .endpointPort(flow.getDestPort())
                        .endpointVlan(flow.getDestVlan())
                        .endpointInnerVlan(flow.getDestInnerVlan())
                        .flow(flow)
                        .yFlow(yFlow)
                        .build())
                .collect(Collectors.toSet()));

        YFlowRepository yFlowRepository = persistenceManager.getRepositoryFactory().createYFlowRepository();
        yFlowRepository.add(yFlow);
        return yFlow;
    }
}
