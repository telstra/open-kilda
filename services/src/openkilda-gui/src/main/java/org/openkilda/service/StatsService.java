package org.openkilda.service;

import org.openkilda.integration.exception.IntegrationException;
import org.openkilda.integration.service.StatsIntegrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The Class StatsService.
 *
 * @author Gaurav Chugh
 */
@Service
public class StatsService {

    @Autowired
    private StatsIntegrationService statsIntegrationService;
    
    public String getStats(String startDate, String endDate, String downsample,
			String SwitchId, String port, String flowId, String srcSwitch, String srcPort, String dstSwitch, String dstPort, String statsType, String metric)throws IntegrationException{
    	return statsIntegrationService.getStats(startDate, endDate, downsample, SwitchId,port,flowId, srcSwitch, srcPort, dstSwitch, dstPort, statsType, metric);
    }
}
