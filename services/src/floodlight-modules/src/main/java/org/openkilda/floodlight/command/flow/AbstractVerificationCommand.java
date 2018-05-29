/*
 * Copyright 2017 Telstra Open Source
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openkilda.floodlight.command.flow;

import org.openkilda.floodlight.command.Command;
import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.kafka.KafkaMessageProducer;
import org.openkilda.messaging.Topic;
import org.openkilda.messaging.command.flow.UniFlowVerificationRequest;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowVerificationErrorCode;
import org.openkilda.messaging.info.flow.UniFlowVerificationResponse;

abstract class AbstractVerificationCommand extends Command {
    private final KafkaMessageProducer kafkaProducer;
    private final UniFlowVerificationRequest verificationRequest;

    AbstractVerificationCommand(CommandContext context, UniFlowVerificationRequest verificationRequest) {
        super(context);

        this.verificationRequest = verificationRequest;
        kafkaProducer = getContext().getModuleContext().getServiceImpl(KafkaMessageProducer.class);
    }

    protected void sendResponse(UniFlowVerificationResponse response) {
        InfoMessage message = new InfoMessage(response, System.currentTimeMillis(), getContext().getCorrelationId());
        kafkaProducer.postMessage(Topic.FLOW, message);
    }

    protected void sendErrorResponse(FlowVerificationErrorCode errorCode) {
        UniFlowVerificationResponse response = new UniFlowVerificationResponse(verificationRequest, errorCode);
        sendResponse(response);
    }

    protected UniFlowVerificationRequest getVerificationRequest() {
        return verificationRequest;
    }
}
