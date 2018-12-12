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

import org.openkilda.messaging.error.MessageError;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.dto.BatchResults;
import org.openkilda.northbound.dto.links.LinkDto;
import org.openkilda.northbound.dto.links.LinkParametersDto;
import org.openkilda.northbound.dto.links.LinkPropsDto;
import org.openkilda.northbound.dto.links.LinkUnderMaintenanceDto;
import org.openkilda.northbound.dto.switches.DeleteLinkResult;
import org.openkilda.northbound.service.LinkService;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for links.
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
public class LinkController {

    @Autowired
    private LinkService linkService;

    /**
     * Get all available links.
     *
     * @return list of links.
     */
    @ApiOperation(value = "Get all links", response = LinkDto.class, responseContainer = "List")
    @GetMapping(path = "/links")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<List<LinkDto>> getLinks() {
        return linkService.getLinks();
    }

    /**
     * Delete link.
     *
     * @param linkParameters properties to find a link for delete.
     * @return result of the operation wrapped into {@link DeleteLinkResult}. True means no errors is occurred.
     */
    @ApiOperation(value = "Delete link.", response = DeleteLinkResult.class)
    @DeleteMapping(path = "/links")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<DeleteLinkResult> deleteLink(@RequestBody LinkParametersDto linkParameters) {
        return linkService.deleteLink(linkParameters);
    }

    /**
     * Get link properties from the static link properties table.
     *
     * @return list of link properties.
     */
    @ApiOperation(value = "Get all link properties (static), based on arguments.", response = LinkPropsDto.class,
            responseContainer = "List")
    @GetMapping(path = "/link/props")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<List<LinkPropsDto>> getLinkProps(
            @RequestParam(value = "src_switch", required = false) SwitchId srcSwitch,
            @RequestParam(value = "src_port", required = false) Integer srcPort,
            @RequestParam(value = "dst_switch", required = false) SwitchId dstSwitch,
            @RequestParam(value = "dst_port", required = false) Integer dstPort) {
        return linkService.getLinkProps(srcSwitch, srcPort, dstSwitch, dstPort);
    }

    /**
     * Create/Update link properties in the static link properties table.
     *
     * @param keysAndProps if null, get all link props. Otherwise, the link props that much the primary keys.
     * @return result of the processing.
     */
    @ApiOperation(value = "Create/Update link properties", response = BatchResults.class)
    @PutMapping(path = "/link/props")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<BatchResults> putLinkProps(@RequestBody List<LinkPropsDto> keysAndProps) {
        return linkService.setLinkProps(keysAndProps);
    }

    /**
     * Delete link properties from the static link properties table.
     *
     * @param keysAndProps if null, get all link props. Otherwise, the link props that much the primary keys.
     * @return result of the processing.
     */
    @ApiOperation(value = "Delete link properties (static), based on arguments.", response = BatchResults.class)
    @DeleteMapping(path = "/link/props")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<BatchResults> delLinkProps(@RequestBody List<LinkPropsDto> keysAndProps) {
        return linkService.delLinkProps(keysAndProps);
    }

    /**
     * Get all flows for a particular link.
     *
     * @return list of flows for a particular link.
     */
    @ApiOperation(value = "Get all flows for a particular link, based on arguments.", response = FlowPayload.class,
            responseContainer = "List")
    @GetMapping(path = "/links/flows",
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<List<FlowPayload>> getFlowsForLink(@RequestParam(value = "src_switch") SwitchId srcSwitch,
                                                                @RequestParam(value = "src_port") Integer srcPort,
                                                                @RequestParam(value = "dst_switch") SwitchId dstSwitch,
                                                                @RequestParam(value = "dst_port") Integer dstPort) {
        return linkService.getFlowsForLink(srcSwitch, srcPort, dstSwitch, dstPort);
    }

    /**
     * Reroute all flows for a particular link.
     *
     * @return list of flow ids which was sent to reroute.
     */
    @ApiOperation(value = "Reroute all flows for a particular link, based on arguments.", response = String.class,
            responseContainer = "List")
    @PatchMapping(path = "/links/flows/reroute",
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<List<String>> rerouteFlowsForLink(@RequestParam(value = "src_switch") SwitchId srcSwitch,
                                                                @RequestParam(value = "src_port") Integer srcPort,
                                                                @RequestParam(value = "dst_switch") SwitchId dstSwitch,
                                                                @RequestParam(value = "dst_port") Integer dstPort) {
        return linkService.rerouteFlowsForLink(srcSwitch, srcPort, dstSwitch, dstPort);
    }

    /**
     * Update "Under maintenance" flag in the link.
     *
     * @return updated link.
     */
    @ApiOperation(value = "Update \"Under maintenance\" flag for the link.", response = LinkDto.class,
            responseContainer = "List")
    @PatchMapping(path = "/links/under-maintenance",
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<List<LinkDto>> updateIslUnderMaintenance(@RequestBody LinkUnderMaintenanceDto link) {
        return linkService.updateLinkUnderMaintenance(link);
    }
}
