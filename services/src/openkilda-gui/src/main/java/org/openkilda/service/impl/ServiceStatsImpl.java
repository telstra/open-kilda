package org.openkilda.service.impl;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.openkilda.integration.service.StatsIntegrationService;
import org.openkilda.service.ServiceStats;
import org.openkilda.utility.StatsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The Class StatsServiceImpl.
 * 
 * @author Gaurav Chugh
 */
@Service
public class ServiceStatsImpl implements ServiceStats {

    /** The Constant logger. */
    static final Logger log = Logger.getLogger(ServiceStatsImpl.class);

    /** The stats util. */
    @Autowired
    private StatsUtil statsUtil;

    @Autowired
    private StatsIntegrationService statsIntegrationService;


    /*
     * (non-Javadoc)
     * 
     * @see org.openkilda.service.StatsService#getStats(java.lang.String, java.lang.String,
     * java.lang.String, javax.servlet.http.HttpServletRequest)
     */
    @Override
    public String getStats(String startDate, String endDate, String metric,
            HttpServletRequest request) {

        log.info("Inside getStats");
        long startTime = System.currentTimeMillis();
        Map<String, String> query_pairs = statsUtil.getQuery_pairs(request);
        String requestBody = statsUtil.getRequestBody(startDate, endDate, metric, query_pairs);

        log.info("Inside getStats: : startDate: " + startDate + ": endDate: " + endDate
                + ": metric: " + metric);

        String responseEntity = statsIntegrationService.getStats(requestBody);

        log.info("Inside getStats: : Method Execution Time in Milliseconds is:"
                + (System.currentTimeMillis() - startTime));

        return responseEntity;
    }

}
