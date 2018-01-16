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
import org.openkilda.model.response.FlowCount;
import org.openkilda.model.response.FlowPath;
import org.openkilda.service.ServiceFlow;

/**
 * The Class FlowController.
 *
 * @author Gaurav Chugh
 */
@Controller
@RequestMapping(value = "/flows")
public class FlowController extends BaseController {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(FlowController.class);

    /** The process switch details. */
    @Autowired
    private ServiceFlow serviceFlow;

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
     * Gets the flow count.
     *
     * @return the flow count
     */
    @RequestMapping(value = "/count", method = RequestMethod.GET)
    public @ResponseBody ResponseEntity<Object> getFlowCount() {

        LOG.info("Inside SwitchController method getFlowCount ");
        Collection<FlowCount> flowsInfo = new ArrayList<FlowCount>();
        List<FlowInfo> flows = null;
        try {
            flows = serviceFlow.getAllFlows();
        } catch (Exception exception) {
            LOG.error("Exception in getFlowCount " + exception.getMessage());
        }

        if (flows != null) {
            flowsInfo = serviceFlow.getFlowsInfo(flows);
        }
        LOG.info("exit SwitchController method getFlowCount ");
        return new ResponseEntity<Object>(flowsInfo, HttpStatus.OK);
    }

    /**
     * Gets the topology flows.
     *
     * @return the topology flows
     */
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public @ResponseBody ResponseEntity<Object> getFlows() {
        return new ResponseEntity<Object>(serviceFlow.getAllFlows(), HttpStatus.OK);
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
     * Gets the path link.
     *
     * @param flowid the flowid
     * @return the path link
     */
    @RequestMapping(value = "/path/{flowid}", method = RequestMethod.GET)
    public @ResponseBody ResponseEntity<Object> getFlowPath(@PathVariable final String flowid) {

        LOG.info("Inside FlowController method getFlowPath ");
        FlowPath pathResponse = null;
        try {
            pathResponse = serviceFlow.getFlowPath(flowid);
        } catch (Exception exception) {
            LOG.error("Exception in getFlowPath " + exception.getMessage());
        }
        LOG.info("exit FlowController method getFlowPath ");
        return new ResponseEntity<Object>(pathResponse, HttpStatus.OK);
    }

}
