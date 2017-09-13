package org.bitbucket.openkilda.northbound.controller;

import static org.bitbucket.openkilda.messaging.Utils.CORRELATION_ID;
import static org.bitbucket.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;
import static org.bitbucket.openkilda.messaging.Utils.FLOW_ID;

import org.bitbucket.openkilda.messaging.error.MessageError;
import org.bitbucket.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPathPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPayload;
import org.bitbucket.openkilda.northbound.service.FlowService;

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
     * @param flow          flow
     * @param correlationId correlation ID header value
     * @return flow
     */
    @ApiOperation(value = "Creates new flow", response = FlowPayload.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, response = FlowPayload.class, message = "Operation is successful"),
            @ApiResponse(code = 400, response = MessageError.class, message = "Invalid input data"),
            @ApiResponse(code = 401, response = MessageError.class, message = "Unauthorized"),
            @ApiResponse(code = 403, response = MessageError.class, message = "Forbidden"),
            @ApiResponse(code = 404, response = MessageError.class, message = "Not found"),
            @ApiResponse(code = 500, response = MessageError.class, message = "General error"),
            @ApiResponse(code = 503, response = MessageError.class, message = "Service unavailable")})
    @RequestMapping(
            value = "/flows",
            method = RequestMethod.PUT,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FlowPayload> createFlow(
            @RequestBody FlowPayload flow,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {
        logger.debug("Create flow: {}={}, flow={}", CORRELATION_ID, correlationId, flow);
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
    @ApiOperation(value = "Gets flow", response = FlowPayload.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, response = FlowPayload.class, message = "Operation is successful"),
            @ApiResponse(code = 400, response = MessageError.class, message = "Invalid input data"),
            @ApiResponse(code = 401, response = MessageError.class, message = "Unauthorized"),
            @ApiResponse(code = 403, response = MessageError.class, message = "Forbidden"),
            @ApiResponse(code = 404, response = MessageError.class, message = "Not found"),
            @ApiResponse(code = 500, response = MessageError.class, message = "General error"),
            @ApiResponse(code = 503, response = MessageError.class, message = "Service unavailable")})
    @RequestMapping(
            value = "/flows/{flow-id}",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<FlowPayload> getFlow(
            @PathVariable(name = "flow-id") String flowId,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {
        logger.debug("Get flow: {}={}, {}={}", CORRELATION_ID, correlationId, FLOW_ID, flowId);
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
    @ApiOperation(value = "Deletes flow", response = FlowPayload.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, response = FlowPayload.class, message = "Operation is successful"),
            @ApiResponse(code = 400, response = MessageError.class, message = "Invalid input data"),
            @ApiResponse(code = 401, response = MessageError.class, message = "Unauthorized"),
            @ApiResponse(code = 403, response = MessageError.class, message = "Forbidden"),
            @ApiResponse(code = 404, response = MessageError.class, message = "Not found"),
            @ApiResponse(code = 500, response = MessageError.class, message = "General error"),
            @ApiResponse(code = 503, response = MessageError.class, message = "Service unavailable")})
    @RequestMapping(
            value = "/flows/{flow-id}",
            method = RequestMethod.DELETE,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<FlowPayload> deleteFlow(
            @PathVariable(name = "flow-id") String flowId,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {
        logger.debug("Delete flow: {}={}, {}={}", CORRELATION_ID, correlationId, FLOW_ID, flowId);
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
    @ApiOperation(value = "Updates flow", response = FlowPayload.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, response = FlowPayload.class, message = "Operation is successful"),
            @ApiResponse(code = 400, response = MessageError.class, message = "Invalid input data"),
            @ApiResponse(code = 401, response = MessageError.class, message = "Unauthorized"),
            @ApiResponse(code = 403, response = MessageError.class, message = "Forbidden"),
            @ApiResponse(code = 404, response = MessageError.class, message = "Not found"),
            @ApiResponse(code = 500, response = MessageError.class, message = "General error"),
            @ApiResponse(code = 503, response = MessageError.class, message = "Service unavailable")})
    @RequestMapping(
            value = "/flows/{flow-id}",
            method = RequestMethod.PUT,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FlowPayload> updateFlow(
            @PathVariable(name = "flow-id") String flowId,
            @RequestBody FlowPayload flow,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {
        logger.debug("Update flow: {}={}, {}={}, flow={}", CORRELATION_ID, correlationId, FLOW_ID, flowId, flow);
        FlowPayload response = flowService.updateFlow(flow, correlationId);
        return new ResponseEntity<>(response, new HttpHeaders(), HttpStatus.OK);
    }

    /**
     * Dumps all flows. Dumps all flows with specific status if specified.
     *
     * @param correlationId correlation ID header value
     * @return list of flow
     */
    @ApiOperation(value = "Dumps all flows", response = FlowPayload.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, response = FlowPayload.class, message = "Operation is successful"),
            @ApiResponse(code = 400, response = MessageError.class, message = "Invalid input data"),
            @ApiResponse(code = 401, response = MessageError.class, message = "Unauthorized"),
            @ApiResponse(code = 403, response = MessageError.class, message = "Forbidden"),
            @ApiResponse(code = 404, response = MessageError.class, message = "Not found"),
            @ApiResponse(code = 500, response = MessageError.class, message = "General error"),
            @ApiResponse(code = 503, response = MessageError.class, message = "Service unavailable")})
    @RequestMapping(
            value = "/flows",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<List<FlowPayload>> getFlows(
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {
        logger.debug("Get flows: {}={}", CORRELATION_ID, correlationId);
        List<FlowPayload> response = flowService.getFlows(correlationId);
        return new ResponseEntity<>(response, new HttpHeaders(), HttpStatus.OK);
    }

    /**
     * Gets flow status.
     *
     * @param flowId        flow id
     * @param correlationId correlation ID header value
     * @return list of flow
     */
    @ApiOperation(value = "Gets flow status", response = FlowIdStatusPayload.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, response = FlowIdStatusPayload.class, message = "Operation is successful"),
            @ApiResponse(code = 400, response = MessageError.class, message = "Invalid input data"),
            @ApiResponse(code = 401, response = MessageError.class, message = "Unauthorized"),
            @ApiResponse(code = 403, response = MessageError.class, message = "Forbidden"),
            @ApiResponse(code = 404, response = MessageError.class, message = "Not found"),
            @ApiResponse(code = 500, response = MessageError.class, message = "General error"),
            @ApiResponse(code = 503, response = MessageError.class, message = "Service unavailable")})
    @RequestMapping(
            value = "/flows/status/{flow-id}",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<FlowIdStatusPayload> statusFlow(
            @PathVariable(name = "flow-id") String flowId,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {
        logger.debug("Flow status: {}={}", CORRELATION_ID, correlationId);
        FlowIdStatusPayload response = flowService.statusFlow(flowId, correlationId);
        return new ResponseEntity<>(response, new HttpHeaders(), HttpStatus.OK);
    }

    /**
     * Gets flow path.
     *
     * @param flowId        flow id
     * @param correlationId correlation ID header value
     * @return list of flow
     */
    @ApiOperation(value = "Gets flow path", response = FlowPathPayload.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, response = FlowPathPayload.class, message = "Operation is successful"),
            @ApiResponse(code = 400, response = MessageError.class, message = "Invalid input data"),
            @ApiResponse(code = 401, response = MessageError.class, message = "Unauthorized"),
            @ApiResponse(code = 403, response = MessageError.class, message = "Forbidden"),
            @ApiResponse(code = 404, response = MessageError.class, message = "Not found"),
            @ApiResponse(code = 500, response = MessageError.class, message = "General error"),
            @ApiResponse(code = 503, response = MessageError.class, message = "Service unavailable")})
    @RequestMapping(
            value = "/flows/path/{flow-id}", method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<FlowPathPayload> pathFlow(
            @PathVariable(name = "flow-id") String flowId,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {
        logger.debug("Flow path: {}={}, {}={}", CORRELATION_ID, correlationId, FLOW_ID, flowId);
        FlowPathPayload response = flowService.pathFlow(flowId, correlationId);
        return new ResponseEntity<>(response, new HttpHeaders(), HttpStatus.OK);
    }
}
