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

package org.openkilda.northbound.controller.v1;

import org.openkilda.messaging.payload.network.PathsDto;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.controller.BaseController;
import org.openkilda.northbound.editor.CaseInsensitiveEnumEditor;
import org.openkilda.northbound.service.NetworkService;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for network info.
 */
@RestController
@RequestMapping("/v1/network")
@PropertySource("classpath:northbound.properties")
public class NetworkController extends BaseController {

    @Autowired
    private NetworkService networkService;

    /**
     * Handles paths between two endpoints requests.
     */
    @GetMapping(path = "/paths")
    @ApiOperation(value = "Get paths between two switches", response = PathsDto.class)
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<PathsDto> getPaths(
            @RequestParam("src_switch") SwitchId srcSwitchId, @RequestParam("dst_switch") SwitchId dstSwitchId,
            @ApiParam(value = "Valid values are: TRANSIT_VLAN, VXLAN. If encapsulation type is not specified, default "
                    + "value from OpenKilda Configuration will be used")
            @RequestParam(value = "encapsulation_type", required = false) FlowEncapsulationType encapsulationType,
            @ApiParam(value = "Valid values are: COST, LATENCY, MAX_LATENCY, COST_AND_AVAILABLE_BANDWIDTH. If path "
                    + "computation strategy is not specified, default value from OpenKilda Configuration will be used")
            @RequestParam(value = "path_computation_strategy", required = false)
                    PathComputationStrategy pathComputationStrategy,
            @ApiParam(value = "Maximum latency of flow path in milliseconds. Required for MAX_LATENCY strategy. "
                    + "Other strategies will ignore this parameter. If max_latency is 0 LATENCY strategy will be used "
                    + "instead of MAX_LATENCY")
            @RequestParam(value = "max_latency", required = false) Long maxLatencyMs,
            @ApiParam(value = "Second tier for flow path latency in milliseconds. If there is no path with required "
                    + "max_latency, max_latency_tier2 with be used instead. Used only with MAX_LATENCY strategy. "
                    + "Other strategies will ignore this parameter.")
            @RequestParam(value = "max_latency_tier2", required = false) Long maxLatencyTier2Ms,
            @ApiParam(value = "Maximum count of paths which will be calculated. "
                    + "If maximum path count is not specified, default value from OpenKilda Configuration will be used")
            @RequestParam(value = "max_path_count", required = false) Integer maxPathCount) {

        Duration maxLatency = maxLatencyMs != null ? Duration.ofMillis(maxLatencyMs) : null;
        Duration maxLatencyTier2 = maxLatencyTier2Ms != null ? Duration.ofMillis(maxLatencyTier2Ms) : null;

        return networkService.getPaths(srcSwitchId, dstSwitchId, encapsulationType, pathComputationStrategy, maxLatency,
                maxLatencyTier2, maxPathCount);
    }

    /**
     * This method adds custom Editor to parse Enums from string ignoring case.
     */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(FlowEncapsulationType.class,
                new CaseInsensitiveEnumEditor(FlowEncapsulationType.class));
        binder.registerCustomEditor(PathComputationStrategy.class,
                new CaseInsensitiveEnumEditor(PathComputationStrategy.class));
    }
}
