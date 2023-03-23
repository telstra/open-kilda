/* Copyright 2018 Telstra Open Source
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
import org.openkilda.messaging.command.flow.PathValidateRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.network.PathValidationResult;
import org.openkilda.messaging.info.network.PathsInfoData;
import org.openkilda.messaging.nbtopology.request.GetPathsRequest;
import org.openkilda.messaging.payload.network.PathDto;
import org.openkilda.messaging.payload.network.PathValidationPayload;
import org.openkilda.messaging.payload.network.PathsDto;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.converter.PathMapper;
import org.openkilda.northbound.dto.v2.flows.PathValidateResponse;
import org.openkilda.northbound.messaging.MessagingChannel;
import org.openkilda.northbound.service.NetworkService;
import org.openkilda.northbound.utils.RequestCorrelationId;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NetworkServiceImpl implements NetworkService {

    @Autowired
    private PathMapper pathMapper;

    /**
     * The kafka topic for the nb topology.
     */
    @Value("#{kafkaTopicsConfig.getTopoNbTopic()}")
    private String nbworkerTopic;

    @Autowired
    private MessagingChannel messagingChannel;

    @Override
    public CompletableFuture<PathsDto> getPaths(
            SwitchId srcSwitch, SwitchId dstSwitch, FlowEncapsulationType encapsulationType,
            PathComputationStrategy pathComputationStrategy, Duration maxLatency, Duration maxLatencyTier2,
            Integer maxPathCount) {
        log.info("API request: Get Paths: srcSwitch {}, dstSwitch {}, encapsulationType {}, "
                + "pathComputationStrategy {}, maxLatency {}, maxLatencyTier2 {}, maxPathCount {}",
                srcSwitch, dstSwitch, encapsulationType, pathComputationStrategy,
                maxLatency, maxLatencyTier2, maxPathCount);

        String correlationId = RequestCorrelationId.getId();

        if (maxPathCount != null && maxPathCount <= 0) {
            throw new MessageException(correlationId, System.currentTimeMillis(), ErrorType.PARAMETERS_INVALID,
                    "Bad max_path_count parameter", "The number MAX_PATH_COUNT should be positive");
        }

        if (PathComputationStrategy.MAX_LATENCY.equals(pathComputationStrategy) && maxLatency == null) {
            throw new MessageException(correlationId, System.currentTimeMillis(), ErrorType.PARAMETERS_INVALID,
                    "Missed max_latency parameter.", "MAX_LATENCY path computation strategy requires non null "
                    + "max_latency parameter. If max_latency will be equal to 0 LATENCY strategy will be used instead "
                    + "of MAX_LATENCY.");
        }

        GetPathsRequest request = new GetPathsRequest(srcSwitch, dstSwitch, encapsulationType, pathComputationStrategy,
                maxLatency, maxLatencyTier2, maxPathCount);
        CommandMessage message = new CommandMessage(request, System.currentTimeMillis(), correlationId);

        return messagingChannel.sendAndGetChunked(nbworkerTopic, message)
                .thenApply(paths -> {
                    List<PathDto> pathsDtoList = paths.stream().map(PathsInfoData.class::cast)
                            .map(p -> pathMapper.mapToPath(p.getPath()))
                            .collect(Collectors.toList());
                    return new PathsDto(pathsDtoList);
                });
    }

    /**
     * Validates that a flow with the given path can possibly be created. If it is not possible,
     * it responds with the reasons, such as: not enough bandwidth, requested latency is too low, there is no
     * links between the selected switches, and so on.
     * @param pathValidationPayload a path together with validation parameters provided by a user
     * @return either a successful response or a list of errors
     */
    @Override
    public CompletableFuture<PathValidateResponse> validateFlowPath(PathValidationPayload pathValidationPayload) {
        PathValidateRequest request = new PathValidateRequest(pathValidationPayload);

        CommandMessage message = new CommandMessage(request, System.currentTimeMillis(),
                RequestCorrelationId.getId());
        return messagingChannel.sendAndGet(nbworkerTopic, message)
                .thenApply(PathValidationResult.class::cast)
                .thenApply(pathMapper::toPathValidateResponse);
    }
}
