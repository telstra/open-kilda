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

package org.openkilda.floodlight.command.ping;

import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.floodlight.command.Command;
import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;
import org.openkilda.floodlight.service.ping.PingService;
import org.openkilda.messaging.floodlight.response.PingResponse;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.model.Ping;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

abstract class PingCommand extends Command {
    protected static final Logger logPing = LoggerFactory.getLogger("open-kilda.flows.PING");

    private final IKafkaProducerService producerService;
    private final PingService pingService;

    private final KafkaChannel kafkaChannel;

    PingCommand(CommandContext context) {
        super(context);

        FloodlightModuleContext moduleContext = getContext().getModuleContext();
        pingService = moduleContext.getServiceImpl(PingService.class);
        producerService = moduleContext.getServiceImpl(IKafkaProducerService.class);
        kafkaChannel = moduleContext.getServiceImpl(KafkaUtilityService.class).getKafkaChannel();
    }

    void sendErrorResponse(UUID pingId, Ping.Errors errorCode) {
        PingResponse response = new PingResponse(pingId, errorCode);
        sendResponse(response);
    }

    void sendResponse(PingResponse response) {
        InfoMessage message = new InfoMessage(response, System.currentTimeMillis(), getContext().getCorrelationId());
        // TODO(surabujin): return future to avoid thread occupation during wait period(use CommandProcessorService)
        producerService.sendMessageAndTrack(kafkaChannel.getPingTopic(), message);
    }

    protected PingService getPingService() {
        return pingService;
    }
}
