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

package org.openkilda.northbound.controller;

import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;
import static org.openkilda.messaging.Utils.FLOW_ID;

import io.swagger.annotations.ApiParam;
import org.openkilda.messaging.error.MessageError;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.payload.flow.FlowCacheSyncResults;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.info.flow.FlowInfoData;
import org.openkilda.northbound.dto.FlowValidationDto;
import org.openkilda.northbound.service.BatchResults;
import org.openkilda.northbound.service.FlowService;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.openkilda.northbound.utils.ExtraAuthRequired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.InvalidPathException;
import java.util.List;
import java.util.Optional;


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

    private String getUniqueCorrelation(){
        return DEFAULT_CORRELATION_ID+"-"+System.currentTimeMillis();
    }

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
    @RequestMapping(value = "/v1/flows",
            method = RequestMethod.PUT,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FlowPayload> createFlow(
            @RequestBody FlowPayload flow,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {

        if (correlationId.equals(DEFAULT_CORRELATION_ID))
            correlationId = getUniqueCorrelation();

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
    @RequestMapping(value = "/v1/flows/{flow-id}",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<FlowPayload> getFlow(
            @PathVariable(name = "flow-id") String flowId,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {

        if (correlationId.equals(DEFAULT_CORRELATION_ID))
            correlationId = getUniqueCorrelation();

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
    @RequestMapping(value = "/v1/flows/{flow-id}",
            method = RequestMethod.DELETE,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<FlowPayload> deleteFlow(
            @PathVariable(name = "flow-id") String flowId,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {

        if (correlationId.equals(DEFAULT_CORRELATION_ID))
            correlationId = getUniqueCorrelation();

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
    @RequestMapping(value = "/v1/flows/{flow-id}",
            method = RequestMethod.PUT,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FlowPayload> updateFlow(
            @PathVariable(name = "flow-id") String flowId,
            @RequestBody FlowPayload flow,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {

        if (correlationId.equals(DEFAULT_CORRELATION_ID))
            correlationId = getUniqueCorrelation();

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
    @RequestMapping(value = "/v1/flows",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<List<FlowPayload>> getFlows(
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {

        if (correlationId.equals(DEFAULT_CORRELATION_ID))
            correlationId = getUniqueCorrelation();

        logger.debug("Get flows: {}={}", CORRELATION_ID, correlationId);
        List<FlowPayload> response = flowService.getFlows(correlationId);
        return new ResponseEntity<>(response, new HttpHeaders(), HttpStatus.OK);
    }


    /**
     * Delete all flows.
     *
     * @param correlationId correlation ID header value
     * @return list of flows that have been deleted
     */
    @ApiOperation(value = "Delete all flows. Requires special authorization", response = FlowPayload.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, response = FlowPayload.class, message = "Operation is successful"),
            @ApiResponse(code = 400, response = MessageError.class, message = "Invalid input data"),
            @ApiResponse(code = 401, response = MessageError.class, message = "Unauthorized"),
            @ApiResponse(code = 403, response = MessageError.class, message = "Forbidden"),
            @ApiResponse(code = 404, response = MessageError.class, message = "Not found"),
            @ApiResponse(code = 500, response = MessageError.class, message = "General error"),
            @ApiResponse(code = 503, response = MessageError.class, message = "Service unavailable")})
    @RequestMapping(value = "/v1/flows",
            method = RequestMethod.DELETE,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @ExtraAuthRequired
    @SuppressWarnings("unchecked") // the error is unchecked
    public ResponseEntity<List<FlowPayload>> deleteFlows(
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {

        if (correlationId.equals(DEFAULT_CORRELATION_ID))
            correlationId = getUniqueCorrelation();

        logger.debug("Delete flows: {}={}", CORRELATION_ID);
        List<FlowPayload> response = flowService.deleteFlows(correlationId);
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
    @RequestMapping(value = "/v1/flows/status/{flow-id}",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<FlowIdStatusPayload> statusFlow(
            @PathVariable(name = "flow-id") String flowId,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {

        if (correlationId.equals(DEFAULT_CORRELATION_ID))
            correlationId = getUniqueCorrelation();

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
    @RequestMapping(value = "/v1/flows/path/{flow-id}", method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<FlowPathPayload> pathFlow(
            @PathVariable(name = "flow-id") String flowId,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {

        if (correlationId.equals(DEFAULT_CORRELATION_ID))
            correlationId = getUniqueCorrelation();

        logger.debug("Flow path: {}={}, {}={}", CORRELATION_ID, correlationId, FLOW_ID, flowId);
        FlowPathPayload response = flowService.pathFlow(flowId, correlationId);
        return new ResponseEntity<>(response, new HttpHeaders(), HttpStatus.OK);
    }


    /**
     * Push flows to kilda ... this can be used to get flows into kilda without kilda creating them
     * itself. Kilda won't expect to create them .. it may (and should) validate them at some stage.
     *
     * @param externalFlows a list of flows to push to kilda for it to absorb without expectation of creating the flow rules
     * @param correlationId correlation ID header value
     * @return list of flow
     */
    @ApiOperation(value = "Push flows without expectation of modifying switches. It can push to switch and validate.", response = BatchResults.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, response = BatchResults.class, message = "Operation is successful"),
            @ApiResponse(code = 400, response = MessageError.class, message = "Invalid input data"),
            @ApiResponse(code = 401, response = MessageError.class, message = "Unauthorized"),
            @ApiResponse(code = 403, response = MessageError.class, message = "Forbidden"),
            @ApiResponse(code = 404, response = MessageError.class, message = "Not found"),
            @ApiResponse(code = 500, response = MessageError.class, message = "General error"),
            @ApiResponse(code = 503, response = MessageError.class, message = "Service unavailable")})
    @RequestMapping(path = "/v1/push/flows",
            method = RequestMethod.PUT,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public BatchResults pushFlows(
            @RequestBody List<FlowInfoData> externalFlows,
            @ApiParam(value = "default: false. If true, this will propagate rules to the switches.",
                    required = false)
            @RequestParam("propagate") Optional<Boolean> propagate,
            @ApiParam(value = "default: false. If true, will wait until poll timeout for validation.",
                    required = false)
            @RequestParam("propagate") Optional<Boolean> verify,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {

        if (correlationId.equals(DEFAULT_CORRELATION_ID))
            correlationId = getUniqueCorrelation();

        Boolean defaultPropagate = false;
        Boolean defaultVerify = false;
        return flowService.pushFlows(externalFlows, correlationId,
                propagate.orElse(defaultPropagate),
                verify.orElse(defaultVerify));
    }


    /**
     * Unpush flows to kilda ... essentially the opposite of push.
     *
     * @param externalFlows a list of flows to unpush without propagation to Floodlight
     * @param correlationId correlation ID header value
     * @return list of flow
     */
    @ApiOperation(value = "Unpush flows without expectation of modifying switches. It can push to switch and validate.", response = BatchResults.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, response = BatchResults.class, message = "Operation is successful"),
            @ApiResponse(code = 400, response = MessageError.class, message = "Invalid input data"),
            @ApiResponse(code = 401, response = MessageError.class, message = "Unauthorized"),
            @ApiResponse(code = 403, response = MessageError.class, message = "Forbidden"),
            @ApiResponse(code = 404, response = MessageError.class, message = "Not found"),
            @ApiResponse(code = 500, response = MessageError.class, message = "General error"),
            @ApiResponse(code = 503, response = MessageError.class, message = "Service unavailable")})
    @RequestMapping(path = "/v1/unpush/flows",
            method = RequestMethod.PUT,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public BatchResults unpushFlows(
            @RequestBody List<FlowInfoData> externalFlows,
            @ApiParam(value = "default: false. If true, this will propagate rules to the switches.",
                    required = false)
            @RequestParam("propagate") Optional<Boolean> propagate,
            @ApiParam(value = "default: false. If true, will wait until poll timeout for validation.",
                    required = false)
            @RequestParam("propagate") Optional<Boolean> verify,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {

        if (correlationId.equals(DEFAULT_CORRELATION_ID))
            correlationId = getUniqueCorrelation();

        Boolean defaultPropagate = false;
        Boolean defaultVerify = false;
        return flowService.unpushFlows(externalFlows, correlationId,
                propagate.orElse(defaultPropagate),
                verify.orElse(defaultVerify));
    }


    /**
     * Initiates flow rerouting if any shorter paths are available.
     *
     * @param flowId id of flow to be rerouted.
     * @param correlationId correlation ID header value.
     * @return flow payload with updated path.
     */
    @ApiOperation(value = "Reroute flow", response = FlowPathPayload.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, response = FlowPathPayload.class, message = "Operation is successful"),
            @ApiResponse(code = 400, response = MessageError.class, message = "Invalid input data"),
            @ApiResponse(code = 401, response = MessageError.class, message = "Unauthorized"),
            @ApiResponse(code = 403, response = MessageError.class, message = "Forbidden"),
            @ApiResponse(code = 404, response = MessageError.class, message = "Not found"),
            @ApiResponse(code = 500, response = MessageError.class, message = "General error"),
            @ApiResponse(code = 503, response = MessageError.class, message = "Service unavailable")})
    @RequestMapping(path = "/v1/flows/{flow_id}/reroute",
            method = RequestMethod.PATCH)
    @ResponseStatus(HttpStatus.OK)
    public FlowPathPayload rerouteFlow(@PathVariable("flow_id") String flowId,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {

        if (correlationId.equals(DEFAULT_CORRELATION_ID))
            correlationId = getUniqueCorrelation();

        logger.debug("Received reroute request with correlation_id {} for flow {}", correlationId, flowId);
        return flowService.rerouteFlow(flowId, correlationId);
    }


    /**
     * Compares the Flow from the DB to what is on each switch.
     *
     * @param flowId id of flow to be rerouted.
     * @param correlationId correlation ID header value.
     * @return flow payload with updated path.
     */
    @ApiOperation(value = "Validate flow, comparing the DB to each switch", response = FlowPathPayload.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, response = FlowPathPayload.class, message = "Operation is successful"),
            @ApiResponse(code = 400, response = MessageError.class, message = "Invalid input data"),
            @ApiResponse(code = 401, response = MessageError.class, message = "Unauthorized"),
            @ApiResponse(code = 403, response = MessageError.class, message = "Forbidden"),
            @ApiResponse(code = 404, response = MessageError.class, message = "Not found"),
            @ApiResponse(code = 500, response = MessageError.class, message = "General error"),
            @ApiResponse(code = 503, response = MessageError.class, message = "Service unavailable")})
    @RequestMapping(path = "/v1/flows/{flow_id}/validate",
            method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<List<FlowValidationDto>> validateFlow(@PathVariable("flow_id") String flowId,
                                          @RequestHeader(value = CORRELATION_ID,
                                                defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {

        if (correlationId.equals(DEFAULT_CORRELATION_ID))
            correlationId = getUniqueCorrelation();

        logger.debug("Received Flow Validation request with correlation_id {} for flow {}", correlationId, flowId);
        ResponseEntity<List<FlowValidationDto>> response;

        try {
            List<FlowValidationDto> result = flowService.validateFlow(flowId, correlationId);
            if (result == null) {
                logger.info("VALIDATE FLOW: Flow Not Found: {}", flowId);
                response = new ResponseEntity<>(null, new HttpHeaders(), HttpStatus.NOT_FOUND);
            } else {
                response = new ResponseEntity<>(result, new HttpHeaders(), HttpStatus.OK);
            }
        } catch (InvalidPathException e) {
            logger.error("VALIDATE FLOW: Flow has no path: {}", flowId);
            logger.error(e.getMessage());
            response = new ResponseEntity<>(null, new HttpHeaders(), HttpStatus.NOT_FOUND);
        }
        return response;
    }



    /**
     * Make sure any Flow caches are in sync with the DB. This is primarily a janitor primitive.
     *
     * @param correlationId correlation ID header value.
     * @return a detailed response of the sync operation (added, deleted, modified, unchanged flows)
     */
    @ApiOperation(value = "Sync Flow Cache(s)", response = FlowCacheSyncResults.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, response = FlowCacheSyncResults.class, message = "Operation is successful"),
            @ApiResponse(code = 400, response = MessageError.class, message = "Invalid input data"),
            @ApiResponse(code = 401, response = MessageError.class, message = "Unauthorized"),
            @ApiResponse(code = 403, response = MessageError.class, message = "Forbidden"),
            @ApiResponse(code = 404, response = MessageError.class, message = "Not found"),
            @ApiResponse(code = 500, response = MessageError.class, message = "General error"),
            @ApiResponse(code = 503, response = MessageError.class, message = "Service unavailable")})
    @RequestMapping(path = "/v1/flows/cachesync",
            method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public FlowCacheSyncResults syncFlowCache(@RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {

        if (correlationId.equals(DEFAULT_CORRELATION_ID))
            correlationId = getUniqueCorrelation();

        logger.debug("Received sync FlowCache with correlation_id {}", correlationId);
        return flowService.syncFlowCache(correlationId);
    }


}
