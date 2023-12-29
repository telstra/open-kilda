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
import org.openkilda.model.PortInfo;
import org.openkilda.model.VictoriaStatsReq;
import org.openkilda.model.victoria.Status;
import org.openkilda.model.victoria.VictoriaData;
import org.openkilda.model.victoria.VictoriaStatsRes;
import org.openkilda.service.StatsService;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(StatsController.class);
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
     * Retrieves Victoria statistics data for a specific flow based on the provided parameters.
     *
     * @param flowId    The ID of the flow for which statistics are retrieved.
     * @param startDate The start date of the data retrieval period.
     * @param endDate   The end date of the data retrieval period.
     * @param step      The time step for data aggregation.
     * @param metric    A list of metrics for which statistics are retrieved.
     * @param direction The direction of the flow data ('forward' or 'reverse'). Optional.
     * @return A {@link ResponseEntity} containing a {@link VictoriaStatsRes} object with the retrieved statistics.
     * @see VictoriaStatsRes
     */
    @RequestMapping(value = "flowgraph/{statsType}",
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
        //TODO find a way to unite this controller method with the commonVictoriaStats method.
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

            res = convertToVictoriaStatsRes(victoriaResult);
        } catch (InvalidRequestException e) {
            res = buildVictoriaBadRequestErrorRes(e.getMessage());
        }
        return res;
    }

    /**
     * Gets the flow path stat from victoria db.
     *
     * @param victoriaStatsReq the flow path stat (flowid , switchId, startDate, endDate, labels)
     * @return the flow path stat
     */
    @RequestMapping(value = "common", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = {IConstants.Permission.MENU_FLOWS})
    @ResponseBody
    public ResponseEntity<VictoriaStatsRes> commonVictoriaStats(@RequestBody VictoriaStatsReq victoriaStatsReq) {

        LOGGER.info(String.format("Get flow path stat request: %s", victoriaStatsReq));
        ResponseEntity<VictoriaStatsRes> res;
        try {
            List<VictoriaData> victoriaResult = statsService.getVictoriaStats(victoriaStatsReq);

            res = convertToVictoriaStatsRes(victoriaResult);
        } catch (InvalidRequestException e) {
            res = buildVictoriaBadRequestErrorRes(e.getMessage());
        }
        return res;
    }

    /**
     * Retrieves statistics for switch ports.
     * This method handles HTTP POST requests to fetch statistics for switch ports. It requires a valid
     * VictoriaStatsReq object in the request body to specify the statistics parameters.
     *
     * @param statsReq The VictoriaStatsReq object containing the statistics parameters.
     * @return A list of PortInfo objects representing the statistics for switch ports.
     * @throws InvalidRequestException If the request parameters are invalid or malformed.
     * @see VictoriaStatsReq
     * @see PortInfo
     * @see StatsService#getSwitchPortsStats(VictoriaStatsReq)
     */
    @RequestMapping(value = "switchports", method = RequestMethod.POST,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = {IConstants.Permission.MENU_SWITCHES})
    @ResponseBody
    public List<PortInfo> switchPortsStats(@RequestBody VictoriaStatsReq statsReq) throws InvalidRequestException {
        LOGGER.info("POST switch ports stat ");
        return statsService.getSwitchPortsStats(statsReq);
    }

    private ResponseEntity<VictoriaStatsRes> convertToVictoriaStatsRes(List<VictoriaData> victoriaResult) {
        ResponseEntity<VictoriaStatsRes> res;
        Optional<VictoriaData> errorData = victoriaResult.stream().filter(this::hasError).findFirst();

        if (errorData.isPresent()) {
            VictoriaData err = errorData.get();
            res = buildVictoriaBadRequestErrorRes(Integer.parseInt(err.getErrorType()), err.getError());
        } else {
            res = ResponseEntity.ok(VictoriaStatsRes.builder().status(SUCCESS)
                    .dataList(victoriaResult).build());
        }
        return res;
    }

    private boolean hasError(VictoriaData victoriaData) {
        return victoriaData != null && Status.ERROR.equals(victoriaData.getStatus());
    }

    private ResponseEntity<VictoriaStatsRes> buildVictoriaBadRequestErrorRes(String message) {
        return buildVictoriaBadRequestErrorRes(400, message);
    }

    private ResponseEntity<VictoriaStatsRes> buildVictoriaBadRequestErrorRes(int statusCode, String message) {
        return ResponseEntity.status(statusCode).body(VictoriaStatsRes.builder().message(message)
                .status(ERROR).build());
    }
}
