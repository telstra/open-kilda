package org.openkilda.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.openkilda.constants.OpenTsDB.StatsType;
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

    /**
     * Gets the stats.
     *
     * @param startDate the start date
     * @param endDate the end date
     * @param downsample the downsample
     * @param SwitchId the switch id
     * @param port the port
     * @param flowId the flow id
     * @param srcSwitch the src switch
     * @param srcPort the src port
     * @param dstSwitch the dst switch
     * @param dstPort the dst port
     * @param statsType the stats type
     * @param metric the metric
     * @return the stats
     * @throws IntegrationException the integration exception
     */
    public String getSwitchIslStats(String startDate, String endDate, String downsample,
            String srcSwitch, String srcPort,
            String dstSwitch, String dstPort, String metric)
            throws IntegrationException {
        return statsIntegrationService.getStats(startDate, endDate, downsample, null, null, null, srcSwitch, srcPort, dstSwitch, dstPort, StatsType.ISL, metric);
    }

    /**
     * Gets the flow stats.
     *
     * @param startDate the start date
     * @param endDate the end date
     * @param downsample the downsample
     * @param flowId the flow id
     * @param metric the metric
     * @return the flow stats
     * @throws IntegrationException the integration exception
     */
    public String getFlowStats(String startDate, String endDate, String downsample, String flowId,
            String metric) throws IntegrationException {
        return statsIntegrationService.getStats(startDate, endDate, downsample, null, null, flowId, null, null, null, null,
                StatsType.FLOW, metric);
    }

    /**
     * Gets the switch stats.
     *
     * @param switchid the switchid
     * @param portnumber the portnumber
     * @param startDate the start date
     * @param endDate the end date
     * @param downsample the downsample
     * @param metric the metric
     * @return the switch stats
     * @throws IntegrationException the integration exception
     */
    public String getSwitchPortStats(String startDate, String endDate, String downsample,
            String switchid, String portnumber, String metric) throws IntegrationException {
        return statsIntegrationService.getStats(startDate, endDate, downsample, switchid,
                portnumber, null, null, null, null, null, StatsType.PORT, metric);
    }


}
