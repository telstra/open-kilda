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
import org.openkilda.messaging.model.system.FeatureTogglesDto;
import org.openkilda.messaging.nbtopology.request.CreateOrUpdateFeatureTogglesRequest;
import org.openkilda.messaging.nbtopology.request.GetFeatureTogglesRequest;
import org.openkilda.messaging.nbtopology.response.FeatureTogglesResponse;
import org.openkilda.northbound.messaging.MessagingChannel;
import org.openkilda.northbound.service.FeatureTogglesService;
import org.openkilda.northbound.utils.RequestCorrelationId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class FeatureTogglesServiceImpl implements FeatureTogglesService {

    private final Logger logger = LoggerFactory.getLogger(FeatureTogglesServiceImpl.class);

    /**
     * The kafka topic for the nb topology.
     */
    @Value("#{kafkaTopicsConfig.getTopoNbTopic()}")
    private String nbworkerTopic;

    @Autowired
    private MessagingChannel messagingChannel;

    @Override
    public CompletableFuture<FeatureTogglesDto> toggleFeatures(FeatureTogglesDto dto) {
        String correlationId = RequestCorrelationId.getId();
        logger.debug("Processing request to toggle features, new properties are {}", dto);
        CreateOrUpdateFeatureTogglesRequest request = new CreateOrUpdateFeatureTogglesRequest(dto);
        CommandMessage message = new CommandMessage(request, System.currentTimeMillis(), correlationId);

        return messagingChannel.sendAndGet(nbworkerTopic, message)
                .thenApply(response -> ((FeatureTogglesResponse) response).getFeatureTogglesDto());
    }

    @Override
    public CompletableFuture<FeatureTogglesDto> getFeatureTogglesState() {
        String correlationId = RequestCorrelationId.getId();
        GetFeatureTogglesRequest request = new GetFeatureTogglesRequest();
        CommandMessage message = new CommandMessage(request, System.currentTimeMillis(), correlationId);

        return messagingChannel.sendAndGet(nbworkerTopic, message)
                .thenApply(response -> ((FeatureTogglesResponse) response).getFeatureTogglesDto());
    }
}
