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

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.payload.network.PathValidationPayload;
import org.openkilda.northbound.dto.v2.flows.PathValidateResponse;
import org.openkilda.northbound.service.NetworkService;

import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/v2/network")
public class NetworkControllerV2 {

    private final NetworkService networkService;

    @Autowired
    public NetworkControllerV2(NetworkService networkService) {
        this.networkService = networkService;
    }

    /**
     * Validates that a given path complies with the chosen strategy and the network availability.
     * It is required that the input contains path nodes. Other parameters are optional.
     * @param pathValidationPayload a payload with a path and additional flow parameters provided by a user
     * @return either a successful response or the list of errors
     */
    @PostMapping(path = "/path/check")
    @ApiOperation(value = "Validates that a given path complies with the chosen strategy and the network availability")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<PathValidateResponse> validateCustomFlowPath(
            @RequestBody PathValidationPayload pathValidationPayload) {

        if (pathValidationPayload == null
                || pathValidationPayload.getNodes() == null
                || pathValidationPayload.getNodes().size() < 2) {
            throw new MessageException(ErrorType.DATA_INVALID, "Invalid Request Body",
                    "Invalid 'nodes' value in the request body");
        }

        return networkService.validateFlowPath(pathValidationPayload);
    }
}
