package org.openkilda.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;

import org.openkilda.auth.model.Permissions;
import org.openkilda.constants.IConstants;

/**
 * The Class TopologyController.
 *
 * @author Gaurav Chugh
 */
@Controller
@RequestMapping(value = "/topology")
public class TopologyController extends BaseController {

    /**
     * Topology.
     *
     * @param model the model
     * @param request the request
     * @return the model and view
     */
    @RequestMapping
    @Permissions(values = {IConstants.Permission.MENU_TOPOLOGY})
    public ModelAndView topology(final HttpServletRequest request) {
        return validateAndRedirect(request, IConstants.View.TOPOLOGY);
    }
}
