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

package org.openkilda.northbound.service.impl;

import static org.openkilda.messaging.Utils.FLOW_ID;
import static org.openkilda.northbound.utils.async.AsyncUtils.collectResponses;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.FlowCreateRequest;
import org.openkilda.messaging.command.flow.FlowDeleteRequest;
import org.openkilda.messaging.command.flow.FlowPingRequest;
import org.openkilda.messaging.command.flow.FlowReadRequest;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.command.flow.FlowUpdateRequest;
import org.openkilda.messaging.command.flow.FlowsDumpRequest;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowHistoryData;
import org.openkilda.messaging.info.flow.FlowInfoData;
import org.openkilda.messaging.info.flow.FlowOperation;
import org.openkilda.messaging.info.flow.FlowPingResponse;
import org.openkilda.messaging.info.flow.FlowReadResponse;
import org.openkilda.messaging.info.flow.FlowRerouteResponse;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.messaging.info.flow.FlowStatusResponse;
import org.openkilda.messaging.info.meter.FlowMeterEntries;
import org.openkilda.messaging.model.BidirectionalFlowDto;
import org.openkilda.messaging.model.FlowDto;
import org.openkilda.messaging.nbtopology.request.FlowPatchRequest;
import org.openkilda.messaging.nbtopology.request.FlowValidationRequest;
import org.openkilda.messaging.nbtopology.request.GetFlowHistoryRequest;
import org.openkilda.messaging.nbtopology.request.GetFlowPathRequest;
import org.openkilda.messaging.nbtopology.request.MeterModifyRequest;
import org.openkilda.messaging.nbtopology.response.FlowValidationResponse;
import org.openkilda.messaging.nbtopology.response.GetFlowPathResponse;
import org.openkilda.messaging.payload.flow.DiverseGroupPayload;
import org.openkilda.messaging.payload.flow.FlowCreatePayload;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowReroutePayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.messaging.payload.flow.FlowUpdatePayload;
import org.openkilda.messaging.payload.flow.GroupFlowPathPayload;
import org.openkilda.messaging.payload.history.FlowEventPayload;
import org.openkilda.northbound.converter.FlowMapper;
import org.openkilda.northbound.dto.BatchResults;
import org.openkilda.northbound.dto.v1.flows.FlowPatchDto;
import org.openkilda.northbound.dto.v1.flows.FlowValidationDto;
import org.openkilda.northbound.dto.v1.flows.PingInput;
import org.openkilda.northbound.dto.v1.flows.PingOutput;
import org.openkilda.northbound.messaging.MessagingChannel;
import org.openkilda.northbound.service.FlowService;
import org.openkilda.northbound.utils.CorrelationIdFactory;
import org.openkilda.northbound.utils.RequestCorrelationId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Manages operations with flows.
 */
@Service
public class FlowServiceImpl implements FlowService {
    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(FlowServiceImpl.class);

    /**
     * The kafka topic for the flow topology.
     */
    @Value("#{kafkaTopicsConfig.getFlowTopic()}")
    private String topic;

    @Value("#{kafkaTopicsConfig.getTopoNbTopic()}")
    private String nbworkerTopic;

    /**
     * The kafka topic for `ping` topology.
     */
    @Value("#{kafkaTopicsConfig.getPingTopic()}")
    private String pingTopic;

    @Value("${neo4j.uri}")
    private String neoUri;

    @Value("${neo4j.user}")
    private String neoUser;

    @Value("${neo4j.password}")
    private String neoPswd;

    @Autowired
    private FlowMapper flowMapper;

    @Autowired
    private MessagingChannel messagingChannel;

    @Autowired
    private CorrelationIdFactory idFactory;

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowPayload> createFlow(final FlowCreatePayload input) {
        final String correlationId = RequestCorrelationId.getId();
        logger.info("Create flow: {}", input);

        FlowCreateRequest payload = new FlowCreateRequest(new FlowDto(input), input.getDiverseFlowId());
        CommandMessage request = new CommandMessage(
                payload, System.currentTimeMillis(), correlationId, Destination.WFM);

        return messagingChannel.sendAndGet(topic, request)
                .thenApply(FlowResponse.class::cast)
                .thenApply(FlowResponse::getPayload)
                .thenApply(flowMapper::toFlowOutput);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowPayload> getFlow(final String id) {
        logger.debug("Get flow request for flow {}", id);

        return getBidirectionalFlow(id, RequestCorrelationId.getId())
                .thenApply(BidirectionalFlowDto::getForward)
                .thenApply(flowMapper::toFlowOutput);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowPayload> updateFlow(final FlowUpdatePayload input) {
        final String correlationId = RequestCorrelationId.getId();
        logger.info("Update flow request for flow {}", input.getId());

        FlowUpdateRequest payload = new FlowUpdateRequest(new FlowDto(input), input.getDiverseFlowId());
        CommandMessage request = new CommandMessage(
                payload, System.currentTimeMillis(), correlationId, Destination.WFM);

        return messagingChannel.sendAndGet(topic, request)
                .thenApply(FlowResponse.class::cast)
                .thenApply(FlowResponse::getPayload)
                .thenApply(flowMapper::toFlowOutput);
    }

    @Override
    public CompletableFuture<FlowPayload> patchFlow(String flowId, FlowPatchDto flowPatchDto) {
        logger.info("Patch flow request for flow {}", flowId);

        FlowDto flowDto = flowMapper.toFlowDto(flowPatchDto);
        flowDto.setFlowId(flowId);
        CommandMessage request = new CommandMessage(new FlowPatchRequest(flowDto), System.currentTimeMillis(),
                RequestCorrelationId.getId());

        return messagingChannel.sendAndGet(nbworkerTopic, request)
                .thenApply(FlowResponse.class::cast)
                .thenApply(FlowResponse::getPayload)
                .thenApply(flowMapper::toFlowOutput);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<List<FlowPayload>> getAllFlows() {
        final String correlationId = RequestCorrelationId.getId();
        logger.debug("Get flows request processing");
        FlowsDumpRequest data = new FlowsDumpRequest();
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);

        return messagingChannel.sendAndGetChunked(topic, request)
                .thenApply(result -> result.stream()
                        .map(FlowReadResponse.class::cast)
                        .map(FlowReadResponse::getPayload)
                        .map(BidirectionalFlowDto::getForward)
                        .map(flowMapper::toFlowOutput)
                        .collect(Collectors.toList()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<List<FlowPayload>> deleteAllFlows() {
        CompletableFuture<List<FlowPayload>> result = new CompletableFuture<>();
        logger.warn("Delete all flows request");
        // TODO: Need a getFlowIDs .. since that is all we need
        CompletableFuture<List<FlowPayload>> getFlowsStage = this.getAllFlows();

        getFlowsStage.thenApply(flows -> {
            List<CompletableFuture<?>> deletionRequests = new ArrayList<>();
            for (int i = 0; i < flows.size(); i++) {
                String requestId = idFactory.produceChained(String.valueOf(i));
                FlowPayload flow = flows.get(i);
                deletionRequests.add(sendDeleteFlow(flow.getId(), requestId));
            }
            return deletionRequests;
        }).thenApply(requests -> collectResponses(requests, FlowPayload.class)
                .thenApply(result::complete));

        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowPayload> deleteFlow(final String id) {
        logger.info("Delete flow request for flow: {}", id);
        final String correlationId = RequestCorrelationId.getId();

        return sendDeleteFlow(id, correlationId);
    }

    private CompletableFuture<FlowPayload> sendDeleteFlow(String flowId, String correlationId) {
        CommandMessage request = buildDeleteFlowCommand(flowId, correlationId);

        return messagingChannel.sendAndGet(topic, request)
                .thenApply(FlowResponse.class::cast)
                .thenApply(FlowResponse::getPayload)
                .thenApply(flowMapper::toFlowOutput);
    }

    /**
     * Non-blocking primitive .. just create and send delete request.
     *
     * @return the request
     */
    private CommandMessage buildDeleteFlowCommand(final String id, final String correlationId) {
        FlowDto flow = new FlowDto();
        flow.setFlowId(id);
        FlowDeleteRequest data = new FlowDeleteRequest(flow);
        return new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowIdStatusPayload> statusFlow(final String id) {
        logger.debug("Flow status request for flow: {}", id);
        return getBidirectionalFlow(id, RequestCorrelationId.getId())
                .thenApply(flowMapper::toFlowIdStatusPayload);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowPathPayload> pathFlow(final String id) {
        logger.debug("Flow path request for flow {}", id);
        final String correlationId = RequestCorrelationId.getId();

        GetFlowPathRequest data = new GetFlowPathRequest(id);
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId);

        return messagingChannel.sendAndGetChunked(nbworkerTopic, request)
                .thenApply(result -> result.stream()
                        .map(GetFlowPathResponse.class::cast)
                        .map(GetFlowPathResponse::getPayload)
                        .collect(Collectors.toList()))
                .thenApply(respList -> buildFlowPathPayload(respList, id));
    }

    private FlowPathPayload buildFlowPathPayload(List<GroupFlowPathPayload> paths, String flowId) {
        GroupFlowPathPayload flowPathPayload = paths.stream().filter(e -> e.getId().equals(flowId)).findAny().get();
        // fill main flow path
        FlowPathPayload payload = new FlowPathPayload();
        payload.setId(flowPathPayload.getId());
        payload.setForwardPath(flowPathPayload.getForwardPath());
        payload.setReversePath(flowPathPayload.getReversePath());

        // fill group paths
        if (paths.size() > 1) {
            DiverseGroupPayload groupPayload = new DiverseGroupPayload();
            groupPayload.setOverlappingSegments(flowPathPayload.getSegmentsStats());
            groupPayload.setOtherFlows(
                    paths.stream().filter(e -> !e.getId().equals(flowId)).collect(Collectors.toList()));

            payload.setDiverseGroupPayload(groupPayload);
        }
        return payload;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<BatchResults> unpushFlows(List<FlowInfoData> externalFlows, Boolean propagate,
                                                       Boolean verify) {
        FlowOperation op = (propagate) ? FlowOperation.UNPUSH_PROPAGATE : FlowOperation.UNPUSH;
        // TODO: ADD the VERIFY implementation
        return flowPushUnpush(externalFlows, op);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<BatchResults> pushFlows(List<FlowInfoData> externalFlows,
                                                     Boolean propagate, Boolean verify) {
        FlowOperation op = (propagate) ? FlowOperation.PUSH_PROPAGATE : FlowOperation.PUSH;
        // TODO: ADD the VERIFY implementation
        return flowPushUnpush(externalFlows, op);
    }

    /**
     * Reads {@link BidirectionalFlowDto} flow representation from the Storm.
     *
     * @return the bidirectional flow.
     */
    private CompletableFuture<BidirectionalFlowDto> getBidirectionalFlow(String flowId, String correlationId) {
        FlowReadRequest data = new FlowReadRequest(flowId);
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);

        return messagingChannel.sendAndGet(topic, request)
                .thenApply(FlowReadResponse.class::cast)
                .thenApply(FlowReadResponse::getPayload);
    }

    /**
     * There are only minor differences between push and unpush .. this utility function helps
     */
    private CompletableFuture<BatchResults> flowPushUnpush(List<FlowInfoData> externalFlows, FlowOperation op) {
        final String correlationId = RequestCorrelationId.getId();
        String collect = externalFlows.stream().map(FlowInfoData::getFlowId).collect(Collectors.joining());
        logger.debug("Flow {}: id: {}", op, collect);
        logger.debug("Size of list: {}", externalFlows.size());
        // First, send them all, then wait for all the responses.
        // Send the command to both Flow Topology and to TE
        List<CompletableFuture<?>> flowRequests = new ArrayList<>();    // used for error reporting, if needed
        for (int i = 0; i < externalFlows.size(); i++) {
            FlowInfoData data = externalFlows.get(i);
            data.setOperation(op);  // <-- this is what determines PUSH / UNPUSH
            String flowCorrelation = correlationId + "-FLOW-" + i;
            InfoMessage flowRequest =
                    new InfoMessage(data, System.currentTimeMillis(), flowCorrelation, Destination.WFM, null);
            flowRequests.add(messagingChannel.sendAndGet(topic, flowRequest));
        }

        FlowState expectedState;
        switch (op) {
            case PUSH:
                expectedState = FlowState.UP;
                break;
            case PUSH_PROPAGATE:
                expectedState = FlowState.IN_PROGRESS;
                break;
            default:
                expectedState = FlowState.DOWN;
        }

        CompletableFuture<List<String>> flowFailures = collectResponses(flowRequests, FlowStatusResponse.class)
                .thenApply(flows ->
                        flows.stream()
                                .map(FlowStatusResponse::getPayload)
                                .filter(payload -> payload.getStatus() != expectedState)
                                .map(payload -> "FAILURE (FlowTopo): Flow " + payload.getId()
                                        + " NOT in " + expectedState
                                        + " state: state = " + payload.getStatus())
                                .collect(Collectors.toList()));

        return flowFailures.thenApply(failures -> {
            BatchResults batchResults = new BatchResults(failures.size(),
                    externalFlows.size() - failures.size(), failures);
            logger.debug("Returned: {}", batchResults);
            return batchResults;
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowReroutePayload> rerouteFlow(String flowId) {
        logger.info("Reroute flow request for flow {}", flowId);
        return reroute(flowId, false);
    }

    @Override
    public CompletableFuture<FlowReroutePayload> syncFlow(String flowId) {
        logger.info("Forced reroute request for flow {}", flowId);
        return reroute(flowId, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<List<FlowValidationDto>> validateFlow(final String flowId) {
        logger.debug("Validate flow request for flow {}", flowId);
        CommandMessage message = new CommandMessage(new FlowValidationRequest(flowId),
                System.currentTimeMillis(), RequestCorrelationId.getId());

        return messagingChannel.sendAndGetChunked(nbworkerTopic, message)
                .thenApply(response -> response.stream()
                        .map(FlowValidationResponse.class::cast)
                        .map(flowMapper::toFlowValidationDto)
                        .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<PingOutput> pingFlow(String flowId, PingInput payload) {
        FlowPingRequest request = new FlowPingRequest(flowId, payload.getTimeoutMillis());

        final String correlationId = RequestCorrelationId.getId();
        CommandMessage message = new CommandMessage(
                request, System.currentTimeMillis(), correlationId, Destination.WFM);

        return messagingChannel.sendAndGet(pingTopic, message)
                .thenApply(FlowPingResponse.class::cast)
                .thenApply(flowMapper::toPingOutput);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowMeterEntries> modifyMeter(String flowId) {
        MeterModifyRequest request = new MeterModifyRequest(flowId);

        final String correlationId = RequestCorrelationId.getId();
        CommandMessage message = new CommandMessage(request, System.currentTimeMillis(), correlationId);
        return messagingChannel.sendAndGet(nbworkerTopic, message)
                .thenApply(FlowMeterEntries.class::cast);
    }

    private CompletableFuture<FlowReroutePayload> reroute(String flowId, boolean forced) {
        logger.debug("Reroute flow: {}={}, forced={}", FLOW_ID, flowId, forced);
        String correlationId = RequestCorrelationId.getId();
        FlowRerouteRequest payload = new FlowRerouteRequest(flowId, forced);
        CommandMessage command = new CommandMessage(
                payload, System.currentTimeMillis(), correlationId, Destination.WFM);

        return messagingChannel.sendAndGet(topic, command)
                .thenApply(FlowRerouteResponse.class::cast)
                .thenApply(response ->
                        flowMapper.toReroutePayload(flowId, response.getPayload(), response.isRerouted()));
    }

    @Override
    public CompletableFuture<List<FlowEventPayload>> listFlowEvents(String flowId,
                                                                    long timestampFrom,
                                                                    long timestampTo) {
        String correlationId = RequestCorrelationId.getId();
        GetFlowHistoryRequest request = GetFlowHistoryRequest.builder()
                .flowId(flowId)
                .timestampFrom(timestampFrom)
                .timestampTo(timestampTo)
                .build();
        CommandMessage command = new CommandMessage(request, System.currentTimeMillis(), correlationId);
        return messagingChannel.sendAndGet(nbworkerTopic, command)
                .thenApply(FlowHistoryData.class::cast)
                .thenApply(FlowHistoryData::getPayload);
    }

}
