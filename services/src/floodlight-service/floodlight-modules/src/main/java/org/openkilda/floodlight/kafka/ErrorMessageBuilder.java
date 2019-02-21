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

package org.openkilda.floodlight.kafka;

import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;

final class ErrorMessageBuilder {

    private ErrorType errorType;
    private String message;
    private String description;
    private String correlationId;
    private String topic;

    private ErrorMessageBuilder(ErrorType errorType) {
        this.errorType = errorType;
    }

    static ErrorMessageBuilder anError(ErrorType errorType) {
        return new ErrorMessageBuilder(errorType);
    }

    /**
     * Sets the message and returns a reference to this Builder so that the methods can be chained together.
     *
     * @param message the message to set
     * @return a reference to this Builder
     */
    ErrorMessageBuilder withMessage(String message) {
        this.message = message;
        return this;
    }

    /**
     * Sets the description and returns a reference to this Builder so that the methods can be chained together.
     *
     * @param description the description to set
     * @return a reference to this Builder
     */
    ErrorMessageBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    /**
     * Sets the correlation id and returns a reference to this Builder so that the methods can be chained together.
     *
     * @param correlationId the correlation id to set
     * @return a reference to this Builder
     */
    ErrorMessageBuilder withCorrelationId(String correlationId) {
        this.correlationId = correlationId;
        return this;
    }

    /**
     * Sets the topic and returns a reference to this Builder so that the methods can be chained together.
     *
     * @param topic the topic to set
     * @return a reference to this Builder
     */
    ErrorMessageBuilder withTopic(String topic) {
        this.topic = topic;
        return this;
    }

    void sendVia(IKafkaProducerService producerService) {
        ErrorData errorData = new ErrorData(errorType, message, description);
        ErrorMessage error = new ErrorMessage(errorData, System.currentTimeMillis(), correlationId);
        producerService.sendMessageAndTrack(topic, correlationId, error);
    }
}
