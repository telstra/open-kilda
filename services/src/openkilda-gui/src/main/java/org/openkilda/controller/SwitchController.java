package org.openkilda.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.openkilda.constants.IConstants;
import org.openkilda.model.IslLinkInfo;
import org.openkilda.model.PortInfo;
import org.openkilda.model.SwitchInfo;
import org.openkilda.service.ServiceSwitch;

/**
 * The Class SwitchController.
 *
 * @author sumitpal.singh
 *
 */
@Controller
@RequestMapping(value = "/switch")
public class SwitchController extends BaseController {

    /** The Constant log. */
    private static final Logger log = Logger.getLogger(SwitchController.class);

    /** The process switch details. */
    @Autowired
    private ServiceSwitch serviceSwitch;


    /**
     * Switch list.
     *
     * @param model the model
     * @param request the request
     * @return the model and view
     */
    @RequestMapping
    public ModelAndView switchList(final HttpServletRequest request) {
        return validateAndRedirect(request, IConstants.View.SWITCH_LIST);

    }

    /**
     * Switch details.
     *
     * @param model the model
     * @param request the request
     * @return the model and view
     */
    @RequestMapping(value = "/details")
    public ModelAndView switchDetails(final HttpServletRequest request) {
        return validateAndRedirect(request, IConstants.View.SWITCH);
    }


    /**
     * Port details.
     *
     * @param model the model
     * @param request the request
     * @return the model and view
     */
    @RequestMapping(value = "/portdetails", method = RequestMethod.GET)
    public ModelAndView portDetails(final HttpServletRequest request) {
        return validateAndRedirect(request, IConstants.View.PORT_DETAILS);
    }


    /**
     * Isl details.
     *
     * @param model the model
     * @param request the request
     * @return the model and view
     */
    @RequestMapping(value = "/isl", method = RequestMethod.GET)
    public ModelAndView islDetails(final HttpServletRequest request) {
        return validateAndRedirect(request, IConstants.View.ISL);
    }


    /**
     * Gets the switches detail.
     *
     * @return the switches detail
     */
    @RequestMapping(value = "/list")
    public @ResponseBody ResponseEntity<Object> getSwitchesDetail() {
        log.info("Inside controller method getSwitchesdetail");
        List<SwitchInfo> switchesInfo = null;
        try {
            switchesInfo = serviceSwitch.getSwitches();
        } catch (Exception exception) {
            log.error("Exception in getSwitchesDetail " + exception.getMessage());
        }
        log.info("exit controller method getSwitchesdetail");
        return new ResponseEntity<Object>(switchesInfo, HttpStatus.OK);
    }

    /**
     * Gets the ports detail switch id.
     *
     * @param switchId the switch id
     * @return the ports detail switch id
     */
    @RequestMapping(value = "/{switchId}/ports", method = RequestMethod.GET)
    public @ResponseBody ResponseEntity<Object> getPortsDetailbySwitchId(
            @PathVariable final String switchId) {

        log.info("Inside SwitchController method getPortsDetailbySwitchId : SwitchId " + switchId);
        List<PortInfo> portResponse = null;
        try {
            portResponse = serviceSwitch.getPortDetailBySwitchId(switchId);
        } catch (Exception exception) {
            log.error("Exception in getPortsDetailbySwitchId : " + exception.getMessage());
        }
        log.info("exit SwitchController method getPortsDetailbySwitchId ");
        return new ResponseEntity<Object>(portResponse, HttpStatus.OK);
    }

    /**
     * Gets the links detail.
     *
     * @return the links detail
     */
    @RequestMapping(value = "/links", method = RequestMethod.GET)
    public @ResponseBody ResponseEntity<Object> getLinksDetail() {

        log.info("Inside SwitchController method getLinksDetail");
        List<IslLinkInfo> flows = null;
        try {
            flows = serviceSwitch.getIslLinks();
        } catch (Exception exception) {
            log.error(" Exception in getLinksDetail " + exception.getMessage());
        }
        log.info("exit SwitchController method getLinksDetail");
        return new ResponseEntity<Object>(flows, HttpStatus.OK);
    }

}
