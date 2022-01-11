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

package org.openkilda.wfm.topology.flowhs.service.yflow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.request.SpeakerRequest;
import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.DeleteSpeakerCommandsRequest;
import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.messaging.command.yflow.YFlowDeleteRequest;
import org.openkilda.messaging.command.yflow.YFlowResponse;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.FlowStatus;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.exception.UnknownKeyException;
import org.openkilda.wfm.topology.flowhs.service.AbstractYFlowTest;
import org.openkilda.wfm.topology.flowhs.service.FlowDeleteService;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class YFlowDeleteServiceTest extends AbstractYFlowTest<SpeakerRequest> {
    @Mock
    private FlowGenericCarrier flowDeleteHubCarrier;
    @Mock
    private FlowGenericCarrier yFlowDeleteHubCarrier;

    @Before
    public void init() {
        doAnswer(buildSpeakerRequestAnswer())
                .when(flowDeleteHubCarrier).sendSpeakerRequest(any(SpeakerRequest.class));
        doAnswer(buildSpeakerRequestAnswer())
                .when(yFlowDeleteHubCarrier).sendSpeakerRequest(any(SpeakerRequest.class));
    }

    @Test
    public void shouldDeleteFlowWithTransitSwitches() throws DuplicateKeyException {
        // given
        String yFlowId = "test_y_flow_1";
        createYFlowViaTransit(yFlowId);

        // when
        processRequestAndSpeakerCommands(yFlowId);
        //then
        verifyNorthboundSuccessResponse(yFlowDeleteHubCarrier, YFlowResponse.class);
        verifyYFlowIsAbsent(yFlowId);
    }

    @Test
    public void shouldDeleteFlowWithProtectedPath() throws DuplicateKeyException {
        // given
        String yFlowId = "test_y_flow_2";
        createYFlowWithProtected(yFlowId);

        // when
        processRequestAndSpeakerCommands(yFlowId);
        //then
        verifyNorthboundSuccessResponse(yFlowDeleteHubCarrier, YFlowResponse.class);
        verifyYFlowIsAbsent(yFlowId);
    }

    @Test
    public void shouldFailIfNoFlowExists() throws DuplicateKeyException {
        String yFlowId = "unknown_yflow";

        // when
        FlowDeleteService flowDeleteService = makeFlowDeleteService(0);
        YFlowDeleteService service = makeYFlowDeleteService(flowDeleteService, 0);
        YFlowDeleteRequest yFlowRequest = new YFlowDeleteRequest(yFlowId);
        service.handleRequest(yFlowRequest.getYFlowId(), new CommandContext(), yFlowRequest);
        //then
        verifyNorthboundErrorResponse(yFlowDeleteHubCarrier, ErrorType.NOT_FOUND);
    }

    @Test
    public void shouldDeleteOnUnsuccessfulMeterRemoval() throws DuplicateKeyException {
        // given
        String yFlowId = "test_y_flow_10";
        createYFlowViaTransit(yFlowId);

        YFlowDeleteService service = makeYFlowDeleteService(0);

        // when
        service.handleRequest(yFlowId, new CommandContext(), new YFlowDeleteRequest(yFlowId));
        verifyYFlowStatus(yFlowId, FlowStatus.IN_PROGRESS);
        // and
        handleSpeakerCommandsAndFailRemove(service, yFlowId, yFlowId);

        //then
        verifyNorthboundSuccessResponse(yFlowDeleteHubCarrier, YFlowResponse.class);
        verifyYFlowIsAbsent(yFlowId);
    }

    @Test
    public void shouldDeleteOnTimeoutDuringMeterRemoval() throws DuplicateKeyException {
        // given
        String yFlowId = "test_y_flow_11";
        createYFlowViaTransit(yFlowId);

        YFlowDeleteService service = makeYFlowDeleteService(0);

        // when
        service.handleRequest(yFlowId, new CommandContext(), new YFlowDeleteRequest(yFlowId));
        verifyYFlowStatus(yFlowId, FlowStatus.IN_PROGRESS);
        // and
        handleSpeakerCommandsAndTimeoutRemove(service, yFlowId);

        //then
        verifyNorthboundSuccessResponse(yFlowDeleteHubCarrier, YFlowResponse.class);
        verifyYFlowIsAbsent(yFlowId);
    }

    private void processRequestAndSpeakerCommands(String yFlowId) throws DuplicateKeyException {
        YFlowDeleteRequest yFlowRequest = new YFlowDeleteRequest(yFlowId);

        YFlowDeleteService service = makeYFlowDeleteService(0);
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

    private void handleAsyncResponse(YFlowDeleteService service,
                                     String yFlowFsmKey, SpeakerResponse commandResponse) {
        try {
            service.handleAsyncResponse(yFlowFsmKey, commandResponse);
        } catch (UnknownKeyException ex) {
            //skip
        }
    }

    private void handleSpeakerCommandsAndFailRemove(YFlowDeleteService yFlowDeleteService, String yFlowFsmKey,
                                                    String commandFlowIdToFail) {
        handleSpeakerRequests(request -> {
            SpeakerResponse commandResponse;
            if (request instanceof FlowSegmentRequest) {
                FlowSegmentRequest flowSegmentRequest = (FlowSegmentRequest) request;
                commandResponse = flowSegmentRequest.isRemoveRequest()
                        && flowSegmentRequest.getMetadata().getFlowId().equals(commandFlowIdToFail)
                        ? buildErrorSpeakerResponse(flowSegmentRequest)
                        : buildSuccessfulSpeakerResponse(flowSegmentRequest);
            } else {
                BaseSpeakerCommandsRequest speakerCommandsRequest = (BaseSpeakerCommandsRequest) request;
                commandResponse = request instanceof DeleteSpeakerCommandsRequest
                        ? buildErrorYFlowSpeakerResponse(speakerCommandsRequest)
                        : buildSuccessfulYFlowSpeakerResponse(speakerCommandsRequest);
            }
            handleAsyncResponse(yFlowDeleteService, yFlowFsmKey, commandResponse);
        });
    }

    private void handleSpeakerCommandsAndTimeoutRemove(YFlowDeleteService yFlowDeleteService, String yFlowFsmKey) {
        handleSpeakerRequests(request -> {
            SpeakerResponse commandResponse;
            if (request instanceof FlowSegmentRequest) {
                FlowSegmentRequest flowSegmentRequest = (FlowSegmentRequest) request;
                commandResponse = buildSuccessfulSpeakerResponse(flowSegmentRequest);
                handleAsyncResponse(yFlowDeleteService, yFlowFsmKey, commandResponse);
            } else {
                BaseSpeakerCommandsRequest speakerCommandsRequest = (BaseSpeakerCommandsRequest) request;
                if (speakerCommandsRequest instanceof DeleteSpeakerCommandsRequest) {
                    try {
                        yFlowDeleteService.handleTimeout(yFlowFsmKey);
                    } catch (UnknownKeyException ex) {
                        //skip
                    }
                } else {
                    commandResponse = buildSuccessfulYFlowSpeakerResponse(speakerCommandsRequest);
                    handleAsyncResponse(yFlowDeleteService, yFlowFsmKey, commandResponse);
                }
            }
        });
    }

    private FlowDeleteService makeFlowDeleteService(int retriesLimit) {
        return new FlowDeleteService(flowDeleteHubCarrier, persistenceManager,
                flowResourcesManager, retriesLimit);
    }

    private YFlowDeleteService makeYFlowDeleteService(int retriesLimit) {
        return makeYFlowDeleteService(makeFlowDeleteService(retriesLimit), retriesLimit);
    }

    private YFlowDeleteService makeYFlowDeleteService(FlowDeleteService flowDeleteService,
                                                      int retriesLimit) {
        return new YFlowDeleteService(yFlowDeleteHubCarrier, persistenceManager, flowResourcesManager, ruleManager,
                flowDeleteService, retriesLimit);
    }
}
