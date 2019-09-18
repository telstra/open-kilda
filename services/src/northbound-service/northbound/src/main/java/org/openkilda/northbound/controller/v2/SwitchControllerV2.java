/* Copyright 2019 Telstra Open Source
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

package org.openkilda.northbound.controller.v2;

import org.openkilda.model.SwitchId;
import org.openkilda.northbound.controller.BaseController;
import org.openkilda.northbound.dto.v2.switches.PortHistoryResponse;
import org.openkilda.northbound.dto.v2.switches.PortPropertiesDto;
import org.openkilda.northbound.dto.v2.switches.PortPropertiesResponse;
import org.openkilda.northbound.service.SwitchService;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/v2/switches")
public class SwitchControllerV2 extends BaseController {

    @Autowired
    private SwitchService switchService;

    /**
     * Get a history of the specified switch's port.
     *
     * @param switchId the switch id.
     * @param port the port of the switch.
     * @return port history.
     */
    @ApiOperation(value = "Get port history of the switch", response = PortHistoryResponse.class,
            responseContainer = "List")
    @GetMapping(value = "/{switch_id}/ports/{port}/history")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<List<PortHistoryResponse>> getPortHistory(
            @PathVariable("switch_id") SwitchId switchId,
            @PathVariable("port") int port,
            @ApiParam(value = "default: the day before timeTo.")
            @RequestParam(value = "timeFrom", required = false) @DateTimeFormat(iso = ISO.DATE_TIME)
                    Optional<Date> optionalFrom,
            @ApiParam(value = "default: now.")
            @RequestParam(value = "timeTo", required = false) @DateTimeFormat(iso = ISO.DATE_TIME)
                    Optional<Date> optionalTo) {
        Instant timeTo = optionalTo.map(Date::toInstant).orElseGet(Instant::now);
        Instant timeFrom = optionalFrom.map(Date::toInstant).orElseGet(() ->
                timeTo.minus(1, ChronoUnit.DAYS));

        return switchService.getPortHistory(switchId, port, timeFrom, timeTo);
    }

    /**
     * Get port properties.
     *
     * @param switchId the switch id.
     * @param port the port of the switch.
     * @return port properties.
     */
    @ApiOperation(value = "Get port properties", response = PortPropertiesResponse.class)
    @GetMapping(value = "/{switch_id}/ports/{port}/properties")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<PortPropertiesResponse> getPortProperties(@PathVariable("switch_id") SwitchId switchId,
                                                                       @PathVariable("port") int port) {
        return switchService.getPortProperties(switchId, port);
    }

    /**
     * Update port properties.
     *
     * @param switchId the switch id.
     * @param port the port of the switch.
     * @return port properties.
     */
    @ApiOperation(value = "Update port properties", response = PortPropertiesResponse.class)
    @PutMapping(value = "/{switch_id}/ports/{port}/properties")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<PortPropertiesResponse> updatePortProperties(@PathVariable("switch_id") SwitchId switchId,
                                                                          @PathVariable("port") int port,
                                                                          @RequestBody PortPropertiesDto dto) {
        return switchService.updatePortProperties(switchId, port, dto);
    }
}
