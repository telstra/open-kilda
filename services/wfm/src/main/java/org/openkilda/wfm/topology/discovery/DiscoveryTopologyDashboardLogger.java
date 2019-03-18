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

package org.openkilda.wfm.topology.discovery;

import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.discovery.model.IslReference;
import org.openkilda.wfm.topology.discovery.model.LinkStatus;
import org.openkilda.wfm.topology.utils.AbstractLogWrapper;

import lombok.EqualsAndHashCode;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
public class DiscoveryTopologyDashboardLogger extends AbstractLogWrapper {

    private static final String SWITCH_ID = "switch_id";
    private static final String SRC_SWITCH = "src_switch";
    private static final String DST_SWITCH = "dst_switch";
    private static final String PORT = "port";
    private static final String SRC_PORT = "src_port";
    private static final String DST_PORT = "dst_port";
    private static final String STATE = "state";
    private static final String TYPE = "event_type";
    private static final String EVENT = "event";

    private static final String TAG = "SWITCH_PORT_ISL_DASHBOARD";

    public DiscoveryTopologyDashboardLogger(Logger logger) {
        super(logger);
    }

    /**
     * Log a port add event.
     *
     * @param level a log level.
     * @param message a log message.
     * @param switchId a switch ID.
     * @param port a port number.
     */
    public void onPortAdd(Level level, String message, SwitchId switchId, int port) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "Port add");
        data.put(TYPE, PORT);
        data.put(SWITCH_ID, switchId.toString());
        data.put(PORT, String.valueOf(port));
        proceed(level, message, data);
    }

    /**
     * Log a port delete event.
     *
     * @param level a log level.
     * @param message a log message.
     * @param switchId a switch ID.
     * @param port a port number.
     */
    public void onPortDelete(Level level, String message, SwitchId switchId, int port) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "Port delete");
        data.put(TYPE, PORT);
        data.put(SWITCH_ID, switchId.toString());
        data.put(PORT, String.valueOf(port));
        proceed(level, message, data);
    }

    /**
     * Log a port online state event.
     *
     * @param level a log level.
     * @param message a log message.
     * @param switchId a switch ID.
     * @param port a port number.
     * @param state a port online status.
     */
    public void onUpdatePortOnlineStatus(Level level, String message, SwitchId switchId, int port, String state) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "Port status online");
        data.put(TYPE, PORT);
        data.put(SWITCH_ID, switchId.toString());
        data.put(PORT, String.valueOf(port));
        data.put(STATE, state);
        proceed(level, message, data);
    }

    /**
     * Log a port changing state event.
     *
     * @param level a log level.
     * @param message a log message.
     * @param switchId a switch ID.
     * @param port a port number.
     * @param linkStatus a port status.
     */
    public void onUpdatePortStatus(Level level, String message, SwitchId switchId, int port, LinkStatus linkStatus) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "Port status");
        data.put(TYPE, PORT);
        data.put(SWITCH_ID, switchId.toString());
        data.put(PORT, String.valueOf(port));
        data.put(STATE, linkStatus.toString());
        proceed(level, message, data);
    }

    /**
     * Log an ISL changing state event.
     *
     * @param level a log level.
     * @param message a log message.
     * @param ref an ISL path reference.
     * @param state an ISL status.
     */
    public void onIslUpdateStatus(Level level, String message, IslReference ref, String state) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "Isl status");
        data.put(TYPE, "isl");
        data.put(SRC_SWITCH, ref.getSource().getDatapath().toString());
        data.put(DST_SWITCH, ref.getDest().getDatapath().toString());
        data.put(STATE, state);
        data.put(SRC_PORT, String.valueOf(ref.getSource().getPortNumber()));
        data.put(DST_PORT, String.valueOf(ref.getDest().getPortNumber()));
        proceed(level, message, data);
    }

    /**
     * Log a Switch status event.
     *
     * @param level a log level.
     * @param message a log message.
     * @param switchId a switch ID.
     * @param state a switch state.
     */
    public void onSwitchUpdateStatus(Level level, String message, SwitchId switchId, String state) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "Switch status");
        data.put(TYPE, "switch");
        data.put(SWITCH_ID, switchId.toString());
        data.put(STATE, state);
        proceed(level, message, data);
    }

    /**
     * Log on a switch add event.
     *
     * @param level a log level.
     * @param message a log message.
     * @param switchId a switch ID.
     */
    public void onSwitchAdd(Level level, String message, SwitchId switchId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "Switch add");
        data.put(TYPE, "switch");
        data.put(SWITCH_ID, switchId.toString());
        data.put(EVENT, "add");
        proceed(level, message, data);
    }

    /**
     * Log on a switch delete event.
     *
     * @param level a log level.
     * @param message a log message.
     * @param switchId a switch ID.
     */
    public void onSwitchDelete(Level level, String message, SwitchId switchId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "Switch delete");
        data.put(TYPE, "switch");
        data.put(SWITCH_ID, switchId.toString());
        data.put(EVENT, "delete");
        proceed(level, message, data);
    }
}
