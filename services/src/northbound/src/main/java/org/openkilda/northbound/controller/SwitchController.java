package org.openkilda.northbound.controller;

import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;

import org.openkilda.messaging.command.switches.ConnectModeRequest;
import org.openkilda.messaging.command.switches.DeleteRulesAction;
import org.openkilda.messaging.command.switches.InstallRulesAction;
import org.openkilda.messaging.error.MessageError;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.northbound.dto.SwitchDto;
import org.openkilda.northbound.service.SwitchService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * REST Controller for switches.
 */
@RestController
@PropertySource("classpath:northbound.properties")
@Api(value = "switches")
@ApiResponses(value = {
        @ApiResponse(code = 200, response = FlowPayload.class, message = "Operation is successful"),
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
     * Get all available links.
     *
     * @return list of links.
     */
    @ApiOperation(value = "Get all available switches", response = SwitchDto.class)
    @GetMapping(path = "/switches")
    @ResponseStatus(HttpStatus.OK)
    public List<SwitchDto> getSwitches() {
        return switchService.getSwitches();
    }

    /**
     * Delete switch rules.
     *
     * @param switchId switch id to delete rules from
     * @param deleteAction defines what to do about the default rules
     * @param oneCookie the cookie to use if deleting one rule (could be any rule)
     * @param correlationId correlation ID header value
     * @return list of the cookies of the rules that have been deleted
     */
    @ApiOperation(value = "Delete switch rules. Requires special authorization",
            response = Long.class, responseContainer = "List")
    @DeleteMapping(value = "/switches/{switch-id}/rules",
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @ExtraAuthRequired
    public ResponseEntity deleteSwitchRules(
            @PathVariable("switch-id") String switchId,
            @ApiParam(value = "default: IGNORE. Can be one of DeleteRulesAction: " +
                    " DROP,DROP_ADD,IGNORE,OVERWRITE,ONE,REMOVE_DROP,REMOVE_BROADCAST," +
                    "REMOVE_UNICAST,REMOVE_DEFAULTS,REMOVE_ADD",
                    required = false)
            @RequestParam("delete-action") Optional<DeleteRulesAction> deleteAction,
            @RequestParam("one-cookie") Optional<Long> oneCookie,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {
        List<Long> response = switchService
                .deleteRules(switchId, deleteAction.orElse(DeleteRulesAction.IGNORE), oneCookie.orElse(0L), correlationId);
        return ResponseEntity.ok(response);
    }

    /**
     * Install switch rules.
     *
     * @param switchId switch id to delete rules from
     * @param installAction defines what to do about the default rules
     * @param correlationId correlation ID header value
     * @return list of the cookies of the rules that have been installed
     */
    @ApiOperation(value = "Install switch rules. Requires special authorization",
            response = String.class, responseContainer = "List")
    @PutMapping(value = "/switches/{switch-id}/rules",
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @ExtraAuthRequired
    public ResponseEntity installSwitchRules(
            @PathVariable("switch-id") String switchId,
            @ApiParam(value = "default: INSTALL_DEFAULTS. Can be one of InstallRulesAction: " +
                    " INSTALL_DROP,INSTALL_BROADCAST,INSTALL_UNICAST,INSTALL_DEFAULTS",
                    required = false)
            @RequestParam("install-action") Optional<InstallRulesAction> installAction,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {
        List<Long> response = switchService
                .installRules(switchId, installAction.orElse(InstallRulesAction.INSTALL_DEFAULTS), correlationId);
        return ResponseEntity.ok(response);
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
     * @param correlationId correlation ID header value
     * @return the value of the toggle in Floodlight.
     */
    @ApiOperation(value = "Set the connect mode if mode is specified. If mode is null, this is effectively a get.",
            response = ConnectModeRequest.Mode.class)
    @PutMapping(value = "/switches/toggle-connect-mode",
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity toggleSwitchConnectMode(
            @RequestParam("mode") ConnectModeRequest.Mode mode,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {
        ConnectModeRequest.Mode response = switchService.connectMode(mode, correlationId);
        return ResponseEntity.ok(response);
    }



    /**
     * Sync rules on the switch.
     * @param switchId id of the switch.
     */
    @ApiOperation(value = "Sync rules on the switch", response = SwitchDto.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, response = FlowPayload.class, message = "Operation is successful"),
            @ApiResponse(code = 400, response = MessageError.class, message = "Invalid input data"),
            @ApiResponse(code = 401, response = MessageError.class, message = "Unauthorized"),
            @ApiResponse(code = 403, response = MessageError.class, message = "Forbidden"),
            @ApiResponse(code = 404, response = MessageError.class, message = "Not found"),
            @ApiResponse(code = 500, response = MessageError.class, message = "General error"),
            @ApiResponse(code = 503, response = MessageError.class, message = "Service unavailable")})
    @GetMapping(path = "/{switch_id}/sync_rules")
    @ResponseStatus(HttpStatus.OK)
    public void syncRules(@PathVariable(name = "switch_id") String switchId,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {
        switchService.syncRules(switchId, correlationId);
    }

}
