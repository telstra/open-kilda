/* Copyright 2017 Telstra Open Source
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

package org.openkilda.northbound.service;

import org.openkilda.messaging.info.meter.FlowMeterEntries;
import org.openkilda.messaging.payload.flow.FlowCreatePayload;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowReroutePayload;
import org.openkilda.messaging.payload.flow.FlowResponsePayload;
import org.openkilda.messaging.payload.flow.FlowUpdatePayload;
import org.openkilda.messaging.payload.history.FlowHistoryEntry;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.dto.BatchResults;
import org.openkilda.northbound.dto.v1.flows.FlowConnectedDevicesResponse;
import org.openkilda.northbound.dto.v1.flows.FlowPatchDto;
import org.openkilda.northbound.dto.v1.flows.FlowValidationDto;
import org.openkilda.northbound.dto.v1.flows.PingInput;
import org.openkilda.northbound.dto.v1.flows.PingOutput;
import org.openkilda.northbound.dto.v2.flows.FlowLoopResponse;
import org.openkilda.northbound.dto.v2.flows.FlowPatchV2;
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2;
import org.openkilda.northbound.dto.v2.flows.FlowRerouteResponseV2;
import org.openkilda.northbound.dto.v2.flows.FlowResponseV2;
import org.openkilda.northbound.dto.v2.flows.SwapFlowEndpointPayload;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * FlowService is for operations on flows, primarily against the Flow Topology.
 */
public interface FlowService {
    /**
     * Creates flow.
     *
     * @param flow flow
     * @return created flow
     */
    CompletableFuture<FlowResponsePayload> createFlow(final FlowCreatePayload flow);

    /**
     * Creates new flow.
     *
     * @param flow flow
     * @return created flow
     */
    CompletableFuture<FlowResponseV2> createFlow(final FlowRequestV2 flow);

    /**
     * Deletes flow.
     *
     * @param id flow id
     * @return deleted flow
     */
    CompletableFuture<FlowResponsePayload> deleteFlow(final String id);

    /**
     * Deletes flow.
     *
     * @param id flow id
     * @return deleted flow
     */
    CompletableFuture<FlowResponseV2> deleteFlowV2(final String id);

    /**
     * Updates flow.
     *
     * @param flow flow
     * @return updated flow
     */
    CompletableFuture<FlowResponsePayload> updateFlow(final FlowUpdatePayload flow);

    /**
     * Updates flow.
     *
     * @param flow flow
     * @return updated flow
     */
    CompletableFuture<FlowResponseV2> updateFlow(FlowRequestV2 flow);

    /**
     * Patch flow.
     *
     * @param flowId       flow id
     * @param flowPatchDto flow parameters for update
     * @return updated flow
     */
    CompletableFuture<FlowResponsePayload> patchFlow(final String flowId, final FlowPatchDto flowPatchDto);

    /**
     * Patch flow.
     *
     * @param flowId       flow id
     * @param flowPatchDto flow parameters for update
     * @return updated flow
     */
    CompletableFuture<FlowResponseV2> patchFlow(String flowId, FlowPatchV2 flowPatchDto);

    /**
     * Gets flow by id.
     *
     * @param id flow id
     * @return flow
     */
    CompletableFuture<FlowResponsePayload> getFlow(final String id);

    /**
     * Gets flow by id.
     *
     * @param id flow id
     * @return flow
     */
    CompletableFuture<FlowResponseV2> getFlowV2(final String id);

    /**
     * Gets all the flows.
     *
     * @return the list of all flows with specified status
     */
    CompletableFuture<List<FlowResponsePayload>> getAllFlows();

    /**
     * Gets all the flows.
     *
     * @return the list of all flows with specified status
     */
    CompletableFuture<List<FlowResponseV2>> getAllFlowsV2(String status);

    /**
     * Deletes all flows. Primarily this is a combination of getAllFlows and deleteFlow.
     * This should be called with care ..
     *
     * @return the list of all deleted flows
     */
    CompletableFuture<List<FlowResponsePayload>> deleteAllFlows();

    /**
     * Gets flow status by id.
     *
     * @param id flow id
     * @return flow status
     */
    CompletableFuture<FlowIdStatusPayload> statusFlow(final String id);

    /**
     * Gets flow path by id.
     *
     * @param id flow id
     * @return Flow path
     */
    CompletableFuture<FlowPathPayload> pathFlow(final String id);

    /**
     * Use this to push flows that may not be in the database / caches but they should be.
     *
     * @deprecated Push flow operation is deprecated.
     */
    @Deprecated
    CompletableFuture<BatchResults> pushFlows();

    /**
     * Use this to unpush flows .. ie undo a push
     *
     * @deprecated Unpush flow operation is deprecated.
     */
    @Deprecated
    CompletableFuture<BatchResults> unpushFlows();

    /**
     * Performs rerouting of specific flow.
     *
     * @param flowId id of flow to be rerouted.
     * @return updated flow path information with the result whether or not path was changed.
     */
    CompletableFuture<FlowReroutePayload> rerouteFlow(final String flowId);

    /**
     * Performs flow paths swapping for flow with protected path.
     *
     * @param flowId id of the flow to swap paths.
     * @return flow payload.
     */
    CompletableFuture<FlowResponsePayload> swapFlowPaths(final String flowId);

    /**
     * Performs rerouting of specific flow.
     *
     * @param flowId id of flow to be rerouted.
     * @return updated flow path information with the result whether or not path was changed.
     */
    CompletableFuture<FlowRerouteResponseV2> rerouteFlowV2(final String flowId);

    /**
     * Performs synchronization (reinstalling) of specific flow.
     *
     * @param flowId id of flow to be synchronized.
     * @return updated flow.
     */
    CompletableFuture<FlowReroutePayload> syncFlow(final String flowId);

    /**
     * Performs validation of specific flow - ie comparing what is in the database with what is
     * on the network.
     *
     * @param flowId id of the flow
     * @return the results of the comparison
     * @throws org.openkilda.messaging.error.MessageException if the flow doesn't exist
     * @throws java.nio.file.InvalidPathException             if the flow doesn't return a path and it should.
     */
    CompletableFuture<List<FlowValidationDto>> validateFlow(final String flowId);

    CompletableFuture<PingOutput> pingFlow(String flowId, PingInput payload);

    /**
     * Modify burst size on switch.
     *
     * @param flowId id of the flow
     * @return the meter entry
     */
    CompletableFuture<FlowMeterEntries> modifyMeter(String flowId);

    CompletableFuture<List<FlowHistoryEntry>> listFlowEvents(String flowId,
                                                             long timestampFrom,
                                                             long timestampTo, int maxCount);

    /**
     * Swaps a flow endpoint.
     *
     * @param input a payload.
     * @return the list of updated flows.
     */
    CompletableFuture<SwapFlowEndpointPayload> swapFlowEndpoint(SwapFlowEndpointPayload input);

    /**
     * Get Flow connected devices.
     *
     * @param flowId flow ID
     * @param since timestamp. Device will be included in response if device `time_last_seen` >= `since`
     * @return the list devices connected to flow.
     */
    CompletableFuture<FlowConnectedDevicesResponse> getFlowConnectedDevices(String flowId, Instant since);

    /**
     * Get flow loops.
     *
     * @param flowId    filter by flow id
     * @param switchId  filter by switch id
     * @return the list of flow loops.
     */
    CompletableFuture<List<FlowLoopResponse>> getFlowLoops(String flowId, String switchId);

    /**
     * Create flow loop.
     *
     * @param flowId    flow id
     * @param switchId  switch id
     * @return created flow loop.
     */
    CompletableFuture<FlowLoopResponse> createFlowLoop(String flowId, SwitchId switchId);

    /**
     * Delete flow loop.
     *
     * @param flowId    flow id
     * @return deleted flow loop.
     */
    CompletableFuture<FlowLoopResponse> deleteFlowLoop(String flowId);
}
