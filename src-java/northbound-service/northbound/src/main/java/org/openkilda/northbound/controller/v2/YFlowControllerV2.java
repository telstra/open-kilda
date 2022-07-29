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

import org.openkilda.northbound.controller.BaseController;
import org.openkilda.northbound.dto.v2.yflows.SubFlowsDump;
import org.openkilda.northbound.dto.v2.yflows.YFlow;
import org.openkilda.northbound.dto.v2.yflows.YFlowCreatePayload;
import org.openkilda.northbound.dto.v2.yflows.YFlowDump;
import org.openkilda.northbound.dto.v2.yflows.YFlowPatchPayload;
import org.openkilda.northbound.dto.v2.yflows.YFlowPaths;
import org.openkilda.northbound.dto.v2.yflows.YFlowPingPayload;
import org.openkilda.northbound.dto.v2.yflows.YFlowPingResult;
import org.openkilda.northbound.dto.v2.yflows.YFlowRerouteResult;
import org.openkilda.northbound.dto.v2.yflows.YFlowSyncResult;
import org.openkilda.northbound.dto.v2.yflows.YFlowUpdatePayload;
import org.openkilda.northbound.dto.v2.yflows.YFlowValidationResult;
import org.openkilda.northbound.service.YFlowService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;
import javax.validation.Valid;

@RestController
@RequestMapping("/v2/y-flows")
public class YFlowControllerV2 extends BaseController {
    @Autowired
    private YFlowService flowService;

    @PostMapping
    @Operation(summary = "Creates a new Y-flow")
    @ApiResponse(responseCode = "201", content = @Content(schema = @Schema(implementation = YFlow.class)))
    public CompletableFuture<YFlow> createYFlow(@Valid @RequestBody YFlowCreatePayload flow) {
        return flowService.createYFlow(flow);
    }

    @GetMapping
    @Operation(summary = "Dump all Y-flows")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = YFlowDump.class)))
    public CompletableFuture<YFlowDump> dumpYFlows() {
        return flowService.dumpYFlows();
    }

    @GetMapping(value = "/{y_flow_id:.+}")
    @Operation(summary = "Gets Y-flow")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = YFlow.class)))
    public CompletableFuture<YFlow> getYFlow(@PathVariable(name = "y_flow_id") String yFlowId) {
        return flowService.getYFlow(yFlowId);
    }

    @GetMapping(value = "/{y_flow_id:.+}/paths")
    @Operation(summary = "Gets Y-flow paths")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = YFlowPaths.class)))
    public CompletableFuture<YFlowPaths> getYFlowPaths(@PathVariable(name = "y_flow_id") String yFlowId) {
        return flowService.getYFlowPaths(yFlowId);
    }

    @PutMapping(value = "/{y_flow_id:.+}")
    @Operation(summary = "Updates Y-flow")
    @ApiResponse(responseCode = "202", content = @Content(schema = @Schema(implementation = YFlow.class)))
    public CompletableFuture<YFlow> updateYFlow(@PathVariable(name = "y_flow_id") String yFlowId,
                                                @Valid @RequestBody YFlowUpdatePayload flow) {
        return flowService.updateYFlow(yFlowId, flow);
    }

    @PatchMapping(value = "/{y_flow_id:.+}")
    @Operation(summary = "Updates Y-flow partially")
    @ApiResponse(responseCode = "202", content = @Content(schema = @Schema(implementation = YFlow.class)))
    public CompletableFuture<YFlow> patchYFlow(@PathVariable(name = "y_flow_id") String yFlowId,
                                               @Valid @RequestBody YFlowPatchPayload flowPatch) {
        return flowService.patchYFlow(yFlowId, flowPatch);
    }

    @DeleteMapping(value = "/{y_flow_id:.+}")
    @Operation(summary = "Deletes Y-flow")
    @ApiResponse(responseCode = "202", content = @Content(schema = @Schema(implementation = YFlow.class)))
    public CompletableFuture<YFlow> deleteYFlow(@PathVariable(name = "y_flow_id") String yFlowId) {
        return flowService.deleteYFlow(yFlowId);
    }

    @GetMapping(value = "/{y_flow_id:.+}/flows")
    @Operation(summary = "Gets subordinate flows of Y-flow")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = SubFlowsDump.class)))
    public CompletableFuture<SubFlowsDump> getSubFlows(@PathVariable(name = "y_flow_id") String yFlowId) {
        return flowService.getSubFlows(yFlowId);
    }

    @PostMapping(path = "/{y_flow_id:.+}/reroute")
    @Operation(summary = "Reroute Y-flow")
    @ApiResponse(responseCode = "202", content = @Content(schema = @Schema(implementation = YFlowRerouteResult.class)))
    public CompletableFuture<YFlowRerouteResult> rerouteYFlow(@PathVariable(name = "y_flow_id") String yFlowId) {
        return flowService.rerouteYFlow(yFlowId);
    }

    @PostMapping(path = "/{y_flow_id:.+}/validate")
    @Operation(summary = "Validate Y-flow")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = YFlowValidationResult.class)))
    public CompletableFuture<YFlowValidationResult> validateYFlow(@PathVariable("y_flow_id") String yFlowId) {
        return flowService.validateYFlow(yFlowId);
    }

    @PostMapping(path = "/{y_flow_id:.+}/sync")
    @Operation(summary = "Synchronize Y-flow")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = YFlowSyncResult.class)))
    public CompletableFuture<YFlowSyncResult> synchronizeYFlow(@PathVariable("y_flow_id") String yFlowId) {
        return flowService.synchronizeYFlow(yFlowId);
    }

    @PostMapping(path = "/{y_flow_id}/ping")
    @Operation(summary = "Verify flow - using special network packet that is being routed in the same way as client "
            + "traffic")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = YFlowPingResult.class)))
    public CompletableFuture<YFlowPingResult> pingYFlow(
            @RequestBody YFlowPingPayload payload,
            @PathVariable("y_flow_id") String yFlowId) {
        return flowService.pingYFlow(yFlowId, payload);
    }

    @PostMapping(path = "/{y_flow_id:.+}/swap")
    @Operation(summary = "Swap paths for y-flow with protected path")
    @ApiResponse(responseCode = "202", content = @Content(schema = @Schema(implementation = YFlow.class)))
    public CompletableFuture<YFlow> swapYFlowPaths(@PathVariable("y_flow_id") String yFlowId) {
        return flowService.swapYFlowPaths(yFlowId);
    }
}
