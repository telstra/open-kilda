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

package org.openkilda.wfm.topology.network;

import org.openkilda.model.SwitchId;
import org.openkilda.wfm.share.utils.AbstractLogWrapper;
import org.openkilda.wfm.topology.network.controller.sw.AbstractPort;
import org.openkilda.wfm.topology.network.model.Endpoint;
import org.openkilda.wfm.topology.network.model.IslReference;
import org.openkilda.wfm.topology.network.model.LinkStatus;

import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.util.HashMap;
import java.util.Map;

public class NetworkTopologyDashboardLogger extends AbstractLogWrapper {

    private static final String SWITCH_ID = "switch_id";
    private static final String SRC_SWITCH = "src_switch";
    private static final String DST_SWITCH = "dst_switch";
    private static final String PORT = "port";
    private static final String SRC_PORT = "src_port";
    private static final String DST_PORT = "dst_port";
    private static final String STATE = "state";
    private static final String TYPE = "event_type";
    private static final String SRC_SWITCH_PORT = "src_switch_port";
    private static final String DST_SWITCH_PORT = "dst_switch_port";
    private static final String PORT_TYPE = "port_type";
    private static final String SWITCH_PORT = "switch_port";
    private static final String HOST = "host_name";

    private static final String TAG = "SWITCH_PORT_ISL_DASHBOARD";

    public NetworkTopologyDashboardLogger(Logger logger) {
        super(logger);
    }

    /**
     * Log a port add event.
     *
     * @param switchId a switch ID.
     * @param port a port number.
     */
    public void onPortAdd(SwitchId switchId, AbstractPort port) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "switch-port-isl");
        data.put(TYPE, PORT);
        data.put(SWITCH_ID, switchId.toString());
        data.put(PORT, String.valueOf(port.getPortNumber()));
        data.put(STATE, "add");
        data.put(PORT_TYPE, port.getLogIdentifier());
        data.put(SWITCH_PORT, port.getLogEndpoint());
        proceed(Level.INFO, String.format("Add port %d on switch %s", port.getPortNumber(), switchId), data);
    }

    /**
     * Log a port delete event.
     *
     * @param switchId a switch ID.
     * @param port a port number.
     */
    public void onPortDelete(SwitchId switchId, AbstractPort port) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "switch-port-isl");
        data.put(TYPE, PORT);
        data.put(SWITCH_ID, switchId.toString());
        data.put(PORT, String.valueOf(port.getPortNumber()));
        data.put(STATE, "delete");
        data.put(PORT_TYPE, port.getLogIdentifier());
        data.put(SWITCH_PORT, port.getLogEndpoint());
        proceed(Level.INFO, String.format("Delete port %d on switch %s", port.getPortNumber(), switchId), data);
    }

    /**
     * Log a port changing state event.
     *
     * @param endpoint an endpoint.
     * @param linkStatus a port status.
     */
    public void onUpdatePortStatus(Endpoint endpoint, LinkStatus linkStatus) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "switch-port-isl");
        data.put(TYPE, PORT);
        data.put(SWITCH_ID, endpoint.getDatapath().toString());
        data.put(PORT, String.valueOf(endpoint.getPortNumber()));
        data.put(STATE, linkStatus.toString());
        data.put(SWITCH_PORT, endpoint.toString());
        String message = String.format("Port status event: switch_id=%s, port_id=%d, state=%s",
                endpoint.getDatapath().toString(), endpoint.getPortNumber(), linkStatus);
        proceed(Level.INFO, message, data);
    }

    /**
     * Log an ISL changing state event.
     *
     * @param ref an ISL path reference.
     * @param state an ISL status.
     */
    public void onIslUpdateStatus(IslReference ref, String state) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "switch-port-isl");
        data.put(TYPE, "isl");
        data.put(SRC_SWITCH, ref.getSource().getDatapath().toString());
        data.put(DST_SWITCH, ref.getDest().getDatapath().toString());
        data.put(STATE, state);
        data.put(SRC_PORT, String.valueOf(ref.getSource().getPortNumber()));
        data.put(DST_PORT, String.valueOf(ref.getDest().getPortNumber()));
        data.put(SRC_SWITCH_PORT, ref.getSource().toString());
        data.put(DST_SWITCH_PORT, ref.getDest().toString());
        String message = String.format("ISL between %s and %s changed status to: %s",
                ref.getSource().toString(),
                ref.getDest().toString(),
                state);
        proceed(Level.INFO, message, data);
    }

    /**
     * Log a Switch status event.
     *
     * @param switchId a switch ID.
     * @param state a switch state.
     */
    public void onSwitchUpdateStatus(SwitchId switchId, String state, String hostName) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "switch-port-isl");
        data.put(TYPE, "switch");
        data.put(SWITCH_ID, switchId.toString());
        data.put(STATE, state);
        data.put(HOST, hostName);
        proceed(Level.INFO, String.format("Switch '%s' (%s) change status to '%s'", hostName, switchId, state), data);
    }

    /**
     * Log on a switch add event.
     *
     * @param switchId a switch ID.
     */
    public void onSwitchAdd(SwitchId switchId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "switch-port-isl");
        data.put(TYPE, "switch");
        data.put(SWITCH_ID, switchId.toString());
        data.put(STATE, "add");
        proceed(Level.INFO, String.format("Switch '%s' connected", switchId), data);
    }

    /**
     * Log on a switch delete event.
     *
     * @param switchId a switch ID.
     */
    public void onSwitchDelete(SwitchId switchId, String hostName) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "switch-port-isl");
        data.put(TYPE, "switch");
        data.put(SWITCH_ID, switchId.toString());
        data.put(STATE, "delete");
        data.put(HOST, hostName);
        proceed(Level.INFO, String.format("Switch '%s' (%s) has been deleted", hostName, switchId), data);
    }
}
