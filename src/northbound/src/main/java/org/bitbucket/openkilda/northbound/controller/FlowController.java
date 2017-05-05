package org.bitbucket.openkilda.northbound.controller;

import static org.bitbucket.openkilda.northbound.utils.Constants.CORRELATION_ID;
import static org.bitbucket.openkilda.northbound.utils.Constants.DEFAULT_CORRELATION_ID;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.bitbucket.openkilda.northbound.model.Flow;
import org.bitbucket.openkilda.northbound.service.FlowService;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST Controller for flow requests.
 */
@RestController
@PropertySource("classpath:northbound.properties")
public class FlowController {
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
     * Creates new flow.
     *
     * @param flow          Flow entity
     * @param correlationId Correlation ID header value
     * @return Flow entity
     */
    @TypeHint(Flow.class)
    @StatusCodes({@ResponseCode(code = 200, condition = "Operation is successful"),
            @ResponseCode(code = 400, condition = "Invalid input data"),
            @ResponseCode(code = 404, condition = "Not found"),
            @ResponseCode(code = 500, condition = "General error"),
            @ResponseCode(code = 503, condition = "Service unavailable")})
    @RequestMapping(value = "/flows", method = RequestMethod.PUT,
            produces = APPLICATION_JSON_UTF8_VALUE, consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<Flow> createFlow(@RequestBody Flow flow,
                                           @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID)
                                                   String correlationId) {
        logger.trace("createFlow: {}={}, flow={}", CORRELATION_ID, correlationId, flow.toString());
        Flow response = flowService.create(flow);
        return new ResponseEntity<>(response, new HttpHeaders(), HttpStatus.OK);
    }

    /**
     * Gets flow.
     *
     * @param flowId        Flow ID
     * @param correlationId Correlation ID header value
     * @return Flow entity
     */
    @TypeHint(Flow.class)
    @StatusCodes({@ResponseCode(code = 200, condition = "Operation is successful"),
            @ResponseCode(code = 400, condition = "Invalid input data"),
            @ResponseCode(code = 404, condition = "Not found"),
            @ResponseCode(code = 500, condition = "General error"),
            @ResponseCode(code = 503, condition = "Service unavailable")})
    @RequestMapping(value = "/flows/{flow-id}", method = RequestMethod.GET,
            produces = APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<Flow> getFlow(@PathVariable(name = "flow-id") String flowId,
                                        @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID)
                                                String correlationId) {
        logger.trace("getFlow: {}={}, flow-id={}", CORRELATION_ID, correlationId, flowId);
        Flow response = flowService.get(flowId);
        return new ResponseEntity<>(response, new HttpHeaders(), HttpStatus.OK);
    }

    /**
     * Deletes new flow.
     *
     * @param flowId        Flow ID
     * @param correlationId Correlation ID header value
     * @return Flow entity
     */
    @TypeHint(Flow.class)
    @StatusCodes({@ResponseCode(code = 200, condition = "Operation is successful"),
            @ResponseCode(code = 400, condition = "Invalid input data"),
            @ResponseCode(code = 404, condition = "Not found"),
            @ResponseCode(code = 500, condition = "General error"),
            @ResponseCode(code = 503, condition = "Service unavailable")})
    @RequestMapping(value = "/flows/{flow-id}", method = RequestMethod.DELETE,
            produces = APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<Flow> deleteFlow(@PathVariable(name = "flow-id") String flowId,
                                           @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID)
                                                   String correlationId) {
        logger.trace("deleteFlow: {}={}, flow-id={}", CORRELATION_ID, correlationId, flowId);
        Flow response = flowService.delete(flowId);
        return new ResponseEntity<>(response, new HttpHeaders(), HttpStatus.OK);
    }

    /**
     * Updates existing flow.
     *
     * @param flow          Flow entity
     * @param flowId        Flow ID
     * @param correlationId Correlation ID header value
     * @return Flow entity
     */
    @TypeHint(Flow.class)
    @StatusCodes({@ResponseCode(code = 200, condition = "Operation is successful"),
            @ResponseCode(code = 400, condition = "Invalid input data"),
            @ResponseCode(code = 404, condition = "Not found"),
            @ResponseCode(code = 500, condition = "General error"),
            @ResponseCode(code = 503, condition = "Service unavailable")})
    @RequestMapping(value = "/flows/{flow-id}", method = RequestMethod.PUT,
            produces = APPLICATION_JSON_UTF8_VALUE, consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<Flow> updateFlow(@RequestBody Flow flow, @PathVariable(name = "flow-id") String flowId,
                                           @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID)
                                                   String correlationId) {
        logger.trace("createFlow: {}={}, flow-id={}, flow={}", CORRELATION_ID, correlationId, flowId, flow.toString());
        Flow response = flowService.update(flow);
        return new ResponseEntity<>(response, new HttpHeaders(), HttpStatus.OK);
    }

    /**
     * Dumps all flows.
     *
     * @param correlationId Correlation ID header value
     * @return Flow entity
     */
    @TypeHint(Flow.class)
    @StatusCodes({@ResponseCode(code = 200, condition = "Operation is successful"),
            @ResponseCode(code = 400, condition = "Invalid input data"),
            @ResponseCode(code = 404, condition = "Not found"),
            @ResponseCode(code = 500, condition = "General error"),
            @ResponseCode(code = 503, condition = "Service unavailable")})
    @RequestMapping(value = "/flows", method = RequestMethod.GET,
            produces = APPLICATION_JSON_UTF8_VALUE, consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Flow>> dumpFlows(
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {
        logger.trace("dumpFlow: {}={}", CORRELATION_ID, correlationId);
        List<Flow> response = flowService.dump();
        return new ResponseEntity<>(response, new HttpHeaders(), HttpStatus.OK);
    }
}
