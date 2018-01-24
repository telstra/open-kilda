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

    private static final Logger LOGGER = LoggerFactory.getLogger(HomeController.class);

    /**
     * Return to home view name.
     *
     * @return home view name
     */
    @RequestMapping
    public String home() {
        LOGGER.info("[home] - start");
        return IConstants.View.HOME;
    }
}
