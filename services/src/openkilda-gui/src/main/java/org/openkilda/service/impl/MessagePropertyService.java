package org.openkilda.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * The Class AuthPropertyService.
 */
@Component
public class MessagePropertyService {
    private static final Logger _log = LoggerFactory.getLogger(MessagePropertyService.class);

    public static final String CODE = ".code";
    public static final String MESSAGE = ".message";
    public static final String AUXILARY = ".auxilary";

    private static Properties messages;

    static {
        messages = loadPropertiesFile("error-messages.properties");
    }

    /**
     * Static Method to load property values in {@link Properties} instance.
     * 
     * @param fileName {@link String}
     */
    public static Properties loadPropertiesFile(final String fileName) {
        Properties localProperties = new Properties();
        try {
            localProperties.load(MessagePropertyService.class.getClassLoader().getResourceAsStream(
                    fileName));
        } catch (Exception e) {
            _log.error("[loadPropertiesFile] Exception : ", e);
        }
        return localProperties;
    }

    /**
     * Gets the message.
     *
     * @param msg the msg
     * @return the message
     */
    public static String getCode(final String msg) {
        return messages.getProperty(msg + CODE);
    }

    /**
     * Gets the message.
     *
     * @param msg the msg
     * @return the message
     */
    public static String getMessage(final String msg) {
        return messages.getProperty(msg + MESSAGE);
    }

    /**
     * Gets the message.
     *
     * @param msg the msg
     * @return the message
     */
    public static String getAuxilaryMessage(final String msg) {
        return messages.getProperty(msg + AUXILARY);
    }

    /**
     * Gets the message.
     *
     * @param msg the msg
     * @return the message
     */
    public static String getPropertyValue(final String msg) {
        return messages.getProperty(msg);
    }
}
