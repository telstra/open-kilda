/* Copyright 2023 Telstra Open Source
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

import org.openkilda.log.ActivityLogger;
import org.openkilda.log.constants.ActivityType;
import org.openkilda.model.YFlowRerouteResult;
import org.openkilda.service.YFlowService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/y-flows")
public class YFlowController extends BaseController {

    private static final Logger LOGGER = LoggerFactory.getLogger(YFlowController.class);

    @Autowired
    private YFlowService yFlowService;

    @Autowired
    private ActivityLogger activityLogger;

    /**
     * Reroute the y-flow and returns the shared path and sub-flow paths with all nodes/switches exists in
     * provided y-flow.
     *
     * @param yFlowId ID of y-flow to be rerouted.
     * @return the result of the rerouting operation.
     */
    @RequestMapping(value = "/{yFlowId}/reroute", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody YFlowRerouteResult rerouteFlow(@PathVariable final String yFlowId) {
        activityLogger.log(ActivityType.FLOW_REROUTE, yFlowId);
        LOGGER.info("Reroute y-flow. Y-Flow id: '" + yFlowId + "'");
        return yFlowService.rerouteFlow(yFlowId);
    }
}
