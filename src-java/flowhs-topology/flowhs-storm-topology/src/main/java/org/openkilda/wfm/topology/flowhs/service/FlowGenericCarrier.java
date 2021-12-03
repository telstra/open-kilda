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

package org.openkilda.wfm.topology.flowhs.service;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.info.stats.RemoveFlowPathInfo;
import org.openkilda.messaging.info.stats.StatsNotification;
import org.openkilda.messaging.info.stats.UpdateFlowPathInfo;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;
import org.openkilda.wfm.topology.flowhs.service.common.HistoryUpdateCarrier;
import org.openkilda.wfm.topology.flowhs.service.common.LifecycleEventCarrier;
import org.openkilda.wfm.topology.flowhs.service.common.NorthboundResponseCarrier;

public interface FlowGenericCarrier extends NorthboundResponseCarrier, HistoryUpdateCarrier, LifecycleEventCarrier {
    /**
     * Sends commands to speaker.
     * @param command command to be executed.
     */
    void sendSpeakerRequest(FlowSegmentRequest command);

    /**
     * Sends update on periodic ping status for the flow.
     * @param flowId flow id
     * @param enabled flag
     */
    void sendPeriodicPingNotification(String flowId, boolean enabled);

    /**
     * Sends ActivateFlowMonitoringInfoData to server42-control topology.
     * @param flow requested flow
     */
    default void sendActivateFlowMonitoring(RequestedFlow flow) {}

    /**
     * Sends DeactivateFlowMonitoringInfoData to server42-control topology.
     * @param flow requested flow
     */
    default void sendDeactivateFlowMonitoring(String flow, SwitchId srcSwitchId, SwitchId dstSwitchId) {}

    /**
     * Sends UpdateFlowInfo to flow-monitoring topology.
     * @param flowInfo message to send
     */
    default void sendNotifyFlowMonitor(CommandData flowInfo) {}

    /**
     * Sends UpdateFlowInfo to stats topology.
     * @param flowPathInfo message to send
     */
    default void sendNotifyFlowStats(UpdateFlowPathInfo flowPathInfo) {}

    /**
     * Sends RemoveFlowPathInfo to stats topology.
     * @param flowPathInfo message to send
     */
    default void sendNotifyFlowStats(RemoveFlowPathInfo flowPathInfo) {}

    default void sendStatsNotification(StatsNotification notification) {}
}
