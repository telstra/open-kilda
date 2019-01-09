/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;

@Controller
@RequestMapping(value = { "/home", "/topology", "/flows", "/isl", "/switches", "/usermanagement", "/useractivity",
        "/storesetting", "/application-setting" }, method = RequestMethod.GET)
public class CommonRequestHandlerController extends BaseController {
    /**
     * CommonRequestHandlerController.
     *
     * @param request the request
     * 
     * @return the model and view
     */
    @RequestMapping
    public ModelAndView parentRoutes(final HttpServletRequest request) {
        return new ModelAndView("forward:/ui/index.html");
    }

    @RequestMapping(value = { "/details/*", "/switch/*", "/edit/*", "/add-new", }, method = RequestMethod.GET)
    public ModelAndView childRoutes(final HttpServletRequest request) {
        return new ModelAndView("forward:/ui/index.html");
    }
    
    @RequestMapping(value = { "/details/*/*/*"}, method = RequestMethod.GET)
    public ModelAndView childChildRoutes(final HttpServletRequest request) {
        return new ModelAndView("forward:/./ui/index.html");
    }
}
