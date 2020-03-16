/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.service;

import org.openkilda.constants.IConstants.Metrics;
import org.openkilda.constants.IConstants.Status;
import org.openkilda.constants.OpenTsDb.StatsType;
import org.openkilda.integration.converter.PortConverter;
import org.openkilda.integration.exception.IntegrationException;
import org.openkilda.integration.model.response.IslLink;
import org.openkilda.integration.model.response.IslPath;
import org.openkilda.integration.service.StatsIntegrationService;
import org.openkilda.integration.service.SwitchIntegrationService;
import org.openkilda.integration.source.store.SwitchStoreService;
import org.openkilda.integration.source.store.dto.Port;
import org.openkilda.model.FlowPathStats;
import org.openkilda.model.PortDiscrepancy;
import org.openkilda.model.PortInfo;
import org.openkilda.model.SwitchPortStats;
import org.openkilda.store.service.StoreService;
import org.openkilda.utility.ApplicationProperties;
import org.openkilda.utility.CollectionUtil;
import org.openkilda.utility.IoUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Class StatsService.
 *
 * @author Gaurav Chugh
 */

@Service
public class StatsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatsService.class);

    @Autowired
    private StatsIntegrationService statsIntegrationService;

    @Autowired
    private SwitchIntegrationService switchIntegrationService;
    
    @Autowired
    private StoreService storeService;
    
    @Autowired
    private SwitchStoreService switchStoreService;
    
    @Autowired
    private PortConverter portConverter;

    @Autowired
    private ApplicationProperties applicationProperties;

    /**
     * Gets the stats.
     *
     * @param startDate
     *            the start date
     * @param endDate
     *            the end date
     * @param downsample
     *            the downsample
     * @param srcSwitch
     *            the src switch
     * @param srcPort
     *            the src port
     * @param dstSwitch
     *            the dst switch
     * @param dstPort
     *            the dst port
     * @param metric
     *            the metric
     * @return the stats
     * @throws IntegrationException
     *             the integration exception
     */
    public String getSwitchIslStats(String startDate, String endDate, String downsample, String srcSwitch,
            String srcPort, String dstSwitch, String dstPort, String metric) throws IntegrationException {
        return statsIntegrationService.getStats(startDate, endDate, downsample, null, null, null, srcSwitch, srcPort,
                dstSwitch, dstPort, StatsType.ISL, metric, null);
    }

    /**
     * Gets the flow stats.
     *
     * @param startDate
     *            the start date
     * @param endDate
     *            the end date
     * @param downsample
     *            the downsample
     * @param flowId
     *            the flow id
     * @param metric
     *            the metric
     * @return the flow stats
     * @throws IntegrationException
     *             the integration exception
     */
    public String getFlowStats(String startDate, String endDate, String downsample, String flowId, String metric)
            throws IntegrationException {
        return statsIntegrationService.getStats(startDate, endDate, downsample, null, null, flowId, null, null, null,
                null, StatsType.FLOW, metric, null);
    }

    /**
     * Gets the switch stats.
     *
     * @param switchid
     *            the switchid
     * @param portnumber
     *            the portnumber
     * @param startDate
     *            the start date
     * @param endDate
     *            the end date
     * @param downsample
     *            the downsample
     * @param metric
     *            the metric
     * @return the switch stats
     * @throws IntegrationException
     *             the integration exception
     */
    public String getSwitchPortStats(String startDate, String endDate, String downsample, String switchid,
            String portnumber, String metric) throws IntegrationException {
        List<String> switchIds = new ArrayList<String>();
        switchIds.add(switchid);
        return statsIntegrationService.getStats(startDate, endDate, downsample, switchIds, portnumber, null, null, null,
                null, null, StatsType.PORT, metric, null);
    }

    /**
     * Gets the switch isl loss packet stats.
     *
     * @param startDate
     *            the start date
     * @param endDate
     *            the end date
     * @param downsample
     *            the downsample
     * @param srcSwitch
     *            the src switch
     * @param srcPort
     *            the src port
     * @param dstSwitch
     *            the dst switch
     * @param dstPort
     *            the dst port
     * @param metric
     *            the metric
     * @return the switch isl loss packet stats
     */
    public String getSwitchIslLossPacketStats(String startDate, String endDate, String downsample, String srcSwitch,
            String srcPort, String dstSwitch, String dstPort, String metric) {
        return statsIntegrationService.getStats(startDate, endDate, downsample, null, null, null, srcSwitch, srcPort,
                dstSwitch, dstPort, StatsType.ISL_LOSS_PACKET, metric, null);
    }

    /**
     * Gets the flow loss packet stats.
     *
     * @param startDate
     *            the start date
     * @param endDate
     *            the end date
     * @param downsample
     *            the downsample
     * @param flowId
     *            the flow id
     * @param direction
     *            the direction
     * @return the flow loss packet stats
     * @throws IntegrationException
     *             the integration exception
     */
    public String getFlowLossPacketStats(String startDate, String endDate, String downsample, String flowId,
            String direction) throws IntegrationException {
        return statsIntegrationService.getStats(startDate, endDate, downsample, null, null, flowId, null, null, null,
                null, StatsType.FLOW_LOSS_PACKET, Metrics.FLOW_INGRESS_PACKETS.getTag().replace("Flow_", ""),
                direction);
    }

    /**
     * Gets the flow path stat.
     *
     * @param flowPathStats
     *            the flow path stat
     * @return the flow path stat
     */
    public String getFlowPathStats(FlowPathStats flowPathStats) {
        return statsIntegrationService.getStats(flowPathStats.getStartDate(), flowPathStats.getEndDate(),
                flowPathStats.getDownsample(), getSwitches(flowPathStats), null, flowPathStats.getFlowid(), null, null,
                null, null, StatsType.FLOW_RAW_PACKET, flowPathStats.getMetric(), flowPathStats.getDirection());
    }

    /**
     * Gets the switch ports stats.
     *
     * @param startDate
     *            the start date
     * @param endDate
     *            the end date
     * @param downSample
     *            the down sample
     * @param switchId
     *            the switch id
     * @return the switch ports stats
     */
    public List<PortInfo> getSwitchPortsStats(String startDate, String endDate, String downSample, String switchId) {
        List<String> switchIds = Arrays.asList(switchId);
        List<SwitchPortStats> switchPortStats = new ArrayList<SwitchPortStats>();
        try {
            String result = statsIntegrationService.getStats(startDate, endDate, downSample, switchIds, null, null,
                    null, null, null, null, StatsType.SWITCH_PORT, null, null);
            ObjectMapper mapper = new ObjectMapper();
            switchPortStats = mapper.readValue(result,
                    TypeFactory.defaultInstance().constructCollectionLikeType(List.class, SwitchPortStats.class));
        } catch (Exception e) {
            LOGGER.error("Error occurred while retriving switch port stats", e);
        }
        List<PortInfo> portStats = getSwitchPortStatsReport(switchPortStats, switchId);
        if (storeService.getSwitchStoreConfig().getUrls().size() > 0) {
            if (!CollectionUtil.isEmpty(switchIds)) {
                try {
                    List<Port> inventoryPorts = switchStoreService
                            .getSwitchPort(IoUtil.switchCodeToSwitchId(switchIds.get(0)));
                    processInventoryPorts(portStats, inventoryPorts);
                } catch (Exception ex) {
                    LOGGER.error("Error occurred while retriving switch ports stats for inventory", ex);
                }
            }
        }
        return portStats;
    }
    
    
    /**
     * Gets the meter stats.
     *
     * @param startDate
     *            the start date
     * @param endDate
     *            the end date
     * @param downsample
     *            the downsample
     * @param flowId
     *            the flow id
     * @param metric
     *            the direction
     * @return the flow stats
     * @throws IntegrationException
     *             the integration exception
     */
    public String getMeterStats(String startDate, String endDate, String downsample,
            String flowId, String metric, String direction)
            throws IntegrationException {
        return statsIntegrationService.getStats(startDate, endDate, downsample, null, 
                null, flowId, null, null, null, null, 
                StatsType.METER, metric, direction);
    }

    private void processInventoryPorts(final List<PortInfo> portStats, final List<Port> inventoryPorts) {
        if (!CollectionUtil.isEmpty(inventoryPorts)) {
            List<PortInfo> discrepancyPorts = new ArrayList<PortInfo>();
            for (Port port : inventoryPorts) {
                int index = -1;
                for (PortInfo portInfo : portStats) {
                    if (port.getPortNumber() == Integer.parseInt(portInfo.getPortNumber())) {
                        index = portStats.indexOf(portInfo);
                        break;
                    }
                }
                if (index >= 0) {
                    PortInfo portInfo = portStats.get(index);
                    portConverter.appendInventoryInfo(portInfo, port);
                    PortDiscrepancy portDiscrepancy = new PortDiscrepancy();
                    portDiscrepancy.setControllerDiscrepancy(false);
                    if (!portInfo.getAssignmenttype().equalsIgnoreCase(port.getAssignmentType())) {
                        portDiscrepancy.setAssignmentType(true);
                        portDiscrepancy.setControllerAssignmentType(portInfo.getAssignmenttype());
                        portDiscrepancy.setInventoryAssignmentType(port.getAssignmentType());
                    }
                    portInfo.setDiscrepancy(portDiscrepancy);
                } else {
                    PortInfo portInfoObj = new PortInfo();
                    portConverter.toPortInfo(portInfoObj, port);
                    discrepancyPorts.add(portInfoObj);
                }
            }

            for (PortInfo portInfo : portStats) {
                boolean flag = false;
                for (Port port : inventoryPorts) {
                    if (port.getPortNumber() == Integer.parseInt(portInfo.getPortNumber())) {
                        flag = true;
                        break;
                    }
                }
                if (!flag) {
                    PortDiscrepancy discrepancy = new PortDiscrepancy();
                    discrepancy.setInventoryDiscrepancy(true);
                    discrepancy.setControllerDiscrepancy(false);
                    discrepancy.setAssignmentType(true);
                    discrepancy.setControllerAssignmentType(portInfo.getAssignmenttype());
                    discrepancy.setInventoryAssignmentType(null);

                    portInfo.setDiscrepancy(discrepancy);
                }
            }
            portStats.addAll(discrepancyPorts);
        }
    }
    
    /**
     * Creates the switch post stat report.
     *
     * @param switchPortStats
     *            the list
     * @return the ports stat
     */
    private List<PortInfo> getSwitchPortStatsReport(List<SwitchPortStats> switchPortStats, String switchId) {
        Map<String, Map<String, Double>> portStatsByPortNo = new HashMap<String, Map<String, Double>>();
        for (SwitchPortStats stats : switchPortStats) {
            String port = stats.getTags().getPort();

            if (Integer.parseInt(port) > 0) {
                if (!portStatsByPortNo.containsKey(port)) {
                    portStatsByPortNo.put(port, new HashMap<String, Double>());
                }
                portStatsByPortNo.get(port).put(
                        stats.getMetric().replace(applicationProperties.getOpenTsdbMetricPrefix() + "switch.", ""),
                        calculateHighestValue(stats.getDps()));
            }
        }

        return getIslPorts(portStatsByPortNo, switchId);
    }

    /**
     * Calculate highest value.
     *
     * @param dps
     *            the dps
     * @return the double
     */
    private double calculateHighestValue(Map<String, Double> dps) {
        double maxVal = 0.0;
        if (!dps.isEmpty()) {
            long maxTimestamp = 0;
            for (String key : dps.keySet()) {
                long val = Long.parseLong(key);
                if (maxTimestamp < val) {
                    maxTimestamp = val;
                }
            }
            maxVal = BigDecimal.valueOf(dps.get(String.valueOf(maxTimestamp))).setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        }
        return maxVal;
    }

    /**
     * Sets the isl ports.
     *
     * @param portInfos
     *            the port infos
     * @param switchid
     *            the switchid
     * @return the list
     */
    private List<PortInfo> getIslPorts(final Map<String, Map<String, Double>> portStatsByPortNo, String switchid) {
        List<PortInfo> portInfos = getPortInfo(portStatsByPortNo);

        List<IslLink> islLinkPorts = switchIntegrationService.getIslLinkPortsInfo(null);
        String switchIdInfo = null;
        if (islLinkPorts != null) {
            for (IslLink islLink : islLinkPorts) {
                for (IslPath islPath : islLink.getPath()) {
                    switchIdInfo = ("SW" + islPath.getSwitchId().replaceAll(":", "")).toUpperCase();
                    if (switchIdInfo.equals(switchid)) {
                        for (int i = 0; i < portInfos.size(); i++) {
                            if (portInfos.get(i).getPortNumber().equals(islPath.getPortNo().toString())) {
                                portInfos.get(i).setAssignmenttype("ISL");
                            }
                        }
                    }
                }
            }
        }
        return portInfos;
    }

    private List<PortInfo> getPortInfo(final Map<String, Map<String, Double>> portStatsByPortNo) {
        List<PortInfo> portInfos = new ArrayList<PortInfo>();
        for (Map.Entry<String, Map<String, Double>> portStats : portStatsByPortNo.entrySet()) {
            PortInfo portInfo = new PortInfo();
            portInfo.setPortNumber(portStats.getKey());
            portInfo.setAssignmenttype("PORT");
            portInfo.setStatus(Status.DOWN);
            if (portStats.getValue().containsKey("state")) {
                portInfo.setStatus(portStats.getValue().get("state") == 0 ? Status.DOWN : Status.UP);
                portStats.getValue().remove("state");
            }
            portInfo.setStats(portStats.getValue());
            portInfos.add(portInfo);
        }
        return portInfos;
    }

    private List<String> getSwitches(FlowPathStats flowPathStats) {
        List<String> switches = null;
        if (flowPathStats != null) {
            switches = flowPathStats.getSwitches();
            if (switches == null || switches.isEmpty()) {
                switches = new ArrayList<String>();
                switches.add("*");
            }
        }
        return switches;
    }
}
