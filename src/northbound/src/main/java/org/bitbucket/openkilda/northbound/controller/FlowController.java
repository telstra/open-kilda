package org.bitbucket.openkilda.northbound.controller;

import static org.bitbucket.openkilda.northbound.utils.Constants.CORRELATION_ID;
import static org.bitbucket.openkilda.northbound.utils.Constants.DEFAULT_CORRELATION_ID;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.bitbucket.openkilda.messaging.payload.FlowPayload;
import org.bitbucket.openkilda.messaging.payload.response.FlowPathResponsePayload;
import org.bitbucket.openkilda.messaging.payload.response.FlowStatusResponsePayload;
import org.bitbucket.openkilda.messaging.payload.response.FlowsResponsePayload;
import org.bitbucket.openkilda.messaging.payload.response.FlowsStatusResponsePayload;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
     * @param flow          flow
     * @param correlationId correlation ID header value
     * @return flow
     */
    @TypeHint(FlowPayload.class)
    @StatusCodes({
            @ResponseCode(code = 200, condition = "Operation is successful"),
            @ResponseCode(code = 400, condition = "Invalid input data"),
            @ResponseCode(code = 404, condition = "Not found"),
            @ResponseCode(code = 500, condition = "General error"),
            @ResponseCode(code = 503, condition = "Service unavailable")})
    @RequestMapping(
            value = "/flows",
            method = RequestMethod.PUT,
            produces = APPLICATION_JSON_UTF8_VALUE,
            consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<FlowPayload> createFlow(
            @RequestBody FlowPayload flow,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {
        logger.trace("Create flow: {}={}, flow={}", CORRELATION_ID, correlationId, flow);
        FlowPayload response = flowService.createFlow(flow, correlationId);
        return new ResponseEntity<>(response, new HttpHeaders(), HttpStatus.OK);
    }

    /**
     * Gets flow.
     *
     * @param flowId        flow id
     * @param correlationId correlation ID header value
     * @return flow
     */
    @TypeHint(FlowPayload.class)
    @StatusCodes({
            @ResponseCode(code = 200, condition = "Operation is successful"),
            @ResponseCode(code = 400, condition = "Invalid input data"),
            @ResponseCode(code = 404, condition = "Not found"),
            @ResponseCode(code = 500, condition = "General error"),
            @ResponseCode(code = 503, condition = "Service unavailable")})
    @RequestMapping(
            value = "/flows/{flow-id}",
            method = RequestMethod.GET,
            produces = APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<FlowPayload> getFlow(
            @PathVariable(name = "flow-id") String flowId,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {
        logger.trace("Get flow: {}={}, flow-id={}", CORRELATION_ID, correlationId, flowId);
        FlowPayload response = flowService.getFlow(flowId, correlationId);
        return new ResponseEntity<>(response, new HttpHeaders(), HttpStatus.OK);
    }

    /**
     * Deletes flow.
     *
     * @param flowId        flow id
     * @param correlationId correlation ID header value
     * @return flow
     */
    @TypeHint(FlowPayload.class)
    @StatusCodes({
            @ResponseCode(code = 200, condition = "Operation is successful"),
            @ResponseCode(code = 400, condition = "Invalid input data"),
            @ResponseCode(code = 404, condition = "Not found"),
            @ResponseCode(code = 500, condition = "General error"),
            @ResponseCode(code = 503, condition = "Service unavailable")})
    @RequestMapping(
            value = "/flows/{flow-id}",
            method = RequestMethod.DELETE,
            produces = APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<FlowPayload> deleteFlow(
            @PathVariable(name = "flow-id") String flowId,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {
        logger.trace("Delete flow: {}={}, flow-id={}", CORRELATION_ID, correlationId, flowId);
        FlowPayload response = flowService.deleteFlow(flowId, correlationId);
        return new ResponseEntity<>(response, new HttpHeaders(), HttpStatus.OK);
    }

    /**
     * Updates existing flow.
     *
     * @param flow          flow
     * @param flowId        flow id
     * @param correlationId correlation ID header value
     * @return flow
     */
    @TypeHint(FlowPayload.class)
    @StatusCodes({
            @ResponseCode(code = 200, condition = "Operation is successful"),
            @ResponseCode(code = 400, condition = "Invalid input data"),
            @ResponseCode(code = 404, condition = "Not found"),
            @ResponseCode(code = 500, condition = "General error"),
            @ResponseCode(code = 503, condition = "Service unavailable")})
    @RequestMapping(
            value = "/flows/{flow-id}",
            method = RequestMethod.PUT,
            produces = APPLICATION_JSON_UTF8_VALUE,
            consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<FlowPayload> updateFlow(
            @PathVariable(name = "flow-id") String flowId,
            @RequestBody FlowPayload flow,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {
        logger.trace("Update flow: {}={}, flow-id={}, flow={}", CORRELATION_ID, correlationId, flowId, flow);
        FlowPayload response = flowService.updateFlow(flow, correlationId);
        return new ResponseEntity<>(response, new HttpHeaders(), HttpStatus.OK);
    }

    /**
     * Dumps all flows.
     * Dumps all flows with specific status if specified.
     *
     * @param status        target flow status
     * @param correlationId correlation ID header value
     * @return list of flow
     */
    @TypeHint(FlowPayload.class)
    @StatusCodes({
            @ResponseCode(code = 200, condition = "Operation is successful"),
            @ResponseCode(code = 400, condition = "Invalid input data"),
            @ResponseCode(code = 404, condition = "Not found"),
            @ResponseCode(code = 500, condition = "General error"),
            @ResponseCode(code = 503, condition = "Service unavailable")})
    @RequestMapping(
            value = "/flows",
            method = RequestMethod.GET,
            produces = APPLICATION_JSON_UTF8_VALUE,
            consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<FlowsResponsePayload> getFlows(
            @RequestParam(value = "status", required = false) String status,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {
        logger.trace("Get flows: {}={}", CORRELATION_ID, correlationId);
        FlowsResponsePayload response = flowService.getFlows(status, correlationId);
        return new ResponseEntity<>(response, new HttpHeaders(), HttpStatus.OK);
    }

    /**
     * Gets flow status.
     *
     * @param flowId        flow id
     * @param correlationId correlation ID header value
     * @return list of flow
     */
    @TypeHint(FlowPayload.class)
    @StatusCodes({
            @ResponseCode(code = 200, condition = "Operation is successful"),
            @ResponseCode(code = 400, condition = "Invalid input data"),
            @ResponseCode(code = 404, condition = "Not found"),
            @ResponseCode(code = 500, condition = "General error"),
            @ResponseCode(code = 503, condition = "Service unavailable")})
    @RequestMapping(
            value = "/flows/status/{flow-id}",
            method = RequestMethod.GET,
            produces = APPLICATION_JSON_UTF8_VALUE,
            consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<FlowStatusResponsePayload> statusFlow(
            @PathVariable(name = "flow-id") String flowId,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {
        logger.trace("Flow status: {}={}", CORRELATION_ID, correlationId);
        FlowStatusResponsePayload response = flowService.statusFlow(flowId, correlationId);
        return new ResponseEntity<>(response, new HttpHeaders(), HttpStatus.OK);
    }

    /**
     * Gets all flows statuses.
     * Gets all flows with specific status if specified.
     *
     * @param status        target flow status
     * @param correlationId correlation ID header value
     * @return list of flow
     */
    @TypeHint(FlowPayload.class)
    @StatusCodes({
            @ResponseCode(code = 200, condition = "Operation is successful"),
            @ResponseCode(code = 400, condition = "Invalid input data"),
            @ResponseCode(code = 404, condition = "Not found"),
            @ResponseCode(code = 500, condition = "General error"),
            @ResponseCode(code = 503, condition = "Service unavailable")})
    @RequestMapping(
            value = "/flows/status",
            method = RequestMethod.GET,
            produces = APPLICATION_JSON_UTF8_VALUE,
            consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<FlowsStatusResponsePayload> statusFlows(
            @RequestParam(value = "status", required = false) String status,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {
        logger.trace("Flows status: {}={}", CORRELATION_ID, correlationId);
        FlowsStatusResponsePayload response = flowService.statusFlows(status, correlationId);
        return new ResponseEntity<>(response, new HttpHeaders(), HttpStatus.OK);
    }

    /**
     * Gets flow path.
     *
     * @param flowId        flow id
     * @param correlationId correlation ID header value
     * @return list of flow
     */
    @TypeHint(FlowPayload.class)
    @StatusCodes({
            @ResponseCode(code = 200, condition = "Operation is successful"),
            @ResponseCode(code = 400, condition = "Invalid input data"),
            @ResponseCode(code = 404, condition = "Not found"),
            @ResponseCode(code = 500, condition = "General error"),
            @ResponseCode(code = 503, condition = "Service unavailable")})
    @RequestMapping(
            value = "/flows/path/{flow-id}", method = RequestMethod.GET,
            produces = APPLICATION_JSON_UTF8_VALUE,
            consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<FlowPathResponsePayload> pathFlow(
            @PathVariable(name = "flow-id") String flowId,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {
        logger.trace("Flow path: {}={}, flow-id={}", CORRELATION_ID, correlationId, flowId);
        FlowPathResponsePayload response = flowService.pathFlow(flowId, correlationId);
        return new ResponseEntity<>(response, new HttpHeaders(), HttpStatus.OK);
    }
}
