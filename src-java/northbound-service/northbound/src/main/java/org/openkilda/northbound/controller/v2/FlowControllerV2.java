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

import static java.lang.String.format;

import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.northbound.controller.FlowControllerBase;
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

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/v2/flows")
public class FlowControllerV2 extends FlowControllerBase {
    private static final int DEFAULT_MAX_HISTORY_RECORD_COUNT = 100;

    @Autowired
    private FlowService flowService;

    @ApiOperation(value = "Creates new flow", response = FlowResponseV2.class)
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowResponseV2> createFlow(@RequestBody FlowRequestV2 flow) {
        verifyRequest(flow);
        return flowService.createFlow(flow);
    }

    @ApiOperation(value = "Updates flow", response = FlowResponseV2.class)
    @PutMapping(value = "/{flow_id:.+}")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowResponseV2> updateFlow(@PathVariable(name = "flow_id") String flowId,
                                                        @RequestBody FlowRequestV2 flow) {
        verifyRequest(flow);
        return flowService.updateFlow(flow);
    }

    /**
     * Initiates flow rerouting if any shorter path is available.
     *
     * @param flowId id of flow to be rerouted.
     * @return the flow with updated path.
     */
    @ApiOperation(value = "Reroute flow", response = FlowRerouteResponseV2.class)
    @PostMapping(path = "/{flow_id}/reroute")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowRerouteResponseV2> rerouteFlow(@PathVariable("flow_id") String flowId) {
        return flowService.rerouteFlowV2(flowId);
    }

    @ApiOperation(value = "Deletes flow", response = FlowResponseV2.class)
    @DeleteMapping(value = "/{flow_id:.+}")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowResponseV2> deleteFlow(@PathVariable(name = "flow_id") String flowId) {
        return flowService.deleteFlowV2(flowId);
    }

    /**
     * Gets flow.
     *
     * @param flowId        flow id
     * @return flow
     */
    @ApiOperation(value = "Gets flow", response = FlowResponseV2.class)
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
    @ApiOperation(value = "Dumps all flows", response = FlowResponseV2.class, responseContainer = "List")
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<List<FlowResponseV2>> getFlows(
            @RequestParam(value = "status", required = false) String status) {
        return flowService.getAllFlowsV2(status);
    }

    /**
     * Gets flow status.
     *
     * @param flowId        flow id
     * @return list of flow
     */
    @ApiOperation(value = "Gets flow status", response = FlowIdStatusPayload.class)
    @GetMapping(value = "/status/{flow_id:.+}")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowIdStatusPayload> statusFlow(@PathVariable(name = "flow_id") String flowId) {
        return flowService.statusFlow(flowId);
    }

    /**
     * Bulk update for flow.
     */
    @ApiOperation(value = "Swap flow endpoints", response = SwapFlowEndpointPayload.class)
    @PostMapping("/swap-endpoint")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<SwapFlowEndpointPayload> swapFlowEndpoint(@RequestBody SwapFlowEndpointPayload payload) {
        return flowService.swapFlowEndpoint(payload);
    }

    /**
     * Updates existing flow params.
     *
     * @param flowPatchDto  flow parameters for update
     * @param flowId        flow id
     * @return flow
     */
    @ApiOperation(value = "Updates flow", response = FlowResponseV2.class)
    @PatchMapping(value = "/{flow_id:.+}")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowResponseV2> patchFlow(@PathVariable(name = "flow_id") String flowId,
                                                       @ApiParam(value = "To remove flow from a diverse group, "
                                                               + "need to pass the parameter \"diverse_flow_id\" "
                                                               + "equal to the empty string.")
                                                       @RequestBody FlowPatchV2 flowPatchDto) {
        return flowService.patchFlow(flowId, flowPatchDto);
    }

    /**
     * Get existing flow loops.
     *
     * @param flowId filter by flow id
     * @param switchId filter by switch id
     * @return list of flow loops
     */
    @ApiOperation(value = "Get flow loops", response = FlowLoopResponse.class, responseContainer = "List")
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
    @ApiOperation(value = "Create flow loop", response = FlowLoopResponse.class)
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
    @ApiOperation(value = "Delete flow loop", response = FlowLoopResponse.class)
    @DeleteMapping(value = "/{flow_id}/loops")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowLoopResponse> deleteFlowLoop(@PathVariable(name = "flow_id") String flowId) {
        return flowService.deleteFlowLoop(flowId);
    }

    /**
     * Gets flow statuses from history.
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @ApiOperation(value = "Gets flow status timestamps for flow from history",
            response = FlowHistoryStatusesResponse.class)
    @GetMapping(path = "/{flow_id}/history/statuses")
    public CompletableFuture<ResponseEntity<FlowHistoryStatusesResponse>> getFlowStatusTimestamps(
            @PathVariable("flow_id") String flowId,
            @ApiParam(value = "default: 0 (1 January 1970 00:00:00).")
            @RequestParam(value = "timeFrom", required = false) Optional<Long> optionalTimeFrom,
            @ApiParam(value = "default: now.")
            @RequestParam(value = "timeTo", required = false) Optional<Long> optionalTimeTo,
            @ApiParam(value = "Return at most N latest records. "
                    + "Default: if `timeFrom` or/and `timeTo` parameters are presented default value of "
                    + "`maxCount` is infinite (all records in time interval will be returned). "
                    + "Otherwise default value of `maxCount` will be equal to 100. In This case response will contain "
                    + "header 'Content-Range'.")
            @RequestParam(value = "max_count", required = false) Optional<Integer> optionalMaxCount) {
        int maxCount = optionalMaxCount.orElseGet(() -> {
            if (optionalTimeFrom.isPresent() || optionalTimeTo.isPresent()) {
                return Integer.MAX_VALUE;
            } else {
                return DEFAULT_MAX_HISTORY_RECORD_COUNT;
            }
        });

        Long timeTo = optionalTimeTo.orElseGet(() -> Instant.now().getEpochSecond());
        Long timeFrom = optionalTimeFrom.orElse(0L);
        return flowService.getFlowStatuses(flowId, timeFrom, timeTo, maxCount)
                .thenApply(statuses -> {
                    HttpHeaders headers = new HttpHeaders();

                    if (!optionalMaxCount.isPresent() && !optionalTimeFrom.isPresent() && !optionalTimeTo.isPresent()
                            && statuses.getHistoryStatuses().size() == DEFAULT_MAX_HISTORY_RECORD_COUNT) {
                        // if request has no parameters we assume that default value of `maxCount` is 100. To indicate
                        // that response may contain not all of history records "Content-Range" header will be added to
                        // response.
                        headers.add(HttpHeaders.CONTENT_RANGE, format("items 0-%d/*",
                                statuses.getHistoryStatuses().size() - 1));
                    }
                    return new ResponseEntity<>(statuses, headers, HttpStatus.OK);
                });
    }

    @ApiOperation(value = "Creates a new flow mirror point", response = FlowMirrorPointResponseV2.class)
    @PostMapping(path = "/{flow_id}/mirror")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowMirrorPointResponseV2> createFlowMirrorPoint(
            @PathVariable("flow_id") String flowId,
            @RequestBody FlowMirrorPointPayload mirrorPoint) {
        return flowService.createFlowMirrorPoint(flowId, mirrorPoint);
    }

    @ApiOperation(value = "Deletes the flow mirror point", response = FlowMirrorPointResponseV2.class)
    @DeleteMapping(path = "/{flow_id}/mirror/{mirror_point_id}")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowMirrorPointResponseV2> deleteFlowMirrorPoint(
            @PathVariable("flow_id") String flowId,
            @PathVariable("mirror_point_id") String mirrorPointId) {
        return flowService.deleteFlowMirrorPoint(flowId, mirrorPointId);
    }

    @ApiOperation(value = "Get list of flow mirror points", response = FlowMirrorPointsResponseV2.class)
    @GetMapping(path = "/{flow_id}/mirror")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowMirrorPointsResponseV2> getFlowMirrorPoints(@PathVariable("flow_id") String flowId) {
        return flowService.getFlowMirrorPoints(flowId);
    }
}
