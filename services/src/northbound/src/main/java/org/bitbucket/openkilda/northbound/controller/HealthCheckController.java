package org.bitbucket.openkilda.northbound.controller;

import static org.bitbucket.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;
import static org.bitbucket.openkilda.messaging.error.ErrorType.INTERNAL_ERROR;

import org.bitbucket.openkilda.messaging.error.MessageError;
import org.bitbucket.openkilda.messaging.error.MessageException;
import org.bitbucket.openkilda.northbound.model.HealthCheck;

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
    private HealthCheck healthCheck;

    /**
     * Gets the health-check status.
     *
     * @return health-check model entity
     */
    @ApiOperation(value = "Gets health-check status", response = HealthCheck.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, response = MessageError.class, message = "Operation is successful"),
            @ApiResponse(code = 401, response = MessageError.class, message = "Unauthorized"),
            @ApiResponse(code = 403, response = MessageError.class, message = "Forbidden"),
            @ApiResponse(code = 404, response = MessageError.class, message = "Not found"),
            @ApiResponse(code = 503, response = MessageError.class, message = "Service unavailable")})
    @RequestMapping(value = "/health-check",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<HealthCheck> getHealthCheck() {
        logger.debug("getHealthCheck");
        if (healthCheck == null) {
            throw new MessageException(DEFAULT_CORRELATION_ID, System.currentTimeMillis(),
                    INTERNAL_ERROR, "Service unavailable", "Could not get initial data");
        }
        return new ResponseEntity<>(healthCheck, new HttpHeaders(), HttpStatus.OK);
    }
}
