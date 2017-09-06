package org.bitbucket.openkilda.northbound.controller;

import static org.bitbucket.openkilda.messaging.Utils.CORRELATION_ID;
import static org.bitbucket.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;

import org.bitbucket.openkilda.messaging.error.MessageError;
import org.bitbucket.openkilda.northbound.model.HealthCheck;
import org.bitbucket.openkilda.northbound.service.HealthCheckService;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for health-check request.
 */
@RestController
@PropertySource("classpath:northbound.properties")
public class HealthCheckController {
    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckController.class);

    /**
     * The health-check instance.
     */
    @Autowired
    private HealthCheckService healthCheckService;

    /**
     * Gets the health-check status.
     *
     * @param correlationId request correlation id
     * @return health-check model entity
     */
    @ApiOperation(value = "Gets health-check status", response = HealthCheck.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, response = MessageError.class, message = "Operation is successful"),
            @ApiResponse(code = 401, response = MessageError.class, message = "Unauthorized"),
            @ApiResponse(code = 403, response = MessageError.class, message = "Forbidden"),
            @ApiResponse(code = 503, response = MessageError.class, message = "Service unavailable")})
    @RequestMapping(value = "/health-check",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<HealthCheck> getHealthCheck(
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {
        logger.debug("getHealthCheck");

        HealthCheck healthCheck = healthCheckService.getHealthCheck(correlationId);
        HttpStatus status = healthCheck.hasNonOperational() ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.OK;

        return new ResponseEntity<>(healthCheck, new HttpHeaders(), status);
    }
}
