package org.openkilda.controller;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;
import org.openkilda.service.ServiceStats;
import org.openkilda.utility.MetricsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
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
    private ServiceStats statsService;

    /**
     * Gets the stats.
     *
     * @param request the request
     * @param model the model
     * @param startDate the start date
     * @param endDate the end date
     * @param metric the metric
     * @return the stats
     * @throws Exception the exception
     */
    @RequestMapping(value = "/{startDate}/{endDate}/{metric:.+}", method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody ResponseEntity<Object> getStats(@Context HttpServletRequest request,
             @PathVariable String startDate, @PathVariable String endDate,
            @PathVariable String metric) throws Exception {

        log.info("Inside StatsController method getStats ");
        String response = statsService.getStats(startDate, endDate, metric, request);
        if (response == null || response.equalsIgnoreCase(""))
            return new ResponseEntity<Object>(new JSONObject(), HttpStatus.NO_CONTENT);
        return new ResponseEntity<Object>(response, HttpStatus.OK);
    }

    /**
     * Gets the metric detail.
     *
     * @param model the model
     * @param request the request
     * @return the metric detail
     */
    @RequestMapping(value = "/metrics", method = RequestMethod.GET)
    public @ResponseBody ResponseEntity<Object> getMetricDetail() {

        log.info("Inside getMetricDetail");
        List<String> metricsList = null;
        try {
            metricsList = MetricsUtil.getMetricList();
        } catch (Exception exception) {
            log.error("Exception in getMetricDetail " + exception.getMessage());
        }
        return new ResponseEntity<Object>(metricsList, HttpStatus.OK);
    }
}
