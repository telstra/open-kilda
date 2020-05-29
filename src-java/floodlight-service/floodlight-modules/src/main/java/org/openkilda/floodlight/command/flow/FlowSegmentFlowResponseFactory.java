/* Copyright 2020 Telstra Open Source
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

package org.openkilda.floodlight.command.flow;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.BaseFlow;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.rule.FlowCommandErrorData;

import java.util.Optional;

public class FlowSegmentFlowResponseFactory extends FlowSegmentResponseFactory {
    private final CommandMessage request;
    private final Destination destination;
    private final BaseFlow payload;

    public FlowSegmentFlowResponseFactory(
            String targetKafkaTopic, CommandMessage request, Destination destination, BaseFlow payload) {
        super(targetKafkaTopic);
        this.request = request;
        this.destination = destination;
        this.payload = payload;
    }

    @Override
    public Optional<Message> makeSuccessResponse() {
        request.setDestination(destination);
        return Optional.of(request);
    }

    @Override
    public Optional<Message> makeFailResponse(Exception e) {
        ErrorData error = new FlowCommandErrorData(payload.getId(), payload.getCookie(), payload.getTransactionId(),
                ErrorType.CREATION_FAILURE, e.getMessage(), e.getMessage());
        return Optional.of(new ErrorMessage(
                error, System.currentTimeMillis(), request.getCorrelationId(), destination));
    }
}
