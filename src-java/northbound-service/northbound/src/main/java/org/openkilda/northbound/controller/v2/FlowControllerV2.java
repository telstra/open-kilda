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

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.northbound.controller.BaseController;
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2;
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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@RestController
@RequestMapping("/v2/flows")
public class FlowControllerV2 extends BaseController {
    private static final int DEFAULT_MAX_HISTORY_RECORD_COUNT = 100;

    @Autowired
    private FlowService flowService;

    @PostMapping
    @Operation(summary = "Creates new flow")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = FlowResponseV2.class)))
    public CompletableFuture<FlowResponseV2> createFlow(@RequestBody FlowRequestV2 flow) {
        verifyRequest(flow);
        return flowService.createFlow(flow);
    }

    @PutMapping(value = "/{flow_id:.+}")
    @Operation(summary = "Updates flow")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = FlowResponseV2.class)))
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
    @PostMapping(path = "/{flow_id}/reroute")
    @Operation(summary = "Reroute flow")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = FlowRerouteResponseV2.class)))
    public CompletableFuture<FlowRerouteResponseV2> rerouteFlow(@PathVariable("flow_id") String flowId) {
        return flowService.rerouteFlowV2(flowId);
    }

    @DeleteMapping(value = "/{flow_id:.+}")
    @Operation(summary = "Deletes flow")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = FlowResponseV2.class)))
    public CompletableFuture<FlowResponseV2> deleteFlow(@PathVariable(name = "flow_id") String flowId) {
        return flowService.deleteFlowV2(flowId);
    }

    /**
     * Gets flow.
     *
     * @param flowId        flow id
     * @return flow
     */
    @GetMapping(value = "/{flow_id:.+}")
    @Operation(summary = "Gets flow")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = FlowResponseV2.class)))
    public CompletableFuture<FlowResponseV2> getFlow(@PathVariable(name = "flow_id") String flowId) {
        return flowService.getFlowV2(flowId);
    }

    /**
     * Dumps all flows. Dumps all flows with specific status if specified.
     *
     * @return list of flow
     */
    @GetMapping
    @Operation(summary = "Dumps all flows")
    @ApiResponse(responseCode = "200",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = FlowResponseV2.class))))
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
    @GetMapping(value = "/status/{flow_id:.+}")
    @Operation(summary = "Gets flow status")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = FlowIdStatusPayload.class)))
    public CompletableFuture<FlowIdStatusPayload> statusFlow(@PathVariable(name = "flow_id") String flowId) {
        return flowService.statusFlow(flowId);
    }

    /**
     * Bulk update for flow.
     */
    @PostMapping("/swap-endpoint")
    @Operation(summary = "Swap flow endpoints")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = SwapFlowEndpointPayload.class)))
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
    @PatchMapping(value = "/{flow_id:.+}")
    @Operation(summary = "Updates flow")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = FlowResponseV2.class)))
    public CompletableFuture<FlowResponseV2> patchFlow(@PathVariable(name = "flow_id") String flowId,
                                                       @Parameter(description = "To remove flow from a diverse group, "
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
    @GetMapping(value = "/loops")
    @Operation(summary = "Get flow loops")
    @ApiResponse(responseCode = "200",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = FlowLoopResponse.class))))
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
    @PostMapping(value = "/{flow_id}/loops")
    @Operation(summary = "Create flow loop")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = FlowLoopResponse.class)))
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
    @DeleteMapping(value = "/{flow_id}/loops")
    @Operation(summary = "Delete flow loop")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = FlowLoopResponse.class)))
    public CompletableFuture<FlowLoopResponse> deleteFlowLoop(@PathVariable(name = "flow_id") String flowId) {
        return flowService.deleteFlowLoop(flowId);
    }

    /**
     * Gets flow statuses from history.
     */
    @GetMapping(path = "/{flow_id}/history/statuses")
    @Operation(summary = "Gets flow status timestamps for flow from history")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = FlowHistoryStatusesResponse.class)))
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public CompletableFuture<ResponseEntity<FlowHistoryStatusesResponse>> getFlowStatusTimestamps(
            @PathVariable("flow_id") String flowId,
            @Parameter(description = "default: 0 (1 January 1970 00:00:00).")
            @RequestParam(value = "timeFrom", required = false) Optional<Long> optionalTimeFrom,
            @Parameter(description = "default: now.")
            @RequestParam(value = "timeTo", required = false) Optional<Long> optionalTimeTo,
            @Parameter(description = "Return at most N latest records. "
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

    private void verifyRequest(FlowRequestV2 request) {
        exposeBodyValidationResults(Stream.concat(
                verifyFlowEndpoint(request.getSource(), "source"),
                verifyFlowEndpoint(request.getDestination(), "destination")));
    }

    private Stream<Optional<String>> verifyFlowEndpoint(FlowEndpointV2 endpoint, String name) {
        return Stream.of(
                verifyEndpointVlanId(name, "vlanId", endpoint.getVlanId()),
                verifyEndpointVlanId(name, "innerVlanId", endpoint.getInnerVlanId()));
    }

    private Optional<String> verifyEndpointVlanId(String endpoint, String field, int value) {
        if (! Utils.validateVlanRange(value)) {
            return Optional.of(String.format("Invalid %s value %d into %s endpoint", field, value, endpoint));
        }
        return Optional.empty();
    }

    @PostMapping(path = "/{flow_id}/mirror")
    @Operation(summary = "Creates a new flow mirror point")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = FlowMirrorPointResponseV2.class)))
    public CompletableFuture<FlowMirrorPointResponseV2> createFlowMirrorPoint(
            @PathVariable("flow_id") String flowId,
            @RequestBody FlowMirrorPointPayload mirrorPoint) {
        return flowService.createFlowMirrorPoint(flowId, mirrorPoint);
    }

    @DeleteMapping(path = "/{flow_id}/mirror/{mirror_point_id}")
    @Operation(summary = "Deletes the flow mirror point")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = FlowMirrorPointResponseV2.class)))
    public CompletableFuture<FlowMirrorPointResponseV2> deleteFlowMirrorPoint(
            @PathVariable("flow_id") String flowId,
            @PathVariable("mirror_point_id") String mirrorPointId) {
        return flowService.deleteFlowMirrorPoint(flowId, mirrorPointId);
    }

    @GetMapping(path = "/{flow_id}/mirror")
    @Operation(summary = "Get list of flow mirror points")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = FlowMirrorPointsResponseV2.class)))
    public CompletableFuture<FlowMirrorPointsResponseV2> getFlowMirrorPoints(@PathVariable("flow_id") String flowId) {
        return flowService.getFlowMirrorPoints(flowId);
    }
}
