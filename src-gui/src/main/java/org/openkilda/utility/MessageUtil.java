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

package org.openkilda.utility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * The Class MessageUtil.
 */
@Component
public final class MessageUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageUtil.class);

    public static final String CODE = ".code";
    public static final String MESSAGE = ".message";
    public static final String AUXILARY = ".auxilary";

    private static Properties messages;

    static {
        messages = loadPropertiesFile("error-messages.properties");
    }

    private MessageUtil() {
        
    }
    
    /**
     * Static Method to load property values in {@link Properties} instance.
     *
     * @param fileName {@link String}
     */
    public static Properties loadPropertiesFile(final String fileName) {
        Properties localProperties = new Properties();
        try {
            localProperties.load(MessageUtil.class.getClassLoader().getResourceAsStream(
                    fileName));
        } catch (Exception e) {
            LOGGER.warn("Load properties file. Exception : ", e);
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
