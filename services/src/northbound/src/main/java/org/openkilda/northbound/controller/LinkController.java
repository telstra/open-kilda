package org.openkilda.northbound.controller;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.openkilda.messaging.error.MessageError;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.northbound.dto.LinksDto;
import org.openkilda.northbound.service.LinkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST Controller for links.
 */
@RestController
@PropertySource("classpath:northbound.properties")
public class LinkController {

    @Autowired
    private LinkService linkService;

    /**
     * Get all available links.
     *
     * @return list of links.
     */
    @ApiOperation(value = "Get all links", response = LinksDto.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, response = FlowPayload.class, message = "Operation is successful"),
            @ApiResponse(code = 400, response = MessageError.class, message = "Invalid input data"),
            @ApiResponse(code = 401, response = MessageError.class, message = "Unauthorized"),
            @ApiResponse(code = 403, response = MessageError.class, message = "Forbidden"),
            @ApiResponse(code = 404, response = MessageError.class, message = "Not found"),
            @ApiResponse(code = 500, response = MessageError.class, message = "General error"),
            @ApiResponse(code = 503, response = MessageError.class, message = "Service unavailable")})
    @GetMapping(path = "/links")
    @ResponseStatus(HttpStatus.OK)
    public List<LinksDto> getLinks() {
        return linkService.getLinks();
    }

}
