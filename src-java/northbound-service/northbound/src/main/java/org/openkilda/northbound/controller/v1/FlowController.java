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

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.meter.FlowMeterEntries;
import org.openkilda.messaging.payload.flow.FlowCreatePayload;
import org.openkilda.messaging.payload.flow.FlowFlushReroutePayload;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowReroutePayload;
import org.openkilda.messaging.payload.flow.FlowResponsePayload;
import org.openkilda.messaging.payload.flow.FlowUpdatePayload;
import org.openkilda.messaging.payload.history.FlowHistoryEntry;
import org.openkilda.northbound.controller.BaseController;
import org.openkilda.northbound.dto.BatchResults;
import org.openkilda.northbound.dto.v1.flows.FlowConnectedDevicesResponse;
import org.openkilda.northbound.dto.v1.flows.FlowPatchDto;
import org.openkilda.northbound.dto.v1.flows.FlowValidationDto;
import org.openkilda.northbound.dto.v1.flows.PingInput;
import org.openkilda.northbound.dto.v1.flows.PingOutput;
import org.openkilda.northbound.service.FlowService;
import org.openkilda.northbound.utils.ExtraAuthRequired;
import org.openkilda.northbound.utils.flowhistory.FlowHistoryHelper;
import org.openkilda.northbound.utils.flowhistory.FlowHistoryRangeConstraints;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
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
@PropertySource("classpath:northbound.properties")
@Tag(name = "Flow Controller", description = "performs CRUD and other operations for simple flows")
public class FlowController extends BaseController {

    /**
     * The flow service instance.
     */
    @Autowired
    private FlowService flowService;

    /**
     * Creates new flow.
     *
     * @param flow          flow
     * @return flow
     */
    @Operation(summary = "Creates new flow")
    @PutMapping
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowResponsePayload> createFlow(@RequestBody FlowCreatePayload flow) {
        return flowService.createFlow(flow);
    }

    /**
     * Gets flow.
     *
     * @param flowId        flow id
     * @return flow
     */
    @Operation(summary = "Gets flow")
    @GetMapping(value = "/{flow-id:.+}")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowResponsePayload> getFlow(@PathVariable(name = "flow-id") String flowId) {
        return flowService.getFlow(flowId);
    }

    /**
     * Deletes flow.
     *
     * @param flowId        flow id
     * @return flow
     */
    @Operation(summary = "Deletes flow")
    @DeleteMapping(value = "/{flow-id:.+}")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowResponsePayload> deleteFlow(@PathVariable(name = "flow-id") String flowId) {
        return flowService.deleteFlow(flowId);
    }

    /**
     * Updates existing flow.
     *
     * @param flow          flow
     * @param flowId        flow id
     * @return flow
     */
    @Operation(summary = "Updates flow")
    @PutMapping(value = "/{flow-id:.+}")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowResponsePayload> updateFlow(@PathVariable(name = "flow-id") String flowId,
                                                             @RequestBody FlowUpdatePayload flow) {
        return flowService.updateFlow(flowId, flow);
    }

    /**
     * Updates existing flow params.
     *
     * @param flowPatchDto  flow parameters for update
     * @param flowId        flow id
     * @return flow
     */
    @Operation(summary = "Updates flow")
    @PatchMapping(value = "/{flow-id:.+}")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowResponsePayload> patchFlow(@PathVariable(name = "flow-id") String flowId,
                                                            @RequestBody FlowPatchDto flowPatchDto) {
        return flowService.patchFlow(flowId, flowPatchDto);
    }

    /**
     * Dumps all flows. Dumps all flows with specific status if specified.
     *
     * @return list of flow
     */
    @Operation(summary = "Dumps all flows")
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<List<FlowResponsePayload>> getFlows() {
        return flowService.getAllFlows();
    }

    /**
     * Delete all flows.
     *
     * @return list of flows that have been deleted
     */
    @Operation(summary = "Delete all flows. Requires special authorization")
    @DeleteMapping
    @ResponseStatus(HttpStatus.OK)
    @ExtraAuthRequired
    public CompletableFuture<List<FlowResponsePayload>> deleteFlows() {
        return flowService.deleteAllFlows();
    }

    /**
     * Gets flow status.
     *
     * @param flowId        flow id
     * @return list of flow
     */
    @Operation(summary = "Gets flow status")
    @GetMapping(value = "/status/{flow-id:.+}")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowIdStatusPayload> statusFlow(@PathVariable(name = "flow-id") String flowId) {
        return flowService.statusFlow(flowId);
    }

    /**
     * Gets flow path.
     *
     * @param flowId        flow id
     * @return list of flow
     */
    @Operation(summary = "Gets flow path")
    @GetMapping(value = "/{flow-id}/path")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowPathPayload> pathFlow(@PathVariable(name = "flow-id") String flowId) {
        return flowService.pathFlow(flowId);
    }

    /**
     * Push flows to kilda ... this can be used to get flows into kilda without kilda creating them
     * itself. Kilda won't expect to create them .. it may (and should) validate them at some stage.
     *
     * @deprecated Push flow operation is deprecated.
     */
    @Deprecated
    @Operation(summary = "Push flows without expectation of modifying switches. It can push to switch and validate.")
    @PutMapping(path = "/push")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<BatchResults> pushFlows() {
        return flowService.pushFlows();
    }

    /**
     * Unpush flows to kilda ... essentially the opposite of push.
     *
     * @deprecated Unpush flow operation is deprecated.
     */
    @Deprecated
    @Operation(summary = "Unpush flows without expectation of modifying switches. It can push to switch and validate.")
    @PutMapping(path = "/unpush")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<BatchResults> unpushFlows() {
        return flowService.unpushFlows();
    }


    /**
     * Initiates flow rerouting if any shorter paths are available.
     *
     * @param flowId id of flow to be rerouted.
     * @return flow payload with updated path.
     */
    @Operation(summary = "Reroute flow")
    @PatchMapping(path = "/{flow_id}/reroute")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowReroutePayload> rerouteFlow(@PathVariable("flow_id") String flowId) {
        return flowService.rerouteFlow(flowId);
    }

    /**
     * Flush rerouting when stuck in the progress queue. For Internal Use
     *
     * @param flowId id of flow to be flushed.
     * @return flow payload with updated path.
     */
    @Operation(summary = "Reroute flow")
    @PatchMapping(path = "/{flow_id}/reroute/flush")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowFlushReroutePayload> flushRerouteFlow(@PathVariable("flow_id") String flowId) {
        return flowService.flushRerouteFlow(flowId);
    }

    /**
     * Initiates flow paths swapping for flow with protected path.
     *
     * @param flowId id of flow to swap paths.
     * @return flow payload.
     */
    @Operation(summary = "Swap paths for flow with protected path")
    @PatchMapping(path = "/{flow_id}/swap")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowResponsePayload> swapFlowPaths(@PathVariable("flow_id") String flowId) {
        return flowService.swapFlowPaths(flowId);
    }

    /**
     * Initiates flow synchronization (reinstalling). In other words it means flow update with newly generated rules.
     *
     * @param flowId id of flow to be rerouted.
     * @return flow payload with updated path.
     */
    @Operation(summary = "Sync flow")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Operation is successful")})
    @PatchMapping(path = "/{flow_id}/sync")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowReroutePayload> syncFlow(@PathVariable("flow_id") String flowId) {
        return flowService.syncFlow(flowId);
    }

    /**
     * Compares the Flow from the DB to what is on each switch.
     *
     * @param flowId id of flow to be rerouted.
     * @return flow payload with updated path.
     */
    @Operation(summary = "Validate flow, comparing the DB to each switch")
    @GetMapping(path = "/{flow_id}/validate")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<List<FlowValidationDto>> validateFlow(@PathVariable("flow_id") String flowId) {
        return flowService.validateFlow(flowId);
    }

    /**
     * Verify flow integrity by sending "ping" package over flow path.
     */
    @Operation(summary =
            "Verify flow - using special network packet that is being routed in the same way as client traffic")
    @PutMapping(path = "/{flow_id}/ping")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<PingOutput> pingFlow(
            @RequestBody PingInput payload,
            @PathVariable("flow_id") String flowId) {
        return flowService.pingFlow(flowId, payload);
    }

    /**
     * Invalidate (purge) the flow resources cache and initialize it with DB data.
     */
    @Operation(summary = "Invalidate (purge) Flow Resources Cache(s)")
    @DeleteMapping(path = "/cache")
    @ResponseStatus(HttpStatus.OK)
    public void invalidateFlowCache() {
        //TODO: to be removed
    }

    /**
     * Update burst parameter in meter.
     */
    @Operation(summary = "Update burst parameter in meter")
    @PatchMapping(path = "/{flow_id}/meters")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowMeterEntries> updateMetersBurst(@PathVariable("flow_id") String flowId) {
        return flowService.modifyMeter(flowId);
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

    /**
     * Gets flow connected devices.
     */
    @Operation(summary = "Gets flow connected devices")
    @GetMapping(path = "/{flow_id}/devices")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<FlowConnectedDevicesResponse> getConnectedDevices(
            @PathVariable("flow_id") String flowId,
            @Parameter(description = "Device will be included in response if it's `time_last_seen` >= `since`. "
                    + "Example of `since` value: `2019-09-30T16:14:12.538Z`")
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
