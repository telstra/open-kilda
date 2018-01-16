package org.openkilda.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

import org.openkilda.integration.service.StatsIntegrationService;
import org.openkilda.service.helper.StatsHelper;

/**
 * The Class StatsServiceImpl.
 *
 * @author Gaurav Chugh
 */
@Service
public class ServiceStats {

    @Autowired
    private StatsHelper statsHelper;

    @Autowired
    private StatsIntegrationService statsIntegrationService;


    public String getStats(final String startDate, final String endDate, final String metric,
            final Map<String, String[]> requestParams) {
        String requestBody = statsHelper.getRequestBody(startDate, endDate, metric, requestParams);
        return statsIntegrationService.getStats(requestBody);
    }

}
