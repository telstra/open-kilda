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

import org.openkilda.messaging.model.HealthCheck;
import org.openkilda.northbound.controller.BaseController;
import org.openkilda.northbound.service.HealthCheckService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for health-check request.
 */
@RestController
@RequestMapping("/v1")
public class HealthCheckController extends BaseController {
    /**
     * The health-check instance.
     */
    @Autowired
    private HealthCheckService healthCheckService;

    /**
     * Gets the health-check status.
     *
     * @return health-check model entity
     */
    @GetMapping(value = "/health-check")
    @Operation(summary = "Gets health-check status")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = HealthCheck.class)))
    public ResponseEntity<HealthCheck> getHealthCheck() {
        HealthCheck healthCheck = healthCheckService.getHealthCheck();
        HttpStatus status = healthCheck.hasNonOperational() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK;

        return new ResponseEntity<>(healthCheck, new HttpHeaders(), status);
    }
}
