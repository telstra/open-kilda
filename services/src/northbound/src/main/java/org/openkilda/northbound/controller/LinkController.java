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
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.northbound.dto.BatchResults;
import org.openkilda.northbound.dto.links.LinkDto;
import org.openkilda.northbound.dto.links.LinkPropsDto;
import org.openkilda.northbound.service.LinkService;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    public List<LinkDto> getLinks() {
        return linkService.getLinks();
    }

    /**
     * Get link properties from the static link properties table.
     *
     * @return list of link properties.
     */
    @ApiOperation(value = "Get all link properties (static), based on arguments.", response = LinkPropsDto.class,
            responseContainer = "List")
    @RequestMapping(path = "/link/props",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public List<LinkPropsDto> getLinkProps(@RequestParam(value = "src_switch", required = false) SwitchId srcSwitch,
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
    @RequestMapping(path = "/link/props",
            method = RequestMethod.PUT,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public BatchResults putLinkProps(
            @RequestBody List<LinkPropsDto> keysAndProps) {
        return linkService.setLinkProps(keysAndProps);
    }

    /**
     * Delete link properties from the static link properties table.
     *
     * @param keysAndProps if null, get all link props. Otherwise, the link props that much the primary keys.
     * @return result of the processing.
     */
    @ApiOperation(value = "Delete link properties (static), based on arguments.", response = BatchResults.class)
    @RequestMapping(path = "/link/props",
            method = RequestMethod.DELETE,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public BatchResults delLinkProps(
            @RequestBody List<LinkPropsDto> keysAndProps) {
        return linkService.delLinkProps(keysAndProps);
    }
}
