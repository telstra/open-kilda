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

package org.openkilda.wfm.share.logger;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.share.utils.AbstractLogWrapper;

import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class FlowOperationsDashboardLogger extends AbstractLogWrapper {

    private static final String FLOW_ID = "flow_id";
    private static final String EVENT_TYPE = "event_type";
    private static final String FLOW_READ_EVENT = "flow_read";
    private static final String FLOW_CREATE_EVENT = "flow_create";
    private static final String FLOW_UPDATE_EVENT = "flow_update";
    private static final String FLOW_DELETE_EVENT = "flow_delete";
    private static final String STATUS_UPDATE_EVENT = "status_update";
    private static final String STATUS = "status";

    private static final String TAG = "FLOW_OPERATIONS_DASHBOARD";

    public FlowOperationsDashboardLogger(Logger logger) {
        super(logger);
    }

    /**
     * Log a flow-dump event.
     */
    public void onFlowDump() {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-dump");
        data.put(EVENT_TYPE, FLOW_READ_EVENT);
        proceed(Level.INFO, String.format("Dump flows"), data);
    }

    /**
     * Log a flow-dump-by-link event.
     */
    public void onFlowPathsDumpByLink(SwitchId srcSwitchId, Integer srcPort,
                                      SwitchId dstSwitchId, Integer dstPort) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-dump-by-link");
        data.put(EVENT_TYPE, FLOW_READ_EVENT);
        proceed(Level.INFO, String.format("Dump flows by link %s_%d-%s_%d", srcSwitchId, srcPort,
                dstSwitchId, dstPort), data);
    }

    /**
     * Log a flow-dump-by-endpoint event.
     */
    public void onFlowPathsDumpByEndpoint(SwitchId switchId, Integer port) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-dump-by-endpoint");
        data.put(EVENT_TYPE, FLOW_READ_EVENT);
        proceed(Level.INFO, String.format("Dump flows by end-point %s_%d", switchId, port), data);
    }

    /**
     * Log a flow-dump-by-switch event.
     */
    public void onFlowPathsDumpBySwitch(SwitchId switchId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-dump-by-switch");
        data.put(EVENT_TYPE, FLOW_READ_EVENT);
        proceed(Level.INFO, String.format("Dump flows by switch %s", switchId), data);
    }

    /**
     * Log a flow-read event.
     */
    public void onFlowRead(String flowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-read");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, FLOW_READ_EVENT);
        proceed(Level.INFO, String.format("Read the flow %s", flowId), data);
    }

    /**
     * Log a flow-paths-read event.
     */
    public void onFlowPathsRead(String flowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-paths-read");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, FLOW_READ_EVENT);
        proceed(Level.INFO, String.format("Read paths of the flow %s", flowId), data);
    }

    /**
     * Log a flow-create event.
     */
    public void onFlowCreate(Flow flow) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-create");
        data.put(FLOW_ID, flow.getFlowId());
        data.put(EVENT_TYPE, FLOW_CREATE_EVENT);
        proceed(Level.INFO, String.format("Create the flow: %s", flow), data);
    }

    /**
     * Log a flow-push event.
     */
    public void onFlowPush(Flow flow) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-push");
        data.put(FLOW_ID, flow.getFlowId());
        data.put(EVENT_TYPE, FLOW_CREATE_EVENT);
        proceed(Level.INFO, String.format("Push the flow: %s", flow), data);
    }

    /**
     * Log a flow-status-update event.
     */
    public void onFlowStatusUpdate(String flowId, FlowStatus status) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-status-update");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, STATUS_UPDATE_EVENT);
        data.put(STATUS, status.toString());
        proceed(Level.INFO, String.format("Update the status of the flow %s to %s", flowId, status), data);
    }

    /**
     * Log a flow-update event.
     */
    public void onFlowUpdate(Flow flow) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-update");
        data.put(FLOW_ID, flow.getFlowId());
        data.put(EVENT_TYPE, FLOW_UPDATE_EVENT);
        proceed(Level.INFO, String.format("Update the flow: %s", flow), data);
    }

    /**
     * Log a flow-patch-update event.
     */
    public void onFlowPatchUpdate(Flow flow) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-patch-update");
        data.put(FLOW_ID, flow.getFlowId());
        data.put(EVENT_TYPE, FLOW_UPDATE_EVENT);
        proceed(Level.INFO, String.format("Patch update the flow: %s", flow), data);
    }

    /**
     * Log a flow-delete event.
     */
    public void onFlowDelete(String flowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-delete");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, FLOW_DELETE_EVENT);
        proceed(Level.INFO, String.format("Delete the flow %s", flowId), data);
    }

    /**
     * Log a flow-endpoint-swap event.
     */
    public void onFlowEndpointSwap(Flow firstFlow, Flow secondFlow) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-endpoint-swap");
        data.put(FLOW_ID, firstFlow.getFlowId());
        data.put(EVENT_TYPE, FLOW_UPDATE_EVENT);
        proceed(Level.INFO, String.format("Swap end-points for the flows %s / %s", firstFlow, secondFlow), data);
    }

    /**
     * Log a flow-paths-swap event.
     */
    public void onFlowPathsSwap(Flow flow) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-paths-swap");
        data.put(FLOW_ID, flow.getFlowId());
        data.put(EVENT_TYPE, FLOW_UPDATE_EVENT);
        proceed(Level.INFO, String.format("Swap paths for the flow: %s", flow), data);
    }

    /**
     * Log a flow-paths-reroute event.
     */
    public void onFlowPathReroute(String flowId, Collection<PathId> pathIds, boolean forceToReroute) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-paths-reroute");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, FLOW_UPDATE_EVENT);
        data.put("forced_reroute", Boolean.toString(forceToReroute));
        proceed(Level.INFO, String.format("Reroute paths %s of the flow %s", pathIds, flowId), data);
    }
}
