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
import static org.openkilda.messaging.Utils.EXTRA_AUTH;
import static org.openkilda.messaging.Utils.FLOW_ID;

import org.openkilda.messaging.error.MessageError;
import org.openkilda.messaging.payload.flow.FlowCacheSyncResults;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.info.flow.FlowInfoData;
import org.openkilda.northbound.service.BatchResults;
import org.openkilda.northbound.service.FlowService;

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
import org.springframework.web.bind.annotation.*;

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
    @RequestMapping(
            value = "/flows",
            method = RequestMethod.DELETE,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @SuppressWarnings("unchecked") // the error is unchecked
    public ResponseEntity<List<FlowPayload>> deleteFlows(
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId,
            @RequestHeader(value = EXTRA_AUTH, defaultValue = "0") long extra_auth
            ) {
        logger.debug("Delete flows: {}={}, {}={}", CORRELATION_ID, correlationId, EXTRA_AUTH, extra_auth);

        long current_auth = System.currentTimeMillis();
        if (Math.abs(current_auth-extra_auth) > 120*1000) {
            /*
             * The request needs to be within 120 seconds of the system clock.
             */
            return new ResponseEntity("Invalid Auth: " + current_auth, new HttpHeaders(), HttpStatus.UNAUTHORIZED);
        }

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


    /**
     * Push flows to kilda ... this can be used to get flows into kilda without kilda creating them
     * itself. Kilda won't expect to create them .. it may (and should) validate them at some stage.
     *
     * @param externalFlows a list of flows to push to kilda for it to absorb without expectation of creating the flow rules
     * @param correlationId correlation ID header value
     * @return list of flow
     */
    @ApiOperation(value = "Push flows without expectation of modifying switches", response = BatchResults.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, response = BatchResults.class, message = "Operation is successful"),
            @ApiResponse(code = 400, response = MessageError.class, message = "Invalid input data"),
            @ApiResponse(code = 401, response = MessageError.class, message = "Unauthorized"),
            @ApiResponse(code = 403, response = MessageError.class, message = "Forbidden"),
            @ApiResponse(code = 404, response = MessageError.class, message = "Not found"),
            @ApiResponse(code = 500, response = MessageError.class, message = "General error"),
            @ApiResponse(code = 503, response = MessageError.class, message = "Service unavailable")})
    @RequestMapping(path = "/push/flows",
            method = RequestMethod.PUT,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public BatchResults pushFlows(
            @RequestBody List<FlowInfoData> externalFlows,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {

        return flowService.pushFlows(externalFlows, correlationId);
    }


    /**
     * Unpush flows to kilda ... essentially the opposite of push.
     *
     * @param externalFlows a list of flows to unpush without propagation to Floodlight
     * @param correlationId correlation ID header value
     * @return list of flow
     */
    @ApiOperation(value = "Unpush flows without expectation of modifying switches", response = BatchResults.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, response = BatchResults.class, message = "Operation is successful"),
            @ApiResponse(code = 400, response = MessageError.class, message = "Invalid input data"),
            @ApiResponse(code = 401, response = MessageError.class, message = "Unauthorized"),
            @ApiResponse(code = 403, response = MessageError.class, message = "Forbidden"),
            @ApiResponse(code = 404, response = MessageError.class, message = "Not found"),
            @ApiResponse(code = 500, response = MessageError.class, message = "General error"),
            @ApiResponse(code = 503, response = MessageError.class, message = "Service unavailable")})
    @RequestMapping(path = "/unpush/flows",
            method = RequestMethod.PUT,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public BatchResults unpushFlows(
            @RequestBody List<FlowInfoData> externalFlows,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {

        return flowService.unpushFlows(externalFlows, correlationId);
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
    @RequestMapping(path = "/flows/{flow_id}/reroute",
            method = RequestMethod.PATCH)
    @ResponseStatus(HttpStatus.OK)
    public FlowPathPayload rerouteFlow(@PathVariable("flow_id") String flowId,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {
        logger.debug("Received reroute request with correlation_id {} for flow {}", correlationId, flowId);
        return flowService.rerouteFlow(flowId, correlationId);
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
    @RequestMapping(path = "/flows/cachesync",
            method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public FlowCacheSyncResults syncFlowCache(@RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {
        logger.debug("Received sync FlowCache with correlation_id {}", correlationId);
        return flowService.syncFlowCache(correlationId);
    }


}
