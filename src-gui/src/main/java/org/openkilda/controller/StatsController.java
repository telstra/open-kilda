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

package org.openkilda.controller;

import static org.openkilda.model.victoria.Status.ERROR;
import static org.openkilda.model.victoria.Status.SUCCESS;

import org.openkilda.auth.model.Permissions;
import org.openkilda.config.ApplicationProperties;
import org.openkilda.constants.Direction;
import org.openkilda.constants.IConstants;
import org.openkilda.constants.Metrics;
import org.openkilda.constants.OpenTsDb;
import org.openkilda.exception.InvalidRequestException;
import org.openkilda.model.FlowPathStats;
import org.openkilda.model.PortInfo;
import org.openkilda.model.victoria.Status;
import org.openkilda.model.victoria.VictoriaData;
import org.openkilda.model.victoria.VictoriaStatsRes;
import org.openkilda.service.StatsService;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping(value = "/api/stats")
public class StatsController {

    private static final Logger LOGGER = Logger.getLogger(StatsController.class);

    private static final String VICTORIA_METRICS_URL_EMPTY = "Victoria Metrics DB URL has not been configured"
            + " for Openkilda-gui.";
    private static final String REQUIRED_START_DATE_ERROR = "startDate must not be null or empty.";
    private static final String STATS_TYPE_ERROR = "statsType path variable should not be empty or wrong.";
    private static final String REQUIRED_METRIC_ERROR = "metric must not be null or empty.";
    private static final String REQUIRED_FLOW_ID_ERROR = "flowid must not be null or empty.";
    private static final String INVALID_DIRECTION_ERROR = "Invalid direction parameter. "
            + "Allowed values are 'forward' or 'reverse'.";

    private final StatsService statsService;
    private final ApplicationProperties applicationProperties;

    public StatsController(StatsService statsService, ApplicationProperties applicationProperties) {
        this.statsService = statsService;
        this.applicationProperties = applicationProperties;
    }


    /**
     * Gets the metric detail.
     *
     * @return the metric detail
     */
    @RequestMapping(value = "/metrics")
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody List<String> getMetricDetail() {
        return Metrics.list(applicationProperties.getMetricPrefix());
    }


    /**
     * Gets the isl stats.
     *
     * @param startDate  the start date
     * @param endDate    the end date
     * @param downsample the downsample
     * @return the flow stats
     */
    @RequestMapping(value =
            "isl/{srcSwitch}/{srcPort}/{dstSwitch}/{dstPort}/{startDate}/{endDate}/{downsample}/{metric}",
            method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = {IConstants.Permission.MENU_ISL})
    @ResponseBody
    public String getIslStats(@PathVariable String srcSwitch, @PathVariable String srcPort,
                              @PathVariable String dstSwitch, @PathVariable String dstPort,
                              @PathVariable String startDate, @PathVariable String endDate,
                              @PathVariable String downsample, @PathVariable String metric) {

        LOGGER.info("Get stat for Isl");
        return statsService.getSwitchIslStats(startDate, endDate, downsample, srcSwitch, srcPort, dstSwitch, dstPort,
                metric);

    }

    /**
     * Gets the port stats.
     *
     * @param switchid   the switchid
     * @param port       the port
     * @param startDate  the start date
     * @param endDate    the end date
     * @param downsample the downsample
     * @return the port stats
     */
    @RequestMapping(value = "switchid/{switchid}/port/{port}/{startDate}/{endDate}/{downsample}/{metric}",
            method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = {IConstants.Permission.MENU_SWITCHES})
    @ResponseBody
    public String getPortStats(@PathVariable String switchid, @PathVariable String port,
                               @PathVariable String startDate, @PathVariable String endDate,
                               @PathVariable String downsample, @PathVariable String metric) {

        LOGGER.info("Get stat for port");
        return statsService.getSwitchPortStats(startDate, endDate, downsample, switchid, port, metric);

    }

    /**
     * Gets the flow stats.
     *
     * @param flowid     the flowid
     * @param startDate  the start date
     * @param endDate    the end date
     * @param downsample the downsample
     * @return the flow stats
     */
    @RequestMapping(value = "flowid/{flowid}/{startDate}/{endDate}/{downsample}/{metric}",
            method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = {IConstants.Permission.MENU_FLOWS})
    @ResponseBody
    public String getFlowStats(@PathVariable String flowid, @PathVariable String startDate,
                               @PathVariable String endDate, @PathVariable String downsample,
                               @PathVariable String metric) {

        LOGGER.info("Get stat for flow");
        return statsService.getFlowStats(startDate, endDate, downsample, flowid, metric);
    }

    /**
     * Retrieves Victoria statistics data for a specific flow based on the provided parameters.
     *
     * @param flowId The ID of the flow for which statistics are retrieved.
     * @param startDate The start date of the data retrieval period.
     * @param endDate The end date of the data retrieval period.
     * @param step The time step for data aggregation.
     * @param metric A list of metrics for which statistics are retrieved.
     * @param direction The direction of the flow data ('forward' or 'reverse'). Optional.
     * @return A {@link ResponseEntity} containing a {@link VictoriaStatsRes} object with the retrieved statistics.
     * @see VictoriaStatsRes
     */
    @RequestMapping(value = "victoria/{statsType}",
            method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = {IConstants.Permission.MENU_FLOWS})
    @ResponseBody
    public ResponseEntity<VictoriaStatsRes> getFlowVictoriaStats(@PathVariable String statsType,
                                                                 @RequestParam String flowId,
                                                                 @RequestParam String startDate,
                                                                 @RequestParam String endDate,
                                                                 @RequestParam String step,
                                                                 @RequestParam List<String> metric,
                                                                 @RequestParam(required = false) String direction) {

        if (StringUtils.isBlank(applicationProperties.getVictoriaBaseUrl())
                || applicationProperties.getVictoriaBaseUrl().contains("://:/prometheus")) {
            return buildServiceUnavailableRes();
        }

        LOGGER.info("Get victoria stat for flow");
        if (StringUtils.isBlank(startDate)) {
            return buildVictoriaBadRequestErrorRes(REQUIRED_START_DATE_ERROR);
        }
        if (OpenTsDb.StatsType.byJsonValue(statsType) == null) {
            return buildVictoriaBadRequestErrorRes(STATS_TYPE_ERROR);
        }
        if (CollectionUtils.isEmpty(metric)) {
            return buildVictoriaBadRequestErrorRes(REQUIRED_METRIC_ERROR);
        }
        if (StringUtils.isBlank(flowId)) {
            return buildVictoriaBadRequestErrorRes(REQUIRED_FLOW_ID_ERROR);
        }
        if (StringUtils.isNotBlank(direction) && !Direction.isValidDisplayName(direction)) {
            return buildVictoriaBadRequestErrorRes(INVALID_DIRECTION_ERROR);
        }
        ResponseEntity<VictoriaStatsRes> res;
        try {
            List<VictoriaData> victoriaResult =
                    statsService.getTransformedFlowVictoriaStats(statsType, startDate, endDate, step,
                            flowId, metric, Direction.byDisplayName(direction));

            Optional<VictoriaData> errorData = victoriaResult.stream().filter(this::hasError).findFirst();

            if (errorData.isPresent()) {
                VictoriaData err = errorData.get();
                res = buildVictoriaBadRequestErrorRes(Integer.parseInt(err.getErrorType()), err.getError());
            } else {
                res = ResponseEntity.ok(VictoriaStatsRes.builder().status(SUCCESS)
                        .dataList(victoriaResult).build());
            }
        } catch (InvalidRequestException e) {
            res = buildVictoriaBadRequestErrorRes(e.getMessage());
        }
        return res;
    }

    /**
     * Gets the switch isl loss packet stats.
     *
     * @param srcSwitch  the src switch
     * @param srcPort    the src port
     * @param dstSwitch  the dst switch
     * @param dstPort    the dst port
     * @param startDate  the start date
     * @param endDate    the end date
     * @param downsample the downsample
     * @param metric     the metric
     * @return the isl loss packet stats
     */
    @RequestMapping(value =
            "isl/losspackets/{srcSwitch}/{srcPort}/{dstSwitch}/{dstPort}/{startDate}/{endDate}/{downsample}/{metric}",
            method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = {IConstants.Permission.MENU_ISL})
    @ResponseBody
    public String getIslLossPacketStats(@PathVariable String srcSwitch, @PathVariable String srcPort,
                                        @PathVariable String dstSwitch, @PathVariable String dstPort,
                                        @PathVariable String startDate, @PathVariable String endDate,
                                        @PathVariable String downsample, @PathVariable String metric) {

        LOGGER.info("Get stat of Isl loss packet");
        return statsService.getSwitchIslLossPacketStats(startDate, endDate, downsample, srcSwitch, srcPort, dstSwitch,
                dstPort, metric);
    }

    /**
     * Gets the flow loss packet stats.
     *
     * @param flowid     the flowid
     * @param startDate  the start date
     * @param endDate    the end date
     * @param downsample the downsample
     * @param direction  the direction
     * @return the flow loss packet stats
     */
    @RequestMapping(value = "flow/losspackets/{flowid}/{startDate}/{endDate}/{downsample}/{direction}",
            method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = {IConstants.Permission.MENU_FLOWS})
    @ResponseBody
    public String getFlowLossPacketStats(@PathVariable String flowid, @PathVariable String startDate,
                                         @PathVariable String endDate, @PathVariable String downsample,
                                         @PathVariable String direction) {

        LOGGER.info("Get stat of flow loss packet");
        return statsService.getFlowLossPacketStats(startDate, endDate, downsample, flowid, direction);
    }

    /**
     * Gets the flow path stat.
     *
     * @param flowPathStats the flow path stat (flowid , list of switchids, start date, end date)
     * @return the flow path stat
     */
    @RequestMapping(value = "flowpath", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = {IConstants.Permission.MENU_FLOWS})
    @ResponseBody
    public String getFlowPathStat(@RequestBody FlowPathStats flowPathStats) {

        LOGGER.info("Get flow path stat ");
        return statsService.getFlowPathStats(flowPathStats);
    }

    /**
     * Gets the switch ports stats.
     *
     * @param switchid   the switchid
     * @param startDate  the start date
     * @param endDate    the end date
     * @param downsample the downsample
     * @return the switch ports stats
     */
    @RequestMapping(value = "switchports/{switchid}/{startDate}/{endDate}/{downsample}", method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = {IConstants.Permission.MENU_SWITCHES})
    @ResponseBody
    public List<PortInfo> getSwitchPortsStats(@PathVariable String switchid, @PathVariable String startDate,
                                              @PathVariable String endDate, @PathVariable String downsample) {

        LOGGER.info("Get switch ports stat ");
        return statsService.getSwitchPortsStats(startDate, endDate, downsample, switchid);
    }


    /**
     * Gets the meter stats.
     *
     * @param flowid     the flowid
     * @param startDate  the start date
     * @param endDate    the end date
     * @param downsample the downsample
     * @param direction  the direction
     * @param metric     the metric
     * @return the meter stats
     */
    @RequestMapping(value = "meter/{flowid}/{startDate}/{endDate}/{downsample}/{metric}/{direction}",
            method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = {IConstants.Permission.MENU_FLOWS})
    @ResponseBody
    public String getMeterStats(@PathVariable String flowid, @PathVariable String startDate,
                                @PathVariable String endDate, @PathVariable String downsample,
                                @PathVariable String metric, @PathVariable String direction) {

        LOGGER.info("Get stat for meter");
        return statsService.getMeterStats(startDate, endDate, downsample, flowid, metric, direction);
    }

    private boolean hasError(VictoriaData victoriaData) {
        return victoriaData != null && Status.ERROR.equals(victoriaData.getStatus());
    }

    private ResponseEntity<VictoriaStatsRes> buildServiceUnavailableRes() {
        return buildVictoriaBadRequestErrorRes(503, StatsController.VICTORIA_METRICS_URL_EMPTY);
    }

    private ResponseEntity<VictoriaStatsRes> buildVictoriaBadRequestErrorRes(String message) {
        return buildVictoriaBadRequestErrorRes(400, message);
    }

    private ResponseEntity<VictoriaStatsRes> buildVictoriaBadRequestErrorRes(int statusCode, String message) {
        return ResponseEntity.status(statusCode).body(VictoriaStatsRes.builder().message(message)
                .status(ERROR).build());
    }
}
