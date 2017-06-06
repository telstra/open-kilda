package org.bitbucket.openkilda.topology.controller;

import static org.bitbucket.openkilda.messaging.Utils.CORRELATION_ID;
import static org.bitbucket.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.bitbucket.openkilda.messaging.payload.flow.FlowPayload;
import org.bitbucket.openkilda.topology.model.Topology;
import org.bitbucket.openkilda.topology.service.TopologyService;

import com.webcohesion.enunciate.metadata.rs.ResponseCode;
import com.webcohesion.enunciate.metadata.rs.StatusCodes;
import com.webcohesion.enunciate.metadata.rs.TypeHint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for topology requests.
 */
@RestController
@PropertySource("classpath:topology.properties")
public class TopologyController {
    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(TopologyController.class);

    /**
     * The topology service instance.
     */
    @Autowired
    private TopologyService topologyService;

    /**
     * Cleans topology.
     *
     * @param correlationId correlation ID header value
     * @return network topology
     */
    @StatusCodes({
            @ResponseCode(code = 200, condition = "Operation is successful"),
            @ResponseCode(code = 400, condition = "Invalid input data"),
            @ResponseCode(code = 404, condition = "Not found"),
            @ResponseCode(code = 500, condition = "General error"),
            @ResponseCode(code = 503, condition = "Service unavailable")})
    @RequestMapping(
            value = "/clear",
            method = RequestMethod.GET,
            produces = APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<Topology> clear(
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {
        logger.debug("Clear topology: {}={}", CORRELATION_ID, correlationId);
        Topology topology = topologyService.clear(correlationId);
        return new ResponseEntity<>(topology, new HttpHeaders(), HttpStatus.OK);
    }

    /**
     * Gets flow.
     *
     * @param correlationId correlation ID header value
     * @return network topology
     */
    @TypeHint(FlowPayload.class)
    @StatusCodes({
            @ResponseCode(code = 200, condition = "Operation is successful"),
            @ResponseCode(code = 400, condition = "Invalid input data"),
            @ResponseCode(code = 404, condition = "Not found"),
            @ResponseCode(code = 500, condition = "General error"),
            @ResponseCode(code = 503, condition = "Service unavailable")})
    @RequestMapping(
            value = "/network",
            method = RequestMethod.GET,
            produces = APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<Topology> network(
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {
        logger.debug("Get topology: {}={}", CORRELATION_ID, correlationId);
        Topology topology = topologyService.network(correlationId);
        return new ResponseEntity<>(topology, new HttpHeaders(), HttpStatus.OK);
    }
}
