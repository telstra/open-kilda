package org.openkilda.service;

import javax.servlet.http.HttpServletRequest;

/**
 * The Interface StatsService.
 * 
 * @author Gaurav Chugh
 */
public interface ServiceStats {


    /**
     * Gets the stats.
     *
     * @param startDate the start date
     * @param endDate the end date
     * @param metric the metric
     * @param request the request
     * @return the stats
     */
    String getStats(String startDate, String endDate, String metric, HttpServletRequest request);


}
