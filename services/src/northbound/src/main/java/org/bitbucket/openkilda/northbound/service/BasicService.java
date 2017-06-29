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
