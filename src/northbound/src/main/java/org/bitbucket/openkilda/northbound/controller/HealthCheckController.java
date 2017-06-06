package org.bitbucket.openkilda.northbound.controller;

import static org.bitbucket.openkilda.messaging.error.ErrorType.INTERNAL_ERROR;

import org.bitbucket.openkilda.messaging.error.MessageException;
import org.bitbucket.openkilda.northbound.model.HealthCheck;

import com.webcohesion.enunciate.metadata.rs.ResponseCode;
import com.webcohesion.enunciate.metadata.rs.StatusCodes;
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
    @RequestMapping(value = "/health-check",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @StatusCodes({@ResponseCode(code = 200, condition = "Operation is successful"),
            @ResponseCode(code = 503, condition = "Service unavailable")})
    public ResponseEntity<HealthCheck> getHealthCheck() {
        logger.debug("getHealthCheck");
        if (healthCheck == null) {
            throw new MessageException(INTERNAL_ERROR, System.currentTimeMillis());
        }
        return new ResponseEntity<>(healthCheck, new HttpHeaders(), HttpStatus.OK);
    }
}
