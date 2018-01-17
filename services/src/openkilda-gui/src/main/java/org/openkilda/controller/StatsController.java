package org.openkilda.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;
import org.openkilda.constants.IConstants.Metrics;
import org.openkilda.service.StatsService;
import org.openkilda.utility.StringUtil;

/**
 * The Class StatsController.
 *
 * @author sumitpal.singh
 */
@Controller
@RequestMapping(value = "/stats")
public class StatsController {

    /** The Constant logger. */
    private static final Logger LOGGER = Logger.getLogger(StatsController.class);

    /** The stats service. */
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
    public @ResponseBody ResponseEntity<Object> getStats(@Context final HttpServletRequest request,
            @PathVariable final String startDate, @PathVariable final String endDate,
            @PathVariable final String metric) throws Exception {
        LOGGER.info("Inside StatsController method getStats ");
        String response = statsService.getStats(startDate, endDate, metric, request.getParameterMap());
        if (StringUtil.isNullOrEmpty(response)) {
            return new ResponseEntity<Object>(new JSONObject(), HttpStatus.NO_CONTENT);
        }
        return new ResponseEntity<Object>(response, HttpStatus.OK);
    }

    /**
     * Gets the metric detail.
     *
     * @return the metric detail
     */
    @RequestMapping(value = "/metrics")
    @ResponseBody
    public String[] getMetricDetail() {
        return Metrics.LIST;
    }
}
