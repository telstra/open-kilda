package org.bitbucket.openkilda.northbound.service;

import static org.bitbucket.openkilda.messaging.Utils.CORRELATION_ID;
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
