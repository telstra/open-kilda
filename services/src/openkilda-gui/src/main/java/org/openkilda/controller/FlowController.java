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
import org.openkilda.integration.model.Flow;
import org.openkilda.integration.model.FlowStatus;
import org.openkilda.integration.model.response.FlowPayload;
import org.openkilda.log.ActivityLogger;
import org.openkilda.log.constants.ActivityType;
import org.openkilda.model.FlowCount;
import org.openkilda.model.FlowInfo;
import org.openkilda.model.FlowPath;
import org.openkilda.service.FlowService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

import org.usermanagement.model.UserInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

/**
 * The Class FlowController.
 *
 * @author Gaurav Chugh
 */
@Controller
@RequestMapping(value = "/flows")
public class FlowController extends BaseController {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlowController.class);

    @Autowired
    private FlowService flowService;

    @Autowired
    private ActivityLogger activityLogger;

    @Autowired
    private ServerContext serverContext;

    /**
     * Return to flows view.
     *
     * @param request is HttpServletRequest with request information
     * @return flows view if called with valid user session.
     */
    @RequestMapping
    @Permissions(values = { IConstants.Permission.MENU_FLOWS })
    public ModelAndView flowList(final HttpServletRequest request) {
        return validateAndRedirect(request, IConstants.View.FLOW_LIST);
    }

    /**
     * Return to flow details view.
     *
     * @param request is HttpServletRequest with request information
     * @return flow details view if called with valid user session.
     */
    @RequestMapping(value = "/details")
    public ModelAndView flowDetails(final HttpServletRequest request) {
        return validateAndRedirect(request, IConstants.View.FLOW_DETAILS);
    }


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
    public @ResponseBody List<FlowInfo> getFlows() {
        return flowService.getAllFlows();
    }

    /**
     * Returns flow path with all nodes/switches exists in provided flow.
     *
     * @param flowId id of flow path requested.
     * @return flow path with all nodes/switches exists in provided flow
     */
    @RequestMapping(value = "/path/{flowId}", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody FlowPayload getFlowPath(@PathVariable final String flowId) {
        LOGGER.info("[getFlowPath] - start. Flow id: " + flowId);
        return flowService.getFlowPath(flowId);
    }

    /**
     * Re route the flow and returns flow path with all nodes/switches exists in provided flow.
     *
     * @param flowId id of reroute requested.
     * @return reroute flow of new flow path with all nodes/switches exist
     */
    @RequestMapping(value = "/{flowId}/reroute", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody FlowPath rerouteFlow(@PathVariable final String flowId) {
        activityLogger.log(ActivityType.FLOW_REROUTE, flowId);
        LOGGER.info("[rerouteFlow] - start. Flow id: " + flowId);
        return flowService.rerouteFlow(flowId);
    }

    /**
     * Validate the flow.
     *
     * @param flowId id of validate flow requested.
     * @return validate flow
     */
    @RequestMapping(value = "/{flowId}/validate", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody String validateFlow(@PathVariable final String flowId) {
        activityLogger.log(ActivityType.FLOW_VALIDATE, flowId);
        LOGGER.info("[validateFlow] - start. Flow id: " + flowId);
        return flowService.validateFlow(flowId);
    }

    /**
     * Get flow by Id.
     *
     * @param flowId id of flow requested.
     * @return flow
     */
    @RequestMapping(value = "/{flowId}", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody Flow getFlowById(@PathVariable final String flowId) {
        LOGGER.info("[getFlowById] - start. Flow id: " + flowId);
        return flowService.getFlowById(flowId);
    }

    /**
     * Get flow Status by Id.
     *
     * @param flowId id of flow requested.
     * @return flow
     */
    @RequestMapping(value = "/{flowId}/status", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody FlowStatus getFlowStatusById(@PathVariable final String flowId) {
        LOGGER.info("[getFlowStatusById] - start. Flow id: " + flowId);
        return flowService.getFlowStatusById(flowId);
    }

    /**
     * Creates the flow.
     *
     * @param flow the flow
     * @return the flow
     */
    @RequestMapping(method = RequestMethod.PUT)
    @ResponseStatus(HttpStatus.CREATED)
    @Permissions(values = { IConstants.Permission.FW_FLOW_CREATE })
    public @ResponseBody Flow createFlow(@RequestBody final Flow flow) {
        LOGGER.info("[createFlow] - start. Flow id: " + flow.getId());
        return flowService.createFlow(flow);
    }

    /**
     * Update flow.
     *
     * @param flowId the flow id
     * @param flow the flow
     * @return the flow
     */
    @RequestMapping(value = "/{flowId}", method = RequestMethod.PUT)
    @ResponseStatus(HttpStatus.CREATED)
    @Permissions(values = { IConstants.Permission.FW_FLOW_UPDATE })
    public @ResponseBody Flow updateFlow(@PathVariable("flowId") final String flowId, @RequestBody final Flow flow) {
        LOGGER.info("[updateFlow] - start. Flow id: " + flowId);
        return flowService.updateFlow(flowId, flow);
    }

    /**
     * Delete flow.
     *
     * @param userInfo the user info
     * @param flowId the flow id
     * @return the flow
     */
    @RequestMapping(value = "/{flowId}", method = RequestMethod.DELETE)
    @ResponseStatus(HttpStatus.OK)
    @Permissions(values = { IConstants.Permission.FW_FLOW_DELETE })
    @ResponseBody
    public Flow deleteFlow(@RequestBody final UserInfo userInfo,
            @PathVariable("flowId") final String flowId) {
        LOGGER.info("[deleteFlow] - start. Flow id: " + flowId);
        if (serverContext.getRequestContext() != null) {
            userInfo.setUserId(serverContext.getRequestContext().getUserId());
        }
        return flowService.deleteFlow(flowId, userInfo);
    }
}
