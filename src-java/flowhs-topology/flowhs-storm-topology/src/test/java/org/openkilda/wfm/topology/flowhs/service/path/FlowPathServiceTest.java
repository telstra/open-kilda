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

package org.openkilda.wfm.topology.flowhs.service.path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import org.openkilda.floodlight.api.request.EgressFlowSegmentInstallRequest;
import org.openkilda.floodlight.api.request.EgressFlowSegmentRemoveRequest;
import org.openkilda.floodlight.api.request.EgressFlowSegmentVerifyRequest;
import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.request.IngressFlowSegmentInstallRequest;
import org.openkilda.floodlight.api.request.IngressFlowSegmentRemoveRequest;
import org.openkilda.floodlight.api.request.IngressFlowSegmentVerifyRequest;
import org.openkilda.floodlight.api.request.SpeakerRequest;
import org.openkilda.floodlight.api.request.TransitFlowSegmentInstallRequest;
import org.openkilda.floodlight.api.request.TransitFlowSegmentRemoveRequest;
import org.openkilda.floodlight.api.request.TransitFlowSegmentVerifyRequest;
import org.openkilda.floodlight.api.request.factory.EgressFlowSegmentRequestFactory;
import org.openkilda.floodlight.api.request.factory.IngressFlowSegmentRequestFactory;
import org.openkilda.floodlight.api.request.factory.TransitFlowSegmentRequestFactory;
import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse.ErrorCode;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.exception.UnknownKeyException;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathChunk;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathOperationConfig;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathReference;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathRequest;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathRequest.PathChunkType;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathResult;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathResultCode;
import org.openkilda.wfm.topology.utils.FlowSegmentRequestMetaFactory;

import com.google.common.base.Objects;
import lombok.Value;
import org.apache.commons.lang3.function.FailableConsumer;
import org.apache.storm.shade.com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;

@RunWith(MockitoJUnitRunner.class)
public class FlowPathServiceTest {
    private final FlowPathOperationConfig config = new FlowPathOperationConfig(3);

    private FlowSegmentRequestMetaFactory flowSegmentRequestMetaFactory;

    private FlowEndpoint ingressEndpoint;
    private FlowEndpoint egressEndpoint;
    private IngressFlowSegmentRequestFactory ingressSegment;
    private TransitFlowSegmentRequestFactory transitSegment;
    private EgressFlowSegmentRequestFactory egressSegment;

    @Mock
    private FlowPathCarrier carrier;

    private final Queue<SpeakerRequest> speakerRequests = new ArrayDeque<>();

    @Before
    public void setUp() throws Exception {
        flowSegmentRequestMetaFactory = newFlowSegmentRequestMetaFactory();

        ingressEndpoint = new FlowEndpoint(new SwitchId(1), 10, 101);
        egressEndpoint = new FlowEndpoint(new SwitchId(3), 11, 102);

        ingressSegment = flowSegmentRequestMetaFactory.produceIngressSegmentFactory(
                1000L, ingressEndpoint, 1, egressEndpoint.getSwitchId());
        transitSegment = flowSegmentRequestMetaFactory.produceTransitSegmentFactory(new SwitchId(2), 2, 3);
        egressSegment = flowSegmentRequestMetaFactory.produceEgressSegmentFactory(egressEndpoint, 4, ingressEndpoint);

        doAnswer(invocation -> {
            SpeakerRequest request = invocation.getArgument(0);
            speakerRequests.offer(request);
            return request;
        }).when(carrier).sendSpeakerRequest(any(SpeakerRequest.class));
    }

    // install

    @Test
    public void testInstall() throws Exception {
        FlowPathService service = newService();
        final String requestKey = "test-request-key";
        FlowPathRequest pathRequest = newTwoChunkFlowPathRequest();
        service.installPath(
                pathRequest, requestKey, config, new CommandContext("test-correlation-id"));

        handleSpeakerRequestAndProvideSuccessResponses(service, requestKey,
                // not ingress chunk
                new SpeakerFlowSegmentReference(TransitFlowSegmentInstallRequest.class, transitSegment.getSwitchId()),
                new SpeakerFlowSegmentReference(EgressFlowSegmentInstallRequest.class, egressSegment.getSwitchId()),
                new SpeakerFlowSegmentReference(TransitFlowSegmentVerifyRequest.class, transitSegment.getSwitchId()),
                new SpeakerFlowSegmentReference(EgressFlowSegmentVerifyRequest.class, egressSegment.getSwitchId()),
                // ingress chunk
                new SpeakerFlowSegmentReference(IngressFlowSegmentInstallRequest.class, ingressSegment.getSwitchId()),
                new SpeakerFlowSegmentReference(IngressFlowSegmentVerifyRequest.class, ingressSegment.getSwitchId()));

        verifyRequestIsOver(pathRequest.getReference(), FlowPathResultCode.SUCCESS);

        Assert.assertTrue(service.deactivate());
    }

    @Test
    public void testInstallSingleChunk() throws Exception {
        FlowPathRequest pathRequest = FlowPathRequest.builder()
                .flowId(flowSegmentRequestMetaFactory.getFlowId())
                .pathId(new PathId("test-path-id"))
                .canRevertOnError(true)
                .pathChunk(new FlowPathChunk(PathChunkType.NOT_INGRESS, Arrays.asList(transitSegment, egressSegment)))
                .build();

        FlowPathService service = newService();
        final String requestKey = "test-request-key";
        service.installPath(pathRequest, requestKey, config, new CommandContext("test-correlation-id"));

        handleSpeakerRequestAndProvideSuccessResponses(service, requestKey,
                // not ingress chunk
                new SpeakerFlowSegmentReference(TransitFlowSegmentInstallRequest.class, transitSegment.getSwitchId()),
                new SpeakerFlowSegmentReference(EgressFlowSegmentInstallRequest.class, egressSegment.getSwitchId()),
                new SpeakerFlowSegmentReference(TransitFlowSegmentVerifyRequest.class, transitSegment.getSwitchId()),
                new SpeakerFlowSegmentReference(EgressFlowSegmentVerifyRequest.class, egressSegment.getSwitchId()));

        verifyRequestIsOver(pathRequest.getReference(), FlowPathResultCode.SUCCESS);

        Assert.assertTrue(service.deactivate());
    }

    @Test
    public void testInstallFailsFirstChunk() throws Exception {
        FlowPathService service = newService();
        final String requestKey = "test-request-key";
        FlowPathRequest pathRequest = newTwoChunkFlowPathRequest();
        service.installPath(
                pathRequest, requestKey, config, new CommandContext("test-correlation-id"));

        SpeakerFlowSegmentReference installTransitSegmentReference = new SpeakerFlowSegmentReference(
                TransitFlowSegmentInstallRequest.class, transitSegment.getSwitchId());
        handleSpeakerRequests(
                // not ingress
                request -> provideFlowSegmentSuccessResultExceptSpecific(
                        service, installTransitSegmentReference, request, requestKey),
                installTransitSegmentReference,  // attempt #1
                new SpeakerFlowSegmentReference(EgressFlowSegmentInstallRequest.class, egressSegment.getSwitchId()),
                installTransitSegmentReference,  // attempt #2
                installTransitSegmentReference,  // attempt #3
                // revert
                new SpeakerFlowSegmentReference(TransitFlowSegmentRemoveRequest.class, transitSegment.getSwitchId()),
                new SpeakerFlowSegmentReference(EgressFlowSegmentRemoveRequest.class, egressSegment.getSwitchId()));

        verifyRequestIsOver(pathRequest.getReference(), FlowPathResultCode.SPEAKER_ERROR);

        Assert.assertTrue(service.deactivate());
    }

    @Test
    public void testInstallFailsSecondChunk() throws Exception {
        FlowPathService service = newService();
        final String requestKey = "test-request-key";
        FlowPathRequest pathRequest = newTwoChunkFlowPathRequest();
        service.installPath(
                pathRequest, requestKey, config, new CommandContext("test-correlation-id"));

        SpeakerFlowSegmentReference installIngressSegmentReference = new SpeakerFlowSegmentReference(
                IngressFlowSegmentInstallRequest.class, ingressSegment.getSwitchId());
        handleSpeakerRequests(
                request -> provideFlowSegmentSuccessResultExceptSpecific(
                        service, installIngressSegmentReference, request, requestKey),
                // not ingress chunk
                new SpeakerFlowSegmentReference(TransitFlowSegmentInstallRequest.class, transitSegment.getSwitchId()),
                new SpeakerFlowSegmentReference(EgressFlowSegmentInstallRequest.class, egressSegment.getSwitchId()),
                new SpeakerFlowSegmentReference(TransitFlowSegmentVerifyRequest.class, transitSegment.getSwitchId()),
                new SpeakerFlowSegmentReference(EgressFlowSegmentVerifyRequest.class, egressSegment.getSwitchId()),

                // ingress chunk
                installIngressSegmentReference,  // attempt #1
                installIngressSegmentReference,  // attempt #2
                installIngressSegmentReference,  // attempt #3

                // revert
                new SpeakerFlowSegmentReference(IngressFlowSegmentRemoveRequest.class, ingressSegment.getSwitchId()),
                new SpeakerFlowSegmentReference(TransitFlowSegmentRemoveRequest.class, transitSegment.getSwitchId()),
                new SpeakerFlowSegmentReference(EgressFlowSegmentRemoveRequest.class, egressSegment.getSwitchId()));

        verifyRequestIsOver(pathRequest.getReference(), FlowPathResultCode.SPEAKER_ERROR);

        Assert.assertTrue(service.deactivate());
    }

    @Test
    public void testInstallFailsVerifyFirstChunk() throws Exception {
        FlowPathService service = newService();
        final String requestKey = "test-request-key";
        FlowPathRequest pathRequest = newTwoChunkFlowPathRequest();
        service.installPath(
                pathRequest, requestKey, config, new CommandContext("test-correlation-id"));

        SpeakerFlowSegmentReference verifyTransitSegmentReference = new SpeakerFlowSegmentReference(
                TransitFlowSegmentVerifyRequest.class, transitSegment.getSwitchId());
        handleSpeakerRequests(
                // not ingress
                request -> provideFlowSegmentSuccessResultExceptSpecific(
                        service, verifyTransitSegmentReference, request, requestKey),
                new SpeakerFlowSegmentReference(TransitFlowSegmentInstallRequest.class, transitSegment.getSwitchId()),
                new SpeakerFlowSegmentReference(EgressFlowSegmentInstallRequest.class, egressSegment.getSwitchId()),
                verifyTransitSegmentReference,  // attempt #1
                new SpeakerFlowSegmentReference(EgressFlowSegmentVerifyRequest.class, egressSegment.getSwitchId()),
                verifyTransitSegmentReference,  // attempt #2
                verifyTransitSegmentReference,  // attempt #3
                // revert
                new SpeakerFlowSegmentReference(TransitFlowSegmentRemoveRequest.class, transitSegment.getSwitchId()),
                new SpeakerFlowSegmentReference(EgressFlowSegmentRemoveRequest.class, egressSegment.getSwitchId()));

        verifyRequestIsOver(pathRequest.getReference(), FlowPathResultCode.SPEAKER_ERROR);

        Assert.assertTrue(service.deactivate());
    }

    @Test
    public void testInstallFailsWithoutRevert() throws Exception {
        FlowPathService service = newService();
        final String requestKey = "test-request-key";
        FlowPathRequest pathRequest = FlowPathRequest.builder()
                .flowId(flowSegmentRequestMetaFactory.getFlowId())
                .pathId(new PathId("test-path-id"))
                .canRevertOnError(false)
                .pathChunk(new FlowPathChunk(PathChunkType.NOT_INGRESS, Arrays.asList(transitSegment, egressSegment)))
                .build();
        service.installPath(
                pathRequest, requestKey, config, new CommandContext("test-correlation-id"));

        SpeakerFlowSegmentReference installTransitSegmentReference = new SpeakerFlowSegmentReference(
                TransitFlowSegmentInstallRequest.class, transitSegment.getSwitchId());
        handleSpeakerRequests(
                request -> provideFlowSegmentSuccessResultExceptSpecific(
                        service, installTransitSegmentReference, request, requestKey),
                installTransitSegmentReference,  // attempt #1
                installTransitSegmentReference,  // attempt #2
                installTransitSegmentReference,  // attempt #3
                new SpeakerFlowSegmentReference(EgressFlowSegmentInstallRequest.class, egressSegment.getSwitchId()));

        verifyRequestIsOver(pathRequest.getReference(), FlowPathResultCode.SPEAKER_ERROR);

        Assert.assertTrue(service.deactivate());
    }

    // remove
    // TODO

    // utility/service

    private void verifyRequestIsOver(FlowPathReference reference, FlowPathResultCode resultCode) {
        Assert.assertTrue(speakerRequests.isEmpty());

        FlowPathResult result = new FlowPathResult(reference, resultCode);
        verify(carrier).processFlowPathOperationResults(eq(result));
    }

    private void handleSpeakerRequestAndProvideSuccessResponses(
            FlowPathService service, String requestKey, SpeakerFlowSegmentReference... expectedArray) throws Exception {
        handleSpeakerRequests(
                request -> service.handleSpeakerResponse(
                        requestKey, buildSuccessfulSpeakerResponse(request)), expectedArray);
    }

    private void provideFlowSegmentSuccessResultExceptSpecific(
            FlowPathService service, SpeakerFlowSegmentReference forceFailFor, SpeakerRequest request,
            String requestKey) throws UnknownKeyException {
        if (forceFailFor.isMatch(request)) {
            service.handleSpeakerResponse(requestKey, buildFailedSpeakerResponse(request, ErrorCode.UNKNOWN));
        } else {
            service.handleSpeakerResponse(requestKey, buildSuccessfulSpeakerResponse(request));
        }
    }

    private void handleSpeakerRequests(
            FailableConsumer<SpeakerRequest, Exception> handler, SpeakerFlowSegmentReference... expectedArray)
            throws Exception {
        List<SpeakerRequest> unexpected = new ArrayList<>();
        List<SpeakerFlowSegmentReference> expected = Lists.newArrayList(expectedArray);
        proceedSpeakerRequests(request -> {
            SpeakerFlowSegmentReference match = null;
            for (Iterator<SpeakerFlowSegmentReference> iterator = expected.iterator(); iterator.hasNext(); ) {
                SpeakerFlowSegmentReference reference = iterator.next();
                if (reference.isMatch(request)) {
                    iterator.remove();
                    match = reference;
                    break;
                }
            }

            if (match != null) {
                handler.accept(request);
            } else {
                unexpected.add(request);
            }
        });

        Assert.assertTrue(expected.isEmpty());
        Assert.assertTrue(unexpected.isEmpty());
    }

    private void proceedSpeakerRequests(FailableConsumer<SpeakerRequest, Exception> handler) throws Exception {
        for (SpeakerRequest entry = speakerRequests.poll(); entry != null; entry = speakerRequests.poll()) {
            handler.accept(entry);
        }
    }

    private SpeakerFlowSegmentResponse buildSuccessfulSpeakerResponse(SpeakerRequest request) {
        if (request instanceof FlowSegmentRequest) {
            return buildSuccessfulSpeakerResponse((FlowSegmentRequest) request);
        } else {
            throw new IllegalArgumentException(String.format(
                    "Expect %s as argument, but got %s",
                    FlowSegmentRequest.class.getName(), request.getClass().getName()));
        }
    }

    private SpeakerFlowSegmentResponse buildSuccessfulSpeakerResponse(FlowSegmentRequest request) {
        return SpeakerFlowSegmentResponse.builder()
                .messageContext(request.getMessageContext())
                .commandId(request.getCommandId())
                .metadata(request.getMetadata())
                .switchId(request.getSwitchId())
                .success(true)
                .build();
    }

    private FlowErrorResponse buildFailedSpeakerResponse(SpeakerRequest request, ErrorCode errorCode) {
        if (request instanceof FlowSegmentRequest) {
            return buildFailedSpeakerResponse((FlowSegmentRequest) request, errorCode);
        } else {
            throw new IllegalArgumentException(String.format(
                    "Expect %s as argument, but got %s",
                    FlowSegmentRequest.class.getName(), request.getClass().getName()));
        }
    }

    private FlowErrorResponse buildFailedSpeakerResponse(
            FlowSegmentRequest request, ErrorCode errorCode) {
        return FlowErrorResponse.errorBuilder()
                .messageContext(request.getMessageContext())
                .commandId(request.getCommandId())
                .metadata(request.getMetadata())
                .switchId(request.getSwitchId())
                .description("test-forced-error-response")
                .errorCode(errorCode)
                .build();
    }

    private FlowPathService newService() {
        FlowPathService service = new FlowPathService(carrier);
        service.activate();
        return service;
    }

    private FlowSegmentRequestMetaFactory newFlowSegmentRequestMetaFactory() {
        Cookie cookie = FlowSegmentCookie.builder()
                .flowEffectiveId(0x1234)
                .direction(FlowPathDirection.FORWARD)
                .build();
        return new FlowSegmentRequestMetaFactory("dummy-flow", cookie, 1001);
    }

    private FlowPathRequest newTwoChunkFlowPathRequest() {
        return FlowPathRequest.builder()
                .flowId(flowSegmentRequestMetaFactory.getFlowId())
                .pathId(new PathId("test-path-id"))
                .canRevertOnError(true)
                .pathChunk(new FlowPathChunk(PathChunkType.NOT_INGRESS, Arrays.asList(transitSegment, egressSegment)))
                .pathChunk(new FlowPathChunk(PathChunkType.INGRESS, Collections.singletonList(ingressSegment)))
                .build();
    }

    @Value
    private static class SpeakerFlowSegmentReference {
        Class<?> klass;
        SwitchId switchId;

        boolean isMatch(SpeakerRequest request) {
            return klass.isInstance(request) && Objects.equal(switchId, request.getSwitchId());
        }
    }
}
