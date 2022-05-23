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

import static org.openkilda.northbound.config.SwaggerConfig.DRAFT_API_TAG;

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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;
import javax.validation.Valid;

@Api(tags = {DRAFT_API_TAG})
@RestController
@RequestMapping("/v2/y-flows")
public class YFlowControllerV2 extends BaseController {
    @Autowired
    private YFlowService flowService;

    @ApiOperation(value = "Creates a new Y-flow", response = YFlow.class)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompletableFuture<YFlow> createYFlow(@Valid @RequestBody YFlowCreatePayload flow) {
        return flowService.createYFlow(flow);
    }

    @ApiOperation(value = "Dump all Y-flows", response = YFlowDump.class)
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<YFlowDump> dumpYFlows() {
        return flowService.dumpYFlows();
    }

    @ApiOperation(value = "Gets Y-flow", response = YFlow.class)
    @GetMapping(value = "/{y_flow_id:.+}")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<YFlow> getYFlow(@PathVariable(name = "y_flow_id") String yFlowId) {
        return flowService.getYFlow(yFlowId);
    }

    @ApiOperation(value = "Gets Y-flow paths", response = YFlowPaths.class)
    @GetMapping(value = "/{y_flow_id:.+}/paths")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<YFlowPaths> getYFlowPaths(@PathVariable(name = "y_flow_id") String yFlowId) {
        return flowService.getYFlowPaths(yFlowId);
    }

    @ApiOperation(value = "Updates Y-flow", response = YFlow.class)
    @PutMapping(value = "/{y_flow_id:.+}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletableFuture<YFlow> updateYFlow(@PathVariable(name = "y_flow_id") String yFlowId,
                                                @Valid @RequestBody YFlowUpdatePayload flow) {
        return flowService.updateYFlow(yFlowId, flow);
    }

    @ApiOperation(value = "Updates Y-flow partially", response = YFlow.class)
    @PatchMapping(value = "/{y_flow_id:.+}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletableFuture<YFlow> patchYFlow(@PathVariable(name = "y_flow_id") String yFlowId,
                                               @Valid @RequestBody YFlowPatchPayload flowPatch) {
        return flowService.patchYFlow(yFlowId, flowPatch);
    }

    @ApiOperation(value = "Deletes Y-flow", response = YFlow.class)
    @DeleteMapping(value = "/{y_flow_id:.+}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletableFuture<YFlow> deleteYFlow(@PathVariable(name = "y_flow_id") String yFlowId) {
        return flowService.deleteYFlow(yFlowId);
    }

    @ApiOperation(value = "Gets subordinate flows of Y-flow", response = SubFlowsDump.class)
    @GetMapping(value = "/{y_flow_id:.+}/flows")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<SubFlowsDump> getSubFlows(@PathVariable(name = "y_flow_id") String yFlowId) {
        return flowService.getSubFlows(yFlowId);
    }

    @ApiOperation(value = "Reroute Y-flow", response = YFlowRerouteResult.class)
    @PostMapping(path = "/{y_flow_id:.+}/reroute")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletableFuture<YFlowRerouteResult> rerouteYFlow(@PathVariable(name = "y_flow_id") String yFlowId) {
        return flowService.rerouteYFlow(yFlowId);
    }

    @ApiOperation(value = "Validate Y-flow", response = YFlowValidationResult.class)
    @PostMapping(path = "/{y_flow_id:.+}/validate")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<YFlowValidationResult> validateYFlow(@PathVariable("y_flow_id") String yFlowId) {
        return flowService.validateYFlow(yFlowId);
    }

    @ApiOperation(value = "Synchronize Y-flow", response = YFlowSyncResult.class)
    @PostMapping(path = "/{y_flow_id:.+}/sync")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<YFlowSyncResult> synchronizeYFlow(@PathVariable("y_flow_id") String yFlowId) {
        return flowService.synchronizeYFlow(yFlowId);
    }

    @ApiOperation(
            value = "Verify flow - using special network packet that is being routed in the same way as client traffic",
            response = YFlowPingResult.class)
    @PostMapping(path = "/{y_flow_id}/ping")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<YFlowPingResult> pingYFlow(
            @RequestBody YFlowPingPayload payload,
            @PathVariable("y_flow_id") String yFlowId) {
        return flowService.pingYFlow(yFlowId, payload);
    }

    @ApiOperation(value = "Swap paths for y-flow with protected path", response = YFlow.class)
    @PostMapping(path = "/{y_flow_id:.+}/swap")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletableFuture<YFlow> swapYFlowPaths(@PathVariable("y_flow_id") String yFlowId) {
        return flowService.swapYFlowPaths(yFlowId);
    }
}
