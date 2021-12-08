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

package org.openkilda.northbound.service.impl;

import static java.lang.String.format;
import static org.openkilda.messaging.Utils.FLOW_ID;
import static org.openkilda.messaging.command.flow.FlowRerouteRequest.createManualFlowRerouteRequest;
import static org.openkilda.northbound.utils.async.AsyncUtils.collectResponses;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.CreateFlowLoopRequest;
import org.openkilda.messaging.command.flow.DeleteFlowLoopRequest;
import org.openkilda.messaging.command.flow.FlowDeleteRequest;
import org.openkilda.messaging.command.flow.FlowMirrorPointCreateRequest;
import org.openkilda.messaging.command.flow.FlowMirrorPointDeleteRequest;
import org.openkilda.messaging.command.flow.FlowPathSwapRequest;
import org.openkilda.messaging.command.flow.FlowPingRequest;
import org.openkilda.messaging.command.flow.FlowRequest;
import org.openkilda.messaging.command.flow.FlowRequest.Type;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.command.flow.SwapFlowEndpointRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.flow.FlowMirrorPointResponse;
import org.openkilda.messaging.info.flow.FlowPingResponse;
import org.openkilda.messaging.info.flow.FlowRerouteResponse;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.messaging.info.flow.SwapFlowResponse;
import org.openkilda.messaging.info.meter.FlowMeterEntries;
import org.openkilda.messaging.model.FlowDto;
import org.openkilda.messaging.model.FlowPatch;
import org.openkilda.messaging.model.FlowPathDto;
import org.openkilda.messaging.model.FlowPathDto.FlowProtectedPathDto;
import org.openkilda.messaging.nbtopology.request.FlowConnectedDeviceRequest;
import org.openkilda.messaging.nbtopology.request.FlowMirrorPointsDumpRequest;
import org.openkilda.messaging.nbtopology.request.FlowPatchRequest;
import org.openkilda.messaging.nbtopology.request.FlowReadRequest;
import org.openkilda.messaging.nbtopology.request.FlowValidationRequest;
import org.openkilda.messaging.nbtopology.request.FlowsDumpRequest;
import org.openkilda.messaging.nbtopology.request.GetFlowHistoryRequest;
import org.openkilda.messaging.nbtopology.request.GetFlowLoopsRequest;
import org.openkilda.messaging.nbtopology.request.GetFlowPathRequest;
import org.openkilda.messaging.nbtopology.request.GetFlowStatusTimestampsRequest;
import org.openkilda.messaging.nbtopology.request.MeterModifyRequest;
import org.openkilda.messaging.nbtopology.response.FlowLoopsResponse;
import org.openkilda.messaging.nbtopology.response.FlowMirrorPointsDumpResponse;
import org.openkilda.messaging.nbtopology.response.FlowValidationResponse;
import org.openkilda.messaging.nbtopology.response.GetFlowPathResponse;
import org.openkilda.messaging.payload.flow.DiverseGroupPayload;
import org.openkilda.messaging.payload.flow.FlowCreatePayload;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload.FlowProtectedPath;
import org.openkilda.messaging.payload.flow.FlowReroutePayload;
import org.openkilda.messaging.payload.flow.FlowResponsePayload;
import org.openkilda.messaging.payload.flow.FlowUpdatePayload;
import org.openkilda.messaging.payload.flow.GroupFlowPathPayload;
import org.openkilda.messaging.payload.history.FlowHistoryEntry;
import org.openkilda.messaging.payload.history.FlowStatusTimestampsEntry;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.converter.ConnectedDeviceMapper;
import org.openkilda.northbound.converter.FlowMapper;
import org.openkilda.northbound.converter.PathMapper;
import org.openkilda.northbound.dto.BatchResults;
import org.openkilda.northbound.dto.v1.flows.FlowConnectedDevicesResponse;
import org.openkilda.northbound.dto.v1.flows.FlowPatchDto;
import org.openkilda.northbound.dto.v1.flows.FlowValidationDto;
import org.openkilda.northbound.dto.v1.flows.PingInput;
import org.openkilda.northbound.dto.v1.flows.PingOutput;
import org.openkilda.northbound.dto.v2.flows.FlowHistoryStatusesResponse;
import org.openkilda.northbound.dto.v2.flows.FlowLoopResponse;
import org.openkilda.northbound.dto.v2.flows.FlowMirrorPointPayload;
import org.openkilda.northbound.dto.v2.flows.FlowMirrorPointResponseV2;
import org.openkilda.northbound.dto.v2.flows.FlowMirrorPointsResponseV2;
import org.openkilda.northbound.dto.v2.flows.FlowPatchV2;
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2;
import org.openkilda.northbound.dto.v2.flows.FlowRerouteResponseV2;
import org.openkilda.northbound.dto.v2.flows.FlowResponseV2;
import org.openkilda.northbound.dto.v2.flows.SwapFlowEndpointPayload;
import org.openkilda.northbound.messaging.MessagingChannel;
import org.openkilda.northbound.service.FlowService;
import org.openkilda.northbound.utils.CorrelationIdFactory;
import org.openkilda.northbound.utils.RequestCorrelationId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
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
     * The kafka topic for the new flow topology.
     */
    @Value("#{kafkaTopicsConfig.getFlowHsTopic()}")
    private String flowHsTopic;

    /**
     * The kafka topic for `nbWorker` topology.
     */
    @Value("#{kafkaTopicsConfig.getTopoNbTopic()}")
    private String nbworkerTopic;

    /**
     * The kafka topic for `reroute` topology.
     */
    @Value("#{kafkaTopicsConfig.getTopoRerouteTopic()}")
    private String rerouteTopic;

    /**
     * The kafka topic for `ping` topology.
     */
    @Value("#{kafkaTopicsConfig.getPingTopic()}")
    private String pingTopic;

    @Autowired
    private FlowMapper flowMapper;

    @Autowired
    private PathMapper pathMapper;

    @Autowired
    private ConnectedDeviceMapper connectedDeviceMapper;

    @Autowired
    private MessagingChannel messagingChannel;

    @Autowired
    private CorrelationIdFactory idFactory;

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowResponsePayload> createFlow(FlowCreatePayload request) {
        logger.info("Create flow: {}", request);

        final String correlationId = RequestCorrelationId.getId();

        FlowRequest flowRequest;
        try {
            flowRequest = flowMapper.toFlowCreateRequest(request);
        } catch (IllegalArgumentException e) {
            logger.error("Can not parse arguments: {}", e.getMessage(), e);
            throw new MessageException(correlationId, System.currentTimeMillis(), ErrorType.DATA_INVALID,
                    e.getMessage(), "Can not parse arguments of the create flow request");
        }

        CommandMessage command = new CommandMessage(flowRequest,
                System.currentTimeMillis(), correlationId, Destination.WFM);

        return messagingChannel.sendAndGet(flowHsTopic, command)
                .thenApply(FlowResponse.class::cast)
                .thenApply(FlowResponse::getPayload)
                .thenApply(flowMapper::toFlowResponseOutput);
    }

    @Override
    public CompletableFuture<FlowResponseV2> createFlow(FlowRequestV2 request) {
        logger.info("Processing flow creation: {}", request);

        final String correlationId = RequestCorrelationId.getId();
        FlowRequest flowRequest;

        try {
            flowRequest = flowMapper.toFlowCreateRequest(request);
        } catch (IllegalArgumentException e) {
            logger.error("Can not parse arguments: {}", e.getMessage(), e);
            throw new MessageException(correlationId, System.currentTimeMillis(), ErrorType.DATA_INVALID,
                    e.getMessage(), "Can not parse arguments of the create flow request");
        }

        CommandMessage command = new CommandMessage(flowRequest,
                System.currentTimeMillis(), correlationId, Destination.WFM);

        return messagingChannel.sendAndGet(flowHsTopic, command)
                .thenApply(FlowResponse.class::cast)
                .thenApply(FlowResponse::getPayload)
                .thenApply(flowMapper::toFlowResponseV2);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowResponsePayload> getFlow(final String id) {
        logger.debug("Get flow request for flow {}", id);
        return getFlowResponse(id, RequestCorrelationId.getId())
                .thenApply(flowMapper::toFlowResponseOutput);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowResponseV2> getFlowV2(final String id) {
        logger.debug("Get flow request for flow {}", id);
        FlowReadRequest data =
                new FlowReadRequest(id);
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(),
                RequestCorrelationId.getId(), Destination.WFM);
        return messagingChannel.sendAndGet(nbworkerTopic, request)
                .thenApply(FlowResponse.class::cast)
                .thenApply(FlowResponse::getPayload)
                .thenApply(flowMapper::toFlowResponseV2);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowResponsePayload> updateFlow(final FlowUpdatePayload request) {
        logger.info("Update flow request for flow {}", request.getId());

        final String correlationId = RequestCorrelationId.getId();
        FlowRequest updateRequest;

        try {
            updateRequest = flowMapper.toFlowUpdateRequest(request);
        } catch (IllegalArgumentException e) {
            logger.error("Can not parse arguments: {}", e.getMessage(), e);
            throw new MessageException(correlationId, System.currentTimeMillis(), ErrorType.DATA_INVALID,
                    e.getMessage(), "Can not parse arguments of the update flow request");
        }

        CommandMessage command = new CommandMessage(updateRequest,
                System.currentTimeMillis(), correlationId, Destination.WFM);

        return messagingChannel.sendAndGet(flowHsTopic, command)
                .thenApply(FlowResponse.class::cast)
                .thenApply(FlowResponse::getPayload)
                .thenApply(flowMapper::toFlowResponseOutput);
    }

    @Override
    public CompletableFuture<FlowResponseV2> updateFlow(FlowRequestV2 request) {
        logger.info("Processing flow update: {}", request);

        final String correlationId = RequestCorrelationId.getId();
        FlowRequest updateRequest;

        try {
            updateRequest = flowMapper.toFlowRequest(request).toBuilder().type(Type.UPDATE).build();
        } catch (IllegalArgumentException e) {
            logger.error("Can not parse arguments: {}", e.getMessage(), e);
            throw new MessageException(correlationId, System.currentTimeMillis(), ErrorType.DATA_INVALID,
                    e.getMessage(), "Can not parse arguments of the update flow request");
        }

        CommandMessage command = new CommandMessage(updateRequest,
                System.currentTimeMillis(), correlationId, Destination.WFM);

        return messagingChannel.sendAndGet(flowHsTopic, command)
                .thenApply(FlowResponse.class::cast)
                .thenApply(FlowResponse::getPayload)
                .thenApply(flowMapper::toFlowResponseV2);
    }

    @Override
    public CompletableFuture<FlowResponsePayload> patchFlow(String flowId, FlowPatchDto flowPatchDto) {
        logger.info("Patch flow request for flow {}", flowId);
        String correlationId = RequestCorrelationId.getId();

        FlowPatch flowPatch;
        try {
            flowPatch = flowMapper.toFlowPatch(flowPatchDto);
        } catch (IllegalArgumentException e) {
            logger.error("Can not parse arguments: {}", e.getMessage(), e);
            throw new MessageException(correlationId, System.currentTimeMillis(), ErrorType.DATA_INVALID,
                    e.getMessage(), "Can not parse arguments of the flow patch request");
        }
        flowPatch.setFlowId(flowId);
        CommandMessage request = new CommandMessage(new FlowPatchRequest(flowPatch), System.currentTimeMillis(),
                correlationId);

        return messagingChannel.sendAndGet(nbworkerTopic, request)
                .thenApply(FlowResponse.class::cast)
                .thenApply(FlowResponse::getPayload)
                .thenApply(flowMapper::toFlowResponseOutput);
    }

    @Override
    public CompletableFuture<FlowResponseV2> patchFlow(String flowId, FlowPatchV2 flowPatchDto) {
        logger.info("Patch flow request for flow {}", flowId);
        String correlationId = RequestCorrelationId.getId();

        FlowPatch flowPatch;
        try {
            flowPatch = flowMapper.toFlowPatch(flowPatchDto);
        } catch (IllegalArgumentException e) {
            logger.error("Can not parse arguments: {}", e.getMessage(), e);
            throw new MessageException(correlationId, System.currentTimeMillis(), ErrorType.DATA_INVALID,
                    e.getMessage(), "Can not parse arguments of the flow patch request");
        }
        flowPatch.setFlowId(flowId);
        CommandMessage request = new CommandMessage(new FlowPatchRequest(flowPatch), System.currentTimeMillis(),
                correlationId);

        return messagingChannel.sendAndGet(nbworkerTopic, request)
                .thenApply(FlowResponse.class::cast)
                .thenApply(FlowResponse::getPayload)
                .thenApply(flowMapper::toFlowResponseV2);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<List<FlowResponsePayload>> getAllFlows() {
        final String correlationId = RequestCorrelationId.getId();
        logger.debug("Get flows request processing");
        return handleGetAllFlowsRequest(
                new FlowsDumpRequest(), correlationId, flowMapper::toFlowResponseOutput);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<List<FlowResponseV2>> getAllFlowsV2(String status) {
        final String correlationId = RequestCorrelationId.getId();
        logger.debug("Get flows request processing");

        FlowsDumpRequest data;
        try {
            data = new FlowsDumpRequest(status);
        } catch (IllegalArgumentException e) {
            logger.error("Can not parse arguments: {}", e.getMessage(), e);
            throw new MessageException(correlationId, System.currentTimeMillis(), ErrorType.DATA_INVALID,
                    e.getMessage(), "Can not parse arguments of the flow dump request");
        }

        return handleGetAllFlowsRequest(data, correlationId, flowMapper::toFlowResponseV2);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<List<FlowResponsePayload>> deleteAllFlows() {
        CompletableFuture<List<FlowResponsePayload>> result = new CompletableFuture<>();
        logger.warn("Delete all flows request");
        // TODO: Need a getFlowIDs .. since that is all we need
        CompletableFuture<List<FlowResponsePayload>> getFlowsStage = this.getAllFlows();

        getFlowsStage.thenApply(flows -> {
            List<CompletableFuture<?>> deletionRequests = new ArrayList<>();
            for (int i = 0; i < flows.size(); i++) {
                String requestId = idFactory.produceChained(String.valueOf(i));
                FlowResponsePayload flow = flows.get(i);
                if (flow.getYFlowId() != null) {
                    // Skip y-sub-flows.
                    continue;
                }
                deletionRequests.add(sendDeleteFlow(flow.getId(), requestId));
            }
            return deletionRequests;
        }).thenApply(requests -> collectResponses(requests, FlowResponsePayload.class)
                .thenApply(result::complete));

        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowResponsePayload> deleteFlow(final String id) {
        logger.info("Delete flow request for flow: {}", id);
        final String correlationId = RequestCorrelationId.getId();

        return sendDeleteFlow(id, correlationId);
    }

    private CompletableFuture<FlowResponsePayload> sendDeleteFlow(String flowId, String correlationId) {
        CommandMessage request = buildDeleteFlowCommand(flowId, correlationId);

        return messagingChannel.sendAndGet(flowHsTopic, request)
                .thenApply(FlowResponse.class::cast)
                .thenApply(FlowResponse::getPayload)
                .thenApply(flowMapper::toFlowResponseOutput);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowResponseV2> deleteFlowV2(String flowId) {
        logger.info("Delete flow request for flow: {}", flowId);

        CommandMessage command = new CommandMessage(new FlowDeleteRequest(flowId),
                System.currentTimeMillis(), RequestCorrelationId.getId(), Destination.WFM);

        return messagingChannel.sendAndGet(flowHsTopic, command)
                .thenApply(FlowResponse.class::cast)
                .thenApply(FlowResponse::getPayload)
                .thenApply(flowMapper::toFlowResponseV2);

    }

    /**
     * Non-blocking primitive .. just create and send delete request.
     *
     * @return the request
     */
    private CommandMessage buildDeleteFlowCommand(String flowId, String correlationId) {
        FlowDeleteRequest data = new FlowDeleteRequest(flowId);
        return new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowIdStatusPayload> statusFlow(final String id) {
        logger.debug("Flow status request for flow: {}", id);
        return getFlowResponse(id, RequestCorrelationId.getId())
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

    private FlowPathPayload buildFlowPathPayload(List<FlowPathDto> paths, String flowId) {
        FlowPathDto askedPathDto = paths.stream().filter(e -> e.getId().equals(flowId)).findAny()
                .orElseThrow(() -> new IllegalStateException(format("Path for flow %s is not found.", flowId)));
        // fill primary flow path
        FlowPathPayload payload = new FlowPathPayload();
        payload.setId(askedPathDto.getId());
        payload.setForwardPath(askedPathDto.getForwardPath());
        payload.setReversePath(askedPathDto.getReversePath());

        if (askedPathDto.getProtectedPath() != null) {
            FlowProtectedPathDto protectedPath = askedPathDto.getProtectedPath();

            payload.setProtectedPath(FlowProtectedPath.builder()
                    .forwardPath(protectedPath.getForwardPath())
                    .reversePath(protectedPath.getReversePath())
                    .build());
        }

        // fill group paths
        if (paths.size() > 1) {
            payload.setDiverseGroupPayload(DiverseGroupPayload.builder()
                    .overlappingSegments(askedPathDto.getSegmentsStats())
                    .otherFlows(mapGroupPaths(paths, flowId, FlowPathDto::isPrimaryPathCorrespondStat))
                    .build());

            if (askedPathDto.getProtectedPath() != null) {
                payload.setDiverseGroupProtectedPayload(DiverseGroupPayload.builder()
                        .overlappingSegments(askedPathDto.getProtectedPath().getSegmentsStats())
                        .otherFlows(mapGroupPaths(paths, flowId, e -> !e.isPrimaryPathCorrespondStat()))
                        .build());
            }
        }
        return payload;
    }

    private List<GroupFlowPathPayload> mapGroupPaths(
            List<FlowPathDto> paths, String flowId, Predicate<FlowPathDto> predicate) {
        return paths.stream()
                .filter(e -> !e.getId().equals(flowId))
                .filter(predicate)
                .map(pathMapper::mapGroupFlowPathPayload)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<BatchResults> unpushFlows() {
        String correlationId = RequestCorrelationId.getId();
        throw new MessageException(correlationId, System.currentTimeMillis(), ErrorType.NOT_PERMITTED,
                "Operation not permitted", "Unpush flow operation is deprecated");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<BatchResults> pushFlows() {
        String correlationId = RequestCorrelationId.getId();
        throw new MessageException(correlationId, System.currentTimeMillis(), ErrorType.NOT_PERMITTED,
                "Operation not permitted", "Push flow operation is deprecated");
    }

    /**
     * Reads {@link FlowResponse} flow representation from the Storm.
     *
     * @return the bidirectional flow.
     */
    private CompletableFuture<FlowDto> getFlowResponse(String flowId, String correlationId) {
        FlowReadRequest data = new FlowReadRequest(flowId);
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);

        return messagingChannel.sendAndGet(nbworkerTopic, request)
                .thenApply(FlowResponse.class::cast)
                .thenApply(FlowResponse::getPayload);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<FlowResponsePayload> swapFlowPaths(String flowId) {
        final String correlationId = RequestCorrelationId.getId();
        logger.info("Swapping paths for flow : {}", flowId);

        FlowPathSwapRequest payload = new FlowPathSwapRequest(flowId);
        CommandMessage request = new CommandMessage(
                payload, System.currentTimeMillis(), correlationId, Destination.WFM);

        return messagingChannel.sendAndGet(flowHsTopic, request)
                .thenApply(FlowResponse.class::cast)
                .thenApply(FlowResponse::getPayload)
                .thenApply(flowMapper::toFlowResponseOutput);
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

    @Override
    public CompletableFuture<FlowRerouteResponseV2> rerouteFlowV2(String flowId) {
        logger.info("Processing flow reroute: {}", flowId);

        FlowRerouteRequest payload = createManualFlowRerouteRequest(flowId, false, false, "initiated via Northbound");
        CommandMessage command = new CommandMessage(
                payload, System.currentTimeMillis(), RequestCorrelationId.getId(), Destination.WFM);

        return messagingChannel.sendAndGet(rerouteTopic, command)
                .thenApply(FlowRerouteResponse.class::cast)
                .thenApply(response ->
                        flowMapper.toRerouteResponseV2(flowId, response.getPayload(), response.isRerouted()));
    }

    private CompletableFuture<FlowReroutePayload> reroute(String flowId, boolean forced) {
        logger.debug("Reroute flow: {}={}, forced={}", FLOW_ID, flowId, forced);
        String correlationId = RequestCorrelationId.getId();
        FlowRerouteRequest payload = createManualFlowRerouteRequest(flowId, forced, false, "initiated via Northbound");
        CommandMessage command = new CommandMessage(
                payload, System.currentTimeMillis(), correlationId, Destination.WFM);

        return messagingChannel.sendAndGet(rerouteTopic, command)
                .thenApply(FlowRerouteResponse.class::cast)
                .thenApply(response ->
                        flowMapper.toReroutePayload(flowId, response.getPayload(), response.isRerouted()));
    }

    @Override
    public CompletableFuture<List<FlowHistoryEntry>> listFlowEvents(String flowId,
                                                                    long timestampFrom,
                                                                    long timestampTo, int maxCount) {
        if (maxCount < 1) {
            throw new MessageException(RequestCorrelationId.getId(), System.currentTimeMillis(),
                    ErrorType.PARAMETERS_INVALID, format("Invalid `max_count` argument '%s'.", maxCount),
                    "`max_count` argument must be positive.");
        }
        String correlationId = RequestCorrelationId.getId();
        GetFlowHistoryRequest request = GetFlowHistoryRequest.builder()
                .flowId(flowId)
                .timestampFrom(timestampFrom)
                .timestampTo(timestampTo)
                .maxCount(maxCount)
                .build();
        CommandMessage command = new CommandMessage(request, System.currentTimeMillis(), correlationId);
        return messagingChannel.sendAndGetChunked(nbworkerTopic, command)
                .thenApply(result -> result.stream()
                        .map(FlowHistoryEntry.class::cast)
                        .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<FlowHistoryStatusesResponse> getFlowStatuses(String flowId, long timestampFrom,
                                                                          long timestampTo, int maxCount) {
        if (maxCount < 1) {
            throw new MessageException(RequestCorrelationId.getId(), System.currentTimeMillis(),
                    ErrorType.PARAMETERS_INVALID, format("Invalid `max_count` argument '%s'.", maxCount),
                    "`max_count` argument must be positive.");
        }
        String correlationId = RequestCorrelationId.getId();
        GetFlowStatusTimestampsRequest request = GetFlowStatusTimestampsRequest.builder()
                .flowId(flowId)
                .timestampFrom(timestampFrom)
                .timestampTo(timestampTo)
                .maxCount(maxCount)
                .build();
        CommandMessage command = new CommandMessage(request, System.currentTimeMillis(), correlationId);
        return messagingChannel.sendAndGetChunked(nbworkerTopic, command)
                .thenApply(result -> result.stream()
                        .map(FlowStatusTimestampsEntry.class::cast)
                        .map(flowMapper::toFlowHistoryStatus)
                        .collect(Collectors.toList()))
                .thenApply(FlowHistoryStatusesResponse::new);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<SwapFlowEndpointPayload> swapFlowEndpoint(SwapFlowEndpointPayload input) {
        final String correlationId = RequestCorrelationId.getId();
        logger.info("Swap endpoints for flow {} and {}", input.getFirstFlow().getFlowId(),
                input.getSecondFlow().getFlowId());

        SwapFlowEndpointRequest payload = new SwapFlowEndpointRequest(flowMapper.toSwapFlowDto(input.getFirstFlow()),
                flowMapper.toSwapFlowDto(input.getSecondFlow()));

        CommandMessage request = new CommandMessage(
                payload, System.currentTimeMillis(), correlationId, Destination.WFM);

        return messagingChannel.sendAndGet(flowHsTopic, request)
                .thenApply(SwapFlowResponse.class::cast)
                .thenApply(response -> new SwapFlowEndpointPayload(
                        flowMapper.toSwapOutput(response.getFirstFlow().getPayload()),
                        flowMapper.toSwapOutput(response.getSecondFlow().getPayload())));
    }

    @Override
    public CompletableFuture<FlowConnectedDevicesResponse> getFlowConnectedDevices(String flowId, Instant since) {
        logger.info("Get connected devices for flow {} since {}", flowId, since);

        FlowConnectedDeviceRequest request = new FlowConnectedDeviceRequest(flowId, since);

        CommandMessage message = new CommandMessage(
                request, System.currentTimeMillis(), RequestCorrelationId.getId(), Destination.WFM);

        return messagingChannel.sendAndGet(nbworkerTopic, message)
                .thenApply(org.openkilda.messaging.nbtopology.response.FlowConnectedDevicesResponse.class::cast)
                .thenApply(connectedDeviceMapper::toResponse);
    }

    @Override
    public CompletableFuture<List<FlowLoopResponse>> getFlowLoops(String flowId, String switchId) {
        logger.info("Get flow loops for flow {} and switch {}", flowId, switchId);

        GetFlowLoopsRequest request = new GetFlowLoopsRequest(flowId, switchId);

        CommandMessage message = new CommandMessage(
                request, System.currentTimeMillis(), RequestCorrelationId.getId(), Destination.WFM);

        return messagingChannel.sendAndGet(nbworkerTopic, message)
                .thenApply(result -> Optional.of(result).map(FlowLoopsResponse.class::cast)
                        .map(FlowLoopsResponse::getPayload)
                        .orElse(Collections.emptyList())
                        .stream()
                        .map(flowMapper::toFlowLoopResponse)
                        .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<FlowLoopResponse> createFlowLoop(String flowId, SwitchId switchId) {
        logger.info("Create flow loop for flow {} and switch {}", flowId, switchId);

        CreateFlowLoopRequest request = new CreateFlowLoopRequest(flowId, switchId);

        CommandMessage message = new CommandMessage(
                request, System.currentTimeMillis(), RequestCorrelationId.getId(), Destination.WFM);

        return messagingChannel.sendAndGet(flowHsTopic, message)
                .thenApply(org.openkilda.messaging.info.flow.FlowResponse.class::cast)
                .thenApply(flowMapper::toFlowLoopResponse);
    }

    @Override
    public CompletableFuture<FlowLoopResponse> deleteFlowLoop(String flowId) {
        logger.info("Delete flow loop for flow {}", flowId);

        DeleteFlowLoopRequest request = new DeleteFlowLoopRequest(flowId);

        CommandMessage message = new CommandMessage(
                request, System.currentTimeMillis(), RequestCorrelationId.getId(), Destination.WFM);

        return messagingChannel.sendAndGet(flowHsTopic, message)
                .thenApply(org.openkilda.messaging.info.flow.FlowResponse.class::cast)
                .thenApply(flowMapper::toFlowLoopResponse);
    }

    @Override
    public CompletableFuture<FlowMirrorPointResponseV2> createFlowMirrorPoint(String flowId,
                                                                              FlowMirrorPointPayload payload) {
        logger.info("Processing flow mirror point creation: {}, for flow {}", payload, flowId);

        final String correlationId = RequestCorrelationId.getId();
        FlowMirrorPointCreateRequest request;
        try {
            if (!payload.getMirrorPointId().matches("^[\\w-]{1,100}$")) {
                throw new IllegalArgumentException("Mirror path ID can only consist of alphanumeric characters, "
                        + "underscore, and hyphen. The length of this parameter must not exceed 100 characters.");
            }
            request = flowMapper.toFlowMirrorPointCreateRequest(flowId, payload);
        } catch (IllegalArgumentException ex) {
            throw new MessageException(correlationId, System.currentTimeMillis(),
                    ErrorType.PARAMETERS_INVALID, ex.getMessage(),
                    "Can not parse arguments of the mirror point create request");
        }

        CommandMessage command = new CommandMessage(request, System.currentTimeMillis(), correlationId);

        return messagingChannel.sendAndGet(flowHsTopic, command)
                .thenApply(FlowMirrorPointResponse.class::cast)
                .thenApply(flowMapper::toFlowMirrorPointResponseV2);
    }

    @Override
    public CompletableFuture<FlowMirrorPointResponseV2> deleteFlowMirrorPoint(String flowId, String mirrorPointId) {
        logger.info("Processing flow mirror point deletion: {}, for flow {}", mirrorPointId, flowId);

        final String correlationId = RequestCorrelationId.getId();
        FlowMirrorPointDeleteRequest request = new FlowMirrorPointDeleteRequest(flowId, mirrorPointId);

        CommandMessage command = new CommandMessage(request, System.currentTimeMillis(), correlationId);

        return messagingChannel.sendAndGet(flowHsTopic, command)
                .thenApply(FlowMirrorPointResponse.class::cast)
                .thenApply(flowMapper::toFlowMirrorPointResponseV2);
    }

    @Override
    public CompletableFuture<FlowMirrorPointsResponseV2> getFlowMirrorPoints(String flowId) {
        logger.info("Processing flow mirror point getting for flow {}", flowId);

        final String correlationId = RequestCorrelationId.getId();
        FlowMirrorPointsDumpRequest request = new FlowMirrorPointsDumpRequest(flowId);

        CommandMessage command = new CommandMessage(request, System.currentTimeMillis(), correlationId);

        return messagingChannel.sendAndGet(nbworkerTopic, command)
                .thenApply(FlowMirrorPointsDumpResponse.class::cast)
                .thenApply(flowMapper::toFlowMirrorPointsResponseV2);
    }

    private <T> CompletableFuture<List<T>> handleGetAllFlowsRequest(
            FlowsDumpRequest payload, String correlationId, Function<FlowDto, T> encoder) {
        CommandMessage request = new CommandMessage(
                payload, System.currentTimeMillis(), correlationId, Destination.WFM);
        return messagingChannel.sendAndGetChunked(nbworkerTopic, request)
                .thenApply(result -> result.stream()
                        .map(FlowResponse.class::cast)
                        .map(FlowResponse::getPayload)
                        .map(encoder)
                        .collect(Collectors.toList()));
    }
}
