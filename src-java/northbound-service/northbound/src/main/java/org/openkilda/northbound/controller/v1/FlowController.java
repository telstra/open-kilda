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

package org.openkilda.northbound.controller.v1;

import static java.lang.String.format;

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.meter.FlowMeterEntries;
import org.openkilda.messaging.payload.flow.FlowCreatePayload;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowReroutePayload;
import org.openkilda.messaging.payload.flow.FlowResponsePayload;
import org.openkilda.messaging.payload.flow.FlowUpdatePayload;
import org.openkilda.messaging.payload.history.FlowHistoryEntry;
import org.openkilda.northbound.controller.BaseController;
import org.openkilda.northbound.dto.v1.flows.FlowConnectedDevicesResponse;
import org.openkilda.northbound.dto.v1.flows.FlowPatchDto;
import org.openkilda.northbound.dto.v1.flows.FlowValidationDto;
import org.openkilda.northbound.dto.v1.flows.PingInput;
import org.openkilda.northbound.dto.v1.flows.PingOutput;
import org.openkilda.northbound.service.FlowService;
import org.openkilda.northbound.utils.ExtraAuthRequired;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;


/**
 * REST Controller for flow requests.
 */
@RestController
@RequestMapping("/v1/flows")
public class FlowController extends BaseController {
    private static final int DEFAULT_MAX_HISTORY_RECORD_COUNT = 100;

    /**
     * The flow service instance.
     */
    @Autowired
    private FlowService flowService;

    /**
     * Creates new flow.
     *
     * @param flow flow
     * @return flow
     */
    @PutMapping
    @Operation(summary = "Creates new flow")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = FlowResponsePayload.class)))
    public CompletableFuture<FlowResponsePayload> createFlow(@RequestBody FlowCreatePayload flow) {
        return flowService.createFlow(flow);
    }

    /**
     * Gets flow.
     *
     * @param flowId flow id
     * @return flow
     */
    @GetMapping(value = "/{flow-id:.+}")
    @Operation(summary = "Gets flow")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = FlowResponsePayload.class)))
    public CompletableFuture<FlowResponsePayload> getFlow(@PathVariable(name = "flow-id") String flowId) {
        return flowService.getFlow(flowId);
    }

    /**
     * Deletes flow.
     *
     * @param flowId flow id
     * @return flow
     */
    @DeleteMapping(value = "/{flow-id:.+}")
    @Operation(summary = "Deletes flow")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = FlowResponsePayload.class)))
    public CompletableFuture<FlowResponsePayload> deleteFlow(@PathVariable(name = "flow-id") String flowId) {
        return flowService.deleteFlow(flowId);
    }

    /**
     * Updates existing flow.
     *
     * @param flow flow
     * @param flowId flow id
     * @return flow
     */
    @PutMapping(value = "/{flow-id:.+}")
    @Operation(summary = "Updates flow")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = FlowResponsePayload.class)))
    public CompletableFuture<FlowResponsePayload> updateFlow(@PathVariable(name = "flow-id") String flowId,
                                                             @RequestBody FlowUpdatePayload flow) {
        return flowService.updateFlow(flow);
    }

    /**
     * Updates existing flow params.
     *
     * @param flowPatchDto flow parameters for update
     * @param flowId flow id
     * @return flow
     */
    @PatchMapping(value = "/{flow-id:.+}")
    @Operation(summary = "Updates flow")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = FlowResponsePayload.class)))
    public CompletableFuture<FlowResponsePayload> patchFlow(@PathVariable(name = "flow-id") String flowId,
                                                            @RequestBody FlowPatchDto flowPatchDto) {
        return flowService.patchFlow(flowId, flowPatchDto);
    }

    /**
     * Dumps all flows. Dumps all flows with specific status if specified.
     *
     * @return list of flow
     */
    @GetMapping
    @Operation(summary = "Dumps all flows")
    @ApiResponse(responseCode = "200", content = @Content(array = @ArraySchema(
            schema = @Schema(implementation = FlowResponsePayload.class))))
    public CompletableFuture<List<FlowResponsePayload>> getFlows() {
        return flowService.getAllFlows();
    }

    /**
     * Delete all flows.
     *
     * @return list of flows that have been deleted
     */
    @DeleteMapping
    @ExtraAuthRequired
    @Operation(summary = "Delete all flows. Requires special authorization")
    @ApiResponse(responseCode = "200", content = @Content(array = @ArraySchema(
            schema = @Schema(implementation = FlowResponsePayload.class))))
    @Parameter(in = ParameterIn.HEADER, name = Utils.EXTRA_AUTH,
            content = @Content(schema = @Schema(implementation = String.class)))
    public CompletableFuture<List<FlowResponsePayload>> deleteFlows() {
        return flowService.deleteAllFlows();
    }

    /**
     * Gets flow status.
     *
     * @param flowId flow id
     * @return list of flow
     */
    @GetMapping(value = "/status/{flow-id:.+}")
    @Operation(summary = "Gets flow status")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = FlowIdStatusPayload.class)))
    public CompletableFuture<FlowIdStatusPayload> statusFlow(@PathVariable(name = "flow-id") String flowId) {
        return flowService.statusFlow(flowId);
    }

    /**
     * Gets flow path.
     *
     * @param flowId flow id
     * @return list of flow
     */
    @GetMapping(value = "/{flow-id}/path")
    @Operation(summary = "Gets flow path")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = FlowPathPayload.class)))
    public CompletableFuture<FlowPathPayload> pathFlow(@PathVariable(name = "flow-id") String flowId) {
        return flowService.pathFlow(flowId);
    }

    @Deprecated
    @PutMapping(path = "/push")
    @ResponseStatus(HttpStatus.GONE)
    @Hidden
    public String pushFlows() {
        return "Push flow operation is deprecated";
    }

    @Deprecated
    @PutMapping(path = "/unpush")
    @ResponseStatus(HttpStatus.GONE)
    @Hidden
    public String unpushFlows() {
        return "Unpush flow operation is deprecated";
    }


    /**
     * Initiates flow rerouting if any shorter paths are available.
     *
     * @param flowId id of flow to be rerouted.
     * @return flow payload with updated path.
     */
    @PatchMapping(path = "/{flow_id}/reroute")
    @Operation(summary = "Reroute flow")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = FlowReroutePayload.class)))
    public CompletableFuture<FlowReroutePayload> rerouteFlow(@PathVariable("flow_id") String flowId) {
        return flowService.rerouteFlow(flowId);
    }

    /**
     * Initiates flow paths swapping for flow with protected path.
     *
     * @param flowId id of flow to swap paths.
     * @return flow payload.
     */
    @PatchMapping(path = "/{flow_id}/swap")
    @Operation(summary = "Swap paths for flow with protected path")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = FlowResponsePayload.class)))
    public CompletableFuture<FlowResponsePayload> swapFlowPaths(@PathVariable("flow_id") String flowId) {
        return flowService.swapFlowPaths(flowId);
    }

    /**
     * Initiates flow synchronization (reinstalling). In other words it means flow update with newly generated rules.
     *
     * @param flowId id of flow to be rerouted.
     * @return flow payload with updated path.
     */
    @PatchMapping(path = "/{flow_id}/sync")
    @Operation(summary = "Sync flow")
    @ApiResponse(responseCode = "200", description = "Operation is successful",
            content = @Content(schema = @Schema(implementation = FlowReroutePayload.class)))
    public CompletableFuture<FlowReroutePayload> syncFlow(@PathVariable("flow_id") String flowId) {
        return flowService.syncFlow(flowId);
    }

    /**
     * Compares the Flow from the DB to what is on each switch.
     *
     * @param flowId id of flow to be rerouted.
     * @return flow payload with updated path.
     */
    @GetMapping(path = "/{flow_id}/validate")
    @Operation(summary = "Validate flow, comparing the DB to each switch")
    @ApiResponse(responseCode = "200",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = FlowValidationDto.class))))
    public CompletableFuture<List<FlowValidationDto>> validateFlow(@PathVariable("flow_id") String flowId) {
        return flowService.validateFlow(flowId);
    }

    /**
     * Verify flow integrity by sending "ping" package over flow path.
     */
    @PutMapping(path = "/{flow_id}/ping")
    @Operation(summary = "Verify flow - using special network packet that is being routed in the same way "
            + "as client traffic")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = PingOutput.class)))
    public CompletableFuture<PingOutput> pingFlow(
            @RequestBody PingInput payload,
            @PathVariable("flow_id") String flowId) {
        return flowService.pingFlow(flowId, payload);
    }

    @Deprecated
    @DeleteMapping(path = "/cache")
    @ResponseStatus(HttpStatus.GONE)
    @Hidden
    public void invalidateFlowCache() {
        //TODO: to be removed
    }

    /**
     * Update burst parameter in meter.
     */
    @PatchMapping(path = "/{flow_id}/meters")
    @Operation(summary = "Update burst parameter in meter")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = FlowMeterEntries.class)))
    public CompletableFuture<FlowMeterEntries> updateMetersBurst(@PathVariable("flow_id") String flowId) {
        return flowService.modifyMeter(flowId);
    }

    /**
     * Gets flow history.
     */
    @GetMapping(path = "/{flow_id}/history")
    @Operation(summary = "Gets history for flow")
    @ApiResponse(responseCode = "200",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = FlowHistoryEntry.class))))
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public CompletableFuture<ResponseEntity<List<FlowHistoryEntry>>> getHistory(
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
        return flowService.listFlowEvents(flowId, timeFrom, timeTo, maxCount)
                .thenApply(events -> {
                    HttpHeaders headers = new HttpHeaders();

                    if (!optionalMaxCount.isPresent() && !optionalTimeFrom.isPresent() && !optionalTimeTo.isPresent()
                            && events.size() == DEFAULT_MAX_HISTORY_RECORD_COUNT) {
                        // if request has no parameters we assume that default value of `maxCount` is 100. To indicate
                        // that response may contain not all of history records "Content-Range" header will be added to
                        // response.
                        headers.add(HttpHeaders.CONTENT_RANGE, format("items 0-%d/*", events.size() - 1));
                    }
                    return new ResponseEntity<>(events, headers, HttpStatus.OK);
                });
    }

    /**
     * Gets flow connected devices.
     */
    @GetMapping(path = "/{flow_id}/devices")
    @Operation(summary = "Gets flow connected devices")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = FlowConnectedDevicesResponse.class)))
    public CompletableFuture<FlowConnectedDevicesResponse> getConnectedDevices(
            @PathVariable("flow_id") String flowId,
            @Parameter(description = "Device will be included in response if it's `time_last_seen` >= `since`. "
                    + "Example of `since` value: `2019-09-30T16:14:12.538Z`",
                    required = false)
            @RequestParam(value = "since", required = false) Optional<String> since) {
        Instant sinceInstant;

        if (!since.isPresent() || StringUtils.isEmpty(since.get())) {
            sinceInstant = Instant.MIN;
        } else {
            try {
                sinceInstant = Instant.parse(since.get());
            } catch (DateTimeParseException e) {
                String message = format("Invalid 'since' value '%s'. Correct example of 'since' value is "
                        + "'2019-09-30T16:14:12.538Z'", since.get());
                throw new MessageException(ErrorType.DATA_INVALID, message, "Invalid 'since' value");
            }
        }
        return flowService.getFlowConnectedDevices(flowId, sinceInstant);
    }
}
