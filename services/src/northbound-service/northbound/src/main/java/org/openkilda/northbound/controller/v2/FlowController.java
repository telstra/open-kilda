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

package org.openkilda.northbound.controller.v2;

import org.openkilda.messaging.payload.flow.SwapFlowEndpointPayload;
import org.openkilda.northbound.controller.BaseController;
import org.openkilda.northbound.service.FlowService;

import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController("FlowControllerV2")
@RequestMapping("/v2/flows")
@PropertySource("classpath:northbound.properties")
public class FlowController extends BaseController {

    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(FlowController.class);

    /**
     * The flow service instance.
     */
    @Autowired
    private FlowService flowService;

    /**
     * Bulk update for flow.
     */
    @ApiOperation(value = "Bulk flow update", response = SwapFlowEndpointPayload.class)
    @PatchMapping("/swap-endpoint")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<SwapFlowEndpointPayload> swapFlowEndpoint(@RequestBody SwapFlowEndpointPayload payload) {
        return flowService.swapFlowEndpoint(payload);
    }
}
