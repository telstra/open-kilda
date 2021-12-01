/* Copyright 2021 Telstra Open Source
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
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.IslEndpoint;
import org.openkilda.model.SwitchId;
import org.openkilda.reporting.AbstractDashboardLogger;

import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FlowOperationsDashboardLogger extends AbstractDashboardLogger {

    private static final String FLOW_ID = "flow_id";
    private static final String EVENT_TYPE = "event_type";
    private static final String FLOW_READ_EVENT = "flow_read";
    private static final String FLOW_CREATE_EVENT = "flow_create";
    private static final String CREATE_RESULT_EVENT = "flow_create_result";
    private static final String FLOW_UPDATE_EVENT = "flow_update";
    private static final String UPDATE_RESULT_EVENT = "flow_update_result";
    private static final String FLOW_DELETE_EVENT = "flow_delete";
    private static final String DELETE_RESULT_EVENT = "flow_delete_result";
    private static final String PATHS_SWAP_EVENT = "paths_swap";
    private static final String REROUTE_EVENT = "flow_reroute";
    private static final String REROUTE_RESULT_EVENT = "flow_reroute_result";
    private static final String STATUS_UPDATE_EVENT = "status_update";
    private static final String FLOW_MIRROR_POINT_CREATE_EVENT = "flow_mirror_point_create";
    private static final String FLOW_MIRROR_POINT_CREATE_RESULT_EVENT = "flow_mirror_point_create_result";
    private static final String FLOW_MIRROR_POINT_DELETE_EVENT = "flow_mirror_point_delete";
    private static final String FLOW_MIRROR_POINT_DELETE_RESULT_EVENT = "flow_mirror_point_create_delete";

    private static final String YFLOW_CREATE_EVENT = "y_flow_create";
    private static final String YFLOW_CREATE_RESULT_EVENT = "y_flow_create_result";

    private static final String TAG = "FLOW_OPERATIONS_DASHBOARD";
    private static final String DASHBOARD = "dashboard";

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
        invokeLogger(Level.INFO, "Dump flows", data);
    }

    /**
     * Log a flow-dump-by-link event.
     */
    public void onFlowPathsDumpByLink(SwitchId srcSwitchId, Integer srcPort,
                                      SwitchId dstSwitchId, Integer dstPort) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-dump-by-link");
        data.put(EVENT_TYPE, FLOW_READ_EVENT);
        invokeLogger(Level.INFO, String.format("Dump flows by link %s_%d-%s_%d", srcSwitchId, srcPort,
                dstSwitchId, dstPort), data);
    }

    /**
     * Log a flow-dump-by-endpoint event.
     */
    public void onFlowPathsDumpByEndpoint(SwitchId switchId, Integer port) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-dump-by-endpoint");
        data.put(EVENT_TYPE, FLOW_READ_EVENT);
        invokeLogger(Level.INFO, String.format("Dump flows by end-point %s_%d", switchId, port), data);
    }

    /**
     * Log a flow-dump-by-switch event.
     */
    public void onFlowPathsDumpBySwitch(SwitchId switchId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-dump-by-switch");
        data.put(EVENT_TYPE, FLOW_READ_EVENT);
        invokeLogger(Level.INFO, String.format("Dump flows by switch %s", switchId), data);
    }

    /**
     * Log a flow-read event.
     */
    public void onFlowRead(String flowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-read");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, FLOW_READ_EVENT);
        invokeLogger(Level.INFO, String.format("Read the flow %s", flowId), data);
    }

    /**
     * Log a flow-paths-read event.
     */
    public void onFlowPathsRead(String flowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-paths-read");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, FLOW_READ_EVENT);
        invokeLogger(Level.INFO, String.format("Read paths of the flow %s", flowId), data);
    }

    /**
     * Log a flow-create event.
     */
    public void onFlowCreate(Flow flow) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-create");
        data.put(FLOW_ID, flow.getFlowId());
        data.put(EVENT_TYPE, FLOW_CREATE_EVENT);
        invokeLogger(Level.INFO, String.format("Create the flow: %s", flow), data);
    }

    /**
     * Log a flow-create event.
     */
    public void onFlowCreate(String flowId, SwitchId srcSwitch, int srcPort, int srcVlan,
                             SwitchId destSwitch, int destPort, int destVlan, String diverseFlowId, long bandwidth) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-create");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, FLOW_CREATE_EVENT);
        invokeLogger(Level.INFO, String.format("Create the flow: %s, source %s_%d_%d, destination %s_%d_%d, "
                        + "diverse flowId %s, bandwidth %d", flowId, srcSwitch, srcPort, srcVlan,
                destSwitch, destPort, destVlan, diverseFlowId, bandwidth), data);
    }

    /**
     * Log a flow-create-successful event.
     */
    public void onSuccessfulFlowCreate(String flowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-create-successful");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, CREATE_RESULT_EVENT);
        data.put("create-result", "successful");
        invokeLogger(Level.INFO, String.format("Successful create of the flow %s", flowId), data);
    }

    /**
     * Log a flow-create-failed event.
     */
    public void onFailedFlowCreate(String flowId, String failureReason) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-create-failed");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, CREATE_RESULT_EVENT);
        data.put("update-result", "failed");
        data.put("failure-reason", failureReason);
        invokeLogger(Level.WARN, String.format("Failed create of the flow %s, reason: %s", flowId, failureReason),
                data);
    }

    /**
     * Log a flow-push event.
     */
    public void onFlowPush(Flow flow) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-push");
        data.put(FLOW_ID, flow.getFlowId());
        data.put(EVENT_TYPE, FLOW_CREATE_EVENT);
        invokeLogger(Level.INFO, String.format("Push the flow: %s", flow), data);
    }

    /**
     * Log a flow-status-update event.
     */
    public void onFlowStatusUpdate(String flowId, FlowStatus status) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-status-update");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, STATUS_UPDATE_EVENT);
        data.put("status", status.toString());
        invokeLogger(Level.INFO, String.format("Update the status of the flow %s to %s", flowId, status), data);
    }

    /**
     * Log a flow-update event.
     */
    public void onFlowUpdate(Flow flow) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-update");
        data.put(FLOW_ID, flow.getFlowId());
        data.put(EVENT_TYPE, FLOW_UPDATE_EVENT);
        invokeLogger(Level.INFO, String.format("Update the flow: %s", flow), data);
    }

    /**
     * Log a flow-update event.
     */
    public void onFlowUpdate(String flowId, SwitchId srcSwitch, int srcPort, int srcVlan,
                             SwitchId destSwitch, int destPort, int destVlan, String diverseFlowId, long bandwidth) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-update");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, FLOW_UPDATE_EVENT);
        invokeLogger(Level.INFO, String.format("Update the flow %s with: source %s_%d_%d, destination %s_%d_%d, "
                        + "diverse flowId %s, bandwidth %d", flowId, srcSwitch, srcPort, srcVlan,
                destSwitch, destPort, destVlan, diverseFlowId, bandwidth), data);
    }

    /**
     * Log a flow-update-successful event.
     */
    public void onSuccessfulFlowUpdate(String flowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-update-successful");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, UPDATE_RESULT_EVENT);
        data.put("update-result", "successful");
        invokeLogger(Level.INFO, String.format("Successful update of the flow %s", flowId), data);
    }

    /**
     * Log a flow-update-failed event.
     */
    public void onFailedFlowUpdate(String flowId, String failureReason) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-update-failed");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, UPDATE_RESULT_EVENT);
        data.put("update-result", "failed");
        data.put("failure-reason", failureReason);
        invokeLogger(Level.WARN, String.format("Failed update of the flow %s, reason: %s", flowId, failureReason),
                data);
    }

    /**
     * Log a flow-patch-update event.
     */
    public void onFlowPatchUpdate(Flow flow) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-patch-update");
        data.put(FLOW_ID, flow.getFlowId());
        data.put(EVENT_TYPE, FLOW_UPDATE_EVENT);
        invokeLogger(Level.INFO, String.format("Patch update the flow: %s", flow), data);
    }

    /**
     * Log a flow-delete event.
     */
    public void onFlowDelete(String flowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-delete");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, FLOW_DELETE_EVENT);
        invokeLogger(Level.INFO, String.format("Delete the flow %s", flowId), data);
    }

    /**
     * Log a flow-delete-successful event.
     */
    public void onSuccessfulFlowDelete(String flowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-delete-successful");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, DELETE_RESULT_EVENT);
        data.put("delete-result", "successful");
        invokeLogger(Level.INFO, String.format("Successful delete of the flow %s", flowId), data);
    }

    /**
     * Log a flow-delete-failed event.
     */
    public void onFailedFlowDelete(String flowId, String failureReason) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-delete-failed");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, DELETE_RESULT_EVENT);
        data.put("delete-result", "failed");
        data.put("failure-reason", failureReason);
        invokeLogger(Level.WARN, String.format("Failed delete of the flow %s, reason: %s", flowId, failureReason),
                data);
    }

    /**
     * Log a flow-endpoint-swap event.
     */
    public void onFlowEndpointSwap(Flow firstFlow, Flow secondFlow) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-endpoint-swap");
        data.put(FLOW_ID, firstFlow.getFlowId());
        data.put(EVENT_TYPE, FLOW_UPDATE_EVENT);
        invokeLogger(Level.INFO, String.format("Swap end-points for the flows %s / %s", firstFlow, secondFlow), data);
    }

    /**
     * Log a flow-paths-swap event.
     */
    public void onFlowPathsSwap(Flow flow) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-paths-swap");
        data.put(FLOW_ID, flow.getFlowId());
        data.put(EVENT_TYPE, PATHS_SWAP_EVENT);
        invokeLogger(Level.INFO, String.format("Swap paths for the flow: %s", flow), data);
    }

    /**
     * Log a flow-paths-reroute event.
     */
    public void onFlowPathReroute(String flowId, Collection<IslEndpoint> affectedIsl, boolean forceToReroute) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-paths-reroute");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, REROUTE_EVENT);
        data.put("forced_reroute", Boolean.toString(forceToReroute));
        invokeLogger(Level.INFO, String.format("Reroute due to failure on %s ISLs flow %s", affectedIsl, flowId), data);
    }

    /**
     * Log a flow-reroute-successful event.
     */
    public void onSuccessfulFlowReroute(String flowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-reroute-successful");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, REROUTE_RESULT_EVENT);
        data.put("reroute-result", "successful");
        invokeLogger(Level.INFO, String.format("Successful reroute of the flow %s", flowId), data);
    }

    /**
     * Log a flow-reroute-failed event.
     */
    public void onFailedFlowReroute(String flowId, String failureReason) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-reroute-failed");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, REROUTE_RESULT_EVENT);
        data.put("reroute-result", "failed");
        data.put("failure-reason", failureReason);
        invokeLogger(
                Level.WARN, String.format("Failed reroute of the flow %s, reason: %s", flowId, failureReason), data);
    }

    /**
     * Log a flow-mirror-point-create event.
     */
    public void onFlowMirrorPointCreate(String flowId, SwitchId srcSwitch, String direction,
                                        SwitchId destSwitch, int destPort, int destVlan) {
        Map<String, String> data = new HashMap<>();
        data.put(DASHBOARD, "flow-mirror-point-create");
        data.put(TAG, "flow-mirror-point-create");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, FLOW_MIRROR_POINT_CREATE_EVENT);
        invokeLogger(Level.INFO, String.format("Create a mirror point for the flow %s: source switch %s, "
                        + "destination endpoint %s_%d_%d, direction %s",
                flowId, srcSwitch, destSwitch, destPort, destVlan, direction), data);
    }

    /**
     * Log a flow-mirror-point-create-successful event.
     */
    public void onSuccessfulFlowMirrorPointCreate(String flowId, SwitchId srcSwitch, String direction,
                                                  SwitchId destSwitch, int destPort, int destVlan) {
        Map<String, String> data = new HashMap<>();
        data.put(DASHBOARD, "flow-mirror-point-create-successful");
        data.put(TAG, "flow-mirror-point-create-successful");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, FLOW_MIRROR_POINT_CREATE_RESULT_EVENT);
        data.put("flow-mirror-point-create-result", "successful");
        invokeLogger(Level.INFO, String.format("Successful create a mirror point for the flow %s: source switch %s, "
                        + "destination endpoint %s_%d_%d, direction %s",
                flowId, srcSwitch, destSwitch, destPort, destVlan, direction), data);
    }

    /**
     * Log a flow-mirror-point-create-failed event.
     */
    public void onFailedFlowMirrorPointCreate(String flowId, SwitchId srcSwitch, String direction,
                                              SwitchId destSwitch, int destPort, int destVlan, String failureReason) {
        Map<String, String> data = new HashMap<>();
        data.put(DASHBOARD, "flow-mirror-point-create-failed");
        data.put(TAG, "flow-mirror-point-create-failed");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, FLOW_MIRROR_POINT_CREATE_RESULT_EVENT);
        data.put("flow-mirror-point-create-result", "failed");
        data.put("failure-reason", failureReason);
        invokeLogger(Level.WARN, String.format("Failed create a mirror point for the flow %s: source switch %s, "
                        + "destination endpoint %s_%d_%d, direction %s, reason: %s",
                flowId, srcSwitch, destSwitch, destPort, destVlan, direction, failureReason), data);
    }

    /**
     * Log a flow-mirror-point-delete event.
     */
    public void onFlowMirrorPointDelete(String flowId, String flowMirrorPointId) {
        Map<String, String> data = new HashMap<>();
        data.put(DASHBOARD, "flow-mirror-point-delete");
        data.put(TAG, "flow-mirror-point-delete");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, FLOW_MIRROR_POINT_DELETE_EVENT);
        invokeLogger(Level.INFO, String.format("Delete the flow mirror point %s for the flow %s",
                flowMirrorPointId, flowId), data);
    }

    /**
     * Log a flow-delete-successful event.
     */
    public void onSuccessfulFlowMirrorPointDelete(String flowId, String flowMirrorPointId) {
        Map<String, String> data = new HashMap<>();
        data.put(DASHBOARD, "flow-mirror-point-delete-successful");
        data.put(TAG, "flow-mirror-point-delete-successful");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, FLOW_MIRROR_POINT_DELETE_RESULT_EVENT);
        data.put("delete-result", "successful");
        invokeLogger(Level.INFO, String.format("Successful delete of the flow mirror point %s for the flow %s",
                flowMirrorPointId, flowId), data);
    }

    /**
     * Log a flow-delete-failed event.
     */
    public void onFailedFlowMirrorPointDelete(String flowId, String flowMirrorPointId, String failureReason) {
        Map<String, String> data = new HashMap<>();
        data.put(DASHBOARD, "flow-mirror-point-delete-failed");
        data.put(TAG, "flow-mirror-point-delete-failed");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, FLOW_MIRROR_POINT_DELETE_RESULT_EVENT);
        data.put("delete-result", "failed");
        data.put("failure-reason", failureReason);
        invokeLogger(Level.WARN, String.format("Failed delete of the flow mirror point %s for the flow %s, reason: %s",
                flowMirrorPointId, flowId, failureReason), data);
    }

    /**
     * Log a y-flow-create event.
     */
    public void onYFlowCreate(String yFlowId, FlowEndpoint sharedEndpoint,
                              List<FlowEndpoint> subFlowEndpoints, long maximumBandwidth) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "y-flow-create");
        data.put(FLOW_ID, yFlowId);
        data.put(EVENT_TYPE, YFLOW_CREATE_EVENT);
        invokeLogger(Level.INFO, String.format("Create the y-flow: %s, shared endpoint %s, endpoints (%s), "
                        + "bandwidth %d", yFlowId, sharedEndpoint, subFlowEndpoints, maximumBandwidth), data);
    }

    /**
     * Log a y-flow-create-successful event.
     */
    public void onSuccessfulYFlowCreate(String yFlowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "y-flow-create-successful");
        data.put(FLOW_ID, yFlowId);
        data.put(EVENT_TYPE, YFLOW_CREATE_RESULT_EVENT);
        data.put("create-result", "successful");
        invokeLogger(Level.INFO, String.format("Successful create of the y-flow %s", yFlowId), data);
    }

    /**
     * Log a y-flow-create-failed event.
     */
    public void onFailedYFlowCreate(String yFlowId, String failureReason) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "y-flow-create-failed");
        data.put(FLOW_ID, yFlowId);
        data.put(EVENT_TYPE, YFLOW_CREATE_RESULT_EVENT);
        data.put("update-result", "failed");
        data.put("failure-reason", failureReason);
        invokeLogger(Level.WARN, String.format("Failed create of the y-flow %s, reason: %s", yFlowId, failureReason),
                data);
    }

    /**
     * Log a y-flow-status-update event.
     */
    public void onYFlowStatusUpdate(String yFlowId, FlowStatus status) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "y-flow-status-update");
        data.put(FLOW_ID, yFlowId);
        data.put(EVENT_TYPE, STATUS_UPDATE_EVENT);
        data.put("status", status.toString());
        invokeLogger(Level.INFO, String.format("Update the status of the y-flow %s to %s", yFlowId, status), data);
    }
}
