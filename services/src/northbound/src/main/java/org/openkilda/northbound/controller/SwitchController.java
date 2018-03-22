package org.openkilda.northbound.controller;

import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.openkilda.messaging.command.switches.DefaultRulesAction;
import org.openkilda.messaging.error.MessageError;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.northbound.dto.SwitchDto;
import org.openkilda.northbound.service.SwitchService;
import org.openkilda.northbound.utils.ExtraAuthRequired;
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
     * @param defaultRules defines what to do about the default rules
     * @param correlationId correlation ID header value
     * @return list of rules that have been deleted
     */
    @ApiOperation(value = "Delete switch rules. Requires special authorization",
            response = String.class, responseContainer = "List")
    @DeleteMapping(value = "/switches/{switch-id}/rules",
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @ExtraAuthRequired
    public ResponseEntity deleteSwitchRules(
            @PathVariable("switch-id") String switchId,
            @RequestParam("defaultRules") Optional<DefaultRulesAction> defaultRules,
            @RequestHeader(value = CORRELATION_ID, defaultValue = DEFAULT_CORRELATION_ID) String correlationId) {
        List<Long> response = switchService
                .deleteRules(switchId, defaultRules.orElse(DefaultRulesAction.IGNORE), correlationId);
        return ResponseEntity.ok(response);
    }
}
