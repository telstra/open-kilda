/* Copyright 2019 Telstra Open Source
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

package org.openkilda.reporting;

import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.event.Level;

import java.util.Map;

public abstract class AbstractDashboardLogger {

    protected final Logger logger;

    protected AbstractDashboardLogger(Logger logger) {
        this.logger = logger;
    }


    protected void invokeLogger(String message, Map<String, String> context) {
        invokeLogger(Level.INFO, message, context);
    }

    /**
     * Build and write log message and MDC custom fields.
     *
     * @param level a log level.
     * @param message a message text.
     * @param logData a data for MDC custom fields.
     */
    protected void invokeLogger(Level level, String message, Map<String, String> logData) {
        Map<String, String> oldValues = MDC.getCopyOfContextMap();
        logData.forEach(MDC::put);
        try {
            invokeLogger(level, message);
        } finally {
            for (String key : logData.keySet()) {
                String original = oldValues.get(key);
                if (original != null) {
                    MDC.put(key, original);
                } else {
                    MDC.remove(key);
                }
            }
        }
    }

    private void invokeLogger(Level level, String message) {
        switch (level) {
            case INFO:
                logger.info(message);
                break;
            case WARN:
                logger.warn(message);
                break;
            case ERROR:
                logger.error(message);
                break;
            case DEBUG:
                logger.debug(message);
                break;
            case TRACE:
                logger.trace(message);
                break;
            default:
                throw new IllegalArgumentException(String.format("Unhandled log level %s", level));
        }
    }
}
