package org.openkilda.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;

import org.openkilda.constants.IConstants;

/**
 * The Class HomeController.
 *
 * @author Gaurav Chugh
 */
@Controller
@RequestMapping(value = "/home")
public class HomeController extends BaseController {

    /**
     * Return to home view name.
     *
     * @return home view name
     */
    @RequestMapping
    public ModelAndView home(final HttpServletRequest request) {
        return validateAndRedirect(request, IConstants.View.HOME);
    }
}
