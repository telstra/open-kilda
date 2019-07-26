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

import org.openkilda.messaging.info.event.SwitchChangeType;

import net.floodlightcontroller.core.PortChangeType;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

public class FloodlightDashboardLogger {
    private static final String FIELD_STATE = "state";

    private final Logger logger;

    public FloodlightDashboardLogger(Logger logger) {
        this.logger = logger;
    }

    /**
     * Log a port change status event.
     *
     * @param dpId a switch id.
     * @param portDesc port descriptor.
     * @param type a port state.
     */
    public void onPortEvent(DatapathId dpId, OFPortDesc portDesc, PortChangeType type) {
        OFPort port = portDesc.getPortNo();
        Map<String, String> context = makePortContext(dpId, port);
        context.put(FIELD_STATE, type.toString());

        proceed(String.format("OF port event (%s-%s - %s): %s", dpId, port, type, portDesc), context);
    }

    /**
     * Log a switch change status event.
     *
     * @param dpId a switch id.
     * @param state a switch state.
     */
    public void onSwitchEvent(DatapathId dpId, SwitchChangeType state) {
        Map<String, String> context = makeSwitchContext(dpId);
        context.put(FIELD_STATE, state.toString());

        proceed(String.format("OF switch event (%s - %s)", dpId, state), context);
    }

    /**
     * Build and write log message and MDC custom fields.
     *  @param message a message text.
     * @param logData a data for MDC custom fields.
     */
    protected void proceed(String message, Map<String, String> logData) {
        Map<String, String> oldValues = MDC.getCopyOfContextMap();
        logData.forEach(MDC::put);
        try {
            logger.info(message);
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

    private Map<String, String> makeSwitchContext(DatapathId dpId) {
        return makeContextTemplate(dpId, EventType.SWITCH);
    }

    private Map<String, String> makePortContext(DatapathId dpid, OFPort port) {
        Map<String, String> data = makeContextTemplate(dpid, EventType.PORT);
        data.put("port", port.toString());
        return data;
    }

    private Map<String, String> makeContextTemplate(DatapathId dpId, EventType event) {
        Map<String, String> data = new HashMap<>();
        data.put("FLOODLIGHT-DASHBOARD-TAG", "of-dashboard-event");
        data.put("switch_id", dpId.toString());
        data.put("event_type", event.toString());
        return data;
    }

    enum EventType {
        SWITCH,
        PORT
    }
}
