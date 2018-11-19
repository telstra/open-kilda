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

import org.openkilda.auth.model.Permissions;
import org.openkilda.constants.IConstants;
import org.openkilda.constants.IConstants.Metrics;
import org.openkilda.model.FlowPathStats;
import org.openkilda.model.PortInfo;
import org.openkilda.service.StatsService;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/stats")
public class StatsController {

    private static final Logger LOGGER = Logger.getLogger(StatsController.class);

    @Autowired
    private StatsService statsService;

    /**
     * Gets the metric detail.
     *
     * @return the metric detail
     */
    @RequestMapping(value = "/metrics")
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody List<String> getMetricDetail() {
        return Metrics.list();
    }


    /**
     * Gets the isl stats.
     *
     * @param startDate the start date
     * @param endDate the end date
     * @param downsample the downsample
     * @return the flow stats
     * @throws Exception the exception
     */
    @RequestMapping(value =
            "isl/{srcSwitch}/{srcPort}/{dstSwitch}/{dstPort}/{startDate}/{endDate}/{downsample}/{metric}",
            method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = { IConstants.Permission.MENU_ISL })
    @ResponseBody
    public String getIslStats(@PathVariable String srcSwitch, @PathVariable String srcPort,
            @PathVariable String dstSwitch, @PathVariable String dstPort, @PathVariable String startDate,
            @PathVariable String endDate, @PathVariable String downsample, @PathVariable String metric)
            throws Exception {

        LOGGER.info("Inside StatsController method getIslStats ");
        return statsService.getSwitchIslStats(startDate, endDate, downsample, srcSwitch, srcPort, dstSwitch, dstPort,
                metric);

    }

    /**
     * Gets the port stats.
     *
     * @param switchid the switchid
     * @param port the port
     * @param startDate the start date
     * @param endDate the end date
     * @param downsample the downsample
     * @return the port stats
     * @throws Exception the exception
     */
    @RequestMapping(value = "switchid/{switchid}/port/{port}/{startDate}/{endDate}/{downsample}/{metric}",
            method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = { IConstants.Permission.MENU_SWITCHES })
    @ResponseBody
    public String getPortStats(@PathVariable String switchid, @PathVariable String port,
            @PathVariable String startDate, @PathVariable String endDate, @PathVariable String downsample,
            @PathVariable String metric) throws Exception {

        LOGGER.info("Inside StatsController method getPortStats ");
        return statsService.getSwitchPortStats(startDate, endDate, downsample, switchid, port, metric);

    }

    /**
     * Gets the flow stats.
     *
     * @param flowid the flowid
     * @param startDate the start date
     * @param endDate the end date
     * @param downsample the downsample
     * @return the flow stats
     * @throws Exception the exception
     */
    @RequestMapping(value = "flowid/{flowid}/{startDate}/{endDate}/{downsample}/{metric}",
            method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = { IConstants.Permission.MENU_FLOWS })
    @ResponseBody
    public String getFlowStats(@PathVariable String flowid, @PathVariable String startDate,
            @PathVariable String endDate, @PathVariable String downsample, @PathVariable String metric)
            throws Exception {

        LOGGER.info("Inside StatsController method getFlowStats ");
        return statsService.getFlowStats(startDate, endDate, downsample, flowid, metric);
    }

    
    /**
     * Gets the switch isl loss packet stats.
     *
     * @param srcSwitch the src switch
     * @param srcPort the src port
     * @param dstSwitch the dst switch
     * @param dstPort the dst port
     * @param startDate the start date
     * @param endDate the end date
     * @param downsample the downsample
     * @param metric the metric
     * @return the isl loss packet stats
     * @throws Exception the exception
     */
    @RequestMapping(value =
            "isl/losspackets/{srcSwitch}/{srcPort}/{dstSwitch}/{dstPort}/{startDate}/{endDate}/{downsample}/{metric}",
            method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = { IConstants.Permission.MENU_ISL })
    @ResponseBody
    public String getIslLossPacketStats(@PathVariable String srcSwitch, @PathVariable String srcPort,
            @PathVariable String dstSwitch, @PathVariable String dstPort, @PathVariable String startDate,
            @PathVariable String endDate, @PathVariable String downsample, @PathVariable String metric)
            throws Exception {

        LOGGER.info("Inside StatsController method getIslLossPacketStats ");
        return statsService.getSwitchIslLossPacketStats(startDate, endDate, downsample, srcSwitch, srcPort, dstSwitch,
                dstPort, metric);
    }
    
    /**
     * Gets the flow loss packet stats.
     *
     * @param flowid the flowid
     * @param startDate the start date
     * @param endDate the end date
     * @param downsample the downsample
     * @param direction the direction
     * @return the flow loss packet stats
     * @throws Exception the exception
     */
    @RequestMapping(value = "flow/losspackets/{flowid}/{startDate}/{endDate}/{downsample}/{direction}",
            method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = { IConstants.Permission.MENU_FLOWS })
    @ResponseBody
    public String getFlowLossPacketStats(@PathVariable String flowid, @PathVariable String startDate,
            @PathVariable String endDate, @PathVariable String downsample, @PathVariable String direction)
            throws Exception {

        LOGGER.info("Inside StatsController method getFlowLossPacketStats ");
        return statsService.getFlowLossPacketStats(startDate, endDate, downsample, flowid, direction);
    }
    
    /**
     * Gets the flow path stat.
     *
     * @param flowPathStats the flow path stat (flowid , list of switchids, start date, end date)
     * @return the flow path stat
     * @throws Exception the exception
     */
    @RequestMapping(value = "flowpath", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = { IConstants.Permission.MENU_FLOWS })
    @ResponseBody
    public String getFlowPathStat(@RequestBody FlowPathStats flowPathStats) throws Exception {

        LOGGER.info("Inside StatsController method getFlowPathStat ");
        return statsService.getFlowPathStats(flowPathStats);
    }
    
    /**
     * Gets the switch ports stats.
     *
     * @param switchid the switchid
     * @param startDate the start date
     * @param endDate the end date
     * @param downsample the downsample
     * @return the switch ports stats
     * @throws Exception the exception
     */
    @RequestMapping(value = "switchports/{switchid}/{startDate}/{endDate}/{downsample}", method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = { IConstants.Permission.MENU_SWITCHES })
    @ResponseBody
    public List<PortInfo> getSwitchPortsStats(@PathVariable String switchid,
            @PathVariable String startDate, @PathVariable String endDate, @PathVariable String downsample)
            throws Exception {

        LOGGER.info("Inside StatsController method getSwitchPortsStats ");
        return statsService.getSwitchPortsStats(startDate, endDate, downsample, switchid);
    }
}
