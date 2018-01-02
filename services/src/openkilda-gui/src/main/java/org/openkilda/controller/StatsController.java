package org.openkilda.atdd.utils.controller;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;

import org.apache.log4j.Logger;
import org.openkilda.service.StatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * The Class StatsController.
 * 
 * @author sumitpal.singh
 */
@Controller
@RequestMapping(value = "/stats")
public class StatsController {

	/** The Constant logger. */
	static final Logger log = Logger.getLogger(StatsController.class);

	/** The stats service. */
	@Autowired
	private StatsService statsService;

	/**
	 * Gets the stats.
	 *
	 * @param request
	 *            the request
	 * @param model
	 *            the model
	 * @param startDate
	 *            the start date
	 * @param endDate
	 *            the end date
	 * @param metric
	 *            the metric
	 * @return the stats
	 * @throws Exception
	 *             the exception
	 */
	@RequestMapping(value = "/{startDate}/{endDate}/{metric:.+}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody ResponseEntity<Object> getStats(
			@Context HttpServletRequest request, ModelMap model,
			@PathVariable String startDate, @PathVariable String endDate,
			@PathVariable String metric) throws Exception {

		log.info("inside StatsController method getStats ");
		Object response = statsService.getStats(startDate, endDate, metric,
				request);
		return new ResponseEntity<Object>(response, HttpStatus.OK);
	}
 
}
