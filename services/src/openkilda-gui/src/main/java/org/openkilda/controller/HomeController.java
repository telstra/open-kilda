package org.openkilda.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import org.openkilda.constants.IConstants;

/**
 * The Class HomeController.
 *
 * @author Gaurav Chugh
 */
@Controller
@RequestMapping(value = "/home")
public class HomeController extends BaseController {
    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(HomeController.class);

    /**
     * Home.
     *
     * @param model the model
     * @param request the request
     * @return the string
     */

    @RequestMapping
    public String home() {
        LOG.info("Inside HomeController method home");
        return IConstants.View.HOME;
    }

}
