/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.service.yflow;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.request.SpeakerRequest;
import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.InstallSpeakerCommandsRequest;
import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.messaging.command.yflow.YFlowPathSwapRequest;
import org.openkilda.messaging.command.yflow.YFlowResponse;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.YFlow;
import org.openkilda.model.YSubFlow;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.exception.UnknownKeyException;
import org.openkilda.wfm.topology.flowhs.service.AbstractYFlowTest;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowPathSwapHubCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowPathSwapService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RunWith(MockitoJUnitRunner.class)
public class YFlowPathSwapServiceTest extends AbstractYFlowTest<SpeakerRequest> {

    @Mock
    private FlowPathSwapHubCarrier flowPathSwapHubCarrier;
    @Mock
    private FlowGenericCarrier yFlowPathSwapHubCarrier;

    @Before
    public void init() {
        doAnswer(buildSpeakerRequestAnswer())
                .when(flowPathSwapHubCarrier).sendSpeakerRequest(any(SpeakerRequest.class));
        doAnswer(buildSpeakerRequestAnswer())
                .when(yFlowPathSwapHubCarrier).sendSpeakerRequest(any(SpeakerRequest.class));
    }

    @Test
    public void shouldSuccessfullySwapYFlowPaths() throws DuplicateKeyException {
        // given
        YFlow origin = createYFlow();
        YFlowPathSwapRequest request = new YFlowPathSwapRequest(origin.getYFlowId());

        // when
        processPathSwapRequestAndSpeakerCommands(request);

        // then
        verifyNorthboundSuccessResponse(yFlowPathSwapHubCarrier);
        YFlow result = verifyYFlowStatus(request.getYFlowId(), FlowStatus.UP, FlowStatus.UP, FlowStatus.UP);
        verifyPathSwapped(origin, result);
    }

    @Test
    public void shouldFailSwapOnUnsuccessfulSubFlowRulesInstallation()
            throws DuplicateKeyException {
        // given
        YFlow origin = createYFlow();
        String yFlowId = origin.getYFlowId();
        YFlowPathSwapRequest request = new YFlowPathSwapRequest(yFlowId);

        YFlowPathSwapService service = makeYFlowPathSwapService(0);

        // when
        service.handleRequest(yFlowId, new CommandContext(), request);
        verifyYFlowStatus(yFlowId, FlowStatus.IN_PROGRESS, FlowStatus.IN_PROGRESS, FlowStatus.IN_PROGRESS);
        // and
        handleSpeakerCommandsAndFailInstall(service, yFlowId, yFlowId + "_1");

        // then
        verifyNorthboundErrorResponse(yFlowPathSwapHubCarrier, ErrorType.INTERNAL_ERROR);
        YFlow result = verifyYFlowStatus(yFlowId, FlowStatus.UP);
        verifyPathNotSwapped(origin, result);
    }

    @Test
    public void shouldFailSwapOnTimeoutDuringYFlowRulesInstallation()
            throws DuplicateKeyException {
        // given
        YFlow origin = createYFlow();
        String yFlowId = origin.getYFlowId();
        YFlowPathSwapRequest request = new YFlowPathSwapRequest(origin.getYFlowId());

        YFlowPathSwapService service = makeYFlowPathSwapService(0);

        // when
        service.handleRequest(yFlowId, new CommandContext(), request);
        verifyYFlowStatus(yFlowId, FlowStatus.IN_PROGRESS, FlowStatus.IN_PROGRESS, FlowStatus.IN_PROGRESS);
        // and
        handleSpeakerCommandsAndTimeoutInstall(service, request.getYFlowId());

        // then
        verifyNorthboundErrorResponse(yFlowPathSwapHubCarrier, ErrorType.INTERNAL_ERROR);
        YFlow result = verifyYFlowStatus(request.getYFlowId(), FlowStatus.UP);
        verifyPathNotSwapped(origin, result);
    }

    private YFlow createYFlow() {
        YFlow result = createYFlowWithProtected("test_yflow");
        YFlowRepository yFlowRepository = persistenceManager.getRepositoryFactory().createYFlowRepository();
        yFlowRepository.detach(result);
        return result;
    }

    private void processPathSwapRequestAndSpeakerCommands(YFlowPathSwapRequest yFlowRequest)
            throws DuplicateKeyException {
        YFlowPathSwapService service = makeYFlowPathSwapService(0);
        service.handleRequest(yFlowRequest.getYFlowId(), new CommandContext(), yFlowRequest);

        verifyYFlowStatus(yFlowRequest.getYFlowId(), FlowStatus.IN_PROGRESS);

        handleSpeakerRequests(speakerRequest -> {
            SpeakerResponse commandResponse;
            if (speakerRequest instanceof FlowSegmentRequest) {
                FlowSegmentRequest flowSegmentRequest = (FlowSegmentRequest) speakerRequest;
                commandResponse = buildSuccessfulSpeakerResponse(flowSegmentRequest);
            } else {
                BaseSpeakerCommandsRequest speakerCommandsRequest = (BaseSpeakerCommandsRequest) speakerRequest;
                commandResponse = buildSuccessfulYFlowSpeakerResponse(speakerCommandsRequest);
            }
            handleAsyncResponse(service, yFlowRequest.getYFlowId(), commandResponse);
        });
    }

    private void handleAsyncResponse(YFlowPathSwapService yFlowPathSwapService,
                                     String yFlowFsmKey, SpeakerResponse commandResponse) {
        try {
            yFlowPathSwapService.handleAsyncResponse(yFlowFsmKey, commandResponse);
        } catch (UnknownKeyException ex) {
            //skip
        }
    }

    private YFlow verifyYFlowStatus(String yFlowId, FlowStatus expectedStatus,
                                    FlowStatus expectedFirstSubFlowStatus, FlowStatus expectedSecondSubFlowStatus) {
        YFlow flow = getYFlow(yFlowId);
        assertEquals(expectedStatus, flow.getStatus());

        Set<FlowStatus> expectedSubFlowStatuses = Stream.of(expectedFirstSubFlowStatus, expectedSecondSubFlowStatus)
                .collect(Collectors.toSet());
        Set<FlowStatus> actualSubFlowStatuses = flow.getSubFlows().stream()
                .map(YSubFlow::getFlow)
                .map(Flow::getStatus)
                .collect(Collectors.toSet());

        assertEquals(expectedSubFlowStatuses, actualSubFlowStatuses);

        return flow;
    }

    private void handleSpeakerCommandsAndFailInstall(YFlowPathSwapService service, String yFlowFsmKey,
                                                     String commandFlowIdToFail) {
        handleSpeakerRequests(request -> {
            SpeakerResponse commandResponse;
            if (request instanceof FlowSegmentRequest) {
                FlowSegmentRequest flowSegmentRequest = (FlowSegmentRequest) request;
                commandResponse = flowSegmentRequest.isInstallRequest()
                        && flowSegmentRequest.getMetadata().getFlowId().equals(commandFlowIdToFail)
                        ? buildErrorSpeakerResponse(flowSegmentRequest)
                        : buildSuccessfulSpeakerResponse(flowSegmentRequest);
            } else {
                BaseSpeakerCommandsRequest speakerCommandsRequest = (BaseSpeakerCommandsRequest) request;
                commandResponse = request instanceof InstallSpeakerCommandsRequest
                        ? buildErrorYFlowSpeakerResponse(speakerCommandsRequest)
                        : buildSuccessfulYFlowSpeakerResponse(speakerCommandsRequest);
            }
            handleAsyncResponse(service, yFlowFsmKey, commandResponse);
        });
    }

    private void handleSpeakerCommandsAndTimeoutInstall(YFlowPathSwapService service, String yFlowFsmKey) {
        handleSpeakerRequests(request -> {
            SpeakerResponse commandResponse;
            if (request instanceof FlowSegmentRequest) {
                FlowSegmentRequest flowSegmentRequest = (FlowSegmentRequest) request;
                commandResponse = buildSuccessfulSpeakerResponse(flowSegmentRequest);
                handleAsyncResponse(service, yFlowFsmKey, commandResponse);
            } else {
                BaseSpeakerCommandsRequest speakerCommandsRequest = (BaseSpeakerCommandsRequest) request;
                if (speakerCommandsRequest instanceof InstallSpeakerCommandsRequest) {
                    try {
                        service.handleTimeout(yFlowFsmKey);
                    } catch (UnknownKeyException ex) {
                        //skip
                    }
                } else {
                    commandResponse = buildSuccessfulYFlowSpeakerResponse(speakerCommandsRequest);
                    handleAsyncResponse(service, yFlowFsmKey, commandResponse);
                }
            }
        });
    }

    private void verifyPathSwapped(YFlow origin, YFlow result) {
        assertEquals(origin.getYPoint(), result.getProtectedPathYPoint());
        assertEquals(origin.getProtectedPathYPoint(), result.getYPoint());

        origin.getSubFlows().forEach(subFlow -> {
            Flow originSubFlow = subFlow.getFlow();
            Flow resultSubFlow = result.getSubFlows().stream()
                    .filter(sf -> sf.getSubFlowId().equals(originSubFlow.getFlowId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Can't find the result sub-flow: " + originSubFlow))
                    .getFlow();

            assertEquals(originSubFlow.getProtectedForwardPathId(), resultSubFlow.getForwardPathId());
            assertEquals(originSubFlow.getForwardPathId(), resultSubFlow.getProtectedForwardPathId());
            assertEquals(originSubFlow.getProtectedReversePathId(), resultSubFlow.getReversePathId());
            assertEquals(originSubFlow.getReversePathId(), resultSubFlow.getProtectedReversePathId());
        });
    }

    private void verifyPathNotSwapped(YFlow origin, YFlow result) {
        assertEquals(origin.getYPoint(), result.getYPoint());
        assertEquals(origin.getProtectedPathYPoint(), result.getProtectedPathYPoint());

        origin.getSubFlows().forEach(subFlow -> {
            Flow originSubFlow = subFlow.getFlow();
            Flow resultSubFlow = result.getSubFlows().stream()
                    .filter(sf -> sf.getSubFlowId().equals(originSubFlow.getFlowId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Can't find the result sub-flow: " + originSubFlow))
                    .getFlow();

            assertEquals(originSubFlow.getForwardPathId(), resultSubFlow.getForwardPathId());
            assertEquals(originSubFlow.getProtectedForwardPathId(), resultSubFlow.getProtectedForwardPathId());
            assertEquals(originSubFlow.getReversePathId(), resultSubFlow.getReversePathId());
            assertEquals(originSubFlow.getProtectedReversePathId(), resultSubFlow.getProtectedReversePathId());
        });
    }

    protected void verifyNorthboundSuccessResponse(FlowGenericCarrier carrierMock) {
        verifyNorthboundSuccessResponse(carrierMock, YFlowResponse.class);
    }

    private FlowPathSwapService makeFlowPathSwapService(int retriesLimit) {
        return new FlowPathSwapService(flowPathSwapHubCarrier, persistenceManager, ruleManager, flowResourcesManager,
                retriesLimit);
    }

    private YFlowPathSwapService makeYFlowPathSwapService(int retriesLimit) {
        return makeYFlowPathSwapService(makeFlowPathSwapService(retriesLimit), retriesLimit);
    }

    private YFlowPathSwapService makeYFlowPathSwapService(FlowPathSwapService flowPathSwapService, int retriesLimit) {
        return new YFlowPathSwapService(yFlowPathSwapHubCarrier, persistenceManager, ruleManager, flowPathSwapService,
                retriesLimit);
    }
}
