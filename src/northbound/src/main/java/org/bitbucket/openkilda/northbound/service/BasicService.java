package org.bitbucket.openkilda.northbound.service;

import static org.bitbucket.openkilda.messaging.error.ErrorType.INTERNAL_ERROR;
import static org.bitbucket.openkilda.northbound.utils.Constants.CORRELATION_ID;

import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.error.ErrorData;
import org.bitbucket.openkilda.messaging.error.ErrorMessage;
import org.bitbucket.openkilda.messaging.info.InfoData;
import org.bitbucket.openkilda.messaging.info.InfoMessage;
import org.bitbucket.openkilda.northbound.utils.NorthboundException;

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
                throw new NorthboundException(INTERNAL_ERROR, message.getTimestamp());
            } else if (message instanceof InfoMessage) {
                InfoMessage info = (InfoMessage) message;
                data = info.getData();
                if (data == null) {
                    logger.error("Response message data is empty: {}={}, command={}, info={}",
                            CORRELATION_ID, correlationId, commandMessage, info);
                    throw new NorthboundException(INTERNAL_ERROR, message.getTimestamp());
                }
            } else {
                logger.error("Response message type is unexpected: {}:{}, command={}, message={}",
                        CORRELATION_ID, correlationId, commandMessage, message);
                throw new NorthboundException(INTERNAL_ERROR, message.getTimestamp());
            }
        } else {
            logger.error("Response message is empty: {}={}, command={}",
                    CORRELATION_ID, correlationId, commandMessage);
            throw new NorthboundException(INTERNAL_ERROR, System.currentTimeMillis());
        }
        return data;
    }
}
