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

package org.openkilda.northbound.controller.v2;

import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.history.FlowHistoryEntry;
import org.openkilda.northbound.controller.BaseController;
import org.openkilda.northbound.controller.v2.validator.FlowRequestV2Validator;
import org.openkilda.northbound.dto.v2.flows.FlowHistoryStatusesResponse;
import org.openkilda.northbound.dto.v2.flows.FlowLoopPayload;
import org.openkilda.northbound.dto.v2.flows.FlowLoopResponse;
import org.openkilda.northbound.dto.v2.flows.FlowMirrorPointPayload;
import org.openkilda.northbound.dto.v2.flows.FlowMirrorPointResponseV2;
import org.openkilda.northbound.dto.v2.flows.FlowMirrorPointsResponseV2;
import org.openkilda.northbound.dto.v2.flows.FlowPatchV2;
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2;
import org.openkilda.northbound.dto.v2.flows.FlowRerouteResponseV2;
import org.openkilda.northbound.dto.v2.flows.FlowResponseV2;
import org.openkilda.northbound.dto.v2.flows.SwapFlowEndpointPayload;
import org.openkilda.northbound.service.FlowService;
import org.openkilda.northbound.utils.flowhistory.FlowHistoryHelper;
import org.openkilda.northbound.utils.flowhistory.FlowHistoryRangeConstraints;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/v2/flows")
@Tag(name = "Flow controller", description = "performs CRUD and other operations for simple flows")
public class FlowControllerV2 extends BaseController {

    @Autowired
    private FlowService flowService;

    @Operation(summary = "Creates new flow")
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowResponseV2> createFlow(@RequestBody FlowRequestV2 flow) {
        exposeBodyValidationResults(FlowRequestV2Validator.validateFlowRequestV2(flow), "Could not create flow");
        return flowService.createFlow(flow);
    }

    @Operation(summary = "Updates flow")
    @PutMapping(value = "/{flow_id:.+}")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowResponseV2> updateFlow(@PathVariable(name = "flow_id") String flowId,
                                                        @RequestBody FlowRequestV2 flow) {
        exposeBodyValidationResults(FlowRequestV2Validator.validateFlowRequestV2(flow), "Could not update flow");
        return flowService.updateFlow(flowId, flow);
    }

    /**
     * Initiates flow rerouting if any shorter path is available.
     *
     * @param flowId id of flow to be rerouted.
     * @return the flow with updated path.
     */
    @Operation(summary = "Reroute flow")
    @PostMapping(path = "/{flow_id}/reroute")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowRerouteResponseV2> rerouteFlow(@PathVariable("flow_id") String flowId) {
        return flowService.rerouteFlowV2(flowId);
    }

    @Operation(summary = "Deletes flow")
    @DeleteMapping(value = "/{flow_id:.+}")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowResponseV2> deleteFlow(@PathVariable(name = "flow_id") String flowId) {
        return flowService.deleteFlowV2(flowId);
    }

    /**
     * Gets flow.
     *
     * @param flowId flow id
     * @return flow
     */
    @Operation(summary = "Gets flow")
    @GetMapping(value = "/{flow_id:.+}")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowResponseV2> getFlow(@PathVariable(name = "flow_id") String flowId) {
        return flowService.getFlowV2(flowId);
    }

    /**
     * Dumps all flows. Dumps all flows with specific status if specified.
     *
     * @return list of flow
     */
    @Operation(summary = "Dumps all flows")
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<List<FlowResponseV2>> getFlows(
            @RequestParam(value = "status", required = false) String status) {
        return flowService.getAllFlowsV2(status);
    }

    /**
     * Gets flow status.
     *
     * @param flowId flow id
     * @return list of flow
     */
    @Operation(summary = "Gets flow status")
    @GetMapping(value = "/status/{flow_id:.+}")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowIdStatusPayload> statusFlow(@PathVariable(name = "flow_id") String flowId) {
        return flowService.statusFlow(flowId);
    }

    /**
     * Bulk update for flow.
     */
    @Operation(summary = "Swap flow endpoints")
    @PostMapping("/swap-endpoint")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<SwapFlowEndpointPayload> swapFlowEndpoint(@RequestBody SwapFlowEndpointPayload payload) {
        return flowService.swapFlowEndpoint(payload);
    }

    /**
     * Updates existing flow params.
     *
     * @param flowPatchDto flow parameters for update
     * @param flowId flow id
     * @return flow
     */
    @Operation(summary = "Updates flow")
    @PatchMapping(value = "/{flow_id:.+}")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowResponseV2> patchFlow(@PathVariable(name = "flow_id") String flowId,
                                                       @Parameter(description = "To remove flow from a diverse group, "
                                                               + "need to pass the parameter \"diverse_flow_id\" "
                                                               + "equal to the empty string.")
                                                       @Valid @RequestBody FlowPatchV2 flowPatchDto) {
        return flowService.patchFlow(flowId, flowPatchDto);
    }

    /**
     * Get existing flow loops.
     *
     * @param flowId filter by flow id
     * @param switchId filter by switch id
     * @return list of flow loops
     */
    @Operation(summary = "Get flow loops")
    @GetMapping(value = "/loops")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<List<FlowLoopResponse>> getFlowLoops(
            @RequestParam(value = "flow_id", required = false) String flowId,
            @RequestParam(value = "switch_id", required = false) String switchId) {
        return flowService.getFlowLoops(flowId, switchId);
    }

    /**
     * Create flow loop.
     *
     * @param flowId flow id
     * @param flowLoopPayload parameters for flow loop
     * @return created flow loop
     */
    @Operation(summary = "Create flow loop")
    @PostMapping(value = "/{flow_id}/loops")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowLoopResponse> createFlowLoop(@PathVariable(name = "flow_id") String flowId,
                                                              @RequestBody FlowLoopPayload flowLoopPayload) {
        return flowService.createFlowLoop(flowId, flowLoopPayload.getSwitchId());
    }

    /**
     * Delete flow loop.
     *
     * @param flowId flow id
     * @return deleted flow loop
     */
    @Operation(summary = "Delete flow loop")
    @DeleteMapping(value = "/{flow_id}/loops")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowLoopResponse> deleteFlowLoop(@PathVariable(name = "flow_id") String flowId) {
        return flowService.deleteFlowLoop(flowId);
    }

    /**
     * Gets flow statuses from history.
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @Operation(summary = "Gets flow status timestamps for flow from history")
    @GetMapping(path = "/{flow_id}/history/statuses")
    public CompletableFuture<ResponseEntity<FlowHistoryStatusesResponse>> getFlowStatusTimestamps(
            @PathVariable("flow_id") String flowId,
            @Parameter(description =
                    "Linux epoch time in seconds or milliseconds. Default: 0 (1 January 1970 00:00:00).")
            @RequestParam(value = "timeFrom", required = false) Optional<Long> optionalTimeFrom,
            @Parameter(description = "Linux epoch time in seconds or milliseconds. Default: now.")
            @RequestParam(value = "timeTo", required = false) Optional<Long> optionalTimeTo,
            @Parameter(description = "Return at most N latest records. "
                    + "Default: if `timeFrom` or/and `timeTo` parameters are presented default value of "
                    + "`maxCount` is infinite (all records in time interval will be returned). "
                    + "Otherwise default value of `maxCount` will be equal to 100. In This case response will contain "
                    + "header 'Content-Range'.")
            @RequestParam(value = "max_count", required = false)
            Optional<@Min(1) @Max(Integer.MAX_VALUE) Integer> optionalMaxCount) {
        FlowHistoryRangeConstraints constraints =
                new FlowHistoryRangeConstraints(optionalTimeFrom, optionalTimeTo, optionalMaxCount);

        return FlowHistoryHelper.getFlowStatuses(flowService, flowId, constraints);
    }

    /**
     * Gets flow history.
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @Operation(summary = "Gets history for flow")
    @GetMapping(path = "/{flow_id}/history")
    public CompletableFuture<ResponseEntity<List<FlowHistoryEntry>>> getHistory(
            @PathVariable("flow_id") String flowId,
            @Parameter(description =
                    "Linux epoch time in seconds or milliseconds. Default: 0 (1 January 1970 00:00:00).")
            @RequestParam(value = "timeFrom", required = false) Optional<Long> optionalTimeFrom,
            @Parameter(description = "Linux epoch time in seconds or milliseconds. Default: now.")
            @RequestParam(value = "timeTo", required = false) Optional<Long> optionalTimeTo,
            @Parameter(description = "Return at most N latest records. "
                    + "Default: if `timeFrom` or/and `timeTo` parameters are presented default value of "
                    + "`maxCount` is infinite (all records in time interval will be returned). "
                    + "Otherwise default value of `maxCount` will be equal to 100. In This case response will contain "
                    + "header 'Content-Range'.")
            @RequestParam(value = "max_count", required = false) Optional<Integer> optionalMaxCount) {
        FlowHistoryRangeConstraints constraints =
                new FlowHistoryRangeConstraints(optionalTimeFrom, optionalTimeTo, optionalMaxCount);

        return FlowHistoryHelper.getFlowHistoryEvents(flowService, flowId, constraints);
    }

    @Operation(summary = "Creates a new flow mirror point")
    @PostMapping(path = "/{flow_id}/mirror")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowMirrorPointResponseV2> createFlowMirrorPoint(
            @PathVariable("flow_id") String flowId,
            @RequestBody FlowMirrorPointPayload mirrorPoint) {
        return flowService.createFlowMirrorPoint(flowId, mirrorPoint);
    }

    @Operation(summary = "Deletes the flow mirror point")
    @DeleteMapping(path = "/{flow_id}/mirror/{mirror_point_id}")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowMirrorPointResponseV2> deleteFlowMirrorPoint(
            @PathVariable("flow_id") String flowId,
            @PathVariable("mirror_point_id") String mirrorPointId) {
        return flowService.deleteFlowMirrorPoint(flowId, mirrorPointId);
    }

    @Operation(summary = "Get list of flow mirror points")
    @GetMapping(path = "/{flow_id}/mirror")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowMirrorPointsResponseV2> getFlowMirrorPoints(@PathVariable("flow_id") String flowId) {
        return flowService.getFlowMirrorPoints(flowId);
    }
}
