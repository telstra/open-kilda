package org.openkilda.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;

import org.apache.log4j.Logger;
import org.openkilda.constants.IConstants.Metrics;
import org.openkilda.constants.OpenTsDB;
import org.openkilda.service.StatsService;

@Controller
@RequestMapping(value = "/stats")
public class StatsController {

    private static final Logger LOGGER = Logger.getLogger(StatsController.class);

    @Autowired
    private StatsService statsService;

    /**
     * Gets the stats.
     *
     * @param request the request
     * @param startDate the start date
     * @param endDate the end date
     * @param metric the metric
     * @return the stats
     * @throws Exception the exception
     */
    @RequestMapping(value = "/{startDate}/{endDate}/{metric:.+}", method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody String getStats(@Context final HttpServletRequest request,
            @PathVariable final String startDate, @PathVariable final String endDate,
            @PathVariable final String metric) throws Exception {
        return statsService.getStats(startDate, endDate, metric, request.getParameterMap());
    }

    /**
     * Gets the metric detail.
     *
     * @return the metric detail
     */
    @RequestMapping(value = "/metrics")
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody String[] getMetricDetail() {
        return Metrics.LIST;
    }

    
    /**
	 * Gets the isl stats.
	 *
	 * @param flowid the flowid
	 * @param startDate the start date
	 * @param endDate the end date
	 * @param downsample the downsample
	 * @return the flow stats
	 * @throws Exception the exception
	 */
	@RequestMapping(value = "isl/{srcSwitch}/{srcPort}/{dstSwitch}/{dstPort}/{startDate}/{endDate}/{downsample}/{metric:.+}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.OK)
	public @ResponseBody String getIslStats(
			@PathVariable String srcSwitch, @PathVariable String srcPort,
			@PathVariable String dstSwitch, @PathVariable String dstPort,
			@PathVariable String startDate, @PathVariable String endDate,
			@PathVariable String downsample,@PathVariable String metric) throws Exception {

		LOGGER.info("Inside StatsController method getIslStats ");
		return statsService.getStats(startDate, endDate, downsample,
				null, null, null,srcSwitch,srcPort, dstSwitch, dstPort, OpenTsDB.ISL_STATS, metric);
		 
	}

	/**
	 * Gets the port stats.
	 *
	 * @param switchid the switchid
	 * @param portnumber the portnumber
	 * @param startDate the start date
	 * @param endDate the end date
	 * @param downsample the downsample
	 * @return the port stats
	 * @throws Exception the exception
	 */
	@RequestMapping(value = "switchid/{switchid}/portnumber/{portnumber}/{startDate}/{endDate}/{downsample}/{metric:.+}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.OK)
	public @ResponseBody String getPortStats(
			@PathVariable String switchid, @PathVariable String portnumber,
			@PathVariable String startDate, @PathVariable String endDate,
			@PathVariable String downsample,@PathVariable String metric) throws Exception {

		LOGGER.info("Inside StatsController method getPortStats ");
		return statsService.getStats(startDate, endDate, downsample,
				switchid, portnumber, null, null, null, null, null, OpenTsDB.PORT_STATS, metric);
		 
	}
	
	/**
	 * Gets the flow stats.
	 *
	 * @param flowid the flowid
	 * @param startDate the start date
	 * @param endDate the end date
	 * @param downsample the downsample
	 * @return the flow stats
	 * @throws Exception the exception
	 */
	@RequestMapping(value = "flowid/{flowid}/{startDate}/{endDate}/{downsample}/{metric:.+}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.OK)
	public @ResponseBody String getFlowStats(
			@PathVariable String flowid, @PathVariable String startDate,
			@PathVariable String endDate,@PathVariable String downsample,
			@PathVariable String metric) throws Exception {

		LOGGER.info("Inside StatsController method getFlowStats ");
		return statsService.getStats(startDate, endDate, downsample,
				null, null, flowid, null, null, null, null, OpenTsDB.FLOW_STATS, metric);
	}
	
}
