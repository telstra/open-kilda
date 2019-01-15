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

import static org.openkilda.messaging.error.ErrorType.PARAMETERS_INVALID;

import org.openkilda.messaging.command.switches.ConnectModeRequest;
import org.openkilda.messaging.command.switches.ConnectModeRequest.Mode;
import org.openkilda.messaging.command.switches.DeleteRulesAction;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria.DeleteRulesCriteriaBuilder;
import org.openkilda.messaging.command.switches.InstallRulesAction;
import org.openkilda.messaging.error.MessageError;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.meter.SwitchMeterEntries;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.info.switches.PortDescription;
import org.openkilda.messaging.info.switches.SwitchPortsDescription;
import org.openkilda.messaging.payload.switches.PortConfigurationPayload;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.dto.switches.DeleteMeterResult;
import org.openkilda.northbound.dto.switches.PortDto;
import org.openkilda.northbound.dto.switches.RulesSyncResult;
import org.openkilda.northbound.dto.switches.RulesValidationResult;
import org.openkilda.northbound.dto.switches.SwitchDto;
import org.openkilda.northbound.dto.switches.UnderMaintenanceDto;
import org.openkilda.northbound.service.SwitchService;
import org.openkilda.northbound.utils.ExtraAuthRequired;
import org.openkilda.northbound.utils.RequestCorrelationId;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for switches.
 */
@RestController
@RequestMapping(path = "/switches", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
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
public class SwitchController {

    private static final Logger LOGGER = LoggerFactory.getLogger(SwitchController.class);

    @Autowired
    private SwitchService switchService;

    /**
     * Get all available switches.
     *
     * @return list of switches.
     */
    @ApiOperation(value = "Get all available switches", response = SwitchDto.class, responseContainer = "List")
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<List<SwitchDto>> getSwitches() {
        return switchService.getSwitches();
    }

    /**
     * Get switch.
     *
     * @param switchId the switch
     * @return switch.
     */
    @ApiOperation(value = "Get switch", response = SwitchDto.class)
    @GetMapping(value = "/{switch-id}")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<SwitchDto> getSwitch(
            @PathVariable("switch-id") SwitchId switchId) {
        return switchService.getSwitch(switchId);
    }

    /**
     * Get switch rules.
     *
     * @param switchId the switch
     * @param cookie filter the response based on this cookie
     * @return list of the cookies of the rules that have been deleted
     */
    @ApiOperation(value = "Get switch rules from the switch", response = SwitchFlowEntries.class)
    @GetMapping(value = "/{switch-id}/rules")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<SwitchFlowEntries> getSwitchRules(
            @PathVariable("switch-id") SwitchId switchId,
            @ApiParam(value = "Results will be filtered based on matching the cookie.",
                    required = false)
            @RequestParam(value = "cookie", required = false) Optional<Long> cookie) {
        return switchService.getRules(switchId, cookie.orElse(0L));
    }


    /**
     * Delete switch rules.
     *
     * @param switchId switch id to delete rules from
     * @param deleteAction defines what to do about the default rules
     * @param cookie the cookie to use if deleting a rule (could be any rule)
     * @param inPort the in port to use if deleting a rule
     * @param inVlan the in vlan to use if deleting a rule
     * @param outPort the out port to use if deleting a rule
     * @return list of the cookies of the rules that have been deleted
     */
    @ApiOperation(value = "Delete switch rules. Requires special authorization",
            response = Long.class, responseContainer = "List")
    @ApiResponse(code = 200, response = Long.class, responseContainer = "List", message = "Operation is successful")
    @DeleteMapping(value = "/{switch-id}/rules")
    @ExtraAuthRequired
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<List<Long>> deleteSwitchRules(
            @PathVariable("switch-id") SwitchId switchId,
            @ApiParam(value = "default: IGNORE_DEFAULTS. Can be one of DeleteRulesAction: "
                    + "DROP_ALL,DROP_ALL_ADD_DEFAULTS,IGNORE_DEFAULTS,OVERWRITE_DEFAULTS,"
                    + "REMOVE_DROP,REMOVE_BROADCAST,REMOVE_UNICAST,REMOVE_DEFAULTS,REMOVE_ADD_DEFAULTS",
                    required = false)
            @RequestParam(value = "delete-action", required = false) Optional<DeleteRulesAction> deleteAction,
            @RequestParam(value = "cookie", required = false) Optional<Long> cookie,
            @RequestParam(value = "in-port", required = false) Optional<Integer> inPort,
            @RequestParam(value = "in-vlan", required = false) Optional<Integer> inVlan,
            @RequestParam(value = "priority", required = false) Optional<Integer> priority,
            @RequestParam(value = "out-port", required = false) Optional<Integer> outPort) {

        CompletableFuture<List<Long>> result;

        //TODO: "priority" can't be used as a standalone criterion - because currently it's ignored in OFFlowDelete.
        if (cookie.isPresent() || inPort.isPresent() || inVlan.isPresent() /*|| priority.isPresent()*/
                || outPort.isPresent()) {
            if (deleteAction.isPresent()) {
                throw new MessageException(RequestCorrelationId.getId(), System.currentTimeMillis(),
                        PARAMETERS_INVALID, "Criteria parameters and delete-action are both provided.",
                        "Either criteria parameters or delete-action should be provided.");

            }

            DeleteRulesCriteriaBuilder builder = DeleteRulesCriteria.builder();
            cookie.ifPresent(builder::cookie);
            inPort.ifPresent(builder::inPort);
            inVlan.ifPresent(builder::inVlan);
            priority.ifPresent(builder::priority);
            outPort.ifPresent(builder::outPort);

            result = switchService.deleteRules(switchId, builder.build());
        } else {
            DeleteRulesAction deleteRulesAction = deleteAction.orElse(DeleteRulesAction.IGNORE_DEFAULTS);

            result = switchService.deleteRules(switchId, deleteRulesAction);
        }
        return result;
    }

    /**
     * Install switch rules.
     *
     * @param switchId switch id to delete rules from
     * @param installAction defines what to do about the default rules
     * @return list of the cookies of the rules that have been installed
     */
    @ApiOperation(value = "Install switch rules. Requires special authorization",
            response = Long.class, responseContainer = "List")
    @ApiResponse(code = 200, response = Long.class, responseContainer = "List", message = "Operation is successful")
    @PutMapping(value = "/{switch-id}/rules")
    @ExtraAuthRequired
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<List<Long>> installSwitchRules(
            @PathVariable("switch-id") SwitchId switchId,
            @ApiParam(value = "default: INSTALL_DEFAULTS. Can be one of InstallRulesAction: "
                    + " INSTALL_DROP,INSTALL_BROADCAST,INSTALL_UNICAST,INSTALL_DEFAULTS",
                    required = false)
            @RequestParam(value = "install-action", required = false) Optional<InstallRulesAction> installAction) {
        return switchService.installRules(switchId, installAction.orElse(InstallRulesAction.INSTALL_DEFAULTS));
    }


    /**
     * Toggle the global behavior of Floodlight when the switch connects:
     *      - AUTO - this is the default. Installs all default rules when a switch connects
     *      - SAFE - add the default rules slowly .. monitoring traffic on existing rules
     *      - MANUAL - don't install any default rules. Call addRule for that.
     * NOTE: no action is taking with existing, connected switches. This operation will only affect
     *      future connections
     *
     * @param mode the connectMode to use. A Null value is a No-Op and can be used to return existing value.
     * @return the value of the toggle in Floodlight.
     */
    @ApiOperation(value = "Set the connect mode if mode is specified. If mode is null, this is effectively a get.",
            response = ConnectModeRequest.Mode.class)
    @PutMapping(value = "/toggle-connect-mode")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<Mode> toggleSwitchConnectMode(@RequestParam("mode") ConnectModeRequest.Mode mode) {
        return switchService.connectMode(mode);
    }

    /**
     * Validate the rules installed on the switch against the flows in Neo4J.
     *
     * @return the validation details.
     */
    @ApiOperation(value = "Validate the rules installed on the switch", response = RulesValidationResult.class)
    @GetMapping(path = "/{switch_id}/rules/validate")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<RulesValidationResult> validateRules(@PathVariable(name = "switch_id") SwitchId switchId) {
        return switchService.validateRules(switchId);
    }

    /**
     * Synchronize (install) missing flows that should be on the switch but exist only in neo4j.
     *
     * @return the synchronization result.
     */
    @ApiOperation(value = "Synchronize rules on the switch", response = RulesSyncResult.class)
    @GetMapping(path = "/{switch_id}/rules/synchronize")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<RulesSyncResult> syncRules(@PathVariable(name = "switch_id") SwitchId switchId) {
        return switchService.syncRules(switchId);
    }

    /**
     * Gets meters from the switch.
     * @param switchId switch dpid.
     * @return list of meters exists on the switch
     */
    @ApiOperation(value = "Dump all meter from the switch", response = SwitchMeterEntries.class)
    @GetMapping(path = "/{switch_id}/meters")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<SwitchMeterEntries> getMeters(@PathVariable(name = "switch_id") SwitchId switchId) {
        return switchService.getMeters(switchId);
    }

    /**
     * Remove the meter from specific switch.
     * @param switchId switch dpid.
     * @param meterId id of the meter to be deleted.
     * @return result of the operation wrapped into {@link DeleteMeterResult}. True means no errors is occurred.
     */
    @ApiOperation(value = "Delete meter from the switch", response = DeleteMeterResult.class)
    @DeleteMapping(path = "/{switch_id}/meter/{meter_id}")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<DeleteMeterResult> deleteMeter(@PathVariable(name = "switch_id") SwitchId switchId,
                                                            @PathVariable(name = "meter_id") long meterId) {
        return switchService.deleteMeter(switchId, meterId);
    }

    /**
     * Configure port.
     *
     * @param switchId the switch id
     * @param portNo the port no
     * @param portConfig the port configuration payload
     * @return the response entity
     */
    @ApiOperation(value = "Configure port on the switch", response = PortDto.class)
    @ApiResponse(code = 200, response = PortDto.class, message = "Operation is successful")
    @PutMapping(value = "/{switch_id}/port/{port_no}/config")
    public CompletableFuture<PortDto> configurePort(
            @PathVariable(name = "switch_id") SwitchId switchId,
            @PathVariable(name = "port_no") int portNo,
            @RequestBody PortConfigurationPayload portConfig) {
        LOGGER.info("Port Configuration '{}' request for port {} of switch {}", portConfig, portNo, switchId);
        return switchService.configurePort(switchId, portNo, portConfig);
    }

    /**
     * Get a description of the switch ports.
     *
     * @param switchId the switch id.
     * @return switch ports description.
     */
    @ApiOperation(value = "Get switch ports description from the switch", response = SwitchPortsDescription.class)
    @GetMapping(value = "/{switch-id}/ports")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<SwitchPortsDescription> getSwitchPortsDescription(
            @PathVariable("switch-id") SwitchId switchId) {
        return switchService.getSwitchPortsDescription(switchId);
    }

    /**
     * Get a description of the switch port.
     *
     * @param switchId the switch id.
     * @param port the port of the switch.
     * @return port description.
     */
    @ApiOperation(value = "Get port description from the switch", response = PortDescription.class)
    @GetMapping(value = "/{switch-id}/ports/{port}")
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<PortDescription> getPortDescription(
            @PathVariable("switch-id") SwitchId switchId,
            @PathVariable("port") int port) {
        return switchService.getPortDescription(switchId, port);
    }

    /**
     * Update "Under maintenance" flag for the switch.
     *
     * @param switchId the switch id.
     * @param underMaintenanceDto under maintenance flag.
     * @return updated switch.
     */
    @ApiOperation(value = "Update \"Under maintenance\" flag for the switch.", response = SwitchDto.class)
    @PatchMapping(path = "/{switch-id}/under-maintenance",
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public CompletableFuture<SwitchDto> updateLinkUnderMaintenance(@PathVariable("switch-id") SwitchId switchId,
            @RequestBody UnderMaintenanceDto underMaintenanceDto) {
        return switchService.updateSwitchUnderMaintenance(switchId, underMaintenanceDto);
    }
}
