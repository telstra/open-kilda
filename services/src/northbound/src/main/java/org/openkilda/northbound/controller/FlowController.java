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

import static org.openkilda.messaging.Utils.EXTRA_AUTH;

import org.openkilda.messaging.command.flow.SynchronizeCacheAction;
import org.openkilda.messaging.error.MessageError;
import org.openkilda.messaging.info.flow.FlowInfoData;
import org.openkilda.messaging.payload.flow.FlowCacheSyncResults;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowReroutePayload;
import org.openkilda.northbound.dto.BatchResults;
import org.openkilda.northbound.dto.flows.FlowValidationDto;
import org.openkilda.northbound.dto.flows.PingInput;
import org.openkilda.northbound.dto.flows.PingOutput;
import org.openkilda.northbound.service.FlowService;
import org.openkilda.northbound.utils.ExtraAuthRequired;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.InvalidPathException;
import java.util.List;
import java.util.Optional;


/**
 * REST Controller for flow requests.
 */
@RestController
@PropertySource("classpath:northbound.properties")
@Api
@ApiResponses(value = {
        @ApiResponse(code = 200, message = "Operation is successful"),
        @ApiResponse(code = 400, response = MessageError.class, message = "Invalid input data"),
        @ApiResponse(code = 401, response = MessageError.class, message = "Unauthorized"),
        @ApiResponse(code = 403, response = MessageError.class, message = "Forbidden"),
        @ApiResponse(code = 404, response = MessageError.class, message = "Not found"),
        @ApiResponse(code = 500, response = MessageError.class, message = "General error"),
        @ApiResponse(code = 503, response = MessageError.class, message = "Service unavailable")})
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
     * @return flow
     */
    @ApiOperation(value = "Creates new flow", response = FlowPayload.class)
    @RequestMapping(
            value = "/flows",
            method = RequestMethod.PUT,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FlowPayload> createFlow(@RequestBody FlowPayload flow) {
        FlowPayload response = flowService.createFlow(flow);
        return new ResponseEntity<>(response, new HttpHeaders(), HttpStatus.OK);
    }

    /**
     * Gets flow.
     *
     * @param flowId        flow id
     * @return flow
     */
    @ApiOperation(value = "Gets flow", response = FlowPayload.class)
    @RequestMapping(
            value = "/flows/{flow-id:.+}",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<FlowPayload> getFlow(@PathVariable(name = "flow-id") String flowId) {
        FlowPayload response = flowService.getFlow(flowId);
        return new ResponseEntity<>(response, new HttpHeaders(), HttpStatus.OK);
    }

    /**
     * Deletes flow.
     *
     * @param flowId        flow id
     * @return flow
     */
    @ApiOperation(value = "Deletes flow", response = FlowPayload.class)
    @RequestMapping(
            value = "/flows/{flow-id:.+}",
            method = RequestMethod.DELETE,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<FlowPayload> deleteFlow(@PathVariable(name = "flow-id") String flowId) {
        FlowPayload response = flowService.deleteFlow(flowId);
        return new ResponseEntity<>(response, new HttpHeaders(), HttpStatus.OK);
    }

    /**
     * Updates existing flow.
     *
     * @param flow          flow
     * @param flowId        flow id
     * @return flow
     */
    @ApiOperation(value = "Updates flow", response = FlowPayload.class)
    @RequestMapping(
            value = "/flows/{flow-id:.+}",
            method = RequestMethod.PUT,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FlowPayload> updateFlow(
            @PathVariable(name = "flow-id") String flowId,
            @RequestBody FlowPayload flow) {
        FlowPayload response = flowService.updateFlow(flow);
        return new ResponseEntity<>(response, new HttpHeaders(), HttpStatus.OK);
    }

    /**
     * Dumps all flows. Dumps all flows with specific status if specified.
     *
     * @return list of flow
     */
    @ApiOperation(value = "Dumps all flows", response = FlowPayload.class, responseContainer = "List")
    @RequestMapping(
            value = "/flows",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<List<FlowPayload>> getFlows() {
        List<FlowPayload> response = flowService.getFlows();
        return new ResponseEntity<>(response, new HttpHeaders(), HttpStatus.OK);
    }


    /**
     * Delete all flows.
     *
     * @return list of flows that have been deleted
     */
    @ApiOperation(value = "Delete all flows. Requires special authorization", response = FlowPayload.class,
            responseContainer = "List")
    @RequestMapping(
            value = "/flows",
            method = RequestMethod.DELETE,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @ExtraAuthRequired
    @SuppressWarnings("unchecked") // the error is unchecked
    public ResponseEntity<List<FlowPayload>> deleteFlows(
            @RequestHeader(value = EXTRA_AUTH, defaultValue = "0") long extraAuth) {
        long currentAuth = System.currentTimeMillis();
        if (Math.abs(currentAuth - extraAuth) > 120 * 1000) {
            /*
             * The request needs to be within 120 seconds of the system clock.
             */
            return new ResponseEntity("Invalid Auth: " + currentAuth, new HttpHeaders(), HttpStatus.UNAUTHORIZED);
        }

        List<FlowPayload> response = flowService.deleteFlows();
        return new ResponseEntity<>(response, new HttpHeaders(), HttpStatus.OK);
    }



    /**
     * Gets flow status.
     *
     * @param flowId        flow id
     * @return list of flow
     */
    @ApiOperation(value = "Gets flow status", response = FlowIdStatusPayload.class)
    @RequestMapping(
            value = "/flows/status/{flow-id:.+}",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<FlowIdStatusPayload> statusFlow(@PathVariable(name = "flow-id") String flowId) {
        FlowIdStatusPayload response = flowService.statusFlow(flowId);
        return new ResponseEntity<>(response, new HttpHeaders(), HttpStatus.OK);
    }

    /**
     * Gets flow path.
     *
     * @param flowId        flow id
     * @return list of flow
     */
    @ApiOperation(value = "Gets flow path", response = FlowPathPayload.class)
    @RequestMapping(
            value = "/flows/{flow-id}/path", method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<FlowPathPayload> pathFlow(@PathVariable(name = "flow-id") String flowId) {
        FlowPathPayload response = flowService.pathFlow(flowId);
        return new ResponseEntity<>(response, new HttpHeaders(), HttpStatus.OK);
    }


    /**
     * Push flows to kilda ... this can be used to get flows into kilda without kilda creating them
     * itself. Kilda won't expect to create them .. it may (and should) validate them at some stage.
     *
     * @param externalFlows a list of flows to push to kilda for it to absorb without expectation of creating the flow
     *        rules
     * @return list of flow
     */
    @ApiOperation(value = "Push flows without expectation of modifying switches. It can push to switch and validate.",
            response = BatchResults.class)
    @RequestMapping(path = "/push/flows",
            method = RequestMethod.PUT,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public BatchResults pushFlows(
            @RequestBody List<FlowInfoData> externalFlows,
            @ApiParam(value = "default: false. If true, this will propagate rules to the switches.",
                    required = false)
            @RequestParam(value = "propagate", required = false) Optional<Boolean> propagate,
            @ApiParam(value = "default: false. If true, will wait until poll timeout for validation.",
                    required = false)
            @RequestParam("verify") Optional<Boolean> verify) {

        Boolean defaultPropagate = false;
        Boolean defaultVerify = false;
        return flowService.pushFlows(externalFlows, propagate.orElse(defaultPropagate), verify.orElse(defaultVerify));
    }


    /**
     * Unpush flows to kilda ... essentially the opposite of push.
     *
     * @param externalFlows a list of flows to unpush without propagation to Floodlight
     * @return list of flow
     */
    @ApiOperation(value = "Unpush flows without expectation of modifying switches. It can push to switch and validate.",
            response = BatchResults.class)
    @RequestMapping(path = "/unpush/flows",
            method = RequestMethod.PUT,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public BatchResults unpushFlows(
            @RequestBody List<FlowInfoData> externalFlows,
            @ApiParam(value = "default: false. If true, this will propagate rules to the switches.",
                    required = false)
            @RequestParam(value = "propagate", required = false) Optional<Boolean> propagate,
            @ApiParam(value = "default: false. If true, will wait until poll timeout for validation.",
                    required = false)
            @RequestParam(value = "verify", required = false) Optional<Boolean> verify) {
        Boolean defaultPropagate = false;
        Boolean defaultVerify = false;
        return flowService.unpushFlows(externalFlows, propagate.orElse(defaultPropagate), verify.orElse(defaultVerify));
    }


    /**
     * Initiates flow rerouting if any shorter paths are available.
     *
     * @param flowId id of flow to be rerouted.
     * @return flow payload with updated path.
     */
    @ApiOperation(value = "Reroute flow", response = FlowReroutePayload.class)
    @RequestMapping(path = "/flows/{flow_id}/reroute",
            method = RequestMethod.PATCH)
    @ResponseStatus(HttpStatus.OK)
    public FlowReroutePayload rerouteFlow(@PathVariable("flow_id") String flowId) {
        return flowService.rerouteFlow(flowId);
    }

    /**
     * Initiates flow synchronization (reinstalling). In other words it means flow update with newly generated rules.
     *
     * @param flowId id of flow to be rerouted.
     * @return flow payload with updated path.
     */
    @ApiOperation(value = "Sync flow", response = FlowReroutePayload.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, response = FlowReroutePayload.class, message = "Operation is successful")})
    @RequestMapping(path = "/flows/{flow_id}/sync",
            method = RequestMethod.PATCH)
    @ResponseStatus(HttpStatus.OK)
    public FlowReroutePayload syncFlow(@PathVariable("flow_id") String flowId) {
        return flowService.syncFlow(flowId);
    }

    /**
     * Compares the Flow from the DB to what is on each switch.
     *
     * @param flowId id of flow to be rerouted.
     * @return flow payload with updated path.
     */
    @ApiOperation(value = "Validate flow, comparing the DB to each switch", response = FlowValidationDto.class,
            responseContainer = "List")
    @RequestMapping(path = "/flows/{flow_id}/validate",
            method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<List<FlowValidationDto>> validateFlow(@PathVariable("flow_id") String flowId) {

        logger.debug("Received Flow Validation request with flow {}", flowId);
        ResponseEntity<List<FlowValidationDto>> response;

        try {
            List<FlowValidationDto> result = flowService.validateFlow(flowId);
            if (result == null) {
                logger.info("VALIDATE FLOW: Flow Not Found: {}", flowId);
                response = ResponseEntity.notFound().build();
            } else {
                response = ResponseEntity.ok(result);
            }
        } catch (InvalidPathException e) {
            logger.error("VALIDATE FLOW: Flow has no path: {}", flowId);
            logger.error(e.getMessage());
            response = ResponseEntity.notFound().build();
        }
        return response;
    }

    /**
     * Verify flow integrity by sending "ping" package over flow path.
     */
    @ApiOperation(
            value = "Verify flow - using special network packet that is being routed in the same way as client traffic")
    @RequestMapping(path = "/flows/{flow_id}/ping", method = RequestMethod.PUT)
    @ResponseStatus(HttpStatus.OK)
    public PingOutput pingFlow(
            @RequestBody PingInput payload,
            @PathVariable("flow_id") String flowId) {
        return flowService.pingFlow(flowId, payload);
    }

    /**
     * Make sure any Flow caches are in sync with the DB. This is primarily a janitor primitive.
     *
     * @return a detailed response of the sync operation (added, deleted, modified, unchanged flows)
     */
    @ApiOperation(value = "Sync Flow Cache(s)", response = FlowCacheSyncResults.class)
    @RequestMapping(path = "/flows/cachesync",
            method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public FlowCacheSyncResults syncFlowCache() {
        return flowService.syncFlowCache(SynchronizeCacheAction.NONE);
    }

    /**
     * Invalidate (purge) the flow cache and initialize it with DB data.
     *
     * @return a response of the invalidate operation
     */
    @ApiOperation(value = "Invalidate (purge) Flow Cache(s)", response = FlowCacheSyncResults.class)
    @DeleteMapping(path = "/flows/cache")
    @ResponseStatus(HttpStatus.OK)
    public FlowCacheSyncResults invalidateFlowCache() {
        return flowService.syncFlowCache(SynchronizeCacheAction.INVALIDATE_CACHE);
    }

    /**
     * Refresh (synchronize) the flow cache with DB data.
     *
     * @return a detailed response of the refresh operation (added, deleted, modified, unchanged flows)
     */
    @ApiOperation(value = "Refresh Flow Cache(s)", response = FlowCacheSyncResults.class)
    @PatchMapping(path = "/flows/cache")
    @ResponseStatus(HttpStatus.OK)
    public FlowCacheSyncResults refreshFlowCache() {
        return flowService.syncFlowCache(SynchronizeCacheAction.SYNCHRONIZE_CACHE);
    }

}
