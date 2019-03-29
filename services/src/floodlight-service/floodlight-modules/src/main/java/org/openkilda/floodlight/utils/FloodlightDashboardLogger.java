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

package org.openkilda.floodlight.utils;

import org.openkilda.model.SwitchId;

import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.event.Level;

import java.util.HashMap;
import java.util.Map;

public class FloodlightDashboardLogger {

    private static final String TAG = "FLOODLIGHT-DASHBOARD-TAG";

    private static final String SWITCH_ID = "switch_id";
    private static final String PORT = "port";
    private static final String STATE = "state";
    private static final String TYPE = "event_type";

    private final Logger logger;

    public FloodlightDashboardLogger(Logger logger) {
        this.logger = logger;
    }


    /**
     * Log a port change status event.
     *
     * @param switchId a switch id.
     * @param portNumber a port number.
     * @param state a state.
     */
    public void onPortEvent(Level level, SwitchId switchId, int portNumber, String state) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "of-dashboard-event");
        data.put(TYPE, "port");
        data.put(SWITCH_ID, switchId.toString());
        data.put(PORT, String.valueOf(portNumber));
        data.put(STATE, state);
        String message = String.format("OF port event (%s-%s - %s)", switchId, portNumber, state);
        proceed(level, message, data);
    }

    /**
     * Log a switch change status event.
     *
     * @param switchId a switch id.
     * @param state a state.
     */
    public void onSwitchEvent(Level level, String message, SwitchId switchId, String state) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "of-dashboard-event");
        data.put(TYPE, "switch");
        data.put(SWITCH_ID, switchId.toString());
        data.put(STATE, state);
        proceed(level, message, data);
    }

    /**
     * Build and write log message and MDC custom fields.
     *
     * @param level a log level.
     * @param message a message text.
     * @param logData a data for MDC custom fields.
     */
    protected void proceed(Level level, String message, Map<String, String> logData) {
        Map<String, String> oldValues = MDC.getCopyOfContextMap();
        logData.forEach(MDC::put);
        try {
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
        } finally {
            logData.forEach((key, value) -> MDC.remove(key));
            oldValues.forEach(MDC::put);
        }
    }
}
