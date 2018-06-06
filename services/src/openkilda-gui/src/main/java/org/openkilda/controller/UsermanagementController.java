package org.openkilda.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;

import org.openkilda.constants.IConstants;

/**
 * The Class UserManagementController.
 *
 * @author Neeraj Kumar
 */
@Controller
@RequestMapping(value = "/usermanagement")
public class UsermanagementController extends BaseController {

    /**
     * UserManagement.
     *
     * @param model the model
     * @param request the request
     * @return the model and view
     */
    @RequestMapping
    public ModelAndView usermanagement(final HttpServletRequest request) {
        return validateAndRedirect(request, IConstants.View.USERMANAGEMENT);
    }
}
