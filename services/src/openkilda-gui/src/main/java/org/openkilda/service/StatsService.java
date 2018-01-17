package org.openkilda.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

import org.openkilda.integration.exception.IntegrationException;
import org.openkilda.integration.service.StatsIntegrationService;

/**
 * The Class StatsService.
 *
 * @author Gaurav Chugh
 */
@Service
public class StatsService {

    @Autowired
    private StatsIntegrationService statsIntegrationService;


    public String getStats(final String startDate, final String endDate, final String metric,
            final Map<String, String[]> requestParams) throws IntegrationException {
        return statsIntegrationService.getStats(startDate, endDate, metric, requestParams);
    }
}
