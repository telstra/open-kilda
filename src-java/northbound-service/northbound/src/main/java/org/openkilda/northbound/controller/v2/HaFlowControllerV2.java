/* Copyright 2023 Telstra Open Source
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

import static org.openkilda.northbound.config.SwaggerConfig.DRAFT_API_TAG;

import org.openkilda.messaging.payload.history.HaFlowHistoryEntry;
import org.openkilda.northbound.controller.BaseController;
import org.openkilda.northbound.dto.v2.flows.FlowHistoryStatusesResponse;
import org.openkilda.northbound.dto.v2.haflows.HaFlow;
import org.openkilda.northbound.dto.v2.haflows.HaFlowCreatePayload;
import org.openkilda.northbound.dto.v2.haflows.HaFlowDump;
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchPayload;
import org.openkilda.northbound.dto.v2.haflows.HaFlowPaths;
import org.openkilda.northbound.dto.v2.haflows.HaFlowPingPayload;
import org.openkilda.northbound.dto.v2.haflows.HaFlowPingResult;
import org.openkilda.northbound.dto.v2.haflows.HaFlowRerouteResult;
import org.openkilda.northbound.dto.v2.haflows.HaFlowSyncResult;
import org.openkilda.northbound.dto.v2.haflows.HaFlowUpdatePayload;
import org.openkilda.northbound.dto.v2.haflows.HaFlowValidationResult;
import org.openkilda.northbound.service.HaFlowService;
import org.openkilda.northbound.utils.flowhistory.FlowHistoryHelper;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
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
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@Api(tags = {DRAFT_API_TAG})
@RestController
@RequestMapping("/v2/ha-flows")
public class HaFlowControllerV2 extends BaseController {
    private final HaFlowService flowService;

    @Autowired
    public HaFlowControllerV2(HaFlowService flowService) {
        this.flowService = flowService;
    }

    @ApiOperation(value = "Creates a new HA-flow", response = HaFlow.class)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompletableFuture<HaFlow> createHaFlow(@Valid @RequestBody HaFlowCreatePayload flow) {
        return flowService.createHaFlow(flow);
    }

    @ApiOperation(value = "Dump all HA-flows", response = HaFlowDump.class)
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<HaFlowDump> dumpHaFlows() {
        return flowService.dumpHaFlows();
    }

    @ApiOperation(value = "Gets HA-flow", response = HaFlow.class)
    @GetMapping(value = "/{ha_flow_id:.+}")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<HaFlow> getHaFlow(@PathVariable(name = "ha_flow_id") String haFlowId) {
        return flowService.getHaFlow(haFlowId);
    }

    @ApiOperation(value = "Gets HA-flow paths", response = HaFlowPaths.class)
    @GetMapping(value = "/{ha_flow_id:.+}/paths")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<HaFlowPaths> getHaFlowPaths(@PathVariable(name = "ha_flow_id") String haFlowId) {
        return flowService.getHaFlowPaths(haFlowId);
    }

    @ApiOperation(value = "Updates HA-flow", response = HaFlow.class)
    @PutMapping(value = "/{ha_flow_id:.+}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletableFuture<HaFlow> updateHaFlow(@PathVariable(name = "ha_flow_id") String haFlowId,
                                                 @Valid @RequestBody HaFlowUpdatePayload flow) {
        return flowService.updateHaFlow(haFlowId, flow);
    }

    @ApiOperation(value = "Updates HA-flow partially", response = HaFlow.class)
    @PatchMapping(value = "/{ha_flow_id:.+}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletableFuture<HaFlow> patchHaFlow(@PathVariable(name = "ha_flow_id") String haFlowId,
                                                @Valid @RequestBody HaFlowPatchPayload flowPatch) {
        return flowService.patchHaFlow(haFlowId, flowPatch);
    }

    @ApiOperation(value = "Deletes HA-flow", response = HaFlow.class)
    @DeleteMapping(value = "/{ha_flow_id:.+}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletableFuture<HaFlow> deleteHaFlow(@PathVariable(name = "ha_flow_id") String haFlowId) {
        return flowService.deleteHaFlow(haFlowId);
    }

    @ApiOperation(value = "Reroute HA-flow", response = HaFlowRerouteResult.class)
    @PostMapping(path = "/{ha_flow_id:.+}/reroute")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletableFuture<HaFlowRerouteResult> rerouteHaFlow(@PathVariable(name = "ha_flow_id") String haFlowId) {
        return flowService.rerouteHaFlow(haFlowId);
    }

    @ApiOperation(value = "Validate HA-flow", response = HaFlowValidationResult.class)
    @PostMapping(path = "/{ha_flow_id:.+}/validate")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<HaFlowValidationResult> validateHaFlow(@PathVariable("ha_flow_id") String haFlowId) {
        return flowService.validateHaFlow(haFlowId);
    }

    @ApiOperation(value = "Synchronize HA-flow", response = HaFlowSyncResult.class)
    @PostMapping(path = "/{ha_flow_id:.+}/sync")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<HaFlowSyncResult> synchronizeHaFlow(@PathVariable("ha_flow_id") String haFlowId) {
        return flowService.synchronizeHaFlow(haFlowId);
    }

    @ApiOperation(
            value = "Verify flow - using special network packet that is being routed in the same way as client traffic",
            response = HaFlowPingResult.class)
    @PostMapping(path = "/{ha_flow_id}/ping")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<HaFlowPingResult> pingHaFlow(
            @RequestBody HaFlowPingPayload payload,
            @PathVariable("ha_flow_id") String haFlowId) {
        return flowService.pingHaFlow(haFlowId, payload);
    }

    @ApiOperation(value = "Swap paths for HA-flow with protected path", response = HaFlow.class)
    @PostMapping(path = "/{ha_flow_id:.+}/swap")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletableFuture<HaFlow> swapHaFlowPaths(@PathVariable("ha_flow_id") String haFlowId) {
        return flowService.swapHaFlowPaths(haFlowId);
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
            @ApiParam(value = "Returns at most N latest records. "
                    + "Default: if `timeFrom` or `timeTo` parameters are present, the default value of "
                    + "`maxCount` is infinite (all records in time interval will be returned). "
                    + "Otherwise the default value of `maxCount` is 100. In this case, the response contains "
                    + "a 'Content-Range' header.")
            @RequestParam(value = "max_count", required = false)
            @Min(1)
            @Max(Integer.MAX_VALUE)
            Optional<Integer> optionalMaxCount) {

        //TODO decide whether remove it or implement. It is only supported by simple flow service,
        //TODO but seems like there have been no calls of this API for many months.
        if (true) {
            throw new UnsupportedOperationException();
        }
        return FlowHistoryHelper
                .getFlowStatuses(flowService, flowId, optionalTimeFrom, optionalTimeTo, optionalMaxCount);
    }

    /**
     * Gets flow history.
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @ApiOperation(value = "Gets history for HA-flow", response = HaFlowHistoryEntry.class, responseContainer = "List")
    @GetMapping(path = "/{flow_id}/history")
    public CompletableFuture<ResponseEntity<List<HaFlowHistoryEntry>>> getHistory(
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

        return FlowHistoryHelper
                .getFlowHistoryEvents(flowService, flowId, optionalTimeFrom, optionalTimeTo, optionalMaxCount);
    }
}
