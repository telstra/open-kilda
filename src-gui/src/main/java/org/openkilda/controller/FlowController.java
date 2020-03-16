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

import org.openkilda.auth.context.ServerContext;
import org.openkilda.auth.model.Permissions;
import org.openkilda.constants.IConstants;
import org.openkilda.exception.NoDataFoundException;
import org.openkilda.integration.model.Flow;
import org.openkilda.integration.model.FlowStatus;
import org.openkilda.integration.model.response.FlowPayload;
import org.openkilda.log.ActivityLogger;
import org.openkilda.log.constants.ActivityType;
import org.openkilda.model.FlowCount;
import org.openkilda.model.FlowInfo;
import org.openkilda.model.FlowPath;
import org.openkilda.model.Status;
import org.openkilda.service.FlowService;
import org.openkilda.utility.StringUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.usermanagement.model.UserInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
/**
 * The Class FlowController.
 *
 * @author Gaurav Chugh
 */

@RestController
@RequestMapping(value = "/api/flows")
public class FlowController extends BaseController {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlowController.class);

    @Autowired
    private FlowService flowService;

    @Autowired
    private ActivityLogger activityLogger;

    @Autowired
    private ServerContext serverContext;

    /**
     * Returns information of no of flow between any two switches.
     *
     * @return no of flow between any two switches.
     */
    @RequestMapping(value = "/count", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody Collection<FlowCount> getFlowCount() {
        Collection<FlowCount> flowsInfo = new ArrayList<FlowCount>();
        List<Flow> flows = flowService.getAllFlowList();
        if (flows != null) {
            flowsInfo = flowService.getFlowsCount(flows);
        }
        return flowsInfo;
    }

    /**
     * Returns all flows exists in the system.
     *
     * @return all flows exists in the system.
     */
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody List<FlowInfo> getFlows(
            @RequestParam(name = "status", required = false) List<String> statuses,
            @RequestParam(name = "controller", required = false) boolean controller) {
        return flowService.getAllFlows(statuses, controller);
    }

    /**
     * Returns flow path with all nodes/switches exists in provided flow.
     *
     * @param flowId
     *            id of flow path requested.
     * @return flow path with all nodes/switches exists in provided flow
     */
    @RequestMapping(value = "/path/{flowId}", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody FlowPayload getFlowPath(@PathVariable final String flowId) {
        LOGGER.info("Get flow path. Flow id: '" + flowId + "'");
        return flowService.getFlowPath(flowId);
    }

    /**
     * Re route the flow and returns flow path with all nodes/switches exists in
     * provided flow.
     *
     * @param flowId
     *            id of reroute requested.
     * @return reroute flow of new flow path with all nodes/switches exist
     */
    @RequestMapping(value = "/{flowId}/reroute", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody FlowPath rerouteFlow(@PathVariable final String flowId) {
        activityLogger.log(ActivityType.FLOW_REROUTE, flowId);
        LOGGER.info("Reroute flow. Flow id: '" + flowId + "'");
        return flowService.rerouteFlow(flowId);
    }

    /**
     * Validate the flow.
     *
     * @param flowId
     *            id of validate flow requested.
     * @return validate flow
     */
    @RequestMapping(value = "/{flowId}/validate", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody String validateFlow(@PathVariable final String flowId) {
        activityLogger.log(ActivityType.FLOW_VALIDATE, flowId);
        LOGGER.info("Validate flow. Flow id: '" + flowId + "'");
        return flowService.validateFlow(flowId);
    }

    /**
     * Get flow by Id.
     *
     * @param flowId
     *            id of flow requested.
     * @return flowInfo
     */
    @RequestMapping(value = "/{flowId}", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody FlowInfo getFlowById(@PathVariable final String flowId,
            @RequestParam(name = "controller", required = false) boolean controller) {
        LOGGER.info("Get flow by id. Flow id: '" + flowId + "'");
        FlowInfo flowInfo = flowService.getFlowById(flowId, controller);
        if (flowInfo != null && StringUtil.isNullOrEmpty(flowInfo.getFlowid())) {
            throw new NoDataFoundException("No flow found");
        }
        return flowInfo;
    }

    /**
     * Get flow Status by Id.
     *
     * @param flowId
     *            id of flow requested.
     * @return flow
     */
    @RequestMapping(value = "/{flowId}/status", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody FlowStatus getFlowStatusById(@PathVariable final String flowId) {
        LOGGER.info("Get flow status by id. Flow id: '" + flowId + "'");
        return flowService.getFlowStatusById(flowId);
    }

    /**
     * Creates the flow.
     *
     * @param flow
     *            the flow
     * @return the flow
     */
    @RequestMapping(method = RequestMethod.PUT)
    @ResponseStatus(HttpStatus.CREATED)
    @Permissions(values = { IConstants.Permission.FW_FLOW_CREATE })
    public @ResponseBody Flow createFlow(@RequestBody final Flow flow) {
        LOGGER.info("Create flow. Flow id: '" + flow.getId() + "'");
        return flowService.createFlow(flow);
    }

    /**
     * Update flow.
     *
     * @param flowId
     *            the flow id
     * @param flow
     *            the flow
     * @return the flow
     */
    @RequestMapping(value = "/{flowId}", method = RequestMethod.PUT)
    @ResponseStatus(HttpStatus.CREATED)
    @Permissions(values = { IConstants.Permission.FW_FLOW_UPDATE })
    public @ResponseBody Flow updateFlow(@PathVariable("flowId") final String flowId, @RequestBody final Flow flow) {
        LOGGER.info("Update flow. Flow id: '" + flowId + "'");
        return flowService.updateFlow(flowId, flow);
    }

    /**
     * Delete flow.
     *
     * @param userInfo
     *            the user info
     * @param flowId
     *            the flow id
     * @return the flow
     */
    @RequestMapping(value = "/{flowId}", method = RequestMethod.DELETE)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = { IConstants.Permission.FW_FLOW_DELETE })
    @ResponseBody
    public Flow deleteFlow(@RequestBody final UserInfo userInfo, @PathVariable("flowId") final String flowId) {
        LOGGER.info("Delete flow. Flow id: '" + flowId + "'");
        if (serverContext.getRequestContext() != null) {
            userInfo.setUserId(serverContext.getRequestContext().getUserId());
        }
        return flowService.deleteFlow(flowId, userInfo);
    }

    /**
     * Validate the flow.
     *
     * @param flowId
     *            id of validate flow requested.
     * @return validate flow
     */
    @RequestMapping(value = "/{flowId}/sync", method = RequestMethod.PATCH)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = { IConstants.Permission.FW_FLOW_RESYNC })
    public @ResponseBody String resyncFlow(@PathVariable final String flowId) {
        LOGGER.info("Resync flow. Flow id: '" + flowId + "'");
        return flowService.resyncFlow(flowId);
    }

    /**
     * Returns statuses exists in the system.
     *
     * @return statuses exists in the system.
     */
    @RequestMapping(value = "/status", method = { RequestMethod.GET })
    public Set<String> getAllStatus() {
        LOGGER.info("Get all statuses. ");
        return flowService.getAllStatus();
    }

    /**
     * Get statuses exists in the system with cron.
     *
     */
    @Scheduled(fixedDelayString = "${status.cron.time}")
    public void getAllStatusWithCron() {
        LOGGER.info("Get all status cron. ");
        Status statuses = Status.INSTANCE;
        statuses.setStatuses(flowService.getAllStatus());
    }
    
    /**
     * Flow ping.
     *
     * @param flowId the flow id
     * @param flow the flow
     * @return the string
     */
    @RequestMapping(value = "/{flowId}/ping", method = RequestMethod.PUT)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = { IConstants.Permission.FW_FLOW_PING })
    public @ResponseBody String flowPing(@PathVariable final String flowId, @RequestBody final Flow flow) {
        LOGGER.info("Flow ping. Flow id: '" + flowId + "'");
        activityLogger.log(ActivityType.FLOW_PING, flowId);
        return flowService.flowPing(flowId, flow);
    }

}
