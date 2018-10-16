/* Copyright 2017 Telstra Open Source
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

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.system.FeatureToggleRequest;
import org.openkilda.messaging.command.system.FeatureToggleStateRequest;
import org.openkilda.messaging.info.system.FeatureTogglesResponse;
import org.openkilda.messaging.payload.FeatureTogglePayload;
import org.openkilda.northbound.converter.FeatureTogglesMapper;
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

    @Value("#{kafkaTopicsConfig.getTopoEngTopic()}")
    private String topoEngTopic;

    @Autowired
    private MessagingChannel messagingChannel;

    @Autowired
    private FeatureTogglesMapper mapper;

    @Override
    public void toggleFeatures(FeatureTogglePayload dto) {
        String correlationId = RequestCorrelationId.getId();
        logger.debug("Processing request to toggle features, new properties are {}", dto);
        FeatureToggleRequest request = mapper.toRequest(dto);
        CommandMessage message = new CommandMessage(request, System.currentTimeMillis(), correlationId,
                Destination.TOPOLOGY_ENGINE);

        messagingChannel.send(topoEngTopic, message);
    }

    @Override
    public CompletableFuture<FeatureTogglePayload> getFeatureTogglesState() {
        String correlationId = RequestCorrelationId.getId();
        FeatureToggleStateRequest teRequest = new FeatureToggleStateRequest();
        CommandMessage requestMessage = new CommandMessage(teRequest, System.currentTimeMillis(),
                correlationId, Destination.TOPOLOGY_ENGINE);

        return messagingChannel.sendAndGet(topoEngTopic, requestMessage)
                .thenApply(FeatureTogglesResponse.class::cast)
                .thenApply(mapper::toDto);

    }
}
