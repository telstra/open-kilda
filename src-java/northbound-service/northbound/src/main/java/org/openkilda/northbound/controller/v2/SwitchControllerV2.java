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

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.controller.BaseController;
import org.openkilda.northbound.dto.v2.switches.LagPortRequest;
import org.openkilda.northbound.dto.v2.switches.LagPortResponse;
import org.openkilda.northbound.dto.v2.switches.PortHistoryResponse;
import org.openkilda.northbound.dto.v2.switches.PortPropertiesDto;
import org.openkilda.northbound.dto.v2.switches.PortPropertiesResponse;
import org.openkilda.northbound.dto.v2.switches.SwitchConnectedDevicesResponse;
import org.openkilda.northbound.dto.v2.switches.SwitchConnectionsResponse;
import org.openkilda.northbound.dto.v2.switches.SwitchDtoV2;
import org.openkilda.northbound.dto.v2.switches.SwitchPatchDto;
import org.openkilda.northbound.dto.v2.switches.SwitchPropertiesDump;
import org.openkilda.northbound.service.SwitchService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeParseException;
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
    @GetMapping(value = "/{switch_id}/ports/{port}/history")
    @Operation(summary = "Get port history of the switch")
    @ApiResponse(responseCode = "200",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = PortHistoryResponse.class))))
    public CompletableFuture<List<PortHistoryResponse>> getPortHistory(
            @PathVariable("switch_id") SwitchId switchId,
            @PathVariable("port") int port,
            @Parameter(description = "default: the day before timeTo.")
            @RequestParam(value = "timeFrom", required = false) @DateTimeFormat(iso = ISO.DATE_TIME)
            Optional<Date> optionalFrom,
            @Parameter(description = "default: now.")
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
    @GetMapping(value = "/{switch_id}/ports/{port}/properties")
    @Operation(summary = "Get port properties")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = PortPropertiesResponse.class)))
    public CompletableFuture<PortPropertiesResponse> getPortProperties(@PathVariable("switch_id") SwitchId switchId,
                                                                       @PathVariable("port") int port) {
        return switchService.getPortProperties(switchId, port);
    }

    /**
     * Update port properties.
     *
     * @param switchId the switch id.
     * @param port the port of the switch.
     */
    @PutMapping(value = "/{switch_id}/ports/{port}/properties")
    @Operation(summary = "Update port properties")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = PortPropertiesResponse.class)))
    public CompletableFuture<PortPropertiesResponse> updatePortProperties(@PathVariable("switch_id") SwitchId switchId,
                                                                          @PathVariable("port") int port,
                                                                          @RequestBody PortPropertiesDto dto) {
        return switchService.updatePortProperties(switchId, port, dto);
    }

    /**
     * Gets switch connected devices.
     */
    @GetMapping(path = "/{switch_id}/devices")
    @Operation(summary = "Gets switch connected devices")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = SwitchConnectedDevicesResponse.class)))
    public CompletableFuture<SwitchConnectedDevicesResponse> getConnectedDevices(
            @PathVariable("switch_id") SwitchId switchId,
            @Parameter(description = "Device will be included in response if it's `time_last_seen` >= `since`. "
                    + "Example of `since` value: `2019-09-30T16:14:12.538Z`",
                    required = false)
            @RequestParam(value = "since", required = false) Optional<String> since) {
        Instant sinceInstant;

        if (!since.isPresent() || StringUtils.isEmpty(since.get())) {
            sinceInstant = Instant.MIN;
        } else {
            try {
                sinceInstant = Instant.parse(since.get());
            } catch (DateTimeParseException e) {
                String message = String.format("Invalid 'since' value '%s'. Correct example of 'since' value is "
                        + "'2019-09-30T16:14:12.538Z'", since.get());
                throw new MessageException(ErrorType.DATA_INVALID, message, "Invalid 'since' value");
            }
        }
        return switchService.getSwitchConnectedDevices(switchId, sinceInstant);
    }

    /**
     * Update switch.
     *
     * @param switchId the switch
     * @return switch.
     */
    @PatchMapping(value = "/{switch_id}")
    @Operation(summary = "Update switch")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = SwitchDtoV2.class)))
    public CompletableFuture<SwitchDtoV2> patchSwitch(@PathVariable("switch_id") SwitchId switchId,
                                                      @Parameter(description = "To remove the pop value, "
                                                              + "need to pass an empty string.")
                                                      @RequestBody SwitchPatchDto dto) {
        return switchService.patchSwitch(switchId, dto);
    }

    /**
     * Return active switch connections to the speakers.
     */
    @GetMapping(path = "/{switch_id}/connections")
    @Operation(summary = "Get active switch connections")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = SwitchConnectionsResponse.class)))
    public CompletableFuture<SwitchConnectionsResponse> getConnections(@PathVariable("switch_id") SwitchId switchId) {
        return switchService.getSwitchConnections(switchId);
    }

    /**
     * Get switch properties.
     *
     * @return switch ports description.
     */
    @GetMapping(value = "/properties")
    @Operation(summary = "Get switch properties for all switches")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = SwitchPropertiesDump.class)))
    public CompletableFuture<SwitchPropertiesDump> getSwitchProperties() {
        return switchService.dumpSwitchProperties();
    }

    /**
     * Create LAG logical port.
     *
     * @param switchId the switch
     */
    @PostMapping(value = "/{switch_id}/lags")
    @Operation(summary = "Create LAG logical port")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = LagPortResponse.class)))
    public CompletableFuture<LagPortResponse> createLagPort(
            @PathVariable("switch_id") SwitchId switchId,
            @Parameter(description = "Physical ports which will be grouped")
            @RequestBody LagPortRequest lagPortRequest) {
        return switchService.createLag(switchId, lagPortRequest);
    }

    /**
     * Get LAG logical ports.
     *
     * @param switchId the switch
     */
    @GetMapping(value = "/{switch_id}/lags")
    @Operation(summary = "Read all LAG logical ports on specific switch")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = LagPortResponse.class)))
    public CompletableFuture<List<LagPortResponse>> getLagPorts(@PathVariable("switch_id") SwitchId switchId) {
        return switchService.getLagPorts(switchId);
    }

    /**
     * Update LAG logical port.
     */
    @PutMapping(value = "/{switch_id}/lags/{logical_port_number}")
    @Operation(summary = "Update LAG logical port")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = LagPortResponse.class)))
    public CompletableFuture<LagPortResponse> updateLagPort(
            @PathVariable("switch_id") SwitchId switchId,
            @PathVariable("logical_port_number") int logicalPortNumber,
            @RequestBody LagPortRequest payload) {
        return switchService.updateLagPort(switchId, logicalPortNumber, payload);
    }

    /**
     * Delete LAG logical port.
     *
     * @param switchId the switch
     * @param logicalPortNumber the switch
     */
    @DeleteMapping(value = "/{switch_id}/lags/{logical_port_number}")
    @Operation(summary = "Delete LAG logical port")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = LagPortResponse.class)))
    public CompletableFuture<LagPortResponse> deleteLagPort(
            @PathVariable("switch_id") SwitchId switchId,
            @PathVariable("logical_port_number") int logicalPortNumber) {
        return switchService.deleteLagPort(switchId, logicalPortNumber);
    }
}
