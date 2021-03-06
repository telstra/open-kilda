/* Copyright 2020 Telstra Open Source
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

package org.openkilda.grpc.speaker.controller;

import org.openkilda.grpc.speaker.service.HealthCheckService;
import org.openkilda.messaging.model.HealthCheck;

import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for health-check request.
 */
@RestController
@PropertySource("classpath:grpc-service.properties")
public class HealthCheckController {
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckController.class);

    @Autowired
    private HealthCheckService healthCheckService;

    /**
     * Gets the health-check status.
     *
     * @return health-check model entity
     */
    @ApiOperation(value = "Gets health-check status", response = HealthCheck.class)
    @GetMapping(value = "/health-check")
    public ResponseEntity<HealthCheck> getHealthCheck() {
        logger.debug("getHealthCheck");

        HealthCheck healthCheck = healthCheckService.getHealthCheck();
        HttpStatus status = healthCheck.hasNonOperational() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK;

        return new ResponseEntity<>(healthCheck, new HttpHeaders(), status);
    }
}
