package org.openkilda.northbound.controller;

import java.util.List;

import org.openkilda.messaging.error.MessageError;
import org.openkilda.northbound.dto.LinkPropsDto;
import org.openkilda.northbound.dto.LinksDto;
import org.openkilda.northbound.service.LinkPropsResult;
import org.openkilda.northbound.service.LinkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * REST Controller for links.
 */
@RestController
@PropertySource("classpath:northbound.properties")
@ApiResponses(value = { 
		@ApiResponse(code = 400, response = MessageError.class, message = "Invalid input data"),
		@ApiResponse(code = 401, response = MessageError.class, message = "Unauthorized"),
		@ApiResponse(code = 403, response = MessageError.class, message = "Forbidden"),
		@ApiResponse(code = 404, response = MessageError.class, message = "Not found"),
		@ApiResponse(code = 500, response = MessageError.class, message = "General error"),
		@ApiResponse(code = 503, response = MessageError.class, message = "Service unavailable") })
public class LinkController {

	@Autowired
	private LinkService linkService;

	// TODO: Does LinkController really return all of the codes below? Looks like
	// copy / paste.

	/**
	 * Get all available links.
	 *
	 * @return list of links.
	 */
	@ApiOperation(value = "Get all links", response = LinksDto.class, responseContainer = "List")
	@ApiResponse(code = 200, response = LinksDto.class, responseContainer = "List", message = "Operation is successful")
	@GetMapping(path = "/links")
	@ResponseStatus(HttpStatus.OK)
	public List<LinksDto> getLinks() {
		return linkService.getLinks();
	}

	/**
	 * Get link properties from the static link properties table.
	 *
	 * @param keys
	 *            if null, get all link props. Otherwise, the link props that much
	 *            the primary keys.
	 * @return list of link properties.
	 */
	@ApiOperation(value = "Get all link properties (static), based on arguments.", response = LinkPropsDto.class, responseContainer = "List")
	@ApiResponse(code = 200, response = LinkPropsDto.class, responseContainer = "List", message = "Operation is successful")
	@RequestMapping(path = "/link/props", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
	@ResponseStatus(HttpStatus.OK)
	public List<LinkPropsDto> getLinkProps(LinkPropsDto keys) {
		return linkService.getLinkProps(keys);
	}

	/**
	 * Create/Update link properties in the static link properties table.
	 *
	 * @param keysAndProps
	 *            if null, get all link props. Otherwise, the link props that much
	 *            the primary keys.
	 * @return list of link properties.
	 */
	@ApiOperation(value = "Get all link properties (static), based on arguments.", response = LinkPropsResult.class)
	@ApiResponse(code = 200, response = LinkPropsResult.class, message = "Operation is successful")
	@RequestMapping(path = "/link/props", method = RequestMethod.PUT, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
	@ResponseStatus(HttpStatus.OK)
	public LinkPropsResult putLinkProps(@RequestBody List<LinkPropsDto> keysAndProps) {
		return linkService.setLinkProps(keysAndProps);
	}

	/**
	 * Delete link properties from the static link properties table.
	 *
	 * @param keysAndProps
	 *            if null, get all link props. Otherwise, the link props that much
	 *            the primary keys.
	 * @return list of link properties.
	 */
	@ApiOperation(value = "Get all link properties (static), based on arguments.", response = LinkPropsResult.class)
	@ApiResponse(code = 200, response = LinkPropsResult.class, message = "Operation is successful")
	@RequestMapping(path = "/link/props", method = RequestMethod.DELETE, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
	@ResponseStatus(HttpStatus.OK)
	public LinkPropsResult delLinkProps(@RequestBody List<LinkPropsDto> keysAndProps) {
		return linkService.delLinkProps(keysAndProps);
	}

}
