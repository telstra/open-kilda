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
import org.openkilda.northbound.dto.v2.switches.SwitchFlowsPerPortResponse;
import org.openkilda.northbound.dto.v2.switches.SwitchPatchDto;
import org.openkilda.northbound.dto.v2.switches.SwitchPropertiesDump;
import org.openkilda.northbound.dto.v2.switches.SwitchValidationResultV2;
import org.openkilda.northbound.service.SwitchService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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
    @Operation(summary = "Get port history of the switch")
    @GetMapping(value = "/{switch_id}/ports/{port}/history")
    @ResponseStatus(HttpStatus.OK)
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
    @Operation(description = "Get port properties")
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
     */
    @Operation(summary = "Update port properties")
    @PutMapping(value = "/{switch_id}/ports/{port}/properties")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<PortPropertiesResponse> updatePortProperties(@PathVariable("switch_id") SwitchId switchId,
                                                                          @PathVariable("port") int port,
                                                                          @RequestBody PortPropertiesDto dto) {
        return switchService.updatePortProperties(switchId, port, dto);
    }

    /**
     * Gets switch connected devices.
     */
    @Operation(summary = "Gets switch connected devices")
    @GetMapping(path = "/{switch_id}/devices")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<SwitchConnectedDevicesResponse> getConnectedDevices(
            @PathVariable("switch_id") SwitchId switchId,
            @Parameter(description = "Device will be included in response if it's `time_last_seen` >= `since`. "
                    + "Example of `since` value: `2019-09-30T16:14:12.538Z`")
            @RequestParam(value = "since", required = false) Optional<String> since) {
        Instant sinceInstant;

        if (since.isEmpty() || StringUtils.isEmpty(since.get())) {
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
    @Operation(summary = "Update switch")
    @PatchMapping(value = "/{switch_id}")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<SwitchDtoV2> patchSwitch(@PathVariable("switch_id") SwitchId switchId,
                                                      @Parameter(description = "To remove the pop value, "
                                                              + "need to pass an empty string.")
                                                      @RequestBody SwitchPatchDto dto) {
        return switchService.patchSwitch(switchId, dto);
    }

    /**
     * Return active switch connections to the speakers.
     */
    @Operation(summary = "Get active switch connections")
    @GetMapping(path = "/{switch_id}/connections")
    public CompletableFuture<SwitchConnectionsResponse> getConnections(@PathVariable("switch_id") SwitchId switchId) {
        return switchService.getSwitchConnections(switchId);
    }

    /**
     * Get switch properties.
     *
     * @return switch ports description.
     */
    @Operation(summary = "Get switch properties for all switches")
    @GetMapping(value = "/properties")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<SwitchPropertiesDump> getSwitchProperties() {
        return switchService.dumpSwitchProperties();
    }

    /**
     * Create LAG logical port.
     *
     * @param switchId the switch
     */
    @Operation(summary = "Create LAG logical port")
    @PostMapping(value = "/{switch_id}/lags")
    @ResponseStatus(HttpStatus.OK)
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
    @Operation(summary = "Read all LAG logical ports on specific switch")
    @GetMapping(value = "/{switch_id}/lags")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<List<LagPortResponse>> getLagPorts(@PathVariable("switch_id") SwitchId switchId) {
        return switchService.getLagPorts(switchId);
    }

    /**
     * Update LAG logical port.
     */
    @Operation(summary = "Update LAG logical port")
    @PutMapping(value = "/{switch_id}/lags/{logical_port_number}")
    @ResponseStatus(HttpStatus.OK)
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
    @Operation(summary = "Delete LAG logical port")
    @DeleteMapping(value = "/{switch_id}/lags/{logical_port_number}")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<LagPortResponse> deleteLagPort(
            @PathVariable("switch_id") SwitchId switchId,
            @PathVariable("logical_port_number") int logicalPortNumber) {
        return switchService.deleteLagPort(switchId, logicalPortNumber);
    }

    /**
     * Validate the rules, groups, lags and the meters installed on the switch against the flows in the database.
     *
     * @param includeString validated fields to include in response
     * @param excludeString drop flow id, flow path and y flow id
     * @return the validation details.
     */
    @Operation(summary = "Validate rules, lags, groups and meters installed on the switch")
    @GetMapping(path = "/{switch_id}/validate")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<SwitchValidationResultV2> validateSwitch(
            @PathVariable(name = "switch_id") SwitchId switchId,
            @RequestParam(name = "include", required = false) String includeString,
            @RequestParam(name = "exclude", required = false) String excludeString) {
        return switchService.validateSwitch(switchId, includeString, excludeString);
    }

    /**
     * Retrieves a map of all flows for each port for the given switch.
     * When a port ID is provided, returns flows only for the given port.
     * Ports that don't have any associated flow are skipped, i.e. if no flows are going through this switch,
     * returns an empty output.
     * @param switchId a specific switch
     * @param portIds optional. Filters the output to display this port only
     * @return mapping of port->[flows]
     */
    @Operation(summary = "Get all flows for each port for the given switch")
    @GetMapping("/{switch_id}/flows-by-port")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<SwitchFlowsPerPortResponse> getSwitchFlows(
            @PathVariable("switch_id") SwitchId switchId,
            @RequestParam(value = "ports", required = false) List<Integer> portIds) {
        return switchService.getFlowsPerPortForSwitch(switchId, portIds);
    }
}
