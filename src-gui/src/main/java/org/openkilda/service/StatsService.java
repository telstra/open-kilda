/* Copyright 2023 Telstra Open Source
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

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.openkilda.constants.Metrics.ISL_LATENCY;
import static org.openkilda.constants.Metrics.ISL_RTT;
import static org.openkilda.constants.Metrics.SWITCH_STATE;
import static org.openkilda.utility.VictoriaQueryUtil.buildRangeQueryParams;

import org.openkilda.config.ApplicationProperties;
import org.openkilda.constants.Direction;
import org.openkilda.constants.IConstants.Status;
import org.openkilda.constants.Metrics;
import org.openkilda.constants.OpenTsDb.StatsType;
import org.openkilda.exception.InvalidRequestException;
import org.openkilda.integration.converter.PortConverter;
import org.openkilda.integration.model.response.IslLink;
import org.openkilda.integration.model.response.IslPath;
import org.openkilda.integration.service.StatsIntegrationService;
import org.openkilda.integration.service.SwitchIntegrationService;
import org.openkilda.integration.source.store.SwitchStoreService;
import org.openkilda.integration.source.store.dto.Port;
import org.openkilda.model.PortDiscrepancy;
import org.openkilda.model.PortInfo;
import org.openkilda.model.SwitchLogicalPort;
import org.openkilda.model.VictoriaStatsReq;
import org.openkilda.model.victoria.MetricValues;
import org.openkilda.model.victoria.RangeQueryParams;
import org.openkilda.model.victoria.VictoriaData;
import org.openkilda.model.victoria.dbdto.VictoriaDbRes;
import org.openkilda.store.service.StoreService;
import org.openkilda.utility.CollectionUtil;
import org.openkilda.utility.IoUtil;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class StatsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatsService.class);

    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm:ss");

    private final StatsIntegrationService statsIntegrationService;

    private final SwitchIntegrationService switchIntegrationService;

    private final StoreService storeService;

    private final SwitchStoreService switchStoreService;


    private final ApplicationProperties appProps;

    public StatsService(StatsIntegrationService statsIntegrationService,
                        SwitchIntegrationService switchIntegrationService,
                        StoreService storeService,
                        SwitchStoreService switchStoreService,
                        ApplicationProperties appProps) {
        this.statsIntegrationService = statsIntegrationService;
        this.switchIntegrationService = switchIntegrationService;
        this.storeService = storeService;
        this.switchStoreService = switchStoreService;
        this.appProps = appProps;
    }

    /**
     * Retrieves and transforms Victoria statistics data for a specific flow and metric.
     *
     * @param startDate  The start date of the data retrieval period.
     * @param endDate    The end date of the data retrieval period.
     * @param step       The time step for data aggregation.
     * @param flowId     The ID of the flow.
     * @param metricList The metric for which statistics are retrieved.
     * @param direction  The direction of the flow data (null for both forward and reverse).
     * @return A {@code List} of {@link VictoriaData} objects representing statistics for each metric and direction.
     * @throws InvalidRequestException if there is an issue with the request parameters.
     */
    public List<VictoriaData> getTransformedFlowVictoriaStats(String statsType,
                                                              String startDate,
                                                              String endDate,
                                                              String step,
                                                              String flowId,
                                                              List<String> metricList,
                                                              Direction direction) throws InvalidRequestException {
        validateRequestParameters(startDate, metricList, flowId);

        Long startTimeStamp = parseTimeStamp(startDate);
        Long endTimeStamp = parseTimeStamp(endDate);
        StatsType type = StatsType.byJsonValue(statsType);

        List<VictoriaData> victoriaDataList = new ArrayList<>();
        for (String metric : metricList) {
            String metricName;
            if (StatsType.FLOW.equals(type)) {
                metricName = Metrics.flowMetricName(metric, appProps.getMetricPrefix());
            } else if (StatsType.METER.equals(type)) {
                metricName = Metrics.meterMetricName(metric, appProps.getMetricPrefix());
            } else {
                throw new InvalidRequestException("This statsType is unsupported");
            }

            if (StringUtils.isBlank(metricName)) {
                throw new InvalidRequestException(String.format("There is no such metric: %s", metricName));
            }
            Direction[] directions = (direction == null)
                    ? new Direction[]{Direction.FORWARD, Direction.REVERSE} : new Direction[]{direction};

            for (Direction dir : directions) {
                Map<String, String> queryParamLabelFilters = buildQueryParamLabelFilters(flowId, dir);
                RangeQueryParams rangeQueryParams = buildRangeQueryParams(startTimeStamp, endTimeStamp, step,
                        metricName, queryParamLabelFilters, true, true);
                victoriaDataList.add(buildVictoriaData(statsIntegrationService.getVictoriaStats(rangeQueryParams),
                        metricName));
            }
        }

        LOGGER.info("Received the following metrics responses: {}", victoriaDataList);
        return victoriaDataList;
    }

    /**
     * Retrieves Victoria Flow Path statistics data based on the provided request parameters.
     *
     * @param statsReq The request parameters for querying Victoria Flow Path statistics.
     * @return A list of VictoriaData objects representing the queried statistics.
     * @throws InvalidRequestException if the request parameters are invalid or if there are issues with the query.
     */
    public List<VictoriaData> getVictoriaStats(VictoriaStatsReq statsReq) throws InvalidRequestException {
        validateRequestParameters(statsReq.getStartDate(), statsReq.getStatsType(), statsReq.getMetrics());

        Long startTimeStamp = parseTimeStamp(statsReq.getStartDate());
        Long endTimeStamp = Optional.ofNullable(parseTimeStamp(statsReq.getEndDate()))
                .orElse(System.currentTimeMillis() / 1000);

        List<VictoriaData> victoriaDataList = new ArrayList<>();
        List<String> metricNameList = new ArrayList<>();

        if (CollectionUtils.isNotEmpty(statsReq.getMetrics())) {
            statsReq.getMetrics().forEach(metric ->
                    metricNameList.addAll(
                            getMetricByMetricEndingAngStatsType(
                                    StatsType.byJsonValue(statsReq.getStatsType()), metric)));
        } else {
            metricNameList.addAll(getMetricByStatsType(StatsType.byJsonValue(statsReq.getStatsType())));
        }

        for (String metricName : metricNameList) {
            if (StringUtils.isBlank(metricName)) {
                throw new InvalidRequestException(String.format("There is no such metric: %s", metricName));
            }
            boolean useRate = !metricName.equals(SWITCH_STATE.getMetricName(appProps.getMetricPrefix()));
            boolean useSum = useRate;
            if (metricName.equals(ISL_LATENCY.getMetricName(appProps.getMetricPrefix()))
                    || metricName.equals(ISL_RTT.getMetricName(appProps.getMetricPrefix()))) {
                useRate = false;
            }
            RangeQueryParams rangeQueryParams = buildRangeQueryParams(startTimeStamp, endTimeStamp, statsReq.getStep(),
                    metricName, statsReq.getLabels(), useRate, useSum);
            victoriaDataList.addAll(buildVictoriaDataList(statsIntegrationService.getVictoriaStats(rangeQueryParams),
                    metricName));
        }
        LOGGER.debug("Received the following metrics responses: {}", victoriaDataList);
        return victoriaDataList;
    }

    /**
     * Retrieve statistics for switch ports.
     *
     * @param statsReq The request object containing statistics parameters.
     * @return A list of PortInfo objects representing switch port statistics.
     * @throws InvalidRequestException If the request is invalid or malformed.
     */
    public List<PortInfo> getSwitchPortsStats(VictoriaStatsReq statsReq) throws InvalidRequestException {
        List<VictoriaData> victoriaDataList = getVictoriaStats(statsReq);
        String switchId = statsReq.getLabels().get("switchid");
        List<PortInfo> portStats = getSwitchPortStatsReport(victoriaDataList, switchId);
        if (!storeService.getSwitchStoreConfig().getUrls().isEmpty()) {
            try {
                List<Port> inventoryPorts = switchStoreService
                        .getSwitchPort(IoUtil.switchCodeToSwitchId(switchId));
                processInventoryPorts(portStats, inventoryPorts);
            } catch (Exception ex) {
                LOGGER.error("Error occurred while retriving switch ports stats for inventory", ex);
            }

        }
        return portStats;
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
                    PortConverter.appendInventoryInfo(portInfo, port);
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
                    PortConverter.toPortInfo(portInfoObj, port);
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
     * @param switchPortStats the list
     * @return the ports stat
     */
    private List<PortInfo> getSwitchPortStatsReport(List<VictoriaData> switchPortStats, String switchId) {
        Map<String, Map<String, Double>> portStatsByPortNo = new HashMap<String, Map<String, Double>>();
        for (VictoriaData stats : switchPortStats) {
            String port = stats.getTags().get("port");

            if (Integer.parseInt(port) > 0) {
                if (!portStatsByPortNo.containsKey(port)) {
                    portStatsByPortNo.put(port, new HashMap<String, Double>());
                }
                portStatsByPortNo.get(port).put(
                        stats.getMetric().replace(appProps.getMetricPrefix() + "switch.", ""),
                        getLastValue(stats.getTimeToValueMap()));
            }
        }

        return getIslPorts(portStatsByPortNo, switchId);
    }

    /**
     * Calculate highest value.
     *
     * @param dps the dps
     * @return the double
     */
    private double getLastValue(LinkedHashMap<Long, Double> dps) {
        double lastValue = 0.0;
        if (!dps.isEmpty()) {
            long lastTimeStamp = 0;
            for (Map.Entry<Long, Double> entry : dps.entrySet()) {
                Long timeStamp = entry.getKey();
                if (timeStamp > lastTimeStamp) {
                    lastTimeStamp = timeStamp;
                }
            }
            lastValue = BigDecimal.valueOf(dps.get(lastTimeStamp)).setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        }
        return lastValue;
    }

    /**
     * Sets the isl ports.
     *
     * @param portStatsByPortNo the port infos
     * @param switchid          the switchid
     * @return the list
     */
    private List<PortInfo> getIslPorts(final Map<String, Map<String, Double>> portStatsByPortNo, String switchid) {
        List<PortInfo> portInfos = buildPortInfos(portStatsByPortNo);
        String switchIdentifier = IoUtil.switchCodeToSwitchId(switchid);
        List<SwitchLogicalPort> switchLogicalPorts = switchIntegrationService.getLogicalPort(switchIdentifier);
        if (isNotEmpty(switchLogicalPorts)) {
            for (SwitchLogicalPort logicalPort : switchLogicalPorts) {
                for (String portNumber : logicalPort.getPortNumbers()) {
                    for (PortInfo portInfo : portInfos) {
                        if (portInfo.getPortNumber().equals(logicalPort.getLogicalPortNumber())) {
                            portInfo.setLogicalPort(true);
                            portInfo.setAssignmenttype("PORT");
                            portInfo.setPortNumbers(logicalPort.getPortNumbers());
                        } else if (portInfo.getPortNumber().equals(portNumber)) {
                            portInfo.setAssignmenttype("LAG_GROUP");
                            portInfo.setLogicalGroupName(logicalPort.getLogicalPortNumber());
                            portInfo.setLogicalPort(false);
                        }
                    }
                }
            }
        }
        List<IslLink> islLinkPorts = switchIntegrationService.getIslLinkPortsInfo(null);
        String switchIdInfo = null;
        if (islLinkPorts != null) {
            for (IslLink islLink : islLinkPorts) {
                for (IslPath islPath : islLink.getPath()) {
                    switchIdInfo = ("SW" + islPath.getSwitchId().replaceAll(":", "")).toUpperCase();
                    if (switchIdInfo.equals(switchid)) {
                        for (PortInfo portInfo : portInfos) {
                            if (portInfo.getPortNumber().equals(islPath.getPortNo().toString())) {
                                portInfo.setAssignmenttype("ISL");
                            }
                        }
                    }
                }
            }
        }
        return portInfos;
    }

    private List<PortInfo> buildPortInfos(final Map<String, Map<String, Double>> portStatsByPortNo) {
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

    private List<VictoriaData> buildVictoriaDataList(VictoriaDbRes dbData, String metricName) {
        List<VictoriaData> result = new ArrayList<>();
        if (dbData.getData() == null) {
            return result;
        }

        dbData.getData().getResult().stream()
                .map(metricValues -> buildVictoriaData(dbData, metricName, metricValues))
                .forEach(result::add);
        return result;
    }

    private VictoriaData buildVictoriaData(VictoriaDbRes dbData, String metricName) {
        MetricValues metricValues = null;
        if (dbData.getData() != null && isNotEmpty(dbData.getData().getResult())) {
            metricValues = dbData.getData().getResult().get(0);
        }
        return buildVictoriaData(dbData, metricName, metricValues);
    }

    private VictoriaData buildVictoriaData(VictoriaDbRes dbData, String metricName, MetricValues metricValues) {
        LinkedHashMap<Long, Double> timeToValueMap = new LinkedHashMap<>();
        Map<String, String> tags = new HashMap<>();

        if (metricValues != null) {
            tags = metricValues.getTags();
            metricValues.getValues()
                    .forEach(timeToValue ->
                            timeToValueMap.put(Long.parseLong(timeToValue[0]), Double.valueOf(timeToValue[1])));
        }
        return VictoriaData.builder()
                .tags(tags)
                .metric(metricName)
                .timeToValueMap(timeToValueMap)
                .status(dbData.getStatus())
                .error(dbData.getError())
                .errorType(dbData.getErrorType())
                .build();
    }

    private Map<String, String> buildQueryParamLabelFilters(String flowId, Direction direction) {
        Map<String, String> queryParamLabelFilters = new LinkedHashMap<>();
        queryParamLabelFilters.put("flowid", flowId);
        queryParamLabelFilters.put("direction", direction.getDisplayName());
        return queryParamLabelFilters;
    }


    private void validateRequestParameters(String startDate, List<String> metric, String flowId)
            throws InvalidRequestException {
        if (StringUtils.isBlank(startDate) || CollectionUtils.isEmpty(metric) || StringUtils.isBlank(flowId)) {
            throw new InvalidRequestException("startDate, metric, and flowid must not be null or empty");
        }
    }

    private void validateRequestParameters(String startDate, String statsType, List<String> metrics)
            throws InvalidRequestException {
        if (StringUtils.isBlank(startDate)) {
            throw new InvalidRequestException("Empty startDate");
        }
        if ((CollectionUtils.isEmpty(metrics) && StringUtils.isBlank(statsType))) {
            throw new InvalidRequestException("Metric list and statsType can not be null or empty at the same time");
        }
    }

    private Long parseTimeStamp(String date) throws InvalidRequestException {
        if (StringUtils.isBlank(date)) {
            return null;
        }
        try {
            return convertToTimeStamp(date, dateTimeFormatter);
        } catch (DateTimeParseException e) {
            throw new InvalidRequestException("Date wrong format, should be: 'yyyy-MM-dd-HH:mm:ss' or empty", e);
        }
    }

    private Long convertToTimeStamp(String timeString, DateTimeFormatter formatter) throws DateTimeParseException {
        LocalDateTime localDateTime = LocalDateTime.parse(timeString, formatter);
        return localDateTime.toEpochSecond(ZoneOffset.UTC);
    }

    private List<String> getMetricByStatsType(StatsType statsType) {
        return getMetricByMetricEndingAngStatsType(statsType, null);
    }

    private List<String> getMetricByMetricEndingAngStatsType(StatsType statsType, String metric) {
        List<String> metricList = new ArrayList<>();
        if (statsType == null) {
            String fullMetricName = Metrics.getFullMetricNameByMetricName(metric, appProps.getMetricPrefix());
            if (fullMetricName != null) {
                metricList.add(fullMetricName);
            }
        } else if (statsType.equals(StatsType.PORT)) {
            metricList = Metrics.switchValue(metric, appProps.getMetricPrefix());
        } else if (statsType.equals(StatsType.FLOW)) {
            metricList = Metrics.flowValue(metric, true, appProps.getMetricPrefix());
        } else if (statsType.equals(StatsType.ISL)) {
            metricList = Metrics.switchValue(metric, appProps.getMetricPrefix());
        } else if (statsType.equals(StatsType.ISL_LOSS_PACKET)) {
            metricList = Metrics.switchValue(metric, appProps.getMetricPrefix());
        } else if (statsType.equals(StatsType.FLOW_LOSS_PACKET)) {
            metricList = Metrics.flowValue("packets", false, appProps.getMetricPrefix());
            metricList.addAll(Metrics.flowValue(metric, false, appProps.getMetricPrefix()));
        } else if (statsType.equals(StatsType.FLOW_RAW_PACKET)) {
            metricList = Metrics.flowRawValue(metric, appProps.getMetricPrefix());
        } else if (statsType.equals(StatsType.SWITCH_PORT)) {
            metricList = Metrics.getStartsWith("Switch_", appProps.getMetricPrefix());
        } else if (statsType.equals(StatsType.METER)) {
            metricList = Metrics.meterValue(metric, appProps.getMetricPrefix());
        }
        return metricList;
    }
}
