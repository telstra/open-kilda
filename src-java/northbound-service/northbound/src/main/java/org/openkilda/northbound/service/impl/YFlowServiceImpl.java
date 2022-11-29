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

import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.YFlowPingRequest;
import org.openkilda.messaging.command.yflow.SubFlowsReadRequest;
import org.openkilda.messaging.command.yflow.SubFlowsResponse;
import org.openkilda.messaging.command.yflow.YFlowDeleteRequest;
import org.openkilda.messaging.command.yflow.YFlowPartialUpdateRequest;
import org.openkilda.messaging.command.yflow.YFlowPathSwapRequest;
import org.openkilda.messaging.command.yflow.YFlowPathsReadRequest;
import org.openkilda.messaging.command.yflow.YFlowPathsResponse;
import org.openkilda.messaging.command.yflow.YFlowReadRequest;
import org.openkilda.messaging.command.yflow.YFlowRequest;
import org.openkilda.messaging.command.yflow.YFlowRerouteRequest;
import org.openkilda.messaging.command.yflow.YFlowRerouteResponse;
import org.openkilda.messaging.command.yflow.YFlowResponse;
import org.openkilda.messaging.command.yflow.YFlowSyncRequest;
import org.openkilda.messaging.command.yflow.YFlowValidationRequest;
import org.openkilda.messaging.command.yflow.YFlowValidationResponse;
import org.openkilda.messaging.command.yflow.YFlowsDumpRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.flow.YFlowPingResponse;
import org.openkilda.northbound.converter.YFlowMapper;
import org.openkilda.northbound.dto.v2.yflows.SubFlowsDump;
import org.openkilda.northbound.dto.v2.yflows.YFlow;
import org.openkilda.northbound.dto.v2.yflows.YFlowCreatePayload;
import org.openkilda.northbound.dto.v2.yflows.YFlowDump;
import org.openkilda.northbound.dto.v2.yflows.YFlowPatchPayload;
import org.openkilda.northbound.dto.v2.yflows.YFlowPaths;
import org.openkilda.northbound.dto.v2.yflows.YFlowPingPayload;
import org.openkilda.northbound.dto.v2.yflows.YFlowPingResult;
import org.openkilda.northbound.dto.v2.yflows.YFlowRerouteResult;
import org.openkilda.northbound.dto.v2.yflows.YFlowSyncResult;
import org.openkilda.northbound.dto.v2.yflows.YFlowUpdatePayload;
import org.openkilda.northbound.dto.v2.yflows.YFlowValidationResult;
import org.openkilda.northbound.messaging.MessagingChannel;
import org.openkilda.northbound.service.YFlowService;
import org.openkilda.northbound.utils.RequestCorrelationId;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Handles the Y-flow operations.
 */
@Slf4j
@Service
public class YFlowServiceImpl implements YFlowService {
    @Value("#{kafkaTopicsConfig.getFlowHsTopic()}")
    private String flowHsTopic;

    @Value("#{kafkaTopicsConfig.getTopoRerouteTopic()}")
    private String rerouteTopic;

    @Value("#{kafkaTopicsConfig.getPingTopic()}")
    private String pingTopic;

    @Autowired
    private MessagingChannel messagingChannel;

    @Autowired
    private YFlowMapper flowMapper;

    @Override
    public CompletableFuture<YFlow> createYFlow(YFlowCreatePayload createPayload) {
        log.info("API request: create y-flow: {}", createPayload);
        String correlationId = RequestCorrelationId.getId();

        YFlowRequest flowRequest;
        try {
            flowRequest = flowMapper.toYFlowCreateRequest(createPayload);
        } catch (IllegalArgumentException e) {
            throw new MessageException(correlationId, System.currentTimeMillis(), ErrorType.DATA_INVALID,
                    e.getMessage(), "Can not parse arguments of the create y-flow request");
        }

        CommandMessage command = new CommandMessage(flowRequest, System.currentTimeMillis(), correlationId);
        return messagingChannel.sendAndGet(flowHsTopic, command)
                .thenApply(YFlowResponse.class::cast)
                .thenApply(YFlowResponse::getYFlow)
                .thenApply(flowMapper::toYFlow);
    }

    @Override
    public CompletableFuture<YFlowDump> dumpYFlows() {
        log.info("API request: Dump all y-flows");
        YFlowsDumpRequest dumpRequest = new YFlowsDumpRequest();
        CommandMessage request = new CommandMessage(dumpRequest, System.currentTimeMillis(),
                RequestCorrelationId.getId());
        return messagingChannel.sendAndGetChunked(flowHsTopic, request)
                .thenApply(result -> result.stream()
                        .map(YFlowResponse.class::cast)
                        .map(YFlowResponse::getYFlow)
                        .map(flowMapper::toYFlow)
                        .collect(Collectors.toList()))
                .thenApply(YFlowDump::new);
    }

    @Override
    public CompletableFuture<YFlow> getYFlow(String yFlowId) {
        log.info("API request: Get y-flow: {}", yFlowId);
        YFlowReadRequest readRequest = new YFlowReadRequest(yFlowId);
        CommandMessage request = new CommandMessage(readRequest, System.currentTimeMillis(),
                RequestCorrelationId.getId());
        return messagingChannel.sendAndGet(flowHsTopic, request)
                .thenApply(YFlowResponse.class::cast)
                .thenApply(YFlowResponse::getYFlow)
                .thenApply(flowMapper::toYFlow);
    }

    @Override
    public CompletableFuture<YFlowPaths> getYFlowPaths(String yFlowId) {
        log.info("API request: Get y-flow paths: {}", yFlowId);
        YFlowPathsReadRequest readPathsRequest = new YFlowPathsReadRequest(yFlowId);
        CommandMessage request = new CommandMessage(readPathsRequest, System.currentTimeMillis(),
                RequestCorrelationId.getId());
        return messagingChannel.sendAndGet(flowHsTopic, request)
                .thenApply(YFlowPathsResponse.class::cast)
                .thenApply(flowMapper::toYFlowPaths);
    }

    @Override
    public CompletableFuture<YFlow> updateYFlow(String yFlowId, YFlowUpdatePayload updatePayload) {
        log.info("API request: Update y-flow {}. New properties {}", yFlowId, updatePayload);
        String correlationId = RequestCorrelationId.getId();

        YFlowRequest flowRequest;
        try {
            flowRequest = flowMapper.toYFlowUpdateRequest(yFlowId, updatePayload);
        } catch (IllegalArgumentException e) {
            throw new MessageException(correlationId, System.currentTimeMillis(), ErrorType.DATA_INVALID,
                    e.getMessage(), "Can not parse arguments of the create y-flow request");
        }

        CommandMessage command = new CommandMessage(flowRequest, System.currentTimeMillis(), correlationId);
        return messagingChannel.sendAndGet(flowHsTopic, command)
                .thenApply(YFlowResponse.class::cast)
                .thenApply(YFlowResponse::getYFlow)
                .thenApply(flowMapper::toYFlow);
    }

    @Override
    public CompletableFuture<YFlow> patchYFlow(String yFlowId, YFlowPatchPayload patchPayload) {
        log.info("API request: Patch y-flow {}. New properties {}", yFlowId, patchPayload);
        String correlationId = RequestCorrelationId.getId();

        YFlowPartialUpdateRequest yFlowPartialUpdateRequest;
        try {
            yFlowPartialUpdateRequest = flowMapper.toYFlowPatchRequest(yFlowId, patchPayload);
        } catch (IllegalArgumentException e) {
            throw new MessageException(correlationId, System.currentTimeMillis(), ErrorType.DATA_INVALID,
                    e.getMessage(), "Can not parse arguments of the flow patch request");
        }

        CommandMessage request = new CommandMessage(yFlowPartialUpdateRequest,
                System.currentTimeMillis(), correlationId);
        return messagingChannel.sendAndGet(flowHsTopic, request)
                .thenApply(YFlowResponse.class::cast)
                .thenApply(YFlowResponse::getYFlow)
                .thenApply(flowMapper::toYFlow);
    }

    @Override
    public CompletableFuture<YFlow> deleteYFlow(String yFlowId) {
        log.info("API request: Delete y-flow: {}", yFlowId);
        CommandMessage command = new CommandMessage(new YFlowDeleteRequest(yFlowId), System.currentTimeMillis(),
                RequestCorrelationId.getId());
        return messagingChannel.sendAndGet(flowHsTopic, command)
                .thenApply(YFlowResponse.class::cast)
                .thenApply(YFlowResponse::getYFlow)
                .thenApply(flowMapper::toYFlow);
    }

    @Override
    public CompletableFuture<SubFlowsDump> getSubFlows(String yFlowId) {
        log.info("API request: Get y-flow sub-flows: {}", yFlowId);
        CommandMessage request = new CommandMessage(new SubFlowsReadRequest(yFlowId), System.currentTimeMillis(),
                RequestCorrelationId.getId());
        return messagingChannel.sendAndGet(flowHsTopic, request)
                .thenApply(SubFlowsResponse.class::cast)
                .thenApply(flowMapper::toSubFlowsDump);
    }

    @Override
    public CompletableFuture<YFlowRerouteResult> rerouteYFlow(String yFlowId) {
        log.info("API request: Reroute y-flow: {}", yFlowId);
        YFlowRerouteRequest flowRerouteRequest = new YFlowRerouteRequest(yFlowId, "initiated via Northbound");
        CommandMessage command = new CommandMessage(flowRerouteRequest, System.currentTimeMillis(),
                RequestCorrelationId.getId());
        return messagingChannel.sendAndGet(rerouteTopic, command)
                .thenApply(YFlowRerouteResponse.class::cast)
                .thenApply(flowMapper::toRerouteResult);
    }

    @Override
    public CompletableFuture<YFlowValidationResult> validateYFlow(String yFlowId) {
        log.info("API request: Validate y-flow: {}", yFlowId);
        CommandMessage command = new CommandMessage(new YFlowValidationRequest(yFlowId), System.currentTimeMillis(),
                RequestCorrelationId.getId());
        return messagingChannel.sendAndGet(flowHsTopic, command)
                .thenApply(YFlowValidationResponse.class::cast)
                .thenApply(flowMapper::toValidationResult);
    }

    @Override
    public CompletableFuture<YFlowSyncResult> synchronizeYFlow(String yFlowId) {
        log.info("API request: Synchronize y-flow: {}", yFlowId);
        CommandMessage command = new CommandMessage(new YFlowSyncRequest(yFlowId), System.currentTimeMillis(),
                RequestCorrelationId.getId());
        return messagingChannel.sendAndGet(flowHsTopic, command)
                .thenApply(YFlowRerouteResponse.class::cast)
                .thenApply(flowMapper::toSyncResult);
    }

    @Override
    public CompletableFuture<YFlowPingResult> pingYFlow(String yFlowId, YFlowPingPayload payload) {
        log.info("API request: Ping y-flow {}, payload {}", yFlowId, payload);
        CommandMessage command = new CommandMessage(new YFlowPingRequest(yFlowId, payload.getTimeoutMillis()),
                System.currentTimeMillis(), RequestCorrelationId.getId());
        return messagingChannel.sendAndGet(pingTopic, command)
                .thenApply(YFlowPingResponse.class::cast)
                .thenApply(flowMapper::toPingResult);
    }

    @Override
    public CompletableFuture<YFlow> swapYFlowPaths(String yFlowId) {
        log.info("API request: Swap y-flow paths: {}", yFlowId);

        CommandMessage request = new CommandMessage(new YFlowPathSwapRequest(yFlowId),
                System.currentTimeMillis(), RequestCorrelationId.getId());
        return messagingChannel.sendAndGet(flowHsTopic, request)
                .thenApply(YFlowResponse.class::cast)
                .thenApply(YFlowResponse::getYFlow)
                .thenApply(flowMapper::toYFlow);
    }
}
