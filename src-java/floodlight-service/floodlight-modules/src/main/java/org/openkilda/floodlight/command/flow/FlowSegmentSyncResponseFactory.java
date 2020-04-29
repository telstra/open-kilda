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

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowInstallResponse;

import java.util.Optional;

public class FlowSegmentSyncResponseFactory extends FlowSegmentResponseFactory {
    private final String correlationId;

    public FlowSegmentSyncResponseFactory(String correlationId, String targetKafkaTopic) {
        super(targetKafkaTopic);
        this.correlationId = correlationId;
    }

    @Override
    public String makeKafkaKey(String requestKey) {
        return correlationId;
    }

    @Override
    public Optional<Message> makeSuccessResponse() {
        return Optional.of(new InfoMessage(new FlowInstallResponse(), System.currentTimeMillis(), correlationId));
    }

    @Override
    public Optional<Message> makeFailResponse(Exception e) {
        ErrorData payload = new ErrorData(ErrorType.INTERNAL_ERROR, "Error during flow installation", e.getMessage());
        return Optional.of(new ErrorMessage(payload, System.currentTimeMillis(), correlationId));
    }
}
