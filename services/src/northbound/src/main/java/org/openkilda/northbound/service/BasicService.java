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

package org.openkilda.northbound.service;

import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.messaging.error.ErrorType.INTERNAL_ERROR;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Basic service interface.
 */
public interface BasicService {
    /**
     * The logger.
     */
    Logger logger = LoggerFactory.getLogger(BasicService.class);

    /**
     * Validates info message.
     *
     * @param correlationId     request correlation id
     * @param requestMessage    request command message
     * @param responseMessage   response info message
     * @return parsed response InfoData
     */
    default InfoData validateInfoMessage(final Message requestMessage,
                                         final Message responseMessage,
                                         final String correlationId) {
        InfoData data;
        if (responseMessage != null) {
            if (responseMessage instanceof ErrorMessage) {
                ErrorData error = ((ErrorMessage) responseMessage).getData();
                logger.error("Response message is error: {}={}, command={}, error={}",
                        CORRELATION_ID, correlationId, requestMessage, error);
                throw new MessageException((ErrorMessage) responseMessage);
            } else if (responseMessage instanceof InfoMessage) {
                InfoMessage info = (InfoMessage) responseMessage;
                data = info.getData();
                if (data == null) {
                    String errorMessage = "Response message data is empty";
                    logger.error("{}: {}={}, command={}, info={}", errorMessage,
                            CORRELATION_ID, correlationId, requestMessage, info);
                    throw new MessageException(responseMessage.getCorrelationId(), responseMessage.getTimestamp(),
                            INTERNAL_ERROR, errorMessage, requestMessage.toString());
                }
            } else {
                String errorMessage = "Response message type is unexpected";
                logger.error("{}: {}:{}, command={}, message={}", errorMessage,
                        CORRELATION_ID, correlationId, requestMessage, responseMessage);
                throw new MessageException(responseMessage.getCorrelationId(), responseMessage.getTimestamp(),
                        INTERNAL_ERROR, errorMessage, requestMessage.toString());
            }
        } else {
            String errorMessage = "Response message is empty";
            logger.error("{}: {}={}, command={}", errorMessage,
                    CORRELATION_ID, correlationId, requestMessage);
            throw new MessageException(requestMessage.getCorrelationId(), System.currentTimeMillis(),
                    INTERNAL_ERROR, errorMessage, requestMessage.toString());
        }
        return data;
    }
}
