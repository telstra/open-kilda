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

import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.model.system.KildaConfigurationDto;
import org.openkilda.messaging.nbtopology.request.KildaConfigurationGetRequest;
import org.openkilda.messaging.nbtopology.request.KildaConfigurationUpdateRequest;
import org.openkilda.messaging.nbtopology.response.KildaConfigurationResponse;
import org.openkilda.northbound.messaging.MessagingChannel;
import org.openkilda.northbound.service.KildaConfigurationService;
import org.openkilda.northbound.utils.RequestCorrelationId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class KildaConfigurationServiceImpl implements KildaConfigurationService {

    private final Logger logger = LoggerFactory.getLogger(KildaConfigurationServiceImpl.class);

    /**
     * The kafka topic for the nb topology.
     */
    @Value("#{kafkaTopicsConfig.getTopoNbTopic()}")
    private String nbworkerTopic;

    @Autowired
    private MessagingChannel messagingChannel;

    @Override
    public CompletableFuture<KildaConfigurationDto> getKildaConfiguration() {
        String correlationId = RequestCorrelationId.getId();
        KildaConfigurationGetRequest request = new KildaConfigurationGetRequest();
        CommandMessage message = new CommandMessage(request, System.currentTimeMillis(), correlationId);

        return messagingChannel.sendAndGet(nbworkerTopic, message)
                .thenApply(response -> ((KildaConfigurationResponse) response).getKildaConfigurationDto());
    }

    @Override
    public CompletableFuture<KildaConfigurationDto> updateKildaConfiguration(KildaConfigurationDto dto) {
        String correlationId = RequestCorrelationId.getId();
        logger.debug("Processing request to update kilda config, new properties: {}", dto);
        KildaConfigurationUpdateRequest request = new KildaConfigurationUpdateRequest(dto);
        CommandMessage message = new CommandMessage(request, System.currentTimeMillis(), correlationId);

        return messagingChannel.sendAndGet(nbworkerTopic, message)
                .thenApply(response -> ((KildaConfigurationResponse) response).getKildaConfigurationDto());
    }
}
