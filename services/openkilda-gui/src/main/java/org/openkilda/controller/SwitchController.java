package org.openkilda.controller;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.log4j.Logger;
import org.openkilda.model.PortInfo;
import org.openkilda.model.SwitchRelationData;
import org.openkilda.switchhelper.ProcessSwitchDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 *
 * The Class SwitchController
 * 
 * @author sumitpal.singh
 * 
 */
@Controller
@RequestMapping(value = "/switch")
public class SwitchController {

    /** The Constant LOG. */

    private static final Logger log = Logger.getLogger(SwitchController.class);

    @Autowired
    ProcessSwitchDetails processSwitchDetails;

    /**
     * get all switch.
     * 
     * @param model
     * @param request
     * @return
     */
    @RequestMapping(method = RequestMethod.GET)
    public @ResponseBody ResponseEntity<Object> getSwitchesDetial(ModelMap model,
            HttpServletRequest request) {
        log.info("inside controller method getSwitchData");
        SwitchRelationData switchDataList = new SwitchRelationData();

        try {
            switchDataList = processSwitchDetails.getswitchdataList();
        } catch (Exception exception) {
            log.fatal("Fatal Exception in getSwitchData "
                    + ExceptionUtils.getFullStackTrace(exception));
        }
        model.addAttribute("getSwitchData", switchDataList);
        return new ResponseEntity<Object>(switchDataList, HttpStatus.OK);
    }

    /**
     * get all ports based on switchId.
     * 
     * @param model
     * @param request
     * @param switchId
     * @return
     */
    @RequestMapping(value = "/{switchId}/ports", method = RequestMethod.GET)
    public @ResponseBody ResponseEntity<Object> getPortsDetailSwitchId(ModelMap model,
            HttpServletRequest request, @PathVariable String switchId) {

        log.info("inside getPortsDetailSwitchId : SwitchId " + switchId);
        List<PortInfo> portResponse = null;
        try {
            portResponse = ProcessSwitchDetails.getPortResponseBasedOnSwitchId(switchId);
        } catch (Exception exception) {
            log.fatal("Fatal Exception in getPortsDetailSwitchId "
                    + ExceptionUtils.getFullStackTrace(exception));
        }
        model.addAttribute("getPortsDetailSwitchId", portResponse);
        return new ResponseEntity<Object>(portResponse, HttpStatus.OK);
    }

    /**
     * get all Flows .
     * 
     * @param model
     * @param request
     * @return
     */
    @RequestMapping(value = "/flows", method = RequestMethod.GET)
    public @ResponseBody ResponseEntity<Object> getFlowsDetail(ModelMap model,
            HttpServletRequest request) {

        log.info("inside getFlowsDetail ");
        SwitchRelationData flowResponse = null;
        try {
            flowResponse = processSwitchDetails.getAllFlows(null);
        } catch (Exception exception) {
            log.fatal("Fatal Exception in getFlowsDetail "
                    + ExceptionUtils.getFullStackTrace(exception));
        }
        model.addAttribute("getFlowsDetail", flowResponse);
        return new ResponseEntity<Object>(flowResponse, HttpStatus.OK);
    }

    /**
     * get all Links .
     * 
     * @param model
     * @param request
     * @return
     */
    @RequestMapping(value = "/links", method = RequestMethod.GET)
    public @ResponseBody ResponseEntity<Object> getLinksDetail(ModelMap model,
            HttpServletRequest request) {

        log.info("inside getLinksDetail");
        SwitchRelationData portResponse = null;
        try {
            portResponse = processSwitchDetails.getAllLinks(null);
        } catch (Exception exception) {
            log.fatal("Fatal Exception in getLinksDetail "
                    + ExceptionUtils.getFullStackTrace(exception));
        }
        model.addAttribute("getLinksDetail", portResponse);
        return new ResponseEntity<Object>(portResponse, HttpStatus.OK);
    }
}
