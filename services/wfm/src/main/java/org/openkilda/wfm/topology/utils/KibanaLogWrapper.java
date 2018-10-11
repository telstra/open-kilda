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

package org.openkilda.wfm.topology.utils;

import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.model.SwitchId;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

/**
 * The KIBANA dashboard log wrapper.
 */
@Slf4j
public class KibanaLogWrapper {

    private static final String SWITCH_ID = "switch_id";
    private static final String SRC_SWITCH = "src_switch";
    private static final String PORT = "port";
    private static final String SRC_PORT = "src_port";
    private static final String DST_PORT = "dst_port";
    private static final String STATE = "state";

    /**
     * The Kibana dashboard tag.
     */
    private static final String TAG = "KIBANA_DASHBOARD_TAG";

    /**
     * Log a switch discovery event with dashboard tags.
     *
     * @param switchId a switch id.
     * @param state a state of switch.
     */
    public void onSwitchDiscovery(SwitchId switchId, SwitchChangeType state) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "DISCO: Switch Event");
        data.put(SWITCH_ID, switchId.toString());
        data.put(STATE, state.toString());
        proceed("Switch event", data);
    }

    /**
     * Log a port discovery event with dashboard tags.
     *
     * @param switchId a switch id.
     * @param portId a port id.
     * @param state a port state.
     */
    public void onPortDiscovery(SwitchId switchId, int portId, String state) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "DISCO: Port Event");
        data.put(SWITCH_ID, switchId.toString());
        data.put(PORT, String.valueOf(portId));
        data.put(STATE, state);
        proceed("Port discovery", data);
    }

    /**
     * Log an ISL Discovery when loop detected event, with dashboard tags.
     *
     * @param srcSwitch a source switch id.
     * @param srcPort a source port id.
     * @param dstPort a destination port id.
     * @param state an ISL state.
     */
    public void onIslDiscoveryLoop(SwitchId srcSwitch, int srcPort, int dstPort, IslChangeType state) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "DISCO: ISL Event");
        data.put(SRC_SWITCH, srcSwitch.toString());
        data.put(SRC_PORT, String.valueOf(srcPort));
        data.put(DST_PORT, String.valueOf(dstPort));
        data.put(STATE, state.toString());
        proceed("Loop detected", data);
    }

    /**
     * Log an ISL Discovery when state changed event, with dashboard tags.
     *
     * @param srcSwitch a source switch id.
     * @param srcPort a source port id.
     * @param dstPort a destination port id.
     * @param state an ISL state.
     */
    public void onIslDiscovery(SwitchId srcSwitch, int srcPort, int dstPort, IslChangeType state) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "DISCO: ISL Event");
        data.put(SRC_SWITCH, srcSwitch.toString());
        data.put(STATE, state.toString());
        data.put(SRC_PORT, String.valueOf(srcPort));
        data.put(DST_PORT, String.valueOf(dstPort));
        proceed("State changed", data);
    }

    /**
     * Build and write log message and MDC custom fields.
     *
     * @param message a message text.
     * @param logData a data for MDC custom fields.
     */
    private void proceed(String message, Map<String, String> logData) {
        Map<String, String> oldValues = MDC.getCopyOfContextMap();
        logData.forEach(MDC::put);
        try {
            log.info(message);
        } finally {
            logData.forEach((key, value) -> MDC.remove(key));
            oldValues.forEach(MDC::put);
        }
    }
}
