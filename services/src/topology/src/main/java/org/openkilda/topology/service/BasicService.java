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

package org.openkilda.topology.service;

import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.messaging.error.ErrorType.INTERNAL_ERROR;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandMessage;
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
                throw new MessageException(error.getErrorType(), message.getTimestamp());
            } else if (message instanceof InfoMessage) {
                InfoMessage info = (InfoMessage) message;
                data = info.getData();
                if (data == null) {
                    logger.error("Response message data is empty: {}={}, command={}, info={}",
                            CORRELATION_ID, correlationId, commandMessage, info);
                    throw new MessageException(INTERNAL_ERROR, message.getTimestamp());
                }
            } else {
                logger.error("Response message type is unexpected: {}:{}, command={}, message={}",
                        CORRELATION_ID, correlationId, commandMessage, message);
                throw new MessageException(INTERNAL_ERROR, message.getTimestamp());
            }
        } else {
            logger.error("Response message is empty: {}={}, command={}",
                    CORRELATION_ID, correlationId, commandMessage);
            throw new MessageException(INTERNAL_ERROR, System.currentTimeMillis());
        }
        return data;
    }
}
