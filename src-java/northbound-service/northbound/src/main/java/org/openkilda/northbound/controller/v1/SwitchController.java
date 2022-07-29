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

package org.openkilda.northbound.controller.v1;

import static org.openkilda.messaging.error.ErrorType.PARAMETERS_INVALID;

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.switches.DeleteRulesAction;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria.DeleteRulesCriteriaBuilder;
import org.openkilda.messaging.command.switches.InstallRulesAction;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.meter.SwitchMeterEntries;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.info.switches.PortDescription;
import org.openkilda.messaging.info.switches.SwitchPortsDescription;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.switches.PortConfigurationPayload;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.controller.BaseController;
import org.openkilda.northbound.dto.v1.switches.DeleteMeterResult;
import org.openkilda.northbound.dto.v1.switches.DeleteSwitchResult;
import org.openkilda.northbound.dto.v1.switches.PortDto;
import org.openkilda.northbound.dto.v1.switches.RulesSyncResult;
import org.openkilda.northbound.dto.v1.switches.RulesValidationResult;
import org.openkilda.northbound.dto.v1.switches.SwitchDto;
import org.openkilda.northbound.dto.v1.switches.SwitchPropertiesDto;
import org.openkilda.northbound.dto.v1.switches.SwitchSyncRequest;
import org.openkilda.northbound.dto.v1.switches.SwitchSyncResult;
import org.openkilda.northbound.dto.v1.switches.SwitchValidationResult;
import org.openkilda.northbound.dto.v1.switches.UnderMaintenanceDto;
import org.openkilda.northbound.service.SwitchService;
import org.openkilda.northbound.utils.ExtraAuthRequired;
import org.openkilda.northbound.utils.RequestCorrelationId;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for switches.
 */
@RestController
@RequestMapping("/v1/switches")
public class SwitchController extends BaseController {
    @Autowired
    private SwitchService switchService;

    /**
     * Get all available switches.
     *
     * @return list of switches.
     */
    @GetMapping
    @Operation(summary = "Get all available switches")
    @ApiResponse(responseCode = "200",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = SwitchDto.class))))
    public CompletableFuture<List<SwitchDto>> getSwitches() {
        return switchService.getSwitches();
    }

    /**
     * Get switch.
     *
     * @param switchId the switch
     * @return switch.
     */
    @GetMapping(value = "/{switch-id}")
    @Operation(summary = "Get switch")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = SwitchDto.class)))
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
    @GetMapping(value = "/{switch-id}/rules")
    @Operation(summary = "Get switch rules from the switch")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = SwitchFlowEntries.class)))
    public CompletableFuture<SwitchFlowEntries> getSwitchRules(
            @PathVariable("switch-id") SwitchId switchId,
            @Parameter(description = "Results will be filtered based on matching the cookie.",
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
    @DeleteMapping(value = "/{switch-id}/rules")
    @ExtraAuthRequired
    @Operation(summary = "Delete switch rules. Requires special authorization")
    @ApiResponse(responseCode = "200",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = Long.class))))
    @Parameter(in = ParameterIn.HEADER, name = Utils.EXTRA_AUTH,
            content = @Content(schema = @Schema(implementation = String.class)))
    public CompletableFuture<List<Long>> deleteSwitchRules(
            @PathVariable("switch-id") SwitchId switchId,
            @Parameter(description = "default: IGNORE_DEFAULTS. Can be one of DeleteRulesAction: "
                    + "DROP_ALL,DROP_ALL_ADD_DEFAULTS,IGNORE_DEFAULTS,OVERWRITE_DEFAULTS,"
                    + "REMOVE_DROP,REMOVE_BROADCAST,REMOVE_UNICAST,REMOVE_VERIFICATION_LOOP,REMOVE_BFD_CATCH,"
                    + "REMOVE_ROUND_TRIP_LATENCY,REMOVE_UNICAST_VXLAN,REMOVE_MULTITABLE_PRE_INGRESS_PASS_THROUGH,"
                    + "REMOVE_MULTITABLE_INGRESS_DROP,REMOVE_MULTITABLE_POST_INGRESS_DROP,"
                    + "REMOVE_MULTITABLE_EGRESS_PASS_THROUGH,REMOVE_MULTITABLE_TRANSIT_DROP,"
                    + "REMOVE_LLDP_INPUT_PRE_DROP, REMOVE_LLDP_INGRESS,REMOVE_LLDP_POST_INGRESS,"
                    + "REMOVE_LLDP_POST_INGRESS_VXLAN,REMOVE_LLDP_POST_INGRESS_ONE_SWITCH,REMOVE_LLDP_TRANSIT,"
                    + "REMOVE_ARP_INPUT_PRE_DROP,REMOVE_ARP_INGRESS,REMOVE_ARP_POST_INGRESS,"
                    + "REMOVE_ARP_POST_INGRESS_VXLAN,REMOVE_ARP_POST_INGRESS_ONE_SWITCH,REMOVE_ARP_TRANSIT,"
                    + "REMOVE_SERVER_42_FLOW_RTT_TURNING,REMOVE_SERVER_42_FLOW_RTT_OUTPUT_VLAN,"
                    + "REMOVE_SERVER_42_FLOW_RTT_OUTPUT_VXLAN,"
                    + "REMOVE_SERVER_42_ISL_RTT_TURNING,REMOVE_SERVER_42_ISL_RTT_OUTPUT,"
                    + "REMOVE_DEFAULTS,REMOVE_ADD_DEFAULTS,REMOVE_SERVER_42_FLOW_RTT_VXLAN_TURNING",
                    required = false)
            @RequestParam(value = "delete-action", required = false) Optional<DeleteRulesAction> deleteAction,
            @RequestParam(value = "cookie", required = false) Optional<Long> cookie,
            @RequestParam(value = "in-port", required = false) Optional<Integer> inPort,
            @RequestParam(value = "in-vlan", required = false) Optional<Integer> inVlan,
            @RequestParam(value = "encapsulation-type", required = false) Optional<String> encapsulationType,
            @RequestParam(value = "priority", required = false) Optional<Integer> priority,
            @RequestParam(value = "out-port", required = false) Optional<Integer> outPort) {

        CompletableFuture<List<Long>> result;

        //TODO: "priority" can't be used as a standalone criterion - because currently it's ignored in OFFlowDelete.
        if (cookie.isPresent() || inPort.isPresent() || inVlan.isPresent() /*|| priority.isPresent()*/
                || outPort.isPresent() || encapsulationType.isPresent()) {
            if (deleteAction.isPresent()) {
                throw new MessageException(RequestCorrelationId.getId(), System.currentTimeMillis(),
                        PARAMETERS_INVALID, "Criteria parameters and delete-action are both provided.",
                        "Either criteria parameters or delete-action should be provided.");

            }

            if (inVlan.isPresent() != encapsulationType.isPresent()) {
                throw new MessageException(RequestCorrelationId.getId(), System.currentTimeMillis(),
                        PARAMETERS_INVALID, "Encapsulation criteria is not full.",
                        "In VLAN and encapsulation type should be provided.");
            }

            DeleteRulesCriteriaBuilder builder = DeleteRulesCriteria.builder();
            cookie.ifPresent(builder::cookie);
            inPort.ifPresent(builder::inPort);
            inVlan.ifPresent(builder::encapsulationId);
            priority.ifPresent(builder::priority);
            outPort.ifPresent(builder::outPort);

            if (encapsulationType.isPresent()) {
                try {
                    builder.encapsulationType(FlowEncapsulationType.valueOf(encapsulationType.get().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    throw new MessageException(RequestCorrelationId.getId(), System.currentTimeMillis(),
                            PARAMETERS_INVALID, "Encapsulation type is not right.",
                            "The correct encapsulation type should be provided.");
                }
            }

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
    @PutMapping(value = "/{switch-id}/rules")
    @ExtraAuthRequired
    @Operation(summary = "Install switch rules. Requires special authorization")
    @ApiResponse(responseCode = "200", description = "Operation is successful",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = Long.class))))
    @Parameter(in = ParameterIn.HEADER, name = Utils.EXTRA_AUTH,
            content = @Content(schema = @Schema(implementation = String.class)))
    public CompletableFuture<List<Long>> installSwitchRules(
            @PathVariable("switch-id") SwitchId switchId,
            @Parameter(description = "default: INSTALL_DEFAULTS. Can be one of InstallRulesAction: "
                    + "INSTALL_DROP,INSTALL_BROADCAST,INSTALL_UNICAST,INSTALL_DROP_VERIFICATION_LOOP,INSTALL_BFD_CATCH,"
                    + "INSTALL_ROUND_TRIP_LATENCY,INSTALL_UNICAST_VXLAN,INSTALL_MULTITABLE_PRE_INGRESS_PASS_THROUGH,"
                    + "INSTALL_MULTITABLE_INGRESS_DROP,INSTALL_MULTITABLE_POST_INGRESS_DROP,"
                    + "INSTALL_MULTITABLE_EGRESS_PASS_THROUGH,INSTALL_MULTITABLE_TRANSIT_DROP,"
                    + "INSTALL_LLDP_INPUT_PRE_DROP,INSTALL_LLDP_INGRESS,INSTALL_LLDP_POST_INGRESS,"
                    + "INSTALL_LLDP_POST_INGRESS_VXLAN,INSTALL_LLDP_POST_INGRESS_ONE_SWITCH,INSTALL_LLDP_TRANSIT,"
                    + "INSTALL_ARP_INPUT_PRE_DROP,INSTALL_ARP_INGRESS,INSTALL_ARP_POST_INGRESS,"
                    + "INSTALL_ARP_POST_INGRESS_VXLAN,INSTALL_ARP_POST_INGRESS_ONE_SWITCH,INSTALL_ARP_TRANSIT,"
                    + "INSTALL_SERVER_42_FLOW_RTT_TURNING,INSTALL_SERVER_42_FLOW_RTT_OUTPUT_VLAN,"
                    + "INSTALL_SERVER_42_FLOW_RTT_OUTPUT_VXLAN,"
                    + "INSTALL_SERVER_42_ISL_RTT_TURNING,INSTALL_SERVER_42_ISL_RTT_OUTPUT,"
                    + "INSTALL_SERVER_42_FLOW_RTT_VXLAN_TURNING,INSTALL_DEFAULTS")
            @RequestParam(value = "install-action", required = false) Optional<InstallRulesAction> installAction) {
        return switchService.installRules(switchId, installAction.orElse(InstallRulesAction.INSTALL_DEFAULTS));
    }


    /**
     * Not supported anymore.
     */
    @Deprecated
    @PutMapping(value = "/toggle-connect-mode")
    @ResponseStatus(HttpStatus.GONE)
    @Hidden
    public String toggleSwitchConnectMode() {
        return "Connect mode is not supported anymore.";
    }

    /**
     * Validate the rules installed on the switch against the flows in the database.
     *
     * @return the validation details.
     */
    @GetMapping(path = "/{switch_id}/rules/validate")
    @Operation(summary = "Validate the rules installed on the switch")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = RulesValidationResult.class)))
    public CompletableFuture<RulesValidationResult> validateRules(@PathVariable(name = "switch_id") SwitchId switchId) {
        return switchService.validateRules(switchId);
    }

    /**
     * Validate the rules and the meters installed on the switch against the flows in the database.
     *
     * @return the validation details.
     */
    @GetMapping(path = "/{switch_id}/validate")
    @Operation(summary = "Validate the rules and the meters installed on the switch")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = SwitchValidationResult.class)))
    public CompletableFuture<SwitchValidationResult> validateSwitch(
            @PathVariable(name = "switch_id") SwitchId switchId) {
        return switchService.validateSwitch(switchId);
    }

    /**
     * Synchronize (install) missing flows that should be on the switch but exist only in the database.
     *
     * @return the synchronization result.
     */
    @GetMapping(path = "/{switch_id}/rules/synchronize")
    @Operation(summary = "Synchronize rules on the switch")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = RulesSyncResult.class)))
    public CompletableFuture<RulesSyncResult> syncRules(@PathVariable(name = "switch_id") SwitchId switchId) {
        return switchService.syncRules(switchId);
    }

    /**
     * Synchronize (install) missing flows that should be on the switch but exist only in the database.
     * Optionally removes excess rules from the switch.
     *
     * @return the synchronization result.
     */
    @PatchMapping(path = "/{switch_id}/synchronize")
    @Operation(summary = "Synchronize rules on the switch")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = SwitchSyncResult.class)))
    public CompletableFuture<SwitchSyncResult> syncSwitch(@PathVariable(name = "switch_id") SwitchId switchId,
                                                          @RequestBody SwitchSyncRequest request) {
        return switchService.syncSwitch(switchId, request.isRemoveExcess());
    }

    /**
     * Gets meters from the switch.
     *
     * @param switchId switch dpid.
     * @return list of meters exists on the switch
     */
    @GetMapping(path = "/{switch_id}/meters")
    @Operation(summary = "Dump all meter from the switch")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = SwitchMeterEntries.class)))
    public CompletableFuture<SwitchMeterEntries> getMeters(@PathVariable(name = "switch_id") SwitchId switchId) {
        return switchService.getMeters(switchId);
    }

    /**
     * Remove the meter from specific switch.
     *
     * @param switchId switch dpid.
     * @param meterId id of the meter to be deleted.
     * @return result of the operation wrapped into {@link DeleteMeterResult}. True means no errors is occurred.
     */
    @DeleteMapping(path = "/{switch_id}/meter/{meter_id}")
    @Operation(summary = "Delete meter from the switch")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = DeleteMeterResult.class)))
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
    @PutMapping(value = "/{switch_id}/port/{port_no}/config")
    @Operation(summary = "Configure port on the switch")
    @ApiResponse(responseCode = "200", description = "Operation is successful",
            content = @Content(schema = @Schema(implementation = PortDto.class)))
    public CompletableFuture<PortDto> configurePort(
            @PathVariable(name = "switch_id") SwitchId switchId,
            @PathVariable(name = "port_no") int portNo,
            @RequestBody PortConfigurationPayload portConfig) {
        return switchService.configurePort(switchId, portNo, portConfig);
    }

    /**
     * Get a description of the switch ports.
     *
     * @param switchId the switch id.
     * @return switch ports description.
     */
    @GetMapping(value = "/{switch-id}/ports")
    @Operation(summary = "Get switch ports description from the switch")
    @ApiResponse(responseCode = "200",
            content = @Content(schema = @Schema(implementation = SwitchPortsDescription.class)))
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
    @GetMapping(value = "/{switch-id}/ports/{port}")
    @Operation(summary = "Get port description from the switch")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = PortDescription.class)))
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
    @PostMapping(path = "/{switch-id}/under-maintenance", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @Operation(summary = "Update \"Under maintenance\" flag for the switch.")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = SwitchDto.class)))
    public CompletableFuture<SwitchDto> updateLinkUnderMaintenance(@PathVariable("switch-id") SwitchId switchId,
                                                                 @RequestBody UnderMaintenanceDto underMaintenanceDto) {
        return switchService.updateSwitchUnderMaintenance(switchId, underMaintenanceDto);
    }

    /**
     * Delete switch.
     *
     * @param switchId id of switch to delete
     * @param force True value means that all switch checks (switch is deactivated, there is no flow with this switch,
     *              switch has no ISLs) will be ignored.
     * @return result of the operation wrapped into {@link DeleteSwitchResult}. True means no errors is occurred.
     */
    @DeleteMapping(value = "/{switch-id}")
    @ExtraAuthRequired
    @Operation(summary = "Delete switch. Requires special authorization.")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = DeleteSwitchResult.class)))
    @Parameter(in = ParameterIn.HEADER, name = Utils.EXTRA_AUTH,
            content = @Content(schema = @Schema(implementation = String.class)))
    public CompletableFuture<DeleteSwitchResult> deleteSwitch(
            @PathVariable("switch-id") SwitchId switchId,
            @Parameter(description = "default: false. True value means that all switch checks (switch is deactivated, "
                    + "there is no flow with this switch, switch has no ISLs) will be ignored.")
            @RequestParam(name = "force", required = false, defaultValue = "false") boolean force) {
        return switchService.deleteSwitch(switchId, force);
    }

    /**
     * Get all flows for a particular switch.
     *
     * @param switchId the switch
     * @param port the port
     * @return all flows for a particular switch.
     */
    @GetMapping(value = "/{switch-id}/flows", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @Operation(summary = "Get a list of flows that goes through a particular switch, based on arguments.")
    @ApiResponse(responseCode = "200", content = @Content(array = @ArraySchema(
            schema = @Schema(implementation = FlowPayload.class))))
    public CompletableFuture<List<FlowPayload>> getFlowsForSwitch(@PathVariable(value = "switch-id") SwitchId switchId,
                                                                  @RequestParam(value = "port", required = false)
                                                                  Integer port) {
        return switchService.getFlowsForSwitch(switchId, port);
    }

    /**
     * Get switch properties.
     *
     * @param switchId the switch id.
     * @return switch ports description.
     */
    @GetMapping(value = "/{switch-id}/properties")
    @Operation(summary = "Get switch properties from the switch")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = SwitchPropertiesDto.class)))
    public CompletableFuture<SwitchPropertiesDto> getSwitchProperties(@PathVariable("switch-id") SwitchId switchId) {
        return switchService.getSwitchProperties(switchId);
    }

    /**
     * Update switch properties.
     *
     * @param switchId the switch id.
     * @return switch ports description.
     */
    @PutMapping(value = "/{switch-id}/properties")
    @Operation(summary = "Update switch properties from the switch")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = SwitchPropertiesDto.class)))
    public CompletableFuture<SwitchPropertiesDto> updateSwitchProperties(
            @PathVariable("switch-id") SwitchId switchId, @RequestBody SwitchPropertiesDto switchPropertiesDto) {
        return switchService.updateSwitchProperties(switchId, switchPropertiesDto);
    }
}
