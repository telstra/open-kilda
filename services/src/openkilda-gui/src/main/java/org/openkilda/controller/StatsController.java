package org.openkilda.controller;

import java.util.List;

import org.apache.log4j.Logger;
import org.openkilda.auth.model.Permissions;
import org.openkilda.constants.IConstants;
import org.openkilda.constants.IConstants.Metrics;
import org.openkilda.service.StatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
@RequestMapping(value = "/stats")
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
     * @param flowid the flowid
     * @param startDate the start date
     * @param endDate the end date
     * @param downsample the downsample
     * @return the flow stats
     * @throws Exception the exception
     */
    @RequestMapping(
            value = "isl/{srcSwitch}/{srcPort}/{dstSwitch}/{dstPort}/{startDate}/{endDate}/{downsample}/{metric}",
            method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = {IConstants.Permission.MENU_ISL})
    public @ResponseBody String getIslStats(@PathVariable String srcSwitch,
            @PathVariable String srcPort, @PathVariable String dstSwitch,
            @PathVariable String dstPort, @PathVariable String startDate,
            @PathVariable String endDate, @PathVariable String downsample,
            @PathVariable String metric) throws Exception {

        LOGGER.info("Inside StatsController method getIslStats ");
        return statsService.getSwitchIslStats(startDate, endDate, downsample, srcSwitch,
                srcPort, dstSwitch, dstPort, metric);

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
    @RequestMapping(
            value = "switchid/{switchid}/port/{port}/{startDate}/{endDate}/{downsample}/{metric}",
            method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = {IConstants.Permission.MENU_SWITCHES})
    public @ResponseBody String getPortStats(@PathVariable String switchid,
            @PathVariable String port, @PathVariable String startDate,
            @PathVariable String endDate, @PathVariable String downsample,
            @PathVariable String metric) throws Exception {

        LOGGER.info("Inside StatsController method getPortStats ");
        return statsService.getSwitchPortStats(startDate, endDate, downsample, switchid, port,
                metric);

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
    @Permissions(values = {IConstants.Permission.MENU_FLOWS})
    public @ResponseBody String getFlowStats(@PathVariable String flowid,
            @PathVariable String startDate, @PathVariable String endDate,
            @PathVariable String downsample, @PathVariable String metric) throws Exception {

        LOGGER.info("Inside StatsController method getFlowStats ");
        return statsService.getFlowStats(startDate, endDate, downsample, flowid, metric);
    }

}
