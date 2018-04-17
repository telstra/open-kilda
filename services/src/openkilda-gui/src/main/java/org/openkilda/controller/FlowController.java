package org.openkilda.controller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.openkilda.constants.IConstants;
import org.openkilda.integration.model.Flow;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

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

    /**
     * Return to flows view.
     *
     * @param request is HttpServletRequest with request information
     * @return flows view if called with valid user session.
     */
    @RequestMapping
    public ModelAndView flowList(final HttpServletRequest request) {
        LOGGER.info("[flowList] - start");
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
        LOGGER.info("[flowDetails] - start");
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
        LOGGER.info("[getFlowCount] - start");
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
        LOGGER.info("[getFlows] - start");
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
    public @ResponseBody FlowPath getFlowPath(@PathVariable final String flowId) {
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
        LOGGER.info("[rerouteFlow] - start. Flow id: " + flowId);
        return flowService.rerouteFlow(flowId);
    }

    /**
     * Validate the flow
     * 
     * @param flowId id of validate flow requested.
     * @return validate flow
     */
    @RequestMapping(value = "/{flowId}/validate", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody String validateFlow(@PathVariable final String flowId) {
        LOGGER.info("[validateFlow] - start. Flow id: " + flowId);
        return flowService.validateFlow(flowId);
    }

    /**
     * Get flow by Id
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
}
