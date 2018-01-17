package org.openkilda.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.openkilda.constants.IConstants;
import org.openkilda.model.FlowInfo;
import org.openkilda.model.FlowPath;
import org.openkilda.model.response.FlowCount;
import org.openkilda.service.FlowService;

/**
 * The Class FlowController.
 *
 * @author Gaurav Chugh
 */
@Controller
@RequestMapping(value = "/flows")
public class FlowController extends BaseController {

    /** The Constant LOG. */
    private static final Logger LOGGER = LoggerFactory.getLogger(FlowController.class);

    /** The process switch details. */
    @Autowired
    private FlowService serviceFlow;

    /**
     * Flow list.
     *
     * @param model the model
     * @param request the request
     * @return the model and view
     */
    @RequestMapping
    public ModelAndView flowList(final HttpServletRequest request) {
    	return validateAndRedirect(request, IConstants.View.FLOW_LIST);
    }

    /**
     * Gets the flow details.
     *
     * @return the flows details
     */
    @RequestMapping(value = "/details")
    public ModelAndView flowDetails(final HttpServletRequest request) {
        return validateAndRedirect(request, IConstants.View.FLOW_DETAILS);
    }


    /**
     * Gets the flow count.
     *
     * @return the flow count
     */
    @RequestMapping(value = "/count", method = RequestMethod.GET)
    public @ResponseBody ResponseEntity<Object> getFlowCount() {
        LOGGER.info("Inside SwitchController method getFlowCount ");
        Collection<FlowCount> flowsInfo = new ArrayList<FlowCount>();
        try {
            List<FlowInfo> flows = serviceFlow.getAllFlows();
            if (flows != null) {
                flowsInfo = serviceFlow.getFlowsInfo(flows);
            }
        } catch (Exception exception) {
            LOGGER.error("Exception in getFlowCount " + exception.getMessage());
        }

        LOGGER.info("exit SwitchController method getFlowCount ");
        return new ResponseEntity<Object>(flowsInfo, HttpStatus.OK);
    }

    /**
     * Gets the topology flows.
     *
     * @return the topology flows
     */
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public @ResponseBody ResponseEntity<Object> getFlows() {
        try {
            return new ResponseEntity<Object>(serviceFlow.getAllFlows(), HttpStatus.OK);
        } catch (Exception e) {
            LOGGER.info("[getFlows] Exception: " + e.getMessage(), e);
            return new ResponseEntity<Object>(null, HttpStatus.OK);
        }
    }

    /**
     * Gets the path link.
     *
     * @param flowid the flowid
     * @return the path link
     */
    @RequestMapping(value = "/path/{flowid}", method = RequestMethod.GET)
    public @ResponseBody ResponseEntity<Object> getFlowPath(@PathVariable final String flowid) {

        LOGGER.info("Inside FlowController method getFlowPath ");
        FlowPath pathResponse = null;
        try {
            pathResponse = serviceFlow.getFlowPath(flowid);
        } catch (Exception exception) {
            LOGGER.error("Exception in getFlowPath " + exception.getMessage());
        }
        LOGGER.info("exit FlowController method getFlowPath ");
        return new ResponseEntity<Object>(pathResponse, HttpStatus.OK);
    }

}
