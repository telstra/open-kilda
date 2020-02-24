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
import org.openkilda.model.SwitchId;
import org.openkilda.reporting.AbstractDashboardLogger;

import net.floodlightcontroller.core.PortChangeType;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class FloodlightDashboardLogger extends AbstractDashboardLogger {
    public FloodlightDashboardLogger(Logger logger) {
        super(logger);
    }

    /**
     * Log a port change status event.
     *
     * @param dpId a switch id.
     * @param portDesc port descriptor.
     * @param type a port state.
     */
    public void onPortEvent(DatapathId dpId, OFPortDesc portDesc, PortChangeType type) {
        // ensure "correct"(same to other dashboard loggers) event naming
        switch (type) {
            case UP:
                onPortUpDown(dpId, portDesc, "UP");
                break;
            case DOWN:
                onPortUpDown(dpId, portDesc, "DOWN");
                break;
            default:
                // other events are logged only by FL so it's naming have no "correct" definition yet
                opPortStatusChange(dpId, portDesc, type);
        }
    }

    /**
     * Log a switch change status event.
     *
     * @param dpId a switch id.
     * @param state a switch state.
     */
    public void onSwitchEvent(DatapathId dpId, SwitchChangeType state) {
        Map<String, String> context = makeSwitchContext(dpId);
        populateState(context, state.toString());

        invokeLogger(String.format("OF switch event (%s - %s)", dpId, state), context);
    }

    private void onPortUpDown(DatapathId dpId, OFPortDesc portDesc, String event) {
        final OFPort ofPort = portDesc.getPortNo();
        Map<String, String> context = makePortContext(dpId, ofPort);
        populateState(context, event);

        String switchPort = formatSwitchPort(dpId, ofPort);
        invokeLogger(String.format("OF port event %s for %s: %s", event, switchPort, portDesc), context);
    }

    private void opPortStatusChange(DatapathId dpId, OFPortDesc portDesc, PortChangeType type) {
        OFPort ofPort = portDesc.getPortNo();
        Map<String, String> context = makePortContext(dpId, ofPort);
        populateState(context, type.toString());

        String switchPort = formatSwitchPort(dpId, ofPort);
        invokeLogger(String.format("OF port event %s for %s: %s", type, switchPort, portDesc), context);
    }

    private void populateState(Map<String, String> context, String event) {
        context.put("state", event);
    }

    private Map<String, String> makeSwitchContext(DatapathId dpId) {
        return makeContextTemplate(dpId, "switch");
    }

    private Map<String, String> makePortContext(DatapathId dpId, OFPort port) {
        Map<String, String> context = makeContextTemplate(dpId, "port");
        context.put("port", String.valueOf(port.getPortNumber()));
        context.put("switch_port", formatSwitchPort(dpId, port));
        return context;
    }

    private Map<String, String> makeContextTemplate(DatapathId dpId, String eventType) {
        Map<String, String> context = new HashMap<>();

        // TODO(surabujin): drop `FLOODLIGHT-DASHBOARD-TAG` field after dashboard query switch to the `dashboard` field
        context.put("FLOODLIGHT-DASHBOARD-TAG", "of-dashboard-event");

        context.put("dashboard", "switch-port-isl");
        context.put("switch_id", dpId.toString());
        context.put("event_type", eventType);
        return context;
    }

    private String formatSwitchPort(DatapathId dpId, OFPort ofPort) {
        SwitchId swId = new SwitchId(dpId.getLong());
        return String.format("%s_%d", swId, ofPort.getPortNumber());
    }
}
