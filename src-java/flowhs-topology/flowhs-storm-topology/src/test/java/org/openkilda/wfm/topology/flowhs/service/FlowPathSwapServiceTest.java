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

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.request.SpeakerRequest;
import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse.ErrorCode;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowStatus;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.RuleManagerImpl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class FlowPathSwapServiceTest extends AbstractFlowTest<SpeakerRequest> {
    private static final int SPEAKER_COMMAND_RETRIES_LIMIT = 0;

    @Mock
    private FlowPathSwapHubCarrier carrier;
    private RuleManager ruleManager;

    @Before
    public void setUp() {
        doAnswer(buildSpeakerRequestAnswer()).when(carrier).sendSpeakerRequest(any(SpeakerRequest.class));

        setupFlowRepositorySpy();
        setupFlowPathRepositorySpy();

        RuleManagerConfig ruleManagerConfig = configurationProvider.getConfiguration(RuleManagerConfig.class);
        ruleManager = spy(new RuleManagerImpl(ruleManagerConfig));
    }

    @Test
    public void shouldSuccessfullySwapFlowPaths() throws Exception {
        // given
        Flow origin = dummyFactory.makeFlowWithProtectedPath(flowSource, flowDestination,
                singletonList(islSourceDest), singletonList(islSourceDestAlt));

        // when
        FlowPathSwapService service = makeService();
        service.handleRequest(dummyRequestKey, commandContext, origin.getFlowId());

        verifyFlowStatus(origin.getFlowId(), FlowStatus.IN_PROGRESS);

        SpeakerRequest speakerRequest;
        while ((speakerRequest = requests.poll()) != null) {
            produceAsyncResponse(service, dummyRequestKey, speakerRequest);
        }

        // then
        Flow result = verifyFlowStatus(origin.getFlowId(), FlowStatus.UP);
        verifyPathSwapped(origin, result);
        verifyNorthboundSuccessResponse(carrier);
    }

    @Test
    public void shouldFailSwapOnUnsuccessfulInstallation() throws Exception {
        // given
        Flow origin = dummyFactory.makeFlowWithProtectedPath(flowSource, flowDestination,
                singletonList(islSourceDest), singletonList(islSourceDestAlt));

        // when
        FlowPathSwapService service = makeService();
        service.handleRequest(dummyRequestKey, commandContext, origin.getFlowId());

        verifyFlowStatus(origin.getFlowId(), FlowStatus.IN_PROGRESS);

        int failCounter = 1;
        SpeakerRequest speakerRequest;
        while ((speakerRequest = requests.poll()) != null) {
            if (speakerRequest instanceof FlowSegmentRequest) {
                FlowSegmentRequest flowSegmentRequest = (FlowSegmentRequest) speakerRequest;
                if (flowSegmentRequest.isInstallRequest() && failCounter > 0) {
                    service.handleAsyncResponse(dummyRequestKey, FlowErrorResponse.errorBuilder()
                            .messageContext(flowSegmentRequest.getMessageContext())
                            .errorCode(ErrorCode.UNKNOWN)
                            .description(injectedErrorMessage)
                            .commandId(flowSegmentRequest.getCommandId())
                            .metadata(flowSegmentRequest.getMetadata())
                            .switchId(flowSegmentRequest.getSwitchId())
                            .build());
                    failCounter--;
                } else {
                    service.handleAsyncResponse(dummyRequestKey, buildSpeakerResponse(flowSegmentRequest));
                }
            } else {
                fail();
            }
        }

        // then
        Flow result = verifyFlowStatus(origin.getFlowId(), FlowStatus.UP);
        verifyPathNotSwapped(origin, result);
        verifyNorthboundSuccessResponse(carrier);
    }

    @Test
    public void shouldFailSwapOnTimeoutDuringInstallation() throws Exception {
        // given
        Flow origin = dummyFactory.makeFlowWithProtectedPath(flowSource, flowDestination,
                singletonList(islSourceDest), singletonList(islSourceDestAlt));

        // when
        FlowPathSwapService service = makeService();
        service.handleRequest(dummyRequestKey, commandContext, origin.getFlowId());

        verifyFlowStatus(origin.getFlowId(), FlowStatus.IN_PROGRESS);

        service.handleTimeout(dummyRequestKey);

        SpeakerRequest speakerRequest;
        while ((speakerRequest = requests.poll()) != null) {
            produceAsyncResponse(service, dummyRequestKey, speakerRequest);
        }

        // then
        Flow result = verifyFlowStatus(origin.getFlowId(), FlowStatus.UP);
        verifyPathNotSwapped(origin, result);
        verifyNorthboundSuccessResponse(carrier);
    }

    private void produceAsyncResponse(FlowPathSwapService service, String fsmKey, SpeakerRequest speakerRequest)
            throws Exception {
        SpeakerResponse commandResponse;
        if (speakerRequest instanceof FlowSegmentRequest) {
            commandResponse = buildSpeakerResponse((FlowSegmentRequest) speakerRequest);
        } else {
            BaseSpeakerCommandsRequest speakerCommandsRequest = (BaseSpeakerCommandsRequest) speakerRequest;
            commandResponse = buildSuccessfulYFlowSpeakerResponse(speakerCommandsRequest);
        }
        service.handleAsyncResponse(fsmKey, commandResponse);
    }

    private void verifyPathSwapped(Flow origin, Flow result) {
        assertEquals(origin.getProtectedForwardPathId(), result.getForwardPathId());
        assertEquals(origin.getForwardPathId(), result.getProtectedForwardPathId());
        assertEquals(origin.getProtectedReversePathId(), result.getReversePathId());
        assertEquals(origin.getReversePathId(), result.getProtectedReversePathId());
    }

    private void verifyPathNotSwapped(Flow origin, Flow result) {
        assertEquals(origin.getForwardPathId(), result.getForwardPathId());
        assertEquals(origin.getProtectedForwardPathId(), result.getProtectedForwardPathId());
        assertEquals(origin.getReversePathId(), result.getReversePathId());
        assertEquals(origin.getProtectedReversePathId(), result.getProtectedReversePathId());
    }

    @Override
    protected void verifyNorthboundSuccessResponse(FlowGenericCarrier carrierMock) {
        verifyNorthboundSuccessResponse(carrierMock, FlowResponse.class);
    }

    private FlowPathSwapService makeService() {
        return new FlowPathSwapService(carrier, persistenceManager, ruleManager, flowResourcesManager,
                SPEAKER_COMMAND_RETRIES_LIMIT);
    }
}
