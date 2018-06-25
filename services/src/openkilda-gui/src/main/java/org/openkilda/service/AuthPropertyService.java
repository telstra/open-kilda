package org.openkilda.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import org.openkilda.model.response.Error;

/**
 * The Class AuthPropertyService.
 */
@Component
public class AuthPropertyService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthPropertyService.class);

    public static final String CODE = ".code";
    public static final String MESSAGE = ".message";

    @Autowired
    private Environment authMessages;

    /**
     * Returns error code and message from the properties file.
     *
     * @param errorMsg whose code and message are requested.
     * @return error message and code.
     */
    public Error getError(final String errorMsg) {
        LOGGER.info("[getError] Error message: " + errorMsg);
        String errorMessageCode = authMessages.getProperty(errorMsg + CODE);
        String errorMessage = authMessages.getProperty(errorMsg + MESSAGE);
        return new Error(Integer.valueOf(errorMessageCode), errorMessage);
    }

    /**
     * Gets the message.
     *
     * @param msg the msg
     * @return the message
     */
    public String getMessage(final String msg) {
        LOGGER.info("[getMessage] Message: " + msg);
        return authMessages.getProperty(msg);
    }
}
