package org.openkilda.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.openkilda.constants.IConstants;
import org.openkilda.model.IslLinkInfo;
import org.openkilda.model.PortInfo;
import org.openkilda.model.SwitchInfo;
import org.openkilda.service.SwitchService;

/**
 * The Class SwitchController.
 *
 * @author sumitpal.singh
 *
 */
@Controller
@RequestMapping(value = "/switch")
public class SwitchController extends BaseController {

    @Autowired
    private SwitchService serviceSwitch;


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
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody List<SwitchInfo> getSwitchesDetail() {
        return serviceSwitch.getSwitches();
    }

    /**
     * Gets the ports detail switch id.
     *
     * @param switchId the switch id
     * @return the ports detail switch id
     */
    @RequestMapping(value = "/{switchId}/ports", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody List<PortInfo> getPortsDetailbySwitchId(
            @PathVariable final String switchId) {
        return serviceSwitch.getPortsBySwitchId(switchId);
    }

    /**
     * Gets the links detail.
     *
     * @return the links detail
     */
    @RequestMapping(value = "/links", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody List<IslLinkInfo> getLinksDetail() {
        return serviceSwitch.getIslLinks();
    }
}
