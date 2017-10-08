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

package org.bitbucket.openkilda.northbound.service;

import static org.bitbucket.openkilda.messaging.Utils.CORRELATION_ID;
import static org.bitbucket.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;
import static org.bitbucket.openkilda.messaging.error.ErrorType.INTERNAL_ERROR;

import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.error.ErrorData;
import org.bitbucket.openkilda.messaging.error.ErrorMessage;
import org.bitbucket.openkilda.messaging.error.MessageException;
import org.bitbucket.openkilda.messaging.info.InfoData;
import org.bitbucket.openkilda.messaging.info.InfoMessage;

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
     * @param correlationId  request correlation id
     * @param commandMessage request command message
     * @param message        response info message
     * @return parsed response InfoData
     */
    default InfoData validateInfoMessage(final CommandMessage commandMessage,
                                         final Message message,
                                         final String correlationId) {
        InfoData data;
        if (message != null) {
            if (message instanceof ErrorMessage) {
                ErrorData error = ((ErrorMessage) message).getData();
                logger.error("Response message is error: {}={}, command={}, error={}",
                        CORRELATION_ID, correlationId, commandMessage, error);
                throw new MessageException((ErrorMessage) message);
            } else if (message instanceof InfoMessage) {
                InfoMessage info = (InfoMessage) message;
                data = info.getData();
                if (data == null) {
                    String errorMessage = "Response message data is empty";
                    logger.error("{}: {}={}, command={}, info={}", errorMessage,
                            CORRELATION_ID, correlationId, commandMessage, info);
                    throw new MessageException(message.getCorrelationId(), message.getTimestamp(),
                            INTERNAL_ERROR, errorMessage, commandMessage.toString());
                }
            } else {
                String errorMessage = "Response message type is unexpected";
                logger.error("{}: {}:{}, command={}, message={}", errorMessage,
                        CORRELATION_ID, correlationId, commandMessage, message);
                throw new MessageException(message.getCorrelationId(), message.getTimestamp(),
                        INTERNAL_ERROR, errorMessage, commandMessage.toString());
            }
        } else {
            String errorMessage = "Response message is empty";
            logger.error("{}: {}={}, command={}", errorMessage,
                    CORRELATION_ID, correlationId, commandMessage);
            throw new MessageException(DEFAULT_CORRELATION_ID, System.currentTimeMillis(),
                    INTERNAL_ERROR, errorMessage, commandMessage.toString());
        }
        return data;
    }
}
